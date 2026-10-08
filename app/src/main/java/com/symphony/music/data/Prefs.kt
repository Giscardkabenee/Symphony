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
    const val AUTOMIX = "automix"
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
    val favorites: List<Long> = emptyList(),
    val recents: List<Long> = emptyList(),
    /** Song id to number of times it was started. */
    val playCounts: Map<Long, Int> = emptyMap(),
    val playlists: Map<String, List<Long>> = emptyMap(),
    val eqEnabled: Boolean = false,
    /** Gain of each equalizer band, in dB. */
    val eqLevels: List<Int> = List(EqBands) { 0 },
    /** Strength of the bass boost and of the stereo widening, 0 to 1000. */
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    /** First name used in the home greeting; empty for none. */
    val userName: String = "Giscard",
    val automix: Boolean = false,
    /** Length of the AutoMix cross, in seconds. */
    val automixSeconds: Int = 6,
    /** AutoEq correction for the user's headphones, and whether it is on. */
    val headphone: HeadphoneProfile? = null,
    val headphoneOn: Boolean = true,
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
            eqEnabled = p[booleanPreferencesKey("eq_enabled")] ?: false,
            eqLevels = (p[stringPreferencesKey("eq_levels")] ?: "").split(',').mapNotNull { it.toIntOrNull() }
                .let { list -> List(EqBands) { i -> (list.getOrNull(i) ?: 0).coerceIn(-12, 12) } },
            bassBoost = (p[intPreferencesKey("bass_boost")] ?: 0).coerceIn(0, 1000),
            virtualizer = (p[intPreferencesKey("virtualizer")] ?: 0).coerceIn(0, 1000),
            userName = p[stringPreferencesKey("user_name")] ?: "Giscard",
            automix = p[booleanPreferencesKey(Flags.AUTOMIX)] ?: false,
            automixSeconds = (p[intPreferencesKey("automix_seconds")] ?: 6).coerceIn(2, 12),
            headphone = HeadphoneProfile.decode(p[stringPreferencesKey("hp_profile")]),
            headphoneOn = p[booleanPreferencesKey("hp_enabled")] ?: true,
        )
    }

    suspend fun setTheme(value: Int) { store.edit { it[THEME] = value } }
    suspend fun setLiquidGlass(value: Boolean) { store.edit { it[GLASS] = value } }
    suspend fun setFullCover(value: Boolean) { store.edit { it[FULL_COVER] = value } }
    suspend fun setSyncedLyrics(value: Boolean) { store.edit { it[SYNCED] = value } }
    suspend fun setSkipSilence(value: Boolean) { store.edit { it[SKIP_SILENCE] = value } }

    suspend fun setEqLevels(levels: List<Int>) { store.edit { it[stringPreferencesKey("eq_levels")] = levels.joinToString(",") } }

    suspend fun setUserName(value: String) { store.edit { it[stringPreferencesKey("user_name")] = value.trim() } }

    suspend fun setHeadphone(profile: HeadphoneProfile?) {
        store.edit {
            if (profile == null) it.remove(stringPreferencesKey("hp_profile")) else it[stringPreferencesKey("hp_profile")] = profile.encode()
            it[booleanPreferencesKey("hp_enabled")] = true
        }
    }

    suspend fun setInt(name: String, value: Int) { store.edit { it[intPreferencesKey(name)] = value } }

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
