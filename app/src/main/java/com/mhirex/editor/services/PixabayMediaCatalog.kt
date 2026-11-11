package com.mhirex.editor.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.security.MessageDigest

data class PixabayMediaHit(
    val id: Long,
    val tags: String,
    val creator: String,
    val pageUrl: String,
    val previewUrl: String,
    val downloadUrl: String?,
)

/** User-driven Pixabay discovery with a 24-hour cache for browse and search results. */
object PixabayMediaCatalog {
    private const val CACHE_NAME = "pixabay_search_cache"
    private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L

    suspend fun search(context: Context, query: String, mediaType: String): List<PixabayMediaHit> = withContext(Dispatchers.IO) {
        require(mediaType == "images" || mediaType == "videos")
        val normalizedQuery = query.trim().take(100)
        val cacheKey = sha256("$mediaType\u0000${normalizedQuery.lowercase().ifBlank { "popular" }}")
        val prefs = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cachedAt = prefs.getLong("$cacheKey:time", 0L)
        val cachedJson = prefs.getString("$cacheKey:data", null)
        val body = if (cachedJson != null && now - cachedAt < CACHE_TTL_MS) {
            cachedJson
        } else {
            val queryPart = normalizedQuery.takeIf { it.isNotBlank() }
                ?.let { "&q=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
            val url = java.net.URL("https://mhirex.vercel.app/api/pixabay?type=$mediaType$queryPart")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    Log.w("PixabayMediaCatalog", "Pixabay HTTP $status: ${response.take(200)}")
                    val message = runCatching { JSONObject(response).optString("error") }.getOrNull()
                    throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Pixabay search returned HTTP $status")
                }
                prefs.edit().putString("$cacheKey:data", response).putLong("$cacheKey:time", now).apply()
                response
            } finally {
                connection.disconnect()
            }
        }

        val hits = JSONObject(body).optJSONArray("hits") ?: return@withContext emptyList()
        (0 until hits.length()).mapNotNull { index ->
            val hit = hits.optJSONObject(index) ?: return@mapNotNull null
            val pageUrl = hit.optString("pageURL")
            if (!pageUrl.startsWith("https://pixabay.com/")) return@mapNotNull null
            val downloadUrl = if (mediaType == "videos") {
                hit.optJSONObject("videos")?.optJSONObject("medium")?.optString("url")?.takeIf { it.isNotBlank() }
            } else {
                hit.optString("webformatURL").takeIf { it.isNotBlank() }
            }
            PixabayMediaHit(
                id = hit.optLong("id", -1L),
                tags = hit.optString("tags", "Pixabay media").take(140),
                creator = hit.optString("user", "Pixabay contributor").take(80),
                pageUrl = pageUrl,
                previewUrl = if (mediaType == "videos") {
                    hit.optJSONObject("videos")?.optJSONObject("medium")?.optString("thumbnail")?.takeIf { it.isNotBlank() }.orEmpty()
                } else {
                    hit.optString("previewURL").takeIf { it.isNotBlank() }.orEmpty()
                },
                downloadUrl = downloadUrl,
            )
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
