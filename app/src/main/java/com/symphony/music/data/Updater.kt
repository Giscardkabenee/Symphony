package com.symphony.music.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** What the update row in Settings shows. */
data class UpdateUi(val status: Int = IDLE, val progress: Int = 0) {
    companion object {
        const val IDLE = 0
        const val CHECKING = 1
        const val UP_TO_DATE = 2
        const val DOWNLOADING = 3
        const val ERROR = 4
    }
}

/** Finds the newest APK published on the project's GitHub page, downloads it and opens the installer. */
object Updater {
    private const val RELEASE_API = "https://api.github.com/repos/Giscardkabenee/Symphony/releases/tags/latest"

    data class Release(val build: Long, val url: String)

    fun installedBuild(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    /** The release title ends with the build number, for example "Symphony build 12". */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val build = Regex("(\\d+)\\s*$").find(json.optString("name"))?.groupValues?.get(1)?.toLongOrNull()
            val assets = json.optJSONArray("assets")
            var url: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk")) url = asset.optString("browser_download_url")
                }
            }
            if (build != null && !url.isNullOrEmpty()) Release(build, url) else null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(context: Context, url: String, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, "Symphony.apk")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
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
        } finally {
            connection.disconnect()
        }
        target
    }

    /** Hands the APK to Android's installer, which asks the user to confirm. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
