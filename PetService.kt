package com.victor.mascota

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager

class PetService : Service() {

    companion object {
        @Volatile
        var running = false
        const val ACTION_STOP = "com.victor.mascota.STOP"
        const val CHANNEL = "victor_pet"
        const val GAME_URL = "https://vladiituus-coder.github.io/victor/files/"
    }

    private var wm: WindowManager? = null
    private var pet: PetView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = manager
        val view = PetView(this, manager)
        pet = view
        manager.addView(view, view.params)
        view.start()
    }

    override fun onDestroy() {
        running = false
        pet?.let {
            it.shutdown()
            try {
                wm?.removeView(it)
            } catch (e: Exception) {
            }
        }
        pet = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Víctor", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PetService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Víctor está despierto")
            .setContentText("Tocá para abrir los ajustes")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dormir a Víctor", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }
}
