package com.symphony.music.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long, val text: String)

data class LyricsData(val lines: List<LyricLine>, val synced: Boolean)

private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/** Parses LRC text. Lines without time tags give plain, unsynced lyrics. */
fun parseLyrics(raw: String): LyricsData? {
    val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (text.isEmpty()) return null
    val synced = ArrayList<LyricLine>()
    for (line in text.split('\n')) {
        val tags = timeTag.findAll(line).toList()
        if (tags.isEmpty()) continue
        val content = line.replace(timeTag, "").trim()
        for (tag in tags) {
            val minutes = tag.groupValues[1].toLong()
            val seconds = tag.groupValues[2].toLong()
            val fraction = tag.groupValues[3]
            val millis = when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                else -> fraction.take(3).toLong()
            }
            synced += LyricLine(minutes * 60_000 + seconds * 1000 + millis, content)
        }
    }
    if (synced.size >= 2) {
        return LyricsData(synced.sortedBy { it.timeMs }.filter { it.text.isNotEmpty() }, true)
    }
    val plain = text.split('\n').map { it.trim() }.filterNot { it.startsWith("[") && it.endsWith("]") }
    return if (plain.any { it.isNotEmpty() }) LyricsData(plain.map { LyricLine(0, it) }, false) else null
}

/** Looks for a .lrc file next to the song, then for lyrics embedded in the file's tags. */
suspend fun loadLyrics(context: Context, song: Song): LyricsData? = withContext(Dispatchers.IO) {
    try {
        if (song.path.isNotEmpty()) {
            val sidecar = File(song.path.substringBeforeLast('.') + ".lrc")
            if (sidecar.canRead()) {
                parseLyrics(sidecar.readText())?.let { return@withContext it }
            }
        }
    } catch (e: Exception) {
        // Not readable on this Android version: fall through to embedded tags.
    }
    try {
        val groups = MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(song.uri)).get()
        for (g in 0 until groups.length) {
            val group = groups.get(g)
            for (f in 0 until group.length) {
                val metadata = group.getFormat(f).metadata ?: continue
                for (i in 0 until metadata.length()) {
                    val raw: String? = when (val entry = metadata.get(i)) {
                        is VorbisComment ->
                            if (entry.key.equals("LYRICS", true) || entry.key.equals("UNSYNCEDLYRICS", true)) entry.value else null
                        is TextInformationFrame ->
                            if (entry.id == "USLT") entry.values.joinToString("\n") else null
                        is BinaryFrame ->
                            if (entry.id == "USLT") decodeUslt(entry.data) else null
                        else -> null
                    }
                    if (!raw.isNullOrBlank()) {
                        parseLyrics(raw)?.let { return@withContext it }
                    }
                }
            }
        }
    } catch (e: Exception) {
        // No readable tags.
    }
    null
}

/** Decodes an ID3v2 USLT frame body: encoding, language, description, then the lyrics text. */
private fun decodeUslt(data: ByteArray): String? {
    if (data.size < 5) return null
    val encoding = data[0].toInt()
    val charset: Charset = when (encoding) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }
    val wide = encoding == 1 || encoding == 2
    var i = 4
    // Skip the null-terminated content description.
    if (wide) {
        while (i + 1 < data.size && !(data[i].toInt() == 0 && data[i + 1].toInt() == 0)) i += 2
        i += 2
    } else {
        while (i < data.size && data[i].toInt() != 0) i++
        i += 1
    }
    if (i >= data.size) return null
    return String(data, i, data.size - i, charset).trim('\u0000', ' ', '\n')
}

/**
 * Lyrics from LRCLIB, a free public lyrics database. The song's title, artist, album and
 * duration are sent to lrclib.net. Results are kept on the phone so each song is fetched once.
 */
suspend fun loadOnlineLyrics(context: Context, song: Song): LyricsData? = withContext(Dispatchers.IO) {
    val cache = File(File(context.filesDir, "lyrics").apply { mkdirs() }, "${song.id}.lrc")
    try {
        if (cache.exists()) {
            parseLyrics(cache.readText())?.let { return@withContext it }
        }
    } catch (e: Exception) {
        // Unreadable cache: fetch again.
    }
    val raw = try {
        fetchFromLrclib(song)
    } catch (e: Exception) {
        null
    }
    if (raw.isNullOrBlank()) return@withContext null
    try {
        cache.writeText(raw)
    } catch (e: Exception) {
        // Cache is optional.
    }
    parseLyrics(raw)
}

private fun fetchFromLrclib(song: Song): String? {
    val title = URLEncoder.encode(song.title, "UTF-8")
    val artist = URLEncoder.encode(if (song.artist == "—") "" else song.artist, "UTF-8")
    val album = URLEncoder.encode(if (song.album == "—") "" else song.album, "UTF-8")
    val seconds = song.duration / 1000

    val exact = httpGet("https://lrclib.net/api/get?track_name=$title&artist_name=$artist&album_name=$album&duration=$seconds")
    if (exact != null) {
        pickLyrics(JSONObject(exact))?.let { return it }
    }

    val found = httpGet("https://lrclib.net/api/search?track_name=$title&artist_name=$artist") ?: return null
    val results = JSONArray(found)
    var fallback: String? = null
    for (i in 0 until results.length()) {
        val item = results.getJSONObject(i)
        val close = kotlin.math.abs(item.optDouble("duration", -100.0) - seconds) <= 4
        val synced = if (item.isNull("syncedLyrics")) null else item.optString("syncedLyrics").takeIf { it.isNotBlank() }
        if (close && synced != null) return synced
        if (close && fallback == null) fallback = pickLyrics(item)
    }
    return fallback
}

private fun pickLyrics(item: JSONObject): String? {
    val synced = if (item.isNull("syncedLyrics")) null else item.optString("syncedLyrics").takeIf { it.isNotBlank() }
    if (synced != null) return synced
    return if (item.isNull("plainLyrics")) null else item.optString("plainLyrics").takeIf { it.isNotBlank() }
}

private fun httpGet(address: String): String? {
    val connection = URL(address).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "Symphony (https://github.com/Giscardkabenee/Symphony)")
        if (connection.responseCode == 200) connection.inputStream.bufferedReader().use { it.readText() } else null
    } finally {
        connection.disconnect()
    }
}
