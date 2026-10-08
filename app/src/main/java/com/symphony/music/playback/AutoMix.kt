package com.symphony.music.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * AutoMix: the next song starts under the end of the current one and the two cross over.
 * A second player carries the incoming song during the fade, then the main player takes over at the
 * same spot. Silence at the end of a song and at the start of the next is skipped, and songs that
 * follow each other on the same album are left untouched.
 */
class AutoMix(
    private val context: Context,
    private val main: ExoPlayer,
    private val fader: ExoPlayer,
    private val scope: CoroutineScope,
    /** Called on the main thread once a mix has handed over to the next song. */
    private val onMixed: (MediaItem) -> Unit = {},
) {
    var enabled = false
    var fadeMs = 6000L

    private class Plan(val key: String) {
        @Volatile var ready = false
        @Volatile var outro = 0L
        @Volatile var intro = 0L
    }

    private var plan: Plan? = null
    private var fading = false
    private var loop: Job? = null

    fun start() {
        loop = scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (e: Exception) {
                    fading = false
                    main.volume = 1f
                }
                delay(200)
            }
        }
    }

    fun stop() {
        loop?.cancel()
    }

    private fun MediaItem.uri(): Uri? = localConfiguration?.uri ?: requestMetadata.mediaUri

    /** Two songs that follow each other on the same album play as the album intends. */
    private fun sameAlbumRun(a: MediaItem, b: MediaItem): Boolean {
        val albumA = a.mediaMetadata.albumTitle?.toString()
        val albumB = b.mediaMetadata.albumTitle?.toString()
        val trackA = a.mediaMetadata.trackNumber ?: return false
        val trackB = b.mediaMetadata.trackNumber ?: return false
        return albumA != null && albumA == albumB && trackB == trackA + 1
    }

    /** Length of the cross: longer, like a DJ, during a DJ mix. */
    private val crossMs get() = if (DjSession.active) 10_000L else fadeMs

    private suspend fun tick() {
        if (!(enabled || DjSession.active) || fading || !main.isPlaying) return
        if (SleepTimer.endOfTrack.value || SleepTimer.endsAt.value > 0L) return
        if (main.repeatMode == Player.REPEAT_MODE_ONE) return
        val next = main.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return
        val duration = main.duration
        if (duration == C.TIME_UNSET || duration < crossMs * 3) return
        val current = main.currentMediaItem ?: return
        val incoming = main.getMediaItemAt(next)
        if (!DjSession.active && sameAlbumRun(current, incoming)) return
        val position = main.currentPosition
        val key = current.mediaId + ">" + incoming.mediaId + "@" + next
        // Look for silences about 40 s ahead, off the main thread.
        if (plan?.key != key && duration - position < 40_000) {
            val fresh = Plan(key)
            fresh.outro = duration
            plan = fresh
            scope.launch {
                withContext(Dispatchers.Default) {
                    current.uri()?.let { fresh.outro = SilenceScanner.outroStart(context, it, duration) }
                    incoming.uri()?.let { fresh.intro = SilenceScanner.introEnd(context, it) }
                }
                fresh.ready = true
            }
        }
        val p = plan?.takeIf { it.key == key } ?: return
        val end = p.outro.coerceIn(crossMs, duration)
        if (position >= end - crossMs) crossfade(next, incoming, if (p.ready) p.intro else 0L, end - position)
    }

    private suspend fun crossfade(nextIndex: Int, item: MediaItem, introMs: Long, remainingMs: Long) {
        fading = true
        val fromIndex = main.currentMediaItemIndex
        val dj = DjSession.active
        var handed = false
        try {
            val fade = remainingMs.coerceIn(1500L, crossMs)
            fader.volume = 0f
            fader.setMediaItem(item, introMs)
            fader.prepare()
            fader.play()
            withTimeoutOrNull(3000) { while (!fader.isPlaying) delay(20) } ?: return
            // Equal-power curves: the sum sounds steady through the cross.
            val steps = (fade / 40).toInt().coerceAtLeast(10)
            for (i in 1..steps) {
                if (!main.playWhenReady || main.currentMediaItemIndex != fromIndex && main.currentMediaItemIndex != nextIndex) return
                val x = i / steps.toFloat()
                if (dj) {
                    // DJ transition: the outgoing song loses its treble and fades late, the incoming
                    // one arrives without bass, then the basses swap halfway.
                    main.volume = if (x < 0.55f) 1f else cos((x - 0.55f) / 0.45f * PI / 2).toFloat()
                    fader.volume = sin(minOf(1f, x / 0.45f) * PI / 2).toFloat()
                    DjFx.highCutMain = (20_000.0 * Math.pow(250.0 / 20_000.0, x.toDouble())).toFloat()
                    DjFx.lowCutFader = if (x < 0.5f) 400f else (400.0 * Math.pow(20.0 / 400.0, ((x - 0.5f) / 0.5f).toDouble())).toFloat()
                } else {
                    main.volume = cos(x * PI / 2).toFloat()
                    fader.volume = sin(x * PI / 2).toFloat()
                }
                delay(40)
            }
            // Hand over: the main player jumps to the incoming song where the second player is.
            main.volume = 0f
            DjFx.highCutMain = 0f
            main.seekTo(nextIndex, fader.currentPosition + 250)
            handed = true
            withTimeoutOrNull(3000) { while (!(main.isPlaying && main.playbackState == Player.STATE_READY)) delay(15) }
            for (i in 1..6) {
                main.volume = i / 6f
                fader.volume = 1f - i / 6f
                delay(25)
            }
        } finally {
            main.volume = 1f
            fader.stop()
            fader.clearMediaItems()
            DjFx.reset()
            fading = false
            plan = null
            if (handed) onMixed(item)
        }
    }
}
