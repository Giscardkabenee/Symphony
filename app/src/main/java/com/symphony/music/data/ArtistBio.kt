package com.symphony.music.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class Release(val title: String, val type: String, val date: String, val cover: String, val link: String)
data class NewsItem(val title: String, val source: String, val link: String, val timeMs: Long)
data class ArtistBio(
    val bio: String? = null,
    val bioUrl: String? = null,
    val fans: Int = 0,
    val latest: Release? = null,
    val news: List<NewsItem> = emptyList(),
)

/**
 * The "About" part of an artist page, like Spotify or Apple Music:
 * a short biography from Wikipedia, the latest release and fan count from Deezer,
 * and recent news from Google News. Only the artist's name is sent.
 * Kept on the phone for 12 hours, and shown from there when offline.
 */
object ArtistInfos {
    private const val FRESH_MS = 12 * 3_600_000L
    private val memory = ConcurrentHashMap<String, Pair<Long, ArtistBio>>()

    suspend fun find(context: Context, name: String): ArtistBio? = withContext(Dispatchers.IO) {
        if (name.isBlank() || name == "—") return@withContext null
        val dir = File(context.filesDir, "artist_info").apply { mkdirs() }
        val file = File(dir, Integer.toHexString(name.lowercase().hashCode()) + ".json")
        val cached = memory[name] ?: runCatching {
            val json = JSONObject(file.readText())
            json.getLong("at") to decode(json)
        }.getOrNull()
        if (cached != null && System.currentTimeMillis() - cached.first < FRESH_MS) {
            memory[name] = cached
            return@withContext cached.second
        }
        val fresh = runCatching { fetch(name) }.getOrNull()
        if (fresh == null || (fresh.bio == null && fresh.latest == null && fresh.news.isEmpty())) {
            return@withContext cached?.second ?: fresh
        }
        val now = System.currentTimeMillis()
        memory[name] = now to fresh
        runCatching { file.writeText(encode(fresh).put("at", now).toString()) }
        fresh
    }

    private suspend fun fetch(name: String): ArtistBio = coroutineScope {
        val bio = async { runCatching { wikipedia(name) }.getOrNull() }
        val deezer = async { runCatching { deezer(name) }.getOrNull() }
        val news = async { runCatching { news(name) }.getOrNull() ?: emptyList() }
        val d = deezer.await()
        val b = bio.await()
        ArtistBio(b?.first, b?.second, d?.first ?: 0, d?.second, news.await())
    }

    // ------------------------------------------------------------ Wikipedia

    private val musicWords = Regex(
        "chant|musici|rappeu|groupe|compositeu|guitari|artiste|DJ|producteur|orchestre|rumba|ndombolo|" +
            "singer|musician|rapper|band|songwriter|guitarist|producer|record|album",
        RegexOption.IGNORE_CASE,
    )

    private fun wikipedia(name: String): Pair<String, String>? {
        val first = if (Locale.getDefault().language == "en") "en" else "fr"
        for (lang in listOf(first, if (first == "fr") "en" else "fr")) {
            val title = wikiTitle(lang, name) ?: continue
            val api = "https://$lang.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1" +
                "&redirects=1&format=json&titles=" + enc(title)
            val pages = JSONObject(get(api) ?: continue).optJSONObject("query")?.optJSONObject("pages") ?: continue
            val page = pages.optJSONObject(pages.keys().next()) ?: continue
            val text = page.optString("extract").trim()
            if (text.length < 40) continue
            val clean = text.replace(Regex("\\s*\\([^()]*\\)"), "").replace(Regex("\n{2,}"), "\n\n")
            return clean to "https://$lang.m.wikipedia.org/wiki/" + enc(page.optString("title", title).replace(' ', '_'))
        }
        return null
    }

    /** A real portrait from the artist's Wikipedia article, when it has one (not a logo or drawing). */
    internal fun wikiPortrait(name: String): String? {
        for (lang in listOf("fr", "en")) {
            val title = wikiTitle(lang, name) ?: continue
            val summary = get("https://$lang.wikipedia.org/api/rest_v1/page/summary/" + enc(title.replace(' ', '_')))
                ?.let { JSONObject(it) } ?: continue
            val thumb = summary.optJSONObject("thumbnail") ?: continue
            val source = thumb.optString("source")
            if (source.isBlank() || source.contains(".svg", ignoreCase = true)) continue
            if (thumb.optInt("width") < 120) continue
            // Wikimedia only serves its standard thumbnail sizes, so the address is used as given.
            return source
        }
        return null
    }

