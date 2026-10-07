package com.symphony.music.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
