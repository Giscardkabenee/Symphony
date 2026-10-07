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
}

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
    val favorites: List<Long> = emptyList(),
    val recents: List<Long> = emptyList(),
    /** Song id to number of times it was started. */
    val playCounts: Map<Long, Int> = emptyMap(),
    val playlists: Map<String, List<Long>> = emptyMap(),
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
            favorites = decodeIds(p[FAVORITES]),
            recents = decodeIds(p[RECENTS]),
            playCounts = decodeCounts(p[PLAY_COUNTS]),
            playlists = decodePlaylists(p[PLAYLISTS]),
        )
    }

    suspend fun setTheme(value: Int) { store.edit { it[THEME] = value } }
    suspend fun setLiquidGlass(value: Boolean) { store.edit { it[GLASS] = value } }
    suspend fun setFullCover(value: Boolean) { store.edit { it[FULL_COVER] = value } }
    suspend fun setSyncedLyrics(value: Boolean) { store.edit { it[SYNCED] = value } }
    suspend fun setSkipSilence(value: Boolean) { store.edit { it[SKIP_SILENCE] = value } }

    suspend fun setFlag(name: String, value: Boolean) { store.edit { it[booleanPreferencesKey(name)] = value } }

    suspend fun toggleFavorite(id: Long) {
        store.edit { p ->
            val ids = decodeIds(p[FAVORITES])
            p[FAVORITES] = encodeIds(if (id in ids) ids - id else listOf(id) + ids)
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

        fun decodeIds(raw: String?): List<Long> =
            raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

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
