package com.symphony.music.ui

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import coil.imageLoader
import coil.request.ImageRequest
import com.symphony.music.PlayerState
import com.symphony.music.R
import com.symphony.music.data.ArtOverrides
import com.symphony.music.data.ArtistImages
import com.symphony.music.data.Song
import com.symphony.music.data.artworkUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val artColors = listOf(
    Color(0xFF1F6F78), Color(0xFF8A5CD6), Color(0xFFC7513E), Color(0xFF3F7D4E),
    Color(0xFFB8862F), Color(0xFF2D5E8A), Color(0xFF7A4B2A),
)

/** Stable placeholder colour for an album or artist without artwork. */
fun colorFor(key: String): Color = artColors[(key.hashCode() and 0x7fffffff) % artColors.size]

/**
 * Swipe a song to the right to play it next, to the left to add it at the end of the queue.
 * The row springs back; a coloured pill grows in the space it uncovers.
 */
@Composable
fun QueueSwipe(onPlayNext: () -> Unit, onAddToQueue: () -> Unit, content: @Composable () -> Unit) {
    val view = LocalView.current
    val next by rememberUpdatedState(onPlayNext)
    val add by rememberUpdatedState(onAddToQueue)
    var last by remember { mutableLongStateOf(0L) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val now = android.os.SystemClock.uptimeMillis()
            if (value != SwipeToDismissBoxValue.Settled && now - last > 700) {
                last = now
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                if (value == SwipeToDismissBoxValue.StartToEnd) next() else add()
            }
            false
        },
        positionalThreshold = { it * 0.28f },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val offset = try { state.requireOffset() } catch (e: IllegalStateException) { 0f }
            if (offset == 0f) return@SwipeToDismissBox
            val toRight = offset > 0
            val width = with(LocalDensity.current) { kotlin.math.abs(offset).toDp() }
            val armed = state.targetValue != SwipeToDismissBoxValue.Settled
            val iconScale by animateFloatAsState(if (armed) 1.15f else 0.85f, spring(dampingRatio = 0.45f), label = "swipeIcon")
            val colors = if (toRight) listOf(Color(0xFF5B3CC4), Color(0xFF8B5CF6)) else listOf(Color(0xFF14B8A6), Color(0xFF0F766E))
            Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .align(if (toRight) Alignment.CenterStart else Alignment.CenterEnd)
                        .width((width - 10.dp).coerceAtLeast(0.dp))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(colors))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (toRight) Arrangement.Start else Arrangement.End,
                ) {
                    Icon(
                        imageVector = if (toRight) Icons.AutoMirrored.Rounded.PlaylistPlay else Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                    )
                    if (width > 120.dp) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(if (toRight) R.string.swipe_next else R.string.swipe_queue),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
    ) { content() }
}

/** Round button over the player's backdrop: a soft frosted disc with a hairline highlight, no heavy rim. */
fun Modifier.playerChip(): Modifier = this
    .clip(CircleShape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.22f))))
    .border(0.6.dp, Color.White.copy(alpha = 0.3f), CircleShape)

/** Where the mini-player and its cover sit, so the full player can grow out of them. */
object PlayerMorph {
    var capsule by androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    var cover by androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
}

/** The one colour for "selected" everywhere (pills, play buttons, switches, tab marker): soft light grey in dark mode. */
val androidx.compose.material3.ColorScheme.selection: Color
    get() = if (background.luminance() < 0.5f) Color(0xFFD6D6DC) else onSurface

/** Whether the player is playing right now, for the little animated bars in the lists. */
val LocalIsPlaying = compositionLocalOf { false }

/** Four bars that dance while the song plays and rest low when it is paused. */
@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val dance = rememberInfiniteTransition(label = "bars")
    val speeds = listOf(520, 380, 610, 450)
    val heights = speeds.mapIndexed { i, ms ->
        val h by dance.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ms, delayMillis = i * 60, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar$i",
        )
        h
    }
    val rest by animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "rest")
    androidx.compose.foundation.Canvas(modifier.size(width = 18.dp, height = 16.dp)) {
        val w = size.width / 7f
        heights.forEachIndexed { i, h ->
            val level = 0.3f + (h - 0.3f) * rest
            val barH = size.height * level.coerceIn(0.2f, 1f)
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(i * 2 * w, size.height - barH),
                size = androidx.compose.ui.geometry.Size(w, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2),
            )
        }
    }
}

