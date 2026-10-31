package com.example.beamsticklauncher

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MediaCategory {
    VIDEO, AUDIO, IMAGE, FOLDER, OTHER
}

data class UsbDrive(
    val label: String,
    val rootDir: File,
    val totalBytes: Long,
    val freeBytes: Long
) {
    val percentUsed: Int
        get() = if (totalBytes > 0L) {
            (((totalBytes - freeBytes).toDouble() / totalBytes.toDouble()) * 100).toInt()
        } else 0

    fun formatCapacity(context: Context): String {
        val freeStr = FileUtils.formatFileSize(freeBytes)
        val totalStr = FileUtils.formatFileSize(totalBytes)
        return context.getString(R.string.storage_capacity_format, freeStr, totalStr, percentUsed)
    }
}

object FileUtils {

    fun scanUsbDrives(context: Context): List<UsbDrive> {
        val drives = mutableListOf<UsbDrive>()
        val foundPaths = mutableSetOf<String>()

        val internalLabel = context.getString(R.string.storage_internal)
        val usbDefaultLabel = context.getString(R.string.storage_usb)

        // 1. Android-Standard über ContextCompat (erfasst SD-Karten und USB-Volumes)
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)

        for (dir in externalDirs) {
            if (dir == null) continue

            val isRemovable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    Environment.isExternalStorageRemovable(dir)
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }

            val root = getRootOfStorage(dir)
            if (root != null && root.exists() && root.canRead()) {
                val canonical = root.canonicalPath
                if (foundPaths.add(canonical)) {
                    val stats = getStorageStats(root)
                    if (stats.first > 0L) {
                        val isInternal = !isRemovable || canonical.contains("emulated", ignoreCase = true)
                        val label = if (isInternal) internalLabel else usbDefaultLabel
                        drives.add(UsbDrive(label, root, stats.first, stats.second))
                    }
                }
            }
        }

        // 2. Scan unter /storage für gemountete Volumes (z.B. /storage/0000-0000)
        val storageDir = File("/storage")
        if (storageDir.exists() && storageDir.isDirectory) {
            val list = storageDir.listFiles()
            if (list != null) {
                for (f in list) {
                    val name = f.name.lowercase(Locale.ROOT)
                    if (name == "self" || name == "knox" || name == "container") continue

                    if (f.isDirectory && f.canRead()) {
                        val canonical = f.canonicalPath
                        if (foundPaths.add(canonical)) {
                            val stats = getStorageStats(f)
                            if (stats.first > 0L) {
                                val isInternal = name == "emulated" || canonical.contains("emulated", ignoreCase = true)
                                val label = if (isInternal) internalLabel else usbDefaultLabel
                                drives.add(UsbDrive(label, f, stats.first, stats.second))
                            }
                        }
                    }
                }
            }
        }

        // 3. Fallback für Emulator: Falls gar kein Wechselspeicher gemountet wurde
        if (drives.isEmpty()) {
            val fallback = Environment.getExternalStorageDirectory()
            if (fallback != null && fallback.exists() && fallback.canRead()) {
                val stats = getStorageStats(fallback)
                drives.add(UsbDrive(internalLabel, fallback, stats.first, stats.second))
            }
        }

        // 4. Feinschliff der USB-Labels:
        // Ein einzelner Stick heißt schlicht "USB", bei mehreren Sticks wird nummeriert ("USB 1", "USB 2" etc.)
        val usbCount = drives.count { it.label == usbDefaultLabel }
        if (usbCount > 1) {
            var counter = 1
            return drives.map { drive ->
                if (drive.label == usbDefaultLabel) {
                    val numberedLabel = context.getString(R.string.storage_usb_numbered, counter)
                    counter++
                    drive.copy(label = numberedLabel)
                } else {
                    drive
                }
            }
        }

        return drives
    }

    private fun getRootOfStorage(file: File): File? {
        var current: File? = file
        while (current != null) {
            val parent = current.parentFile
            if (parent != null && parent.name.equals("Android", ignoreCase = true)) {
                return parent.parentFile
            }
            current = parent
        }
        return null
    }

    private fun getStorageStats(dir: File): Pair<Long, Long> {
        return try {
            val stat = StatFs(dir.absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            Pair(total, free)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }

    fun categorize(file: File): MediaCategory {
        if (file.isDirectory) return MediaCategory.FOLDER

        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "3gp" -> MediaCategory.VIDEO
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "opus" -> MediaCategory.AUDIO
            "jpg", "jpeg", "png", "bmp", "webp", "gif" -> MediaCategory.IMAGE
            else -> MediaCategory.OTHER
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}