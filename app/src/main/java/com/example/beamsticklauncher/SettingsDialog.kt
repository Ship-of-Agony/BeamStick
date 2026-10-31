package com.example.beamsticklauncher

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SettingsDialog(
    context: Context,
    private val settings: AppSettings
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_settings)

        window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.72).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            win.setDimAmount(0.6f)
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        setupViews()
    }

    private fun setupViews() {
        val titleView = findViewById<TextView>(R.id.tvSettingsTitle)
        val subtitleView = findViewById<TextView>(R.id.tvSettingsSubtitle)
        val container = findViewById<LinearLayout>(R.id.settingsContainer)
        val btnClose = findViewById<Button>(R.id.btnDialogClose)

        titleView.setText(R.string.settings_title)
        subtitleView.setText(R.string.settings_subtitle)

        container.removeAllViews()
        val inflater = LayoutInflater.from(context)

        // 1. Kachel: USB-Autostart
        addTile(
            container = container,
            inflater = inflater,
            title = context.getString(R.string.setting_autostart_title),
            subtitle = context.getString(R.string.setting_autostart_sub),
            valueText = if (settings.isAutostartEnabled) context.getString(R.string.status_on) else context.getString(R.string.status_off),
            valueColor = if (settings.isAutostartEnabled) "#4E8CFF" else "#94A3B8"
        ) { _, tvValue ->
            settings.isAutostartEnabled = !settings.isAutostartEnabled
            val isNowOn = settings.isAutostartEnabled
            tvValue.text = if (isNowOn) context.getString(R.string.status_on) else context.getString(R.string.status_off)
            tvValue.setTextColor(Color.parseColor(if (isNowOn) "#4E8CFF" else "#94A3B8"))
        }

        // 2. Kachel: Standard-Videoplayer
        addTile(
            container = container,
            inflater = inflater,
            title = context.getString(R.string.setting_video_title),
            subtitle = context.getString(R.string.setting_video_sub),
            valueText = getAppLabelOrAsk(settings.defaultVideoPlayer),
            valueColor = "#4E8CFF"
        ) { _, tvValue ->
            showAppChooserDialog(
                title = context.getString(R.string.setting_video_title),
                mimeType = "video/*",
                currentPkg = settings.defaultVideoPlayer
            ) { chosen ->
                settings.defaultVideoPlayer = chosen
                tvValue.text = getAppLabelOrAsk(chosen)
            }
        }

        // 3. Kachel: Standard-Audioplayer
        addTile(
            container = container,
            inflater = inflater,
            title = context.getString(R.string.setting_audio_title),
            subtitle = context.getString(R.string.setting_audio_sub),
            valueText = getAppLabelOrAsk(settings.defaultAudioPlayer),
            valueColor = "#4E8CFF"
        ) { _, tvValue ->
            showAppChooserDialog(
                title = context.getString(R.string.setting_audio_title),
                mimeType = "audio/*",
                currentPkg = settings.defaultAudioPlayer
            ) { chosen ->
                settings.defaultAudioPlayer = chosen
                tvValue.text = getAppLabelOrAsk(chosen)
            }
        }

        // 4. Kachel: Standard-Bildbetrachter
        addTile(
            container = container,
            inflater = inflater,
            title = context.getString(R.string.setting_image_title),
            subtitle = context.getString(R.string.setting_image_sub),
            valueText = getAppLabelOrAsk(settings.defaultImageViewer),
            valueColor = "#4E8CFF"
        ) { _, tvValue ->
            showAppChooserDialog(
                title = context.getString(R.string.setting_image_title),
                mimeType = "image/*",
                currentPkg = settings.defaultImageViewer
            ) { chosen ->
                settings.defaultImageViewer = chosen
                tvValue.text = getAppLabelOrAsk(chosen)
            }
        }

        // Close-Button Styling & D-Pad Fokus-Highlight
        btnClose.setText(R.string.settings_btn_close)
        btnClose.isFocusable = true
        btnClose.isFocusableInTouchMode = false
        btnClose.isClickable = true
        btnClose.stateListAnimator = null
        btnClose.setBackgroundResource(R.drawable.bg_tile_normal)

        btnClose.setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundResource(if (hasFocus) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
        }

        btnClose.setOnClickListener { dismiss() }
    }

    private fun addTile(
        container: LinearLayout,
        inflater: LayoutInflater,
        title: String,
        subtitle: String,
        valueText: String,
        valueColor: String,
        onClick: (View, TextView) -> Unit
    ) {
        val tile = inflater.inflate(R.layout.item_settings_tile, container, false)
        val tvTitle = tile.findViewById<TextView>(R.id.tvTileTitle)
        val tvSub = tile.findViewById<TextView>(R.id.tvTileSubtitle)
        val tvValue = tile.findViewById<TextView>(R.id.tvTileValue)

        tvTitle.text = title
        tvSub.text = subtitle
        tvValue.text = valueText
        tvValue.setTextColor(Color.parseColor(valueColor))

        tile.isFocusable = true
        tile.isFocusableInTouchMode = false
        tile.isClickable = true
        tile.setBackgroundResource(R.drawable.bg_tile_normal)

        tile.setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundResource(if (hasFocus) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
        }

        tile.setOnClickListener {
            onClick(tile, tvValue)
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 14
        }
        container.addView(tile, params)
    }

    private fun getAppLabelOrAsk(packageName: String): String {
        if (packageName.isEmpty() || packageName == AppSettings.VALUE_ALWAYS_ASK) {
            return context.getString(R.string.picker_mode_ask_title)
        }
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            when (packageName) {
                "org.xbmc.kodi" -> "Kodi"
                "org.videolan.vlc" -> "VLC"
                "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro" -> "MX Player"
                "com.brouken.player" -> "Just (Player)"
                "org.courville.nova" -> "Nova Video Player"
                else -> packageName
            }
        }
    }

    private fun showAppChooserDialog(
        title: String,
        mimeType: String,
        currentPkg: String,
        showAllAppsDirectly: Boolean = false,
        onChosen: (String) -> Unit
    ) {
        val chooserDialog = Dialog(context)
        chooserDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        chooserDialog.setContentView(R.layout.dialog_settings)

        chooserDialog.window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.72).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            win.setDimAmount(0.6f)
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val tvTitle = chooserDialog.findViewById<TextView>(R.id.tvSettingsTitle)
        val tvSub = chooserDialog.findViewById<TextView>(R.id.tvSettingsSubtitle)
        val container = chooserDialog.findViewById<LinearLayout>(R.id.settingsContainer)
        val btnCancel = chooserDialog.findViewById<Button>(R.id.btnDialogClose)

        tvTitle.text = title
        tvSub.text = if (showAllAppsDirectly) "Select from all installed applications" else "Select a preferred player or choose an installed app"

        container.removeAllViews()
        val inflater = LayoutInflater.from(context)

        if (!showAllAppsDirectly) {
            // 1. Always ask (Standard)
            addPickerEntry(
                container, inflater,
                label = context.getString(R.string.picker_mode_ask_title),
                sub = "Prompt for app every time a file is opened",
                isSelected = (currentPkg == AppSettings.VALUE_ALWAYS_ASK || currentPkg.isEmpty())
            ) {
                onChosen(AppSettings.VALUE_ALWAYS_ASK)
                chooserDialog.dismiss()
            }

            // 2. Große Favoriten-Player (Kodi, VLC, MX Player etc.)
            val recommended = FileOpener.getRecommendedForMime(mimeType)
            val seenPkgs = mutableSetOf<String>()

            for (rec in recommended) {
                seenPkgs.add(rec.packageName)
                val isInstalled = isPackageInstalled(context, rec.packageName)
                val isSelected = (rec.packageName == currentPkg)

                addPickerEntry(
                    container, inflater,
                    label = if (isInstalled) rec.label else "${rec.label} (Play Store)",
                    sub = if (isInstalled) rec.packageName else "Not installed – Click to download from Play Store",
                    isSelected = isSelected
                ) {
                    if (isInstalled) {
                        onChosen(rec.packageName)
                        chooserDialog.dismiss()
                    } else {
                        openPlayStore(context, rec.packageName)
                    }
                }
            }

            // 3. Weitere installierte Player, die diesen MIME-Typ unterstützen
            val installedPlayers = FileOpener.getInstalledPlayersForMime(context, mimeType)
            for (app in installedPlayers) {
                if (seenPkgs.add(app.packageName)) {
                    addPickerEntry(
                        container, inflater,
                        label = app.label,
                        sub = app.packageName,
                        isSelected = (app.packageName == currentPkg)
                    ) {
                        onChosen(app.packageName)
                        chooserDialog.dismiss()
                    }
                }
            }

            // 4. Ganz am Ende: Alle auf dem Gerät installierten Apps durchsuchen
            addPickerEntry(
                container, inflater,
                label = "Browse all installed apps…",
                sub = "Select any installed app on this device (e.g. self-made or custom tools)",
                isSelected = false
            ) {
                chooserDialog.dismiss()
                showAppChooserDialog(
                    title = "All Installed Apps",
                    mimeType = mimeType,
                    currentPkg = currentPkg,
                    showAllAppsDirectly = true,
                    onChosen = onChosen
                )
            }

        } else {
            // Vollständige Liste aller installierten Launchable-Apps
            val allApps = FileOpener.getAllInstalledLaunchableApps(context)
            for (app in allApps) {
                addPickerEntry(
                    container, inflater,
                    label = app.label,
                    sub = app.packageName,
                    isSelected = (app.packageName == currentPkg)
                ) {
                    onChosen(app.packageName)
                    chooserDialog.dismiss()
                }
            }
        }

        btnCancel.setText(R.string.settings_btn_cancel)
        btnCancel.isFocusable = true
        btnCancel.isFocusableInTouchMode = false
        btnCancel.isClickable = true
        btnCancel.stateListAnimator = null
        btnCancel.setBackgroundResource(R.drawable.bg_tile_normal)
        btnCancel.setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundResource(if (hasFocus) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
        }
        btnCancel.setOnClickListener { chooserDialog.dismiss() }

        chooserDialog.show()
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun openPlayStore(context: Context, packageName: String) {
        try {
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(storeIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    private fun addPickerEntry(
        container: LinearLayout,
        inflater: LayoutInflater,
        label: String,
        sub: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        val tile = inflater.inflate(R.layout.item_settings_tile, container, false)
        val tvTitle = tile.findViewById<TextView>(R.id.tvTileTitle)
        val tvSub = tile.findViewById<TextView>(R.id.tvTileSubtitle)
        val tvValue = tile.findViewById<TextView>(R.id.tvTileValue)

        tvTitle.text = label
        tvSub.text = sub

        if (isSelected) {
            tvValue.text = "✓"
            tvValue.setTextColor(Color.parseColor("#4E8CFF"))
            tvValue.visibility = View.VISIBLE
        } else {
            tvValue.visibility = View.GONE
        }

        tile.isFocusable = true
        tile.isFocusableInTouchMode = false
        tile.isClickable = true
        tile.setBackgroundResource(R.drawable.bg_tile_normal)

        tile.setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundResource(if (hasFocus) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
        }

        tile.setOnClickListener { onClick() }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 14
        }
        container.addView(tile, params)
    }
}