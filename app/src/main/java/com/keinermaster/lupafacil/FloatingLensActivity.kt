package com.keinermaster.lupafacil

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Pantalla PRINCIPAL de LupaFacil: control de la lupa flotante de pantalla.
 *
 * La lupa amplia la PANTALLA del telefono (no la camara) y flota sobre
 * cualquier otra app: la persona la arrastra con el dedo hasta el texto
 * que quiere leer y lo ve ampliado. Permite zoom (-/+, pinza) y
 * desplazamiento libre.
 *
 * Correcciones aplicadas en versiones anteriores:
 *  1) Permiso POST_NOTIFICATIONS (Android 13+) antes de arrancar.
 *  2) Diagnostico en pantalla (floatingDiag, nullable por seguridad).
 *  3) Comprobacion real de arranque (isRunning + lastError).
 *  4) Sin camara: la app ya no pide ni usa la camara.
 */
class FloatingLensActivity : AppCompatActivity() {

    companion object {
        /** Tiempo de espera antes de comprobar si el servicio arranco. */
        private const val START_CHECK_DELAY_MS = 1000L
    }

    private lateinit var tts: TtsEngine
    private lateinit var prefs: Prefs

    private lateinit var statusText: TextView
    private lateinit var zoomText: TextView

    /**
     * TextView de diagnostico. Puede ser null si el layout todavia no
     * incluye el id `floatingDiag` (por eso es nullable y se comprueba).
     */
    private var diagText: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Comprobacion diferida del arranque del servicio. */
    private val startCheck = Runnable { checkServiceStarted() }

