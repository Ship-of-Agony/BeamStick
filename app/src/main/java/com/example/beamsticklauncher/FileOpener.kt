package com.example.beamsticklauncher

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

data class AppTarget(
    val label: String,
    val packageName: String,
    val isInstalled: Boolean = true,
    val isRecommended: Boolean = false
)

object FileOpener {

    private val RECOMMENDED_VIDEO_PLAYERS = listOf(
        AppTarget("Kodi", "org.xbmc.kodi", isRecommended = true),
        AppTarget("VLC", "org.videolan.vlc", isRecommended = true),
        AppTarget("MX Player", "com.mxtech.videoplayer.ad", isRecommended = true),
        AppTarget("Just (Player)", "com.brouken.player", isRecommended = true),
        AppTarget("Nova Video Player", "org.courville.nova", isRecommended = true)
    )

    private val RECOMMENDED_AUDIO_PLAYERS = listOf(
        AppTarget("VLC", "org.videolan.vlc", isRecommended = true),
        AppTarget("Kodi", "org.xbmc.kodi", isRecommended = true),
        AppTarget("Musicolet", "in.krosbits.musicolet", isRecommended = true),
        AppTarget("AIMP", "com.aimp.player", isRecommended = true)
    )

    private val RECOMMENDED_IMAGE_VIEWERS = listOf(
        AppTarget("Google Photos / Galerie", "com.google.android.apps.photos", isRecommended = true),
        AppTarget("Kodi", "org.xbmc.kodi", isRecommended = true),
        AppTarget("Simple Gallery", "com.simplemobiletools.gallery", isRecommended = true)
    )

    fun openFile(context: Context, file: File, settings: AppSettings) {
        val category = FileUtils.categorize(file)
        val savedPlayer = when (category) {
            MediaCategory.VIDEO -> settings.defaultVideoPlayer
            MediaCategory.AUDIO -> settings.defaultAudioPlayer
            MediaCategory.IMAGE -> settings.defaultImageViewer
            else -> AppSettings.VALUE_ALWAYS_ASK
        }

        val mimeType = getMimeType(file, category)

        if (savedPlayer.isNotEmpty() && savedPlayer != AppSettings.VALUE_ALWAYS_ASK) {
            try {
                launchSpecificApp(context, file, mimeType, savedPlayer)
                return
            } catch (e: Exception) {
                // Fallback, wenn zugewiesene App nicht gestartet werden kann
            }
        }

        val availableApps = getInstalledPlayersForMime(context, mimeType)
        showPlayerChooser(context, file, mimeType, availableApps)
    }

    private fun getMimeType(file: File, category: MediaCategory): String {
        return when (category) {
            MediaCategory.VIDEO -> "video/*"
            MediaCategory.AUDIO -> "audio/*"
            MediaCategory.IMAGE -> "image/*"
            else -> "*/*"
        }
    }

    fun getRecommendedForMime(mimeType: String): List<AppTarget> {
        return when {
            mimeType.startsWith("video/") -> RECOMMENDED_VIDEO_PLAYERS
            mimeType.startsWith("audio/") -> RECOMMENDED_AUDIO_PLAYERS
            mimeType.startsWith("image/") -> RECOMMENDED_IMAGE_VIEWERS
            else -> RECOMMENDED_VIDEO_PLAYERS
        }
    }

    fun getInstalledPlayersForMime(context: Context, mimeType: String): List<AppTarget> {
        val pm = context.packageManager
        val testUri = Uri.parse("content://${context.packageName}.fileprovider/test")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(testUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val result = mutableListOf<AppTarget>()
        val seenPackages = mutableSetOf<String>()

        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && seenPackages.add(pkg)) {
                val label = info.loadLabel(pm).toString()
                result.add(AppTarget(label = label, packageName = pkg, isInstalled = true))
            }
        }

        return result
    }

    fun getAllInstalledLaunchableApps(context: Context): List<AppTarget> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val tvIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val resolved = (pm.queryIntentActivities(intent, 0) + pm.queryIntentActivities(tvIntent, 0))
        val seenPackages = mutableSetOf<String>()
        val result = mutableListOf<AppTarget>()

        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && seenPackages.add(pkg)) {
                val label = info.loadLabel(pm).toString()
                result.add(AppTarget(label = label, packageName = pkg, isInstalled = true))
            }
        }

        if (result.isEmpty()) {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                if (app.flags and ApplicationInfo.FLAG_SYSTEM == 0 && app.packageName != context.packageName) {
                    val label = pm.getApplicationLabel(app).toString()
                    result.add(AppTarget(label = label, packageName = app.packageName, isInstalled = true))
                }
            }
        }

        return result.sortedBy { it.label.lowercase() }
    }

    fun launchSpecificApp(context: Context, file: File, mimeType: String, packageName: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            setPackage(packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.setDataAndType(uri, mimeType)
                launchIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                throw e
            }
        }
    }

    private fun showPlayerChooser(
        context: Context,
        file: File,
        mimeType: String,
        apps: List<AppTarget>
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_settings)

        dialog.window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.72).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            win.setDimAmount(0.6f)
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val titleView = dialog.findViewById<TextView>(R.id.tvSettingsTitle)
        val subtitleView = dialog.findViewById<TextView>(R.id.tvSettingsSubtitle)
        val container = dialog.findViewById<LinearLayout>(R.id.settingsContainer)
        val btnClose = dialog.findViewById<Button>(R.id.btnDialogClose)

        titleView.text = "Open with…"
        subtitleView.text = file.name

        container.removeAllViews()
        val inflater = LayoutInflater.from(context)

        val effectiveApps = apps.ifEmpty { getAllInstalledLaunchableApps(context) }

        for (app in effectiveApps) {
            val tile = inflater.inflate(R.layout.item_settings_tile, container, false)
            val tvTitle = tile.findViewById<TextView>(R.id.tvTileTitle)
            val tvSub = tile.findViewById<TextView>(R.id.tvTileSubtitle)
            val tvValue = tile.findViewById<TextView>(R.id.tvTileValue)

            tvTitle.text = app.label
            tvSub.text = if (app.isInstalled) app.packageName else "Install from Google Play Store"
            tvValue.visibility = View.GONE

            tile.isFocusable = true
            tile.isFocusableInTouchMode = false
            tile.isClickable = true
            tile.setBackgroundResource(R.drawable.bg_tile_normal)

            tile.setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundResource(if (hasFocus) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
            }

            tile.setOnClickListener {
                dialog.dismiss()
                if (app.isInstalled) {
                    try {
                        launchSpecificApp(context, file, mimeType, app.packageName)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "App could not be launched.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    try {
                        val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(storeIntent)
                    } catch (e: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                    }
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
            container.addView(tile, params)
        }

        btnClose.setText(R.string.settings_btn_cancel)
        btnClose.isFocusable = true
        btnClose.isFocusableInTouchMode = false
        btnClose.isClickable = true
        btnClose.stateListAnimator = null
        btnClose.setBackgroundResource(R.drawable.bg_tile_normal)
        btnClose.setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundResource(if (hasFocus) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
        }
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}