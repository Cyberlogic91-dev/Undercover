package app.undercover.game

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.undercover.game.online.*
import kotlinx.coroutines.delay

private val Blue = Color(0xFF5AA9FF)

/** Point d'entrée du mode en ligne. */
@Composable
internal fun OnlineScreen(vm: GameViewModel, ovm: OnlineViewModel) {
    var confirmLeave by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler {
        if (ovm.role == OnlineViewModel.Role.NONE) vm.goMenu() else confirmLeave = true
    }
    val s = ovm.state
    Box(Modifier.fillMaxSize()) {
        when {
            ovm.role == OnlineViewModel.Role.NONE -> OnlineHome(vm, ovm)
            s == null || !ovm.joined -> OnlineConnecting(ovm) { confirmLeave = true }
            s.adult && !ovm.adultConfirmed -> AdultGate(ovm)
            else -> when (s.phase) {
                OPhase.LOBBY -> OnlineLobby(vm, ovm, s) { confirmLeave = true }
                OPhase.DEAL -> OnlineDeal(vm, ovm, s)
                OPhase.CLUES -> OnlineClues(ovm, s)
                OPhase.VOTE -> OnlineVote(vm, ovm, s)
                OPhase.REVEAL -> OnlineReveal(vm, ovm, s)
                OPhase.MRWHITE -> OnlineMrWhite(ovm, s)
                OPhase.END -> OnlineEnd(vm, ovm, s) { confirmLeave = true }
            }
        }
        if (ovm.role != OnlineViewModel.Role.NONE && ovm.link != OnlineViewModel.Link.ONLINE && ovm.link != OnlineViewModel.Link.CONNECTING)
            Text(if (ovm.link == OnlineViewModel.Link.RECONNECTING) "📡 Reconnexion…" else "⚠️ Hors ligne",
                Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(CircleShape).background(Under).padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(if (ovm.isHost) "Fermer la partie ?" else "Quitter la partie ?") },
        text = { Text(if (ovm.isHost) "La partie sera terminée pour tous les joueurs." else "Tu pourras revenir avec le même code tant que la partie existe.") },
        confirmButton = { TextButton({ confirmLeave = false; ovm.leave(); vm.goMenu() }) { Text(if (ovm.isHost) "Fermer" else "Quitter", color = Under) } },
        dismissButton = { TextButton({ confirmLeave = false }) { Text("Rester") } }
    )
    if (ovm.hostGone) AlertDialog(
        onDismissRequest = {},
        icon = { Text("📴", fontSize = 34.sp) },
        title = { Text("L'hôte a fermé la partie") },
        text = { Text("La partie en ligne est terminée.") },
        confirmButton = { TextButton({ ovm.leave(); vm.goMenu() }) { Text("Retour au menu") } }
    )
    ovm.error?.let { e ->
        AlertDialog(
            onDismissRequest = { ovm.error = null },
            title = { Text("Oups") }, text = { Text(e) },
            confirmButton = {
                TextButton({
                    ovm.error = null
                    if (ovm.link == OnlineViewModel.Link.FAILED || (!ovm.joined && ovm.role == OnlineViewModel.Role.GUEST)) ovm.leave()
                }) { Text("OK") }
            }
        )
    }
}

// ================================================================ accueil