/** Only songs on the phone have options and a favourite state. */
fun Song.hasOptions(): Boolean = id >= 0

fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** Dominant colour of the album cover, or null when there is no cover. */
suspend fun dominantColor(context: Context, uri: Uri): Color? = withContext(Dispatchers.IO) {
    try {
        val request = ImageRequest.Builder(context).data(uri).size(128).allowHardware(false).build()
        val drawable = context.imageLoader.execute(request).drawable ?: return@withContext null
        val palette = Palette.from(drawable.toBitmap()).generate()
        val swatch = palette.darkVibrantSwatch ?: palette.vibrantSwatch
            ?: palette.darkMutedSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
        swatch?.let { Color(it.rgb) }
    } catch (e: Exception) {
        null
    }
}

/** Up to three distinct colours from the cover, for the drifting glow behind the player. */
suspend fun paletteColors(context: Context, uri: Uri): List<Color> = withContext(Dispatchers.IO) {
    try {
        val request = ImageRequest.Builder(context).data(uri).size(128).allowHardware(false).build()
        val drawable = context.imageLoader.execute(request).drawable ?: return@withContext emptyList()
        val palette = Palette.from(drawable.toBitmap()).generate()
        listOfNotNull(
            palette.vibrantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch,
            palette.lightVibrantSwatch,
            palette.dominantSwatch,
        ).map { Color(it.rgb) }.distinct().take(3)
    } catch (e: Exception) {
        emptyList()
    }
}

/** Album cover with a coloured initial behind it when the file has no artwork. */
@Composable
fun Artwork(
    albumId: Long,
    label: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    Box(modifier.clip(shape).background(colorFor(label)), contentAlignment = Alignment.Center) {
        Text(
            text = label.take(1).uppercase(),
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
        )
        AsyncImage(
            model = ArtOverrides.urls[albumId] ?: artworkUri(albumId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** Artist photo found online, over a coloured initial while loading or when none exists. */
@Composable
fun ArtistAvatar(name: String, online: Boolean, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    val context = LocalContext.current
    var fellBack by remember(name) { mutableStateOf(false) }
    val url by produceState<String?>(null, name, online, fellBack) {
        value = if (!online) null
        else if (fellBack) ArtistImages.fallback(context, name)
        else ArtistImages.find(context, name)
    }
    val ring = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(modifier.clip(shape).background(colorFor(name)), contentAlignment = Alignment.Center) {
        Text(
            text = name.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleLarge,
        )
        if (url != null) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .addHeader("User-Agent", ArtistImages.USER_AGENT)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Portraits are usually taller than wide: keep the face, not the chest.
                alignment = androidx.compose.ui.BiasAlignment(0f, -0.7f),
                modifier = Modifier.matchParentSize(),
                // If the Wikipedia portrait can't be loaded, fall back to Deezer once.
                onError = { if (!fellBack && url?.contains("wikimedia") == true) fellBack = true },
            )
        }
        // A fine light ring keeps dark photos from melting into a dark page.
        if (shape == CircleShape) Box(Modifier.matchParentSize().border(1.dp, ring, shape))
    }
}

@Composable
fun SongRow(
    song: Song,
    active: Boolean = false,
    onMore: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .cascade()
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.albumId, song.album, Modifier.size(48.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (active) {
            PlayingBars(LocalIsPlaying.current, LocalContentColor.current, Modifier.padding(horizontal = 10.dp))
        }
        if (song.duration > 0) {
            Text(
                text = formatTime(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Shared blur source: the screen content that glass surfaces blur and refract. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/** Glass lighting kept quiet: a faint sheen from the top and a thin glow along the bottom edge. */
fun Modifier.dropletShine(strength: Float = 1f): Modifier = drawBehind {
    if (size.width <= 0f || size.height <= 0f) return@drawBehind
    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f * strength), 0.5f to Color.Transparent))
    drawRect(Brush.verticalGradient(0.82f to Color.Transparent, 1f to Color.White.copy(alpha = 0.07f * strength)))
}

/** A small glass drop: translucent fill, droplet lighting and a bright rim. */
fun Modifier.liquidDrop(shape: Shape, tint: Color = Color.White.copy(alpha = 0.12f)): Modifier =
    this.clip(shape)
        .background(tint)
        .dropletShine()
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.16f))),
            shape,
        )

