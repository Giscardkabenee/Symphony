package com.symphony.music.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.symphony.music.R
import com.symphony.music.data.ArtistBio
import com.symphony.music.data.Release
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

private fun open(context: android.content.Context, link: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun fans(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f M", count / 1_000_000f)
    count >= 1_000 -> String.format(Locale.getDefault(), "%.1f k", count / 1_000f)
    else -> count.toString()
}

@Composable
private fun releaseType(type: String) = when (type) {
    "single" -> stringResource(R.string.release_single)
    "ep" -> stringResource(R.string.release_ep)
    "compile" -> stringResource(R.string.release_compilation)
    else -> stringResource(R.string.release_album)
}

private fun releaseDate(date: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!
    DateFormat.getDateInstance(DateFormat.LONG).format(parsed)
}.getOrDefault(date)

/** "Latest release", Spotify-style: cover, title, type and date. Opens the album when it is in the library. */
@Composable
fun LatestRelease(release: Release, ownedAlbum: Long?, onAlbum: (Long) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.artist_latest))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { if (ownedAlbum != null) onAlbum(ownedAlbum) else if (release.link.isNotBlank()) open(context, release.link) }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = release.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(releaseDate(release.date).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(release.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val where = if (ownedAlbum != null) stringResource(R.string.in_library) else stringResource(R.string.not_in_library)
                Text(releaseType(release.type) + " · " + where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "About": the photo, fan count and a biography that opens up on tap. */
@Composable
fun ArtistAboutCard(name: String, info: ArtistBio, photos: Boolean) {
    val context = LocalContext.current
    if (info.bio == null && info.fans <= 0) return
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.artist_about))
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { expanded = !expanded }
                .animateContentSize(),
        ) {
            Box {
                ArtistAvatar(name, photos, Modifier.fillMaxWidth().height(220.dp), RoundedCornerShape(0.dp))
                if (info.fans > 0) {
                    Text(
                        stringResource(R.string.artist_fans, fans(info.fans)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (info.bio != null) {
                Text(
                    info.bio,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (expanded) R.string.read_less else R.string.read_more),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (info.bioUrl != null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).clickable { open(context, info.bioUrl) }.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.source_wikipedia), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Recent news about the artist; each opens in the browser. */
@Composable
fun ArtistNews(info: ArtistBio) {
    if (info.news.isEmpty()) return
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.artist_news))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            info.news.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { open(context, item.link) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Newspaper, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        val ago = if (item.timeMs > 0) " · " + DateUtils.getRelativeTimeSpanString(item.timeMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else ""
                        Text(item.source + ago, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
