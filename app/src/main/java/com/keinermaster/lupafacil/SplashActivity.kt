package com.keinermaster.lupafacil

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla de bienvenida (splash).
 *
 * Muestra la dedicatoria a Dilcia Mercedes con una animacion elegante
 * durante ~2.5 segundos, y luego abre la pantalla principal
 * (la lupa flotante de pantalla).
 *
 * No afecta a la funcionalidad: es puramente visual.
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DURATION_MS = 2500L
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val icon = findViewById<View>(R.id.splashIcon)
        val title = findViewById<View>(R.id.splashTitle)
        val line = findViewById<View>(R.id.splashLine)
        val dedication = findViewById<View>(R.id.splashDedication)
        val footer = findViewById<View>(R.id.splashFooter)

        // Estado inicial: todo invisible y desplazado.
        listOf(icon, title, line, dedication, footer).forEach {
            it.alpha = 0f
        }
        icon.translationY = -40f
        title.translationY = -20f
        dedication.translationY = 20f
        footer.translationY = 20f

        // Animacion escalonada (cada elemento aparece con retraso).
        animateIn(icon, 0L, -40f)
        animateIn(title, 200L, -20f)
        animateIn(line, 400L, 0f)
        animateIn(dedication, 600L, 20f)
        animateIn(footer, 900L, 20f)

        // Pulso suave del icono.
        ObjectAnimator.ofFloat(icon, "scaleX", 1f, 1.1f, 1f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(icon, "scaleY", 1f, 1.1f, 1f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Abre la pantalla principal tras el splash.
        handler.postDelayed({
            startActivity(Intent(this, FloatingLensActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, SPLASH_DURATION_MS)
    }

    private fun animateIn(view: View, delayMs: Long, fromTranslationY: Float) {
        view.alpha = 0f
        view.translationY = fromTranslationY
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
