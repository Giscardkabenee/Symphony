package com.symphony.music.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symphony.music.R
import com.symphony.music.playback.SleepTimer
import kotlinx.coroutines.delay

/** Time left before the sleep timer stops the music, ticking every second; 0 when off. */
@Composable
fun rememberSleepLeft(): Long {
    val endsAt by SleepTimer.endsAt.collectAsStateWithLifecycle()
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(endsAt) {
        while (endsAt > 0L) {
            left = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            delay(1000)
        }
        left = 0L
    }
    return if (endsAt > 0L) left else 0L
}

@Composable
private fun SleepOption(icon: ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, fontWeight = FontWeight.SemiBold) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
fun SleepSheet(onDismiss: () -> Unit) {
    val endOfTrack by SleepTimer.endOfTrack.collectAsStateWithLifecycle()
    val left = rememberSleepLeft()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            val status = when {
                left > 0L -> stringResource(R.string.sleep_left, formatTime(left))
                endOfTrack -> stringResource(R.string.sleep_at_end)
                else -> stringResource(R.string.sleep_hint)
            }
            Text(
                text = status,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
            )
            for (minutes in listOf(15, 30, 45, 60, 90)) {
                SleepOption(Icons.Rounded.Timer, stringResource(R.string.sleep_minutes, minutes)) {
                    SleepTimer.start(minutes)
                    onDismiss()
                }
            }
            SleepOption(Icons.Rounded.MusicNote, stringResource(R.string.sleep_end_track)) {
                SleepTimer.stopAtEndOfTrack()
                onDismiss()
            }
            if (left > 0L || endOfTrack) {
                SleepOption(Icons.Rounded.Close, stringResource(R.string.sleep_off)) {
                    SleepTimer.cancel()
                    onDismiss()
                }
            }
        }
    }
}