@Composable
private fun OnlineHome(vm: GameViewModel, ovm: OnlineViewModel) {
    var code by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(vm::goMenu) { Icon(Icons.Default.ArrowBack, "Menu") }
        }
        Text("🌐", fontSize = 54.sp)
        Text("Jouer en ligne", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Teal)
        Text("Chacun sur son téléphone, même à distance.", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            ovm.myName, ovm::changeName, singleLine = true, label = { Text("Ton prénom") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        Spacer(Modifier.height(18.dp))
        Card(border = Teal) {
            Text("Créer une partie", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text("Tu obtiens un code à partager avec tes amis.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            BigButton("➕  Créer", ovm.myName.isNotBlank()) { ovm.create { spicy -> vm.drawOnlinePair(spicy) } }
        }
        Spacer(Modifier.height(14.dp))
        Card(border = Amber) {
            Text("Rejoindre une partie", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, singleLine = true,
                label = { Text("Code à 6 caractères") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (code.length == 6 && ovm.myName.isNotBlank()) ovm.join(code) })
            )
            Spacer(Modifier.height(10.dp))
            BigButton("🚪  Rejoindre", code.length == 6 && ovm.myName.isNotBlank()) { ovm.join(code) }
        }
        Spacer(Modifier.height(18.dp))
        Text("🔒 Les échanges passent par un relais public gratuit et sont chiffrés avec le code de la partie : personne d'autre ne peut les lire.",
            color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OnlineConnecting(ovm: OnlineViewModel, onLeave: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Teal)
        Spacer(Modifier.height(16.dp))
        Text(
            when (ovm.link) {
                OnlineViewModel.Link.CONNECTING -> "Connexion au relais…"
                OnlineViewModel.Link.ONLINE -> if (ovm.isHost) "Création de la partie…" else "Recherche de la partie ${ovm.code}…"
                else -> "Reconnexion…"
            }, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onLeave) { Text("Annuler", color = Muted) }
    }
}

@Composable
private fun AdultGate(ovm: OnlineViewModel) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Text("🔞", fontSize = 36.sp) },
        title = { Text("Partie réservée aux adultes") },
        text = { Text("Cette partie utilise des mots coquins. Confirme que tu as 18 ans ou plus pour la rejoindre.") },
        confirmButton = { TextButton({ ovm.adultConfirmed = true }) { Text("J'ai 18 ans ou plus", color = Under) } },
        dismissButton = { TextButton({ ovm.leave() }) { Text("Quitter") } }
    )
}

// ================================================================ salon

