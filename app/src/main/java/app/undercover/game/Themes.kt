package app.undercover.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Thèmes visuels et sonores. */
enum class SeasonTheme(val label: String, val emoji: String, val aurora: List<Color>, val accent: Color) {
    CLASSIC("Classique", "🌌", listOf(Color(0x553A1C5A), Color(0x88FF6A2B), Color(0x9928F0F0), Color(0x882950FF)), Color(0xFF2ED3E6)),
    HALLOWEEN("Halloween", "🎃", listOf(Color(0x664A1470), Color(0xAAFF7A00), Color(0x8840C040), Color(0x88601090)), Color(0xFFFF8A1F)),
    CHRISTMAS("Noël", "🎄", listOf(Color(0x55102A50), Color(0x88E02040), Color(0x9930E090), Color(0x88B0D8FF)), Color(0xFF4FE3A0)),
    SUMMER("Été", "☀️", listOf(Color(0x55FF5A8A), Color(0xAAFFC21A), Color(0x9920E0D0), Color(0x882090FF)), Color(0xFFFFC21A));

    companion object {
        /** Thème du moment, d'après la date. */
        fun forToday(c: Calendar = Calendar.getInstance()): SeasonTheme {
            val m = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
            return when {
                (m == 10 && d >= 15) || (m == 11 && d <= 2) -> HALLOWEEN
                m == 12 || (m == 1 && d <= 6) -> CHRISTMAS
                m == 7 || m == 8 -> SUMMER
                else -> CLASSIC
            }
        }
    }
}

/** Choix dans les réglages : automatique (selon la date) ou un thème imposé. */
enum class ThemeChoice(val label: String) { AUTO("Auto"), CLASSIC("Classique"), HALLOWEEN("Halloween"), CHRISTMAS("Noël"), SUMMER("Été");
    fun resolve(): SeasonTheme = when (this) {
        AUTO -> SeasonTheme.forToday()
        CLASSIC -> SeasonTheme.CLASSIC
        HALLOWEEN -> SeasonTheme.HALLOWEEN
        CHRISTMAS -> SeasonTheme.CHRISTMAS
        SUMMER -> SeasonTheme.SUMMER
    }
}

private class Particle(val x: Float, val start: Float, val speed: Float, val size: Float, val phase: Float, val kind: Int)

/** Particules du thème : neige, chauves-souris et citrouilles, bulles et soleils… */
@Composable
internal fun ThemeParticles(theme: SeasonTheme) {
    if (theme == SeasonTheme.CLASSIC) return
    val parts = remember(theme) {
        List(if (theme == SeasonTheme.CHRISTMAS) 55 else 22) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * .5f + .5f, Random.nextFloat() * .6f + .5f, Random.nextFloat() * 6.28f, Random.nextInt(3))
        }
    }
    val tr = rememberInfiniteTransition(label = "particles")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        parts.forEach { p ->
            val prog = (p.start + t * p.speed * 2f) % 1f
            val y = when (theme) { SeasonTheme.SUMMER -> 1f - prog; else -> prog } * (size.height + 80f) - 40f
            val x = size.width * p.x + sin(t * 2 * PI.toFloat() * 3 + p.phase) * 22f * density
            when (theme) {
                SeasonTheme.CHRISTMAS -> snow(x, y, p)
                SeasonTheme.HALLOWEEN -> if (p.kind == 0) pumpkin(x, y, p) else bat(x, y, p, t)
                SeasonTheme.SUMMER -> if (p.kind == 0) sun(x, y, p, t) else bubble(x, y, p)
                else -> {}
            }
        }
    }
}

private fun DrawScope.snow(x: Float, y: Float, p: Particle) {
    drawCircle(Color.White.copy(alpha = .55f), 2.6f * p.size * density, Offset(x, y))
}

private fun DrawScope.pumpkin(x: Float, y: Float, p: Particle) {
    val r = 9f * p.size * density
    val c = Color(0xFFFF8A1F).copy(alpha = .55f)
    drawOval(c, Offset(x - r * 1.3f, y - r), androidx.compose.ui.geometry.Size(r * 2.6f, r * 2f))
    drawRect(Color(0xFF4CAF50).copy(alpha = .6f), Offset(x - r * .15f, y - r * 1.45f), androidx.compose.ui.geometry.Size(r * .3f, r * .5f))
    val eye = Color(0xFF14183A).copy(alpha = .8f)
    drawCircle(eye, r * .22f, Offset(x - r * .45f, y - r * .15f)); drawCircle(eye, r * .22f, Offset(x + r * .45f, y - r * .15f))
    drawRect(eye, Offset(x - r * .5f, y + r * .35f), androidx.compose.ui.geometry.Size(r, r * .18f))
}

private fun DrawScope.bat(x: Float, y: Float, p: Particle, t: Float) {
    val s = 10f * p.size * density
    val flap = sin(t * 2 * PI.toFloat() * 40 + p.phase) * .5f + .5f
    val path = Path().apply {
        moveTo(x, y)
        quadraticTo(x - s, y - s * (0.4f + flap), x - s * 2f, y - s * .2f)
        quadraticTo(x - s * 1.2f, y + s * .1f, x, y + s * .35f)
        quadraticTo(x + s * 1.2f, y + s * .1f, x + s * 2f, y - s * .2f)
        quadraticTo(x + s, y - s * (0.4f + flap), x, y)
    }
    drawPath(path, Color(0xFF1A0A28).copy(alpha = .75f))
}

private fun DrawScope.sun(x: Float, y: Float, p: Particle, t: Float) {
    val r = 7f * p.size * density
    val c = Color(0xFFFFC21A).copy(alpha = .45f)
    rotate(t * 360f, Offset(x, y)) {
        repeat(8) { i ->
            val a = i * PI.toFloat() / 4
            drawLine(c, Offset(x + kotlin.math.cos(a) * r * 1.3f, y + sin(a) * r * 1.3f), Offset(x + kotlin.math.cos(a) * r * 1.9f, y + sin(a) * r * 1.9f), 2f * density)
        }
    }
    drawCircle(c, r, Offset(x, y))
}

private fun DrawScope.bubble(x: Float, y: Float, p: Particle) {
    val r = 8f * p.size * density
    drawCircle(Color(0xFF7FF5FF).copy(alpha = .35f), r, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * density))
    drawCircle(Color.White.copy(alpha = .4f), r * .25f, Offset(x - r * .35f, y - r * .35f))
}
