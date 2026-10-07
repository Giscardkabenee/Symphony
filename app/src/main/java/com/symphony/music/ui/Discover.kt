package com.symphony.music.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.symphony.music.PlayerViewModel
import com.symphony.music.R
import com.symphony.music.data.CatalogTrack
import com.symphony.music.data.DeezerSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Look a song up in Deezer's catalogue, hear its 30-second preview, or open its page. */
@Composable
fun DiscoverScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogTrack>>(emptyList()) }
    // 0 = idle, 1 = searching, 2 = done, 3 = failed
    var status by remember { mutableStateOf(0) }
    val q = query.trim()

    LaunchedEffect(q) {
        if (q.length < 2) {
            status = 0
            return@LaunchedEffect
        }
        status = 1
        delay(500)
        val found = try {
            DeezerSearch.search(q)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (found == null) {
            status = 3
        } else {
            results = found
            status = 2
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 210.dp)) {
        stickyHeader {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.discover), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp))
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.discover_search)) },
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.discover_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        when {
            status == 1 -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            status == 3 -> item { EmptyState(stringResource(R.string.podcast_error)) }
            status == 2 && results.isEmpty() -> item { EmptyState(stringResource(R.string.no_results, q)) }
            status == 2 -> items(results.size) { i ->
                val track = results[i]
                val active = state.current?.id == track.toSong().id
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.playPreview(track) }.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(colorFor(track.album)), contentAlignment = Alignment.Center) {
                        if (track.cover.isNotBlank()) {
                            AsyncImage(model = track.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (active) Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.padding(horizontal = 6.dp))
                    if (track.durationMs > 0) {
                        Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            // No app can open the page.
                        }
                    }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = stringResource(R.string.open_in_deezer), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
