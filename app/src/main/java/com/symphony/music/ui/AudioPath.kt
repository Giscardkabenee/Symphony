package com.symphony.music.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symphony.music.R
import com.symphony.music.data.AppSettings
import com.symphony.music.data.Song
import com.symphony.music.playback.PlaybackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val Sheet = Color(0xFF141418)
private val Line = Color.White.copy(alpha = 0.14f)
private val Dim = Color.White.copy(alpha = 0.6f)
private val Good = Color(0xFF4ADE80)
private val Warn = Color(0xFFFBBF24)
private val Note = Color(0xFF60A5FA)

/** What the file itself says: bit depth, sample rate and bitrate, read from its tags. */
private data class FileFacts(val bits: Int, val rate: Int, val bitrate: Int, val mime: String?)

private suspend fun readFile(context: Context, song: Song): FileFacts = withContext(Dispatchers.IO) {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(context, song.uri)
        fun int(key: Int) = r.extractMetadata(key)?.toIntOrNull() ?: 0
        FileFacts(
            bits = if (Build.VERSION.SDK_INT >= 31) int(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE) else 0,
            rate = if (Build.VERSION.SDK_INT >= 31) int(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE) else 0,
            bitrate = int(MediaMetadataRetriever.METADATA_KEY_BITRATE),
            mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
        )
    } catch (e: Exception) {
        FileFacts(0, 0, song.bitrate, null)
    } finally {
        runCatching { r.release() }
    }
}

private enum class Device { Speaker, Wired, Bluetooth, Usb }

