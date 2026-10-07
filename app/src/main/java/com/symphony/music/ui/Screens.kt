package com.symphony.music.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symphony.music.PlayerViewModel
import com.symphony.music.R
import com.symphony.music.data.AlbumInfo
import com.symphony.music.data.Flags
import com.symphony.music.data.Song
import com.symphony.music.data.UpdateUi
import com.symphony.music.data.Updater
import com.symphony.music.data.artworkUri
import com.symphony.music.playback.PlaybackInfo

/** Route argument that stands for the built-in Favourites list. */
const val FAVORITES_KEY = "__favorites__"
const val RECENT_KEY = "__recent__"
const val ADDED_KEY = "__added__"
const val MOST_KEY = "__most__"

/** Room left at the bottom of every list for the floating bar. */
private val BarSpace = 210.dp

@Composable
private fun ScreenTitle(text: String, action: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(title: String, hint: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Artwork(album.id, album.title, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(vm: PlayerViewModel, onSettings: () -> Unit, onStack: (String) -> Unit, onMore: (Song) -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val recent = remember(settings.recents, songs) { vm.songsFor(settings.recents) }
    val added = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(50) }
    val most = remember(settings.playCounts, songs) { vm.songsFor(mostPlayedIds(settings.playCounts)) }

    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        item {
            ScreenTitle(stringResource(R.string.tab_home)) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
        if (songs.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_library), stringResource(R.string.empty_library_hint)) }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { StackCard(stringResource(R.string.recently_played), recent) { onStack(RECENT_KEY) } }
                    item { StackCard(stringResource(R.string.recently_added), added) { onStack(ADDED_KEY) } }
                    item { StackCard(stringResource(R.string.most_played), most) { onStack(MOST_KEY) } }
                }
            }
            item { SectionHeader(stringResource(R.string.all_songs)) }
            items(songs.size) { i ->
                val song = songs[i]
                SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) { vm.play(songs, i) }
            }
        }
    }
}

/** The fifty most started songs, most played first. */
private fun mostPlayedIds(counts: Map<Long, Int>): List<Long> =
    counts.entries.sortedByDescending { it.value }.take(50).map { it.key }

/** A collection shown as a small pile: the newest cover in front, two more peeking out behind it. */
@Composable
private fun StackCard(title: String, songs: List<Song>, onClick: () -> Unit) {
    val covers = remember(songs) { songs.distinctBy { it.albumId }.take(3) }
    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(166.dp)) {
            StackLayer(covers.getOrNull(2), Modifier.align(Alignment.TopCenter).fillMaxWidth(0.72f).height(70.dp), 0.5f)
            StackLayer(covers.getOrNull(1), Modifier.align(Alignment.TopCenter).padding(top = 11.dp).fillMaxWidth(0.86f).height(80.dp), 0.78f)
            StackLayer(
                covers.getOrNull(0),
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(144.dp).shadow(12.dp, RoundedCornerShape(18.dp)),
                1f,
                RoundedCornerShape(18.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.songs_count, songs.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StackLayer(song: Song?, modifier: Modifier, opacity: Float, shape: RoundedCornerShape = RoundedCornerShape(14.dp)) {
    if (song != null) {
        Artwork(song.albumId, song.album, modifier.graphicsLayer { alpha = opacity }, shape)
    } else {
        Box(modifier.graphicsLayer { alpha = opacity }.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
    }
}

// ---------------------------------------------------------------- Albums and artists

@Composable
fun AlbumsScreen(vm: PlayerViewModel, onAlbum: (Long) -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BarSpace),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.offset(x = (-20).dp)) { ScreenTitle(stringResource(R.string.tab_albums)) }
        }
        if (albums.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.empty_library)) }
        }
        items(albums.size) { i ->
            val album = albums[i]
            AlbumCard(album) { onAlbum(album.id) }
        }
    }
}

