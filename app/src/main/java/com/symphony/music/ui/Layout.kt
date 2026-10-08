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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.rounded.ExpandMore
import com.symphony.music.data.ArtistInfo
import com.symphony.music.data.AlbumInfo
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
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

/**
 * Title and line under it for the home screen: they follow the time of day and the day of the week,
 * and change a little from one day (and one hour) to the next.
 */
@Composable
private fun greeting(name: String, songCount: Int, topArtist: String?): Pair<List<String>, List<String>> {
    val now = remember { java.util.Calendar.getInstance() }
    val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
    val day = now.get(java.util.Calendar.DAY_OF_WEEK)
    val seed = now.get(java.util.Calendar.DAY_OF_YEAR)
    val who = if (name.isBlank()) "" else ", " + name.trim()
    val weekend = day == java.util.Calendar.SATURDAY || day == java.util.Calendar.SUNDAY
    val titles = when {
        day == java.util.Calendar.FRIDAY && hour >= 17 -> listOf(R.string.greet_friday, R.string.greet_evening_1)
        weekend && hour in 7..11 -> listOf(R.string.greet_weekend, R.string.greet_morning_1)
        hour in 5..8 -> listOf(R.string.greet_wake_1, R.string.greet_wake_2, R.string.greet_wake_3)
        hour in 9..11 -> listOf(R.string.greet_morning_1, R.string.greet_morning_2)
        hour in 12..13 -> listOf(R.string.greet_noon_1, R.string.greet_noon_2)
        hour in 14..17 -> listOf(R.string.greet_afternoon_1, R.string.greet_afternoon_2)
        hour in 18..21 -> listOf(R.string.greet_evening_1, R.string.greet_evening_2)
        else -> listOf(R.string.greet_night_1, R.string.greet_night_2)
    }
    val lines = mutableListOf(stringResource(R.string.sub_enjoy), stringResource(R.string.sub_ready, songCount))
    if (topArtist != null) lines += stringResource(R.string.sub_artist, topArtist)
    when (hour) {
        in 5..8 -> lines += stringResource(R.string.sub_wake)
        in 22..23, in 0..4 -> lines += stringResource(R.string.sub_night)
    }
    // Today's pick first, then the other phrases of the moment; same for the lines under it.
    val shift = seed % titles.size
    val ordered = titles.indices.map { titles[(it + shift) % titles.size] }.map { stringResource(it, who) }
    val lineShift = (seed + hour) % lines.size
    return ordered to lines.indices.map { lines[(it + lineShift) % lines.size] }
}

/**
 * Home title: the greeting types itself in, shimmers gently, then rolls to the next phrase of the moment;
 * the line under it rolls on its own rhythm.
 */
@Composable
private fun AnimatedHeader(titles: List<String>, lines: List<String>, action: @Composable () -> Unit = {}) {
    var titleIndex by remember(titles) { mutableIntStateOf(0) }
    var lineIndex by remember(lines) { mutableIntStateOf(0) }
    var typed by remember(titles) { mutableIntStateOf(0) }
    val first = titles.firstOrNull().orEmpty()
    // Letter by letter, the first time.
    LaunchedEffect(titles) {
        typed = 0
        while (typed < first.length) {
            delay(38)
            typed++
        }
        while (titles.size > 1) {
            delay(7000)
            titleIndex = (titleIndex + 1) % titles.size
        }
    }
    LaunchedEffect(lines) {
        while (lines.size > 1) {
            delay(4200)
            lineIndex = (lineIndex + 1) % lines.size
        }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val shine = androidx.compose.animation.core.rememberInfiniteTransition(label = "shine")
    val sweep by shine.animateFloat(
        initialValue = -600f,
        targetValue = 1400f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(4200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "sweep",
    )
    val brush = Brush.linearGradient(
        colors = listOf(ink, ink, Color(0xFF8B5CF6), Color(0xFFE2502F), ink, ink),
        start = androidx.compose.ui.geometry.Offset(sweep, 0f),
        end = androidx.compose.ui.geometry.Offset(sweep + 700f, 120f),
    )
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)).statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val shown = if (titleIndex == 0) first.take(typed) else titles[titleIndex]
            androidx.compose.animation.AnimatedContent(
                targetState = titleIndex,
                transitionSpec = {
                    (slideInVertically(tween(520)) { it / 2 } + fadeIn(tween(520))) togetherWith
                        (slideOutVertically(tween(420)) { -it / 2 } + fadeOut(tween(300)))
                },
                label = "title",
            ) { index ->
                Text(
                    text = if (index == titleIndex) shown else titles.getOrElse(index) { "" },
                    style = MaterialTheme.typography.headlineLarge.copy(brush = brush),
                    fontWeight = FontWeight.ExtraBold,
                    minLines = 1,
                )
            }
            androidx.compose.animation.AnimatedContent(
                targetState = lines.getOrElse(lineIndex) { "" },
                transitionSpec = {
                    (slideInVertically(tween(450)) { it } + fadeIn(tween(450))) togetherWith
                        (slideOutVertically(tween(350)) { -it } + fadeOut(tween(250)))
                },
                label = "line",
            ) { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        action()
    }
}

