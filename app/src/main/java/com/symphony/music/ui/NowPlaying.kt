package com.symphony.music.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.symphony.music.PlayerState
import com.symphony.music.PlayerViewModel
import com.symphony.music.R
import com.symphony.music.data.LyricsData
import com.symphony.music.data.Song
import kotlin.math.roundToInt

private val DefaultTint = Color(0xFF1E1E24)
private val Soft = Color.White.copy(alpha = 0.72f)

private const val MODE_COVER = 0
private const val MODE_QUEUE = 1
private const val MODE_LYRICS = 2

/** Full-screen player: cover, queue or lyrics on top, controls below. */
@Composable
fun NowPlaying(vm: PlayerViewModel, onClose: () -> Unit, onMore: (Song) -> Unit, onArtist: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val song = state.current
    if (song == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val context = LocalContext.current
    val tint by produceState(DefaultTint, song.albumId) {
        val found = dominantColor(context, song.artUri)
        value = if (found != null) lerp(found, Color.Black, 0.45f) else DefaultTint
    }
    var mode by rememberSaveable { mutableStateOf(MODE_COVER) }
    val activeLine = remember(lyrics, state.position) { currentLine(lyrics, state.position) }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint, lerp(tint, Color.Black, 0.5f))))
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (mode) {
                    MODE_QUEUE -> QueueList(state, vm, onMore)
                    MODE_LYRICS -> LyricsView(lyrics, activeLine, settings.syncedLyrics, settings.blurLyrics) { vm.seekTo(it) }
                    else -> Cover(song, settings.fullCover, tint, settings.doubleTapSeek, { vm.seekBy(it) }, onClose)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .size(width = 72.dp, height = 36.dp)
                        .clickable(onClickLabel = stringResource(R.string.close), onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 40.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
                }
            }
            Controls(
                song = song,
                state = state,
                vm = vm,
                favorite = song.id in settings.favorites,
                lyricLine = if (mode == MODE_COVER && settings.syncedLyrics && lyrics?.synced == true && activeLine >= 0) {
                    lyrics?.lines?.getOrNull(activeLine)?.text
                } else null,
                hasLyrics = lyrics != null,
                mode = mode,
                showVolume = !settings.hideVolume,
                onMode = { mode = if (mode == it) MODE_COVER else it },
                onMore = { onMore(song) },
                onArtist = { onArtist(song.artist) },
            )
        }
    }
}

private fun currentLine(lyrics: LyricsData?, position: Long): Int {
    if (lyrics == null || !lyrics.synced) return -1
    var index = -1
    for (i in lyrics.lines.indices) {
        if (lyrics.lines[i].timeMs <= position) index = i else break
    }
    return index
}

@Composable
private fun Cover(song: Song, fullCover: Boolean, tint: Color, doubleTap: Boolean, onSeekBy: (Long) -> Unit, onClose: () -> Unit) {
    // Double tap on the left or right half of the cover skips 5 seconds back or forward.
    val taps = Modifier.pointerInput(doubleTap) {
        detectTapGestures(onDoubleTap = { offset ->
            if (doubleTap) onSeekBy(if (offset.x < size.width / 2f) -5000L else 5000L)
        })
    }
    val drag = Modifier.pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total > 160f) onClose() },
        ) { _, amount -> total += amount }
    }
    if (fullCover) {
        Box(Modifier.fillMaxSize().then(drag).then(taps)) {
            Artwork(song.albumId, song.album, Modifier.fillMaxSize(), RectangleShape)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, tint)))
            )
        }
    } else {
        Box(Modifier.fillMaxSize().then(drag).then(taps).statusBarsPadding().padding(32.dp), contentAlignment = Alignment.Center) {
            Artwork(song.albumId, song.album, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(16.dp))
        }
    }
}

