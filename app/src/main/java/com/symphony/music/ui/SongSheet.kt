package com.symphony.music.ui

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.symphony.music.R
import com.symphony.music.data.Song

@Composable
private fun RowScope.QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(78.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** The song's options: cover and favourite on top, three quick actions, then where to go, then the file's details. */
@Composable
fun SongSheet(
    song: Song,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onPlaylist: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share)
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.albumId, song.album, Modifier.size(64.dp), RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = song.artist + " · " + song.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The heart pops when it changes.
            val pop by animateFloatAsState(if (favorite) 1.15f else 1f, spring(dampingRatio = 0.35f), label = "heart")
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (favorite) Color(0xFFE11D48).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClickLabel = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite), onClick = onFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (favorite) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.swipe_next), onPlayNext)
            QuickAction(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.swipe_queue), onQueue)
            QuickAction(Icons.Rounded.Share, shareTitle) {
                try {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("audio/*")
                        .putExtra(Intent.EXTRA_STREAM, song.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, shareTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // No app can receive the file.
                }
            }
        }
        Column(Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            SheetRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist), onPlaylist)
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SheetRow(Icons.Rounded.Album, stringResource(R.string.go_to_album), onAlbum)
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SheetRow(Icons.Rounded.Person, stringResource(R.string.go_to_artist), onArtist)
        }
        // What the file is.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ext = song.path.substringAfterLast('.', "").uppercase()
            if (ext.isNotBlank()) InfoChip(ext)
            if (song.bitrate > 0) InfoChip("${song.bitrate / 1000} kbps")
            if (song.duration > 0) InfoChip(formatTime(song.duration))
            if (song.year > 0) InfoChip(song.year.toString())
        }
    }
}
