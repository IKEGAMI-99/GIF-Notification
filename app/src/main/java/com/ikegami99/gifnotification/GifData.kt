package com.ikegami99.gifnotification

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class GifItem(
    val id: String,
    val title: String,
    val previewUrl: String,
    val originalUrl: String,
)

object GiphyClient {
    suspend fun search(apiKey: String, query: String): List<GifItem> = withContext(Dispatchers.IO) {
        val endpoint = if (query.isBlank()) {
            "https://api.giphy.com/v1/gifs/trending?api_key=${enc(apiKey)}&limit=30&rating=g"
        } else {
            "https://api.giphy.com/v1/gifs/search?api_key=${enc(apiKey)}&q=${enc(query)}&limit=30&rating=g&lang=ja"
        }

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GIPHY API error: HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<GifItem> {
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val images = obj.optJSONObject("images") ?: continue
                val original = images.optJSONObject("original")?.optString("url").orEmpty()
                val preview = images.optJSONObject("fixed_width")?.optString("url")
                    ?: images.optJSONObject("downsized")?.optString("url")
                    ?: original
                if (original.isBlank() || preview.isBlank()) continue
                add(
                    GifItem(
                        id = obj.optString("id", "gif-$i"),
                        title = obj.optString("title", "GIF"),
                        previewUrl = preview,
                        originalUrl = original,
                    )
                )
            }
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

class GifStore(context: Context) {
    private val prefs = context.getSharedPreferences("gif_notification", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("giphy_api_key", "").orEmpty()
        set(value) { prefs.edit().putString("giphy_api_key", value.trim()).apply() }

    fun favorites(): List<GifItem> = readList("favorites")

    fun history(): List<GifItem> = readList("history")

    fun isFavorite(item: GifItem): Boolean = favorites().any { it.id == item.id }

    fun toggleFavorite(item: GifItem): Boolean {
        val list = favorites().toMutableList()
        val existing = list.indexOfFirst { it.id == item.id }
        val nowFavorite = existing < 0
        if (nowFavorite) list.add(0, item) else list.removeAt(existing)
        writeList("favorites", list.take(100))
        return nowFavorite
    }

    fun addHistory(item: GifItem) {
        val list = history().filterNot { it.id == item.id }.toMutableList()
        list.add(0, item)
        writeList("history", list.take(100))
    }

    private fun readList(key: String): List<GifItem> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    GifItem(
                        id = obj.getString("id"),
                        title = obj.optString("title", "GIF"),
                        previewUrl = obj.getString("previewUrl"),
                        originalUrl = obj.getString("originalUrl"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun writeList(key: String, list: List<GifItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("previewUrl", item.previewUrl)
                    .put("originalUrl", item.originalUrl)
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
