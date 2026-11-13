package com.example.data

import java.net.URLDecoder
import java.util.regex.Pattern

enum class LinkSource(val displayName: String) {
    YOUTUBE("YouTube"),
    TIKTOK("TikTok"),
    GOOGLE("Google"),
    WEB("Web Link")
}

object LinkDetector {

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    fun isValidMediaInput(raw: String): Boolean {
        val norm = normalizeUrl(raw)
        if (!norm.startsWith("http://", ignoreCase = true) && !norm.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val hostPart = norm.substringAfter("://").substringBefore("/")
        return hostPart.contains(".") && hostPart.length >= 4
    }

    fun detectSource(rawUrl: String): LinkSource {
        val url = unwrapGoogleUrl(normalizeUrl(rawUrl)).lowercase()
        return when {
            url.contains("youtube.com") || url.contains("youtu.be") || url.contains("youtube-nocookie.com") ->
                LinkSource.YOUTUBE
            url.contains("tiktok.com") ->
                LinkSource.TIKTOK
            url.contains("google.com") || url.contains("goo.gl") || url.contains("g.co") ||
                url.contains("share.google") || url.contains("googleapis.com") || url.contains("googleusercontent.com") ->
                LinkSource.GOOGLE
            else ->
                LinkSource.WEB
        }
    }

    fun extractYouTubeVideoId(url: String): String? {
        val patterns = listOf(
            "(?:youtu\\.be\\/)([a-zA-Z0-9_-]{11})",
            "youtube\\.com\\/(?:watch\\?(?:.*&)?v=|shorts\\/|embed\\/|live\\/|v\\/)([a-zA-Z0-9_-]{11})",
            "music\\.youtube\\.com\\/watch\\?(?:.*&)?v=([a-zA-Z0-9_-]{11})"
        )
        for (p in patterns) {
            val matcher = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    fun extractGoogleDriveFileId(url: String): String? {
        val matcher = Pattern.compile(
            "(?:drive\\.google\\.com\\/(?:file\\/d\\/|open\\?(?:.*&)?id=|uc\\?(?:.*&)?id=|thumbnail\\?(?:.*&)?id=))([a-zA-Z0-9_-]{10,})",
            Pattern.CASE_INSENSITIVE
        ).matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Unwraps Google redirect links such as:
     * https://www.google.com/url?sa=t&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D...
     */
    fun unwrapGoogleUrl(url: String): String {
        val lower = url.lowercase()
        if (lower.contains("google.com/url") || lower.contains("google.com/imgres")) {
            val query = url.substringAfter("?", "")
            if (query.isNotBlank()) {
                val params = query.split("&")
                for (param in params) {
                    val key = param.substringBefore("=").lowercase()
                    val value = param.substringAfter("=", "")
                    if ((key == "url" || key == "q" || key == "imgurl") && value.isNotBlank()) {
                        return try {
                            val decoded = URLDecoder.decode(value, "UTF-8")
                            if (decoded.startsWith("http://") || decoded.startsWith("https://")) decoded else url
                        } catch (_: Exception) {
                            url
                        }
                    }
                }
            }
        }
        return url
    }
}
