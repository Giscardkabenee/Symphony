package com.symphony.music.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import coil.imageLoader
import coil.request.ImageRequest
import com.symphony.music.PlayerState
import com.symphony.music.R
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
            model = artworkUri(albumId),
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
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Floating surface: translucent with a light top edge when glass is on, opaque otherwise. */
@Composable
fun GlassBox(
    glass: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (glass) scheme.surfaceContainerHigh.copy(alpha = 0.82f) else scheme.surfaceContainerHigh
    val edge: Brush = if (glass) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.06f)))
    } else {
        SolidColor(scheme.outlineVariant)
    }
    Box(
        modifier = modifier
            .shadow(if (glass) 0.dp else 8.dp, shape)
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape),
        content = content,
    )
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", R.string.tab_home, Icons.Rounded.Home),
    Tab("albums", R.string.tab_albums, Icons.Rounded.Album),
    Tab("artists", R.string.tab_artists, Icons.Rounded.Person),
    Tab("library", R.string.tab_library, Icons.Rounded.LibraryMusic),
)

/** Mini-player pill above the rounded tab bar, with the round search button beside it. */
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val song = state.current
        val pill: Shape = if (classic) RoundedCornerShape(18.dp) else CircleShape
        val useGlass = glass && !classic
        AnimatedVisibility(
            visible = song != null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(200)),
        ) {
            if (song != null) GlassBox(useGlass, pill, Modifier.fillMaxWidth().height(60.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onOpenPlayer)
                        .padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(song.albumId, song.album, Modifier.size(42.dp), RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = song.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(28.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassBox(useGlass, pill, Modifier.weight(1f).height(64.dp)) {
                Row(Modifier.fillMaxSize().padding(5.dp)) {
                    tabs.forEach { tab ->
                        val selected = route == tab.route
                        val tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        val tabFill by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f) else Color.Transparent,
                            animationSpec = tween(250),
                            label = "tab",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(pill)
                                .background(tabFill)
                                .clickable { onTab(tab.route) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
                            if (!hideLabels) Text(
                                text = stringResource(tab.label),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = tint,
                            )
                        }
                    }
                }
            }
            GlassBox(useGlass, pill, Modifier.size(64.dp)) {
                IconButton(onClick = onSearch, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search))
                }
            }
        }
    }
}
