package com.symphony.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.symphony.music.PlayerViewModel
import com.symphony.music.R
import com.symphony.music.data.RadioApi
import com.symphony.music.data.Station
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale

private const val FAVORITES = "favorites"
private const val TOP = "top"

/** Countries offered as quick filters; the phone's own country comes first when it is known. */
private val countryCodes = listOf("CD", "RW", "UG", "BI", "KE", "TZ", "FR", "BE")

@Composable
fun RadioScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val home = remember { Locale.getDefault().country.uppercase() }
    val codes = remember(home) { if (home.length == 2) (listOf(home) + countryCodes).distinct() else countryCodes }
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(codes.first()) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    // 0 = loading, 1 = loaded, 2 = failed
    var status by remember { mutableStateOf(0) }
    val q = query.trim()

    LaunchedEffect(mode, q) {
        if (q.isEmpty() && mode == FAVORITES) {
            status = 1
            return@LaunchedEffect
        }
        status = 0
        if (q.isNotEmpty()) delay(450)
        val result = try {
            when {
                q.isNotEmpty() -> RadioApi.search(q)
                mode == TOP -> RadioApi.top()
                else -> RadioApi.byCountry(mode)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (result == null) {
            status = 2
        } else {
            stations = result
            status = 1
        }
    }

    val shown = if (q.isEmpty() && mode == FAVORITES) settings.stations else stations

    LazyColumn(contentPadding = PaddingValues(bottom = 210.dp)) {
        stickyHeader {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.radios), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp))
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.radio_search)) },
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
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = mode == FAVORITES, onClick = { mode = FAVORITES }, label = { Text(stringResource(R.string.favorites)) })
                    }
                    item {
                        FilterChip(selected = mode == TOP, onClick = { mode = TOP }, label = { Text(stringResource(R.string.radio_popular)) })
                    }
                    items(codes.size) { i ->
                        val code = codes[i]
                        FilterChip(selected = mode == code, onClick = { mode = code }, label = { Text(Locale("", code).displayCountry) })
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        }
        item {
            Text(
                text = stringResource(R.string.radio_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        when {
            status == 0 -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            status == 2 -> item { EmptyState(stringResource(R.string.radio_error)) }
            shown.isEmpty() -> item {
                EmptyState(stringResource(if (q.isEmpty() && mode == FAVORITES) R.string.radio_no_favorites else R.string.radio_empty))
            }
            else -> items(shown.size) { i ->
                val station = shown[i]
                StationRow(
                    station = station,
                    active = state.current?.id == station.id,
                    favorite = settings.stations.any { it.id == station.id },
                    onFavorite = { vm.toggleStation(station) },
                ) { vm.playStation(station) }
            }
        }
    }
}

@Composable
internal fun StationRow(station: Station, active: Boolean, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(colorFor(station.name)), contentAlignment = Alignment.Center) {
            Text(station.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold)
            if (station.icon.isNotBlank()) {
                AsyncImage(model = station.icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            val details = listOf(station.country, station.tags, if (station.bitrate > 0) "${station.bitrate} kbit/s" else "").filter { it.isNotBlank() }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (active) Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
            )
        }
    }
}
