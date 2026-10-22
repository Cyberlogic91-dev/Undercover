package app.undercover.game

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Narrateur vocal : utilise la synthèse vocale intégrée d'Android (hors ligne si la voix française est installée). */
class Narrator(ctx: Context) : TextToSpeech.OnInitListener {
    var enabled = false
    var ready = false; private set
    private val tts = TextToSpeech(ctx.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val r = tts.setLanguage(Locale.FRANCE)
        ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setSpeechRate(1.0f)
        tts.setPitch(0.95f)
    }

    /** Lit [text] ; [queue] = true pour l'enchaîner après la phrase en cours. */
    fun say(text: String, queue: Boolean = false) {
        if (!enabled || !ready) return
        tts.speak(text, if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun stop() { if (ready) tts.stop() }
    fun release() { tts.stop(); tts.shutdown() }
}
