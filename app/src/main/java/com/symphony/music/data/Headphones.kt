package com.symphony.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** One headphone model in the AutoEq catalogue, with who measured it. */
data class HeadphoneModel(val name: String, val path: String, val source: String)

/** One filter of a parametric equalizer: 0 = peak, 1 = low shelf, 2 = high shelf. */
data class EqFilter(val type: Int, val frequency: Double, val gain: Double, val q: Double)

/** A full correction for one model: overall gain, then the filters. */
data class HeadphoneProfile(val name: String, val source: String, val preamp: Double, val filters: List<EqFilter>) {
    fun encode(): String = listOf(name, source, preamp.toString(), filters.joinToString(";") { "${it.type},${it.frequency},${it.gain},${it.q}" })
        .joinToString("|")

    companion object {
        fun decode(raw: String?): HeadphoneProfile? {
            if (raw.isNullOrBlank()) return null
            return try {
                val parts = raw.split("|")
                val filters = parts[3].split(";").filter { it.isNotBlank() }.map { f ->
                    val v = f.split(",")
                    EqFilter(v[0].toInt(), v[1].toDouble(), v[2].toDouble(), v[3].toDouble())
                }
                HeadphoneProfile(parts[0], parts[1], parts[2].toDouble(), filters)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Corrections computed by the AutoEq project (MIT licence, github.com/jaakkopasanen/AutoEq) from
 * measurements published by oratory1990, crinacle, Rtings and others. The list of models is fetched
 * once and kept; a model's correction is fetched when chosen, then works offline.
 */
object Headphones {
    private const val BASE = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/"
    private val line = Regex("""^- \[(.+)]\(\./(.+)\) by (.+)$""")

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Symphony (https://github.com/Giscardkabenee/Symphony)")
            if (connection.responseCode != 200) throw IOException("HTTP " + connection.responseCode)
            return connection.inputStream.use { it.bufferedReader().readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Every model, from the copy on the phone or, the first time, from the catalogue. */
    suspend fun models(context: Context, refresh: Boolean = false): List<HeadphoneModel> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "autoeq_index.md")
        if (refresh || !file.exists() || file.length() < 1000) {
            val text = get(BASE + "INDEX.md")
            file.writeText(text)
        }
        file.readLines().mapNotNull { row ->
            line.find(row.trim())?.let { m ->
                HeadphoneModel(m.groupValues[1], m.groupValues[2], m.groupValues[3].replace(" on ", " · "))
            }
        }
    }

    /** The parametric correction of one model. */
    suspend fun profile(model: HeadphoneModel): HeadphoneProfile = withContext(Dispatchers.IO) {
        val folder = URLDecoder.decode(model.path.replace("+", "%2B"), "UTF-8")
        val file = folder.substringAfterLast('/') + " ParametricEQ.txt"
        val address = BASE + folder.split('/').joinToString("/") { encode(it) } + "/" + encode(file)
        parse(get(address), model)
    }

    private fun encode(part: String) = URLEncoder.encode(part, "UTF-8").replace("+", "%20")

    private val filterLine = Regex("""Filter\s+\d+:\s+ON\s+(PK|LSC|HSC|LS|HS)\s+Fc\s+([\d.]+)\s+Hz\s+Gain\s+(-?[\d.]+)\s+dB\s+Q\s+([\d.]+)""")
    private val preampLine = Regex("""Preamp:\s+(-?[\d.]+)\s+dB""")

    fun parse(text: String, model: HeadphoneModel): HeadphoneProfile {
        val preamp = preampLine.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val filters = filterLine.findAll(text).map { m ->
            val type = when (m.groupValues[1]) {
                "LSC", "LS" -> 1
                "HSC", "HS" -> 2
                else -> 0
            }
            EqFilter(type, m.groupValues[2].toDouble(), m.groupValues[3].toDouble(), m.groupValues[4].toDouble())
        }.toList()
        if (filters.isEmpty()) throw IOException("no filters")
        return HeadphoneProfile(model.name, model.source, preamp, filters)
    }
}
