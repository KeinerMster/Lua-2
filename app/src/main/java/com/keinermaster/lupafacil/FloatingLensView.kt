package com.keinermaster.lupafacil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Lupa flotante (v8) — COMPATIBILIDAD UNIVERSAL + ACCESIBILIDAD.
 *
 * ==================================================================
 * MEJORAS DE ESTA VERSION
 * ==================================================================
 *
 * COMPATIBILIDAD ENTRE DISPOSITIVOS
 * ---------------------------------
 * 1. TAMANOS ADAPTATIVOS: los tamanos de la lente se calculan como
 *    fraccion del lado MENOR de la pantalla, no en dp fijos. Asi la
 *    lente es proporcionalmente correcta en un movil de 5" y en una
 *    tablet de 10".
 *
 * 2. ORIENTACION: la vista se re-mide al cambiar la orientacion
 *    (onSizeChanged) y el servicio actualiza el tamano de pantalla.
 *
 * 3. NOTCH / CUTOUT: la posicion inicial de la lente se calcula dentro
 *    del area segura (insets), no pegada al borde.
 *
 * 4. BARRA DE NAVEGACION: idem, se respeta el area segura inferior.
 *
 * 5. RATIO DE PANTALLA: el mapeo de coordenadas usa el tamano REAL de
 *    la pantalla (que el servicio comunica), asi funciona en 16:9,
 *    18:9, 20:9 y 21:9.
 *
 * 6. ROW PADDING: se compensa con la escala entre bitmap y pantalla.
 *
 * 7. ZOOM DEL SISTEMA: los tamanos se basan en la densidad real, asi
 *    que respetan el "tamano de pantalla" de los ajustes.
 *
 * ACCESIBILIDAD PARA ADULTOS MAYORES
 * ----------------------------------
 * 8. BOTONES MAS GRANDES: radio de 26dp (antes 20dp). Area de toque
 *    efectiva de 52dp, por encima del minimo recomendado (48dp).
 *
 * 9. LENTE MAS GRANDE POR DEFECTO: 140dp (antes 110dp).
 *
 * 10. ZOOM MAS ALTO POR DEFECTO: 3.0x (antes 2.5x), para leer texto
 *     pequeno de etiquetas y frascos de medicina.
 *
 * 11. VIBRACION AL TOCAR: feedback tactil en cada boton.
 *
 * 12. BOTON DE CENTRAR: si la lente se pierde, un toque la devuelve al
 *     centro de la pantalla.
 *
 * 13. CONTRASTE ALTO: bordes gruesos, colores solidos, sombra marcada.
 *
 * 14. AREA DE ARRASTRE GRANDE: toda la superficie de la lente.
 *
 * ==================================================================
 * CONTROLES
 * ==================================================================
 *  - Arrastrar la lente: moverla por la pantalla.
 *  - Botones − / +: menos / mas aumento.
 *  - Boton ✕: cerrar la lupa.
 *  - Boton II / ▶: pausar (congelar) / reanudar la imagen.
 *  - Boton de mira (+): centrar la lente en la pantalla.
 *  - Pinza con 2 dedos: zoom rapido.
 *  - Doble toque: cambiar el tamano. (Un toque suelto ya no congela.)
 */
