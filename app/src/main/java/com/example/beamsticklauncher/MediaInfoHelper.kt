package com.example.beamsticklauncher

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.util.Locale

data class MediaProperty(
    val key: String,
    val value: String
)

object MediaInfoHelper {

    fun extractInfo(context: Context, file: File): List<MediaProperty> {
        val list = mutableListOf<MediaProperty>()
        val category = FileUtils.categorize(file)

        // 1. Basis-Dateisystemwerte
        list.add(MediaProperty(context.getString(R.string.info_prop_file_size), FileUtils.formatFileSize(file.length())))
        list.add(MediaProperty(context.getString(R.string.info_prop_last_modified), FileUtils.formatDate(file.lastModified())))
        list.add(MediaProperty(context.getString(R.string.info_prop_extension), file.extension.uppercase(Locale.ROOT).ifEmpty { "-" }))

        when (category) {
            MediaCategory.VIDEO -> list.addAll(extractVideoInfo(context, file))
            MediaCategory.AUDIO -> list.addAll(extractAudioInfo(context, file))
            MediaCategory.IMAGE -> list.addAll(extractImageInfo(context, file))
            MediaCategory.FOLDER -> {
                val subFiles = file.listFiles()?.filter { !it.isHidden }?.size ?: 0
                list.add(MediaProperty(context.getString(R.string.info_prop_contains), context.getString(R.string.info_items_count, subFiles)))
            }
            MediaCategory.OTHER -> { /* Dateisystem-Daten genügen */ }
        }

        return list
    }

