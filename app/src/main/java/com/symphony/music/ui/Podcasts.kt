package com.symphony.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.symphony.music.PlayerViewModel
import com.symphony.music.R
import com.symphony.music.data.Episode
import com.symphony.music.data.Podcast
import com.symphony.music.data.PodcastApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Picture from the web over a coloured initial. */
@Composable
private fun WebArt(url: String, label: String, modifier: Modifier, shape: Shape) {
    Box(modifier.clip(shape).background(colorFor(label)), contentAlignment = Alignment.Center) {
        Text(label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold)
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** Search for shows, or browse the ones already followed. */
@Composable
fun PodcastsScreen(vm: PlayerViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Podcast>>(emptyList()) }
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
            PodcastApi.search(q)
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

    val shown = if (q.length < 2) settings.podcasts else results

    LazyColumn(contentPadding = PaddingValues(bottom = 210.dp)) {
        stickyHeader {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.podcasts), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp))
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.podcast_search)) },
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
        if (q.length < 2) {
            item {
                Text(
                    text = stringResource(R.string.podcast_following),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                )
            }
        }
        when {
            status == 1 -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            status == 3 -> item { EmptyState(stringResource(R.string.podcast_error)) }
            shown.isEmpty() -> item {
                EmptyState(stringResource(if (q.length < 2) R.string.podcast_none_followed else R.string.podcast_empty))
            }
            else -> items(shown.size) { i ->
                val podcast = shown[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.rememberPodcast(podcast)
                            onOpen(podcast.id)
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WebArt(podcast.art, podcast.title, Modifier.size(64.dp), RoundedCornerShape(14.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(podcast.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(podcast.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One show: follow button and its episodes, newest first. */
@Composable
fun PodcastScreen(vm: PlayerViewModel, id: Long, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val podcast = remember(id, settings.podcasts) { vm.podcast(id) }
    if (podcast == null) {
        Column(Modifier.statusBarsPadding()) { EmptyState(stringResource(R.string.podcast_error)) }
        return
    }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    // 0 = loading, 1 = loaded, 2 = failed
    var status by remember { mutableStateOf(0) }
    LaunchedEffect(podcast.feedUrl) {
        status = 0
        val found = try {
            PodcastApi.episodes(podcast)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (found == null) {
            status = 2
        } else {
            episodes = found
            status = 1
        }
    }
    val following = settings.podcasts.any { it.id == podcast.id }

    LazyColumn(contentPadding = PaddingValues(bottom = 210.dp)) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth()) {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
                WebArt(podcast.art, podcast.title, Modifier.size(190.dp), RoundedCornerShape(18.dp))
                Spacer(Modifier.height(16.dp))
                Text(podcast.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(podcast.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                if (following) {
                    FilledTonalButton(onClick = { vm.togglePodcast(podcast) }) { Text(stringResource(R.string.podcast_unfollow), fontWeight = FontWeight.Bold) }
                } else {
                    Button(onClick = { vm.togglePodcast(podcast) }) { Text(stringResource(R.string.podcast_follow), fontWeight = FontWeight.Bold) }
                }
            }
        }
        when {
            status == 0 -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            status == 2 -> item { EmptyState(stringResource(R.string.podcast_error)) }
            episodes.isEmpty() -> item { EmptyState(stringResource(R.string.podcast_empty)) }
            else -> items(episodes.size) { i ->
                val episode = episodes[i]
                val active = state.current?.id == episode.id
                val left = settings.episodePositions[episode.id] ?: 0L
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.playEpisode(podcast, episode) }.padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        val details = listOf(
                            episode.date,
                            if (episode.durationMs > 0) formatTime(episode.durationMs) else "",
                            if (left > 5_000) stringResource(R.string.podcast_resume, formatTime(left)) else "",
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                        if (details.isNotEmpty()) {
                            Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (active) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}