class FloatingLensView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // ------------------------------------------------------------------
        // Tamanos de la lente: RADIO en dp. Se recortan automaticamente para
        // que la ventana (lente + botones) nunca sea mayor que ~90% del lado
        // menor de la pantalla; asi "Grande" cabe en cualquier movil.
        // ------------------------------------------------------------------
        /**
         * Tamaños de la lupa (RADIO en dp). Escala compacta pensada para
         * LEER: un círculo pequeño que se arrastra sobre el texto, sin
         * tapar toda la pantalla. (Antes: 70/95/125, demasiado grande.)
         */
        const val SIZE_SMALL = 55f
        const val SIZE_MEDIUM = 75f
        const val SIZE_LARGE = 100f

        /** Formas de la lupa. */
        const val SHAPE_CIRCLE = 0
        const val SHAPE_BAR = 1

        /**
         * Tamaño de la barra de lectura, en dp: franja horizontal para
         * leer línea por línea. Se recorta si la pantalla es pequeña.
         */
        private const val BAR_WIDTH_DP = 320f
        private const val BAR_HEIGHT_DP = 120f
        private const val BAR_CORNER_DP = 28f

        /** Radio minimo de la lente, en dp. */
        private const val MIN_RADIUS_DP = 48f

        /** La ventana completa no pasa de esta fraccion del lado menor. */
        private const val MAX_WINDOW_RATIO = 0.90f

        const val MIN_ZOOM = 1.5f
        const val MAX_ZOOM = 12f

        /** Zoom por defecto: alto, para leer texto pequeno. */
        const val DEFAULT_ZOOM = 3.0f

        /**
         * Radio de los botones, en dp. 18 dp = 36 dp de diámetro: más
         * compactos para no tapar el contenido ampliado (los botones
         * grandes de la pantalla principal siguen siendo enormes).
         */
        private const val BUTTON_RADIUS_DP = 18f

        /** Separacion entre la lente y los botones, en dp. */
        private const val BUTTON_GAP_DP = 4f

        /**
         * Margen alrededor de la lente = separacion + diametro del boton + 4.
         * Con botones de 36 dp, 44 dp evita que queden cortados por el borde
         * de la ventana.
         */
        private const val BUTTON_MARGIN_DP = 44f

        private const val DOUBLE_TAP_TIMEOUT_MS = 300L
        /** Tolerancia de toque, en dp (se multiplica por la densidad al usar). */
        private const val TAP_SLOP_PX = 28f
        private const val CLOSE_DELAY_MS = 120L

        /** Duracion de la vibracion al tocar un boton, en ms. */
        private const val VIBRATE_MS = 40L
    }

    // ------------------------------------------------------------------
    // Callbacks hacia el servicio
    // ------------------------------------------------------------------

    var onZoomChanged: ((Float) -> Unit)? = null
    var onSizeChanged: ((Float) -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var onFreezeChanged: ((Boolean) -> Unit)? = null

    /**
     * Se llama al arrastrar con la POSICION ABSOLUTA (x, y) donde debe ir
     * la ventana en la pantalla. El servicio la limita a los bordes.
     */
    var onPositionChanged: ((Float, Float) -> Unit)? = null

    /** Se llama cuando el usuario suelta la lupa (arrastre o pinza). */
    var onInteractionEnded: (() -> Unit)? = null

    /** Se llama cuando el usuario toca la pantalla FUERA de la lupa. */
    var onOutsideTouch: (() -> Unit)? = null

    /** Se llama cuando el usuario pide centrar la lente. */
    var onCenterRequested: (() -> Unit)? = null

    /**
     * Si es true, arrastrar mueve el CENTRO de la lente dentro de la vista
     * (la lupa amplia lo que haya
     * debajo de donde la pongas. Si es false, el arrastre mueve la ventana
     * completa (servicio flotante) vía onPositionChanged.
     */
    var dragMovesLens = false

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    /**
     * Densidad de pantalla.
     *
     * IMPORTANTE: debe declararse ANTES que cualquier propiedad que la use
     * (radius, buttonMargin, pinceles...). En Kotlin las propiedades se
     * inicializan en orden de declaracion.
     */
    private val density = resources.displayMetrics.density

    private var source: Bitmap? = null
    private var shader: BitmapShader? = null
    private var frozen = false

    /** Posicion de la lente dentro de la vista (en px). */
    private var lensX = 0f
    private var lensY = 0f

    /** Tamano del lado menor de la pantalla (para calcular tamanos). */
    private var screenMinSide = 0f

    var zoomFactor: Float = DEFAULT_ZOOM
        set(value) {
            val clamped = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
            if (clamped == field) return
            field = clamped
            refreshZoomLabel()
            updateShaderMatrix()
            invalidate()
            onZoomChanged?.invoke(clamped)
        }

    /** Radio de la lente en px (modo círculo). */
    var radius: Float = SIZE_SMALL * density
        set(value) {
            field = clampRadiusPx(value)
            clampLensCenter()
            requestLayout()
            invalidate()
        }

    /**
     * Forma de la lupa: SHAPE_CIRCLE (círculo) o SHAPE_BAR (barra de
     * lectura horizontal). Al cambiar se re-mide la ventana.
     */
    var lensShape: Int = SHAPE_CIRCLE
        set(value) {
            val v = if (value == SHAPE_BAR) SHAPE_BAR else SHAPE_CIRCLE
            if (field == v) return
            field = v
            adaptBarToScreen()
            clampLensCenter()
            requestLayout()
            invalidate()
        }

    /** Mitad del ancho/alto de la barra, en px. */
    private var barHalfW = 0f
    private var barHalfH = 0f

    /** Rectángulo reutilizable de la barra. */
    private val barRect = RectF()

    /** Recalcula el tamaño de la barra según la pantalla. */
    private fun adaptBarToScreen() {
        if (screenW > 0f && screenH > 0f) {
            barHalfW = minOf(BAR_WIDTH_DP * density, screenW * 0.94f) / 2f
            barHalfH = minOf(BAR_HEIGHT_DP * density, screenH * 0.4f) / 2f
        } else {
            barHalfW = BAR_WIDTH_DP * density / 2f
            barHalfH = BAR_HEIGHT_DP * density / 2f
        }
    }

    /** Radio maximo que cabe en la pantalla (lente + botones <= 90% del lado menor). */
    private fun maxRadiusPx(): Float {
        val minRadius = MIN_RADIUS_DP * density
        if (screenMinSide <= 0f) return Float.MAX_VALUE
        return (screenMinSide * MAX_WINDOW_RATIO / 2f - buttonMargin).coerceAtLeast(minRadius)
    }

    private fun clampRadiusPx(px: Float): Float =
        px.coerceIn(MIN_RADIUS_DP * density, maxRadiusPx())

    var showZoomLabel: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var colorFilter: ColorFilter? = null
        set(value) {
            field = value
            bitmapPaint.colorFilter = value
            invalidate()
        }

    private val mainHandler = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------
    // Botones auto-ocultables: la lupa se ve como SOLO el círculo; al
    // tocarla aparecen los botones y se esconden solos tras unos segundos
    // sin interactuar. Así no quitan espacio para leer.
    // ------------------------------------------------------------------

    /** Tiempo sin interactuar antes de esconder los botones, en ms. */
    private val CONTROLS_HIDE_DELAY_MS = 4000L

    /** true = los botones están visibles (se dibujan y responden). */
    private var controlsVisible = true

    private val hideControlsRunnable = Runnable {
        controlsVisible = false
        invalidate()
    }

    /**
     * Muestra los botones y (re)inicia el temporizador para esconderlos.
     * Se llama al tocar la lupa.
     */
    fun showControls() {
        controlsVisible = true
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS)
        invalidate()
    }

    /** Si los botones están visibles, reinicia su temporizador. */
    private fun pokeControls() {
        if (!controlsVisible) return
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Al aparecer, los botones se muestran y luego se esconden solos.
        pokeControls()
    }

    // ------------------------------------------------------------------
    // Vibracion (feedback tactil)
    // ------------------------------------------------------------------

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun vibrate() {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, 120))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(VIBRATE_MS)
            }
        } catch (e: Exception) {
            // La vibracion es opcional: si falla, no pasa nada.
        }
    }

    // ------------------------------------------------------------------
    // Pinceles
    // ------------------------------------------------------------------

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 10f * density
        setShadowLayer(12f * density, 0f, 4f * density, Color.argb(180, 0, 0, 0))
    }

    /**
     * Borde de la lupa: anillo rojo fino y elegante (2.5 dp).
     * Mini y refinado, visible sobre cualquier contenido sin taparlo.
     */
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD400")
        textSize = 17f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(5f * density, 0f, 1f * density, Color.BLACK)
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val buttonFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 20, 20, 20)
        style = Paint.Style.FILL
    }

    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD400")
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    private val iconYellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD400")
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val iconWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val iconWhiteThinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val iconWhiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val iconPath = Path()

    private val closeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 200, 40, 40)
        style = Paint.Style.FILL
    }

    private val freezeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 30, 90, 160)
        style = Paint.Style.FILL
    }

    private val centerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 40, 130, 70)
        style = Paint.Style.FILL
    }

    // ------------------------------------------------------------------
    // Objetos reutilizables (evitan asignaciones en el hilo principal)
    // ------------------------------------------------------------------
    private val clipPath = Path()
    private val destRect = RectF()
    private val shaderMatrix = Matrix()
    private val mapPts = FloatArray(2)

    /** Texto del indicador de aumento, precalculado (no se formatea en onDraw). */
    private var zoomLabelText = ""
    private var zoomLabelWidth = 0f

    /** Recalcula el texto del indicador (llamar al cambiar zoom o pausa). */
    private fun refreshZoomLabel() {
        zoomLabelText = if (frozen) {
            "II ${"%.1f".format(zoomFactor)}x"
        } else {
            "${"%.1f".format(zoomFactor)}x"
        }
        zoomLabelWidth = labelPaint.measureText(zoomLabelText)
    }

    private val buttonRadius = BUTTON_RADIUS_DP * density
    private val buttonGap = BUTTON_GAP_DP * density
    private val buttonMargin = BUTTON_MARGIN_DP * density

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = false
        refreshZoomLabel()
    }

    // ------------------------------------------------------------------
    // Medicion: la vista mide EXACTAMENTE lo que ocupa la lente + botones
    // ------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // En el servicio (WRAP_CONTENT) mide lo que ocupa la lente/barra.
        setMeasuredDimension(
            resolveSize(getTotalWidthPx(), widthMeasureSpec),
            resolveSize(getTotalHeightPx(), heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (lensShape == SHAPE_BAR) {
            // La barra va centrada horizontalmente; verticalmente se deja
            // espacio debajo para la fila de botones.
            lensX = w / 2f
            lensY = (h - barButtonRowHeightPx()) / 2f
        } else {
            // La lente se centra dentro de la vista.
            lensX = w / 2f
            lensY = h / 2f
        }
        updateShaderMatrix()
    }

    // ------------------------------------------------------------------
    // API publica
    // ------------------------------------------------------------------

    fun setLensSize(sizeDp: Float) {
        radius = sizeDp * density
    }

    fun getLensSizeDp(): Float = radius / density

    /**
     * Ancho total que ocupa la vista (lente/barra + márgenes), en px.
     * El servicio lo usa para colocar y limitar la ventana.
     */
    fun getTotalWidthPx(): Int = if (lensShape == SHAPE_BAR) {
        (barHalfW * 2f + buttonMargin * 2f).toInt()
    } else {
        ((radius + buttonMargin) * 2f).toInt()
    }

    /**
     * Alto total que ocupa la vista, en px. En modo barra incluye la
     * fila de botones que aparece debajo al tocarla.
     */
    fun getTotalHeightPx(): Int = if (lensShape == SHAPE_BAR) {
        (barHalfH * 2f + buttonMargin + barButtonRowHeightPx()).toInt()
    } else {
        ((radius + buttonMargin) * 2f).toInt()
    }

    /** Alto de la fila de botones bajo la barra (con separaciones), en px. */
    private fun barButtonRowHeightPx(): Float =
        buttonGap + buttonRadius * 2f + buttonGap

    fun isFrozen(): Boolean = frozen

    /**
     * El servicio informa del tamano real de la pantalla: se guarda el
     * lado menor y se recorta el radio para que la lente SIEMPRE quepa.
     */
    fun adaptToScreen(widthPx: Int, heightPx: Int) {
        screenMinSide = minOf(widthPx, heightPx).toFloat()
        radius = clampRadiusPx(radius)
        updateShaderMatrix()
        invalidate()
    }

    /**
     * Actualiza el frame que muestra la lente.
     *
     * Se limita la tasa de refresco para no saturar el hilo principal.
     */
    fun setSource(bitmap: Bitmap?) {
        if (frozen) return
        if (bitmap == null || bitmap.isRecycled) return
        // Mismo frame que ya se muestra: no recrear el shader ni redibujar.
        // (El controlador limita los frames a ~15 fps, pero varios ticks
        // pueden recibir el mismo bitmap.)
        if (bitmap === source) return

        source = bitmap
        // Shader nuevo en cada captura: evita que la GPU reutilice la
        // textura vieja del bitmap.
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        bitmapPaint.shader = shader
        updateShaderMatrix()
        invalidate()
    }

    /** true mientras el usuario arrastra o hace pinza (no se captura). */
    fun isInteracting(): Boolean = dragging || pinching

    fun setFrozen(value: Boolean) {
        if (frozen == value) return
        frozen = value
        refreshZoomLabel()
        invalidate()
        onFreezeChanged?.invoke(value)
    }

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = lensX
        val cy = lensY

        if (lensShape == SHAPE_BAR) {
            drawBar(canvas, cx, cy)
            return
        }

        // 1. Sombra exterior.
        canvas.drawCircle(cx, cy, radius, shadowPaint)

        // 2. Recorte circular.
        clipPath.rewind()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // 3. Contenido ampliado.
        if (shader != null && source != null) {
            canvas.drawCircle(cx, cy, radius, bitmapPaint)
        } else {
            canvas.drawColor(Color.argb(180, 0, 0, 0))
        }

        canvas.restore()

        // 4. Anillo rojo fino y elegante.
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // 5. Indicador de aumento.
        if (showZoomLabel) {
            drawZoomLabel(canvas, cx, cy)
        }

        // 6. Botones (solo si están visibles; se esconden solos).
        if (controlsVisible) {
            drawButtons(canvas, cx, cy)
        }
    }

    /**
     * Dibuja la barra de lectura: franja horizontal redondeada con el
     * contenido ampliado. Usa la misma matriz del shader (centrada en
     * lensX/lensY), solo cambia la forma del recorte.
     */
    private fun drawBar(canvas: Canvas, cx: Float, cy: Float) {
        val corner = BAR_CORNER_DP * density
        barRect.set(cx - barHalfW, cy - barHalfH, cx + barHalfW, cy + barHalfH)

        // 1. Sombra exterior.
        canvas.drawRoundRect(barRect, corner, corner, shadowPaint)

        // 2. Recorte redondeado.
        clipPath.rewind()
        clipPath.addRoundRect(barRect, corner, corner, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // 3. Contenido ampliado.
        if (shader != null && source != null) {
            canvas.drawRoundRect(barRect, corner, corner, bitmapPaint)
        } else {
            canvas.drawColor(Color.argb(180, 0, 0, 0))
        }

        canvas.restore()

        // 4. Borde de alto contraste.
        canvas.drawRoundRect(barRect, corner, corner, borderPaint)

        // 5. Indicador de aumento (abajo dentro de la barra).
        if (showZoomLabel) {
            drawBarZoomLabel(canvas, cx, cy)
        }

        // 6. Botones en fila debajo (solo si están visibles).
        if (controlsVisible) {
            drawBarButtons(canvas, cx, cy)
        }
    }

    /** Etiqueta de aumento dentro de la barra, abajo centrada. */
    private fun drawBarZoomLabel(canvas: Canvas, cx: Float, cy: Float) {
        val text = zoomLabelText
        val textWidth = zoomLabelWidth
        val padding = 8f * density
        val labelY = cy + barHalfH - 16f * density

        destRect.set(
            cx - textWidth / 2f - padding,
            labelY - 14f * density,
            cx + textWidth / 2f + padding,
            labelY + 6f * density
        )
        canvas.drawRoundRect(destRect, 10f * density, 10f * density, labelBgPaint)
        canvas.drawText(text, cx, labelY, labelPaint)
    }

    private fun drawZoomLabel(canvas: Canvas, cx: Float, cy: Float) {
        val text = zoomLabelText
        val textWidth = zoomLabelWidth
        val padding = 8f * density
        val labelY = cy + radius - 22f * density

        destRect.set(
            cx - textWidth / 2f - padding,
            labelY - 17f * density,
            cx + textWidth / 2f + padding,
            labelY + 8f * density
        )
        canvas.drawRoundRect(destRect, 8f * density, 8f * density, labelBgPaint)
        canvas.drawText(text, cx, labelY, labelPaint)
    }

    /** Centro de un boton: direccion (-1,0)=izq, (1,0)=der, (0,-1)=arriba, (0,1)=abajo. */
    private fun buttonCenter(cx: Float, cy: Float, dx: Float, dy: Float): Pair<Float, Float> {
        val dist = radius + buttonGap + buttonRadius
        return Pair(cx + dx * dist, cy + dy * dist)
    }

    /**
     * Dibuja los 5 botones. En modo círculo van alrededor de la lente; en
     * modo barra, en una fila debajo de ella. Los iconos se DIBUJAN con
     * lineas (no con caracteres de fuente): glifos como "⌖" salen como un
     * cuadrado vacio en muchos telefonos y el boton quedaba sin simbolo.
     */
    private fun drawButtons(canvas: Canvas, cx: Float, cy: Float) {
        if (lensShape == SHAPE_BAR) {
            drawBarButtons(canvas, cx, cy)
            return
        }
        // "−" a la izquierda.
        val minus = buttonCenter(cx, cy, -1f, 0f)
        drawButtonAt(canvas, minus.first, minus.second, LensButton.ZOOM_OUT)

        // "+" a la derecha.
        val plus = buttonCenter(cx, cy, 1f, 0f)
        drawButtonAt(canvas, plus.first, plus.second, LensButton.ZOOM_IN)

        // "✕" arriba (cerrar).
        val close = buttonCenter(cx, cy, 0f, -1f)
        drawButtonAt(canvas, close.first, close.second, LensButton.CLOSE)

        // Pausa (II) / reanudar (▶) abajo.
        val freeze = buttonCenter(cx, cy, 0f, 1f)
        drawButtonAt(canvas, freeze.first, freeze.second, LensButton.FREEZE)

        // Centrar (mira) en la esquina inferior derecha.
        val center = buttonCenter(cx, cy, 0.72f, 0.72f)
        drawButtonAt(canvas, center.first, center.second, LensButton.CENTER)
    }

    /** Dibuja un botón individual en (bx, by). */
    private fun drawButtonAt(canvas: Canvas, bx: Float, by: Float, button: LensButton) {
        val r = buttonRadius
        val arm = r * 0.42f
        when (button) {
            LensButton.ZOOM_OUT -> {
                canvas.drawCircle(bx, by, r, buttonFillPaint)
                canvas.drawCircle(bx, by, r, buttonBorderPaint)
                canvas.drawLine(bx - arm, by, bx + arm, by, iconYellowPaint)
            }
            LensButton.ZOOM_IN -> {
                canvas.drawCircle(bx, by, r, buttonFillPaint)
                canvas.drawCircle(bx, by, r, buttonBorderPaint)
                canvas.drawLine(bx - arm, by, bx + arm, by, iconYellowPaint)
                canvas.drawLine(bx, by - arm, bx, by + arm, iconYellowPaint)
            }
            LensButton.CLOSE -> {
                canvas.drawCircle(bx, by, r, closeFillPaint)
                val d = arm * 0.85f
                canvas.drawLine(bx - d, by - d, bx + d, by + d, iconWhitePaint)
                canvas.drawLine(bx - d, by + d, bx + d, by - d, iconWhitePaint)
            }
            LensButton.FREEZE -> {
                canvas.drawCircle(bx, by, r, freezeFillPaint)
                if (frozen) {
                    iconPath.rewind()
                    iconPath.moveTo(bx - arm * 0.6f, by - arm)
                    iconPath.lineTo(bx - arm * 0.6f, by + arm)
                    iconPath.lineTo(bx + arm * 0.9f, by)
                    iconPath.close()
                    canvas.drawPath(iconPath, iconWhiteFillPaint)
                } else {
                    val gap = arm * 0.45f
                    canvas.drawLine(bx - gap, by - arm, bx - gap, by + arm, iconWhitePaint)
                    canvas.drawLine(bx + gap, by - arm, bx + gap, by + arm, iconWhitePaint)
                }
            }
            LensButton.CENTER -> {
                canvas.drawCircle(bx, by, r, centerFillPaint)
                canvas.drawCircle(bx, by, arm * 0.55f, iconWhiteThinPaint)
                val reach = arm * 1.15f
                canvas.drawLine(bx - reach, by, bx + reach, by, iconWhiteThinPaint)
                canvas.drawLine(bx, by - reach, bx, by + reach, iconWhiteThinPaint)
            }
        }
    }

    /** Orden de los botones en la fila bajo la barra. */
    private val barButtonOrder = listOf(
        LensButton.ZOOM_OUT,
        LensButton.ZOOM_IN,
        LensButton.FREEZE,
        LensButton.CENTER,
        LensButton.CLOSE
    )

    /** Centro del botón i-ésimo (0..4) en la fila bajo la barra. */
    private fun barButtonCenter(index: Int, cx: Float, cy: Float): Pair<Float, Float> {
        val spacing = buttonRadius * 2f + buttonGap * 2f
        val y = cy + barHalfH + buttonGap + buttonRadius + buttonGap
        val startX = cx - spacing * 2f
        return Pair(startX + index * spacing, y)
    }

    /** Dibuja los 5 botones en fila debajo de la barra. */
    private fun drawBarButtons(canvas: Canvas, cx: Float, cy: Float) {
        for (i in barButtonOrder.indices) {
            val (bx, by) = barButtonCenter(i, cx, cy)
            drawButtonAt(canvas, bx, by, barButtonOrder[i])
        }
    }

    /**
     * Matriz del shader: muestra la zona del bitmap que hay DEBAJO del
     * centro de la lente, ampliada por zoomFactor.
     *
     * El bitmap NO contiene la lente (el servicio la oculta al capturar).
     * Las coordenadas del bitmap se obtienen con sourceMatrix.
     */
    private fun updateShaderMatrix() {
        val bmp = source ?: return
        val sh = shader ?: return
        if (bmp.isRecycled || width == 0 || height == 0) return

        // Coordenadas del bitmap bajo el centro de la lente, usando la
        // transformación vigente (traslación en el servicio, escalado del
        // Antes se asumía 1:1 siempre y la lupa ampliaba la zona equivocada.
        mapPts[0] = lensX
        mapPts[1] = lensY
        sourceMatrix.mapPoints(mapPts)
        val bmpX = mapPts[0]
        val bmpY = mapPts[1]

        shaderMatrix.reset()
        shaderMatrix.postScale(zoomFactor, zoomFactor)
        shaderMatrix.postTranslate(
            lensX - bmpX * zoomFactor,
            lensY - bmpY * zoomFactor
        )
        sh.setLocalMatrix(shaderMatrix)
    }

    /** Posicion de la ventana en la pantalla (la fija el servicio). */
    private var windowX = 0f
    private var windowY = 0f

    /**
     * Matriz que mapea coordenadas LOCALES de la vista (p. ej. el centro de
     * la lente) a coordenadas del bitmap de origen.
     *
     * En el servicio flotante el bitmap es la captura de pantalla (1:1 con
     * la vista), asi que la matriz es identidad por defecto. Se puede
     * personalizar con setSourceTransform() si alguna vez difieren.
     */
    private val sourceMatrix = Matrix()

    /**
     * Fija una transformación personalizada de vista -> bitmap.
     */
    fun setSourceTransform(matrix: Matrix) {
        sourceMatrix.set(matrix)
        updateShaderMatrix()
        invalidate()
    }

    /** Tamano REAL de la pantalla, en px (lo comunica el servicio). */
    private var screenW = 0f
    private var screenH = 0f

    /**
     * El servicio informa del tamano real de la pantalla.
     *
     * IMPORTANTE: NO se puede usar resources.displayMetrics para esto,
     * porque esta vista vive dentro de una ventana PEQUENA (WRAP_CONTENT)
     * y displayMetrics devolveria el tamano de la VENTANA, no de la
     * pantalla.
     */
    fun setScreenSize(widthPx: Int, heightPx: Int) {
        screenW = widthPx.toFloat()
        screenH = heightPx.toFloat()
        adaptToScreen(widthPx, heightPx)
        adaptBarToScreen()
        // Por si la barra se recortó (pantalla pequeña), re-medir.
        requestLayout()
    }

    /** El servicio informa de donde esta la ventana en la pantalla. */
    fun setWindowPosition(x: Float, y: Float) {
        windowX = x
        windowY = y
        // El bitmap del servicio es 1:1 con la pantalla.
        sourceMatrix.setTranslate(x, y)
        updateShaderMatrix()
        invalidate()
    }

    /**
     * Devuelve la lente al centro de la vista (uso embebido).
     * En el servicio flotante el centrado lo hace el servicio moviendo
     * la ventana (ver FloatingLensService.centerLens).
     */
    fun centerLens() {
        if (width > 0 && height > 0) {
            lensX = width / 2f
            lensY = height / 2f
            updateShaderMatrix()
            invalidate()
        }
    }

    /**
     * Mantiene el centro de la lente dentro de la vista para que ni la
     * lente ni los botones queden cortados (p. ej. tras cambiar el tamaño
     * con doble toque cuando la lente estaba cerca del borde).
     */
    private fun clampLensCenter() {
        if (width <= 0 || height <= 0) return
        if (lensShape == SHAPE_BAR) {
            // La barra más la fila de botones no se salen de la vista.
            val marginX = barHalfW + buttonMargin
            val marginTop = barHalfH + buttonMargin
            val marginBottom = barHalfH + barButtonRowHeightPx()
            lensX = lensX.coerceIn(marginX, (width - marginX).coerceAtLeast(marginX))
            lensY = lensY.coerceIn(marginTop, (height - marginBottom).coerceAtLeast(marginTop))
            return
        }
        val margin = radius + buttonMargin
        lensX = lensX.coerceIn(margin, (width - margin).coerceAtLeast(margin))
        lensY = lensY.coerceIn(margin, (height - margin).coerceAtLeast(margin))
    }

    // ------------------------------------------------------------------
    // Interaccion tactil
    // ------------------------------------------------------------------

    private var dragging = false
    private var dragStartWinX = 0f
    private var dragStartWinY = 0f
    private var lensStartX = 0f
    private var lensStartY = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var downTime = 0L

    private var pinching = false
    private var pinchStartDistance = 0f
    private var pinchStartZoom = 1f

    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private var closeRequested = false

    private val closeRunnable = Runnable {
        // Se restablece el seguro: la vista puede reutilizarse (p. ej. la
        // la lente se oculta y se vuelve a mostrar).
        closeRequested = false
        alpha = 1f
        onCloseRequested?.invoke()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Toque en OTRA parte de la pantalla (el usuario usa la app de
        // abajo): se avisa para refrescar la foto poco despues.
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch?.invoke()
            return false
        }

        val cx = lensX
        val cy = lensY

        // --- Pinza con dos dedos ---
        if (event.pointerCount >= 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    val distance = hypot(dx, dy)
                    if (!pinching) {
                        pinching = true
                        dragging = false
                        pinchStartDistance = distance
                        pinchStartZoom = zoomFactor
                    } else if (pinchStartDistance > 0f) {
                        zoomFactor = pinchStartZoom * (distance / pinchStartDistance)
                    }
                    pokeControls()
                    return true
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                    pinching = false
                    onInteractionEnded?.invoke()
                    return true
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 1. Botones.
                val button = hitTestButton(event.x, event.y, cx, cy)
                if (button != null) {
                    vibrate()
                    handleButton(button)
                    return true
                }

                // 2. Arrastre desde dentro de la lente/barra.
                val dx = event.x - cx
                val dy = event.y - cy
                val insideLens = if (lensShape == SHAPE_BAR) {
                    abs(dx) <= barHalfW && abs(dy) <= barHalfH
                } else {
                    dx * dx + dy * dy <= radius * radius
                }
                if (insideLens) {
                    dragging = true
                    // Se guarda donde estaba la ventana y donde tocó el dedo
                    // EN LA PANTALLA (rawX/rawY). Usar event.x/y aquí era el
                    // error: esas coordenadas son relativas a la ventana, que
                    // se mueve con el dedo, y el arrastre salía lento y
                    // tembloroso.
                    dragStartWinX = windowX
                    dragStartWinY = windowY
                    lensStartX = lensX
                    lensStartY = lensY
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downTime = System.currentTimeMillis()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    pokeControls()
                    if (dragMovesLens) {
                        // Mueve el centro de la lente dentro de la vista,
                        // sin que la lente ni los botones se salgan.
                        val margin = radius + buttonMargin
                        val minX = margin
                        val maxX = (width - margin).coerceAtLeast(minX)
                        val minY = margin
                        val maxY = (height - margin).coerceAtLeast(minY)
                        lensX = (lensStartX + (event.rawX - downRawX)).coerceIn(minX, maxX)
                        lensY = (lensStartY + (event.rawY - downRawY)).coerceIn(minY, maxY)
                        updateShaderMatrix()
                        invalidate()
                    } else {
                        onPositionChanged?.invoke(
                            dragStartWinX + (event.rawX - downRawX),
                            dragStartWinY + (event.rawY - downRawY)
                        )
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    val elapsed = System.currentTimeMillis() - downTime
                    val moved = abs(event.rawX - downRawX) + abs(event.rawY - downRawY)
                    // TAP_SLOP en dp (multiplicado por la densidad): en
                    // pantallas densas 28 px son muy pocos y los toques de
                    // personas mayores no se reconocían.
                    if (elapsed < 250 && moved < TAP_SLOP_PX * density) {
                        handleTap(event.x, event.y)
                    }
                    onInteractionEnded?.invoke()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                val wasActive = dragging || pinching
                dragging = false
                pinching = false
                if (wasActive) onInteractionEnded?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Un toque suelto YA NO congela la imagen: se congelaba sin querer al
     * soltar la lupa y parecía que "no funcionaba". Congelar se hace solo
     * con el botón de pausa. El doble toque cambia el tamaño.
     */
    private fun handleTap(tapX: Float, tapY: Float) {
        val now = System.currentTimeMillis()
        val slop = TAP_SLOP_PX * 2f * density
        val isDoubleTap = (now - lastTapTime) < DOUBLE_TAP_TIMEOUT_MS &&
                abs(tapX - lastTapX) < slop &&
                abs(tapY - lastTapY) < slop

        if (isDoubleTap) {
            lastTapTime = 0L
            cycleSize()
        } else {
            lastTapTime = now
            lastTapX = tapX
            lastTapY = tapY
            // Un toque muestra los botones (si estaban escondidos).
            showControls()
        }
    }

    /** Pequena -> mediana -> grande -> pequena (solo en modo círculo). */
    private fun cycleSize() {
        // En modo barra el tamaño es fijo y ya es el ideal para leer.
        if (lensShape == SHAPE_BAR) return
        val current = getLensSizeDp()
        val candidates = listOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE)
            .map { clampRadiusPx(it * density) / density }
            .distinctBy { it.toInt() }
        val next = candidates.firstOrNull { it > current + 2f } ?: candidates.first()
        setLensSize(next)
        onSizeChanged?.invoke(next)
    }

    private enum class LensButton { ZOOM_OUT, ZOOM_IN, CLOSE, FREEZE, CENTER }

    private fun hitTestButton(tapX: Float, tapY: Float, cx: Float, cy: Float): LensButton? {
        // Ocultos = no existen para el tacto (el toque muestra los botones).
        if (!controlsVisible) return null

        if (lensShape == SHAPE_BAR) {
            for (i in barButtonOrder.indices) {
                val (bx, by) = barButtonCenter(i, cx, cy)
                if (hypot(tapX - bx, tapY - by) <= buttonRadius) {
                    return barButtonOrder[i]
                }
            }
            return null
        }

        val r = buttonRadius

        val minus = buttonCenter(cx, cy, -1f, 0f)
        if (hypot(tapX - minus.first, tapY - minus.second) <= r) return LensButton.ZOOM_OUT

        val plus = buttonCenter(cx, cy, 1f, 0f)
        if (hypot(tapX - plus.first, tapY - plus.second) <= r) return LensButton.ZOOM_IN

        val close = buttonCenter(cx, cy, 0f, -1f)
        if (hypot(tapX - close.first, tapY - close.second) <= r) return LensButton.CLOSE

        val freeze = buttonCenter(cx, cy, 0f, 1f)
        if (hypot(tapX - freeze.first, tapY - freeze.second) <= r) return LensButton.FREEZE

        val center = buttonCenter(cx, cy, 0.72f, 0.72f)
        if (hypot(tapX - center.first, tapY - center.second) <= r) {
            return LensButton.CENTER
        }

        return null
    }

    private fun handleButton(button: LensButton) {
        when (button) {
            LensButton.ZOOM_OUT -> zoomFactor -= 0.5f
            LensButton.ZOOM_IN -> zoomFactor += 0.5f
            LensButton.FREEZE -> setFrozen(!frozen)
            LensButton.CLOSE -> requestClose()
            LensButton.CENTER -> onCenterRequested?.invoke()
        }
        // Seguir usando los botones reinicia el temporizador.
        pokeControls()
    }

    /**
     * Pide el cierre. El callback se publica en el hilo principal con
     * un pequeno retraso para que el evento tactil termine antes.
     */
    private fun requestClose() {
        if (closeRequested) return
        closeRequested = true
        alpha = 0.6f
        mainHandler.removeCallbacks(closeRunnable)
        mainHandler.postDelayed(closeRunnable, CLOSE_DELAY_MS)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(closeRunnable)
        mainHandler.removeCallbacks(hideControlsRunnable)
    }
}