@Composable
fun ArtistsScreen(vm: PlayerViewModel, onArtist: (String) -> Unit) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BarSpace),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.offset(x = (-20).dp)) { ScreenTitle(stringResource(R.string.tab_artists)) }
        }
        if (artists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.empty_library)) }
        }
        items(artists.size) { i ->
            val artist = artists[i]
            Column(
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onArtist(artist.name) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArtistAvatar(artist.name, settings.artistPhotos, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = artist.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.songs_count, artist.songs.size),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Library

@Composable
fun LibraryScreen(vm: PlayerViewModel, onPlaylist: (String) -> Unit, onMore: (Song) -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val recent = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(25) }

    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        item {
            ScreenTitle(stringResource(R.string.tab_library)) {
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_playlist))
                }
            }
        }
        item { SectionHeader(stringResource(R.string.playlists)) }
        item {
            PlaylistRow(
                name = stringResource(R.string.favorites),
                count = settings.favorites.size,
                icon = Icons.Rounded.Favorite,
            ) { onPlaylist(FAVORITES_KEY) }
        }
        val names = settings.playlists.keys.toList()
        items(names.size) { i ->
            val name = names[i]
            PlaylistRow(name, settings.playlists[name]?.size ?: 0, Icons.Rounded.QueueMusic) { onPlaylist(name) }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.recently_added)) }
        }
        items(recent.size) { i ->
            val song = recent[i]
            SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) { vm.play(recent, i) }
        }
    }

    if (creating) {
        NewPlaylistDialog(onDismiss = { creating = false }) { name ->
            vm.createPlaylist(name)
            creating = false
        }
    }
}

