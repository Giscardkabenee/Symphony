package com.symphony.music

import android.app.Application
import android.content.ComponentName
import android.net.Uri
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
import com.symphony.music.data.ArtOverrides
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
    /** Radio stations started in this session, keyed by their negative id. */
    private val live = HashMap<Long, Song>()
    private var ticks = 0
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var lyricsJob: Job? = null

    /** 0 = nothing to show, 1 = searching, 2 = found, 3 = none found, 4 = search failed. */
    private val _lyricsStatus = MutableStateFlow(0)
    val lyricsStatus: StateFlow<Int> = _lyricsStatus.asStateFlow()
    private val _lyricsError = MutableStateFlow("")
    val lyricsError: StateFlow<String> = _lyricsError.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
        }
    }

    init {
        connect()
        // Podcast episodes saved by earlier versions are no longer used.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val app = getApplication<Application>()
            java.io.File(app.getExternalFilesDir(null) ?: app.filesDir, "podcasts").deleteRecursively()
        }
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
            val before = _songs.value.map { it.id }.toHashSet()
            val wasLoaded = _loaded.value
            byId = list.associateBy { it.id }
            _songs.value = list
            _loaded.value = true
            sync()
            // Tell when songs arrived while the app was open.
            val fresh = list.count { it.id !in before }
            if (wasLoaded && before.isNotEmpty() && fresh > 0) {
                val app = getApplication<Application>()
                val text = app.resources.getQuantityString(R.plurals.new_songs, fresh, fresh)
                android.widget.Toast.makeText(app, text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var refreshJob: Job? = null

    /** Several changes often arrive together (a download, then its tags): wait a moment, then reload once. */
    private fun scheduleRefresh(delayMs: Long = 1200) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(delayMs)
            if (_loaded.value) refresh()
        }
    }

    /** The system's music library changed: a song was added, removed or edited. */
    private val libraryWatcher = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduleRefresh()
        }
    }

    // Registered here, after the watcher above exists (init blocks run in the order they are written).
    init {
        getApplication<Application>().contentResolver.registerContentObserver(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, libraryWatcher,
        )
    }

    /**
     * Back in the app: recent audio files in Download and Music that the system has not indexed yet
     * are handed to the media scanner (the watcher then reloads the library), and the list is checked.
     */
    fun onResume() {
        if (!_loaded.value) return
        val app = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val known = _songs.value.map { it.path }.toHashSet()
                val types = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "wma", "amr")
                val recent = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
                val fresh = listOf(android.os.Environment.DIRECTORY_DOWNLOADS, android.os.Environment.DIRECTORY_MUSIC)
                    .map { android.os.Environment.getExternalStoragePublicDirectory(it) }
                    .flatMap { dir ->
                        dir.walkTopDown().maxDepth(3)
                            .filter { it.isFile && it.extension.lowercase() in types && it.lastModified() > recent && it.absolutePath !in known }
                            .toList()
                    }
                if (fresh.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(app, fresh.map { it.absolutePath }.toTypedArray(), null, null)
                }
            } catch (e: Exception) {
                // Folders not readable: the system's own indexing still applies.
            }
        }
        scheduleRefresh(300)
    }

    private fun sync() {
        val c = controller ?: return
        val queue = ArrayList<Song>()
        for (i in 0 until c.mediaItemCount) {
            val id = c.getMediaItemAt(i).mediaId.toLongOrNull() ?: continue
            (byId[id] ?: live[id])?.let { queue += it }
        }
        val current = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { byId[it] ?: live[it] }
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
        _lyricsStatus.value = 0
        if (song == null) return
        if (song.id < 0) {
            // A live stream: no lyrics, no play count.
            _lyricsStatus.value = 3
            return
        }
        lyricsJob = viewModelScope.launch {
            prefs.addRecent(song.id)
            _lyricsStatus.value = 1
            val local = loadLyrics(getApplication(), song)
            if (local != null) {
                _lyrics.value = local
                _lyricsStatus.value = 2
            } else if (prefs.flow.first().onlineLyrics) {
                val result = loadOnlineLyrics(getApplication(), song)
                _lyrics.value = result.data
                _lyricsError.value = result.error ?: ""
                _lyricsStatus.value = when {
                    result.data != null -> 2
                    result.error != null -> 4
                    else -> 3
                }
            } else {
                _lyricsStatus.value = 3
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
                .setTrackNumber(track)
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

    /** Adds a song at the end of the play queue. */
    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(song)) else c.addMediaItem(song.toItem())
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

    /** Searches again for the current song's lyrics. */
    fun retryLyrics() {
        onSongChanged(_state.value.current)
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
    fun setEqLevels(levels: List<Int>) { viewModelScope.launch { prefs.setEqLevels(levels) } }
    fun setHeadphone(profile: com.symphony.music.data.HeadphoneProfile?) { viewModelScope.launch { prefs.setHeadphone(profile) } }
    fun setUserName(value: String) { viewModelScope.launch { prefs.setUserName(value) } }
    fun setEffect(name: String, value: Int) { viewModelScope.launch { prefs.setInt(name, value) } }
    fun setFlag(name: String, value: Boolean) {
        viewModelScope.launch {
            prefs.setFlag(name, value)
            if (name == Flags.FILTER_SHORT) refresh()
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            if (id >= 0) prefs.toggleFavorite(id)
        }
    }

    /** Moves a song in the play queue, as finished by a drag. */
    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) c.moveMediaItem(from, to)
    }

    fun setPlaylistOrder(name: String, ids: List<Long>) {
        viewModelScope.launch { if (name == "__favorites__") prefs.setFavorites(ids) else prefs.setPlaylistOrder(name, ids) }
    }

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
        getApplication<Application>().contentResolver.unregisterContentObserver(libraryWatcher)
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}
