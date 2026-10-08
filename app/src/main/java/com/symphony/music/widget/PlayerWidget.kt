package com.symphony.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.symphony.music.MainActivity
import com.symphony.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Home-screen widget: cover, title, artist, previous / play-pause / next. */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action != PREV && action != TOGGLE && action != NEXT) return
        val player = player
        if (player == null || player.mediaItemCount == 0) {
            // Nothing loaded: open the app instead.
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        when (action) {
            PREV -> player.seekToPrevious()
            NEXT -> player.seekToNextMediaItem()
            TOGGLE -> if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    companion object {
        private const val PREV = "com.symphony.music.widget.PREV"
        private const val TOGGLE = "com.symphony.music.widget.TOGGLE"
        private const val NEXT = "com.symphony.music.widget.NEXT"

        /** The playback service's player, set while it runs. */
        var player: Player? = null

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var coverKey: String? = null
        private var cover: Bitmap? = null
        private var coverJob: Job? = null

        /** Redraws every Symphony widget from the player's state; cheap when none is placed. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            if (manager.getAppWidgetIds(ComponentName(app, PlayerWidget::class.java)).isEmpty()) return
            val key = player?.mediaMetadata?.artworkUri?.toString()
            if (key != coverKey) {
                coverKey = key
                cover = null
                coverJob?.cancel()
                if (key != null) {
                    coverJob = scope.launch {
                        val bitmap = withContext(Dispatchers.IO) { loadCover(app, Uri.parse(key)) }
                        if (coverKey == key) {
                            cover = bitmap
                            render(app)
                        }
                    }
                }
            }
            render(app)
        }

        private fun render(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val component = ComponentName(app, PlayerWidget::class.java)
            val views = RemoteViews(app.packageName, R.layout.widget_player)
            val p = player
            val meta = p?.mediaMetadata
            val title = meta?.title?.toString()
            views.setTextViewText(R.id.widget_title, if (title.isNullOrBlank()) app.getString(R.string.app_name) else title)
            views.setTextViewText(R.id.widget_artist, meta?.artist?.toString() ?: app.getString(R.string.widget_idle))
            views.setImageViewResource(R.id.widget_toggle, if (p?.isPlaying == true) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            val art = cover
            if (art != null) views.setImageViewBitmap(R.id.widget_cover, art) else views.setImageViewResource(R.id.widget_cover, android.R.color.transparent)
            views.setOnClickPendingIntent(R.id.widget_prev, action(app, PREV, 1))
            views.setOnClickPendingIntent(R.id.widget_toggle, action(app, TOGGLE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, action(app, NEXT, 3))
            val open = PendingIntent.getActivity(
                app, 4, Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_cover, open)
            views.setOnClickPendingIntent(R.id.widget_title, open)
            views.setOnClickPendingIntent(R.id.widget_artist, open)
            try {
                manager.updateAppWidget(component, views)
            } catch (e: Exception) {
                // The launcher went away; next refresh will try again.
            }
        }

        private fun action(context: Context, name: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            context, code, Intent(context, PlayerWidget::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Small square cover from the phone or the web; null when there is none. */
        private fun loadCover(context: Context, uri: Uri): Bitmap? = try {
            val bytes = if (uri.scheme == "http" || uri.scheme == "https") {
                val connection = URL(uri.toString()).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                try {
                    connection.inputStream.use { it.readBytes() }
                } finally {
                    connection.disconnect()
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            bytes?.let {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(it, 0, it.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 256) sample *= 2
                val decoded = BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply { inSampleSize = sample })
                decoded?.let { b -> Bitmap.createScaledBitmap(b, 256, 256 * b.height / b.width.coerceAtLeast(1), true) }
            }
        } catch (e: Exception) {
            null
        }
    }
}
