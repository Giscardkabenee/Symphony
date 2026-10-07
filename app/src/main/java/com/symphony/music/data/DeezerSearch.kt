package com.symphony.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Stored in a pseudo-song's path to mark a 30-second catalogue preview. */
const val PREVIEW_MARK = "preview:"

/** A song found in Deezer's public catalogue: details, a 30-second preview and its page. */
data class CatalogTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val preview: String,
    val cover: String,
    val link: String,
) {
    /** The preview as the player sees it: thirty seconds long, with a negative id. */
    fun toSong(): Song = Song(
        id = -(abs(("deezer$id").hashCode().toLong()) + 1),
        title = title,
        artist = artist,
        album = album,
        albumId = -(abs(("deezer$id").hashCode().toLong()) + 1),
        duration = 30_000,
        track = 0,
        year = 0,
        dateAdded = 0,
        path = PREVIEW_MARK,
    )
}

/** Deezer's open search: no account, no key. Only the typed words are sent. */
object DeezerSearch {
    suspend fun search(query: String): List<CatalogTrack> = withContext(Dispatchers.IO) {
        val address = "https://api.deezer.com/search?limit=40&q=" + URLEncoder.encode(query, "UTF-8")
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            if (connection.responseCode != 200) throw IOException("HTTP " + connection.responseCode)
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            val out = ArrayList<CatalogTrack>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val album = item.optJSONObject("album")
                out += CatalogTrack(
                    id = item.optLong("id"),
                    title = item.optString("title"),
                    artist = item.optJSONObject("artist")?.optString("name") ?: "",
                    album = album?.optString("title") ?: "",
                    durationMs = item.optLong("duration") * 1000,
                    preview = item.optString("preview"),
                    cover = album?.optString("cover_big")?.ifBlank { album.optString("cover_medium") } ?: "",
                    link = item.optString("link"),
                )
            }
            out
        } finally {
            connection.disconnect()
        }
    }
}
