package com.keinermaster.lupafacil

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferencias persistentes de accesibilidad.
 *
 * Guarda zoom, brillo, contraste, modo de color, tamaño de texto,
 * velocidad de voz, vibración y las opciones de lectura por voz.
 */
class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lupafacil_prefs", Context.MODE_PRIVATE)

    var zoom: Float
        get() = prefs.getFloat("zoom", 1.0f)
        set(v) = prefs.edit().putFloat("zoom", v).apply()

    var brightness: Float
        get() = prefs.getFloat("brightness", 1.0f)
        set(v) = prefs.edit().putFloat("brightness", v).apply()

    var contrast: Float
        get() = prefs.getFloat("contrast", 1.0f)
        set(v) = prefs.edit().putFloat("contrast", v).apply()

    var colorMode: Int
        get() = prefs.getInt("color_mode", 0)
        set(v) = prefs.edit().putInt("color_mode", v).apply()

    var fontScale: Float
        get() = prefs.getFloat("font_scale", 1.0f)
        set(v) = prefs.edit().putFloat("font_scale", v).apply()

    var speechRate: Float
        get() = prefs.getFloat("speech_rate", 1.0f)
        set(v) = prefs.edit().putFloat("speech_rate", v).apply()

    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(v) = prefs.edit().putBoolean("vibration", v).apply()

    var lensZoom: Float
        get() = prefs.getFloat("lens_zoom", 3.0f)
        set(v) = prefs.edit().putFloat("lens_zoom", v).apply()

    /**
     * Radio de la lupa en dp (55 = pequeña, 75 = mediana, 100 = grande).
     * Clave nueva ("lens_size_dp"): las versiones anteriores usaban
     * "lens_size" con una escala más grande (70/95/125); al cambiar la
     * clave, los valores viejos se descartan y se usa el nuevo defecto.
     */
    var lensSize: Float
        get() = prefs.getFloat("lens_size_dp", 55f)
        set(v) = prefs.edit().putFloat("lens_size_dp", v).apply()

    /**
     * Forma de la lupa: 0 = círculo, 1 = barra de lectura.
     * (Ver FloatingLensView.SHAPE_CIRCLE / SHAPE_BAR.)
     */
    var lensShape: Int
        get() = prefs.getInt("lens_shape", 0)
        set(v) = prefs.edit().putInt("lens_shape", v).apply()

    // ------------------------------------------------------------------
    // Opciones de lectura por voz (interruptores del menu)
    // ------------------------------------------------------------------

    /** Leer en voz alta el texto escaneado por OCR. */
    var readText: Boolean
        get() = prefs.getBoolean("read_text", true)
        set(v) = prefs.edit().putBoolean("read_text", v).apply()

    /** Mostrar el texto escaneado en la pantalla (panel visible). */
    var showText: Boolean
        get() = prefs.getBoolean("show_text", true)
        set(v) = prefs.edit().putBoolean("show_text", v).apply()

    /** Describir la imagen (colores y formas) en voz alta. */
    var readImage: Boolean
        get() = prefs.getBoolean("read_image", false)
        set(v) = prefs.edit().putBoolean("read_image", v).apply()

    /** Confirmar cada accion con voz (ej: "Zoom 2x"). */
    var confirmActions: Boolean
        get() = prefs.getBoolean("confirm_actions", true)
        set(v) = prefs.edit().putBoolean("confirm_actions", v).apply()

    /** Modo facil: solo 3 botones gigantes. */
    var easyMode: Boolean
        get() = prefs.getBoolean("easy_mode", false)
        set(v) = prefs.edit().putBoolean("easy_mode", v).apply()

    /** Si el usuario ya vio el tutorial de bienvenida. */
    var tutorialSeen: Boolean
        get() = prefs.getBoolean("tutorial_seen", false)
        set(v) = prefs.edit().putBoolean("tutorial_seen", v).apply()
}
