package com.symphony.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.symphony.music.data.HeadphoneProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The headphone correction currently asked for; read by the audio processors of both players. */
object HeadphoneEq {
    @Volatile var profile: HeadphoneProfile? = null
        private set
    @Volatile var enabled: Boolean = false
        private set
    val version = AtomicInteger(0)

    fun set(profile: HeadphoneProfile?, enabled: Boolean) {
        if (profile == this.profile && enabled == this.enabled) return
        this.profile = profile
        this.enabled = enabled
        version.incrementAndGet()
    }
}

/**
 * Parametric equalizer inside the player: peak and shelf filters (RBJ biquads) computed for the
 * actual sample rate, applied to 16-bit or float PCM, with the profile's preamp so nothing clips.
 */
class ParametricEqProcessor(private val role: Int = 0) : BaseAudioProcessor() {
    private var channels = 2
    private var rate = 48000
    private var float = false
    private var seen = -1
    private var gain = 1f
    // Per filter: b0 b1 b2 a1 a2; per filter and channel: x1 x2 y1 y2.
    private var coef = DoubleArray(0)
    private var state = DoubleArray(0)
    private var active = false
    // DJ filters: high-pass then low-pass (b0 b1 b2 a1 a2 each), and their state per channel.
    private var djLow = 0f
    private var djHigh = 0f
    private val djCoef = DoubleArray(10)
    private var djState = DoubleArray(0)

    /** Picks up the DJ cut-offs; true when at least one filter is on. */
    private fun djUpdate(): Boolean {
        val low = DjFx.lowCut(role)
        val high = DjFx.highCut(role)
        if (djState.size != channels * 8) djState = DoubleArray(channels * 8)
        if (low != djLow || high != djHigh) {
            if ((djLow == 0f && low > 0f) || (djHigh == 0f && high > 0f)) java.util.Arrays.fill(djState, 0.0)
            djLow = low
            djHigh = high
            fun set(offset: Int, f: Float, highPass: Boolean) {
                val fc = f.toDouble().coerceIn(10.0, rate / 2.2)
                val w0 = 2 * PI * fc / rate
                val alpha = sin(w0) / (2 * 0.707)
                val cw = cos(w0)
                val a0 = 1 + alpha
                val b0 = if (highPass) (1 + cw) / 2 else (1 - cw) / 2
                val b1 = if (highPass) -(1 + cw) else 1 - cw
                djCoef[offset] = b0 / a0; djCoef[offset + 1] = b1 / a0; djCoef[offset + 2] = b0 / a0
                djCoef[offset + 3] = -2 * cw / a0; djCoef[offset + 4] = (1 - alpha) / a0
            }
            if (low > 0f) set(0, low, true)
            if (high > 0f) set(5, high, false)
        }
        return djLow > 0f || djHigh > 0f
    }

