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

data class AppSettings(
    /** 0 = system, 1 = light, 2 = dark */
    val theme: Int = 0,
    val liquidGlass: Boolean = true,
    val fullCover: Boolean = true,
    val syncedLyrics: Boolean = true,
    val skipSilence: Boolean = false,
    val favorites: List<Long> = emptyList(),
    val recents: List<Long> = emptyList(),
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
            favorites = decodeIds(p[FAVORITES]),
            recents = decodeIds(p[RECENTS]),
            playlists = decodePlaylists(p[PLAYLISTS]),
        )
    }

    suspend fun setTheme(value: Int) { store.edit { it[THEME] = value } }
    suspend fun setLiquidGlass(value: Boolean) { store.edit { it[GLASS] = value } }
    suspend fun setFullCover(value: Boolean) { store.edit { it[FULL_COVER] = value } }
    suspend fun setSyncedLyrics(value: Boolean) { store.edit { it[SYNCED] = value } }
    suspend fun setSkipSilence(value: Boolean) { store.edit { it[SKIP_SILENCE] = value } }

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
        val PLAYLISTS = stringPreferencesKey("playlists")

        fun decodeIds(raw: String?): List<Long> =
            raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

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