/**
 * Floating glass: the content behind is blurred, then lit like a pane of glass. A wash of the
 * playing cover's colour, a sheen from the top, a bright rim that catches the light on two
 * corners, and a faint shade along the bottom. Opaque when glass is off.
 */
@Composable
fun GlassBox(
    glass: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haze = LocalHaze.current
    val backdrop = scheme.background
    // Smoked glass: a dark veil in the dark theme, a pale one in the light theme.
    val dark = scheme.background.luminance() < 0.5f
    val glassTint = if (dark) Color(0xFF0C0C10).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.55f)
    val glow by animateColorAsState(accent ?: Color.Transparent, tween(700), label = "glow")
    val base = modifier.shadow(if (glass) 14.dp else 8.dp, shape).clip(shape)
    val surface = when {
        glass && haze != null -> base.hazeEffect(state = haze) {
            blurEnabled = true
            blurRadius = 22.dp
            backgroundColor = backdrop
            tints = listOf(HazeTint(glassTint))
            noiseFactor = 0.02f
        }
        glass -> base.background(glassTint.copy(alpha = 0.82f))
        else -> base.background(scheme.surfaceContainerHigh)
    }
    val lit = if (glass) {
        Modifier.drawBehind {
            if (size.width <= 0f || size.height <= 0f) return@drawBehind
            // Colour of the music, stronger at the two ends.
            drawRect(
                Brush.horizontalGradient(
                    0f to glow.copy(alpha = glow.alpha * 0.24f),
                    0.5f to glow.copy(alpha = glow.alpha * 0.06f),
                    1f to glow.copy(alpha = glow.alpha * 0.18f),
                )
            )
            // Light falling on the top half.
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.08f), 0.5f to Color.Transparent))
            // Thickness of the glass along the bottom.
            drawRect(Brush.verticalGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f)))
        }
    } else {
        Modifier
    }
    val edge: Brush = if (glass) {
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.3f),
            0.35f to Color.White.copy(alpha = 0.12f),
            1f to Color.White.copy(alpha = 0.16f),
        )
    } else {
        SolidColor(scheme.outlineVariant)
    }
    Box(
        modifier = surface.then(lit).border(1.dp, edge, shape),
        content = content,
    )
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", R.string.tab_home, Icons.Rounded.Home),
    Tab("music", R.string.tab_music, Icons.Rounded.MusicNote),
)

/**
 * Two clean white pills: the mini-player, and the tabs with a dark marker that slides with a little
 * bounce to the chosen tab while its name unfolds.
 */
