package com.symphony.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val track: Int,
    val year: Int,
    val dateAdded: Long,
    val path: String,
    /** Bits per second when the system knows it, otherwise 0. */
    val bitrate: Int = 0,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artUri: Uri get() = ArtOverrides.urls[albumId]?.let { Uri.parse(it) } ?: artworkUri(albumId)
}

/** Online pictures that stand in for an album cover, for example a radio station's logo. */
object ArtOverrides {
    val urls = java.util.concurrent.ConcurrentHashMap<Long, String>()
}

data class AlbumInfo(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
)

data class ArtistInfo(
    val name: String,
    val songs: List<Song>,
    val albumCount: Int,
)

fun artworkUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

fun buildAlbums(songs: List<Song>): List<AlbumInfo> =
    songs.groupBy { it.albumId }.map { (id, list) ->
        val first = list.first()
        val artist = list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: first.artist
        AlbumInfo(id, first.album, artist, list.maxOf { it.year }, list.sortedBy { it.track })
    }.sortedBy { it.title.lowercase() }

fun buildArtists(songs: List<Song>): List<ArtistInfo> =
    songs.groupBy { it.artist }.map { (name, list) ->
        ArtistInfo(name, list.sortedBy { it.title.lowercase() }, list.map { it.albumId }.distinct().size)
    }.sortedBy { it.name.lowercase() }

object MusicRepository {

    /** Reads every music file (30 s or longer) known to the system media library. */
    suspend fun loadSongs(context: Context, filterShort: Boolean = true): List<Song> = withContext(Dispatchers.IO) {
        val out = ArrayList<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
        )
        // The bitrate column only exists from Android 11.
        val columns = if (Build.VERSION.SDK_INT >= 30) projection + "bitrate" else projection
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                columns,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iBitrate = c.getColumnIndex("bitrate")
                while (c.moveToNext()) {
                    val duration = c.getLong(iDuration)
                    if (filterShort && duration in 1 until 30_000) continue
                    val artist = c.getString(iArtist)?.takeUnless { it.isBlank() || it == "<unknown>" } ?: "—"
                    out += Song(
                        id = c.getLong(iId),
                        title = c.getString(iTitle) ?: "—",
                        artist = artist,
                        album = c.getString(iAlbum)?.takeUnless { it.isBlank() } ?: "—",
                        albumId = c.getLong(iAlbumId),
                        duration = duration,
                        track = c.getInt(iTrack) % 1000,
                        year = c.getInt(iYear),
                        dateAdded = c.getLong(iAdded),
                        path = c.getString(iData) ?: "",
                        bitrate = if (iBitrate >= 0) c.getInt(iBitrate) else 0,
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted yet: the caller shows the permission screen.
        }
        out
    }
}