private val eraColors = listOf(Color(0xFFB45309), Color(0xFF0F766E), Color(0xFF7C3AED), Color(0xFFBE123C), Color(0xFF1D4ED8), Color(0xFF15803D))

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
    val topArtist = if (settings.playCounts.isEmpty()) null else topArtists.firstOrNull()?.name
    val day = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }
    // 25 songs drawn from favourites, most played and the rest, in an order that changes every day.
    val dailyMix = remember(songs, settings.favorites, settings.playCounts, day) {
        val random = java.util.Random(day.toLong() * 7919)
        val pool = (favorites.shuffled(random).take(8) + most.take(20).shuffled(random).take(8) + songs.shuffled(random).take(20))
            .distinctBy { it.id }
        pool.take(25).shuffled(random)
    }
    // Never played first, then the least played; five of them, different each day.
    val rediscover = remember(songs, settings.playCounts, day) {
        val random = java.util.Random(day.toLong() * 104729)
        songs.filter { (settings.playCounts[it.id] ?: 0) == 0 }.ifEmpty { songs.sortedBy { settings.playCounts[it.id] ?: 0 }.take(30) }
            .shuffled(random).take(5)
    }
    // Songs grouped by decade, oldest first, only when the files carry a year.
    val decades = remember(songs) {
        songs.filter { it.year in 1900..2100 }.groupBy { it.year / 10 * 10 }.toSortedMap().map { it.key to it.value }
    }
    val (greetings, taglines) = greeting(settings.userName, songs.size, topArtist)

    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        stickyHeader {
            AnimatedHeader(greetings, taglines) {
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
        // ---- Mix of the day: favourites, most played and forgotten songs, new every day.
        if (dailyMix.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.daily_mix)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF6D28D9), Color(0xFFDB2777))))
                        .clickable { vm.play(dailyMix, 0) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Four covers as a collage.
                    Column(Modifier.size(92.dp).clip(RoundedCornerShape(16.dp))) {
                        val covers = dailyMix.distinctBy { it.albumId }.take(4)
                        for (r in 0..1) Row(Modifier.weight(1f)) {
                            for (c in 0..1) {
                                val song = covers.getOrNull(r * 2 + c) ?: covers.firstOrNull()
                                if (song != null) Artwork(song.albumId, song.album, Modifier.weight(1f).fillMaxHeight(), RectangleShape)
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.daily_mix), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(
                            text = dailyMix.take(3).joinToString(", ") { it.artist },
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                        )
                        Text(stringResource(R.string.songs_count, dailyMix.size), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.play), tint = Color.Black)
                    }
                }
            }
        }
        // ---- Songs you have never (or hardly) played.
        if (rediscover.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.rediscover)) }
            items(rediscover.size) { i ->
                val song = rediscover[i]
                SongRow(song, active = state.current?.id == song.id) { vm.play(rediscover, i) }
            }
        }
        // ---- One tap per decade.
        if (decades.size > 1) {
            item { SectionHeader(stringResource(R.string.by_era)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(decades.size) { i ->
                        val (decade, list) = decades[i]
                        val tint = eraColors[i % eraColors.size]
                        Column(
                            modifier = Modifier
                                .width(128.dp)
                                .height(84.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(tint, lerp(tint, Color.Black, 0.45f))))
                                .clickable { vm.play(list, 0, shuffle = true) }
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.era_label, decade % 100), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text(stringResource(R.string.songs_count, list.size), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        // ---- A few numbers about the library and listening.
        item { SectionHeader(stringResource(R.string.in_numbers)) }
        item {
            val hours = songs.sumOf { it.duration } / 3_600_000.0
            val plays = settings.playCounts.values.sum()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    String.format(java.util.Locale.getDefault(), "%.1f h", hours) to stringResource(R.string.stat_hours),
                    plays.toString() to stringResource(R.string.stat_plays),
                    settings.favorites.size.toString() to stringResource(R.string.stat_favorites),
                ).forEach { (value, label) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(14.dp),
                    ) {
                        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }
        // ---- Shuffle everything.
        item {
            Button(
                onClick = { vm.play(songs, 0, shuffle = true) },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.shuffle_everything), fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

private val sortLabels = listOf(R.string.sort_az, R.string.sort_za, R.string.sort_artist, R.string.sort_added, R.string.sort_modified, R.string.sort_duration)

private fun sortSongs(list: List<Song>, sort: Int): List<Song> = when (sort) {
    1 -> list.sortedByDescending { it.title.lowercase() }
    2 -> list.sortedWith(compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
    3 -> list.sortedByDescending { it.dateAdded }
    4 -> list.sortedByDescending { it.dateModified }
    5 -> list.sortedByDescending { it.duration }
    else -> list.sortedBy { it.title.lowercase() }
}

private fun sortAlbums(list: List<AlbumInfo>, sort: Int): List<AlbumInfo> = when (sort) {
    1 -> list.sortedByDescending { it.title.lowercase() }
    2 -> list.sortedWith(compareBy<AlbumInfo> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
    3 -> list.sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }
    4 -> list.sortedByDescending { a -> a.songs.maxOf { it.dateModified } }
    5 -> list.sortedByDescending { a -> a.songs.sumOf { it.duration } }
    else -> list.sortedBy { it.title.lowercase() }
}

private fun sortArtists(list: List<ArtistInfo>, sort: Int): List<ArtistInfo> = when (sort) {
    1 -> list.sortedByDescending { it.name.lowercase() }
    3 -> list.sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }
    4 -> list.sortedByDescending { a -> a.songs.maxOf { it.dateModified } }
    5 -> list.sortedByDescending { a -> a.songs.sumOf { it.duration } }
    else -> list.sortedBy { it.name.lowercase() }
}

/** "Play all" and "Shuffle" side by side. */
@Composable
private fun PlayButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.play_all), fontWeight = FontWeight.Bold)
        }
        FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.shuffle), fontWeight = FontWeight.Bold)
        }
    }
}