@Composable
fun FloatingBar(
    state: PlayerState,
    glass: Boolean,
    route: String?,
    onTab: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    hideLabels: Boolean = false,
    onPrevious: () -> Unit = {},
    classic: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dark = scheme.background.luminance() < 0.5f
    val song = state.current
    // One capsule for the mini-player and the tabs, lightly tinted with the cover's colour.
    val found by produceState<Color?>(null, song?.albumId) {
        value = song?.let { dominantColor(context, artworkUri(it.albumId)) }
    }
    val base = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    val capsule by animateColorAsState(
        found?.let { lerp(base, it, if (dark) 0.24f else 0.10f) } ?: base,
        tween(600),
        label = "capsule",
    )
    val accent by animateColorAsState(
        found?.let { if (dark) lerp(it, Color.White, 0.35f) else lerp(it, Color.Black, 0.15f) } ?: scheme.onSurface,
        tween(600),
        label = "accent",
    )
    val edge = scheme.outlineVariant.copy(alpha = if (dark) 0.3f else 0.45f)
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            // The page fades into the background behind the bar, so nothing reads through it.
            .background(
                Brush.verticalGradient(
                    0f to scheme.background.copy(alpha = 0f),
                    0.25f to scheme.background.copy(alpha = 0.92f),
                    0.45f to scheme.background,
                    1f to scheme.background,
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                .clip(shape)
                .background(capsule)
                .border(1.dp, edge, shape)
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
        ) {
            if (song != null) {
                // Swipe the mini-player sideways to change song; it follows the finger and springs back.
                val view = LocalView.current
                val scope = rememberCoroutineScope()
                val shift = remember { androidx.compose.animation.core.Animatable(0f) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { PlayerMorph.capsule = it.boundsInRoot() }
                        .height(66.dp)
                        .clickable(onClick = onOpenPlayer)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val x = shift.value
                                    if (x < -110f) { onNext(); view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                                    else if (x > 110f) { onPrevious(); view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                                    scope.launch { shift.animateTo(0f, spring(dampingRatio = 0.6f)) }
                                },
                                onDragCancel = { scope.launch { shift.animateTo(0f, spring(dampingRatio = 0.6f)) } },
                            ) { _, amount -> scope.launch { shift.snapTo((shift.value + amount * 0.6f).coerceIn(-220f, 220f)) } }
                        }
                        .padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            (slideInVertically(tween(380)) { it } + fadeIn(tween(380))) togetherWith
                                (slideOutVertically(tween(300)) { -it } + fadeOut(tween(220)))
                        },
                        contentKey = { it.id },
                        modifier = Modifier.weight(1f).graphicsLayer {
                            translationX = shift.value
                            alpha = 1f - (kotlin.math.abs(shift.value) / 400f)
                        },
                        label = "miniSong",
                    ) { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Artwork(s.albumId, s.album, Modifier.size(46.dp).onGloballyPositioned { PlayerMorph.cover = it.boundsInRoot() }, RoundedCornerShape(12.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                    IconButton(onClick = onToggle) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = state.isPlaying,
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.4f) + fadeIn(tween(150))) togetherWith
                                    (scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(120)))
                            },
                            label = "miniToggle",
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(26.dp))
                    }
                }
                // Progress, in the cover's colour.
                val fraction = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(scheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent))
                }
            }
            // Tabs: equal slots, a soft marker gliding under the chosen one.
            val items = tabs.map { Triple(it.route, it.label, it.icon) } + Triple("search", R.string.search, Icons.Rounded.Search)
            val selected = items.indexOfFirst { it.first == route }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                val slot = maxWidth / items.size
                val markerX by animateDpAsState(
                    targetValue = slot * selected.coerceAtLeast(0),
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
                    label = "marker",
                )
                val markerAlpha by animateFloatAsState(if (selected >= 0) 1f else 0f, tween(200), label = "markerAlpha")
                Box(
                    Modifier
                        .offset(x = markerX)
                        .width(slot)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = markerAlpha }
                        .clip(CircleShape)
                        .background(scheme.onSurface.copy(alpha = if (dark) 0.14f else 0.08f))
                )
                Row(Modifier.fillMaxSize()) {
                    items.forEachIndexed { i, (r, label, icon) ->
                        TabSlot(icon, stringResource(label), i == selected, !hideLabels, Modifier.width(slot).fillMaxHeight(), selectedTint = scheme.onSurface) {
                            if (r == "search") onSearch() else onTab(r)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabSlot(icon: ImageVector, label: String, selected: Boolean, showLabel: Boolean, modifier: Modifier, selectedTint: Color? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (selected) (selectedTint ?: scheme.surface) else scheme.onSurfaceVariant, tween(260), label = "tint")
    // Physical feel: the slot sinks under the finger, springs back, and the phone gives a short tick.
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f), label = "lift")
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = null, onClickLabel = label) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = if (selected && showLabel) null else label,
            tint = tint,
            modifier = Modifier.size(24.dp).graphicsLayer {
                val k = 1f + 0.08f * lift
                scaleX = k
                scaleY = k
            },
        )
        AnimatedVisibility(
            visible = selected && showLabel,
            enter = fadeIn(tween(220, delayMillis = 80)) + expandHorizontally(spring(dampingRatio = 0.7f)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(180)),
        ) {
            Text(label, modifier = Modifier.padding(start = 8.dp), maxLines = 1, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}
