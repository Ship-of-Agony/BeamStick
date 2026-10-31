package com.example.beamsticklauncher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileAdapter(
    private var files: List<File>,
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    fun updateList(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBadge: TextView = itemView.findViewById(R.id.tvBadge)
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvFileSub: TextView = itemView.findViewById(R.id.tvFileSub)

        init {
            itemView.isFocusable = true
            itemView.isClickable = true
            itemView.isLongClickable = true

            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate()
                        .scaleX(1.035f)
                        .scaleY(1.035f)
                        .translationZ(10f)
                        .setDuration(150)
                        .start()
                    view.setBackgroundResource(R.drawable.bg_tile_focused)
                } else {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setDuration(150)
                        .start()
                    view.setBackgroundResource(R.drawable.bg_tile_normal)
                }
            }
        }

        fun bind(file: File) {
            tvFileName.text = file.name
            val category = FileUtils.categorize(file)

            // Automatischer Fokus-Fluss: Keine feste Blockade mehr auf btnSettings
            itemView.nextFocusUpId = View.NO_ID

            if (file.isDirectory) {
                val count = file.listFiles()?.size ?: 0
                tvFileSub.text = itemView.context.getString(R.string.elements_count, count)
                setupBadge(itemView.context.getString(R.string.badge_folder), "#F59E0B", "#26F59E0B")
            } else {
                val sizeStr = FileUtils.formatFileSize(file.length())
                val dateStr = FileUtils.formatDate(file.lastModified())
                tvFileSub.text = "$sizeStr • $dateStr"

                when (category) {
                    MediaCategory.VIDEO -> setupBadge(itemView.context.getString(R.string.badge_video), "#00D2FF", "#2600D2FF")
                    MediaCategory.AUDIO -> setupBadge(itemView.context.getString(R.string.badge_audio), "#A855F7", "#26A855F7")
                    MediaCategory.IMAGE -> setupBadge(itemView.context.getString(R.string.badge_image), "#10B981", "#2610B981")
                    else -> setupBadge(itemView.context.getString(R.string.badge_file), "#94A3B8", "#2694A3B8")
                }
            }

            itemView.setOnClickListener { onItemClick(file) }
            itemView.setOnLongClickListener {
                onItemLongClick(file)
                true
            }
        }

        private fun setupBadge(text: String, strokeColorHex: String, fillColorHex: String) {
            tvBadge.text = text
            tvBadge.setTextColor(Color.parseColor(strokeColorHex))
            val pill = GradientDrawable().apply {
                setColor(Color.parseColor(fillColorHex))
                setStroke(2, Color.parseColor(strokeColorHex))
                cornerRadius = 16f
            }
            tvBadge.background = pill
        }
    }
}