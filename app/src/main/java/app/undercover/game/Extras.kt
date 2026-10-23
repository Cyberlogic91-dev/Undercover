package app.undercover.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// ================================================================ Réglages

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("RÉGLAGES")

        Card {
            Text("Chrono de débat", fontWeight = FontWeight.Bold, color = Amber)
            Text("Temps pour débattre avant le vote. Le téléphone sonne et vibre à la fin.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Aucun", 60 to "1 min", 90 to "1 min 30", 120 to "2 min", 180 to "3 min", 300 to "5 min").forEach { (s, l) ->
                    FilterChip(vm.timerSeconds == s, { vm.changeTimer(s) }, { Text(l) })
                }
            }
        }

        Card {
            Text("Difficulté", fontWeight = FontWeight.Bold, color = Amber)
            Text("Difficile : les deux mots sont très proches (Tigre / Lion). Facile : idéal avec des enfants.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Difficulty.entries.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = vm.difficulty == d, onClick = { vm.changeDifficulty(d) },
                        shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size)
                    ) { Text(d.label) }
                }
            }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Catégories", fontWeight = FontWeight.Bold, color = Amber, modifier = Modifier.weight(1f))
                val all = vm.chosenCategories.size == vm.visibleCategories.size
                TextButton({ vm.allCategories(!all) }) { Text(if (all) "Aucune" else "Toutes") }
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.visibleCategories.forEach { c ->
                    val n = Words.pairs.count { it.category == c }
                    val adult = c in Words.adultCategories
                    val on = c in vm.chosenCategories
                    FilterChip(on, { vm.toggleCategory(c) }, { Text((if (adult) "🔥 " else "") + "$c ($n)") },
                        leadingIcon = if (on) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
                        colors = if (adult) FilterChipDefaults.filterChipColors(selectedContainerColor = Under.copy(alpha = .35f)) else FilterChipDefaults.filterChipColors())
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!vm.spicyGame) Text("${vm.availablePairs} paires avec ces réglages, dont ${vm.freshPairs} jamais jouées", color = Muted, fontSize = 13.sp)
        }

        // ---------- Mode adulte ----------
        var askAge by remember { mutableStateOf(false) }
        Card(border = if (vm.adultMode) Under else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", fontSize = 26.sp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mode adulte (18+)", fontWeight = FontWeight.Bold, color = if (vm.adultMode) Under else White)
                    Text("Débloque les catégories coquines pour les soirées entre adultes.", color = Muted, fontSize = 13.sp)
                }
                Switch(vm.adultMode, { on -> if (on) askAge = true else vm.changeAdultMode(false) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Under))
            }
        }
        if (askAge) AlertDialog(
            onDismissRequest = { askAge = false },
            icon = { Text("🔞", fontSize = 36.sp) },
            title = { Text("Réservé aux adultes") },
            text = { Text("Ce mode contient des mots coquins et suggestifs. Confirmez que tous les joueurs ont 18 ans ou plus et sont d'accord pour jouer avec ces mots.") },
            confirmButton = { TextButton({ vm.changeAdultMode(true); askAge = false }) { Text("Tout le monde a 18 ans+", color = Under) } },
            dismissButton = { TextButton({ askAge = false }) { Text("Annuler") } }
        )

        // ---------- Mémoire des mots ----------
        var confirmReset by remember { mutableStateOf(false) }
        Card {
            Text("Mots déjà joués", fontWeight = FontWeight.Bold, color = Amber)
            Text("L'appli retient les paires déjà sorties pour ne jamais les reproposer. Quand une sélection est épuisée, seules ses paires sont remises en jeu.",
                color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            val total = Words.pairs.count { vm.adultMode || it.category !in Words.adultCategories }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else 1f - vm.totalFresh.toFloat() / total },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), color = Teal, trackColor = Surf2
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${vm.totalFresh} paires jamais jouées sur $total", Modifier.weight(1f), fontSize = 13.sp)
                TextButton({ confirmReset = true }, enabled = vm.playedCount > 0) { Text("Remettre à zéro") }
            }
        }
        if (confirmReset) AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Oublier les mots joués ?") },
            text = { Text("Les ${vm.playedCount} paires déjà jouées pourront ressortir.") },
            confirmButton = { TextButton({ vm.resetPlayed(); confirmReset = false }) { Text("Remettre à zéro", color = Under) } },
            dismissButton = { TextButton({ confirmReset = false }) { Text("Annuler") } }
        )

        Card {
            Text("Rôles spéciaux", fontWeight = FontWeight.Bold, color = Amber)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("🛡️", fontSize = 26.sp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Garde du corps", fontWeight = FontWeight.Bold)
                    Text("Un Civil peut, une fois par partie, sauver le joueur visé par le vote.", color = Muted, fontSize = 13.sp)
                }
                Switch(vm.bodyguardOn, vm::changeBodyguard)
            }
        }

        Card {
            Text("Thème", fontWeight = FontWeight.Bold, color = Amber)
            Text("En Auto, le décor change selon la date : Halloween, Noël, été…", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice.entries.forEach { t ->
                    val label = if (t == ThemeChoice.AUTO) "Auto (${SeasonTheme.forToday().emoji})" else "${t.resolve().emoji} ${t.label}"
                    FilterChip(vm.themeChoice == t, { vm.changeTheme(t) }, { Text(label) })
                }
            }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎙️", fontSize = 26.sp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Narrateur vocal", fontWeight = FontWeight.Bold)
                    Text("Une voix annonce les tours, le chrono et les révélations.", color = Muted, fontSize = 13.sp)
                }
                Switch(vm.narratorOn, vm::changeNarrator)
            }
            if (vm.narratorOn && !vm.narratorReady) Text(
                "Voix française introuvable : installez-la dans Paramètres Android › Synthèse vocale.",
                color = Under, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }

        Card {
            Text("Ambiance", fontWeight = FontWeight.Bold, color = Amber)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeUp, null, tint = Muted); Spacer(Modifier.width(12.dp))
                Text("Sons", Modifier.weight(1f)); Switch(vm.soundOn, vm::changeSound)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Vibration, null, tint = Muted); Spacer(Modifier.width(12.dp))
                Text("Vibrations", Modifier.weight(1f)); Switch(vm.vibrationOn, vm::changeVibration)
            }
        }

        UpdateCard(vm)

        BigButton("Terminé") { vm.back() }
        Spacer(Modifier.height(8.dp))
    }
}

