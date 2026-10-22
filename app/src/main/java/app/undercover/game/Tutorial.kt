package app.undercover.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class TutoPage(val title: String, val text: String, val visual: @Composable () -> Unit)

/** Tutoriel visuel en quelques pages, montré au premier lancement. */
@Composable
internal fun TutorialScreen(vm: GameViewModel) {
    val pages = remember {
        listOf(
            TutoPage("Un mot secret chacun", "Chaque joueur pioche une carte et découvre un mot. Personne ne montre sa carte !") { VisualWords() },
            TutoPage("3 rôles cachés", "Les Civils ont tous le même mot. L'Undercover a un mot proche… sans savoir qu'il est Undercover. Mr. White n'a aucun mot.") { VisualRoles() },
            TutoPage("Un indice chacun", "À tour de rôle, dis UN mot qui décrit ton mot secret. Ni trop précis, ni trop vague !") { VisualClues() },
            TutoPage("Débattez et votez", "Discutez, puis tout le monde pointe du doigt le joueur le plus suspect. Il est éliminé et son rôle est révélé.") { VisualVote() },
            TutoPage("Qui gagne ?", "Les Civils gagnent s'ils éliminent tous les imposteurs. Les imposteurs gagnent s'il ne reste qu'un Civil. Mr. White éliminé peut gagner en devinant le mot.") { VisualWin() }
        )
    }
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = state.currentPage == pages.lastIndex
    fun done() { vm.finishTutorial() }

    Column(Modifier.fillMaxSize().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(22)
            Spacer(Modifier.weight(1f))
            if (!last) TextButton(::done) { Text("Passer", color = Muted) }
        }
        HorizontalPager(state, Modifier.weight(1f)) { i ->
            val page = pages[i]
            val active = state.currentPage == i
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    // on relance l'animation à chaque arrivée sur la page
                    if (active) key(i) { page.visual() }
                }
                Spacer(Modifier.height(24.dp))
                Text("${i + 1}", color = Amber, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(page.title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Teal, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(page.text, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 23.sp)
            }
        }
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(pages.size) { i ->
                val sel = i == state.currentPage
                Box(Modifier.height(8.dp).width(if (sel) 26.dp else 8.dp).clip(CircleShape).background(if (sel) Teal else Surf2))
            }
        }
        Box(Modifier.padding(horizontal = 24.dp)) {
            BigButton(if (last) "C'est parti !" else "Suivant") {
                if (last) done() else scope.launch { state.animateScrollToPage(state.currentPage + 1) }
            }
        }
    }
}

// ---------------------------------------------------------------- petites illustrations

@Composable
private fun appear(delayMs: Int): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, spring(dampingRatio = .55f, stiffness = 220f)) }
    return a.value
}

@Composable
private fun MiniCard(word: String?, color: Color, delayMs: Int, tilt: Float = 0f, w: Dp = 92.dp) {
    val k = appear(delayMs)
    Box(
        Modifier.width(w).height(w * 1.3f).graphicsLayer { scaleX = k; scaleY = k; alpha = k.coerceIn(0f, 1f); rotationZ = tilt }
            .clip(RoundedCornerShape(16.dp)).background(Color(0xF0E9EEF7))
            .border(3.dp, color, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(word ?: "?", fontWeight = FontWeight.Black, fontSize = if (word == null) 34.sp else 18.sp,
            color = Color(0xFF0A0D1E), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Person(name: String, color: Color, delayMs: Int, size: Dp = 56.dp, crossed: Boolean = false) {
    val k = appear(delayMs)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = k; scaleY = k; alpha = k.coerceIn(0f, 1f) }) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(name.take(1), fontWeight = FontWeight.Black, fontSize = (size.value * .42f).sp, color = Color(0xFF111111))
            }
            if (crossed) Text("✕", fontSize = (size.value * .9f).sp, color = Under, fontWeight = FontWeight.Black)
        }
        Text(name, fontSize = 12.sp, color = White)
    }
}

@Composable
private fun VisualWords() {
    Row(horizontalArrangement = Arrangement.spacedBy((-14).dp), verticalAlignment = Alignment.CenterVertically) {
        MiniCard("Café", Civil, 0, -10f)
        MiniCard("Café", Civil, 250, 0f)
        MiniCard("Thé", Under, 500, 10f)
    }
}

@Composable
private fun VisualRoles() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        listOf(
            Triple("Civil", Civil, "Café"), Triple("Undercover", Under, "Thé"), Triple("Mr. White", White, null)
        ).forEachIndexed { i, (label, c, w) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MiniCard(w, c, i * 300, w = 82.dp)
                Spacer(Modifier.height(8.dp))
                Text(label, color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Bubble(text: String, delayMs: Int) {
    val k = appear(delayMs)
    Box(
        Modifier.graphicsLayer { scaleX = k; scaleY = k; alpha = k.coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = Color(0xFF0A0D1E), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
private fun VisualClues() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
        listOf("Léa" to "Chaud", "Tom" to "Tasse", "Max" to "Matin").forEachIndexed { i, (n, clue) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Bubble("« $clue »", 400 + i * 600)
                Spacer(Modifier.height(10.dp))
                Person(n, listOf(Amber, Color(0xFF5AA9FF), Color(0xFFFF7AB6))[i], i * 150)
            }
        }
    }
}

@Composable
private fun VisualVote() {
    val t = rememberInfiniteTransition(label = "hand")
    val dx by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "dx")
    var out by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1500); out = true }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Person("Léa", Amber, 0)
            Person("Tom", Color(0xFF5AA9FF), 150, crossed = out)
            Person("Max", Color(0xFFFF7AB6), 300)
        }
        Spacer(Modifier.height(18.dp))
        Text("👆", fontSize = 44.sp, modifier = Modifier.graphicsLayer { translationY = dx * density })
        if (out) {
            val k = appear(0)
            Text("Tom était… UNDERCOVER !", color = Under, fontWeight = FontWeight.Black, fontSize = 17.sp,
                modifier = Modifier.graphicsLayer { scaleX = k; scaleY = k })
        }
    }
}

@Composable
private fun VisualWin() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WinRow("🛡️", "Civils", "plus aucun imposteur", Civil, 0)
        WinRow("🕵️", "Imposteurs", "il ne reste qu'un Civil", Under, 350)
        WinRow("❔", "Mr. White", "devine le mot des Civils", White, 700)
    }
}

@Composable
private fun WinRow(emoji: String, who: String, cond: String, c: Color, delayMs: Int) {
    val k = appear(delayMs)
    Row(
        Modifier.width(290.dp).graphicsLayer { translationX = (1 - k) * 120f; alpha = k.coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(16.dp)).background(Surf).border(1.5.dp, c, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 28.sp); Spacer(Modifier.width(12.dp))
        Column {
            Text("$who gagnent", color = c, fontWeight = FontWeight.Black)
            Text("si $cond", fontSize = 13.sp, color = White)
        }
    }
}
