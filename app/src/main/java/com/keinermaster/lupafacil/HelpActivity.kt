package com.keinermaster.lupafacil

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla de ayuda: explica con detalle que hace cada boton.
 * Se abre desde el boton "?" de la barra superior.
 */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        findViewById<Button>(R.id.helpClose).setOnClickListener {
            finish()
        }
    }
}