// ================================================================ Statistiques

@Composable
internal fun StatsScreen(vm: GameViewModel) {
    val h = vm.history
    val stats = remember(h) { vm.playerStats() }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("STATISTIQUES", if (h.isEmpty()) "Joue une partie pour voir apparaître les stats" else null) }
        if (h.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Parties", "${h.size}", Amber, Modifier.weight(1f))
                    StatTile("Civils", "${h.count { it.outcome == Outcome.CIVILS }}", Civil, Modifier.weight(1f))
                    StatTile("Imposteurs", "${h.count { it.outcome != Outcome.CIVILS }}", Under, Modifier.weight(1f))
                }
            }
            item {
                Card {
                    Text("Trophées", fontWeight = FontWeight.Bold, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Trophy("🕵️", "Meilleur Undercover", stats.maxByOrNull { it.undercoverWins }?.takeIf { it.undercoverWins > 0 }, vm) { "${it.undercoverWins} victoire(s)" }
                    Trophy("👻", "Roi du bluff (Mr. White)", stats.maxByOrNull { it.mrWhiteWins }?.takeIf { it.mrWhiteWins > 0 }, vm) { "${it.mrWhiteWins} victoire(s)" }
                    Trophy("🛡️", "Meilleur Civil", stats.maxByOrNull { it.civilWins }?.takeIf { it.civilWins > 0 }, vm) { "${it.civilWins} victoire(s)" }
                    Trophy("🎯", "Le plus souvent imposteur", stats.maxByOrNull { it.undercoverGames + it.mrWhiteGames }?.takeIf { it.undercoverGames + it.mrWhiteGames > 0 }, vm) { "${it.undercoverGames + it.mrWhiteGames} fois" }
                }
            }
            item {
                Card {
                    Text("Joueurs", fontWeight = FontWeight.Bold, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Spacer(Modifier.weight(1f))
                        Text("Parties", Modifier.width(56.dp), color = Muted, fontSize = 12.sp, textAlign = TextAlign.End)
                        Text("Gagnées", Modifier.width(62.dp), color = Muted, fontSize = 12.sp, textAlign = TextAlign.End)
                        Text("%", Modifier.width(44.dp), color = Muted, fontSize = 12.sp, textAlign = TextAlign.End)
                    }
                    stats.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(vm.avatarOfName(s.name), s.name, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(s.name, Modifier.weight(1f))
                            Text("${s.games}", Modifier.width(56.dp), textAlign = TextAlign.End)
                            Text("${s.wins}", Modifier.width(62.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                            Text("${s.rate}", Modifier.width(44.dp), textAlign = TextAlign.End, color = Amber)
                        }
                    }
                }
            }
            item { BadgesShowcase(vm, stats.map { it.name }) }
            item { Text("Dernières parties", fontWeight = FontWeight.Bold, color = Muted) }
            items(h.take(30)) { g ->
                val (label, color) = when (g.outcome) {
                    Outcome.CIVILS -> "Civils" to Civil
                    Outcome.IMPOSTORS -> "Imposteurs" to Under
                    Outcome.MRWHITE -> "Mr. White" to White
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surf).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${g.civilWord} / ${g.undercoverWord}", fontWeight = FontWeight.Bold)
                        Text("${fmt.format(Date(g.time))} · ${g.players.size} joueurs · gagnants : " +
                                g.players.filter { it.third }.joinToString { it.first }, color = Muted, fontSize = 12.sp)
                    }
                    Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            item {
                TextButton({ confirmClear = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Effacer l'historique", color = Under)
                }
            }
        }
        item { BigButton("Retour") { vm.back() }; Spacer(Modifier.height(16.dp)) }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Effacer l'historique ?") },
        text = { Text("Toutes les parties et statistiques seront supprimées.") },
        confirmButton = { TextButton({ vm.clearHistory(); confirmClear = false }) { Text("Effacer", color = Under) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Annuler") } }
    )
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Surf).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Trophy(emoji: String, title: String, s: PlayerStats?, vm: GameViewModel, detail: (PlayerStats) -> String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 24.sp, modifier = Modifier.width(38.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(s?.name ?: "—", fontWeight = FontWeight.Bold)
        }
        if (s != null) {
            Text(detail(s), color = Amber, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Avatar(vm.avatarOfName(s.name), s.name, 34.dp)
        }
    }
}

// ================================================================ Groupes

@Composable
internal fun GroupsDialog(vm: GameViewModel, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Groups, null) },
        title = { Text("Groupes de joueurs") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (vm.groups.isEmpty()) Text("Aucun groupe enregistré pour l'instant.", color = Muted)
                vm.groups.forEach { (name, members) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.loadGroup(name); onDismiss() }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold)
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                members.take(7).forEach { Avatar(vm.avatarOf(it.id), it.name, 26.dp) }
                            }
                            Text("${members.size} joueurs", color = Muted, fontSize = 12.sp)
                        }
                        IconButton({ toDelete = name }) { Icon(Icons.Default.Delete, "Supprimer", tint = Muted) }
                    }
                    HorizontalDivider(color = Surf2)
                }
                Spacer(Modifier.height(14.dp))
                Text("Enregistrer les joueurs actuels", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(newName, { newName = it }, singleLine = true, placeholder = { Text("Famille, Collègues…") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                if (vm.groups.any { it.first == newName.trim() })
                    Text("Ce groupe sera remplacé.", color = Amber, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton({ vm.saveGroup(newName); newName = "" }, enabled = newName.isNotBlank()) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Fermer") } }
    )
    toDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer « $n » ?") },
            confirmButton = { TextButton({ vm.deleteGroup(n); toDelete = null }) { Text("Supprimer", color = Under) } },
            dismissButton = { TextButton({ toDelete = null }) { Text("Annuler") } }
        )
    }
}

