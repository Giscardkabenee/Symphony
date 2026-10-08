package com.symphony.music.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import java.nio.ByteOrder

/** Finds where the music really starts and ends in a file, by decoding its first and last seconds. */
object SilenceScanner {
    /** Peak level under which a 50 ms window counts as silence (about -40 dBFS). */
    private const val THRESHOLD = 0.01f
    private val introCache = LruCache<String, Long>(128)
    private val outroCache = LruCache<String, Long>(128)

    /** Milliseconds of silence at the start, capped at 15 s; 0 when unsure. */
    fun introEnd(context: Context, uri: Uri): Long {
        introCache.get(uri.toString())?.let { return it }
        var found = 0L
        try {
            decode(context, uri, 0L, 15_000L) { time, peak ->
                if (peak > THRESHOLD) {
                    found = time
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            found = 0L
        }
        introCache.put(uri.toString(), found)
        return found
    }

    /** Millisecond after which only silence remains, looking at the last 25 s; [durationMs] when unsure. */
    fun outroStart(context: Context, uri: Uri, durationMs: Long): Long {
        outroCache.get(uri.toString())?.let { return it }
        var last = -1L
        try {
            decode(context, uri, (durationMs - 25_000L).coerceAtLeast(0L), durationMs) { time, peak ->
                if (peak > THRESHOLD) last = time + 50
                true
            }
        } catch (e: Exception) {
            last = -1L
        }
        val result = if (last > 0) last.coerceAtMost(durationMs) else durationMs
        outroCache.put(uri.toString(), result)
        return result
    }

    /** Decodes [fromMs, toMs) and reports the peak of each 50 ms window; stops when [onWindow] returns false. */
    private fun decode(context: Context, uri: Uri, fromMs: Long, toMs: Long, onWindow: (Long, Float) -> Boolean) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            var floatPcm = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = SystemClock.uptimeMillis() + 5000
            while (SystemClock.uptimeMillis() < deadline) {
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(5000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > toMs * 1000) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = decoder.dequeueOutputBuffer(info, 5000)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = decoder.outputFormat
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (out >= 0) {
                    val buffer = decoder.getOutputBuffer(out)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val start = info.presentationTimeUs / 1000
                    var keepGoing = true
                    if (start >= fromMs && info.size > 0) {
                        val perWindow = (rate / 20) * channels
                        var count = 0
                        var peak = 0f
                        var frame = 0L
                        if (floatPcm) {
                            val samples = buffer.asFloatBuffer()
                            while (samples.hasRemaining() && keepGoing) {
                                peak = maxOf(peak, kotlin.math.abs(samples.get()))
                                if (++count == perWindow) {
                                    keepGoing = onWindow(start + frame * 50, peak)
                                    frame++; count = 0; peak = 0f
                                }
                            }
                        } else {
                            val samples = buffer.asShortBuffer()
                            while (samples.hasRemaining() && keepGoing) {
                                peak = maxOf(peak, kotlin.math.abs(samples.get().toInt()) / 32768f)
                                if (++count == perWindow) {
                                    keepGoing = onWindow(start + frame * 50, peak)
                                    frame++; count = 0; peak = 0f
                                }
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(out, false)
                    if (!keepGoing || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
                // Never started.
            }
            codec?.release()
            extractor.release()
        }
    }
}
