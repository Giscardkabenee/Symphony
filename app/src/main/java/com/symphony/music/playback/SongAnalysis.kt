package com.symphony.music.playback

import android.content.Context
import com.symphony.music.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10

/** Tempo in beats per minute and energy from 0 (calm) to 1 (loud and busy). */
data class Groove(val bpm: Float, val energy: Float)

/**
 * Listens to 20 seconds from the middle of each song, once, to estimate its tempo and energy for the
 * DJ mix. Runs quietly in the background and keeps the results on the phone.
 */
object SongAnalysis {
    private val results = ConcurrentHashMap<Long, Groove>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var loaded = false

    /** Bumps each time new songs have been analysed, so lists can reorder. */
    val version = MutableStateFlow(0)

    fun get(id: Long): Groove? = results[id]

    private fun file(context: Context) = File(context.filesDir, "grooves.tsv")

    fun analyse(context: Context, songs: List<Song>) {
        val app = context.applicationContext
        if (job?.isActive == true) return
        job = scope.launch {
            if (!loaded) {
                loaded = true
                try {
                    file(app).takeIf { it.exists() }?.readLines()?.forEach { line ->
                        val p = line.split('\t')
                        if (p.size == 3) results[p[0].toLong()] = Groove(p[1].toFloat(), p[2].toFloat())
                    }
                    version.value++
                } catch (e: Exception) {
                    // Unreadable cache: start again.
                }
            }
            var fresh = 0
            for (song in songs) {
                if (!isActive) break
                if (results.containsKey(song.id) || song.duration < 40_000) continue
                measure(app, song)?.let {
                    results[song.id] = it
                    fresh++
                    if (fresh % 5 == 0) {
                        save(app)
                        version.value++
                    }
                }
            }
            if (fresh > 0) {
                save(app)
                version.value++
            }
        }
    }

    private fun save(context: Context) {
        try {
            file(context).writeText(results.entries.joinToString("\n") { "${it.key}\t${it.value.bpm}\t${it.value.energy}" })
        } catch (e: Exception) {
            // Kept in memory for this session.
        }
    }

    private fun measure(context: Context, song: Song): Groove? = try {
        val levels = ArrayList<Float>(2000)
        val from = (song.duration * 0.35).toLong()
        SilenceScanner.decodeLevels(context, song.uri, from, from + 20_000, 10) { _, _, rms ->
            levels += rms
            levels.size < 2000
        }
        if (levels.size < 400) null else {
            // Energy: average loudness, from about -40 dBFS (0) to -8 dBFS (1).
            val mean = levels.average().coerceAtLeast(1e-6)
            val db = 20 * log10(mean)
            val energy = ((db + 40) / 32).toFloat().coerceIn(0f, 1f)
            // Tempo: the lag at which the rises in loudness repeat best, between 60 and 180 beats per minute.
            val onset = FloatArray(levels.size)
            for (i in 1 until levels.size) onset[i] = maxOf(0f, levels[i] - levels[i - 1])
            val avg = onset.average().toFloat()
            for (i in onset.indices) onset[i] -= avg
            var bestLag = 50
            var best = Double.NEGATIVE_INFINITY
            for (lag in 33..100) {
                var acc = 0.0
                for (i in 0 until onset.size - lag) acc += onset[i] * onset[i + lag]
                if (acc > best) {
                    best = acc
                    bestLag = lag
                }
            }
            var bpm = 6000f / bestLag
            while (bpm < 80f) bpm *= 2
            while (bpm > 165f) bpm /= 2
            Groove(bpm, energy)
        }
    } catch (e: Exception) {
        null
    }
}
