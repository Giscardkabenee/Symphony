package com.symphony.music.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.util.Locale

/** The DJ mix in progress, shared by the app and the playback service. */
object DjSession {
    /** True while a DJ mix plays: AutoMix then mixes like a DJ, whatever its own setting. */
    @Volatile var active = false
    /** The next announcement opens the set. */
    @Volatile var intro = false
    @Volatile var voiceOn = true
    @Volatile var userName = ""
}

/** DJ filters for each player: 0 = the main one, 1 = the one bringing in the next song. */
object DjFx {
    /** High-pass cut-off in Hz (removes the bass below it); 0 = off. */
    @Volatile var lowCutMain = 0f
    @Volatile var lowCutFader = 0f
    /** Low-pass cut-off in Hz (removes the treble above it); 0 = off. */
    @Volatile var highCutMain = 0f
    @Volatile var highCutFader = 0f

    fun lowCut(role: Int) = if (role == 0) lowCutMain else lowCutFader
    fun highCut(role: Int) = if (role == 0) highCutMain else highCutFader

    fun reset() {
        lowCutMain = 0f; lowCutFader = 0f; highCutMain = 0f; highCutFader = 0f
    }
}

/** The DJ's voice: announces songs over the music, which dips while it speaks. */
class DjVoice(context: Context, private val player: Player) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private val french = Locale.getDefault().language == "fr"
    private var count = 0
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            configure()
        }
    }

    private fun configure() {
        tts.language = if (french) Locale.FRENCH else Locale.getDefault()
        tts.setSpeechRate(1.02f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = main.post { duck(true) }.let { }
            override fun onDone(utteranceId: String?) = main.post { duck(false) }.let { }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = main.post { duck(false) }.let { }
        })
    }

    private var ducked = false

    private fun duck(on: Boolean) {
        if (on == ducked) return
        ducked = on
        val from = if (on) 1f else 0.3f
        val to = if (on) 0.3f else 1f
        for (i in 1..8) main.postDelayed({ player.volume = from + (to - from) * i / 8f }, i * 30L)
    }

    /** Says a few words about [item], a little after it starts so any mix has finished. */
    fun announce(item: MediaItem?) {
        if (!ready || item == null || !DjSession.voiceOn) return
        val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return
        val artist = item.mediaMetadata.artist?.toString().orEmpty().takeIf { it.isNotBlank() && it != "—" } ?: ""
        val name = DjSession.userName.trim()
        val text = if (DjSession.intro) {
            DjSession.intro = false
            if (french) "Salut${if (name.isNotEmpty()) " $name" else ""}, ici le DJ Symphony. On ouvre le set avec $title${if (artist.isNotEmpty()) ", de $artist" else ""}."
            else "Hey${if (name.isNotEmpty()) " $name" else ""}, this is DJ Symphony. We open the set with $title${if (artist.isNotEmpty()) " by $artist" else ""}."
        } else {
            count++
            val by = if (artist.isNotEmpty()) artist else if (french) "Symphony" else "Symphony"
            val lines = if (french) listOf(
                "Et maintenant, $title, de $by.",
                "On enchaîne avec $by : $title.",
                "Voici $by, avec $title.",
                "On garde le rythme : $title.",
                if (name.isNotEmpty()) "$name, celle-là est pour toi : $title." else "Celle-là, je l'adore : $title.",
                "Toujours sur Symphony : $by, $title.",
            ) else listOf(
                "And now, $title, by $by.",
                "Next up, $by with $title.",
                "Here's $by, with $title.",
                "Keeping the groove: $title.",
                if (name.isNotEmpty()) "$name, this one's for you: $title." else "I love this one: $title.",
                "Still on Symphony: $by, $title.",
            )
            lines[(count * 7 + title.length) % lines.size]
        }
        main.postDelayed({ tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dj$count") }, 900)
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
    }
}