    /** The article that is really about this musician, not a namesake or a disambiguation page. */
    private fun wikiTitle(lang: String, name: String): String? {
        fun fits(title: String): Boolean {
            val summary = get("https://$lang.wikipedia.org/api/rest_v1/page/summary/" + enc(title.replace(' ', '_')))
                ?.let { JSONObject(it) } ?: return false
            if (summary.optString("type") == "disambiguation") return false
            if (!plain(summary.optString("title")).contains(plain(name))) return false
            val about = summary.optString("description") + " " + summary.optString("extract").take(400)
            return musicWords.containsMatchIn(about)
        }
        if (fits(name)) return name
        val search = get(
            "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srlimit=4&format=json&srsearch=" +
                enc("$name " + if (lang == "fr") "musique" else "music")
        ) ?: return null
        val hits = JSONObject(search).optJSONObject("query")?.optJSONArray("search") ?: return null
        for (i in 0 until hits.length()) {
            val title = hits.getJSONObject(i).optString("title")
            if (plain(title).contains(plain(name)) && fits(title)) return title
        }
        return null
    }

    // ------------------------------------------------------------ Deezer

    private fun deezer(name: String): Pair<Int, Release?>? {
        val found = JSONObject(get("https://api.deezer.com/search/artist?limit=5&q=" + enc(name)) ?: return null)
            .optJSONArray("data") ?: return null
        var artist: JSONObject? = null
        for (i in 0 until found.length()) {
            val a = found.getJSONObject(i)
            if (plain(a.optString("name")) == plain(name)) { artist = a; break }
        }
        artist ?: return null
        val id = artist.optLong("id")
        val albums = JSONObject(get("https://api.deezer.com/artist/$id/albums?limit=100") ?: return artist.optInt("nb_fan") to null)
            .optJSONArray("data") ?: JSONArray()
        var best: JSONObject? = null
        for (i in 0 until albums.length()) {
            val a = albums.getJSONObject(i)
            if (best == null || a.optString("release_date") > best.optString("release_date")) best = a
        }
        val latest = best?.let {
            Release(
                title = it.optString("title"),
                type = it.optString("record_type"),
                date = it.optString("release_date"),
                cover = it.optString("cover_big").ifBlank { it.optString("cover_medium") },
                link = it.optString("link"),
            )
        }
        return artist.optInt("nb_fan") to latest
    }

    // ------------------------------------------------------------ News

    private fun news(name: String): List<NewsItem> {
        val fr = Locale.getDefault().language != "en"
        val address = "https://news.google.com/rss/search?q=" + enc("\"$name\"") +
            if (fr) "&hl=fr&gl=CD&ceid=CD:fr" else "&hl=en&gl=US&ceid=US:en"
        val xml = get(address) ?: return emptyList()
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val dates = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        val items = mutableListOf<NewsItem>()
        var inItem = false
        var tag = ""
        var title = ""; var link = ""; var source = ""; var date = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "item") { inItem = true; title = ""; link = ""; source = ""; date = "" }
                }
                XmlPullParser.TEXT -> if (inItem) when (tag) {
                    "title" -> title += parser.text
                    "link" -> link += parser.text
                    "source" -> source += parser.text
                    "pubDate" -> date += parser.text
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        inItem = false
                        val shown = if (source.isNotBlank()) title.removeSuffix(" - $source") else title
                        val time = runCatching { dates.parse(date.trim())?.time }.getOrNull() ?: 0L
                        if (shown.isNotBlank() && link.isNotBlank()) items += NewsItem(shown.trim(), source.trim(), link.trim(), time)
                    }
                    tag = ""
                }
            }
            parser.next()
        }
        return items.sortedByDescending { it.timeMs }.take(5)
    }

    // ------------------------------------------------------------ Helpers

    private fun plain(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("[^a-z0-9]"), "")

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")

    private fun get(address: String): String? {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "Symphony (https://github.com/Giscardkabenee/Symphony)")
            if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(info: ArtistBio) = JSONObject().apply {
        put("bio", info.bio ?: ""); put("bioUrl", info.bioUrl ?: ""); put("fans", info.fans)
        info.latest?.let {
            put("latest", JSONObject().put("title", it.title).put("type", it.type).put("date", it.date).put("cover", it.cover).put("link", it.link))
        }
        put("news", JSONArray().apply {
            info.news.forEach { put(JSONObject().put("title", it.title).put("source", it.source).put("link", it.link).put("time", it.timeMs)) }
        })
    }

    private fun decode(json: JSONObject): ArtistBio {
        val latest = json.optJSONObject("latest")?.let {
            Release(it.optString("title"), it.optString("type"), it.optString("date"), it.optString("cover"), it.optString("link"))
        }
        val list = json.optJSONArray("news") ?: JSONArray()
        val news = (0 until list.length()).map {
            val n = list.getJSONObject(it)
            NewsItem(n.optString("title"), n.optString("source"), n.optString("link"), n.optLong("time"))
        }
        return ArtistBio(json.optString("bio").ifBlank { null }, json.optString("bioUrl").ifBlank { null }, json.optInt("fans"), latest, news)
    }
}
