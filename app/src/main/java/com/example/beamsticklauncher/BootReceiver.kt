package com.example.beamsticklauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.File

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val settings = AppSettings(context)

        if (!settings.isAutostartEnabled) {
            return
        }

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                // 5 Sekunden warten, damit das Dateisystem USB-Speicher vollstaendig initialisiert
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isUsbStorageAttached(context)) {
                        startLauncher(context)
                    }
                }, 5000)
            }
            Intent.ACTION_MEDIA_MOUNTED -> {
                // Sofort starten, wenn waehrend der Laufzeit ein USB-Medium gemountet wird
                startLauncher(context)
            }
        }
    }

    private fun isUsbStorageAttached(context: Context): Boolean {
        return try {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            // Wenn mehr als ein Verzeichnis vorliegt, ist ein externes Medium (USB/SD) gemountet
            if (externalDirs.size > 1 && externalDirs[1] != null) {
                return true
            }

            // Alternativ /storage/ auf Wechseldatentraeger pruefen
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val contents = storageDir.listFiles() ?: return false
                contents.any { file ->
                    file.isDirectory &&
                            !file.name.equals("emulated", ignoreCase = true) &&
                            !file.name.equals("self", ignoreCase = true)
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun startLauncher(context: Context) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        context.startActivity(launchIntent)
    }
}