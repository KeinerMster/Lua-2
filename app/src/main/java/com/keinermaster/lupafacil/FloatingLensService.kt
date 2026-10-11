package com.keinermaster.lupafacil

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.abs
import androidx.core.app.NotificationCompat

/**
 * Servicio de lupa flotante (v8) — COMPATIBILIDAD UNIVERSAL.
 *
 * ==================================================================
 * MEJORAS DE ESTA VERSION
 * ==================================================================
 *
 * COMPATIBILIDAD ENTRE DISPOSITIVOS
 * ---------------------------------
 * 1. ORIENTACION: se detecta el cambio de orientacion y se recrea el
 *    VirtualDisplay con el nuevo tamano. Antes, al girar el telefono,
 *    la captura quedaba con el tamano antiguo y la lupa se veia mal.
 *
 * 2. AREA SEGURA (notch / barra de navegacion): la posicion inicial de
 *    la lente se calcula dentro del area segura, no pegada al borde.
 *
 * 3. TAMANO ADAPTATIVO: la lente se adapta al tamano de la pantalla
 *    (movil pequeno, movil grande, tablet).
 *
 * 4. RATIO DE PANTALLA: funciona en 16:9, 18:9, 20:9 y 21:9 porque el
 *    mapeo usa el tamano real de la pantalla.
 *
 * 5. ROW PADDING: se compensa con la escala entre bitmap y pantalla.
 *
 * 6. PANTALLA APAGADA: se detecta y se pausa la captura para ahorrar
 *    bateria.
 *
 * 7. PERMISO REVOCADO: si el usuario quita el permiso de overlay, el
 *    servicio se cierra limpiamente en vez de crashear.
 *
 * 8. ANDROID 14/15: startForeground con tipo + registerCallback.
 *
 * ACCESIBILIDAD
 * -------------
 * 9. La lente se oculta con alpha=0 durante 32 ms al capturar, para que
 *    no se fotografíe a si misma (sin parpadeo perceptible).
 *
 * 10. Boton de centrar: si la lente se pierde, vuelve al centro.
 *
 * 11. La lente nunca puede salirse de la pantalla.
 */
class FloatingLensService : Service() {