@Composable
private fun OnlineLobby(vm: GameViewModel, ovm: OnlineViewModel, s: OState, onLeave: () -> Unit) {
    val ctx = LocalContext.current
    var err by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onLeave) { Icon(Icons.Default.Close, "Quitter") }
                Text("Salon en ligne", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Teal, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
        }
        item {
            Card(border = Amber) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Code de la partie", color = Muted, fontSize = 13.sp)
                    Text(ovm.code, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp, color = Amber)
                    OutlinedButton({
                        val msg = "🕵️ Rejoins ma partie d'Undercover en ligne !\nCode : ${ovm.code}\n\nPas encore le jeu ? https://cyberlogic91-dev.github.io/Undercover/"
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Inviter des amis")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Inviter des amis") }
                }
            }
        }
        if (s.adult) item {
            Text("🔥 Partie coquine (18+)", color = Under, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        item { Text("Joueurs (${s.players.size})", color = Muted, fontWeight = FontWeight.Bold) }
        items(s.players, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surf).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(null, p.name, 40.dp, dim = !p.connected)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name + if (p.id == ovm.myId) " (toi)" else "", fontWeight = FontWeight.Bold)
                    Text(if (p.id == s.hostId) "👑 Hôte" else if (p.connected) "Connecté" else "Déconnecté", fontSize = 12.sp,
                        color = if (p.connected) Civil else Under)
                }
                if (ovm.isHost && p.id != ovm.myId) IconButton({ ovm.kick(p.id) }) { Icon(Icons.Default.PersonRemove, "Retirer", tint = Muted) }
            }
        }
        if (ovm.isHost) {
            item {
                Card {
                    Text("Indices", fontWeight = FontWeight.Bold, color = Amber)
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ClueMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(s.clueMode == m, { ovm.setClueMode(m) }, SegmentedButtonDefaults.itemShape(i, 2)) {
                                Text(if (m == ClueMode.WRITTEN) "✍️ Écrits" else "🎙️ À l'oral")
                            }
                        }
                    }
                    Text(if (s.clueMode == ClueMode.WRITTEN) "Chacun tape son indice à son tour, tout le monde le voit."
                         else "Vous êtes en visio : chacun dit son indice à voix haute, puis touche « J'ai parlé ».",
                        color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    val (u, w) = ovm.rolesPreview(s.players.count { it.connected })
                    Text("Rôles : ${s.players.count { it.connected } - u - w} Civils · $u Undercover" + (if (w > 0) " · $w Mr. White" else "") + " (auto)",
                        fontSize = 13.sp)
                    if (vm.adultMode) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("🔥 Partie coquine (18+)", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Switch(s.spicy, { on -> ovm.setSpicy(on, on) }, colors = SwitchDefaults.colors(checkedTrackColor = Under))
                    }
                    Text("Mots : vos réglages (${vm.difficulty.label}" + (if (s.spicy) ", coquins uniquement" else "") + ").",
                        color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                err?.let { Text(it, color = Under, modifier = Modifier.padding(bottom = 6.dp)) }
                BigButton("▶  Lancer la partie", s.players.count { it.connected } >= 3) { err = ovm.start() }
                if (s.players.size < 3) Text("Il faut au moins 3 joueurs. Partage le code !", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
            }
        } else item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Teal)
                    Spacer(Modifier.width(12.dp))
                    Text("En attente du lancement par ${s.player(s.hostId)?.name ?: "l'hôte"}…")
                }
                Text(if (s.clueMode == ClueMode.WRITTEN) "✍️ Indices écrits dans l'appli" else "🎙️ Indices à l'oral (visio)",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ================================================================ distribution

@Composable
private fun OnlineDeal(vm: GameViewModel, ovm: OnlineViewModel, s: OState) {
    val sec = ovm.secret
    var shown by remember(ovm.secret?.round) { mutableStateOf(false) }
    val me = ovm.me
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ton mot secret", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Teal, modifier = Modifier.padding(top = 12.dp))
        Text("Personne ne doit voir ton écran !", color = Muted)
        Spacer(Modifier.weight(.4f))
        if (sec == null) {
            CircularProgressIndicator(color = Teal); Text("Réception de ta carte…", color = Muted, modifier = Modifier.padding(top = 10.dp))
        } else if (!shown) {
            GameCardBack(Modifier.fillMaxWidth(.7f).aspectRatio(.75f), enabled = true) { shown = true; vm.sfx.play(Sfx.Sound.FLIP) }
            Text("Touche la carte pour la retourner", color = Muted, modifier = Modifier.padding(top = 12.dp))
        } else {
            FlipCard(sec.round, back = { GameCardBack(Modifier.fillMaxWidth().height(200.dp), enabled = false) {} }) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(22.dp))
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x66B8C4E0), Color(0x8840E0E8)))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(sec.word ?: "Tu es Mr. White", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0D1E), textAlign = TextAlign.Center)
                        if (sec.word != null) { Spacer(Modifier.height(6.dp)); WordInfo(sec.word, dark = true) }
                        else Text("Tu n'as pas de mot : écoute et bluffe !", color = Color(0xFF0A0D1E), fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val readyCount = s.players.count { it.ready }
        Text("$readyCount / ${s.players.size} joueurs prêts", color = Muted, modifier = Modifier.padding(bottom = 8.dp))
        if (me?.ready == true) Text("✅ Prêt ! On attend les autres…", color = Civil, fontWeight = FontWeight.Bold)
        else BigButton("J'ai mémorisé mon mot", shown && sec != null) { ovm.ready() }
    }
}

// ================================================================ indices

