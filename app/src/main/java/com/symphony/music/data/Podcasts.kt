package com.symphony.music.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class Podcast(val id: Long, val title: String, val author: String, val feedUrl: String, val art: String)

/** One episode. Ids are negative, like radio stations, so they never collide with songs. */
data class Episode(val id: Long, val title: String, val url: String, val art: String, val durationMs: Long, val date: String) {
    fun toSong(podcast: Podcast): Song = Song(
        id = id,
        title = title,
        artist = podcast.title,
        album = podcast.title,
        albumId = id,
        duration = durationMs,
        track = 0,
        year = 0,
        dateAdded = 0,
        path = PODCAST_MARK,
    )
}

/** Stored in a pseudo-song's path so the player can tell an episode from a radio stream. */
const val PODCAST_MARK = "podcast:"

/** Shows are found in Apple's public podcast directory; episodes come straight from each show's feed. */
object PodcastApi {

    suspend fun search(term: String): List<Podcast> = withContext(Dispatchers.IO) {
        val body = open("https://itunes.apple.com/search?media=podcast&limit=30&term=" + URLEncoder.encode(term, "UTF-8"))
            .use { it.bufferedReader().readText() }
        val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
        val out = ArrayList<Podcast>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val feed = item.optString("feedUrl")
            if (feed.isBlank()) continue
            out += Podcast(
                id = item.optLong("collectionId"),
                title = item.optString("collectionName"),
                author = item.optString("artistName"),
                feedUrl = feed,
                art = item.optString("artworkUrl600").ifBlank { item.optString("artworkUrl100") },
            )
        }
        out
    }

    suspend fun episodes(podcast: Podcast): List<Episode> = withContext(Dispatchers.IO) {
        open(podcast.feedUrl).use { parseFeed(it, podcast) }
    }

    /** Opens an address, following redirects by hand because feeds often hop between http and https. */
    private fun open(address: String): InputStream {
        var current = address
        repeat(6) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Symphony (https://github.com/Giscardkabenee/Symphony)")
            val code = connection.responseCode
            if (code in 300..399) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                if (next.isNullOrBlank()) throw IOException("redirect without target")
                current = URL(URL(current), next).toString()
            } else if (code == 200) {
                return connection.inputStream
            } else {
                connection.disconnect()
                throw IOException("HTTP $code")
            }
        }
        throw IOException("too many redirects")
    }

    private fun parseFeed(stream: InputStream, podcast: Podcast): List<Episode> {
        val out = ArrayList<Episode>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)
            var inItem = false
            var title = ""
            var url = ""
            var duration = 0L
            var date = ""
            var image = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && out.size < 150) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            inItem = true
                            title = ""
                            url = ""
                            duration = 0L
                            date = ""
                            image = ""
                        }
                        "title" -> if (inItem) title = parser.nextText().trim()
                        "enclosure" -> if (inItem) url = parser.getAttributeValue(null, "url") ?: ""
                        "itunes:duration" -> if (inItem) duration = parseDuration(parser.nextText())
                        "pubDate" -> if (inItem) date = parser.nextText().trim()
                        "itunes:image" -> if (inItem) image = parser.getAttributeValue(null, "href") ?: ""
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "item") {
                    inItem = false
                    if (url.isNotBlank()) {
                        out += Episode(
                            id = -(abs((podcast.feedUrl + url).hashCode().toLong()) + 1),
                            title = title.ifBlank { podcast.title },
                            url = url,
                            art = image.ifBlank { podcast.art },
                            durationMs = duration,
                            date = if (date.length >= 16) date.substring(5, 16) else date,
                        )
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // A malformed feed: keep the episodes read so far.
        }
        return out
    }

    /** Feeds give a length either in seconds or as H:MM:SS. */
    private fun parseDuration(raw: String): Long {
        val parts = raw.trim().split(':').mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty()) return 0L
        var seconds = 0L
        for (part in parts) seconds = seconds * 60 + part
        return seconds * 1000
    }
}
