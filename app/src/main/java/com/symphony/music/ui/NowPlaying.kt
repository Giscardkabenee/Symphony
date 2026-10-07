package com.symphony.music.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
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
    val lyricsStatus by vm.lyricsStatus.collectAsStateWithLifecycle()
    val lyricsError by vm.lyricsError.collectAsStateWithLifecycle()
    val song = state.current
    if (song == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val context = LocalContext.current
    val rawTint by produceState(DefaultTint, song.albumId) {
        val found = dominantColor(context, song.artUri)
        value = if (found != null) lerp(found, Color.Black, 0.45f) else DefaultTint
    }
    val tint by animateColorAsState(rawTint, tween(700), label = "tint")
    // The cover breathes: full size while playing, slightly smaller when paused.
    val coverScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.93f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "cover",
    )
    var mode by rememberSaveable { mutableStateOf(MODE_COVER) }
    val activeLine = remember(lyrics, state.position) { currentLine(lyrics, state.position) }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint, lerp(tint, Color.Black, 0.5f))))
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // Blurred copy of the cover behind the whole screen (Android 12 and later).
            if (settings.fullCover && Build.VERSION.SDK_INT >= 31) {
                Crossfade(targetState = song, animationSpec = tween(700), label = "backdrop") { s ->
                    Artwork(s.albumId, s.album, Modifier.fillMaxSize().blur(56.dp), RectangleShape)
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Crossfade(targetState = mode, animationSpec = tween(320), modifier = Modifier.fillMaxSize(), label = "mode") { shown ->
                        when (shown) {
                            MODE_QUEUE -> QueueList(state, vm, onMore)
                            MODE_LYRICS -> LyricsView(lyrics, activeLine, settings.syncedLyrics, settings.blurLyrics, lyricsStatus, lyricsError, { vm.retryLyrics() }) { vm.seekTo(it) }
                            else -> Crossfade(targetState = song, animationSpec = tween(520), modifier = Modifier.fillMaxSize(), label = "song") { s ->
                                Cover(s, settings.fullCover, coverScale, settings.doubleTapSeek, { vm.seekBy(it) }, onClose)
                            }
                        }
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
private fun Cover(song: Song, fullCover: Boolean, scale: Float, doubleTap: Boolean, onSeekBy: (Long) -> Unit, onClose: () -> Unit) {
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
        // The cover fills the area and melts into the blurred backdrop at the top and bottom.
        Box(Modifier.fillMaxSize().then(drag).then(taps)) {
            Artwork(
                song.albumId,
                song.album,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.18f to Color.Black,
                                0.72f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                RectangleShape,
            )
        }
    } else {
        Box(Modifier.fillMaxSize().then(drag).then(taps).statusBarsPadding().padding(32.dp), contentAlignment = Alignment.Center) {
            Artwork(
                song.albumId,
                song.album,
                Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                RoundedCornerShape(16.dp),
            )
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
private fun LyricsView(
    lyrics: LyricsData?,
    activeLine: Int,
    highlight: Boolean,
    blurOthers: Boolean,
    status: Int,
    error: String,
    onRetry: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    if (lyrics == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (status == 1 || status == 0) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.lyrics_searching), color = Soft)
            } else {
                Text(
                    text = if (status == 4) stringResource(R.string.lyrics_error) else stringResource(R.string.no_lyrics),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                if (status == 4 && error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = Soft, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
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
        ThinSlider(
            value = shown,
            max = duration,
            label = stringResource(R.string.seek),
            onChange = { dragging = it },
            onFinished = {
                dragging?.let { vm.seekTo(it.toLong()) }
                dragging = null
            },
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
                AnimatedContent(
                    targetState = state.isPlaying,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
                    },
                    label = "play",
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                        modifier = Modifier.size(60.dp),
                    )
                }
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
            ThinSlider(
                value = volume,
                max = maxVolume.toFloat(),
                label = volumeLabel,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                onChange = {
                    volume = it
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0)
                },
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
            ToggleIcon(Icons.Rounded.Lyrics, stringResource(R.string.lyrics), mode == MODE_LYRICS) { onMode(MODE_LYRICS) }
            ToggleIcon(Icons.Rounded.QueueMusic, stringResource(R.string.queue), mode == MODE_QUEUE) { onMode(MODE_QUEUE) }
        }
    }
}

/** Thin bar without a thumb; it thickens while a finger drags it. */
@Composable
private fun ThinSlider(
    value: Float,
    max: Float,
    label: String,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    var active by remember { mutableStateOf(false) }
    val thickness by animateDpAsState(if (active) 10.dp else 5.dp, label = "thickness")
    val fraction = if (max > 0f) (value / max).coerceIn(0f, 1f) else 0f
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..max)
                setProgress { target ->
                    change(target.coerceIn(0f, max))
                    finished()
                    true
                }
            }
            .pointerInput(max) {
                detectTapGestures { offset ->
                    change((offset.x / size.width).coerceIn(0f, 1f) * max)
                    finished()
                }
            }
            .pointerInput(max) {
                detectHorizontalDragGestures(
                    onDragStart = { active = true },
                    onDragEnd = {
                        active = false
                        finished()
                    },
                    onDragCancel = {
                        active = false
                        finished()
                    },
                ) { input, _ ->
                    change((input.position.x / size.width).coerceIn(0f, 1f) * max)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(thickness).clip(CircleShape).background(Color.White.copy(alpha = 0.24f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
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
