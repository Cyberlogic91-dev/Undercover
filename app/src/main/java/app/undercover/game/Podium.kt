package app.undercover.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Gold = Color(0xFFFFC21A)
private val Silver = Color(0xFFC9D2E3)
private val Bronze = Color(0xFFE0904A)

// ================================================================ Podium de la soirée

@Composable
internal fun PodiumScreen(vm: GameViewModel) {
    val ctx = LocalContext.current
    val rank = vm.eveningRanking()
    var confirmNew by remember { mutableStateOf(false) }
    // révélation : 3e, puis 2e, puis 1er
    val rise = remember { List(3) { Animatable(0f) } }
    var showConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        for (i in listOf(2, 1, 0)) {
            if (rank.getOrNull(i) == null) continue
            delay(450)
            launch { rise[i].animateTo(1f, spring(dampingRatio = .6f, stiffness = 140f)) }
            vm.sfx.play(if (i == 0) Sfx.Sound.CAUGHT else Sfx.Sound.TAP)
        }
        delay(500); showConfetti = true
        rank.firstOrNull()?.let { vm.narrator.say("Et le grand vainqueur de la soirée est… ${it.first}, avec ${it.second} points !", queue = true) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text("🏆", fontSize = 52.sp)
            Text("Championnat de la soirée", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Gold, textAlign = TextAlign.Center)
            Text("${vm.eveningGames} partie(s) jouée(s)", color = Muted)
            Spacer(Modifier.height(28.dp))

            if (rank.isEmpty()) {
                Text("Aucune partie terminée ce soir pour l'instant.", color = Muted, modifier = Modifier.padding(32.dp))
            } else {
                Row(Modifier.fillMaxWidth().height(340.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 0, 2).forEach { i ->
                        Box(Modifier.weight(1f)) {
                            rank.getOrNull(i)?.let { e -> PodiumStep(vm, i, e, rise[i].value) }
                        }
                    }
                }
                val rest = rank.drop(3)
                if (rest.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    rest.forEachIndexed { k, e ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Surf).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${k + 4}.", color = Muted, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp))
                            Avatar(vm.avatarOfName(e.first), e.first, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(e.first, Modifier.weight(1f))
                            Text("${e.second} pts", color = Amber, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (rank.isNotEmpty()) BigButton("📤  Partager le podium") {
                ShareImage.share(ctx, ShareImage.podium(ctx, vm), "podium", "🏆 Le podium de notre soirée Undercover !")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton({ confirmNew = true }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("🌙  Nouvelle soirée (remettre à zéro)")
            }
            TextButton({ vm.back() }, Modifier.fillMaxWidth()) { Text("Retour", color = Muted) }
            Spacer(Modifier.height(16.dp))
        }
        if (showConfetti) Confetti(Modifier.fillMaxSize(), listOf(Gold, Silver, Bronze, Teal))
    }
    if (confirmNew) AlertDialog(
        onDismissRequest = { confirmNew = false },
        title = { Text("Commencer une nouvelle soirée ?") },
        text = { Text("Le classement de la soirée repart de zéro. L'historique et les badges sont conservés.") },
        confirmButton = { TextButton({ vm.newEvening(); confirmNew = false; vm.back() }) { Text("Nouvelle soirée", color = Under) } },
        dismissButton = { TextButton({ confirmNew = false }) { Text("Annuler") } }
    )
}

@Composable
private fun PodiumStep(vm: GameViewModel, i: Int, e: Triple<String, Int, Int>, k: Float) {
    val color = listOf(Gold, Silver, Bronze)[i]
    val stepH: Dp = listOf(150.dp, 110.dp, 80.dp)[i]
    val av: Dp = if (i == 0) 84.dp else 66.dp
    Column(
        Modifier.fillMaxWidth().graphicsLayer { translationY = (1 - k) * 600f; alpha = k.coerceIn(0f, 1f) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (i == 0) Text("👑", fontSize = 34.sp)
        Avatar(vm.avatarOfName(e.first), e.first, av, ring = color)
        Text(e.first, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Text("${e.second} pts", color = color, fontWeight = FontWeight.Black, fontSize = 14.sp)
        Text("${e.third} victoire(s)", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(stepH).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = .45f)))),
            contentAlignment = Alignment.TopCenter
        ) { Text("${i + 1}", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Color(0xFF14183A), modifier = Modifier.padding(top = 6.dp)) }
    }
}

// ================================================================ Badges

/** Fenêtre « Nouveau badge ! » affichée en fin de partie. */
@Composable
internal fun NewBadgesDialog(vm: GameViewModel) {
    val list = vm.newBadges
    if (list.isEmpty()) return
    val pop = remember(list) { Animatable(0f) }
    LaunchedEffect(list) {
        delay(900)
        pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = 220f))
        vm.sfx.play(Sfx.Sound.CAUGHT); vm.sfx.vibrate(longArrayOf(40, 40, 120))
        val names = list.joinToString(", ") { it.first }
        vm.narrator.say("Nouveau badge débloqué pour $names !", queue = true)
    }
    if (pop.value == 0f) return
    AlertDialog(
        onDismissRequest = vm::dismissBadges,
        title = { Text("🎖️ Nouveau badge !", fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                list.forEach { (name, badges) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(vm.avatarOfName(name), name, 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(name, fontWeight = FontWeight.Bold)
                            badges.forEach { b ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Text(b.emoji, fontSize = 28.sp, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(b.name, fontWeight = FontWeight.Bold, color = Amber)
                                        Text(b.desc, fontSize = 12.sp, color = Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(vm::dismissBadges) { Text("Bravo !") } }
    )
}

/** Vitrine des badges de chaque joueur (écran Statistiques). */
@Composable
internal fun BadgesShowcase(vm: GameViewModel, names: List<String>) {
    var detail by remember { mutableStateOf<Pair<Badge, Boolean>?>(null) }
    Card {
        Text("Badges", fontWeight = FontWeight.Bold, color = Muted)
        Text("Touche un badge pour savoir comment le débloquer.", fontSize = 12.sp, color = Muted)
        names.forEach { n ->
            val got = vm.badgesOf(n)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(vm.avatarOfName(n), n, 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(n, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${got.size}/${Badges.all.size}", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badges.all.forEach { b ->
                    val on = b.id in got
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .background(if (on) Amber.copy(alpha = .2f) else Surf2)
                            .border(1.dp, if (on) Amber else Color.Transparent, CircleShape)
                            .clickable { detail = b to on },
                        contentAlignment = Alignment.Center
                    ) { Text(b.emoji, fontSize = 18.sp, modifier = Modifier.graphicsLayer { alpha = if (on) 1f else .22f }) }
                }
            }
        }
    }
    detail?.let { (b, on) ->
        AlertDialog(
            onDismissRequest = { detail = null },
            icon = { Text(b.emoji, fontSize = 40.sp, modifier = Modifier.graphicsLayer { alpha = if (on) 1f else .4f }) },
            title = { Text(b.name) },
            text = { Text((if (on) "✅ Débloqué — " else "🔒 À débloquer — ") + b.desc) },
            confirmButton = { TextButton({ detail = null }) { Text("OK") } }
        )
    }
}
