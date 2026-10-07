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

private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")

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

/** Outcome of an online search: lyrics, nothing found, or the reason it could not run. */
class LyricsResult(val data: LyricsData?, val error: String?)

/**
 * Lyrics from LRCLIB, a free public lyrics database. The song's title, artist, album and
 * duration are sent to lrclib.net. Results are kept on the phone so each song is fetched once.
 */
suspend fun loadOnlineLyrics(context: Context, song: Song): LyricsResult = withContext(Dispatchers.IO) {
    val cache = File(File(context.filesDir, "lyrics").apply { mkdirs() }, "${song.id}.lrc")
    try {
        if (cache.exists()) {
            parseLyrics(cache.readText())?.let { return@withContext LyricsResult(it, null) }
        }
    } catch (e: Exception) {
        // Unreadable cache: fetch again.
    }
    val sources: List<(Song) -> String?> = listOf(::fetchFromLrclib, ::fetchFromLyricsPlus, ::fetchFromKugou)
    var raw: String? = null
    var failure: String? = null
    var failures = 0
    for (source in sources) {
        val found = try {
            source(song)
        } catch (e: Exception) {
            failures++
            failure = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
            null
        }
        if (found.isNullOrBlank()) continue
        // Timed lyrics win; plain text is kept only until a timed version turns up.
        if (parseLyrics(found)?.synced == true) {
            raw = found
            break
        }
        if (raw == null) raw = found
    }
    if (raw == null && failures == sources.size) return@withContext LyricsResult(null, failure)
    if (raw.isNullOrBlank()) return@withContext LyricsResult(null, null)
    try {
        cache.writeText(raw)
    } catch (e: Exception) {
        // Cache is optional.
    }
    LyricsResult(parseLyrics(raw), null)
}

private val fileExtension = Regex("""\.(mp3|m4a|flac|ogg|opus|wav|aac)$""", RegexOption.IGNORE_CASE)
private val trackPrefix = Regex("""^\d{1,3}\s*[-._)]\s*""")
private val extraTag = Regex(
    """\s*[(\[][^)\]]*(feat|ft\.|official|officiel|audio|video|vidéo|lyrics|clip|remaster|version|prod)[^)\]]*[)\]]""",
    RegexOption.IGNORE_CASE,
)

/** "03 - Song (Official Video).mp3" becomes "Song". */
private fun cleanTitle(title: String): String =
    title.replace(fileExtension, "").replace(trackPrefix, "").replace(extraTag, "").replace('_', ' ').trim()

private fun fetchFromLrclib(song: Song): String? {
    val artistName = if (song.artist == "—") "" else song.artist.substringBefore(" feat").substringBefore(" ft.").trim()
    val titleName = cleanTitle(song.title).ifEmpty { song.title }
    val title = URLEncoder.encode(titleName, "UTF-8")
    val artist = URLEncoder.encode(artistName, "UTF-8")
    val album = URLEncoder.encode(if (song.album == "—") "" else song.album, "UTF-8")
    val seconds = song.duration / 1000

    val exact = httpGet("https://lrclib.net/api/get?track_name=$title&artist_name=$artist&album_name=$album&duration=$seconds")
    if (exact != null) {
        pickLyrics(JSONObject(exact), true)?.let { return it }
    }
    val queries = ArrayList<String>()
    if (artistName.isNotEmpty()) {
        queries += "https://lrclib.net/api/search?track_name=$title&artist_name=$artist"
        queries += "https://lrclib.net/api/search?q=" + URLEncoder.encode("$artistName $titleName", "UTF-8")
    } else {
        queries += "https://lrclib.net/api/search?q=$title"
    }
    for (query in queries) {
        val found = httpGet(query) ?: continue
        choose(JSONArray(found), seconds)?.let { return it }
    }
    return null
}