@Composable
private fun CluesList(s: OState, highlight: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.order.forEachIndexed { i, id ->
            val p = s.player(id) ?: return@forEachIndexed
            val clue = s.clues[id]
            val now = id == highlight
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (now) Teal.copy(alpha = .16f) else Surf)
                    .then(if (now) Modifier.border(1.5.dp, Teal, RoundedCornerShape(14.dp)) else Modifier)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", fontWeight = FontWeight.Black, color = if (now) Teal else Muted, modifier = Modifier.width(22.dp))
                Avatar(null, p.name, 34.dp, dim = !p.connected)
                Spacer(Modifier.width(10.dp))
                Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                when {
                    clue != null -> Text(if (clue == "✓") "✓ a parlé" else "« $clue »", color = Amber, fontWeight = FontWeight.Bold)
                    now -> Text("…", color = Teal, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun PreviousClues(s: OState) {
    if (s.history.isEmpty()) return
    Card {
        Text("Tours précédents", color = Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        s.history.groupBy { it.first }.forEach { (r, l) ->
            Text("Tour $r : " + l.filter { it.third != "✓" }.joinToString(" · ") { "${s.player(it.second)?.name ?: "?"} « ${it.third} »" }
                .ifEmpty { "indices donnés à l'oral" }, fontSize = 12.sp, color = White)
        }
    }
}

@Composable
private fun OnlineClues(ovm: OnlineViewModel, s: OState) {
    val myTurn = s.currentSpeakerId == ovm.myId
    var text by remember(s.round, s.speaker) { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Tour ${s.round}", fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
            Text(if (s.clueMode == ClueMode.WRITTEN) "Un indice chacun, dans l'ordre." else "Chacun dit son indice à voix haute, dans l'ordre.", color = Muted)
        }
        item { MySecretChip(ovm) }
        item { CluesList(s, s.currentSpeakerId) }
        item {
            if (myTurn) Card(border = Teal) {
                Text("🎤 C'est ton tour !", fontWeight = FontWeight.Black, color = Teal, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                if (s.clueMode == ClueMode.WRITTEN) {
                    OutlinedTextField(text, { text = it.take(40) }, singleLine = true, label = { Text("Ton indice (un mot)") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) ovm.sendClue(text) }))
                    Spacer(Modifier.height(8.dp))
                    BigButton("Envoyer", text.isNotBlank()) { ovm.sendClue(text) }
                } else BigButton("✓  J'ai donné mon indice") { ovm.doneSpeaking() }
            } else {
                val who = s.player(s.currentSpeakerId)?.name ?: "?"
                Text("⏳ Au tour de $who…", color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                if (ovm.isHost) TextButton({ ovm.skipSpeaker() }, Modifier.fillMaxWidth()) { Text("Passer $who (absent ou bloqué)", color = Muted) }
            }
        }
        item { PreviousClues(s) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Rappel discret du mot secret (touche pour l'afficher). */
@Composable
private fun MySecretChip(ovm: OnlineViewModel) {
    val sec = ovm.secret ?: return
    var show by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surf2).clickable { show = !show }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Muted)
        Spacer(Modifier.width(10.dp))
        Text(if (show) (sec.word ?: "Mr. White (pas de mot)") else "Revoir mon mot", fontWeight = FontWeight.Bold, color = if (show) Amber else White)
    }
}

// ================================================================ vote

@Composable
private fun OnlineVote(vm: GameViewModel, ovm: OnlineViewModel, s: OState) {
    val me = ovm.me
    var picked by remember(s.round, s.tie) { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Vote secret", fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
            Text("Débattez, puis chacun vote sur son téléphone.", color = Muted)
        }
        s.message?.let { m -> item { Card(border = Under) { Text("⚖️ $m", fontWeight = FontWeight.Bold) } } }
        item { CluesList(s, null) }
        item {
            LinearProgressIndicator(progress = { if (s.votesNeeded == 0) 0f else s.votesDone.toFloat() / s.votesNeeded },
                Modifier.fillMaxWidth().clip(CircleShape), color = Amber, trackColor = Surf2)
            Text("${s.votesDone} / ${s.votesNeeded} votes", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        when {
            me == null || !me.alive -> item { Text("👻 Tu es éliminé : tu observes la partie.", color = Muted) }
            me.voted -> item { Card { Text("🗳️ Vote envoyé ! On attend les autres…", fontWeight = FontWeight.Bold, color = Civil) } }
            else -> {
                item { Text("Qui est l'imposteur ?", fontWeight = FontWeight.Bold) }
                items(s.players.filter { it.alive && it.id != ovm.myId && (s.tie.isEmpty() || it.id in s.tie) }, key = { it.id }) { p ->
                    val sel = picked == p.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (sel) Under.copy(alpha = .18f) else Surf)
                            .border(if (sel) 2.dp else 0.dp, if (sel) Under else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { picked = p.id; vm.sfx.play(Sfx.Sound.TAP) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(null, p.name, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            s.clues[p.id]?.takeIf { it != "✓" }?.let { Text("« $it »", color = Muted, fontSize = 13.sp) }
                        }
                        if (sel) Text("👉", fontSize = 22.sp)
                    }
                }
                item { BigButton("Voter contre ${s.player(picked)?.name ?: "…"}", picked != null) { picked?.let { ovm.vote(it) } } }
            }
        }
        item { MySecretChip(ovm) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ================================================================ révélation

@Composable
private fun OnlineReveal(vm: GameViewModel, ovm: OnlineViewModel, s: OState) {
    val p = s.player(s.elimId) ?: return
    val role = p.revealedRole ?: Role.CIVIL
    var phase by remember(s.elimId, s.round) { mutableIntStateOf(0) }
    val pop = remember(s.elimId, s.round) { Animatable(0f) }
    LaunchedEffect(s.elimId, s.round) {
        if (s.mrWhiteGuess != null) { phase = 2; pop.snapTo(1f); return@LaunchedEffect }
        vm.sfx.play(Sfx.Sound.DRUM)
        delay(2600)
        phase = 2
        vm.sfx.play(if (role == Role.CIVIL) Sfx.Sound.FAIL else Sfx.Sound.CAUGHT)
        vm.sfx.vibrate(if (role == Role.CIVIL) longArrayOf(400) else longArrayOf(60, 50, 60, 50, 300))
        pop.animateTo(1f, spring(dampingRatio = .4f, stiffness = 260f))
    }
    // l'hôte enchaîne automatiquement après quelques secondes
    if (ovm.isHost) LaunchedEffect(s.seq) { delay(if (s.mrWhiteGuess != null) 5000 else 9000); ovm.advance() }
    val beat = rememberInfiniteTransition(label = "b")
    val pulse by beat.animateFloat(1f, 1.07f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "p")

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Text(if (phase < 2) "Le groupe a voté contre…" else "Éliminé(e)", color = Muted)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.graphicsLayer { val k = if (phase < 2) pulse else 1f; scaleX = k; scaleY = k }) {
                Avatar(null, p.name, 140.dp, ring = if (phase == 2) role.color() else Color.White, dim = phase == 2 && role == Role.CIVIL)
            }
            Text(p.name, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().height(130.dp).graphicsLayer { scaleX = .6f + .4f * pop.value; scaleY = .6f + .4f * pop.value }
                    .clip(RoundedCornerShape(22.dp)).background(if (phase == 2) role.color().copy(alpha = .18f) else Surf)
                    .border(3.dp, if (phase == 2) role.color() else Surf2, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (phase < 2) Text("Son rôle est…", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Muted)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("C'était", color = Muted)
                    Text(role.label.uppercase(), fontSize = 34.sp, fontWeight = FontWeight.Black, color = role.color())
                }
            }
            if (phase == 2) {
                Text(when (role) { Role.CIVIL -> "Oups… un innocent !"; Role.UNDERCOVER -> "Imposteur démasqué ! 🎯"; Role.MRWHITE -> "Mr. White est démasqué !" },
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = role.color(), modifier = Modifier.padding(top = 14.dp))
                s.message?.let { Text(it, color = Muted, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center) }
            }
            Spacer(Modifier.weight(1f))
            if (ovm.isHost && phase == 2) BigButton("Continuer") { ovm.advance() }
            else if (phase == 2) Text("La partie continue dans un instant…", color = Muted)
        }
        if (phase == 2 && role != Role.CIVIL && s.mrWhiteGuess == null) Confetti(Modifier.fillMaxSize(), listOf(role.color(), Amber, Teal))
    }
}

@Composable
private fun OnlineMrWhite(ovm: OnlineViewModel, s: OState) {
    val p = s.player(s.elimId) ?: return
    var g by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Avatar(null, p.name, 120.dp, ring = White)
        Text(if (p.id == ovm.myId) "Dernière chance !" else "${p.name} était Mr. White", fontSize = 26.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 14.dp), textAlign = TextAlign.Center)
        Text(if (p.id == ovm.myId) "Devine le mot des Civils pour gagner seul." else "Il tente de deviner le mot des Civils…",
            color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        if (p.id == ovm.myId) {
            OutlinedTextField(g, { g = it.take(40) }, singleLine = true, label = { Text("Le mot des Civils est…") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Spacer(Modifier.weight(1f))
            BigButton("Valider", g.isNotBlank()) { ovm.guess(g) }
        } else {
            CircularProgressIndicator(color = Teal)
            Spacer(Modifier.weight(1f))
        }
    }
}

// ================================================================ fin

@Composable
private fun OnlineEnd(vm: GameViewModel, ovm: OnlineViewModel, s: OState, onLeave: () -> Unit) {
    val (title, color) = when (s.outcome) {
        Outcome.CIVILS -> "Les Civils gagnent !" to Civil
        Outcome.IMPOSTORS -> "Les imposteurs gagnent !" to Under
        Outcome.MRWHITE -> "Mr. White a trouvé le mot !" to White
        null -> "" to Amber
    }
    val myRole = ovm.secret?.role
    val iWon = when (s.outcome) { Outcome.CIVILS -> myRole == Role.CIVIL; Outcome.IMPOSTORS -> myRole != Role.CIVIL && myRole != null; Outcome.MRWHITE -> myRole == Role.MRWHITE; null -> false }
    LaunchedEffect(s.seq) { vm.sfx.play(Sfx.Sound.WIN) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (iWon) "🎉" else "🕵️", fontSize = 48.sp)
                    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = color, textAlign = TextAlign.Center)
                    Text(if (iWon) "Tu as gagné !" else "Tu as perdu cette fois…", color = Muted)
                }
            }
            item {
                Card {
                    Row {
                        Column(Modifier.weight(1f)) { Text("Mot des Civils", color = Muted, fontSize = 13.sp); Text(s.civilWord ?: "", fontWeight = FontWeight.Bold, color = Civil, fontSize = 18.sp) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Text("Mot Undercover", color = Muted, fontSize = 13.sp); Text(s.undercoverWord ?: "", fontWeight = FontWeight.Bold, color = Under, fontSize = 18.sp) }
                    }
                }
            }
            item {
                Card {
                    Text("Rôles", fontWeight = FontWeight.Bold, color = Muted)
                    s.players.forEach { p ->
                        val r = p.revealedRole
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(null, p.name, 34.dp, ring = r?.color())
                            Spacer(Modifier.width(10.dp))
                            Text(p.name + (if (!p.alive) "  ✕" else "") + if (p.id == ovm.myId) " (toi)" else "", Modifier.weight(1f))
                            Text(r?.label ?: "?", color = r?.color() ?: Muted, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { PreviousClues(s) }
            item {
                if (ovm.isHost) {
                    BigButton("🔁  Rejouer avec le même groupe") { ovm.restart() }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onLeave, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Fermer la partie") }
                } else {
                    Text("L'hôte peut relancer une partie avec le même groupe.", color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onLeave, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Quitter") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (iWon) Confetti(Modifier.fillMaxSize(), listOf(color, Amber, Teal, Blue))
    }
}
