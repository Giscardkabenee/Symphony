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
import com.symphony.music.data.CatalogTrack
import com.symphony.music.data.DeezerSearch
import com.symphony.music.data.Podcast
import com.symphony.music.data.PodcastApi
import com.symphony.music.data.RadioApi
import com.symphony.music.data.Song
import com.symphony.music.data.Station
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

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    onSettings: () -> Unit,
    onStack: (String) -> Unit,
    onAlbum: (Long) -> Unit,
    onMusic: () -> Unit,
    onOnline: () -> Unit,
) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val recent = remember(settings.recents, songs) { vm.songsFor(settings.recents) }
    val added = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(50) }
    val most = remember(settings.playCounts, songs) { vm.songsFor(mostPlayedIds(settings.playCounts)) }
    val latestAlbums = remember(songs) { songs.sortedByDescending { it.dateAdded }.distinctBy { it.albumId }.take(12) }

    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        stickyHeader {
            ScreenTitle(stringResource(R.string.app_name)) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
        // What is in the player; else the podcast episode left unfinished; else the last song listened to.
        val current = state.current
        val episode = if (current == null) settings.lastEpisode else null
        val resume = current ?: episode?.let { it.episode.toSong(it.podcast) } ?: recent.firstOrNull()
        if (resume != null) {
            val position = episode?.let { settings.episodePositions[it.episode.id] } ?: 0L
            item {
                val detail = when {
                    episode != null && position > 0 -> stringResource(R.string.podcast) + " · " + stringResource(R.string.podcast_resume, formatTime(position))
                    episode != null -> stringResource(R.string.podcast) + " · " + resume.artist
                    else -> resume.artist
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable {
                            when {
                                current != null -> vm.toggle()
                                episode != null -> vm.playEpisode(episode.podcast, episode.episode)
                                else -> vm.play(recent, 0)
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (episode != null) {
                        WebArt(episode.episode.art.ifBlank { episode.podcast.art }, episode.podcast.title, Modifier.size(60.dp), RoundedCornerShape(14.dp))
                    } else {
                        Artwork(resume.albumId, resume.album, Modifier.size(60.dp), RoundedCornerShape(14.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (current != null) R.string.home_now else R.string.home_resume).uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(resume.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface), contentAlignment = Alignment.Center) {
                        val playing = current != null && state.isPlaying
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                            tint = MaterialTheme.colorScheme.surface,
                        )
                    }
                }
            }
        }
        if (songs.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_library), stringResource(R.string.empty_library_hint)) }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { StackCard(stringResource(R.string.recently_played), recent) { onStack(RECENT_KEY) } }
                    item { StackCard(stringResource(R.string.recently_added), added) { onStack(ADDED_KEY) } }
                    item { StackCard(stringResource(R.string.most_played), most) { onStack(MOST_KEY) } }
                }
            }
        }
        if (settings.stations.isNotEmpty()) {
            item { SectionLink(stringResource(R.string.favorite_radios), stringResource(R.string.see_all), onOnline) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(settings.stations.size) { i ->
                        val station = settings.stations[i]
                        Column(
                            modifier = Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.playStation(station) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            WebArt(station.icon, station.name, Modifier.size(68.dp), CircleShape)
                            Spacer(Modifier.height(6.dp))
                            Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        if (latestAlbums.isNotEmpty()) {
            item { SectionLink(stringResource(R.string.latest_albums), stringResource(R.string.see_all), onMusic) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(latestAlbums.size) { i ->
                        val song = latestAlbums[i]
                        Column(Modifier.width(120.dp).clip(RoundedCornerShape(14.dp)).clickable { onAlbum(song.albumId) }) {
                            Artwork(song.albumId, song.album, Modifier.size(120.dp), RoundedCornerShape(14.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(song.album, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ---------------------------------------------------------------- Online

/** Everything that needs a connection: radios, podcasts and the catalogue. */
@Composable
fun OnlineScreen(
    vm: PlayerViewModel,
    onRadio: () -> Unit,
    onPodcasts: () -> Unit,
    onPodcast: (Long) -> Unit,
    onDiscover: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        stickyHeader { ScreenTitle(stringResource(R.string.tab_online), stringResource(R.string.online_sub)) }

        item { SectionLink(stringResource(R.string.radios), stringResource(R.string.all_stations), onRadio) }
        if (settings.stations.isEmpty()) {
            item { LibraryEntry(Icons.Rounded.Radio, stringResource(R.string.radios), stringResource(R.string.radio_desc), onRadio) }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(settings.stations.size) { i ->
                        val station = settings.stations[i]
                        Column(
                            modifier = Modifier
                                .size(width = 156.dp, height = 88.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(lerp(colorFor(station.name), Color.Black, 0.5f))
                                .clickable { vm.playStation(station) }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.live_tag), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = Color.White.copy(alpha = 0.8f))
                            Column {
                                Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                if (station.country.isNotBlank()) {
                                    Text(station.country, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SectionLink(stringResource(R.string.podcasts), stringResource(R.string.find_show), onPodcasts) }
        if (settings.podcasts.isEmpty()) {
            item { LibraryEntry(Icons.Rounded.Podcasts, stringResource(R.string.podcasts), stringResource(R.string.podcast_desc), onPodcasts) }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(settings.podcasts.size) { i ->
                        val podcast = settings.podcasts[i]
                        Column(
                            Modifier.width(108.dp).clip(RoundedCornerShape(16.dp)).clickable {
                                vm.rememberPodcast(podcast)
                                onPodcast(podcast.id)
                            },
                        ) {
                            WebArt(podcast.art, podcast.title, Modifier.size(108.dp), RoundedCornerShape(16.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(podcast.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (settings.downloads.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                LibraryEntry(Icons.Rounded.Download, stringResource(R.string.downloads), stringResource(R.string.downloads_offline, settings.downloads.size), onPodcasts)
            }
        }

        item { SectionHeader(stringResource(R.string.discover)) }
        item { LibraryEntry(Icons.Rounded.Explore, stringResource(R.string.discover), stringResource(R.string.discover_desc), onDiscover) }
    }
}

// ---------------------------------------------------------------- Search

/** One field that looks through the phone's music, radio stations, podcasts and the catalogue. */
@Composable
fun SearchScreen(
    vm: PlayerViewModel,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onPodcast: (Long) -> Unit,
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
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var shows by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<CatalogTrack>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // The three online sources are asked together, once typing pauses.
    LaunchedEffect(q) {
        stations = emptyList()
        shows = emptyList()
        tracks = emptyList()
        if (q.length < 2) {
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(600)
        coroutineScope {
            launch {
                stations = try { RadioApi.search(q).take(5) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            }
            launch {
                shows = try { PodcastApi.search(q).take(5) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            }
            launch {
                tracks = try { DeezerSearch.search(q).take(8) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            }
        }
        searching = false
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
        if (stations.isNotEmpty()) {
            item { GroupLabel(stringResource(R.string.radios)) }
            items(stations.size) { i ->
                val station = stations[i]
                StationRow(
                    station = station,
                    active = state.current?.id == station.id,
                    favorite = settings.stations.any { it.id == station.id },
                    onFavorite = { vm.toggleStation(station) },
                ) { vm.playStation(station) }
            }
        }
        if (shows.isNotEmpty()) {
            item { GroupLabel(stringResource(R.string.podcasts)) }
            items(shows.size) { i ->
                val podcast = shows[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.rememberPodcast(podcast)
                            onPodcast(podcast.id)
                        }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WebArt(podcast.art, podcast.title, Modifier.size(52.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(podcast.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(podcast.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item { GroupLabel(stringResource(R.string.catalogue)) }
            items(tracks.size) { i ->
                val track = tracks[i]
                val active = state.current?.id == track.toSong().id
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.playPreview(track) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WebArt(track.cover, track.title, Modifier.size(52.dp), RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold)
                        Text(
                            text = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.preview_tag), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (searching) {
            item {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else if (q.isNotEmpty() && !local && stations.isEmpty() && shows.isEmpty() && tracks.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_results, q)) }
        }
    }
}
