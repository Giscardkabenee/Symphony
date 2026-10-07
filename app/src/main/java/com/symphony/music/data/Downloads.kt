package com.symphony.music.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A podcast episode saved on the phone, with what is needed to list and play it offline. */
data class DownloadedEpisode(val podcast: Podcast, val episode: Episode)

/** Saves podcast episodes in the app's own storage so they play without a connection. */
object Downloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Episode id to percentage, for downloads in progress. */
    val progress = MutableStateFlow<Map<Long, Int>>(emptyMap())

    fun file(context: Context, id: Long): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "podcasts")
        dir.mkdirs()
        return File(dir, "${-id}.audio")
    }

    fun start(context: Context, prefs: Prefs, podcast: Podcast, episode: Episode) {
        val app = context.applicationContext
        if (progress.value.containsKey(episode.id)) return
        progress.update { it + (episode.id to 0) }
        scope.launch {
            val target = file(app, episode.id)
            val part = File(target.path + ".part")
            try {
                fetch(episode.url, part) { percent -> progress.update { it + (episode.id to percent) } }
                if (!part.renameTo(target)) throw IOException("rename failed")
                prefs.addDownload(DownloadedEpisode(podcast, episode))
            } catch (e: Exception) {
                part.delete()
            } finally {
                progress.update { it - episode.id }
            }
        }
    }

    fun delete(context: Context, prefs: Prefs, id: Long) {
        val app = context.applicationContext
        scope.launch {
            file(app, id).delete()
            prefs.removeDownload(id)
        }
    }

    private fun fetch(address: String, target: File, onProgress: (Int) -> Unit) {
        var current = address
        repeat(6) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Symphony (https://github.com/Giscardkabenee/Symphony)")
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val next = connection.getHeaderField("Location")
                    if (next.isNullOrBlank()) throw IOException("redirect without target")
                    current = URL(URL(current), next).toString()
                } else if (code == 200) {
                    val total = connection.contentLengthLong
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            var last = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt()
                                    if (percent != last) {
                                        last = percent
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                    }
                    return
                } else {
                    throw IOException("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("too many redirects")
    }
}