/** Best entry of a search: same length and timed first, then same length, then any text. */
private fun choose(results: JSONArray, seconds: Long): String? {
    var sameLength: String? = null
    var anyText: String? = null
    for (i in 0 until results.length()) {
        val item = results.getJSONObject(i)
        val close = kotlin.math.abs(item.optDouble("duration", -100.0) - seconds) <= 6
        if (close) {
            val timed = textOf(item, "syncedLyrics")
            if (timed != null) return timed
            if (sameLength == null) sameLength = textOf(item, "plainLyrics")
        } else if (anyText == null) {
            // A different recording: its timings would be wrong, so keep only the plain text.
            anyText = textOf(item, "plainLyrics")
        }
    }
    return sameLength ?: anyText
}

private fun textOf(item: JSONObject, key: String): String? =
    if (item.isNull(key)) null else item.optString(key).takeIf { it.isNotBlank() }

private fun pickLyrics(item: JSONObject, allowTimed: Boolean): String? {
    if (allowTimed) textOf(item, "syncedLyrics")?.let { return it }
    return textOf(item, "plainLyrics")
}

/** LyricsPlus community mirrors: line-timed lyrics as JSON, tried one after the other. */
private fun fetchFromLyricsPlus(song: Song): String? {
    val titleName = cleanTitle(song.title).ifEmpty { song.title }
    val artistName = if (song.artist == "—") "" else song.artist.split(",", "&", "/").first().trim()
    var query = "/v2/lyrics/get?title=" + URLEncoder.encode(titleName, "UTF-8") + "&artist=" + URLEncoder.encode(artistName, "UTF-8")
    if (song.duration > 0) query += "&duration=" + (song.duration / 1000)
    val mirrors = listOf(
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev",
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
    )
    for (mirror in mirrors) {
        val body = try {
            httpGet(mirror + query)
        } catch (e: Exception) {
            null
        } ?: continue
        val lines = try {
            JSONObject(body).optJSONArray("lyrics")
        } catch (e: Exception) {
            null
        } ?: continue
        val out = StringBuilder()
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            var text = line.optString("text")
            if (text.isBlank()) {
                val parts = line.optJSONArray("syllabus")
                if (parts != null) {
                    val joined = StringBuilder()
                    for (j in 0 until parts.length()) joined.append(parts.optJSONObject(j)?.optString("text") ?: "")
                    text = joined.toString()
                }
            }
            if (text.isBlank()) continue
            val ms = line.optLong("time", 0L)
            out.append("[%02d:%02d.%02d]".format(ms / 60_000, ms / 1000 % 60, ms % 1000 / 10)).append(text.trim()).append('\n')
        }
        if (out.isNotEmpty()) return out.toString()
    }
    return null
}

/** KuGou's lyrics search: first candidate for the title, artist and duration. */
private fun fetchFromKugou(song: Song): String? {
    val artistName = if (song.artist == "—") "" else song.artist
    val keyword = URLEncoder.encode((cleanTitle(song.title).ifEmpty { song.title } + " " + artistName).trim(), "UTF-8")
    val search = httpGet("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=$keyword&duration=${song.duration}&hash=") ?: return null
    val candidates = JSONObject(search).optJSONArray("candidates") ?: return null
    if (candidates.length() == 0) return null
    val best = candidates.getJSONObject(0)
    val id = URLEncoder.encode(best.optString("id"), "UTF-8")
    val key = URLEncoder.encode(best.optString("accesskey"), "UTF-8")
    if (id.isEmpty() || key.isEmpty()) return null
    val download = httpGet("https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$key&fmt=lrc&charset=utf8") ?: return null
    val content = JSONObject(download).optString("content")
    if (content.isBlank()) return null
    return String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), Charsets.UTF_8)
}

private fun httpGet(address: String): String? {
    val connection = URL(address).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("User-Agent", "Symphony (https://github.com/Giscardkabenee/Symphony)")
        if (connection.responseCode == 200) connection.inputStream.bufferedReader().use { it.readText() } else null
    } finally {
        connection.disconnect()
    }
}