/** Row for an album or an artist in list view. */
@Composable
private fun EntryRow(title: String, detail: String, onClick: () -> Unit, art: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val sortShort = listOf(R.string.sort_short_az, R.string.sort_short_za, R.string.sort_short_artist, R.string.sort_short_added, R.string.sort_short_modified, R.string.sort_short_duration)

/** Sort pill with its menu on the left, list / grid switch on the right. */
@Composable
private fun ListControls(grid: Boolean, onGrid: (Boolean) -> Unit, sort: Int, onSort: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh)
                    .clickable(onClickLabel = stringResource(R.string.sort)) { open = true }
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(sortShort[sort]), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(18.dp)) {
                sortLabels.forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label), fontWeight = if (i == sort) FontWeight.Bold else FontWeight.Normal) },
                        trailingIcon = { if (i == sort) Icon(Icons.Rounded.Check, contentDescription = null) },
                        onClick = {
                            onSort(i)
                            open = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.height(40.dp).clip(CircleShape).background(scheme.surfaceContainerHigh).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(false to Icons.AutoMirrored.Rounded.ViewList, true to Icons.Rounded.GridView).forEach { (value, icon) ->
                val selected = grid == value
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 32.dp)
                        .clip(CircleShape)
                        .background(if (selected) scheme.onSurface else Color.Transparent)
                        .clickable(onClickLabel = stringResource(if (value) R.string.view_grid else R.string.view_list)) { onGrid(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) scheme.surface else scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Everything stored on the phone: songs, albums, artists and playlists behind four pills, sortable, as a grid or a list. */
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
    // Pill, order and layout are remembered between launches.
    val store = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var tab by remember { mutableIntStateOf(store.getInt("music_tab", 0).coerceIn(0, 3)) }
    var sort by remember { mutableIntStateOf(store.getInt("music_sort", 0).coerceIn(0, 5)) }
    var grids by remember {
        mutableStateOf(List(3) { i -> store.getBoolean("music_grid_$i", i != 0) })
    }
    var creating by remember { mutableStateOf(false) }
    val labels = listOf(R.string.songs, R.string.tab_albums, R.string.tab_artists, R.string.playlists)
    val sortedSongs = remember(songs, sort) { sortSongs(songs, sort) }
    val sortedAlbums = remember(albums, sort) { sortAlbums(albums, sort) }
    val sortedArtists = remember(artists, sort) { sortArtists(artists, sort) }
    val grid = tab < 3 && grids[tab]
    // Order and layout, just under the play buttons (or at the top of albums and artists).
    val controls: @Composable () -> Unit = {
        ListControls(
            grid = grid,
            onGrid = { value ->
                grids = grids.toMutableList().also { it[tab] = value }
                store.edit().putBoolean("music_grid_$tab", value).apply()
            },
            sort = sort,
            onSort = { value ->
                sort = value
                store.edit().putInt("music_sort", value).apply()
            },
        )
    }
    val context2 = LocalContext.current
    val nextLabel = stringResource(R.string.queued_next)
    val queueLabel = stringResource(R.string.queued_end)
    val playNext: (Song) -> Unit = { song ->
        vm.playNext(song)
        android.widget.Toast.makeText(context2, String.format(nextLabel, song.title), android.widget.Toast.LENGTH_SHORT).show()
    }
    val addToQueue: (Song) -> Unit = { song ->
        vm.addToQueue(song)
        android.widget.Toast.makeText(context2, String.format(queueLabel, song.title), android.widget.Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        // Fixed title written in on arrival; the line under it rolls through facts about the library.
        val minutes = songs.sumOf { it.duration } / 60_000
        val latest = remember(songs) { songs.maxByOrNull { it.dateAdded } }
        val biggest = remember(artists) { artists.maxByOrNull { it.songs.size } }
        val longest = remember(albums) { albums.maxByOrNull { a -> a.songs.sumOf { it.duration } } }
        val facts = buildList {
            add(stringResource(R.string.library_summary, songs.size, albums.size, artists.size))
            if (minutes > 0) add(stringResource(R.string.music_total, minutes / 60, minutes % 60))
            latest?.let { add(stringResource(R.string.music_latest, it.title)) }
            biggest?.let { add(stringResource(R.string.music_top_artist, it.name, it.songs.size)) }
            longest?.let { add(stringResource(R.string.music_longest_album, it.title)) }
        }
        AnimatedHeader(listOf(stringResource(R.string.tab_music)), facts)
        // Four equal pills, so none is cut off at the edge.
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                Pill(stringResource(label), tab == i, Modifier.weight(1f)) {
                    tab = i
                    store.edit().putInt("music_tab", i).apply()
                }
            }
        }
        if (songs.isEmpty() && tab < 3) {
            EmptyState(stringResource(R.string.empty_library), stringResource(R.string.empty_library_hint))
        } else when {
            // ---- Songs
            tab == 0 && grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = BarSpace),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayButtons({ vm.play(sortedSongs, 0) }, { vm.play(sortedSongs, 0, shuffle = true) })
                        controls()
                    }
                }
                items(sortedSongs.size) { i ->
                    val song = sortedSongs[i]
                    val active = state.current?.id == song.id
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { vm.play(sortedSongs, i) }) {
                        Artwork(song.albumId, song.album, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(14.dp))
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(song.artist + " · " + formatTime(song.duration), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onMore(song) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            tab == 0 -> LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = BarSpace)) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayButtons({ vm.play(sortedSongs, 0) }, { vm.play(sortedSongs, 0, shuffle = true) })
                        controls()
                    }
                }
                items(sortedSongs.size, key = { sortedSongs[it].id }) { i ->
                    val song = sortedSongs[i]
                    QueueSwipe({ playNext(song) }, { addToQueue(song) }) {
                        SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) { vm.play(sortedSongs, i) }
                    }
                }
            }
            // ---- Albums
            tab == 1 && grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarSpace),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { controls() }
                items(sortedAlbums.size) { i ->
                    val album = sortedAlbums[i]
                    AlbumCard(album) { onAlbum(album.id) }
                }
            }
            tab == 1 -> LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = BarSpace)) {
                item { Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { controls() } }
                items(sortedAlbums.size) { i ->
                    val album = sortedAlbums[i]
                    EntryRow(album.title, album.artist + " · " + stringResource(R.string.songs_count, album.songs.size), { onAlbum(album.id) }) {
                        Artwork(album.id, album.title, Modifier.size(56.dp), RoundedCornerShape(10.dp))
                    }
                }
            }
            // ---- Artists
            tab == 2 && grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarSpace),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { controls() }
                items(sortedArtists.size) { i ->
                    val artist = sortedArtists[i]
                    Column(
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onArtist(artist.name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtistAvatar(artist.name, settings.artistPhotos, Modifier.fillMaxWidth().aspectRatio(1f))
                        Spacer(Modifier.height(8.dp))
                        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.songs_count, artist.songs.size), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            tab == 2 -> LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = BarSpace)) {
                item { Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { controls() } }
                items(sortedArtists.size) { i ->
                    val artist = sortedArtists[i]
                    EntryRow(artist.name, stringResource(R.string.songs_count, artist.songs.size), { onArtist(artist.name) }) {
                        ArtistAvatar(artist.name, settings.artistPhotos, Modifier.size(56.dp))
                    }
                }
            }
            // ---- Playlists
            else -> LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = BarSpace)) {
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
                QueueSwipe({ vm.playNext(song) }, { vm.addToQueue(song) }) {
                    SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) { vm.play(foundSongs, i) }
                }
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