// ================================================================ Chrono

@Composable
internal fun TimerCard(vm: GameViewModel) {
    if (vm.timerSeconds == 0) return
    val left = vm.timerLeft
    val frac = if (vm.timerSeconds == 0) 0f else left.toFloat() / vm.timerSeconds
    val color = when { left == 0 -> Under; left <= 10 -> Under; left <= 30 -> Amber; else -> Civil }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { frac }, Modifier.fillMaxSize(), color = color, trackColor = Surf2, strokeWidth = 5.dp)
            Icon(Icons.Default.Timer, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("%d:%02d".format(left / 60, left % 60), fontSize = 28.sp, fontWeight = FontWeight.Black, color = if (left == 0) Under else White)
            Text(if (left == 0) "Temps écoulé : votez !" else if (vm.timerRunning) "Débat en cours" else "Chrono de débat", color = Muted, fontSize = 12.sp)
        }
        if (vm.timerRunning) FilledTonalIconButton(vm::pauseTimer) { Icon(Icons.Default.Pause, "Pause") }
        else FilledIconButton(vm::startTimer, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Amber)) {
            Icon(Icons.Default.PlayArrow, "Démarrer", tint = Color(0xFF111111))
        }
        IconButton(vm::resetTimer) { Icon(Icons.Default.Replay, "Recommencer", tint = Muted) }
    }
}

// ================================================================ Carte qui se retourne

