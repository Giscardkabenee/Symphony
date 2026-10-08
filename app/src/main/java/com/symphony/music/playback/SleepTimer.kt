package com.symphony.music.playback

import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Stops the music after a delay, fading it out, or at the end of the current song. */
object SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private const val FADE_MS = 10_000L

    /** Set by the playback service while it runs. */
    internal var player: ExoPlayer? = null

    /** When the music stops, on the elapsedRealtime clock; 0 when no delay is set. */
    val endsAt = MutableStateFlow(0L)

    /** True while the music is set to stop at the end of the current song. */
    val endOfTrack = MutableStateFlow(false)

    fun start(minutes: Int) {
        cancel()
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L
        endsAt.value = end
        job = scope.launch {
            delay((end - SystemClock.elapsedRealtime() - FADE_MS).coerceAtLeast(0))
            // Lower the volume gently over the last ten seconds.
            val steps = 20
            for (i in steps downTo 1) {
                player?.volume = i / steps.toFloat()
                delay(FADE_MS / steps)
            }
            player?.pause()
            player?.volume = 1f
            endsAt.value = 0L
            job = null
        }
    }

    fun stopAtEndOfTrack() {
        cancel()
        endOfTrack.value = true
        player?.pauseAtEndOfMediaItems = true
    }

    fun cancel() {
        job?.cancel()
        job = null
        player?.volume = 1f
        endsAt.value = 0L
        if (endOfTrack.value) {
            player?.pauseAtEndOfMediaItems = false
            endOfTrack.value = false
        }
    }

    /** Called by the service when the player paused itself at the end of a song. */
    internal fun onTrackEnded() {
        if (endOfTrack.value) {
            player?.pauseAtEndOfMediaItems = false
            endOfTrack.value = false
        }
    }
}
