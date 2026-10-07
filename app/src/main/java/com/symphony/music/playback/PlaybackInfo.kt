package com.symphony.music.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** What the audio output is doing right now, for the Settings screen. */
object PlaybackInfo {
    /** Sample rate of the song being decoded, in Hz; 0 before anything has played. */
    val sampleRate = MutableStateFlow(0)

    /** Whether the running player was started with 32-bit float output. */
    val floatOutput = MutableStateFlow(false)
}
