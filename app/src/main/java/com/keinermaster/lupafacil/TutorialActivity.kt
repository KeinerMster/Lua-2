package com.keinermaster.lupafacil

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tutorial de bienvenida de 3 pasos. Se muestra solo la primera vez
 * que se abre la app. Explica lo basico con letras grandes y lenguaje
 * sencillo, pensado para personas mayores.
 *
 * Correcciones aplicadas:
 *  - Los titulos, textos y botones salen de strings.xml (getString),
 *    asi el tutorial se puede traducir y no hay literales duplicados.
 */
class TutorialActivity : AppCompatActivity() {

    private var step = 0

    /** Titulos de cada paso (recursos de strings.xml). */
    private val titleRes = intArrayOf(
        R.string.tutorial_step1_title,
        R.string.tutorial_step2_title,
        R.string.tutorial_step3_title
    )

    /** Textos de cada paso (recursos de strings.xml). */
    private val textRes = intArrayOf(
        R.string.tutorial_step1_text,
        R.string.tutorial_step2_text,
        R.string.tutorial_step3_text
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        val titleView = findViewById<TextView>(R.id.tutorialStepTitle)
        val textView = findViewById<TextView>(R.id.tutorialStepText)
        val dotsView = findViewById<TextView>(R.id.tutorialDots)
        val nextBtn = findViewById<Button>(R.id.tutorialNext)
        val skipBtn = findViewById<Button>(R.id.tutorialSkip)

        fun render() {
            titleView.text = getString(titleRes[step])
            textView.text = getString(textRes[step])
            dotsView.text = buildDots()
            nextBtn.text = if (step == titleRes.size - 1) {
                getString(R.string.tutorial_start)
            } else {
                getString(R.string.tutorial_next)
            }
        }

        nextBtn.setOnClickListener {
            if (step < titleRes.size - 1) {
                step++
                render()
            } else {
                finish()
            }
        }

        skipBtn.setOnClickListener { finish() }

        render()
    }

    private fun buildDots(): String {
        val sb = StringBuilder()
        for (i in titleRes.indices) {
            sb.append(if (i == step) "● " else "○ ")
        }
        return sb.toString().trim()
    }
}
