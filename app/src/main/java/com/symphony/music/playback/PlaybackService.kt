package com.symphony.music.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.symphony.music.MainActivity
import com.symphony.music.data.AppSettings
import com.symphony.music.data.Prefs
import com.symphony.music.widget.PlayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch

/** Background playback, media notification, lock screen and headset controls. */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var stopOnClose = false
    private var exo: ExoPlayer? = null
    private var preferUsb = false
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            applyOutputDevice()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            applyOutputDevice()
        }
    }
    private var virtualizer: Virtualizer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var appliedEffects: List<Int>? = null
    private var fader: ExoPlayer? = null
    private var automix: AutoMix? = null
    private var djVoice: DjVoice? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        // The sample format is fixed when the player is built, so it is read once at start.
        val initial = runBlocking { Prefs(this@PlaybackService).flow.first() }
        PlaybackInfo.floatOutput.value = initial.floatOutput
        val renderers = SymphonyRenderers(this, 0).setEnableAudioFloatOutput(initial.floatOutput)
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo = player
        SleepTimer.player = player
        PlayerWidget.player = player
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                // Keep the home-screen widget in step with the player.
                PlayerWidget.refresh(this@PlaybackService)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // The opening words of a DJ set, once the first song has started.
                if (isPlaying && DjSession.active && DjSession.intro) djVoice?.announce(player.currentMediaItem)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // A skip during a DJ set gets its own announcement (mixes announce from AutoMix).
                if (DjSession.active && !DjSession.intro && reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && player.volume > 0.5f) {
                    djVoice?.announce(mediaItem)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    SleepTimer.onTrackEnded()
                }
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                PlaybackInfo.sampleRate.value = format.sampleRate
                PlaybackInfo.input.value = PlaybackInfo.Input(
                    mime = format.sampleMimeType,
                    sampleRate = format.sampleRate,
                    channels = format.channelCount,
                    bitrate = if (format.averageBitrate > 0) format.averageBitrate else format.bitrate,
                    pcmEncoding = format.pcmEncoding,
                )
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                PlaybackInfo.decoder.value = decoderName
            }

            override fun onAudioTrackInitialized(
                eventTime: AnalyticsListener.EventTime,
                audioTrackConfig: androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig,
            ) {
                PlaybackInfo.output.value = PlaybackInfo.Output(
                    encoding = audioTrackConfig.encoding,
                    sampleRate = audioTrackConfig.sampleRate,
                    channels = Integer.bitCount(audioTrackConfig.channelConfig),
                    offload = audioTrackConfig.offload,
                )
            }
        })
        (getSystemService(AUDIO_SERVICE) as AudioManager).registerAudioDeviceCallback(deviceCallback, null)
        // Stereo widening effect; not every phone provides it.
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val audioSessionId = audioManager.generateAudioSessionId()
        player.audioSessionId = audioSessionId
        virtualizer = try {
            Virtualizer(0, audioSessionId).apply { if (strengthSupported) setStrength(1000) }
        } catch (e: Exception) {
            null
        }
        PlaybackInfo.audioSessionId.value = audioSessionId
        // Second player for AutoMix: same sound path and effects, never takes audio focus.
        val second = ExoPlayer.Builder(this, SymphonyRenderers(this, 1).setEnableAudioFloatOutput(initial.floatOutput))
            .setAudioAttributes(attributes, false)
            .build()
        second.audioSessionId = audioSessionId
        fader = second
        djVoice = DjVoice(this, player)
        automix = AutoMix(this, player, second, scope) { item -> djVoice?.announce(item) }.also { it.start() }
        equalizer = try { Equalizer(0, audioSessionId) } catch (e: Exception) { null }
        bassBoost = try { BassBoost(0, audioSessionId) } catch (e: Exception) { null }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // The Symphony bars in the media controls instead of the default play icon.
        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(com.symphony.music.R.drawable.ic_notification)
            }
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
        scope.launch {
            Prefs(this@PlaybackService).flow.collect {
                player.skipSilenceEnabled = it.skipSilence
                stopOnClose = it.stopOnClose
                preferUsb = it.usbDac
                applyOutputDevice()
                applyEffects(it)
                automix?.enabled = it.automix
                HeadphoneEq.set(it.headphone, it.headphoneOn)
                DjSession.voiceOn = it.djVoice
                DjSession.userName = it.userName
                automix?.fadeMs = it.automixSeconds * 1000L
            }
        }
    }

    /** Equalizer, bass boost and stereo widening, re-applied only when one of them changed. */
    private fun applyEffects(settings: AppSettings) {
        val wanted = listOf(if (settings.eqEnabled) 1 else 0, if (settings.spatial) 1 else 0, settings.bassBoost, settings.virtualizer) + settings.eqLevels
        if (wanted == appliedEffects) return
        appliedEffects = wanted
        try {
            equalizer?.let { eq ->
                val range = eq.bandLevelRange
                val centers = listOf(60, 230, 910, 3600, 14000)
                for (band in 0 until eq.numberOfBands.toInt()) {
                    // Each band of the phone follows the nearest of the five bands on screen.
                    val hz = eq.getCenterFreq(band.toShort()) / 1000
                    val nearest = centers.indices.minByOrNull { kotlin.math.abs(kotlin.math.ln(centers[it].toDouble()) - kotlin.math.ln(hz.coerceAtLeast(1).toDouble())) } ?: 0
                    val level = (settings.eqLevels.getOrElse(nearest) { 0 } * 100).coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(band.toShort(), level.toShort())
                }
                eq.enabled = settings.eqEnabled
            }
        } catch (e: Exception) {
            // Equalizer unavailable on this device.
        }
        try {
            bassBoost?.let { bass ->
                if (bass.strengthSupported) bass.setStrength(settings.bassBoost.toShort())
                bass.enabled = settings.eqEnabled && settings.bassBoost > 0
            }
        } catch (e: Exception) {
            // Effect unavailable on this device.
        }
        try {
            virtualizer?.let { wide ->
                val slider = settings.eqEnabled && settings.virtualizer > 0
                if (wide.strengthSupported) wide.setStrength((if (slider) settings.virtualizer else 1000).toShort())
                wide.enabled = settings.spatial || slider
            }
        } catch (e: Exception) {
            // Effect unavailable on this device.
        }
    }

    /** Sends sound to a connected USB DAC or USB headset when the user asked for it. */
    private fun applyOutputDevice() {
        val player = exo ?: return
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        val usb = if (preferUsb) {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        } else {
            null
        }
        try {
            fader?.setPreferredAudioDevice(usb)
            player.setPreferredAudioDevice(usb)
        } catch (e: Exception) {
            // Routing stays with the system.
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (stopOnClose) {
            player?.stop()
            stopSelf()
            return
        }
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(deviceCallback)
        exo = null
        automix?.stop()
        automix = null
        djVoice?.release()
        djVoice = null
        fader?.release()
        fader = null
        SleepTimer.cancel()
        SleepTimer.player = null
        PlayerWidget.player = null
        PlayerWidget.refresh(this)
        try {
            virtualizer?.release()
        } catch (e: Exception) {
            // Already released.
        }
        virtualizer = null
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            // Already released.
        }
        equalizer = null
        bassBoost = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Items sent by the app carry only an id: the playable address is rebuilt here. */
    private class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val id = item.mediaId.toLongOrNull()
                when {
                    // Radio streams carry their own address.
                    id == null || id < 0 -> item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
                    else -> item.buildUpon()
                        .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                        .build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}

/** Standard renderers, with the headphone correction added to the audio path. */
private class SymphonyRenderers(context: android.content.Context, private val role: Int) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: android.content.Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): androidx.media3.exoplayer.audio.AudioSink? =
        androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<androidx.media3.common.audio.AudioProcessor>(ParametricEqProcessor(role)))
            .build()
}
