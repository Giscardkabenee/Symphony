package com.symphony.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** A live radio stream. Ids are negative so they never collide with songs on the phone. */
data class Station(
    val id: Long,
    val name: String,
    val url: String,
    val icon: String,
    val country: String,
    val tags: String,
    val bitrate: Int,
) {
    /** The player handles a station as a song without a length. */
    fun toSong(): Song = Song(
        id = id,
        title = name,
        artist = country.ifBlank { "Radio" },
        album = "Radio",
        albumId = id,
        duration = 0,
        track = 0,
        year = 0,
        dateAdded = 0,
        path = "",
        bitrate = bitrate * 1000,
    )
}

/** Radio Browser: a free, community-run directory of stations that needs no account. */
object RadioApi {
    private val servers = listOf(
        "https://de1.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )

    suspend fun top(): List<Station> = get("/json/stations/topclick/60")

    suspend fun byCountry(code: String): List<Station> =
        get("/json/stations/search?countrycode=$code&hidebroken=true&order=clickcount&reverse=true&limit=80")

    suspend fun search(query: String): List<Station> =
        get("/json/stations/search?name=" + URLEncoder.encode(query, "UTF-8") + "&hidebroken=true&order=clickcount&reverse=true&limit=60")

    private suspend fun get(path: String): List<Station> = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (server in servers) {
            try {
                return@withContext parse(http(server + path))
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("no server")
    }

    private fun http(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("User-Agent", "Symphony (https://github.com/Giscardkabenee/Symphony)")
            if (connection.responseCode != 200) throw IOException("HTTP " + connection.responseCode)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<Station> {
        val array = JSONArray(body)
        val out = ArrayList<Station>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            val url = item.optString("url_resolved").ifBlank { item.optString("url") }
            if (name.isEmpty() || url.isBlank()) continue
            val uuid = item.optString("stationuuid").ifBlank { url }
            out += Station(
                id = -(abs(uuid.hashCode().toLong()) + 1),
                name = name,
                url = url,
                icon = item.optString("favicon"),
                country = item.optString("country"),
                tags = item.optString("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", "),
                bitrate = item.optInt("bitrate", 0),
            )
        }
        return out.distinctBy { it.url }
    }
}
