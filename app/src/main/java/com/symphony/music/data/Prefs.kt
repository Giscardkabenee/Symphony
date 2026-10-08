package com.symphony.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Names of the simple on/off settings. */
object Flags {
    const val DOUBLE_TAP = "double_tap_seek"
    const val HIDE_VOLUME = "hide_volume"
    const val HIDE_LABELS = "hide_labels"
    const val CLASSIC_BAR = "classic_bar"
    const val BLUR_LYRICS = "blur_lyrics"
    const val FILTER_SHORT = "filter_short"
    const val STOP_ON_CLOSE = "stop_on_close"
    const val SPATIAL = "spatial_audio"
    const val ONLINE_LYRICS = "online_lyrics"
    const val ARTIST_PHOTOS = "artist_photos"
    const val FLOAT_OUTPUT = "float_output"
    const val USB_DAC = "usb_dac"
    const val WIFI_ONLY = "wifi_only_downloads"
}

/** Number of bands the equalizer screen offers. */
const val EqBands = 5

data class AppSettings(
    /** 0 = system, 1 = light, 2 = dark */
    val theme: Int = 0,
    val liquidGlass: Boolean = true,
    val fullCover: Boolean = true,
    val syncedLyrics: Boolean = true,
    val skipSilence: Boolean = false,
    val doubleTapSeek: Boolean = true,
    val hideVolume: Boolean = false,
    val hideLabels: Boolean = false,
    val classicBar: Boolean = false,
    val blurLyrics: Boolean = true,
    val filterShort: Boolean = true,
    val stopOnClose: Boolean = false,
    val spatial: Boolean = false,
    val onlineLyrics: Boolean = true,
    val artistPhotos: Boolean = true,
    val floatOutput: Boolean = false,
    val usbDac: Boolean = false,
    val wifiOnly: Boolean = false,
    val favorites: List<Long> = emptyList(),
    val recents: List<Long> = emptyList(),
    /** Song id to number of times it was started. */
    val playCounts: Map<Long, Int> = emptyMap(),
    val playlists: Map<String, List<Long>> = emptyMap(),
    /** Favourite radio stations. */
    val stations: List<Station> = emptyList(),
    /** Podcasts the user follows. */
    val podcasts: List<Podcast> = emptyList(),
    /** Where each started episode was left, in milliseconds. */
    val episodePositions: Map<Long, Long> = emptyMap(),
    /** Episodes saved on the phone, newest first. */
    val downloads: List<DownloadedEpisode> = emptyList(),
    /** The podcast episode last listened to, offered again on the home screen. */
    val lastEpisode: DownloadedEpisode? = null,
    val eqEnabled: Boolean = false,
    /** Gain of each equalizer band, in dB. */
    val eqLevels: List<Int> = List(EqBands) { 0 },
    /** Strength of the bass boost and of the stereo widening, 0 to 1000. */
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
)

class Prefs(context: Context) {
    private val store = context.applicationContext.settingsStore