    companion object {
        private const val TAG = "FloatingLensService"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.keinermaster.lupafacil.STOP_FLOATING"
        /**
         * Aplica cambios de tamaño/aumento a la lupa que ya está activa,
         * sin reiniciar el servicio (la pantalla de control la envía).
         */
        const val ACTION_UPDATE_SETTINGS = "com.keinermaster.lupafacil.UPDATE_FLOATING"
        private const val CHANNEL_ID = "lupafacil_floating"
        private const val NOTIF_ID = 2001

        /**
         * Intervalo entre refrescos cuando la lupa esta QUIETA.
         *
         * Mientras arrastras NO se captura: la lupa usa la ultima foto
         * completa de la pantalla (ya contiene todo lo que hay debajo),
         * asi el arrastre es fluido y sin parpadeo. Ademas se refresca al
         * soltar la lupa y al tocar la app de debajo (ver OUTSIDE).
         *
         * Es de 4 s (no 1,5 s): cada refresco oculta la lente un instante
         * y parpadea; para leer textos quietos 4 s es suficiente y molesta
         * mucho menos. Al interactuar se refresca al momento igualmente.
         */
        private const val IDLE_CAPTURE_INTERVAL_MS = 4000L

        /** Espera tras soltar la lupa antes de refrescar la foto. */
        private const val AFTER_DRAG_CAPTURE_MS = 150L

        /** Espera tras un toque en la app de debajo (p. ej. un scroll). */
        private const val OUTSIDE_TOUCH_CAPTURE_MS = 600L

        /** Primera captura, poco despues de mostrar la lupa. */
        private const val FIRST_CAPTURE_MS = 120L

        /**
         * Espera tras ocultar la lente antes de capturar, en ms. 50 ms son
         * ~3 fotogramas a 60 Hz: suficiente para que el compositor presente
         * un frame SIN la lente, pero tan breve que el parpadeo apenas se
         * nota (antes eran 70 ms y el parpadeo se percibía más).
         */
        private const val LENS_HIDE_DELAY_MS = 50L

        /**
         * La foto solo vale si llego DESPUES de ocultar la lente (+20 ms).
         * Si no, se espera un poco mas (maximo 4 veces) y se usa la que haya.
         */
        private const val MIN_FRAME_AFTER_HIDE_NS = 20_000_000L
        private const val FRESH_RETRY_MS = 40L
        private const val MAX_FRESH_RETRIES = 4

        /** Margen minimo desde el borde de la pantalla, en dp. */
        private const val EDGE_MARGIN_DP = 12f

        /**
         * Suavizado del arrastre (0..1). La ventana "persigue" al dedo en
         * vez de saltar con cada evento táctil: estabiliza la lupa con
         * manos temblorosas y el movimiento se siente más fluido.
         */
        private const val DRAG_SMOOTHING = 0.5f

        /**
         * Si el salto supera esta distancia (px) se coloca directo, sin
         * suavizar: cubre los movimientos programáticos (centrar, etc.).
         */
        private const val DRAG_SNAP_DISTANCE_PX = 250f

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var lastError: String? = null
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lensView: FloatingLensView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Dos bitmaps alternados: se escribe en uno mientras se dibuja el otro. */
    private var bitmapA: Bitmap? = null
    private var bitmapB: Bitmap? = null
    private var useBitmapA = true

    /**
     * Ultimo frame recibido de la pantalla. Se mantiene SIEMPRE el mas
     * reciente (y se cierra el anterior) para que la cola del ImageReader
     * nunca se llene y la captura no se quede con una imagen vieja.
     */
    private var latestImage: Image? = null
    private val frameLock = Any()

    @Volatile
    private var latestFrameNanos = 0L

    /** Momento en que se oculto la lente para capturar. */
    private var hideNanos = 0L
    private var freshRetries = 0

    @Volatile
    private var closing = false

    @Volatile
    private var viewAttached = false

    /** Ultimo tamaño total de la ventana (para mantener el centro al redimensionar). */
    private var lastWindowW = 0
    private var lastWindowH = 0

    /** Posicion actual de la ventana en la pantalla. */
    private var windowX = 0f
    private var windowY = 0f

    /** Posicion suavizada que realmente se muestra (persigue a windowX/Y). */
    private var smoothWinX = 0f
    private var smoothWinY = 0f
    private var smoothInit = false

    /** Ultimo destino pedido (para asentar exacto al soltar el dedo). */
    private var lastTargetX = 0f
    private var lastTargetY = 0f

    /** Indica si la pantalla esta encendida (para pausar la captura). */
    @Volatile
    private var screenOn = true

    /**
     * Zoom pendiente de guardar en preferencias. Durante una pinza el zoom
     * cambia decenas de veces por segundo: se guarda con retardo (y una
     * sola vez) en vez de escribir al disco en cada evento.
     */
    private var pendingZoomSave: Float? = null
    private val saveZoomRunnable = Runnable { flushZoomSave() }

    /** Escribe el zoom pendiente, si lo hay. */
    private fun flushZoomSave() {
        val value = pendingZoomSave
        pendingZoomSave = null
        if (value != null) {
            try {
                Prefs(this@FloatingLensService).lensZoom = value
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo guardar el zoom", e)
            }
        }
    }

    /**
     * Escucha el apagado/encendido de pantalla para pausar la captura
     * y ahorrar bateria.
     */
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    Log.i(TAG, "Pantalla apagada: captura en pausa")
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    Log.i(TAG, "Pantalla encendida: captura reanudada")
                }
            }
        }
    }

    private var screenReceiverRegistered = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection detenido por el sistema")
            requestClose("proyeccion detenida")
        }
    }

    /** Detecta cambios de orientacion y de estado de pantalla. */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}

        override fun onDisplayRemoved(displayId: Int) {}

        override fun onDisplayChanged(displayId: Int) {
            // La orientacion o el tamano de pantalla pueden haber cambiado.
            mainHandler.post { handleDisplayChange() }
        }
    }

    private var displayManager: DisplayManager? = null

    private val captureRunnable = Runnable { performCapture() }

    /** Cada frame nuevo de la pantalla: guarda el mas reciente. */
    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        }
        if (image != null) {
            synchronized(frameLock) {
                try {
                    latestImage?.close()
                } catch (e: Exception) {
                    // Ignora.
                }
                latestImage = image
                latestFrameNanos = System.nanoTime()
            }
        }
    }

    private val captureAfterHide = Runnable { captureAfterHideStep() }

    /** Segundo paso del ciclo: ya con la lente oculta, copia la foto. */
    private fun captureAfterHideStep() {
        val view = lensView
        if (closing || view == null) {
            view?.alpha = 1f
            return
        }

        val fresh = latestFrameNanos > hideNanos + MIN_FRAME_AFTER_HIDE_NS
        if (!fresh && freshRetries < MAX_FRESH_RETRIES) {
            freshRetries++
            mainHandler.postDelayed(captureAfterHide, FRESH_RETRY_MS)
            return
        }

        val handler = captureHandler
        if (handler == null) {
            view.alpha = 1f
            scheduleCapture(IDLE_CAPTURE_INTERVAL_MS)
            return
        }

        handler.post {
            captureFrame()
            mainHandler.post {
                lensView?.alpha = 1f
                scheduleCapture(IDLE_CAPTURE_INTERVAL_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestClose("notificacion")
            return START_NOT_STICKY
        }

        // Ajustes en caliente: la lupa sigue activa, solo cambia tamaño/zoom.
        if (intent?.action == ACTION_UPDATE_SETTINGS) {
            applyLiveSettings()
            return START_NOT_STICKY
        }

        // Si ya está activa, no se reinicia: evita duplicar el overlay y
        // fugar el hilo de captura (p. ej. doble toque en ACTIVAR).
        if (isRunning) {
            Log.i(TAG, "La lupa ya está activa: se ignora el arranque duplicado")
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (data == null) {
            // Android exige startForeground si el servicio se inicio con
            // startForegroundService, aunque vayamos a detenerlo.
            startForegroundNotification()
            reportError("Sin datos de proyeccion")
            return START_NOT_STICKY
        }

        if (!startForegroundNotification()) return START_NOT_STICKY
        if (!startProjection(resultCode, data)) return START_NOT_STICKY
        if (!showLensOverlay()) return START_NOT_STICKY

        // Primera foto de la pantalla, enseguida.
        scheduleCapture(FIRST_CAPTURE_MS)

        // Escucha cambios de orientacion y de pantalla.
        registerDisplayListener()
        registerScreenReceiver()

        isRunning = true
        lastError = null
        return START_NOT_STICKY
    }

    /**
     * Registra un error y detiene el servicio de forma segura.
     *
     * No se puede llamar a stopSelf() a secas si el servicio ya esta en
     * primer plano (la notificacion quedaria colgada), ni si nunca llego
     * a estarlo (Android lanza RemoteServiceException).
     */
    private fun reportError(message: String) {
        Log.e(TAG, message)
        lastError = message
        requestClose("error: $message")
    }

    // ==================================================================
    // Notificacion
    // ==================================================================

    private fun startForegroundNotification(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Lupa flotante",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Mantiene activa la lupa flotante"
                    setShowBadge(false)
                }
                getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }

            val stopIntent = Intent(this, FloatingLensService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPending = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("LupaFacil activa")
                .setContentText("La lupa flotante está funcionando")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPending)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error en startForeground", e)
            lastError = "No se pudo iniciar el servicio: ${e.message}"
            false
        }
    }

    // ==================================================================
    // Proyeccion
    // ==================================================================

    private fun startProjection(resultCode: Int, data: Intent): Boolean {
        return try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data)
            if (projection == null) {
                reportError("No se pudo obtener la captura de pantalla")
                return false
            }
            mediaProjection = projection

            // OBLIGATORIO en Android 14+: callback ANTES del VirtualDisplay.
            projection.registerCallback(projectionCallback, mainHandler)

            if (!updateScreenMetrics()) {
                reportError("No se pudo leer el tamaño de la pantalla")
                return false
            }

            // El hilo de captura debe existir ANTES del ImageReader (su
            // listener se ejecuta en este hilo).
            val thread = HandlerThread("LupaFacilCapture").also { it.start() }
            captureThread = thread
            captureHandler = Handler(thread.looper)

            if (!createVirtualDisplay(projection)) {
                return false
            }

            true
        } catch (e: Exception) {
            reportError("Error al iniciar la captura: ${e.message}")
            false
        }
    }

    /** Lee el tamano real de la pantalla (incluye barras del sistema). */
    private fun updateScreenMetrics(): Boolean {
        return try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
            screenWidth > 0 && screenHeight > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error al leer metricas", e)
            false
        }
    }

    /** Crea el VirtualDisplay con el tamano actual de la pantalla. */
    private fun createVirtualDisplay(projection: MediaProjection): Boolean {
        return try {
            virtualDisplay?.release()
            virtualDisplay = null

            // Suelta el frame guardado (es del tamano anterior).
            synchronized(frameLock) {
                try {
                    latestImage?.close()
                } catch (e: Exception) {
                    // Ignora.
                }
                latestImage = null
                latestFrameNanos = 0L
            }
            imageReader?.close()

            // 3 imagenes: 1 guardada + hasta 2 en transito.
            val reader = ImageReader.newInstance(
                screenWidth, screenHeight, PixelFormat.RGBA_8888, 3
            )
            reader.setOnImageAvailableListener(imageListener, captureHandler)
            imageReader = reader

            virtualDisplay = projection.createVirtualDisplay(
                "LupaFacilFloating",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )

            if (virtualDisplay == null) {
                reportError("No se pudo crear la pantalla virtual")
                return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear VirtualDisplay", e)
            false
        }
    }

    // ==================================================================
    // Overlay — VENTANA PEQUENA (solo la lente)
    // ==================================================================

    private fun showLensOverlay(): Boolean {
        return try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val prefs = Prefs(this)
            val lens = FloatingLensView(this).apply {
                zoomFactor = prefs.lensZoom
                setLensSize(prefs.lensSize)
                lensShape = prefs.lensShape

                onCloseRequested = { requestClose("boton ✕") }
                onCenterRequested = { centerLens() }

                onZoomChanged = { value ->
                    pendingZoomSave = value
                    mainHandler.removeCallbacks(saveZoomRunnable)
                    mainHandler.postDelayed(saveZoomRunnable, 800L)
                }
                onSizeChanged = { value ->
                    try {
                        Prefs(this@FloatingLensService).lensSize = value
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo guardar el tamano", e)
                    }
                    mainHandler.post { resizeWindow() }
                }
                onPositionChanged = { x, y -> moveWindowTo(x, y) }
                onInteractionEnded = {
                    snapWindowToTarget()
                    scheduleCapture(AFTER_DRAG_CAPTURE_MS)
                }
                onOutsideTouch = { scheduleCapture(OUTSIDE_TOUCH_CAPTURE_MS) }
            }
            lensView = lens

            // La lente debe conocer la pantalla ANTES de medirse: asi su
            // radio se recorta para que quepa (p. ej. "Grande" en un movil).
            lens.setScreenSize(screenWidth, screenHeight)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // WRAP_CONTENT: la ventana mide solo lo que mide la lente.
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Que el notch no desplace la ventana: la lupa y la foto
                // deben usar el mismo origen (0,0) de la pantalla.
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            // Posicion inicial: centro del area segura.
            val winW = lens.getTotalWidthPx()
            val winH = lens.getTotalHeightPx()
            val margin = (EDGE_MARGIN_DP * resources.displayMetrics.density).toInt()
            windowX = ((screenWidth - winW) / 2f).coerceAtLeast(margin.toFloat())
            windowY = ((screenHeight - winH) / 2f).coerceAtLeast(margin.toFloat())
            params.x = windowX.toInt()
            params.y = windowY.toInt()

            lastWindowW = winW
            lastWindowH = winH
            layoutParams = params
            windowManager?.addView(lens, params)
            viewAttached = true

            // Informa a la vista del tamano real de la pantalla.
            lens.setScreenSize(screenWidth, screenHeight)
            lens.setWindowPosition(windowX, windowY)

            true
        } catch (e: Exception) {
            reportError("No se pudo mostrar la lupa. ¿Falta el permiso de superposición?")
            false
        }
    }

    /** Centra la lente en la pantalla. */
    private fun centerLens() {
        val view = lensView ?: return
        val params = layoutParams ?: return
        val winW = view.getTotalWidthPx()
        val winH = view.getTotalHeightPx()

        windowX = ((screenWidth - winW) / 2f).coerceAtLeast(0f)
        windowY = ((screenHeight - winH) / 2f).coerceAtLeast(0f)
        syncSmoothWithWindow()
        params.x = windowX.toInt()
        params.y = windowY.toInt()

        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo centrar la lente", e)
        }
        view.setWindowPosition(windowX, windowY)
    }

    /**
     * Sincroniza el suavizado con la posicion real de la ventana.
     * Se usa tras movimientos programaticos (centrar, redimensionar) para
     * que el proximo arrastre no "derive" desde una posicion vieja.
     */
    private fun syncSmoothWithWindow() {
        smoothWinX = windowX
        smoothWinY = windowY
        lastTargetX = windowX
        lastTargetY = windowY
        smoothInit = true
    }

    /**
     * Al soltar el dedo, la ventana se coloca EXACTAMENTE en el destino:
     * el suavizado se detiene cuando dejan de llegar eventos tactiles y
     * podria quedar unos pixeles atras.
     */
    private fun snapWindowToTarget() {
        val params = layoutParams ?: return
        val view = lensView ?: return
        if (!smoothInit) return
        smoothWinX = lastTargetX
        smoothWinY = lastTargetY
        windowX = smoothWinX
        windowY = smoothWinY
        params.x = windowX.toInt()
        params.y = windowY.toInt()
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo asentar la ventana", e)
        }
        view.setWindowPosition(windowX, windowY)
    }

    /**
     * Coloca la ventana en la posicion ABSOLUTA (x, y), limitada a la
     * pantalla. Al ser absoluta, si el dedo se pasa del borde y vuelve,
     * la lupa vuelve con el dedo (antes se "despegaba").
     *
     * La posicion se SUAVIZA: la ventana persigue al dedo con un filtro
     * exponencial en vez de saltar con cada evento tactil. Esto estabiliza
     * la lupa con manos temblorosas y el arrastre se siente mas fluido.
     */
    private fun moveWindowTo(x: Float, y: Float) {
        val params = layoutParams ?: return
        val view = lensView ?: return

        val viewW = if (view.width > 0) view.width else view.getTotalWidthPx()
        val viewH = if (view.height > 0) view.height else view.getTotalHeightPx()

        val maxX = (screenWidth - viewW).coerceAtLeast(0)
        val maxY = (screenHeight - viewH).coerceAtLeast(0)

        val targetX = x.coerceIn(0f, maxX.toFloat())
        val targetY = y.coerceIn(0f, maxY.toFloat())
        lastTargetX = targetX
        lastTargetY = targetY

        // Primer movimiento o salto grande (p. ej. centrado): directo.
        if (!smoothInit ||
            abs(targetX - smoothWinX) > DRAG_SNAP_DISTANCE_PX ||
            abs(targetY - smoothWinY) > DRAG_SNAP_DISTANCE_PX
        ) {
            smoothWinX = targetX
            smoothWinY = targetY
            smoothInit = true
        } else {
            smoothWinX += (targetX - smoothWinX) * DRAG_SMOOTHING
            smoothWinY += (targetY - smoothWinY) * DRAG_SMOOTHING
            // Si ya esta a menos de un pixel, se ajusta exacto.
            if (abs(targetX - smoothWinX) < 0.6f) smoothWinX = targetX
            if (abs(targetY - smoothWinY) < 0.6f) smoothWinY = targetY
        }

        windowX = smoothWinX
        windowY = smoothWinY

        // Evita IPCs redundantes si no cruzo un pixel entero.
        val ix = windowX.toInt()
        val iy = windowY.toInt()
        if (ix == params.x && iy == params.y) {
            view.setWindowPosition(windowX, windowY)
            return
        }
        params.x = ix
        params.y = iy

        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo mover la ventana", e)
        }

        view.setWindowPosition(windowX, windowY)
    }

    /**
     * Re-mide la ventana tras cambiar el tamano de la lente, manteniendo
     * el CENTRO de la lupa donde estaba y sin salirse de la pantalla.
     * (Antes crecia hacia abajo/derecha y podia quedar fuera de pantalla.)
     */
    private fun resizeWindow() {
        val params = layoutParams ?: return
        val view = lensView ?: return

        val newW = view.getTotalWidthPx()
        val newH = view.getTotalHeightPx()
        val oldW = if (lastWindowW > 0) lastWindowW else newW
        val oldH = if (lastWindowH > 0) lastWindowH else newH
        val centerX = windowX + oldW / 2f
        val centerY = windowY + oldH / 2f

        windowX = (centerX - newW / 2f).coerceIn(0f, (screenWidth - newW).coerceAtLeast(0).toFloat())
        windowY = (centerY - newH / 2f).coerceIn(0f, (screenHeight - newH).coerceAtLeast(0).toFloat())
        syncSmoothWithWindow()
        lastWindowW = newW
        lastWindowH = newH

        params.x = windowX.toInt()
        params.y = windowY.toInt()

        try {
            view.requestLayout()
            view.forceLayout()
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo redimensionar la ventana", e)
        }
        view.setWindowPosition(windowX, windowY)
        scheduleCapture(AFTER_DRAG_CAPTURE_MS)
    }

    /**
     * Aplica cambios de tamaño y aumento en caliente, sin reiniciar el
     * servicio. La pantalla de control envía ACTION_UPDATE_SETTINGS cuando
     * el usuario toca los botones mientras la lupa está activa.
     * onStartCommand se ejecuta en el hilo principal: es seguro tocar la vista.
     */
    private fun applyLiveSettings() {
        if (closing) return
        try {
            val prefs = Prefs(this)
            val view = lensView ?: return
            view.lensShape = prefs.lensShape
            view.setLensSize(prefs.lensSize)
            view.zoomFactor = prefs.lensZoom
            // Re-mide la ventana manteniendo el centro donde estaba.
            resizeWindow()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron aplicar los ajustes en caliente", e)
        }
    }

    // ==================================================================
    // Cambios de orientacion / pantalla
    // ==================================================================

    private fun registerDisplayListener() {
        try {
            displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            displayManager?.registerDisplayListener(
                displayListener, mainHandler
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar el listener de pantalla", e)
        }
    }

    private fun unregisterDisplayListener() {
        try {
            displayManager?.unregisterDisplayListener(displayListener)
        } catch (e: Exception) {
            // Ignora.
        }
        displayManager = null
    }

    /** Registra el receptor de apagado/encendido de pantalla. */
    private fun registerScreenReceiver() {
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenReceiver, filter)
            }
            screenReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar el receptor de pantalla", e)
        }
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Ignora.
        }
        screenReceiverRegistered = false
    }

    /**
     * Maneja un cambio de orientacion o de tamano de pantalla.
     *
     * Recrea el VirtualDisplay con el nuevo tamano y recoloca la lente
     * dentro de los limites.
     */
    private fun handleDisplayChange() {
        if (closing) return

        val oldW = screenWidth
        val oldH = screenHeight

        if (!updateScreenMetrics()) return

        // Si el tamano no cambio, no hay nada que hacer.
        if (oldW == screenWidth && oldH == screenHeight) return

        Log.i(TAG, "Cambio de pantalla: ${oldW}x$oldH -> ${screenWidth}x$screenHeight")

        // Recrea el VirtualDisplay con el nuevo tamano.
        val projection = mediaProjection ?: return
        createVirtualDisplay(projection)
        scheduleCapture(AFTER_DRAG_CAPTURE_MS)

        // Actualiza la vista con el nuevo tamano.
        val view = lensView ?: return
        view.setScreenSize(screenWidth, screenHeight)

        // Recoloca la lente dentro de los limites.
        val winW = view.getTotalWidthPx()
        val winH = view.getTotalHeightPx()
        lastWindowW = winW
        lastWindowH = winH
        val maxX = (screenWidth - winW).coerceAtLeast(0)
        val maxY = (screenHeight - winH).coerceAtLeast(0)
        windowX = windowX.coerceIn(0f, maxX.toFloat())
        windowY = windowY.coerceIn(0f, maxY.toFloat())

        layoutParams?.let { params ->
            params.x = windowX.toInt()
            params.y = windowY.toInt()
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo recolocar la lente", e)
            }
        }
        view.setWindowPosition(windowX, windowY)
    }

    // ==================================================================
    // Captura
    // ==================================================================

    /**
     * Copia el frame guardado a un bitmap (se llama en el hilo de captura).
     *
     * La lente esta oculta (alpha=0) asi que el frame NO la contiene.
     * Se escribe en el bitmap "de atras" y luego se entrega a la vista.
     * El frame NO se cierra aqui: sigue guardado como "el mas reciente".
     */
    private fun captureFrame() {
        if (closing) return

        synchronized(frameLock) {
            val image = latestImage ?: return
            try {
                val planes = image.planes
                if (planes.isEmpty()) return

                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth
                val bmpWidth = screenWidth + rowPadding / pixelStride
                val bmpHeight = screenHeight

                val writeA = useBitmapA
                var bmp = if (writeA) bitmapA else bitmapB
                if (bmp == null || bmp.isRecycled ||
                    bmp.width != bmpWidth || bmp.height != bmpHeight
                ) {
                    bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                    if (writeA) bitmapA = bmp else bitmapB = bmp
                }

                buffer.rewind()
                bmp.copyPixelsFromBuffer(buffer)

                // La proxima captura escribe en el otro bitmap.
                useBitmapA = !writeA

                val ready: Bitmap = bmp
                mainHandler.post {
                    if (!closing) {
                        lensView?.setSource(ready)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Frame corrupto ignorado", e)
            }
        }
    }

    /** Programa (o reprograma) la siguiente captura. Hilo principal. */
    private fun scheduleCapture(delayMs: Long) {
        if (closing) return
        mainHandler.removeCallbacks(captureRunnable)
        mainHandler.postDelayed(captureRunnable, delayMs)
    }

    /**
     * Ciclo de captura con ocultacion temporal de la lente:
     *
     *   1. Si el usuario arrastra / la imagen esta congelada / la pantalla
     *      esta apagada: no se hace nada y se reintenta mas tarde.
     *   2. Ocultar la lente (alpha = 0) y anotar la hora.
     *   3. Esperar 50 ms y comprobar que YA llego un frame posterior a la
     *      ocultacion (si no, esperar un poco mas) -> captureAfterHide.
     *   4. Copiar ese frame en el hilo de captura.
     *   5. Mostrar la lente (alpha = 1) y programar la siguiente.
     */
    private fun performCapture() {
        if (closing) return

        val view = lensView
        if (view == null) {
            // Sin vista no hay nada que capturar, pero el ciclo debe seguir.
            scheduleCapture(IDLE_CAPTURE_INTERVAL_MS)
            return
        }

        if (!screenOn || view.isFrozen() || view.isInteracting() || captureHandler == null) {
            scheduleCapture(IDLE_CAPTURE_INTERVAL_MS)
            return
        }

        hideNanos = System.nanoTime()
        freshRetries = 0
        view.alpha = 0f
        mainHandler.postDelayed(captureAfterHide, LENS_HIDE_DELAY_MS)
    }

    // ==================================================================
    // Cierre ordenado
    // ==================================================================

    private fun requestClose(reason: String) {
        if (closing) return
        closing = true
        Log.i(TAG, "Cerrando lupa flotante: $reason")

        mainHandler.removeCallbacks(captureRunnable)
        mainHandler.removeCallbacks(captureAfterHide)
        mainHandler.removeCallbacks(saveZoomRunnable)
        flushZoomSave()
        try {
            captureHandler?.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo detener el bucle de captura", e)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeOverlayThenStop()
        } else {
            mainHandler.post { removeOverlayThenStop() }
        }
    }

    private fun removeOverlayThenStop() {
        val view = lensView
        lensView = null
        if (view != null && viewAttached) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "La vista ya estaba quitada", e)
            }
        }
        viewAttached = false
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        try {
            stopSelf()
        } catch (e: Exception) {
            Log.w(TAG, "Error al detener el servicio", e)
        }
    }

    // ==================================================================
    // Limpieza
    // ==================================================================

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // La orientacion cambio: recoloca la lente.
        mainHandler.post { handleDisplayChange() }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error en super.onDestroy", e)
        }

        closing = true
        isRunning = false

        mainHandler.removeCallbacks(captureRunnable)
        mainHandler.removeCallbacks(captureAfterHide)
        mainHandler.removeCallbacks(saveZoomRunnable)
        flushZoomSave()
        unregisterDisplayListener()
        unregisterScreenReceiver()

        try {
            captureHandler?.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.w(TAG, "Error al limpiar el handler", e)
        }
        try {
            captureThread?.quitSafely()
        } catch (e: Exception) {
            Log.w(TAG, "Error al cerrar el hilo", e)
        }
        captureThread = null
        captureHandler = null

        try {
            val view = lensView
            lensView = null
            if (view != null && viewAttached) {
                try {
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    Log.w(TAG, "La vista ya estaba quitada", e)
                }
            }
            viewAttached = false
        } catch (e: Exception) {
            Log.w(TAG, "Error al quitar la vista", e)
        }

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error al liberar VirtualDisplay", e)
        }
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
        } catch (e: Exception) {
            // Ignora.
        }
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error al detener la proyeccion", e)
        }
        synchronized(frameLock) {
            try {
                latestImage?.close()
            } catch (e: Exception) {
                // Ignora.
            }
            latestImage = null
        }
        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error al cerrar ImageReader", e)
        }

        virtualDisplay = null
        mediaProjection = null
        imageReader = null

        try {
            bitmapA?.recycle()
            bitmapB?.recycle()
        } catch (e: Exception) {
            // Ignora.
        }
        bitmapA = null
        bitmapB = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error al quitar la notificacion", e)
        }
    }
}
