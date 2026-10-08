package com.symphony.music.ui

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
    val url by produceState<String?>(null, name, online) {
        value = if (online) ArtistImages.find(context, name) else null
    }
    Box(modifier.clip(shape).background(colorFor(name)), contentAlignment = Alignment.Center) {
        Text(
            text = name.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleLarge,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
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
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.padding(horizontal = 8.dp))
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

/** Mini-player card with a progress ring around play / pause, over a small centred island of three tabs. */
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
    classic: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val song = state.current
        AnimatedVisibility(
            visible = song != null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(200)),
        ) {
            if (song != null) {
                val card = RoundedCornerShape(22.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .shadow(14.dp, card, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                        .clip(card)
                        .background(scheme.surfaceContainerLowest.copy(alpha = 0.96f))
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), card)
                        .clickable(onClick = onOpenPlayer)
                        .padding(start = 9.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(song.albumId, song.album, Modifier.size(44.dp), RoundedCornerShape(11.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    // Play / pause inside a ring that fills as the song advances.
                    val fraction = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
                    val ring = scheme.onSurface
                    val track = scheme.surfaceContainerHighest
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = stringResource(if (state.isPlaying) R.string.pause else R.string.play), onClick = onToggle)
                            .drawBehind {
                                val stroke = 3.dp.toPx()
                                val inset = stroke / 2 + 1.dp.toPx()
                                val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                                drawArc(ring, -90f, 360f * fraction, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(26.dp))
                    }
                }
            }
        }
        // Small island in contrast with the page: dark in light theme, light in dark theme.
        val island = CircleShape
        Row(
            modifier = Modifier
                .height(58.dp)
                .shadow(16.dp, island, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(island)
                .background(scheme.inverseSurface)
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { tab ->
                IslandItem(tab.icon, stringResource(tab.label), route == tab.route) { onTab(tab.route) }
            }
            IslandItem(Icons.Rounded.Search, stringResource(R.string.search), route == "search", onSearch)
        }
    }
}

@Composable
private fun IslandItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) scheme.inverseOnSurface else Color.Transparent, tween(240), label = "fill")
    val tint by animateColorAsState(if (selected) scheme.inverseSurface else scheme.inverseOnSurface.copy(alpha = 0.62f), tween(240), label = "tint")
    // Physical feel: the item sinks under the finger, springs back, and the phone gives a short tick.
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 44.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClickLabel = label) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = tint)
    }
}
