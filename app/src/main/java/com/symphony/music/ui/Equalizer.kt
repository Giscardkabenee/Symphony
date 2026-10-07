package com.symphony.music.ui

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symphony.music.PlayerViewModel
import com.symphony.music.R
import com.symphony.music.data.EqBands
import com.symphony.music.playback.PlaybackInfo
import java.util.Locale
import kotlin.math.roundToInt

private val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

/** Name and gain in dB for each of the five bands. */
private val presets = listOf(
    "Flat" to listOf(0, 0, 0, 0, 0),
    "Bass Boost" to listOf(6, 4, 0, 0, 0),
    "Rock" to listOf(5, 3, -1, 3, 5),
    "Pop" to listOf(-1, 2, 4, 2, -1),
    "Jazz" to listOf(4, 2, -2, 2, 4),
    "Vocal" to listOf(-2, 0, 4, 3, 0),
    "Treble" to listOf(0, 0, 0, 4, 6),
)

/** A slim bar that fills from its middle (or from the left) up to the value. */
@Composable
private fun BarSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    centered: Boolean,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
    onChange: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val trackColor = scheme.surfaceContainerHighest
    val fillColor = scheme.onSurface
    val tick = scheme.onSurfaceVariant
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Slider(
        value = value,
        onValueChange = onChange,
        onValueChangeFinished = onFinished,
        valueRange = range,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        thumb = { Spacer(Modifier.size(width = 2.dp, height = 28.dp)) },
        track = {
            Box(
                Modifier.fillMaxWidth().height(28.dp).drawBehind {
                    val bar = 12.dp.toPx()
                    val top = (size.height - bar) / 2
                    val radius = CornerRadius(bar / 2)
                    drawRoundRect(trackColor, Offset(0f, top), Size(size.width, bar), radius)
                    val origin = if (centered) size.width / 2 else 0f
                    val end = size.width * fraction
                    val left = minOf(origin, end)
                    val width = kotlin.math.abs(end - origin)
                    if (width > 0.5f) drawRoundRect(fillColor, Offset(left, top), Size(width, bar), radius)
                    // The resting point: the middle for a band, the left edge for an effect.
                    drawRoundRect(tick, Offset(origin - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
                },
            )
        },
    )
}

@Composable
private fun EffectRow(icon: ImageVector, title: String, value: Int, enabled: Boolean, onSet: (Int) -> Unit) {
    var live by remember(value) { mutableFloatStateOf(value / 10f) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${live.roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        BarSlider(live, 0f..100f, centered = false, enabled = enabled, label = title, modifier = Modifier.fillMaxWidth(), onFinished = { onSet(live.roundToInt() * 10) }) { live = it }
    }
}

/** Five-band equalizer with presets, bass boost and stereo widening, shown over the settings. */
@Composable
fun EqualizerSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sessionId by PlaybackInfo.audioSessionId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val on = settings.eqEnabled
    // Levels are kept in dB while a finger moves, and saved when it lifts.
    val saved = settings.eqLevels
    val live = remember(saved) { mutableStateListOf<Float>().apply { addAll(saved.map { it.toFloat() }) } }
    val systemPanel = remember(sessionId) { systemEqualizer(context, sessionId) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.equalizer), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    vm.setEqLevels(List(EqBands) { 0 })
                    vm.setEffect("bass_boost", 0)
                    vm.setEffect("virtualizer", 0)
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.eq_reset))
                }
                Switch(checked = on, onCheckedChange = { vm.setFlag("eq_enabled", it) })
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets.size) { i ->
                    val (name, levels) = presets[i]
                    val selected = saved == levels
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
                            .clickable {
                                vm.setEqLevels(levels)
                                if (!on) vm.setFlag("eq_enabled", true)
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(name, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.alpha(if (on) 1f else 0.45f)) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    for (i in 0 until EqBands) {
                        val level = live.getOrElse(i) { 0f }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(bandLabels[i], modifier = Modifier.width(64.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BarSlider(level, -12f..12f, centered = true, enabled = on, label = bandLabels[i], modifier = Modifier.weight(1f), onFinished = { vm.setEqLevels(live.map { it.roundToInt() }) }) {
                                if (i < live.size) live[i] = it
                            }
                            Text(
                                text = String.format(Locale.US, "%+.0f dB", level),
                                modifier = Modifier.width(60.dp),
                                textAlign = TextAlign.End,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    EffectRow(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.eq_bass), settings.bassBoost, on) { vm.setEffect("bass_boost", it) }
                    EffectRow(Icons.Rounded.SurroundSound, stringResource(R.string.eq_virtualizer), settings.virtualizer, on) { vm.setEffect("virtualizer", it) }
                }
            }
            if (systemPanel != null) {
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(systemPanel)
                        } catch (e: Exception) {
                            // The phone's own panel refused to open.
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp).height(52.dp),
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.eq_system), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The phone's own sound-effects panel, when it has one. */
private fun systemEqualizer(context: Context, sessionId: Int): Intent? {
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (intent.resolveActivity(context.packageManager) != null) intent else null
}
