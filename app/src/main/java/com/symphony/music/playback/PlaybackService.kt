package com.symphony.music.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import com.symphony.music.data.Prefs
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
        val renderers = DefaultRenderersFactory(this).setEnableAudioFloatOutput(initial.floatOutput)
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo = player
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                PlaybackInfo.sampleRate.value = format.sampleRate
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
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
                try {
                    virtualizer?.enabled = it.spatial
                } catch (e: Exception) {
                    // Effect unavailable on this device.
                }
            }
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
        try {
            virtualizer?.release()
        } catch (e: Exception) {
            // Already released.
        }
        virtualizer = null
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
                if (id == null) item else item.buildUpon()
                    .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                    .build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
