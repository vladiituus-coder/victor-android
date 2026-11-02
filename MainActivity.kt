package com.victor.mascota

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "Víctor, el Treecko"
        title.textSize = 26f
        root.addView(title)

        status = TextView(this)
        status.textSize = 15f
        status.setPadding(0, pad / 2, 0, pad / 2)
        root.addView(status)

        addButton(root, "1. Dar permiso para flotar sobre otras apps") { askOverlay() }
        addButton(root, "2. Despertar a Víctor") { startPet() }
        addButton(root, "Mandar a Víctor a dormir") {
            stopService(Intent(this, PetService::class.java))
            refresh()
        }
        addButton(root, "Quitar restricciones de batería") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        addButton(root, "Otros permisos de la app (Xiaomi)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        val help = TextView(this)
        help.textSize = 13f
        help.setPadding(0, pad, 0, 0)
        help.text = "Toque en Víctor: reacciona. Doble toque: abre el juego. " +
            "Arrastralo para moverlo de lugar.\n\n" +
            "En Xiaomi activá también: Batería sin restricciones, inicio automático " +
            "y ventanas emergentes en segundo plano, o el sistema lo va a cerrar."
        root.addView(help)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun addButton(parent: LinearLayout, text: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { onClick() }
        parent.addView(b)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val perm = if (Settings.canDrawOverlays(this)) "permiso concedido" else "falta el permiso"
        val run = if (PetService.running) "Víctor está despierto" else "Víctor duerme"
        status.text = "Estado: $run · $perm"
    }

    private fun askOverlay() {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(i)
    }

    private fun startPet() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primero dale el permiso (paso 1)", Toast.LENGTH_LONG).show()
            askOverlay()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, PetService::class.java))
        window.decorView.postDelayed({ refresh() }, 600)
    }
}
