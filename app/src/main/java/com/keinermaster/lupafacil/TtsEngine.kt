package com.keinermaster.lupafacil

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Motor de texto a voz profesional.
 *
 * Mejoras sobre la version anterior:
 *  - Verifica que el motor este listo antes de hablar (antes fallaba en silencio).
 *  - Encola los mensajes en vez de cortarlos (QUEUE_ADD para frases seguidas).
 *  - Callback onReady para saber cuando esta disponible.
 *  - Manejo de errores con logs.
 *  - Locale español con fallback robusto.
 */
class TtsEngine(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsEngine"
        private const val UTTERANCE_ID = "lupafacil"
    }

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var ready = false

    /** Se llama cuando el motor esta listo (o falla). */
    var onReady: ((Boolean) -> Unit)? = null

    /** Velocidad de voz (0.4x a 2.0x). */
    var rate: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.4f, 2.0f)
            if (ready) {
                try {
                    tts?.setSpeechRate(field)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo cambiar velocidad", e)
                }
            }
        }

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear TextToSpeech", e)
            ready = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: run {
                ready = false
                onReady?.invoke(false)
                return
            }
            try {
                // Intenta español; si no esta, usa el idioma del sistema.
                val spanish = Locale("es", "ES")
                val result = engine.setLanguage(spanish)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale.getDefault())
                }
                engine.setSpeechRate(rate)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "Error al reproducir voz")
                    }
                })
                ready = true
                mainHandler.post { onReady?.invoke(true) }
            } catch (e: Exception) {
                Log.e(TAG, "Error al configurar TTS", e)
                ready = false
                mainHandler.post { onReady?.invoke(false) }
            }
        } else {
            Log.w(TAG, "TTS no disponible (status=$status)")
            ready = false
            mainHandler.post { onReady?.invoke(false) }
        }
    }

    /**
     * Dice un texto en voz alta.
     * @param flush true = corta lo anterior; false = lo encola despues.
     */
    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !ready) {
            Log.w(TAG, "TTS no listo, ignorando: $text")
            return
        }
        try {
            val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, mode, null, UTTERANCE_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error al hablar", e)
        }
    }

    /** Detiene la voz actual. */
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error al detener voz", e)
        }
    }

    /** Libera el motor. Llamar en onDestroy. */
    fun shutdown() {
        ready = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error al cerrar TTS", e)
        }
        tts = null
    }
}
