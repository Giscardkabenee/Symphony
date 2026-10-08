package com.symphony.music.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symphony.music.PlayerViewModel
import com.symphony.music.R
import com.symphony.music.data.HeadphoneModel
import com.symphony.music.data.Headphones
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Pick your headphones; Symphony applies the AutoEq correction measured for that model. */
@Composable
fun HeadphoneSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<HeadphoneModel>>(emptyList()) }
    // 0 = loading, 1 = ready, 2 = failed
    var status by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    val applied = stringResource(R.string.hp_applied)
    val failed = stringResource(R.string.hp_failed)

    LaunchedEffect(attempt) {
        status = 0
        models = try {
            Headphones.models(context).also { status = 1 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = 2
            emptyList()
        }
    }
    val q = query.trim()
    val shown = remember(q, models) {
        if (q.length < 2) emptyList() else models.filter { it.name.contains(q, ignoreCase = true) }.take(80)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Headphones, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.hp_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.hp_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))

            // The model in use, with its switch.
            val current = settings.headphone
            if (current != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(current.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = stringResource(R.string.hp_detail, current.source, current.filters.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.headphoneOn, onCheckedChange = { vm.setFlag("hp_enabled", it) })
                    IconButton(onClick = { vm.setHeadphone(null) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.hp_remove))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.hp_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 380.dp)) {
                when {
                    status == 0 -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.hp_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    status == 2 -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.hp_offline), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
                    }
                    q.length < 2 -> Text(
                        stringResource(R.string.hp_hint, models.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    shown.isEmpty() -> Text(stringResource(R.string.no_results, q), modifier = Modifier.align(Alignment.Center))
                    else -> LazyColumn {
                        items(shown.size) { i ->
                            val model = shown[i]
                            val key = model.path
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = busy == null) {
                                        busy = key
                                        scope.launch {
                                            try {
                                                vm.setHeadphone(Headphones.profile(model))
                                                Toast.makeText(context, String.format(applied, model.name), Toast.LENGTH_SHORT).show()
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
                                            }
                                            busy = null
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(model.source, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (busy == key) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else if (current?.name == model.name && current.source == model.source) Text("✓", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.hp_credit),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
