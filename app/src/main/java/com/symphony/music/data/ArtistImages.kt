package com.symphony.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Artist photos from Deezer's public search, which needs no account or key.
 * Only the artist's name is sent. Found addresses are remembered on the phone.
 */
object ArtistImages {
    /** Artist name to image address; an empty string means "looked up, nothing found". */
    private val known = ConcurrentHashMap<String, String>()
    private var loaded = false

    suspend fun find(context: Context, name: String): String? = withContext(Dispatchers.IO) {
        if (name.isBlank() || name == "—") return@withContext null
        val file = File(context.filesDir, "artist_images_v3.json")
        load(file)
        val cached = known[name]
        if (cached != null) return@withContext cached.ifEmpty { null }
        val found = try {
            // A true portrait from Wikipedia first; Deezer's picture is sometimes just an album cover.
            runCatching { ArtistInfos.wikiPortrait(name) }.getOrNull() ?: fetch(name)
        } catch (e: Exception) {
            // Offline: try again another time rather than remembering a miss.
            return@withContext null
        }
        known[name] = found ?: ""
        save(file)
        found
    }

    /** The Wikipedia picture would not load: use Deezer's instead, and remember that. */
    suspend fun fallback(context: Context, name: String): String? = withContext(Dispatchers.IO) {
        val found = try { fetch(name) } catch (e: Exception) { return@withContext null }
        known[name] = found ?: ""
        save(File(context.filesDir, "artist_images_v3.json"))
        found
    }

    /** Wikimedia refuses images to apps that don't say who they are. */
    const val USER_AGENT = "Symphony/1.0 (https://github.com/Giscardkabenee/Symphony; Android music player)"

    @Synchronized
    private fun load(file: File) {
        if (loaded) return
        loaded = true
        try {
            if (!file.exists()) return
            val json = JSONObject(file.readText())
            val names = json.keys()
            while (names.hasNext()) {
                val key = names.next()
                known[key] = json.optString(key)
            }
        } catch (e: Exception) {
            // Start with an empty cache.
        }
    }

    @Synchronized
    private fun save(file: File) {
        try {
            val json = JSONObject()
            for ((key, value) in known) json.put(key, value)
            file.writeText(json.toString())
        } catch (e: Exception) {
            // The cache is optional.
        }
    }

    private fun fetch(name: String): String? {
        val address = "https://api.deezer.com/search/artist?limit=1&q=" + URLEncoder.encode(name, "UTF-8")
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode != 200) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val data = json.optJSONArray("data") ?: return null
            if (data.length() == 0) return null
            val picture = data.getJSONObject(0).optString("picture_big")
            // Deezer's generic silhouette has an empty image id in its address.
            return picture.takeIf { it.isNotBlank() && !it.contains("/artist//") }
        } finally {
            connection.disconnect()
        }
    }
}
