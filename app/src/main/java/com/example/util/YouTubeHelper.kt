package com.example.util

import java.util.regex.Pattern

object YouTubeHelper {

    // Regex to match YouTube standard URLs, shortened youtu.be, shorts, embeds, and lives
    private val YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile(
        "(?:youtu\\.be\\/|youtube\\.com\\/(?:embed\\/|v\\/|shorts\\/|live\\/|watch\\?v=|watch\\?.+&v=))([\\w-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts an 11-character YouTube video ID from various URL formats.
     * Supports:
     * - https://www.youtube.com/watch?v=dQw4w9WgXcQ
     * - https://youtu.be/dQw4w9WgXcQ
     * - https://www.youtube.com/shorts/k9GjF8eP210
     * - https://m.youtube.com/watch?v=dQw4w9WgXcQ
     * - https://www.youtube.com/embed/dQw4w9WgXcQ
     * - https://www.youtube.com/live/dQw4w9WgXcQ
     * - Raw 11-character video ID
     */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()

        // Check if raw ID was provided
        if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return trimmed
        }

        // Match regex
        val matcher = YOUTUBE_VIDEO_ID_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }

        // Fallback for tricky URLs with query params
        if (trimmed.contains("v=")) {
            val afterV = trimmed.substringAfter("v=")
            val id = afterV.substringBefore("&").substringBefore("?").substringBefore("#")
            if (id.length == 11) return id
        }

        if (trimmed.contains("/shorts/")) {
            val afterShorts = trimmed.substringAfter("/shorts/")
            val id = afterShorts.substringBefore("?").substringBefore("&").substringBefore("/").substringBefore("#")
            if (id.length == 11) return id
        }

        if (trimmed.contains("youtu.be/")) {
            val afterSlash = trimmed.substringAfter("youtu.be/")
            val id = afterSlash.substringBefore("?").substringBefore("&").substringBefore("#")
            if (id.length == 11) return id
        }

        return null
    }

    /**
     * Returns true if the URL or string represents a YouTube video.
     */
    fun isYouTubeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return extractVideoId(url) != null ||
                url.contains("youtube.com", ignoreCase = true) ||
                url.contains("youtu.be", ignoreCase = true)
    }

    /**
     * Returns true if the URL points to a YouTube Shorts video.
     */
    fun isShortsUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.contains("/shorts/", ignoreCase = true)
    }

    /**
     * Resolves a high quality thumbnail image URL for YouTube videos.
     */
    fun getThumbnailUrl(url: String?): String {
        val videoId = extractVideoId(url)
        return if (videoId != null) {
            "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        } else {
            ""
        }
    }

    /**
     * Builds responsive HTML5 YouTube Iframe Player code.
     */
    fun buildEmbedHtml(videoId: String, isShort: Boolean = false): String {
        val loopParam = if (isShort) "&loop=1&playlist=$videoId" else ""
        return """
            <!DOCTYPE html>
            <html style="background-color: #000; width: 100%; height: 100%; margin: 0; padding: 0;">
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; background-color: #000; }
                    html, body { width: 100%; height: 100%; overflow: hidden; background: #000; }
                    .video-wrapper { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
                    iframe { width: 100%; height: 100%; border: none; }
                </style>
            </head>
            <body>
                <div class="video-wrapper">
                    <iframe 
                        id="ytplayer"
                        type="text/html"
                        src="https://www.youtube-nocookie.com/embed/$videoId?enablejsapi=1&autoplay=1&playsinline=1&controls=1&rel=0&modestbranding=1&fs=1$loopParam"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                        allowfullscreen>
                    </iframe>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Builds HTML5 direct video player for mp4/webm/streaming URLs.
     */
    fun buildDirectVideoHtml(videoUrl: String): String {
        return """
            <!DOCTYPE html>
            <html style="background-color: #000; width: 100%; height: 100%; margin: 0; padding: 0;">
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; background-color: #000; }
                    html, body { width: 100%; height: 100%; overflow: hidden; background: #000; display: flex; align-items: center; justify-content: center; }
                    video { width: 100%; height: 100%; object-fit: contain; }
                </style>
            </head>
            <body>
                <video src="$videoUrl" autoplay playsinline controls loop></video>
            </body>
            </html>
        """.trimIndent()
    }
}