    private fun djFilter(sample: Float, channel: Int): Float {
        var x = sample.toDouble()
        for (k in 0..1) {
            if ((k == 0 && djLow <= 0f) || (k == 1 && djHigh <= 0f)) continue
            val c = k * 5
            val s = channel * 8 + k * 4
            val y = djCoef[c] * x + djCoef[c + 1] * djState[s] + djCoef[c + 2] * djState[s + 1] - djCoef[c + 3] * djState[s + 2] - djCoef[c + 4] * djState[s + 3]
            djState[s + 1] = djState[s]; djState[s] = x
            djState[s + 3] = djState[s + 2]; djState[s + 2] = y
            x = y
        }
        return x.toFloat().coerceIn(-1f, 1f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        seen = -1
        return inputAudioFormat
    }

    private fun rebuild() {
        seen = HeadphoneEq.version.get()
        val profile = HeadphoneEq.profile
        active = HeadphoneEq.enabled && profile != null && profile.filters.isNotEmpty()
        if (!active || profile == null) return
        gain = 10.0.pow(profile.preamp / 20).toFloat()
        val list = profile.filters.filter { it.frequency > 0 && it.frequency < rate / 2.0 && it.q > 0 }
        coef = DoubleArray(list.size * 5)
        state = DoubleArray(list.size * channels * 4)
        list.forEachIndexed { i, f ->
            val a = 10.0.pow(f.gain / 40)
            val w0 = 2 * PI * f.frequency / rate
            val alpha = sin(w0) / (2 * f.q)
            val cw = cos(w0)
            var b0: Double; var b1: Double; var b2: Double; var a0: Double; var a1: Double; var a2: Double
            when (f.type) {
                1 -> { // low shelf
                    val s = 2 * sqrt(a) * alpha
                    b0 = a * ((a + 1) - (a - 1) * cw + s); b1 = 2 * a * ((a - 1) - (a + 1) * cw); b2 = a * ((a + 1) - (a - 1) * cw - s)
                    a0 = (a + 1) + (a - 1) * cw + s; a1 = -2 * ((a - 1) + (a + 1) * cw); a2 = (a + 1) + (a - 1) * cw - s
                }
                2 -> { // high shelf
                    val s = 2 * sqrt(a) * alpha
                    b0 = a * ((a + 1) + (a - 1) * cw + s); b1 = -2 * a * ((a - 1) + (a + 1) * cw); b2 = a * ((a + 1) + (a - 1) * cw - s)
                    a0 = (a + 1) - (a - 1) * cw + s; a1 = 2 * ((a - 1) - (a + 1) * cw); a2 = (a + 1) - (a - 1) * cw - s
                }
                else -> { // peak
                    b0 = 1 + alpha * a; b1 = -2 * cw; b2 = 1 - alpha * a
                    a0 = 1 + alpha / a; a1 = -2 * cw; a2 = 1 - alpha / a
                }
            }
            coef[i * 5] = b0 / a0; coef[i * 5 + 1] = b1 / a0; coef[i * 5 + 2] = b2 / a0
            coef[i * 5 + 3] = a1 / a0; coef[i * 5 + 4] = a2 / a0
        }
    }

    private fun filter(sample: Float, channel: Int): Float {
        var x = (sample * gain).toDouble()
        val n = coef.size / 5
        for (i in 0 until n) {
            val c = i * 5
            val s = (i * channels + channel) * 4
            val y = coef[c] * x + coef[c + 1] * state[s] + coef[c + 2] * state[s + 1] - coef[c + 3] * state[s + 2] - coef[c + 4] * state[s + 3]
            state[s + 1] = state[s]; state[s] = x
            state[s + 3] = state[s + 2]; state[s + 2] = y
            x = y
        }
        return x.toFloat().coerceIn(-1f, 1f)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (seen != HeadphoneEq.version.get()) rebuild()
        val dj = djUpdate()
        val out = replaceOutputBuffer(size)
        if (!active && !dj) {
            out.put(inputBuffer)
        } else if (float) {
            val input = inputBuffer.order(ByteOrder.nativeOrder())
            var ch = 0
            while (input.remaining() >= 4) {
                var v = input.getFloat()
                if (active) v = filter(v, ch)
                if (dj) v = djFilter(v, ch)
                out.putFloat(v)
                ch = (ch + 1) % channels
            }
        } else {
            val input = inputBuffer.order(ByteOrder.nativeOrder())
            var ch = 0
            while (input.remaining() >= 2) {
                var v = input.getShort() / 32768f
                if (active) v = filter(v, ch)
                if (dj) v = djFilter(v, ch)
                out.putShort((v * 32767f).toInt().toShort())
                ch = (ch + 1) % channels
            }
        }
        out.flip()
    }

    override fun onFlush() {
        java.util.Arrays.fill(state, 0.0)
        java.util.Arrays.fill(djState, 0.0)
    }

    override fun onReset() {
        coef = DoubleArray(0)
        state = DoubleArray(0)
        seen = -1
    }
}