@Composable
internal fun FlipCard(flipKey: Any, back: @Composable () -> Unit, front: @Composable () -> Unit) {
    val rot = remember(flipKey) { Animatable(0f) }
    LaunchedEffect(flipKey) { rot.animateTo(180f, tween(650, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.fillMaxWidth().graphicsLayer { rotationY = rot.value; cameraDistance = 14f * density },
        contentAlignment = Alignment.Center
    ) {
        if (rot.value <= 90f) back()
        else Box(Modifier.graphicsLayer { rotationY = 180f }) { front() }
    }
}

@Composable
internal fun CardBack() {
    Box(
        Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)).background(Surf2)
            .border(2.dp, Amber, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(70.dp).clip(CircleShape).border(3.dp, Amber, CircleShape), contentAlignment = Alignment.Center) {
            Text("?", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Amber)
        }
    }
}

// ================================================================ Confettis

private class Confetto(
    var x: Float, var y: Float, val vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val w: Float, val h: Float, val color: Color
)

@Composable
internal fun Confetti(modifier: Modifier = Modifier, colors: List<Color>) {
    val parts = remember {
        List(140) {
            Confetto(
                Random.nextFloat(), -Random.nextFloat() * .5f,
                (Random.nextFloat() - .5f) * .25f, Random.nextFloat() * .25f + .2f,
                Random.nextFloat() * 360f, (Random.nextFloat() - .5f) * 720f,
                Random.nextFloat() * 8f + 6f, Random.nextFloat() * 6f + 4f, colors.random()
            )
        }
    }
    var frame by remember { mutableLongStateOf(0L) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        val start = last
        while (last - start < 5_000_000_000L) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1e9f; last = now
            parts.forEach { p ->
                p.x += p.vx * dt; p.vy += .35f * dt; p.y += p.vy * dt; p.angle += p.spin * dt
            }
            frame = now
        }
        done = true
    }
    if (done) return
    Canvas(modifier) {
        frame // relecture pour redessiner à chaque image
        parts.forEach { p ->
            val cx = p.x * size.width; val cy = p.y * size.height
            if (cy < size.height + 20) rotate(p.angle, Offset(cx, cy)) {
                drawRect(p.color, Offset(cx - p.w / 2 * density, cy - p.h / 2 * density), Size(p.w * density, p.h * density))
            }
        }
    }
}

// ================================================================ Habillage

private val Montserrat = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.montserrat_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.montserrat_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(R.font.montserrat_black, FontWeight.Black)
)

internal fun appTypography(): Typography {
    val t = Typography()
    fun androidx.compose.ui.text.TextStyle.m() = copy(fontFamily = Montserrat)
    return t.copy(
        displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m(), headlineMedium = t.headlineMedium.m(), headlineSmall = t.headlineSmall.m(),
        titleLarge = t.titleLarge.m(), titleMedium = t.titleMedium.m(), titleSmall = t.titleSmall.m(),
        bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(), bodySmall = t.bodySmall.m(),
        labelLarge = t.labelLarge.m(), labelMedium = t.labelMedium.m(), labelSmall = t.labelSmall.m()
    )
}

/** Fond de nuit avec une aurore qui ondule doucement. */
@Composable
internal fun AuroraBackground(theme: SeasonTheme = SeasonTheme.CLASSIC) {
    val c = theme.aurora
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "aurora")
    val t by tr.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(14000), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "t"
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF12152B), Color(0xFF070816), Color(0xFF03040C))))
        fun blob(cx: Float, cy: Float, r: Float, c: Color, sx: Float = 1f) {
            val center = Offset(size.width * cx, size.height * cy)
            scale(sx, 1f, center) {
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c, Color.Transparent), center, size.width * r), size.width * r, center)
            }
        }
        blob(.15f + .05f * t, .30f, .55f, c[0])            // halo haut
        blob(.98f, .26f - .03f * t, .22f, c[1])             // braise
        blob(.35f + .12f * t, .64f, .55f, c[2], sx = 1.9f)  // aurore
        blob(.75f - .10f * t, .72f, .45f, c[3], sx = 1.6f)  // seconde aurore
        blob(.50f, .80f + .02f * t, .35f, Color(0x55120A40), sx = 2.2f)  // ombre de montagne
    }
}

/** Dos de carte original : dégradé jaune-orangé et emblème détective. */
@Composable
internal fun GameCardBack(modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .graphicsLayer { shadowElevation = 10f; shape = RoundedCornerShape(18.dp); clip = true }
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF7D100), Color(0xFFFFB000), Color(0xFFFFE08A))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.card_emblem), null,
            Modifier.fillMaxSize(.72f)
        )
    }
}


// ================================================================ Bouton « i »

