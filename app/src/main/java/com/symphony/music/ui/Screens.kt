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
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symphony.music.PlayerViewModel
import com.symphony.music.R
import kotlin.math.roundToInt
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
const val MIX_DAY = "__mix_day__"
const val MIX_WEEK = "__mix_week__"
const val MIX_MONTH = "__mix_month__"
val MIX_KEYS = listOf(MIX_DAY, MIX_WEEK, MIX_MONTH)
const val MIX_DJ = "__mix_dj__"

/**
 * Today's DJ set: about 24 songs, ordered like a DJ builds a set: it starts calm, climbs to the most
 * energetic songs, then comes back down, keeping neighbouring tempos close.
 */
fun buildDjSet(all: List<Song>, favorites: List<Song>, most: List<Song>): List<Song> {
    if (all.isEmpty()) return emptyList()
    val cal = java.util.Calendar.getInstance()
    val r = java.util.Random((cal.get(java.util.Calendar.YEAR) * 1000L + cal.get(java.util.Calendar.DAY_OF_YEAR)) * 13)
    val pool = (favorites.shuffled(r).take(10) + most.take(30).shuffled(r).take(12) + all.shuffled(r).take(24))
        .distinctBy { it.id }.take(24)
    fun energy(s: Song) = com.symphony.music.playback.SongAnalysis.get(s.id)?.energy ?: 0.5f
    fun bpm(s: Song) = com.symphony.music.playback.SongAnalysis.get(s.id)?.bpm ?: 110f
    val byEnergy = pool.sortedBy { energy(it) }
    val rising = byEnergy.filterIndexed { i, _ -> i % 2 == 0 }
    val falling = byEnergy.filterIndexed { i, _ -> i % 2 == 1 }.reversed()
    // Inside each half, neighbours with close tempos follow each other.
    fun chain(list: List<Song>): List<Song> {
        if (list.size < 3) return list
        val left = list.toMutableList()
        val out = mutableListOf(left.removeAt(0))
        while (left.isNotEmpty()) {
            val last = bpm(out.last())
            val next = left.take(3).minByOrNull { kotlin.math.abs(bpm(it) - last) }!!
            left.remove(next)
            out += next
        }
        return out
    }
    return chain(rising) + chain(falling)
}

/**
 * A mix drawn from favourites, most played, never played and the rest of the library. It is the same
 * all day (or week, or month) and changes with the next one; the week and month mixes are longer
 * and lean more on what you listen to most.
 */
fun buildMix(key: String, all: List<Song>, favorites: List<Song>, most: List<Song>, counts: Map<Long, Int>): List<Song> {
    if (all.isEmpty()) return emptyList()
    val cal = java.util.Calendar.getInstance()
    val year = cal.get(java.util.Calendar.YEAR)
    val (seed, size) = when (key) {
        MIX_WEEK -> (year * 100L + cal.get(java.util.Calendar.WEEK_OF_YEAR)) * 31 to 40
        MIX_MONTH -> (year * 100L + cal.get(java.util.Calendar.MONTH)) * 97 to 60
        else -> (year * 1000L + cal.get(java.util.Calendar.DAY_OF_YEAR)) * 7919 to 25
    }
    val r = java.util.Random(seed)
    val forgotten = all.filter { (counts[it.id] ?: 0) == 0 }
    val pool = when (key) {
        MIX_WEEK -> most.take(30).shuffled(r).take(16) + favorites.shuffled(r).take(12) + forgotten.shuffled(r).take(8) + all.shuffled(r).take(24)
        MIX_MONTH -> favorites.shuffled(r).take(20) + most.take(50).shuffled(r).take(20) + forgotten.shuffled(r).take(15) + all.shuffled(r).take(40)
        else -> favorites.shuffled(r).take(8) + most.take(20).shuffled(r).take(8) + all.shuffled(r).take(20)
    }
    return pool.distinctBy { it.id }.take(size).shuffled(r)
}

/** Playlist key for the songs of one decade: "__era_1990". */
const val ERA_PREFIX = "__era_"

/** Room left at the bottom of every list for the floating bar. */
internal val BarSpace = 210.dp