@Composable
private fun QueueList(state: PlayerState, vm: PlayerViewModel, onMore: (Song) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 44.dp, bottom = 12.dp),
    ) {
        state.current?.let { now ->
            item {
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(now.albumId, now.album, Modifier.size(56.dp), RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(now.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Soft)
                    }
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable { onMore(now) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.more))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.queue), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.clearQueue() }) {
                    Text(stringResource(R.string.clear), color = Soft, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        items(state.queue.size) { i ->
            val song = state.queue[i]
            val active = i == state.index
            Row(
                modifier = Modifier.fillMaxWidth().clickable { vm.jumpTo(i) }.padding(start = 28.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song.albumId, song.album, Modifier.size(46.dp), RoundedCornerShape(8.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Soft)
                }
                if (active) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp))
                } else {
                    IconButton(onClick = { vm.removeFromQueue(i) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove), tint = Soft)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsView(lyrics: LyricsData?, activeLine: Int, highlight: Boolean, blurOthers: Boolean, onSeek: (Long) -> Unit) {
    if (lyrics == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_lyrics), color = Soft)
        }
        return
    }
    val listState = rememberLazyListState()
    val follow = lyrics.synced && highlight
    LaunchedEffect(activeLine, follow) {
        if (follow && activeLine >= 0) listState.animateScrollToItem((activeLine - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 52.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(lyrics.lines.size) { i ->
            val line = lyrics.lines[i]
            val current = !follow || i == activeLine
            var modifier: Modifier = Modifier.fillMaxWidth()
            if (lyrics.synced) modifier = modifier.clickable { onSeek(line.timeMs) }
            if (!current && blurOthers) modifier = modifier.blur(1.5.dp)
            Text(
                text = line.text,
                modifier = modifier,
                style = if (follow && current) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = if (follow && current) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (current) Color.White else Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun Controls(
    song: Song,
    state: PlayerState,
    vm: PlayerViewModel,
    favorite: Boolean,
    lyricLine: String?,
    hasLyrics: Boolean,
    mode: Int,
    showVolume: Boolean,
    onMode: (Int) -> Unit,
    onMore: () -> Unit,
    onArtist: () -> Unit,
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
    )
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 16.dp)) {
        if (mode != MODE_QUEUE) Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = Soft,
                    modifier = Modifier.clickable(onClick = onArtist),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.more))
            }
        }

        if (lyricLine != null) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onMode(MODE_LYRICS) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(lyricLine, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = Soft, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Soft, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        var dragging by remember { mutableStateOf<Float?>(null) }
        val duration = state.duration.coerceAtLeast(1L).toFloat()
        val shown = dragging ?: state.position.toFloat().coerceIn(0f, duration)
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { vm.seekTo(it.toLong()) }
                dragging = null
            },
            valueRange = 0f..duration,
            colors = sliderColors,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shown.toLong()), style = MaterialTheme.typography.labelMedium, color = Soft)
            Text("-" + formatTime((state.duration - shown.toLong()).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = Soft)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.previous() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.FastRewind, contentDescription = stringResource(R.string.previous), modifier = Modifier.size(44.dp))
            }
            IconButton(onClick = { vm.toggle() }, modifier = Modifier.size(76.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                    modifier = Modifier.size(60.dp),
                )
            }
            IconButton(onClick = { vm.next() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.FastForward, contentDescription = stringResource(R.string.next), modifier = Modifier.size(44.dp))
            }
        }

        val context = LocalContext.current
        val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
        var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
        val volumeLabel = stringResource(R.string.volume)
        if (showVolume) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VolumeDown, contentDescription = null, tint = Soft, modifier = Modifier.size(20.dp))
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0)
                },
                valueRange = 0f..maxVolume.toFloat(),
                colors = sliderColors,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Icon(Icons.Rounded.VolumeUp, contentDescription = volumeLabel, tint = Soft, modifier = Modifier.size(20.dp))
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ToggleIcon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle), state.shuffle) { vm.toggleShuffle() }
            ToggleIcon(
                icon = if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                label = stringResource(R.string.repeat),
                active = state.repeat != Player.REPEAT_MODE_OFF,
            ) { vm.cycleRepeat() }
            ToggleIcon(
                icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = stringResource(R.string.favorite),
                active = favorite,
            ) { vm.toggleFavorite(song.id) }
            if (hasLyrics) {
                ToggleIcon(Icons.Rounded.Lyrics, stringResource(R.string.lyrics), mode == MODE_LYRICS) { onMode(MODE_LYRICS) }
            }
            ToggleIcon(Icons.Rounded.QueueMusic, stringResource(R.string.queue), mode == MODE_QUEUE) { onMode(MODE_QUEUE) }
        }
    }
}

/** Round toggle: a light disc behind the icon shows the active state, not colour alone. */
@Composable
private fun ToggleIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Color.White else Soft)
    }
}
