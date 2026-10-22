package app.undercover.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class Thank(val name: String, val emoji: String, val message: String, val color: Color)

/** Page de remerciements. */
@Composable
internal fun ThanksScreen(vm: GameViewModel) {
    val people = listOf(
        Thank("Mélanie", "💖", "Pour son soutien, sa patience et les premières parties de test", Color(0xFFFF7AB6)),
        Thank("Guillaume", "🎲", "Pour ses parties endiablées et ses idées de règles", Color(0xFF5AA9FF)),
        Thank("Natacha", "✨", "Pour ses bluffs redoutables et sa bonne humeur", Color(0xFFB38CFF)),
        Thank("Aurélie", "🌟", "Pour ses retours précieux et ses fous rires", Amber),
        Thank("Les enfants", "🧸", "Nos testeurs officiels, toujours partants pour une partie !", Mint)
    )
    Box(Modifier.fillMaxSize()) {
        FloatingHearts()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            val t = rememberInfiniteTransition(label = "beat")
            val beat by t.animateFloat(1f, 1.12f, infiniteRepeatable(
                keyframes { durationMillis = 1100; 1f at 0; 1.12f at 140; 1f at 300; 1.08f at 440; 1f at 650 }), label = "b")
            Text("❤️", fontSize = 64.sp, modifier = Modifier.graphicsLayer { scaleX = beat; scaleY = beat })
            Text("Merci !", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Teal)
            Text("Ce jeu n'existerait pas sans eux", color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            people.forEachIndexed { i, p -> ThankCard(p, i * 180) ; Spacer(Modifier.height(12.dp)) }
            Spacer(Modifier.height(18.dp))
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(90.dp))
            Wordmark(26)
            Spacer(Modifier.height(6.dp))
            Text("Développé par Mickaël Monestier", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("avec beaucoup d'amour ❤️", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            BigButton("Retour") { vm.back() }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ThankCard(p: Thank, delayMs: Int) {
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); k.animateTo(1f, spring(dampingRatio = .6f, stiffness = 200f)) }
    Row(
        Modifier.fillMaxWidth().graphicsLayer { alpha = k.value.coerceIn(0f, 1f); translationY = (1 - k.value) * 60f }
            .clip(RoundedCornerShape(20.dp)).background(Surf)
            .border(1.5.dp, p.color.copy(alpha = .55f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(p.color), contentAlignment = Alignment.Center) {
            Text(p.emoji, fontSize = 26.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(p.name, fontWeight = FontWeight.Black, fontSize = 19.sp, color = p.color)
            Text(p.message, fontSize = 13.sp, color = White, lineHeight = 18.sp)
        }
    }
}

/** Petits cœurs qui montent doucement en fond. */
@Composable
private fun FloatingHearts() {
    val hearts = remember { List(18) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * .5f + .5f) } }
    val t = rememberInfiniteTransition(label = "hearts")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "p")
    Canvas(Modifier.fillMaxSize()) {
        hearts.forEach { (x, start, s) ->
            val y = ((start - p * s + 1f) % 1f)
            val cx = size.width * x + kotlin.math.sin((p * 6.28f + x * 10f)) * 18f
            val cy = size.height * y
            val r = 7f * s * density
            val c = Color(0xFFFF5A8A).copy(alpha = .18f + .12f * s)
            drawCircle(c, r, androidx.compose.ui.geometry.Offset(cx - r * .55f, cy))
            drawCircle(c, r, androidx.compose.ui.geometry.Offset(cx + r * .55f, cy))
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - r * 1.45f, cy + r * .3f); lineTo(cx, cy + r * 1.9f); lineTo(cx + r * 1.45f, cy + r * .3f); close()
            }
            drawPath(path, c)
        }
    }
}
