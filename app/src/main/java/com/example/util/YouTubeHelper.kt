package com.example.util

import java.util.regex.Pattern

object YouTubeHelper {

    // Regex to match YouTube standard URLs, shortened youtu.be, shorts, embeds, and lives
    private val YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile(
        "(?:youtu\\.be\\/|(?:m\\.|www\\.)?youtube\\.com\\/(?:embed\\/|v\\/|shorts\\/|live\\/|watch\\?v=|watch\\?.+&v=))([\\w-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts an 11-character YouTube video ID from various URL formats.
     * Supports:
     * - https://www.youtube.com/watch?v=dQw4w9WgXcQ
     * - https://youtu.be/dQw4w9WgXcQ
     * - https://www.youtube.com/shorts/k9GjF8eP210
     * - https://youtube.com/shorts/50_4e0K9_Y8?feature=share
     * - https://m.youtube.com/watch?v=dQw4w9WgXcQ
     * - https://www.youtube.com/embed/dQw4w9WgXcQ
     * - https://www.youtube.com/live/dQw4w9WgXcQ
     * - Raw 11-character video ID
     */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()

        // 1. Raw 11-character video ID
        if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return trimmed
        }

        // 2. YouTube Shorts URL
        if (trimmed.contains("/shorts/")) {
            val afterShorts = trimmed.substringAfter("/shorts/")
            val id = afterShorts.substringBefore("?").substringBefore("&").substringBefore("/").substringBefore("#")
            if (id.length == 11 && id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return id
        }

        // 3. Shortened youtu.be URL
        if (trimmed.contains("youtu.be/")) {
            val afterSlash = trimmed.substringAfter("youtu.be/")
            val id = afterSlash.substringBefore("?").substringBefore("&").substringBefore("/").substringBefore("#")
            if (id.length == 11 && id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return id
        }

        // 4. Standard watch?v= or watch?...&v=
        if (trimmed.contains("v=")) {
            val afterV = trimmed.substringAfter("v=")
            val id = afterV.substringBefore("&").substringBefore("?").substringBefore("/").substringBefore("#")
            if (id.length == 11 && id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return id
        }

        // 5. Embedded /embed/ URL
        if (trimmed.contains("/embed/")) {
            val afterEmbed = trimmed.substringAfter("/embed/")
            val id = afterEmbed.substringBefore("?").substringBefore("&").substringBefore("/").substringBefore("#")
            if (id.length == 11 && id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return id
        }

        // 6. Live /live/ URL
        if (trimmed.contains("/live/")) {
            val afterLive = trimmed.substringAfter("/live/")
            val id = afterLive.substringBefore("?").substringBefore("&").substringBefore("/").substringBefore("#")
            if (id.length == 11 && id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return id
        }

        // 7. Regex pattern matcher fallback
        val matcher = YOUTUBE_VIDEO_ID_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            val id = matcher.group(1)
            if (id != null && id.length == 11) {
                return id
            }
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
     * Includes all critical playback parameters (origin, playsinline=1, enablejsapi=1,
     * widget_referrer, referrerpolicy) to bypass embedding restrictions and prevent
     * "This video is unavailable / Error code: 152-4".
     */
    fun buildEmbedHtml(videoId: String, isShort: Boolean = false): String {
        val loopParam = if (isShort) "&loop=1&playlist=$videoId" else ""
        return """
            <!DOCTYPE html>
            <html lang="en" style="background-color: #000; width: 100%; height: 100%; margin: 0; padding: 0;">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <meta http-equiv="X-UA-Compatible" content="IE=edge">
                <meta name="referrer" content="strict-origin-when-cross-origin">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; background-color: #000; }
                    html, body { width: 100%; height: 100%; overflow: hidden; background: #000; }
                    .video-wrapper { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
                    iframe { width: 100%; height: 100%; border: 0; }
                </style>
            </head>
            <body>
                <div class="video-wrapper">
                    <iframe 
                        id="ytplayer"
                        type="text/html"
                        width="100%"
                        height="100%"
                        src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&controls=1&rel=0&modestbranding=1&fs=1&iv_load_policy=3&origin=https%3A%2F%2Fwww.youtube.com&widget_referrer=https%3A%2F%2Fwww.youtube.com$loopParam"
                        frameborder="0"
                        referrerpolicy="strict-origin-when-cross-origin"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                        allowfullscreen>
                    </iframe>
                </div>
                <script>
                    // Listen for YouTube IFrame API messages to capture restriction errors (e.g. 150, 152, 101, 2, 100)
                    window.addEventListener('message', function(event) {
                        try {
                            var data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                            if (data && (data.event === 'onError' || data.info === 150 || data.info === 152 || data.info === 101 || data.info === 2 || data.info === 100)) {
                                if (window.AndroidBridge && window.AndroidBridge.onPlaybackError) {
                                    window.AndroidBridge.onPlaybackError(parseInt(data.info) || 152);
                                }
                            }
                        } catch(e) {}
                    });

                    // PostMessage play command after iframe loads to ensure instant autoplay
                    window.addEventListener('load', function() {
                        setTimeout(function() {
                            var frame = document.getElementById('ytplayer');
                            if (frame && frame.contentWindow) {
                                try {
                                    frame.contentWindow.postMessage('{"event":"command","func":"playVideo","args":""}', '*');
                                } catch (e) {}
                            }
                        }, 400);
                    });
                </script>
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
            <html lang="en" style="background-color: #000; width: 100%; height: 100%; margin: 0; padding: 0;">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <meta name="referrer" content="no-referrer">
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