    val flow: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = p[THEME] ?: 0,
            liquidGlass = p[GLASS] ?: true,
            fullCover = p[FULL_COVER] ?: true,
            syncedLyrics = p[SYNCED] ?: true,
            skipSilence = p[SKIP_SILENCE] ?: false,
            doubleTapSeek = p[booleanPreferencesKey(Flags.DOUBLE_TAP)] ?: true,
            hideVolume = p[booleanPreferencesKey(Flags.HIDE_VOLUME)] ?: false,
            hideLabels = p[booleanPreferencesKey(Flags.HIDE_LABELS)] ?: false,
            classicBar = p[booleanPreferencesKey(Flags.CLASSIC_BAR)] ?: false,
            blurLyrics = p[booleanPreferencesKey(Flags.BLUR_LYRICS)] ?: true,
            filterShort = p[booleanPreferencesKey(Flags.FILTER_SHORT)] ?: true,
            stopOnClose = p[booleanPreferencesKey(Flags.STOP_ON_CLOSE)] ?: false,
            spatial = p[booleanPreferencesKey(Flags.SPATIAL)] ?: false,
            onlineLyrics = p[booleanPreferencesKey(Flags.ONLINE_LYRICS)] ?: true,
            artistPhotos = p[booleanPreferencesKey(Flags.ARTIST_PHOTOS)] ?: true,
            floatOutput = p[booleanPreferencesKey(Flags.FLOAT_OUTPUT)] ?: false,
            usbDac = p[booleanPreferencesKey(Flags.USB_DAC)] ?: false,
            wifiOnly = p[booleanPreferencesKey(Flags.WIFI_ONLY)] ?: false,
            favorites = decodeIds(p[FAVORITES]),
            recents = decodeIds(p[RECENTS]),
            playCounts = decodeCounts(p[PLAY_COUNTS]),
            playlists = decodePlaylists(p[PLAYLISTS]),
            stations = decodeStations(p[STATIONS]),
            podcasts = decodePodcasts(p[PODCASTS]),
            episodePositions = decodePositions(p[EPISODE_POSITIONS]),
            downloads = decodeDownloads(p[DOWNLOADS]),
            lastEpisode = decodeDownloads(p[LAST_EPISODE]).firstOrNull(),
            eqEnabled = p[booleanPreferencesKey("eq_enabled")] ?: false,
            eqLevels = (p[stringPreferencesKey("eq_levels")] ?: "").split(',').mapNotNull { it.toIntOrNull() }
                .let { list -> List(EqBands) { i -> (list.getOrNull(i) ?: 0).coerceIn(-12, 12) } },
            bassBoost = (p[intPreferencesKey("bass_boost")] ?: 0).coerceIn(0, 1000),
            virtualizer = (p[intPreferencesKey("virtualizer")] ?: 0).coerceIn(0, 1000),
        )
    }

    suspend fun setTheme(value: Int) { store.edit { it[THEME] = value } }
    suspend fun setLiquidGlass(value: Boolean) { store.edit { it[GLASS] = value } }
    suspend fun setFullCover(value: Boolean) { store.edit { it[FULL_COVER] = value } }
    suspend fun setSyncedLyrics(value: Boolean) { store.edit { it[SYNCED] = value } }
    suspend fun setSkipSilence(value: Boolean) { store.edit { it[SKIP_SILENCE] = value } }

    suspend fun setEqLevels(levels: List<Int>) { store.edit { it[stringPreferencesKey("eq_levels")] = levels.joinToString(",") } }

    suspend fun setInt(name: String, value: Int) { store.edit { it[intPreferencesKey(name)] = value } }

    suspend fun setFlag(name: String, value: Boolean) { store.edit { it[booleanPreferencesKey(name)] = value } }

    suspend fun toggleFavorite(id: Long) {
        store.edit { p ->
            val ids = decodeIds(p[FAVORITES])
            p[FAVORITES] = encodeIds(if (id in ids) ids - id else listOf(id) + ids)
        }
    }

    suspend fun toggleStation(station: Station) {
        store.edit { p ->
            val list = decodeStations(p[STATIONS])
            val next = if (list.any { it.id == station.id }) list.filter { it.id != station.id } else listOf(station) + list
            val array = JSONArray()
            for (s in next) {
                array.put(
                    JSONObject().put("id", s.id).put("name", s.name).put("url", s.url).put("icon", s.icon)
                        .put("country", s.country).put("tags", s.tags).put("bitrate", s.bitrate)
                )
            }
            p[STATIONS] = array.toString()
        }
    }

    suspend fun togglePodcast(podcast: Podcast) {
        store.edit { p ->
            val list = decodePodcasts(p[PODCASTS])
            val next = if (list.any { it.id == podcast.id }) list.filter { it.id != podcast.id } else listOf(podcast) + list
            val array = JSONArray()
            for (item in next) {
                array.put(JSONObject().put("id", item.id).put("title", item.title).put("author", item.author).put("feed", item.feedUrl).put("art", item.art))
            }
            p[PODCASTS] = array.toString()
        }
    }

    suspend fun addDownload(item: DownloadedEpisode) {
        store.edit { p ->
            val list = listOf(item) + decodeDownloads(p[DOWNLOADS]).filter { it.episode.id != item.episode.id }
            p[DOWNLOADS] = encodeDownloads(list)
        }
    }

    suspend fun setLastEpisode(item: DownloadedEpisode?) {
        store.edit { p -> if (item == null) p.remove(LAST_EPISODE) else p[LAST_EPISODE] = encodeDownloads(listOf(item)) }
    }

    suspend fun removeDownload(id: Long) {
        store.edit { p -> p[DOWNLOADS] = encodeDownloads(decodeDownloads(p[DOWNLOADS]).filter { it.episode.id != id }) }
    }

    suspend fun saveEpisodePosition(id: Long, positionMs: Long) {
        store.edit { p ->
            val map = LinkedHashMap(decodePositions(p[EPISODE_POSITIONS]))
            map.remove(id)
            map[id] = positionMs
            // Keep the hundred most recent episodes.
            val recent = map.entries.toList().takeLast(100)
            p[EPISODE_POSITIONS] = recent.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    suspend fun addRecent(id: Long) {
        store.edit { p ->
            val ids = decodeIds(p[RECENTS])
            p[RECENTS] = encodeIds((listOf(id) + (ids - id)).take(50))
            val counts = decodeCounts(p[PLAY_COUNTS]).toMutableMap()
            counts[id] = (counts[id] ?: 0) + 1
            p[PLAY_COUNTS] = counts.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    suspend fun createPlaylist(name: String) = editPlaylists { if (name !in it) it[name] = emptyList() }

    suspend fun setPlaylistOrder(name: String, ids: List<Long>) = editPlaylists { if (name in it) it[name] = ids }

    suspend fun setFavorites(ids: List<Long>) { store.edit { it[FAVORITES] = encodeIds(ids) } }

    suspend fun deletePlaylist(name: String) = editPlaylists { it.remove(name) }

    suspend fun addToPlaylist(name: String, id: Long) = editPlaylists {
        val ids = it[name] ?: emptyList()
        if (id !in ids) it[name] = ids + id
    }

    suspend fun removeFromPlaylist(name: String, id: Long) = editPlaylists {
        it[name] = (it[name] ?: emptyList()) - id
    }

    private suspend fun editPlaylists(block: (MutableMap<String, List<Long>>) -> Unit) {
        store.edit { p ->
            val map = decodePlaylists(p[PLAYLISTS]).toMutableMap()
            block(map)
            val json = JSONObject()
            map.forEach { (name, ids) -> json.put(name, JSONArray(ids)) }
            p[PLAYLISTS] = json.toString()
        }
    }

    private companion object {
        val THEME = intPreferencesKey("theme")
        val GLASS = booleanPreferencesKey("liquid_glass")
        val FULL_COVER = booleanPreferencesKey("full_cover")
        val SYNCED = booleanPreferencesKey("synced_lyrics")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val FAVORITES = stringPreferencesKey("favorites")
        val RECENTS = stringPreferencesKey("recents")
        val PLAY_COUNTS = stringPreferencesKey("play_counts")
        val PLAYLISTS = stringPreferencesKey("playlists")
        val STATIONS = stringPreferencesKey("radio_stations")
        val PODCASTS = stringPreferencesKey("podcasts")
        val DOWNLOADS = stringPreferencesKey("podcast_downloads")
        val LAST_EPISODE = stringPreferencesKey("last_episode")
        val EPISODE_POSITIONS = stringPreferencesKey("episode_positions")

        fun decodeIds(raw: String?): List<Long> =
            raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

        fun decodeStations(raw: String?): List<Station> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    Station(o.getLong("id"), o.optString("name"), o.optString("url"), o.optString("icon"), o.optString("country"), o.optString("tags"), o.optInt("bitrate"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun decodePodcasts(raw: String?): List<Podcast> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    Podcast(o.getLong("id"), o.optString("title"), o.optString("author"), o.optString("feed"), o.optString("art"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun encodeDownloads(list: List<DownloadedEpisode>): String {
            val array = JSONArray()
            for (item in list) {
                array.put(
                    JSONObject()
                        .put("id", item.episode.id).put("title", item.episode.title).put("url", item.episode.url)
                        .put("art", item.episode.art).put("duration", item.episode.durationMs).put("date", item.episode.date)
                        .put("pid", item.podcast.id).put("ptitle", item.podcast.title).put("pauthor", item.podcast.author)
                        .put("pfeed", item.podcast.feedUrl).put("part", item.podcast.art)
                )
            }
            return array.toString()
        }

        fun decodeDownloads(raw: String?): List<DownloadedEpisode> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    DownloadedEpisode(
                        Podcast(o.optLong("pid"), o.optString("ptitle"), o.optString("pauthor"), o.optString("pfeed"), o.optString("part")),
                        Episode(o.getLong("id"), o.optString("title"), o.optString("url"), o.optString("art"), o.optLong("duration"), o.optString("date")),
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun decodePositions(raw: String?): Map<Long, Long> {
            if (raw.isNullOrBlank()) return emptyMap()
            val out = LinkedHashMap<Long, Long>()
            for (part in raw.split(',')) {
                val id = part.substringBefore(':').toLongOrNull() ?: continue
                val position = part.substringAfter(':', "").toLongOrNull() ?: continue
                out[id] = position
            }
            return out
        }

        fun decodeCounts(raw: String?): Map<Long, Int> {
            if (raw.isNullOrBlank()) return emptyMap()
            val out = HashMap<Long, Int>()
            for (part in raw.split(',')) {
                val id = part.substringBefore(':').toLongOrNull() ?: continue
                val count = part.substringAfter(':', "").toIntOrNull() ?: continue
                out[id] = count
            }
            return out
        }

        fun encodeIds(ids: List<Long>): String = ids.joinToString(",")

        fun decodePlaylists(raw: String?): Map<String, List<Long>> {
            if (raw.isNullOrBlank()) return emptyMap()
            return try {
                val json = JSONObject(raw)
                val out = sortedMapOf<String, List<Long>>()
                val names = json.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    val array = json.getJSONArray(name)
                    out[name] = (0 until array.length()).map { array.getLong(it) }
                }
                out
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}