/** Where Android most likely sends the music right now. */
private fun currentDevice(context: Context, preferUsb: Boolean): Pair<Device, String> {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val outs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    fun find(vararg types: Int) = outs.firstOrNull { it.type in types }
    val usb = find(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
    if (usb != null && preferUsb) return Device.Usb to usb.productName.toString()
    val bt = find(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
    if (bt != null) return Device.Bluetooth to bt.productName.toString()
    if (usb != null) return Device.Usb to usb.productName.toString()
    val wired = find(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)
    if (wired != null) return Device.Wired to ""
    return Device.Speaker to ""
}

private fun khz(hz: Int): String =
    if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.getDefault(), "%.1f kHz", hz / 1000f)

private fun kbps(bps: Int): String = String.format(Locale.getDefault(), "%,d kb/s", bps / 1000)

@Composable
private fun channels(n: Int) = when (n) {
    1 -> stringResource(R.string.ap_mono)
    2 -> stringResource(R.string.ap_stereo)
    else -> stringResource(R.string.ap_channels, n)
}

@Composable
private fun encodingName(encoding: Int) = when (encoding) {
    AudioFormat.ENCODING_PCM_FLOAT -> stringResource(R.string.ap_pcm_float)
    AudioFormat.ENCODING_PCM_16BIT -> stringResource(R.string.ap_pcm_bits, 16)
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> stringResource(R.string.ap_pcm_bits, 24)
    AudioFormat.ENCODING_PCM_32BIT -> stringResource(R.string.ap_pcm_bits, 32)
    AudioFormat.ENCODING_PCM_8BIT -> stringResource(R.string.ap_pcm_bits, 8)
    else -> stringResource(R.string.ap_compressed)
}

/**
 * "Audio path": the sound's whole journey, live, from the file to the ears, step by step,
 * ending with an honest verdict. Opened from the quality label under the progress bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPathSheet(song: Song, settings: AppSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val input by PlaybackInfo.input.collectAsStateWithLifecycle()
    val output by PlaybackInfo.output.collectAsStateWithLifecycle()
    val decoder by PlaybackInfo.decoder.collectAsStateWithLifecycle()
    val file by produceState<FileFacts?>(null, song.id) { value = readFile(context, song) }
    val mixer = remember {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
    }
    val (device, deviceName) = remember(settings.usbDac) { currentDevice(context, settings.usbDac) }

    val ext = song.path.substringAfterLast('.', "").uppercase()
    val lossless = ext.lowercase() in setOf("flac", "wav", "aiff", "aif", "alac", "ape")
    val sourceRate = input?.sampleRate?.takeIf { it > 0 } ?: file?.rate ?: 0
    val trackRate = output?.sampleRate ?: 0

    // Symphony's own processing that changes the sound.
    val dsp = buildList {
        if (settings.eqEnabled) add(stringResource(R.string.ap_dsp_eq))
        if (settings.eqEnabled && settings.bassBoost > 0) add(stringResource(R.string.ap_dsp_bass))
        if (settings.spatial || (settings.eqEnabled && settings.virtualizer > 0)) add(stringResource(R.string.ap_dsp_spatial))
        val hp = settings.headphone
        if (hp != null && settings.headphoneOn) add(stringResource(R.string.ap_dsp_headphone, hp.name))
    }
    val resampled = sourceRate > 0 && mixer > 0 && sourceRate != mixer && device != Device.Usb
    val bluetooth = device == Device.Bluetooth

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Sheet,
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Text(stringResource(R.string.ap_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.ap_subtitle), color = Dim, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))

            // 1. The file
            Step(Icons.AutoMirrored.Rounded.InsertDriveFile, stringResource(R.string.ap_step_file)) {
                Fact(stringResource(R.string.ap_format), ext.ifBlank { file?.mime ?: "—" } + if (lossless) " · " + stringResource(R.string.quality_lossless) else "")
                val bits = file?.bits?.takeIf { it > 0 }
                if (bits != null || sourceRate > 0) Fact(
                    stringResource(R.string.ap_resolution),
                    listOfNotNull(bits?.let { stringResource(R.string.ap_bits, it) }, sourceRate.takeIf { it > 0 }?.let { khz(it) }).joinToString(" · "),
                )
                val rate = file?.bitrate?.takeIf { it > 0 } ?: input?.bitrate?.takeIf { it > 0 } ?: song.bitrate.takeIf { it > 0 }
                if (rate != null) Fact(stringResource(R.string.ap_bitrate), kbps(rate))
                input?.channels?.takeIf { it > 0 }?.let { Fact(stringResource(R.string.ap_channels_label), channels(it)) }
            }
            // 2. The decoder
            Step(Icons.Rounded.Memory, stringResource(R.string.ap_step_decoder)) {
                Fact(stringResource(R.string.ap_decoder), decoder ?: stringResource(R.string.ap_waiting))
                Fact(stringResource(R.string.ap_decoder_kind), stringResource(if (decoder?.startsWith("c2.android") == true || decoder?.startsWith("OMX.google") == true) R.string.ap_software else R.string.ap_hardware))
            }
            // 3. Symphony's processing
            Step(Icons.Rounded.Tune, stringResource(R.string.ap_step_dsp)) {
                if (dsp.isEmpty()) Fact(stringResource(R.string.ap_dsp_none_label), stringResource(R.string.ap_dsp_none), Good)
                else dsp.forEach { Fact("•", it, Warn) }
                if (settings.automix) Fact(stringResource(R.string.ap_automix_label), stringResource(R.string.ap_automix))
            }
            // 4. What Symphony hands to Android
            Step(Icons.Rounded.GraphicEq, stringResource(R.string.ap_step_out)) {
                val o = output
                if (o == null) Fact(stringResource(R.string.ap_format), stringResource(R.string.ap_waiting))
                else {
                    Fact(stringResource(R.string.ap_format), encodingName(o.encoding) + " · " + khz(o.sampleRate))
                    if (o.offload) Fact(stringResource(R.string.ap_offload_label), stringResource(R.string.ap_offload))
                }
            }
            // 5. Android's mixer
            Step(Icons.Rounded.PhoneAndroid, stringResource(R.string.ap_step_android)) {
                if (device == Device.Usb) {
                    Fact(stringResource(R.string.ap_mixer), stringResource(R.string.ap_usb_direct))
                } else {
                    if (mixer > 0) Fact(stringResource(R.string.ap_mixer), khz(mixer))
                    val from = if (trackRate > 0) trackRate else sourceRate
                    if (from > 0 && mixer > 0) {
                        if (from != mixer) Fact(stringResource(R.string.ap_resample_label), khz(from) + " → " + khz(mixer), Warn)
                        else Fact(stringResource(R.string.ap_resample_label), stringResource(R.string.ap_resample_none), Good)
                    }
                }
            }
            // 6. The device
            val deviceIcon = when (device) {
                Device.Bluetooth -> Icons.Rounded.Bluetooth
                Device.Usb -> Icons.Rounded.Usb
                Device.Wired -> Icons.Rounded.Headphones
                Device.Speaker -> Icons.Rounded.Speaker
            }
            Step(deviceIcon, stringResource(R.string.ap_step_device), last = true) {
                val label = when (device) {
                    Device.Bluetooth -> stringResource(R.string.ap_dev_bt)
                    Device.Usb -> stringResource(R.string.ap_dev_usb)
                    Device.Wired -> stringResource(R.string.ap_dev_wired)
                    Device.Speaker -> stringResource(R.string.ap_dev_speaker)
                }
                Fact(stringResource(R.string.ap_device), if (deviceName.isNotBlank()) "$label · $deviceName" else label)
                if (bluetooth) Fact(stringResource(R.string.ap_bt_label), stringResource(R.string.ap_bt), Warn)
            }

            // Verdict
            val (color, icon, title, detail) = when {
                bluetooth -> Verdict(Note, Icons.Rounded.Bluetooth, stringResource(R.string.ap_v_bt), stringResource(R.string.ap_v_bt_desc))
                dsp.isNotEmpty() -> Verdict(Warn, Icons.Rounded.Tune, stringResource(R.string.ap_v_dsp), stringResource(R.string.ap_v_dsp_desc))
                resampled -> Verdict(Warn, Icons.Rounded.Info, stringResource(R.string.ap_v_resampled, khz(sourceRate), khz(mixer)), stringResource(R.string.ap_v_resampled_desc))
                lossless -> Verdict(Good, Icons.Rounded.CheckCircle, stringResource(R.string.ap_v_faithful), stringResource(R.string.ap_v_faithful_desc))
                else -> Verdict(Good, Icons.Rounded.CheckCircle, stringResource(R.string.ap_v_faithful), stringResource(R.string.ap_v_lossy_desc))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(color.copy(alpha = 0.12f))
                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
                    Spacer(Modifier.height(2.dp))
                    Text(detail, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 18.sp)
                }
            }
        }
    }
}

private data class Verdict(val color: Color, val icon: ImageVector, val title: String, val detail: String)

/** One stage of the path: an icon on a vertical line, a title and its facts. */
@Composable
private fun Step(icon: ImageVector, title: String, last: Boolean = false, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().drawBehind {
            // The line joining this step to the next one.
            if (!last) drawLine(Line, Offset(20.dp.toPx(), 40.dp.toPx()), Offset(20.dp.toPx(), size.height), 2.dp.toPx())
        },
    ) {
        Box(Modifier.width(40.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Dim, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp))
            content()
        }
    }
}

@Composable
private fun Fact(label: String, value: String, tint: Color = Color.White) {
    Row {
        Text("$label  ", fontSize = 15.sp, color = Dim)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}
