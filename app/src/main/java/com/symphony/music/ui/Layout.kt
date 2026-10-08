package com.symphony.music.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symphony.music.PlayerViewModel
import com.symphony.music.R
import com.symphony.music.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Section title with a text link on its right. */
@Composable
private fun SectionLink(title: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) {
            Text(action, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small capital label that separates groups of search results. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

// ---------------------------------------------------------------- Home

/** One of the six shortcuts at the top of the home screen. */
@Composable
private fun QuickTile(title: String, modifier: Modifier, onClick: () -> Unit, art: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(58.dp)) { art() }
        Spacer(Modifier.width(10.dp))
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    onSettings: () -> Unit,
    onStack: (String) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val recent = remember(settings.recents, songs) { vm.songsFor(settings.recents) }
    val added = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(50) }
    val most = remember(settings.playCounts, songs) { vm.songsFor(mostPlayedIds(settings.playCounts)) }
    val favorites = remember(settings.favorites, songs) { vm.songsFor(settings.favorites) }
    // Two albums for the grid: the ones played last, else the ones added last.
    val tileAlbums = remember(recent, added, albums) {
        (recent + added).map { it.albumId }.distinct().mapNotNull { id -> albums.firstOrNull { it.id == id } }.take(2)
    }
    // Most played songs, else the newest ones.
    val topSongs = remember(most, added) { (most.ifEmpty { added }).take(12) }
    // Artists by plays, else by number of songs.
    val topArtists = remember(settings.playCounts, artists) {
        val plays = artists.associateWith { a -> a.songs.sumOf { settings.playCounts[it.id] ?: 0 } }
        artists.sortedWith(compareByDescending<com.symphony.music.data.ArtistInfo> { plays[it] ?: 0 }.thenByDescending { it.songs.size }).take(10)
    }
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(if (hour in 5..17) R.string.greet_day else R.string.greet_evening)

    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        stickyHeader {
            ScreenTitle(greeting, stringResource(R.string.home_count, songs.size)) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
        if (songs.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_library), stringResource(R.string.empty_library_hint)) }
            return@LazyColumn
        }
        item {
            val tiles = buildList<Pair<String, Pair<() -> Unit, @Composable () -> Unit>>> {
                add(stringResource(R.string.favorites) to ({ onStack(FAVORITES_KEY) } to @Composable {
                    Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF5B3CC4), Color(0xFFB57BE8)))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White) }
                }))
                listOf(
                    Triple(R.string.recently_played, RECENT_KEY, recent),
                    Triple(R.string.recently_added, ADDED_KEY, added),
                    Triple(R.string.most_played, MOST_KEY, most),
                ).forEach { (label, key, list) ->
                    val first = list.firstOrNull()
                    add(stringResource(label) to ({ onStack(key) } to @Composable {
                        if (first != null) Artwork(first.albumId, first.album, Modifier.fillMaxSize(), RectangleShape)
                        else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    }))
                }
                tileAlbums.forEach { album ->
                    add(album.title to ({ onAlbum(album.id) } to @Composable {
                        Artwork(album.id, album.title, Modifier.fillMaxSize(), RectangleShape)
                    }))
                }
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (title, action) -> QuickTile(title, Modifier.weight(1f), action.first, action.second) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (topSongs.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.fav_songs)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(topSongs.size) { i ->
                        val song = topSongs[i]
                        val active = state.current?.id == song.id
                        Column(Modifier.width(136.dp).clip(RoundedCornerShape(16.dp)).clickable { vm.play(topSongs, i) }) {
                            Artwork(song.albumId, song.album, Modifier.size(136.dp), RoundedCornerShape(16.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (topArtists.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.your_artists)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(topArtists.size) { i ->
                        val artist = topArtists[i]
                        Column(
                            modifier = Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable { onArtist(artist.name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ArtistAvatar(artist.name, settings.artistPhotos, Modifier.size(72.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Music

@Composable
private fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.onSurface else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, maxLines = 1, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) scheme.surface else scheme.onSurfaceVariant)
    }
}

/** Everything stored on the phone: songs, albums, artists and playlists behind four pills. */
@Composable
fun MusicScreen(
    vm: PlayerViewModel,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onMore: (Song) -> Unit,
) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The pill last used is remembered between launches.
    val store = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var tab by remember { mutableIntStateOf(store.getInt("music_tab", 0).coerceIn(0, 3)) }
    var creating by remember { mutableStateOf(false) }
    val labels = listOf(R.string.songs, R.string.tab_albums, R.string.tab_artists, R.string.playlists)

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.tab_music), stringResource(R.string.library_summary, songs.size, albums.size, artists.size))
        // Four equal pills, so none is cut off at the edge.
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                Pill(stringResource(label), tab == i, Modifier.weight(1f)) {
                    tab = i
                    store.edit().putInt("music_tab", i).apply()
                }
            }
        }
        when (tab) {
            1 -> AlbumsScreen(vm, onAlbum)
            2 -> ArtistsScreen(vm, onArtist)
            3 -> LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = BarSpace)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.new_playlist), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item {
                    PlaylistRow(stringResource(R.string.favorites), settings.favorites.size, Icons.Rounded.Favorite) { onPlaylist(FAVORITES_KEY) }
                }
                val names = settings.playlists.keys.toList()
                items(names.size) { i ->
                    val name = names[i]
                    PlaylistRow(name, settings.playlists[name]?.size ?: 0, Icons.Rounded.QueueMusic) { onPlaylist(name) }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = BarSpace)) {
                if (songs.isEmpty()) {
                    item { EmptyState(stringResource(R.string.empty_library), stringResource(R.string.empty_library_hint)) }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { vm.play(songs, 0) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.play_all), fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(onClick = { vm.play(songs, 0, shuffle = true) }, modifier = Modifier.weight(1f).height(48.dp)) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.shuffle), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(songs.size) { i ->
                        val song = songs[i]
                        SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) { vm.play(songs, i) }
                    }
                }
            }
        }
    }

    if (creating) {
        NewPlaylistDialog(onDismiss = { creating = false }) { name ->
            vm.createPlaylist(name)
            creating = false
        }
    }
}