/** Petit bouton « i » qui déplie la définition du mot juste en dessous. */
@Composable
internal fun WordInfo(word: String, dark: Boolean = false) {
    val def = remember(word) { Words.definition(word) } ?: return
    var open by remember(word) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(if (dark) Color(0x330A0D1E) else Teal.copy(alpha = .2f))
                .border(1.5.dp, if (dark) Color(0xFF0A0D1E) else Teal, CircleShape)
                .clickable { open = !open },
            contentAlignment = Alignment.Center
        ) {
            Text("i", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (dark) Color(0xFF0A0D1E) else Teal)
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Text(
                def, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
                color = if (dark) Color(0xFF0A0D1E) else White,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )
        }
    }
}


// ================================================================ Version et mises à jour

@Composable
internal fun UpdateCard(vm: GameViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_logo), null, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Undercover", fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("Version ${vm.appVersion} (build ${vm.appCode})", color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Développé par CYBERLOGIC", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mises à jour automatiques", fontWeight = FontWeight.Bold)
                Text("Vérifie les nouvelles versions sur GitHub au lancement.", color = Muted, fontSize = 13.sp)
            }
            Switch(vm.autoUpdate, vm::changeAutoUpdate)
        }
        Spacer(Modifier.height(8.dp))
        when (val u = vm.update) {
            is GameViewModel.UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Recherche en cours…", fontSize = 13.sp)
            }
            is GameViewModel.UpdateState.UpToDate -> Text("✅ Tu as la dernière version.", color = Civil, fontSize = 13.sp)
            is GameViewModel.UpdateState.Failed -> Text("⚠️ ${u.message}", color = Under, fontSize = 13.sp)
            is GameViewModel.UpdateState.Available -> Text("🆕 Version ${u.release.version} disponible !", color = Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            is GameViewModel.UpdateState.Downloading -> Column {
                Text("Téléchargement de la version ${u.release.version}… ${(u.progress * 100).toInt()} %", fontSize = 13.sp)
                LinearProgressIndicator(progress = { u.progress }, Modifier.fillMaxWidth().padding(top = 6.dp), color = Teal, trackColor = Surf2)
            }
            is GameViewModel.UpdateState.Ready -> Text("Version ${u.release.version} prête à installer.", color = Civil, fontSize = 13.sp)
            else -> {}
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val u = vm.update) {
                is GameViewModel.UpdateState.Available -> Button({ vm.downloadUpdate(u.release) }, Modifier.weight(1f)) { Text("Installer ${u.release.version}") }
                is GameViewModel.UpdateState.Ready -> Button({ vm.installUpdate() }, Modifier.weight(1f)) { Text("Installer") }
                else -> OutlinedButton({ vm.checkUpdate(manual = true) }, Modifier.weight(1f),
                    enabled = vm.update !is GameViewModel.UpdateState.Checking && vm.update !is GameViewModel.UpdateState.Downloading) { Text("Rechercher une mise à jour") }
            }
            TextButton({ Updater.openPage(ctx) }) { Text("GitHub") }
        }
    }
}

/** Fenêtre « nouvelle version disponible » (affichée sur le menu). */
@Composable
internal fun UpdateDialog(vm: GameViewModel) {
    if (!vm.showUpdateDialog) return
    val r = when (val u = vm.update) {
        is GameViewModel.UpdateState.Available -> u.release
        is GameViewModel.UpdateState.Downloading -> u.release
        is GameViewModel.UpdateState.Ready -> u.release
        else -> return
    }
    val u = vm.update
    AlertDialog(
        onDismissRequest = { if (u !is GameViewModel.UpdateState.Downloading) vm.showUpdateDialog = false },
        icon = { Text("🆕", fontSize = 36.sp) },
        title = { Text("Version ${r.version} disponible") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("Tu as la version ${vm.appVersion}.", color = Muted, fontSize = 13.sp)
                if (r.notes.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(r.notes.replace("**", "").replace("### ", "").replace("## ", ""), fontSize = 14.sp) }
                if (u is GameViewModel.UpdateState.Downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { u.progress }, Modifier.fillMaxWidth(), color = Teal, trackColor = Surf2)
                    Text("${(u.progress * 100).toInt()} %", fontSize = 12.sp, color = Muted)
                }
            }
        },
        confirmButton = {
            when (u) {
                is GameViewModel.UpdateState.Available -> TextButton({ vm.downloadUpdate(r) }) { Text("Mettre à jour") }
                is GameViewModel.UpdateState.Ready -> TextButton({ vm.installUpdate() }) { Text("Installer") }
                else -> {}
            }
        },
        dismissButton = {
            if (u is GameViewModel.UpdateState.Available) TextButton({ vm.skipUpdate(r) }) { Text("Plus tard") }
        }
    )
}
