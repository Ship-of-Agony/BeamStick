package com.example.beamsticklauncher

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MediaInfoDialog(
    context: Context,
    private val file: File
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_media_info)

        window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.72).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            win.setDimAmount(0.65f)
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        setupViews()
    }

    private fun setupViews() {
        val tvBadge = findViewById<TextView>(R.id.tvInfoBadge)
        val tvTitle = findViewById<TextView>(R.id.tvInfoTitle)
        val tvPath = findViewById<TextView>(R.id.tvInfoPath)
        val container = findViewById<LinearLayout>(R.id.infoPropertiesContainer)
        val btnClose = findViewById<Button>(R.id.btnInfoClose)

        val category = FileUtils.categorize(file)
        tvBadge.text = when (category) {
            MediaCategory.VIDEO -> context.getString(R.string.badge_video)
            MediaCategory.AUDIO -> context.getString(R.string.badge_audio)
            MediaCategory.IMAGE -> context.getString(R.string.badge_image)
            MediaCategory.FOLDER -> context.getString(R.string.badge_folder)
            MediaCategory.OTHER -> context.getString(R.string.badge_file)
        }

        tvTitle.text = file.name
        tvPath.text = file.absolutePath

        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        val properties = MediaInfoHelper.extractInfo(context, file)

        var firstItemView: View? = null

        for ((index, prop) in properties.withIndex()) {
            val item = inflater.inflate(R.layout.item_media_prop, container, false)
            val tvKey = item.findViewById<TextView>(R.id.tvPropKey)
            val tvValue = item.findViewById<TextView>(R.id.tvPropValue)

            tvKey.text = prop.key
            tvValue.text = prop.value

            item.isFocusable = true
            item.isFocusableInTouchMode = false
            item.isClickable = true
            item.setBackgroundResource(R.drawable.bg_tile_normal)

            // D-Pad Fokus-Highlight für jede Zeile
            item.setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundResource(if (hasFocus) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
            }

            if (index == 0) {
                firstItemView = item
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
            container.addView(item, params)
        }

        btnClose.isFocusable = true
        btnClose.isFocusableInTouchMode = false
        btnClose.isClickable = true
        btnClose.stateListAnimator = null
        btnClose.setBackgroundResource(R.drawable.bg_tile_normal)
        btnClose.setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundResource(if (hasFocus) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
        }
        btnClose.setOnClickListener { dismiss() }

        // Startfokus auf das erste Element legen, damit man mit Runter/Hoch durchscrollen kann
        firstItemView?.requestFocus() ?: btnClose.requestFocus()
    }
}