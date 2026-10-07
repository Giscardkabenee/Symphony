package com.symphony.music

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.symphony.music.data.AlbumInfo
import com.symphony.music.data.AppSettings
import com.symphony.music.data.Flags
import com.symphony.music.data.ArtistInfo
import com.symphony.music.data.LyricsData
import com.symphony.music.data.MusicRepository
import com.symphony.music.data.Prefs
import com.symphony.music.data.Song
import com.symphony.music.data.UpdateUi
import com.symphony.music.data.Updater
import com.symphony.music.data.buildAlbums
import com.symphony.music.data.buildArtists
import com.symphony.music.data.loadLyrics
import com.symphony.music.data.loadOnlineLyrics
import com.symphony.music.playback.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    val settings: StateFlow<AppSettings> =
        prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val albums: StateFlow<List<AlbumInfo>> =
        _songs.map { buildAlbums(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<ArtistInfo>> =
        _songs.map { buildArtists(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _lyrics = MutableStateFlow<LyricsData?>(null)
    val lyrics: StateFlow<LyricsData?> = _lyrics.asStateFlow()

    private val _update = MutableStateFlow(UpdateUi())
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private var byId: Map<Long, Song> = emptyMap()
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var lyricsJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
        }
    }

    init {
        connect()
        viewModelScope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    val position = c.currentPosition.coerceAtLeast(0)
                    _state.update { it.copy(position = position) }
                }
                delay(500)
            }
        }
    }

    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                sync()
            } catch (e: Exception) {
                controller = null
            }
        }, ContextCompat.getMainExecutor(app))
    }

    /** Reloads the library from the phone's media store. */
    fun refresh() {
        viewModelScope.launch {
            val filter = prefs.flow.first().filterShort
            val list = MusicRepository.loadSongs(getApplication(), filter)
            byId = list.associateBy { it.id }
            _songs.value = list
            _loaded.value = true
            sync()
        }
    }

    private fun sync() {
        val c = controller ?: return
        val queue = ArrayList<Song>()
        for (i in 0 until c.mediaItemCount) {
            val id = c.getMediaItemAt(i).mediaId.toLongOrNull() ?: continue
            byId[id]?.let { queue += it }
        }
        val current = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { byId[it] }
        val previous = _state.value.current
        val duration = c.duration.takeIf { it > 0 } ?: current?.duration ?: 0L
        _state.value = PlayerState(
            current = current,
            isPlaying = c.isPlaying,
            position = c.currentPosition.coerceAtLeast(0),
            duration = duration,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            queue = queue,
            index = c.currentMediaItemIndex,
        )
        if (current?.id != previous?.id) onSongChanged(current)
    }

    private fun onSongChanged(song: Song?) {
        lyricsJob?.cancel()
        _lyrics.value = null
        if (song == null) return
        lyricsJob = viewModelScope.launch {
            prefs.addRecent(song.id)
            val local = loadLyrics(getApplication(), song)
            _lyrics.value = local
            if (local == null && prefs.flow.first().onlineLyrics) {
                _lyrics.value = loadOnlineLyrics(getApplication(), song)
            }
        }
    }

    private fun Song.toItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .build()
        )
        .build()

    fun play(list: List<Song>, index: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        val start = if (shuffle) list.indices.random() else index.coerceIn(0, list.lastIndex)
        c.setMediaItems(list.map { it.toItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(song))
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toItem())
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }

    /** Restarts the song after 3 seconds of listening, otherwise goes to the previous one. */
    fun previous() { controller?.seekToPrevious() }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.update { it.copy(position = positionMs) }
    }

    /** Jumps forward or back by the given number of milliseconds. */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val limit = c.duration.coerceAtLeast(0)
        seekTo((c.currentPosition + deltaMs).coerceIn(0, limit))
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    /** Empties the queue but keeps the song that is playing. */
    fun clearQueue() {
        val c = controller ?: return
        val current = c.currentMediaItemIndex
        val count = c.mediaItemCount
        if (current + 1 < count) c.removeMediaItems(current + 1, count)
        if (current > 0) c.removeMediaItems(0, current)
    }

    fun setTheme(value: Int) { viewModelScope.launch { prefs.setTheme(value) } }
    fun setLiquidGlass(value: Boolean) { viewModelScope.launch { prefs.setLiquidGlass(value) } }
    fun setFullCover(value: Boolean) { viewModelScope.launch { prefs.setFullCover(value) } }
    fun setSyncedLyrics(value: Boolean) { viewModelScope.launch { prefs.setSyncedLyrics(value) } }
    fun setSkipSilence(value: Boolean) { viewModelScope.launch { prefs.setSkipSilence(value) } }
    fun setFlag(name: String, value: Boolean) {
        viewModelScope.launch {
            prefs.setFlag(name, value)
            if (name == Flags.FILTER_SHORT) refresh()
        }
    }

    fun toggleFavorite(id: Long) { viewModelScope.launch { prefs.toggleFavorite(id) } }
    fun createPlaylist(name: String) { viewModelScope.launch { prefs.createPlaylist(name) } }
    fun deletePlaylist(name: String) { viewModelScope.launch { prefs.deletePlaylist(name) } }
    fun addToPlaylist(name: String, id: Long) { viewModelScope.launch { prefs.addToPlaylist(name, id) } }
    fun removeFromPlaylist(name: String, id: Long) { viewModelScope.launch { prefs.removeFromPlaylist(name, id) } }

    /** Looks for a newer APK online; if there is one, downloads it and opens the installer. */
    fun checkUpdate() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            _update.value = UpdateUi(UpdateUi.CHECKING)
            try {
                val release = Updater.latest()
                if (release == null) {
                    _update.value = UpdateUi(UpdateUi.ERROR)
                    return@launch
                }
                if (release.build <= Updater.installedBuild(app)) {
                    _update.value = UpdateUi(UpdateUi.UP_TO_DATE)
                    return@launch
                }
                _update.value = UpdateUi(UpdateUi.DOWNLOADING, 0)
                val file = Updater.download(app, release.url) { percent ->
                    _update.value = UpdateUi(UpdateUi.DOWNLOADING, percent)
                }
                _update.value = UpdateUi(UpdateUi.IDLE)
                Updater.install(app, file)
            } catch (e: Exception) {
                _update.value = UpdateUi(UpdateUi.ERROR)
            }
        }
    }

    fun songsFor(ids: List<Long>): List<Song> = ids.mapNotNull { byId[it] }

    override fun onCleared() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}