@Composable
private fun PlaylistRow(name: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.songs_count, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// ---------------------------------------------------------------- Search

@Composable
fun SearchScreen(vm: PlayerViewModel, onAlbum: (Long) -> Unit, onArtist: (String) -> Unit, onMore: (Song) -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val q = query.trim()
    val foundSongs = remember(q, songs) {
        if (q.isEmpty()) emptyList() else songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) }.take(30)
    }
    val foundAlbums = remember(q, albums) {
        if (q.isEmpty()) emptyList() else albums.filter { it.title.contains(q, true) }.take(10)
    }
    val foundArtists = remember(q, artists) {
        if (q.isEmpty()) emptyList() else artists.filter { it.name.contains(q, true) }.take(10)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (q.isNotEmpty() && foundSongs.isEmpty() && foundAlbums.isEmpty() && foundArtists.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_results, q)) }
        }
        if (foundSongs.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.songs)) }
            items(foundSongs.size) { i ->
                val song = foundSongs[i]
                SongRow(song, onMore = { onMore(song) }) { vm.play(foundSongs, i) }
            }
        }
        if (foundAlbums.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.tab_albums)) }
            items(foundAlbums.size) { i ->
                val album = foundAlbums[i]
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onAlbum(album.id) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(album.id, album.title, Modifier.size(56.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (foundArtists.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.tab_artists)) }
            items(foundArtists.size) { i ->
                val artist = foundArtists[i]
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onArtist(artist.name) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtistAvatar(artist.name, settings.artistPhotos, Modifier.size(56.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Detail screens

@Composable
private fun DetailHeader(
    title: String,
    subtitle: String,
    albumId: Long?,
    label: String,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    artistPhotos: Boolean = false,
    action: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val fallback = colorFor(label)
    val tint by produceState(fallback, albumId) {
        value = albumId?.let { dominantColor(context, artworkUri(it)) } ?: fallback
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Spacer(Modifier.weight(1f))
            action()
        }
        if (albumId != null) {
            Artwork(albumId, label, Modifier.size(210.dp), RoundedCornerShape(16.dp))
        } else {
            ArtistAvatar(label, artistPhotos, Modifier.size(190.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.play), fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.shuffle), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AlbumScreen(vm: PlayerViewModel, id: Long, onBack: () -> Unit, onMore: (Song) -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val album = albums.firstOrNull { it.id == id }
    if (album == null) {
        Column { ScreenTitle(""); EmptyState(stringResource(R.string.empty_list)) }
        return
    }
    val minutes = album.songs.sumOf { it.duration } / 60_000
    val subtitle = buildString {
        append(album.artist)
        if (album.year > 0) append(" · ").append(album.year)
        append(" · ").append(stringResource(R.string.songs_count, album.songs.size))
        append(" · ").append(minutes).append(" min")
    }
    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        item {
            DetailHeader(album.title, subtitle, album.id, album.title, onBack,
                onPlay = { vm.play(album.songs, 0) },
                onShuffle = { vm.play(album.songs, 0, shuffle = true) })
        }
        items(album.songs.size) { i ->
            val song = album.songs[i]
            val active = state.current?.id == song.id
            Row(
                modifier = Modifier.fillMaxWidth().clickable { vm.play(album.songs, i) }.padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (i + 1).toString(),
                    modifier = Modifier.width(28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(formatTime(song.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { onMore(song) }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(vm: PlayerViewModel, name: String, onBack: () -> Unit, onAlbum: (Long) -> Unit, onMore: (Song) -> Unit) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val artist = artists.firstOrNull { it.name == name }
    if (artist == null) {
        Column { ScreenTitle(""); EmptyState(stringResource(R.string.empty_list)) }
        return
    }
    val albumIds = remember(artist) { artist.songs.map { it.albumId }.toSet() }
    val artistAlbums = albums.filter { it.id in albumIds }
    val subtitle = stringResource(R.string.albums_count, artist.albumCount) + " · " + stringResource(R.string.songs_count, artist.songs.size)
    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        item {
            DetailHeader(artist.name, subtitle, null, artist.name, onBack, artistPhotos = settings.artistPhotos,
                onPlay = { vm.play(artist.songs, 0) },
                onShuffle = { vm.play(artist.songs, 0, shuffle = true) })
        }
        if (artistAlbums.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.tab_albums)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(artistAlbums.size) { i ->
                        val album = artistAlbums[i]
                        AlbumCard(album, Modifier.width(140.dp)) { onAlbum(album.id) }
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.songs)) }
        items(artist.songs.size) { i ->
            val song = artist.songs[i]
            SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) { vm.play(artist.songs, i) }
        }
    }
}

@Composable
fun PlaylistScreen(vm: PlayerViewModel, name: String, onBack: () -> Unit, onMore: (Song) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val allSongs by vm.songs.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val isFavorites = name == FAVORITES_KEY
    // Lists the app builds by itself; nothing can be removed from them by hand.
    val smart = name == RECENT_KEY || name == ADDED_KEY || name == MOST_KEY
    val songs = remember(name, settings, allSongs) {
        when (name) {
            RECENT_KEY -> vm.songsFor(settings.recents)
            ADDED_KEY -> allSongs.sortedByDescending { it.dateAdded }.take(50)
            MOST_KEY -> vm.songsFor(mostPlayedIds(settings.playCounts))
            FAVORITES_KEY -> vm.songsFor(settings.favorites)
            else -> vm.songsFor(settings.playlists[name] ?: emptyList())
        }
    }
    val title = when (name) {
        RECENT_KEY -> stringResource(R.string.recently_played)
        ADDED_KEY -> stringResource(R.string.recently_added)
        MOST_KEY -> stringResource(R.string.most_played)
        FAVORITES_KEY -> stringResource(R.string.favorites)
        else -> name
    }
    val minutes = songs.sumOf { it.duration } / 60_000
    val subtitle = stringResource(R.string.songs_count, songs.size) + " · " + minutes + " min"

    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        item {
            DetailHeader(title, subtitle, songs.firstOrNull()?.albumId, title, onBack,
                onPlay = { vm.play(songs, 0) },
                onShuffle = { vm.play(songs, 0, shuffle = true) },
                action = {
                    if (!isFavorites && !smart) {
                        FilledTonalIconButton(onClick = { vm.deletePlaylist(name); onBack() }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_playlist))
                        }
                    }
                })
        }
        if (songs.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_list)) }
        }
        items(songs.size) { i ->
            val song = songs[i]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) { vm.play(songs, i) }
                }
                if (!smart) IconButton(onClick = { if (isFavorites) vm.toggleFavorite(song.id) else vm.removeFromPlaylist(name, song.id) }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BarSpace)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
        item { SettingsLabel(stringResource(R.string.playback)) }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    val rate by PlaybackInfo.sampleRate.collectAsStateWithLifecycle()
                    val runningFloat by PlaybackInfo.floatOutput.collectAsStateWithLifecycle()
                    val info = buildString {
                        append("AudioTrack · ").append(Build.MODEL)
                        if (rate > 0) append(" · ").append("%.1f kHz".format(rate / 1000f))
                        append(" · ").append(if (runningFloat) "32-bit float" else "16-bit PCM")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.output_precision), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !settings.floatOutput,
                            onClick = { vm.setFlag(Flags.FLOAT_OUTPUT, false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) {
                            Text("16-bit PCM")
                        }
                        SegmentedButton(
                            selected = settings.floatOutput,
                            onClick = { vm.setFlag(Flags.FLOAT_OUTPUT, true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) {
                            Text("32-bit float")
                        }
                    }
                    if (settings.floatOutput != runningFloat) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.restart_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Usb, stringResource(R.string.usb_dac), stringResource(R.string.usb_dac_desc), settings.usbDac) { vm.setFlag(Flags.USB_DAC, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.GraphicEq, stringResource(R.string.skip_silence), stringResource(R.string.skip_silence_desc), settings.skipSilence) { vm.setSkipSilence(it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.FastForward, stringResource(R.string.double_tap), stringResource(R.string.double_tap_desc), settings.doubleTapSeek) { vm.setFlag(Flags.DOUBLE_TAP, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Stop, stringResource(R.string.stop_on_close), stringResource(R.string.stop_on_close_desc), settings.stopOnClose) { vm.setFlag(Flags.STOP_ON_CLOSE, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.VolumeOff, stringResource(R.string.hide_volume), stringResource(R.string.hide_volume_desc), settings.hideVolume) { vm.setFlag(Flags.HIDE_VOLUME, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.SurroundSound, stringResource(R.string.spatial), stringResource(R.string.spatial_desc), settings.spatial) { vm.setFlag(Flags.SPATIAL, it) }
            }
        }
        item { SettingsLabel(stringResource(R.string.appearance)) }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DarkMode, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.theme), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    val labels = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        labels.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = settings.theme == index,
                                onClick = { vm.setTheme(index) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                            ) {
                                Text(stringResource(label))
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.AutoAwesome, stringResource(R.string.liquid_glass), stringResource(R.string.liquid_glass_desc), settings.liquidGlass) { vm.setLiquidGlass(it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Fullscreen, stringResource(R.string.full_cover), stringResource(R.string.full_cover_desc), settings.fullCover) { vm.setFullCover(it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Lyrics, stringResource(R.string.synced_lyrics), stringResource(R.string.synced_lyrics_desc), settings.syncedLyrics) { vm.setSyncedLyrics(it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Public, stringResource(R.string.online_lyrics), stringResource(R.string.online_lyrics_desc), settings.onlineLyrics) { vm.setFlag(Flags.ONLINE_LYRICS, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.AccountCircle, stringResource(R.string.artist_photos), stringResource(R.string.artist_photos_desc), settings.artistPhotos) { vm.setFlag(Flags.ARTIST_PHOTOS, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.BlurOn, stringResource(R.string.blur_lyrics), stringResource(R.string.blur_lyrics_desc), settings.blurLyrics) { vm.setFlag(Flags.BLUR_LYRICS, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Menu, stringResource(R.string.classic_bar), stringResource(R.string.classic_bar_desc), settings.classicBar) { vm.setFlag(Flags.CLASSIC_BAR, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Label, stringResource(R.string.hide_labels), stringResource(R.string.hide_labels_desc), settings.hideLabels) { vm.setFlag(Flags.HIDE_LABELS, it) }
            }
        }
        item { SettingsLabel(stringResource(R.string.local_music)) }
        item {
            SettingsCard {
                SettingSwitch(Icons.Rounded.FilterAlt, stringResource(R.string.filter_short), stringResource(R.string.filter_short_desc), settings.filterShort) { vm.setFlag(Flags.FILTER_SHORT, it) }
            }
        }
        item { SettingsLabel(stringResource(R.string.app_section)) }
        item {
            val update by vm.update.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val status = when (update.status) {
                UpdateUi.CHECKING -> stringResource(R.string.update_checking)
                UpdateUi.UP_TO_DATE -> stringResource(R.string.update_none)
                UpdateUi.DOWNLOADING -> stringResource(R.string.update_downloading, update.progress)
                UpdateUi.ERROR -> stringResource(R.string.update_error)
                else -> stringResource(R.string.version_label, Updater.versionName(context), Updater.installedBuild(context))
            }
            val busy = update.status == UpdateUi.CHECKING || update.status == UpdateUi.DOWNLOADING
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.update), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { vm.checkUpdate() }, enabled = !busy) {
                        Text(stringResource(R.string.update_check), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
private fun SettingSwitch(icon: ImageVector, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
