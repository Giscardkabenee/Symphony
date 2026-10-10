package com.symphony.music.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** What the audio output is doing right now, for the Settings screen. */
object PlaybackInfo {
    /** Sample rate of the song being decoded, in Hz; 0 before anything has played. */
    val sampleRate = MutableStateFlow(0)

    /** Whether the running player was started with 32-bit float output. */
    val floatOutput = MutableStateFlow(false)

    /** Audio session of the running player, for the phone's own effects panel. */
    val audioSessionId = MutableStateFlow(0)

    /** Name of the decoder reading the current song (for the audio path sheet). */
    val decoder = MutableStateFlow<String?>(null)

    /** What the song looks like when it enters the player. */
    val input = MutableStateFlow<Input?>(null)

    /** How the player hands the sound to Android. */
    val output = MutableStateFlow<Output?>(null)

    data class Input(val mime: String?, val sampleRate: Int, val channels: Int, val bitrate: Int, val pcmEncoding: Int)
    data class Output(val encoding: Int, val sampleRate: Int, val channels: Int, val offload: Boolean)
}