@Composable
internal fun ScreenTitle(text: String, subtitle: String? = null, action: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)).statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action()
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.cascade().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
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
internal fun AlbumCard(album: AlbumInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.cascade().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Artwork(album.id, album.title, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The fifty most started songs, most played first. */
internal fun mostPlayedIds(counts: Map<Long, Int>): List<Long> =
    counts.entries.sortedByDescending { it.value }.take(50).map { it.key }

/** A collection drawn as a tabbed folder tinted by its newest cover, with two albums sticking out. */
@Composable
internal fun StackCard(title: String, songs: List<Song>, onClick: () -> Unit) {
    val covers = remember(songs) { songs.distinctBy { it.albumId }.take(3) }
    val context = LocalContext.current
    val fallback = colorFor(title)
    val first = covers.firstOrNull()
    val tint by produceState(fallback, first?.albumId) {
        val found = first?.let { dominantColor(context, it.artUri) }
        value = lerp(found ?: fallback, Color.Black, 0.25f)
    }
    val back = lerp(tint, Color.Black, 0.3f)
    val bodyShape = RoundedCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    val frontShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    Box(
        modifier = Modifier
            .size(width = 172.dp, height = 172.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = title, onClick = onClick),
    ) {
        // Back of the folder: the tab, then the body.
        Box(Modifier.offset(y = 18.dp).size(width = 76.dp, height = 30.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(back))
        Box(Modifier.offset(y = 38.dp).fillMaxWidth().height(134.dp).clip(bodyShape).background(back))
        // Two albums peeking out of the folder.
        covers.getOrNull(2)?.let {
            Artwork(it.albumId, it.album, Modifier.offset(x = 40.dp, y = 12.dp).size(width = 72.dp, height = 92.dp).rotate(-7f), RoundedCornerShape(10.dp))
        }
        covers.getOrNull(1)?.let {
            Artwork(it.albumId, it.album, Modifier.offset(x = 90.dp, y = 8.dp).size(width = 72.dp, height = 92.dp).rotate(6f), RoundedCornerShape(10.dp))
        }
        // Front flap: plain colour, the name and the count.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(98.dp)
                .shadow(10.dp, frontShape)
                .clip(frontShape)
                .background(tint),
        ) {
            // A soft sheen on the upper edge gives the flap some volume without hurting the text.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.45f to Color.Transparent)))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = pluralStringResource(R.plurals.songs_count, songs.size, songs.size),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Albums and artists

@Composable
fun AlbumsScreen(vm: PlayerViewModel, onAlbum: (Long) -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    // The title stays put; only the grid scrolls.
    Column(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarSpace),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (albums.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.empty_library)) }
        }
        items(albums.size) { i ->
            val album = albums[i]
            AlbumCard(album) { onAlbum(album.id) }
        }
    }
    }
}

@Composable
fun ArtistsScreen(vm: PlayerViewModel, onArtist: (String) -> Unit) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    // The title stays put; only the grid scrolls.
    Column(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarSpace),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
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
                    text = pluralStringResource(R.plurals.songs_count, artist.songs.size, artist.songs.size),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    }
}

/** Large entry at the top of the library, leading to an online section. */
@Composable
internal fun LibraryEntry(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PlaylistRow(name: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.cascade().fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
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
            Text(pluralStringResource(R.plurals.songs_count, count, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    covers: List<Long> = emptyList(),
    action: @Composable () -> Unit = {},
) {
    if (albumId != null) {
        CoverHeader(title, subtitle, albumId, label, onBack, onPlay, onShuffle, action, covers)
        return
    }
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

/** Album header: the cover across the whole width, blurring downwards into the album's own colour. */
@Composable
private fun CoverHeader(
    title: String,
    subtitle: String,
    albumId: Long,
    label: String,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    action: @Composable () -> Unit,
    covers: List<Long> = emptyList(),
) {
    // White status-bar icons over the cover, whatever the theme; restored on leaving.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller?.isAppearanceLightStatusBars = before }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds()) {
        val side = maxWidth
        // The sharp cover (or a mosaic of covers for a list), dissolving over its lower third into the blurred copy.
        val fade = Modifier
            .fillMaxWidth()
            .height(side)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(0f to Color.Black, 0.6f to Color.Black, 0.95f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
        val mosaic = covers.distinct()
        when {
            mosaic.size >= 4 -> Column(fade) {
                for (r in 0..1) Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0..1) Artwork(mosaic[r * 2 + c], label, Modifier.weight(1f).fillMaxHeight(), RectangleShape)
                }
            }
            mosaic.size == 3 -> Row(fade) {
                Artwork(mosaic[0], label, Modifier.weight(2f).fillMaxHeight(), RectangleShape)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Artwork(mosaic[1], label, Modifier.weight(1f).fillMaxWidth(), RectangleShape)
                    Artwork(mosaic[2], label, Modifier.weight(1f).fillMaxWidth(), RectangleShape)
                }
            }
            else -> Artwork(albumId, label, fade, RectangleShape)
        }
        // A light shade under the status bar.
        Box(
            Modifier.fillMaxWidth().height(side).align(Alignment.TopCenter).background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.28f), 0.16f to Color.Transparent)
            )
        )
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Spacer(Modifier.weight(1f))
                action()
            }
            Spacer(Modifier.height(side * 0.62f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.play), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onShuffle,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shuffle), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/**
 * The page behind an album or playlist: its cover, heavily blurred and tinted with its own colour,
 * stays in place while the header and the songs scroll over it, all in light text.
 */