    // ------------------------------------------------------------------
    // Permiso de notificaciones (Android 13+)
    // ------------------------------------------------------------------
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // No es bloqueante: la lupa funciona igual, pero Android puede
                // no mostrar la notificacion del servicio en primer plano.
                showDiag("Aviso: sin permiso de notificaciones la lupa puede cerrarse sola.")
            }
            // Continua con el flujo normal.
            continueStartFlow()
        }

    // ------------------------------------------------------------------
    // Permiso de captura de pantalla
    // ------------------------------------------------------------------
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                startLensService(result.resultCode, data)
            } else {
                tts.speak("Permiso de captura denegado")
                showDiag("Permiso de captura denegado por el usuario.")
                updateStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_floating_lens)

        tts = TtsEngine(this)
        prefs = Prefs(this)

        statusText = findViewById(R.id.floatingStatus)
        zoomText = findViewById(R.id.floatingZoom)

        // El layout puede no tener este id todavia: se comprueba null.
        diagText = findViewById<TextView?>(R.id.floatingDiag)

        findViewById<Button>(R.id.btnStartFloating).setOnClickListener {
            if (FloatingLensService.isRunning) {
                tts.speak("La lupa ya está activa")
                showDiag("La lupa ya está ACTIVA. Ya puedes salir y abrir otra app.")
                updateStatus()
                return@setOnClickListener
            }
            if (hasOverlayPermission()) {
                requestNotificationPermissionIfNeeded()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btnStopFloating).setOnClickListener {
            stopLensService()
            tts.speak("Lupa flotante detenida")
        }

        findViewById<Button>(R.id.btnFloatingSizeSmall).setOnClickListener {
            setSize(FloatingLensView.SIZE_SMALL, "pequeña")
        }

        findViewById<Button>(R.id.btnFloatingSizeMedium).setOnClickListener {
            setSize(FloatingLensView.SIZE_MEDIUM, "mediana")
        }

        findViewById<Button>(R.id.btnFloatingSizeLarge).setOnClickListener {
            setSize(FloatingLensView.SIZE_LARGE, "grande")
        }

        findViewById<Button>(R.id.btnFloatingZoomIn).setOnClickListener {
            setZoom(prefs.lensZoom + 0.5f)
        }

        findViewById<Button>(R.id.btnFloatingZoomOut).setOnClickListener {
            setZoom(prefs.lensZoom - 0.5f)
        }

        findViewById<Button>(R.id.btnShapeCircle).setOnClickListener {
            setShape(FloatingLensView.SHAPE_CIRCLE, "círculo")
        }

        findViewById<Button>(R.id.btnShapeBar).setOnClickListener {
            setShape(FloatingLensView.SHAPE_BAR, "barra de lectura")
        }

        findViewById<Button>(R.id.btnFloatingHelp).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        // Tutorial de bienvenida, solo la primera vez.
        if (!prefs.tutorialSeen) {
            prefs.tutorialSeen = true
            startActivity(Intent(this, TutorialActivity::class.java))
        }

        updateStatus()
        showDiag("Listo. Pulsa ACTIVAR para empezar.")
    }

    // ------------------------------------------------------------------
    // Diagnostico en pantalla
    // ------------------------------------------------------------------

    /** Muestra un mensaje de diagnostico (si el layout tiene el TextView). */
    private fun showDiag(message: String) {
        val view = diagText ?: return
        try {
            view.text = message
        } catch (e: Exception) {
            // Nunca dejamos que el diagnostico tumbe la pantalla.
        }
    }

    // ------------------------------------------------------------------
    // Ajustes de tamano y aumento
    // ------------------------------------------------------------------

    private fun setSize(sizeDp: Float, name: String) {
        prefs.lensSize = sizeDp
        tts.speak("Lupa $name")
        pushSettingsToService()
        updateStatus()
    }

    private fun setZoom(value: Float) {
        val clamped = value.coerceIn(FloatingLensView.MIN_ZOOM, FloatingLensView.MAX_ZOOM)
        prefs.lensZoom = clamped
        tts.speak("Aumento ${"%.1f".format(clamped)}")
        pushSettingsToService()
        updateStatus()
    }

    /**
     * Cambia la forma de la lupa (círculo o barra de lectura). Se aplica
     * en caliente si la lupa está activa.
     */
    private fun setShape(shape: Int, name: String) {
        prefs.lensShape = shape
        tts.speak("Lupa: $name")
        pushSettingsToService()
        updateStatus()
    }

    /**
     * Resalta la forma elegida y habilita los botones de tamaño solo en
     * modo círculo (en modo barra el tamaño es fijo).
     */
    private fun updateShapeUi() {
        val isBar = prefs.lensShape == FloatingLensView.SHAPE_BAR
        findViewById<Button>(R.id.btnShapeCircle).alpha = if (isBar) 0.55f else 1f
        findViewById<Button>(R.id.btnShapeBar).alpha = if (isBar) 1f else 0.55f
        val sizeAlpha = if (isBar) 0.4f else 1f
        findViewById<Button>(R.id.btnFloatingSizeSmall).alpha = sizeAlpha
        findViewById<Button>(R.id.btnFloatingSizeMedium).alpha = sizeAlpha
        findViewById<Button>(R.id.btnFloatingSizeLarge).alpha = sizeAlpha
        findViewById<Button>(R.id.btnFloatingSizeSmall).isEnabled = !isBar
        findViewById<Button>(R.id.btnFloatingSizeMedium).isEnabled = !isBar
        findViewById<Button>(R.id.btnFloatingSizeLarge).isEnabled = !isBar
    }

    /**
     * Si la lupa ya está activa, le envía los nuevos ajustes para que se
     * apliquen al momento, sin tener que detenerla y volver a activarla.
     */
    private fun pushSettingsToService() {
        if (!FloatingLensService.isRunning) return
        try {
            val intent = Intent(this, FloatingLensService::class.java).apply {
                action = FloatingLensService.ACTION_UPDATE_SETTINGS
            }
            startService(intent)
        } catch (e: Exception) {
            Log.w("FloatingLensActivity", "No se pudo actualizar la lupa activa", e)
        }
    }

    private fun updateStatus() {
        val sizeName = when (prefs.lensSize) {
            FloatingLensView.SIZE_SMALL -> "pequeña"
            FloatingLensView.SIZE_LARGE -> "grande"
            else -> "mediana"
        }
        val shapeName = if (prefs.lensShape == FloatingLensView.SHAPE_BAR) "barra" else "círculo"
        val running = if (FloatingLensService.isRunning) "ACTIVA" else "detenida"
        statusText.text = "Lupa $shapeName $sizeName · $running"
        zoomText.text = "Aumento: ${"%.1f".format(prefs.lensZoom)}x"
        updateShapeUi()
    }

    // ------------------------------------------------------------------
    // Permisos
    // ------------------------------------------------------------------

    private fun hasOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

    private fun requestOverlayPermission() {
        tts.speak("Primero hay que dar permiso para dibujar sobre otras apps")
        showDiag("Falta el permiso \"Dibujar sobre otras apps\".")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                showDiag("No se pudieron abrir los ajustes de overlay: ${e.message}")
            }
        }
    }

    /** Pide POST_NOTIFICATIONS en Android 13+ y luego continua el flujo. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        continueStartFlow()
    }

    /** Siguiente paso: pedir el permiso de captura de pantalla. */
    private fun continueStartFlow() {
        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        // En Android 14 o superior el sistema ofrece compartir "Una sola
        // app" o "Toda la pantalla". La lupa necesita TODA la pantalla.
        showDiag("En la ventana que sale, elige \"Toda la pantalla\" y pulsa Comenzar.")
        tts.speak("En la ventana que sale, elige toda la pantalla y pulsa comenzar")
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } catch (e: Exception) {
            showDiag("No se pudo pedir el permiso de captura: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Arranque y parada del servicio
    // ------------------------------------------------------------------

    private fun startLensService(resultCode: Int, data: Intent) {
        val intent = Intent(this, FloatingLensService::class.java).apply {
            putExtra(FloatingLensService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingLensService.EXTRA_DATA, data)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            showDiag("No se pudo arrancar el servicio: ${e.message}")
            updateStatus()
            return
        }

        tts.speak("Lupa flotante activada. Puedes salir y usar otras apps.")
        showDiag("Arrancando la lupa…")
        updateStatus()

        // Comprueba el arranque real tras 1 segundo.
        mainHandler.removeCallbacks(startCheck)
        mainHandler.postDelayed(startCheck, START_CHECK_DELAY_MS)
    }

    /** Comprueba si el servicio esta realmente activo y muestra el error. */
    private fun checkServiceStarted() {
        updateStatus()

        if (FloatingLensService.isRunning) {
            showDiag("Lupa ACTIVA. Ya puedes salir y abrir otra app.")
        } else {
            val error = FloatingLensService.lastError
            showDiag(
                if (error.isNullOrBlank()) {
                    "La lupa no arrancó. Revisa el permiso de overlay y de captura."
                } else {
                    "La lupa no arrancó: $error"
                }
            )
        }
    }

    private fun stopLensService() {
        try {
            stopService(Intent(this, FloatingLensService::class.java))
        } catch (e: Exception) {
            showDiag("No se pudo detener el servicio: ${e.message}")
        }
        showDiag("Lupa detenida.")
        updateStatus()
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        updateStatus()
        // Si el usuario acaba de conceder el permiso de overlay, lo avisamos.
        if (hasOverlayPermission() && !FloatingLensService.isRunning) {
            showDiag("Permiso de overlay concedido. Pulsa ACTIVAR.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(startCheck)
        tts.shutdown()
    }
}