// ---------------------------------------------------------------- Search

/** One field that looks through the songs, albums and artists on the phone. */
@Composable
fun SearchScreen(
    vm: PlayerViewModel,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onMore: (Song) -> Unit,
) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val foundSongs = remember(q, songs) {
        if (q.isEmpty()) emptyList() else songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) }.take(10)
    }
    val foundAlbums = remember(q, albums) {
        if (q.isEmpty()) emptyList() else albums.filter { it.title.contains(q, true) }.take(5)
    }
    val foundArtists = remember(q, artists) {
        if (q.isEmpty()) emptyList() else artists.filter { it.name.contains(q, true) }.take(5)
    }
    val local = foundSongs.isNotEmpty() || foundAlbums.isNotEmpty() || foundArtists.isNotEmpty()

    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        stickyHeader {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)).statusBarsPadding()) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_all_hint)) },
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        if (q.isEmpty()) {
            item { EmptyState(stringResource(R.string.search_all_title), stringResource(R.string.search_all_text)) }
        }
        if (local) {
            item { GroupLabel(stringResource(R.string.my_music)) }
            items(foundSongs.size) { i ->
                val song = foundSongs[i]
                SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) { vm.play(foundSongs, i) }
            }
            items(foundAlbums.size) { i ->
                val album = foundAlbums[i]
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onAlbum(album.id) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(album.id, album.title, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.album_by, album.artist), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(foundArtists.size) { i ->
                val artist = foundArtists[i]
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onArtist(artist.name) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtistAvatar(artist.name, settings.artistPhotos, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.songs_count, artist.songs.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (q.isNotEmpty() && !local) {
            item { EmptyState(stringResource(R.string.no_results, q)) }
        }
    }
}