@Composable
private fun ImmersivePage(albumId: Long?, label: String, content: @Composable () -> Unit) {
    if (albumId == null) {
        content()
        return
    }
    val context = LocalContext.current
    val fallback = lerp(colorFor(label), Color.Black, 0.45f)
    val found by produceState<Color?>(null, albumId) { value = dominantColor(context, artworkUri(albumId)) }
    val band by animateColorAsState(found?.let { lerp(it, Color.Black, 0.5f) } ?: fallback, tween(500), label = "band")
    val scheme = MaterialTheme.colorScheme.copy(
        onSurface = Color.White,
        onBackground = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.72f),
        surfaceContainerHigh = Color.White.copy(alpha = 0.14f),
        surfaceContainerHighest = Color.White.copy(alpha = 0.18f),
    )
    Box(Modifier.fillMaxSize().background(band)) {
        Artwork(albumId, label, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }.blur(90.dp), RectangleShape)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to band.copy(alpha = 0.35f),
                    0.45f to band.copy(alpha = 0.7f),
                    1f to lerp(band, Color.Black, 0.55f).copy(alpha = 0.92f),
                )
            )
        )
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
            CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
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
        append(" · ").append(pluralStringResource(R.plurals.songs_count, album.songs.size, album.songs.size))
        append(" · ").append(minutes).append(" min")
    }
    ImmersivePage(album.id, album.title) {
    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        item {
            DetailHeader(album.title, subtitle, album.id, album.title, onBack,
                onPlay = { vm.play(album.songs, 0) },
                onShuffle = { vm.play(album.songs, 0, shuffle = true) })
        }
        items(album.songs.size) { i ->
            val song = album.songs[i]
            val active = state.current?.id == song.id
            QueueSwipe({ vm.playNext(song) }, { vm.addToQueue(song) }) {
            Row(
                modifier = Modifier.cascade().fillMaxWidth().clickable { vm.play(album.songs, i) }.padding(start = 20.dp, end = 8.dp),
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
    val subtitle = pluralStringResource(R.plurals.albums_count, artist.albumCount, artist.albumCount) + " · " + pluralStringResource(R.plurals.songs_count, artist.songs.size, artist.songs.size)
    val context = LocalContext.current
    val info by produceState<com.symphony.music.data.ArtistBio?>(null, artist.name, settings.artistInfo) {
        value = if (settings.artistInfo) com.symphony.music.data.ArtistInfos.find(context, artist.name) else null
    }
    val latest = info?.latest
    val ownedLatest = remember(latest, artistAlbums) {
        latest?.let { r -> artistAlbums.firstOrNull { it.title.trim().equals(r.title.trim(), ignoreCase = true) }?.id }
    }
    var allSongs by remember(artist.name) { mutableStateOf(false) }
    val shownSongs = if (allSongs || artist.songs.size <= 6) artist.songs.size else 5
    ImmersivePage(artist.songs.firstOrNull()?.albumId, artist.name) {
    LazyColumn(contentPadding = PaddingValues(bottom = BarSpace)) {
        item {
            DetailHeader(artist.name, subtitle, null, artist.name, onBack, artistPhotos = settings.artistPhotos,
                onPlay = { vm.play(artist.songs, 0) },
                onShuffle = { vm.play(artist.songs, 0, shuffle = true) })
        }
        if (latest != null) item { LatestRelease(latest, ownedLatest, onAlbum) }
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
        items(shownSongs) { i ->
            val song = artist.songs[i]
            QueueSwipe({ vm.playNext(song) }, { vm.addToQueue(song) }) {
                SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) { vm.play(artist.songs, i) }
            }
        }
        if (artist.songs.size > 6) item {
            TextButton(onClick = { allSongs = !allSongs }, modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    if (allSongs) stringResource(R.string.read_less) else stringResource(R.string.show_all_songs, artist.songs.size),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        info?.let { i ->
            item { ArtistAboutCard(artist.name, i, settings.artistPhotos) }
            item { ArtistNews(i) }
        }
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
    val era = if (name.startsWith(ERA_PREFIX)) name.removePrefix(ERA_PREFIX).toIntOrNull() else null
    val smart = name == RECENT_KEY || name == ADDED_KEY || name == MOST_KEY || era != null || name in MIX_KEYS || name == MIX_DJ
    val grooves by com.symphony.music.playback.SongAnalysis.version.collectAsStateWithLifecycle()
    val songs = remember(name, settings, allSongs, if (name == MIX_DJ) grooves else 0) {
        if (name == MIX_DJ) buildDjSet(allSongs, vm.songsFor(settings.favorites), vm.songsFor(mostPlayedIds(settings.playCounts)))
        else if (name in MIX_KEYS) buildMix(name, allSongs, vm.songsFor(settings.favorites), vm.songsFor(mostPlayedIds(settings.playCounts)), settings.playCounts)
        else if (era != null) allSongs.filter { it.year in 1900..2100 && it.year / 10 * 10 == era }.sortedWith(compareBy({ it.year }, { it.title.lowercase() }))
        else when (name) {
            RECENT_KEY -> vm.songsFor(settings.recents)
            ADDED_KEY -> allSongs.sortedByDescending { it.dateAdded }.take(50)
            MOST_KEY -> vm.songsFor(mostPlayedIds(settings.playCounts))
            FAVORITES_KEY -> vm.songsFor(settings.favorites)
            else -> vm.songsFor(settings.playlists[name] ?: emptyList())
        }
    }
    val title = if (era != null) stringResource(R.string.era_label, era % 100) else when (name) {
        MIX_DJ -> stringResource(R.string.dj_title)
        MIX_DAY -> stringResource(R.string.daily_mix)
        MIX_WEEK -> stringResource(R.string.weekly_mix)
        MIX_MONTH -> stringResource(R.string.monthly_mix)
        RECENT_KEY -> stringResource(R.string.recently_played)
        ADDED_KEY -> stringResource(R.string.recently_added)
        MOST_KEY -> stringResource(R.string.most_played)
        FAVORITES_KEY -> stringResource(R.string.favorites)
        else -> name
    }
    val minutes = songs.sumOf { it.duration } / 60_000
    val subtitle = pluralStringResource(R.plurals.songs_count, songs.size, songs.size) + " · " + minutes + " min"
    // Playlists you made (and Favourites) can be put in order by dragging the handle.
    var order by remember(songs) { mutableStateOf(songs.withIndex().toList()) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val a = order.indexOfFirst { it.index == from.key }
        val b = order.indexOfFirst { it.index == to.key }
        if (a >= 0 && b >= 0) order = order.toMutableList().apply { add(b, removeAt(a)) }
    }
    val view = LocalView.current

    ImmersivePage(songs.firstOrNull()?.albumId, title) {
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = BarSpace)) {
        item(key = "header") {
            DetailHeader(title, subtitle, songs.firstOrNull()?.albumId, title, onBack, covers = songs.map { it.albumId }.distinct().take(4),
                onPlay = { if (name == MIX_DJ) vm.playDj(songs) else vm.play(songs, 0) },
                onShuffle = { if (name == MIX_DJ) vm.playDj(songs.shuffled()) else vm.play(songs, 0, shuffle = true) },
                action = {
                    if (!isFavorites && !smart) {
                        FilledTonalIconButton(onClick = { vm.deletePlaylist(name); onBack() }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_playlist))
                        }
                    }
                })
        }
        if (songs.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.empty_list)) }
        }
        items(order, key = { it.index }) { entry ->
            val song = entry.value
            ReorderableItem(reorder, key = entry.index, enabled = !smart) { dragging ->
                Row(
                    modifier = Modifier.background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        SongRow(song, active = state.current?.id == song.id, onMore = { onMore(song) }) {
                            val list = order.map { it.value }
                            vm.play(list, list.indexOf(song).coerceAtLeast(0))
                        }
                    }
                    if (!smart) {
                        IconButton(onClick = { if (isFavorites) vm.toggleFavorite(song.id) else vm.removeFromPlaylist(name, song.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle(
                                onDragStarted = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
                                onDragStopped = { vm.setPlaylistOrder(name, order.map { it.value.id }) },
                            ),
                        ) {
                            Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var equalizer by remember { mutableStateOf(false) }
    if (equalizer) EqualizerSheet(vm) { equalizer = false }
    var headphones by remember { mutableStateOf(false) }
    if (headphones) HeadphoneSheet(vm) { headphones = false }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BarSpace)) {
        stickyHeader {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)).statusBarsPadding().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.AutoAwesome, stringResource(R.string.automix), stringResource(R.string.automix_desc), settings.automix) { vm.setFlag(Flags.AUTOMIX, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.RecordVoiceOver, stringResource(R.string.dj_voice), stringResource(R.string.dj_voice_desc), settings.djVoice) { vm.setFlag(Flags.DJ_VOICE, it) }
                if (settings.automix) {
                    var seconds by remember(settings.automixSeconds) { mutableFloatStateOf(settings.automixSeconds.toFloat()) }
                    Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
                        Text(stringResource(R.string.automix_length, seconds.roundToInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = seconds,
                            onValueChange = { seconds = it },
                            onValueChangeFinished = { vm.setEffect("automix_seconds", seconds.roundToInt()) },
                            valueRange = 2f..12f,
                            steps = 9,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { equalizer = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.equalizer), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(if (settings.eqEnabled) R.string.eq_on else R.string.eq_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { headphones = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.hp_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        val hp = settings.headphone
                        Text(
                            text = when {
                                hp == null -> stringResource(R.string.hp_none)
                                settings.headphoneOn -> hp.name
                                else -> hp.name + " · " + stringResource(R.string.eq_off)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SettingsLabel(stringResource(R.string.appearance)) }
        item {
            // The name shown in the home greeting, saved a moment after typing stops.
            var name by remember(settings.userName) { mutableStateOf(settings.userName) }
            LaunchedEffect(name) {
                kotlinx.coroutines.delay(600)
                if (name.trim() != settings.userName) vm.setUserName(name)
            }
            SettingsCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.user_name)) },
                    supportingText = { Text(stringResource(R.string.user_name_desc)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
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
                if (settings.fullCover) {
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingSwitch(Icons.Rounded.Crop, stringResource(R.string.cover_bleed), stringResource(R.string.cover_bleed_desc), settings.coverBleed) { vm.setFlag(Flags.COVER_BLEED, it) }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Lyrics, stringResource(R.string.synced_lyrics), stringResource(R.string.synced_lyrics_desc), settings.syncedLyrics) { vm.setSyncedLyrics(it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Public, stringResource(R.string.online_lyrics), stringResource(R.string.online_lyrics_desc), settings.onlineLyrics) { vm.setFlag(Flags.ONLINE_LYRICS, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.AccountCircle, stringResource(R.string.artist_photos), stringResource(R.string.artist_photos_desc), settings.artistPhotos) { vm.setFlag(Flags.ARTIST_PHOTOS, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.Info, stringResource(R.string.artist_info), stringResource(R.string.artist_info_desc), settings.artistInfo) { vm.setFlag(Flags.ARTIST_INFO, it) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.BlurOn, stringResource(R.string.blur_lyrics), stringResource(R.string.blur_lyrics_desc), settings.blurLyrics) { vm.setFlag(Flags.BLUR_LYRICS, it) }
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
