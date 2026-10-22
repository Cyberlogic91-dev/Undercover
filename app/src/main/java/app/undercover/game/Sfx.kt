package app.undercover.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Sons générés à la volée (aucun fichier audio embarqué) et vibrations. */
class Sfx(private val ctx: Context) {
    enum class Sound { FLIP, TAP, ELIMINATE, WIN, ALARM, DRUM, FAIL, CAUGHT, WIN_XMAS, WIN_HALLOWEEN, WIN_SUMMER }

    var soundOn = true
    var vibrationOn = true

    private val pool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    private val ids = mutableMapOf<Sound, Int>()
    private val rate = 22050

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    /** À appeler hors du thread principal. */
    fun load() {
        val dir = File(ctx.cacheDir, "sfx").apply { mkdirs() }
        Sound.entries.forEach { s ->
            val f = File(dir, "${s.name.lowercase()}.wav")
            runCatching {
                writeWav(f, synth(s))
                ids[s] = pool.load(f.path, 1)
            }
        }
    }

    fun play(s: Sound) {
        if (!soundOn) return
        ids[s]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun vibrate(pattern: LongArray) {
        if (!vibrationOn) return
        val v = vibrator ?: return
        runCatching {
            if (pattern.size == 1) v.vibrate(VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(VibrationEffect.createWaveform(longArrayOf(0) + pattern, -1))
        }
    }

    fun release() = pool.release()

    // ------------------------------------------------------------ synthèse

    private fun synth(s: Sound): ShortArray = when (s) {
        Sound.FLIP -> noise(0.16, 0.035)
        Sound.TAP -> tone(listOf(1320.0 to 0.045), 0.010)
        Sound.ELIMINATE -> sweep(620.0, 140.0, 0.5)
        Sound.WIN -> tone(listOf(523.25 to .11, 659.25 to .11, 783.99 to .11, 1046.5 to .32), 0.06)
        Sound.ALARM -> tone(listOf(880.0 to .16, 0.0 to .08, 880.0 to .16, 0.0 to .08, 880.0 to .35), 0.01)
        Sound.DRUM -> drumRoll(2.6)
        // Petit air de clochettes (mi mi mi, mi mi mi, mi sol do ré mi)
        Sound.WIN_XMAS -> bells(listOf(659.25 to .16, 659.25 to .16, 659.25 to .3, 659.25 to .16, 659.25 to .16, 659.25 to .3,
            659.25 to .16, 783.99 to .16, 523.25 to .2, 587.33 to .1, 659.25 to .5))
        // Ambiance lugubre : notes mineures avec trémolo
        Sound.WIN_HALLOWEEN -> spooky(listOf(440.0 to .22, 415.3 to .22, 392.0 to .22, 349.23 to .22, 329.63 to .7))
        // Arpège ensoleillé façon steel drum
        Sound.WIN_SUMMER -> bells(listOf(523.25 to .12, 659.25 to .12, 783.99 to .12, 1046.5 to .12, 783.99 to .12, 1046.5 to .45))
        Sound.FAIL -> tone(listOf(392.0 to .22, 369.99 to .22, 349.23 to .22, 329.63 to .6), 0.12)
        Sound.CAUGHT -> tone(listOf(659.25 to .09, 783.99 to .09, 987.77 to .09, 1318.5 to .4), 0.08)
    }

    private fun tone(notes: List<Pair<Double, Double>>, release: Double): ShortArray {
        val out = ArrayList<Short>()
        notes.forEach { (freq, dur) ->
            val n = (dur * rate).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val env = minOf(1.0, t / 0.005) * minOf(1.0, (dur - t) / release.coerceAtLeast(0.005))
                val v = if (freq == 0.0) 0.0 else
                    (sin(2 * PI * freq * t) + 0.3 * sin(4 * PI * freq * t)) / 1.3
                out += (v * env * 0.6 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }

    private fun sweep(f0: Double, f1: Double, dur: Double): ShortArray {
        val n = (dur * rate).toInt()
        var phase = 0.0
        return ShortArray(n) { i ->
            val k = i.toDouble() / n
            val f = f0 + (f1 - f0) * k
            phase += 2 * PI * f / rate
            val env = minOf(1.0, k * 40) * (1 - k)
            (sin(phase) * env * 0.6 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun noise(dur: Double, decay: Double): ShortArray {
        val n = (dur * rate).toInt()
        var last = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val white = Random.nextDouble(-1.0, 1.0)
            last = last * 0.75 + white * 0.25 // filtre passe-bas : effet « souffle »
            val env = minOf(1.0, t / 0.01) * exp(-t / decay)
            (last * env * 1.6 * Short.MAX_VALUE).coerceIn(-32000.0, 32000.0).toInt().toShort()
        }
    }

    /** Notes cristallines qui résonnent (clochettes / steel drum). */
    private fun bells(notes: List<Pair<Double, Double>>): ShortArray {
        val out = ArrayList<Short>()
        notes.forEach { (f, dur) ->
            val n = (dur * rate).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val env = minOf(1.0, t / 0.004) * exp(-t / 0.22)
                val v = sin(2 * PI * f * t) * .6 + sin(2 * PI * f * 2.76 * t) * .25 + sin(2 * PI * f * 5.4 * t) * .1
                out += (v * env * 0.6 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }

    /** Notes graves avec trémolo, pour Halloween. */
    private fun spooky(notes: List<Pair<Double, Double>>): ShortArray {
        val out = ArrayList<Short>()
        notes.forEach { (f, dur) ->
            val n = (dur * rate).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val vib = 1 + 0.012 * sin(2 * PI * 6 * t)
                val trem = 0.75 + 0.25 * sin(2 * PI * 9 * t)
                val env = minOf(1.0, t / 0.03) * minOf(1.0, (dur - t) / 0.05)
                val v = sin(2 * PI * f * vib * t) * .7 + sin(2 * PI * f * 0.5 * t) * .3
                out += (v * env * trem * 0.6 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }

    /** Roulement de caisse claire qui s'accélère et monte en volume. */
    private fun drumRoll(dur: Double): ShortArray {
        val n = (dur * rate).toInt()
        val out = DoubleArray(n)
        var t = 0.0
        while (t < dur) {
            val k = t / dur
            val gap = 0.085 - 0.06 * k                 // les coups se rapprochent
            val vol = 0.25 + 0.75 * k                  // crescendo
            val start = (t * rate).toInt()
            val len = (0.05 * rate).toInt()
            var last = 0.0
            for (i in 0 until len) {
                val j = start + i; if (j >= n) break
                val env = exp(-i.toDouble() / rate / 0.012)
                last = last * 0.4 + Random.nextDouble(-1.0, 1.0) * 0.6
                val body = sin(2 * PI * 190.0 * i / rate) * 0.5
                out[j] += (last + body) * env * vol
            }
            t += gap
        }
        return ShortArray(n) { (out[it].coerceIn(-1.0, 1.0) * 0.7 * Short.MAX_VALUE).toInt().toShort() }
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size * 2)
        }
        FileOutputStream(f).use { it.write(h.array()); it.write(data.array()) }
    }
}