    private fun extractVideoInfo(context: Context, file: File): List<MediaProperty> {
        val list = mutableListOf<MediaProperty>()
        var widthVal = 0
        var heightVal = 0
        var durationMs = 0L
        var bitrateVal = 0L
        var rotationVal = ""
        var mimeVal = ""
        var hdrVal = ""

        // Stufe 1: MediaMetadataRetriever (per FileDescriptor für maximale Kompatibilität)
        val retriever = MediaMetadataRetriever()
        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(file)
            retriever.setDataSource(fis.fd)

            widthVal = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            heightVal = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            bitrateVal = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            rotationVal = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: ""
            mimeVal = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val colorStandard = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)
                val colorTransfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                if (colorTransfer == "6" || colorTransfer == "7" || colorStandard == "6") {
                    hdrVal = "HDR (BT.2020 / SMPTE 2084)"
                }
            }
        } catch (ignored: Exception) {
        } finally {
            try { retriever.release() } catch (ignored: Exception) {}
            try { fis?.close() } catch (ignored: Exception) {}
        }

        // Stufe 2: MediaExtractor für tiefe Spur-Metadaten (Codecs, Untertitel, Spuren)
        var videoCodecName = ""
        var audioCodecName = ""
        var audioChannels = 0
        var audioSampleRate = 0
        var frameRateVal = 0
        val subtitleTracks = mutableListOf<String>()

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackCount = extractor.trackCount

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (mime.startsWith("video/")) {
                    if (videoCodecName.isEmpty()) {
                        videoCodecName = formatCodecName(mime)
                    }
                    if (widthVal == 0 && format.containsKey(MediaFormat.KEY_WIDTH)) {
                        widthVal = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (heightVal == 0 && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        heightVal = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (durationMs == 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRateVal = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                    }
                } else if (mime.startsWith("audio/")) {
                    if (audioCodecName.isEmpty()) {
                        audioCodecName = formatCodecName(mime)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        audioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                } else if (mime.startsWith("text/") || mime.contains("subtitle") || mime.contains("vtt") || mime.contains("subrip")) {
                    val lang = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) format.getString(MediaFormat.KEY_LANGUAGE) else "Track ${i + 1}"
                    subtitleTracks.add(lang ?: "Track ${i + 1}")
                }
            }
        } catch (ignored: Exception) {
        } finally {
            try { extractor.release() } catch (ignored: Exception) {}
        }

        // Stufe 3: Fallback für ältere AVI/DivX-Dateien (Container-Inspektion)
        if (widthVal == 0 && file.extension.equals("avi", ignoreCase = true)) {
            val aviMeta = readAviHeaderDimensions(file)
            if (aviMeta != null) {
                widthVal = aviMeta.first
                heightVal = aviMeta.second
            }
        }

        // Formatierte Ausgabe zusammenbauen
        if (widthVal > 0 && heightVal > 0) {
            val tag = when {
                widthVal >= 3800 || heightVal >= 2100 -> " (4K UHD)"
                widthVal >= 1900 || heightVal >= 1000 -> " (1080p FHD)"
                widthVal >= 1200 || heightVal >= 700 -> " (720p HD)"
                widthVal >= 700 || heightVal >= 480 -> " (SD 480p/576p)"
                else -> ""
            }
            list.add(MediaProperty(context.getString(R.string.info_prop_resolution), "${widthVal} × ${heightVal}$tag"))
        }

        if (durationMs > 0L) {
            list.add(MediaProperty(context.getString(R.string.info_prop_duration), formatDuration(context, durationMs)))
        }

        if (videoCodecName.isNotEmpty()) {
            list.add(MediaProperty(context.getString(R.string.info_prop_video_codec), videoCodecName))
        }

        if (frameRateVal > 0) {
            list.add(MediaProperty(context.getString(R.string.info_prop_framerate), "$frameRateVal fps"))
        }

        if (bitrateVal > 0L) {
            list.add(MediaProperty(context.getString(R.string.info_prop_bitrate), String.format(Locale.getDefault(), "%.2f Mbps", bitrateVal / 1_000_000.0)))
        }

        if (audioCodecName.isNotEmpty()) {
            list.add(MediaProperty(context.getString(R.string.info_prop_audio_codec), audioCodecName))
        }

        if (audioChannels > 0) {
            val chText = when (audioChannels) {
                1 -> "Mono (1.0)"
                2 -> "Stereo (2.0)"
                6 -> "5.1 Surround"
                8 -> "7.1 Surround"
                else -> "$audioChannels Channels"
            }
            list.add(MediaProperty(context.getString(R.string.info_prop_audio_channels), chText))
        }

        if (audioSampleRate > 0) {
            list.add(MediaProperty(context.getString(R.string.info_prop_audio_samplerate), "${audioSampleRate / 1000} kHz"))
        }

        if (subtitleTracks.isNotEmpty()) {
            list.add(MediaProperty(context.getString(R.string.info_prop_subtitles), subtitleTracks.joinToString(", ")))
        } else {
            list.add(MediaProperty(context.getString(R.string.info_prop_subtitles), context.getString(R.string.info_prop_subtitles_none)))
        }

        if (rotationVal.isNotEmpty() && rotationVal != "0") {
            list.add(MediaProperty(context.getString(R.string.info_prop_rotation), "$rotationVal°"))
        }

        if (hdrVal.isNotEmpty()) {
            list.add(MediaProperty(context.getString(R.string.info_prop_hdr), hdrVal))
        }

        if (mimeVal.isNotEmpty()) {
            list.add(MediaProperty(context.getString(R.string.info_prop_mime_type), mimeVal))
        }

        return list
    }

    private fun extractAudioInfo(context: Context, file: File): List<MediaProperty> {
        val list = mutableListOf<MediaProperty>()
        val retriever = MediaMetadataRetriever()
        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(file)
            retriever.setDataSource(fis.fd)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!title.isNullOrEmpty()) list.add(MediaProperty(context.getString(R.string.info_prop_title), title))

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (!artist.isNullOrEmpty()) list.add(MediaProperty(context.getString(R.string.info_prop_artist), artist))

            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            if (!album.isNullOrEmpty()) list.add(MediaProperty(context.getString(R.string.info_prop_album), album))

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs > 0L) {
                list.add(MediaProperty(context.getString(R.string.info_prop_duration), formatDuration(context, durationMs)))
            }

            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            if (bitrate > 0L) {
                list.add(MediaProperty(context.getString(R.string.info_prop_bitrate), "${bitrate / 1000} kbps"))
            }

            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!mime.isNullOrEmpty()) {
                list.add(MediaProperty(context.getString(R.string.info_prop_audio_format), formatCodecName(mime)))
            }

        } catch (ignored: Exception) {
            list.add(MediaProperty(context.getString(R.string.badge_audio), context.getString(R.string.info_metadata_unavailable)))
        } finally {
            try { retriever.release() } catch (ignored: Exception) {}
            try { fis?.close() } catch (ignored: Exception) {}
        }
        return list
    }

    private fun extractImageInfo(context: Context, file: File): List<MediaProperty> {
        val list = mutableListOf<MediaProperty>()
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            if (options.outWidth > 0 && options.outHeight > 0) {
                val mp = (options.outWidth.toDouble() * options.outHeight.toDouble()) / 1_000_000.0
                list.add(MediaProperty(context.getString(R.string.info_prop_dimensions), "${options.outWidth} × ${options.outHeight} px"))
                list.add(MediaProperty(context.getString(R.string.info_prop_megapixels), String.format(Locale.getDefault(), "%.1f MP", mp)))
            }

            if (!options.outMimeType.isNullOrEmpty()) {
                list.add(MediaProperty(context.getString(R.string.info_prop_format), options.outMimeType.substringAfter("image/").uppercase(Locale.ROOT)))
            }
        } catch (ignored: Exception) {
            list.add(MediaProperty(context.getString(R.string.badge_image), context.getString(R.string.info_metadata_unavailable)))
        }
        return list
    }

    private fun formatCodecName(mime: String): String {
        return when {
            mime.contains("avc") || mime.contains("h264") -> "H.264 / AVC"
            mime.contains("hevc") || mime.contains("h265") -> "H.265 / HEVC"
            mime.contains("vp9") -> "VP9"
            mime.contains("vp8") -> "VP8"
            mime.contains("av01") || mime.contains("av1") -> "AV1"
            mime.contains("mp4v-es") -> "MPEG-4 Part 2"
            mime.contains("mpeg2") -> "MPEG-2"
            mime.contains("mp4a-latm") || mime.contains("aac") -> "AAC"
            mime.contains("ac3") -> "Dolby Digital (AC-3)"
            mime.contains("eac3") -> "Dolby Digital Plus (E-AC-3)"
            mime.contains("opus") -> "Opus"
            mime.contains("vorbis") -> "Vorbis"
            mime.contains("flac") -> "FLAC"
            mime.contains("raw") || mime.contains("pcm") -> "PCM Audio"
            else -> mime.substringAfter("/").uppercase(Locale.ROOT)
        }
    }

    private fun formatDuration(context: Context, ms: Long): String {
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            context.getString(R.string.info_duration_hours, hours, minutes, seconds)
        } else {
            context.getString(R.string.info_duration_minutes, minutes, seconds)
        }
    }

    // Liest bei nicht unterstützten AVI-Dateien direkt Breite und Höhe aus dem RIFF-AVI-Header aus
    private fun readAviHeaderDimensions(file: File): Pair<Int, Int>? {
        return try {
            val bytes = ByteArray(72)
            FileInputStream(file).use { it.read(bytes) }
            val header = String(bytes, 0, 12, Charsets.US_ASCII)
            if (header.startsWith("RIFF") && header.endsWith("AVI ")) {
                val width = (bytes[64].toInt() and 0xFF) or
                        ((bytes[65].toInt() and 0xFF) shl 8) or
                        ((bytes[66].toInt() and 0xFF) shl 16) or
                        ((bytes[67].toInt() and 0xFF) shl 24)

                val height = (bytes[68].toInt() and 0xFF) or
                        ((bytes[69].toInt() and 0xFF) shl 8) or
                        ((bytes[70].toInt() and 0xFF) shl 16) or
                        ((bytes[71].toInt() and 0xFF) shl 24)

                if (width in 16..16384 && height in 16..16384) {
                    Pair(width, height)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}