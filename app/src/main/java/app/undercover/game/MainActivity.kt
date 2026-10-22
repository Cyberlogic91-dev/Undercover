package app.undercover.game

import android.os.Bundle
import kotlin.random.Random
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

internal val Bg = Color(0xFF0A0D1E)
internal val Surf = Color(0xB8161A30)
internal val Surf2 = Color(0xD0272C48)
internal val Amber = Color(0xFFFFC21A)
internal val Teal = Color(0xFF2ED3E6)
internal val Mint = Color(0xFF22D39A)
internal val Civil = Color(0xFF3DDC97)
internal val Under = Color(0xFFFF5A5F)
internal val White = Color(0xFFE8EAED)
internal val Muted = Color(0xFF9AA3B8)

internal fun Role.color() = when (this) { Role.CIVIL -> Civil; Role.UNDERCOVER -> Under; Role.MRWHITE -> White }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Amber, onPrimary = Color(0xFF1A1200), background = Bg, surface = Surf,
                    surfaceVariant = Surf2, onSurface = White, onBackground = White, error = Under
                ),
                typography = appTypography()
            ) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App(vm: GameViewModel = viewModel()) {
    var confirmQuit by remember { mutableStateOf(false) }
    val inGame = vm.screen !in setOf(Screen.PODIUM, Screen.THANKS, Screen.TUTORIAL, Screen.MENU, Screen.SETUP, Screen.RULES, Screen.SETTINGS, Screen.STATS, Screen.END)
    BackHandler(enabled = vm.screen != Screen.MENU) {
        when {
            inGame -> confirmQuit = true
            vm.screen == Screen.TUTORIAL -> vm.finishTutorial()
            vm.screen in setOf(Screen.RULES, Screen.SETTINGS, Screen.STATS, Screen.THANKS, Screen.PODIUM) -> vm.back()
            vm.screen == Screen.SETUP -> vm.goMenu()
            else -> vm.backToSetup()
        }
    }
    AuroraBackground(vm.theme)
    ThemeParticles(vm.theme)
    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        AnimatedContent(vm.screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
            when (s) {
                Screen.SETUP -> SetupScreen(vm)
                Screen.RULES -> RulesScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.STATS -> StatsScreen(vm)
                Screen.DISTRIBUTE -> DistributeScreen(vm)
                Screen.PLAY -> PlayScreen(vm) { confirmQuit = true }
                Screen.REVEAL -> RevealScreen(vm)
                Screen.MRWHITE_GUESS -> MrWhiteScreen(vm)
                Screen.END -> EndScreen(vm)
                Screen.SAVED -> SavedScreen(vm)
                Screen.MENU -> MenuScreen(vm)
                Screen.TUTORIAL -> TutorialScreen(vm)
                Screen.THANKS -> ThanksScreen(vm)
                Screen.PODIUM -> PodiumScreen(vm)
            }
        }
    }
    var intro by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    if (intro) IntroOverlay(vm.theme) { intro = false }
    if (confirmQuit) AlertDialog(
        onDismissRequest = { confirmQuit = false },
        title = { Text("Abandonner la partie ?") },
        text = { Text("La partie en cours sera perdue (les scores déjà gagnés sont conservés).") },
        confirmButton = { TextButton({ confirmQuit = false; vm.backToSetup() }) { Text("Abandonner") } },
        dismissButton = { TextButton({ confirmQuit = false }) { Text("Continuer") } }
    )
}

// ------------------------------------------------------------------ composants

@Composable
internal fun Title(text: String, sub: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, textAlign = TextAlign.Center, color = Teal)
        if (sub != null) Text(sub, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(CircleShape)
            .background(
                if (enabled) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF14D6B4), Color(0xFF3DDC6E)))
                else androidx.compose.ui.graphics.SolidColor(Surf2)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else Muted) }
}

@Composable
internal fun Card(modifier: Modifier = Modifier, border: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf)
            .then(if (border != null) Modifier.border(2.dp, border, RoundedCornerShape(20.dp)) else Modifier)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun Stepper(label: String, value: Int, max: Int, color: Color, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        IconButton({ onChange(value - 1) }, enabled = value > 0) { Icon(Icons.Default.Remove, "Moins") }
        Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        IconButton({ onChange(value + 1) }, enabled = value < max) { Icon(Icons.Default.Add, "Plus") }
    }
}

/** Appareil photo, en demandant la caméra frontale quand l'appli photo le permet. */
class SelfieContract : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
        putExtra("android.intent.extras.CAMERA_FACING", 1)
        putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
        putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
    }
}

private val avatarColors = listOf(0xFFFFB020, 0xFF3DDC97, 0xFF5AA9FF, 0xFFFF7AB6, 0xFFB38CFF, 0xFFFF8A4C, 0xFF4FD1C5)

@Composable
internal fun Avatar(img: ImageBitmap?, name: String, size: Dp, ring: Color? = null, dim: Boolean = false) {
    val bg = Color(avatarColors[(name.trim().lowercase().hashCode() and 0x7fffffff) % avatarColors.size])
    Box(
        Modifier.size(size)
            .then(if (ring != null) Modifier.border(size / 22 + 2.dp, ring, CircleShape).padding(size / 22 + 4.dp) else Modifier)
            .clip(CircleShape).background(if (dim) Surf2 else bg),
        contentAlignment = Alignment.Center
    ) {
        if (img != null) Image(
            img, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            colorFilter = if (dim) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
            alpha = if (dim) .6f else 1f
        ) else Text(
            name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" },
            fontSize = (size.value * .38f).sp, fontWeight = FontWeight.Black,
            color = if (dim) Muted else Color(0xFF111111)
        )
    }
}

// ------------------------------------------------------------------ écrans

@Composable
private fun SetupScreen(vm: GameViewModel) {
    val ctx = LocalContext.current
    val camera = rememberLauncherForActivityResult(SelfieContract()) { vm.onCameraResult(it) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setPhoto(vm.pendingSlot, uri)
    }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var showGroups by remember { mutableStateOf(false) }
    if (showGroups) GroupsDialog(vm) { showGroups = false }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(vm::goMenu) { Icon(Icons.Default.ArrowBack, "Menu") }
                Text(if (vm.spicyGame) "Partie coquine 🔥" else "Nouvelle partie", fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (vm.spicyGame) Under else Teal,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton({ showGroups = true }) { Icon(Icons.Default.Groups, "Groupes", tint = Amber) }
            }
            val catLabel = when {
                vm.spicyGame -> "🔥 partie coquine"
                vm.chosenCategories.size == vm.visibleCategories.size -> "toutes catégories"
                else -> "${vm.chosenCategories.size} catégorie(s)"
            }
            val timerLabel = if (vm.timerSeconds == 0) "sans chrono" else "chrono ${vm.timerSeconds / 60}:%02d".format(vm.timerSeconds % 60)
            Text("${vm.difficulty.label} · $catLabel · $timerLabel" + (if (vm.bodyguardOn) " · 🛡️" else "") + "  ✎", color = Muted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.open(Screen.SETTINGS) }.padding(vertical = 6.dp), textAlign = TextAlign.Center)
        }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rôles", fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.weight(1f))
                    if (vm.preset != RolePreset.MANUAL) Text("⚡ Auto · ${vm.names.size} joueurs", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RolePreset.entries.forEach { pr ->
                        val sel = vm.preset == pr
                        Column(
                            Modifier.width(112.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (sel) Teal.copy(alpha = .18f) else Surf2)
                                .border(if (sel) 2.dp else 1.dp, if (sel) Teal else Color.Transparent, RoundedCornerShape(16.dp))
                                .clickable { vm.choosePreset(pr) }.padding(10.dp)
                        ) {
                            Text(pr.emoji, fontSize = 22.sp)
                            Text(pr.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (sel) Teal else White)
                            Text(if (pr == RolePreset.MANUAL) "À ta façon" else vm.presetPreview(pr), fontSize = 11.sp, color = Muted, lineHeight = 14.sp)
                        }
                    }
                }
                Text(vm.preset.desc, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                val civils = vm.names.size - vm.undercovers - vm.mrWhites
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Civil))
                    Spacer(Modifier.width(10.dp))
                    Text("Civils", Modifier.weight(1f), fontSize = 16.sp)
                    Text("$civils", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 48.dp))
                }
                Stepper("Undercover", vm.undercovers, vm.maxImpostors, Under, vm::changeUndercovers)
                Stepper("Mr. White", vm.mrWhites, vm.maxImpostors, White, vm::changeMrWhites)
            }
        }
        item {
            Text("Joueurs (${vm.names.size})", fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.padding(top = 6.dp))
            Text("Touche un rond pour ajouter la photo du joueur", color = Muted, fontSize = 12.sp)
        }
        itemsIndexed(vm.slots, key = { _, s -> s.id }) { i, slot ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(Modifier.clip(CircleShape).clickable { menuFor = slot.id }) {
                        Avatar(vm.avatarOf(slot.id), slot.name, 54.dp)
                        if (vm.avatarOf(slot.id) == null) Box(
                            Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(Surf2),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.PhotoCamera, null, Modifier.size(14.dp), tint = White) }
                    }
                    DropdownMenu(expanded = menuFor == slot.id, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text("Prendre une photo") },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                            onClick = {
                                menuFor = null
                                try { camera.launch(vm.prepareCamera(slot.id)) }
                                catch (e: Exception) { Toast.makeText(ctx, "Aucune appli photo disponible", Toast.LENGTH_SHORT).show() }
                            })
                        DropdownMenuItem(
                            text = { Text("Choisir dans la galerie") },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                            onClick = {
                                menuFor = null; vm.selectSlot(slot.id)
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        if (vm.avatarOf(slot.id) != null) DropdownMenuItem(
                            text = { Text("Retirer la photo") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuFor = null; vm.removePhoto(slot.id) })
                    }
                }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = slot.name, onValueChange = { vm.renamePlayer(i, it) }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done)
                )
                IconButton({ vm.removePlayer(i) }, enabled = vm.names.size > 3) { Icon(Icons.Default.Close, "Retirer") }
            }
        }
        item {
            OutlinedButton(vm::addPlayer, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), enabled = vm.names.size < 20) {
                Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Ajouter un joueur")
            }
        }
        item {
            val dup = vm.names.map { it.trim().lowercase() }.toSet().size != vm.names.size
            val err = when {
                vm.names.any { it.isBlank() } -> "Chaque joueur doit avoir un nom."
                dup -> "Deux joueurs ont le même nom."
                vm.undercovers + vm.mrWhites == 0 -> "Il faut au moins un Undercover ou un Mr. White."
                else -> null
            }
            if (err != null) Text(err, color = Under, modifier = Modifier.padding(vertical = 4.dp))
            Spacer(Modifier.height(6.dp))
            BigButton("Lancer la partie", vm.canStart) { vm.startGame() }
            Spacer(Modifier.height(8.dp))
            TextButton({ vm.open(Screen.RULES) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(6.dp)); Text("Règles du jeu") }
        }
        if (vm.scores.isNotEmpty()) item { Scoreboard(vm) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun Scoreboard(vm: GameViewModel) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Classement", fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.weight(1f))
            TextButton(vm::resetScores) { Text("Remettre à zéro", fontSize = 13.sp) }
        }
        vm.scores.entries.sortedByDescending { it.value }.forEachIndexed { i, (n, s) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == 0 && s > 0) "🏆" else "${i + 1}.", modifier = Modifier.width(32.dp))
                Avatar(vm.avatarOfName(n), n, 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(n, Modifier.weight(1f))
                Text("$s pts", fontWeight = FontWeight.Bold, color = Amber)
            }
        }
    }
}

@Composable
private fun RulesScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Title("RÈGLES")
        val sections = listOf(
            "Le principe" to "Chaque joueur reçoit secrètement un mot. Les Civils ont tous le même mot. Les Undercover ont un mot proche, mais différent — et ils ne savent pas qu'ils sont Undercover ! Mr. White, lui, n'a aucun mot et sait qu'il est Mr. White.",
            "Un tour" to "Dans l'ordre affiché, chaque joueur donne un seul indice (un mot ou une courte phrase) sur son mot, sans le dire. Ensuite, tout le monde débat puis vote en même temps en pointant un joueur du doigt. Le plus désigné est éliminé : seul son rôle est révélé, jamais son mot. Mr. White ne parle jamais en premier.",
            "Mr. White" to "S'il est éliminé, Mr. White a une dernière chance : s'il devine le mot des Civils, il gagne la partie seul.",
            "Victoire" to "Les Civils gagnent s'ils éliminent tous les Undercover et Mr. White. Les imposteurs gagnent s'il ne reste plus qu'un seul Civil en jeu.",
            "Égalité" to "En cas d'égalité, les joueurs concernés redonnent un indice, puis on revote uniquement entre eux. Si l'égalité persiste, l'appli tire au sort.",
            "Garde du corps 🛡️ (option)" to "Un Civil reçoit en plus ce rôle secret. Une seule fois dans la partie, quand le groupe veut éliminer quelqu'un, il peut se dévoiler pour le sauver (mais pas lui-même). Le vote est annulé et le joueur sauvé est intouchable au tour suivant. Attention : un imposteur peut bluffer et prétendre être le Garde du corps… l'appli démasquera le menteur !",
            "Mot inconnu ?" to "Touche le petit « i » sous ton mot secret pour lire sa définition. Elle s'affiche discrètement, seulement pour toi.",
            "Mode adulte 🔥 (option)" to "Réservé aux soirées entre adultes. Active-le dans les Réglages après confirmation que tout le monde a 18 ans ou plus : les catégories « Coquin » et « Soirée et apéro » apparaissent, ainsi qu'un bouton « Partie coquine » dans le menu.",
            "Points" to "Civils vainqueurs : 2 pts chacun.\nUndercover vainqueur : 10 pts.\nMr. White vainqueur : 6 pts.",
            "Astuce" to "Un indice trop précis aide l'Undercover et Mr. White ; trop vague, il te rend suspect. Tout est dans le dosage !"
        )
        sections.forEach { (t, b) ->
            Card(Modifier.padding(bottom = 10.dp)) {
                Text(t, fontWeight = FontWeight.Bold, color = Amber, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text(b, lineHeight = 21.sp)
            }
        }
        OutlinedButton({ vm.open(Screen.TUTORIAL) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("▶  Revoir le tutoriel illustré")
        }
        Spacer(Modifier.height(10.dp))
        BigButton("Compris !") { vm.back() }
        Text("Développé par Mickaël Monestier", color = Muted.copy(alpha = .7f), fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun DistributeScreen(vm: GameViewModel) {
    val p = vm.players[vm.distribIndex]
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Bandeau du haut, légèrement incliné
            Box(
                Modifier.fillMaxWidth().background(Color(0xF0262A40), SlantShape).padding(top = 18.dp, bottom = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.name, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Teal, textAlign = TextAlign.Center)
                    Text("Choisis une carte  ·  ${vm.distribIndex + 1}/${vm.players.size}", fontSize = 18.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoPill("Undercover", "${vm.undercovers}", Under, Modifier.weight(1f))
                InfoPill("Mr. White", "${vm.mrWhites}", White, Modifier.weight(1f))
                InfoPill("Rôle spécial", if (vm.bodyguardOn) "🛡️" else "Aucun", Teal, Modifier.weight(1f))
            }
            val cols = if (vm.deck.size <= 6) 3 else if (vm.deck.size <= 12) 3 else 4
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                vm.deck.indices.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { i ->
                            val owner = vm.cardOwners[i]?.let { id -> vm.players.firstOrNull { it.id == id } }
                            Box(Modifier.weight(1f).aspectRatio(.78f), contentAlignment = Alignment.Center) {
                                if (owner != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Avatar(vm.avatarOf(owner.id), owner.name, 84.dp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(owner.name, Modifier.clip(RoundedCornerShape(6.dp)).background(Surf2).padding(horizontal = 10.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                } else GameCardBack(Modifier.fillMaxSize(), enabled = !vm.revealed) { vm.pickCard(i) }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        // Fenêtre du mot secret, par-dessus la grille
        if (vm.revealed) Box(
            Modifier.fillMaxSize().background(Color(0xCC05060F)).clickable(enabled = false) {}.padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xF2141830))
                    .border(2.dp, Color.White, RoundedCornerShape(26.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(vm.avatarOf(p.id), p.name, 110.dp, ring = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(p.name, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Teal)
                Text(if (p.word == null) "Tu n'as pas de mot secret" else "Voici ton mot secret", color = Muted)
                Spacer(Modifier.height(14.dp))
                FlipCard(vm.distribIndex, back = { GameCardBack(Modifier.fillMaxWidth().height(180.dp), enabled = false) {} }) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 180.dp).clip(RoundedCornerShape(20.dp))
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x66B8C4E0), Color(0x8840E0E8)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                            Text(
                                if (p.word == null) "Tu es Mr. White" else p.word,
                                fontSize = if ((p.word?.length ?: 14) > 14) 26.sp else 34.sp, fontWeight = FontWeight.Black,
                                color = Color(0xFF0A0D1E), textAlign = TextAlign.Center
                            )
                            if (p.word != null) { Spacer(Modifier.height(6.dp)); WordInfo(p.word, dark = true) }
                        }
                    }
                }
                if (p.word == null) Text("Écoute bien les autres et bluffe !", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                if (p.bodyguard) Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Teal.copy(alpha = .15f))
                        .border(1.dp, Teal, RoundedCornerShape(14.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛡️", fontSize = 26.sp); Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Tu es aussi le Garde du corps", fontWeight = FontWeight.Bold, color = Teal)
                        Text("Une fois dans la partie, après un vote, dévoile-toi pour sauver le joueur visé (pas toi-même).", fontSize = 12.sp, color = White)
                    }
                }
                Spacer(Modifier.height(18.dp))
                val last = vm.distribIndex == vm.players.lastIndex
                BigButton(if (last) "OK, on commence !" else "OK") { vm.nextDistribution() }
            }
        }
    }
}

/** Forme du bandeau : bas légèrement en biais. */
private val SlantShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height * .82f); lineTo(0f, size.height); close()
}

@Composable
private fun InfoPill(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x99585C6E)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color(0xFFE6E8F0))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
internal fun WordContent(p: Player) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        if (p.word == null) {
            Text("Tu es", color = Muted)
            Text("MR. WHITE", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("Tu n'as pas de mot. Écoute les autres et bluffe !", color = Muted, textAlign = TextAlign.Center)
        } else {
            Text("Ton mot est", color = Muted)
            Text(p.word, fontSize = 36.sp, fontWeight = FontWeight.Black, color = Amber, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp)); WordInfo(p.word)
        }
    }
}

@Composable
private fun PlayScreen(vm: GameViewModel, onQuit: () -> Unit) {
    var toEliminate by remember { mutableStateOf<Player?>(null) }
    var peek by remember { mutableStateOf<Player?>(null) }
    var peekShown by remember { mutableStateOf(false) }
    var tieDialog by remember { mutableStateOf(false) }
    var guardTarget by remember { mutableStateOf<Player?>(null) }
    var fakeGuard by remember { mutableStateOf<String?>(null) }
    fakeGuard?.let { n ->
        AlertDialog(
            onDismissRequest = { fakeGuard = null },
            icon = { Text("🤥", fontSize = 36.sp) },
            title = { Text("Imposture !") },
            text = { Text("$n n'est pas le Garde du corps. Le vote reprend… et ce mensonge est très suspect !") },
            confirmButton = { TextButton({ fakeGuard = null }) { Text("OK") } }
        )
    }
    val alive = vm.players.filter { it.alive }
    val dead = vm.players.filter { !it.alive }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Tour ${vm.round}", fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onQuit) { Icon(Icons.Default.Close, "Abandonner") }
            }
        }
        if (vm.timerSeconds > 0) item { TimerCard(vm) }
        val tie = vm.tied
        item {
            if (tie.isEmpty()) Card(border = Amber) {
                Text("Ordre de parole", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text("Chacun donne UN indice dans l'ordre ci-dessous, puis débattez et votez. Touchez le joueur qui a le plus de voix.", color = Muted, lineHeight = 20.sp)
            } else Card(border = Under) {
                Text("Égalité !", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Under)
                Spacer(Modifier.height(4.dp))
                Text("Les joueurs à égalité redonnent chacun un indice, puis tout le monde revote uniquement entre eux.", color = Muted, lineHeight = 20.sp)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(vm::cancelTie) { Text("Annuler") }
                    TextButton(vm::drawTie) { Icon(Icons.Default.Casino, null); Spacer(Modifier.width(6.dp)); Text("Encore égalité : tirage au sort") }
                }
            }
        }
        item { Text("En jeu (${alive.size})", color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        itemsIndexed(vm.speakingOrder(), key = { _, p -> p.id }) { i, p ->
            val shielded = p.id == vm.protectedId
            val selectable = (tie.isEmpty() || p.id in tie) && !shielded
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (p.id in tie) Under.copy(alpha = .15f) else Surf)
                    .clickable(enabled = selectable) { toEliminate = p }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (i == 0) Amber else Surf2), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (i == 0) Color(0xFF111111) else White)
                }
                Spacer(Modifier.width(10.dp))
                Avatar(vm.avatarOf(p.id), p.name, 46.dp, ring = if (i == 0) Amber else null, dim = !selectable)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name + if (p.bodyguard && vm.bodyguardUsed) "  🛡️" else "", fontSize = 17.sp, color = if (selectable) White else Muted)
                    if (shielded) Text("Protégé ce tour-ci", fontSize = 12.sp, color = Teal)
                }
                if (shielded) Icon(Icons.Default.Shield, "Protégé", tint = Teal)
                IconButton({ peek = p; peekShown = false }) { Icon(Icons.Outlined.Visibility, "Revoir mon mot", tint = Muted) }
            }
        }
        if (tie.isEmpty() && alive.size > 2) item {
            OutlinedButton({ tieDialog = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Balance, null); Spacer(Modifier.width(8.dp)); Text("Égalité au vote")
            }
        }
        if (dead.isNotEmpty()) {
            item { Text("Éliminés", color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(dead, key = { -it.id }) { p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(vm.avatarOf(p.id), p.name, 34.dp, dim = true)
                    Spacer(Modifier.width(12.dp))
                    Text(p.name, Modifier.weight(1f), color = Muted)
                    Text(p.role.label, color = p.role.color(), fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    toEliminate?.let { t ->
        AlertDialog(
            onDismissRequest = { toEliminate = null },
            icon = { Avatar(vm.avatarOf(t.id), t.name, 72.dp) },
            title = { Text("Éliminer ${t.name} ?") },
            text = { Text("Le groupe a voté contre ${t.name}. Son rôle va être révélé.") },
            confirmButton = { TextButton({ toEliminate = null; vm.eliminate(t.id) }) { Text("Éliminer", color = Under) } },
            dismissButton = {
                Row {
                    if (vm.bodyguardOn && !vm.bodyguardUsed) TextButton({ guardTarget = t; toEliminate = null }) { Text("🛡️ Garde du corps", color = Teal) }
                    TextButton({ toEliminate = null }) { Text("Annuler") }
                }
            }
        )
    }
    guardTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { guardTarget = null },
            icon = { Icon(Icons.Default.Shield, null, tint = Teal) },
            title = { Text("Qui est le Garde du corps ?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Le Garde du corps se dévoile pour sauver ${t.name}. Touchez son nom :", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    alive.filter { it.id != t.id }.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                guardTarget = null
                                if (!vm.bodyguardSave(c.id, t.id)) fakeGuard = c.name
                            }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(vm.avatarOf(c.id), c.name, 36.dp); Spacer(Modifier.width(10.dp)); Text(c.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ guardTarget = null }) { Text("Annuler") } }
        )
    }
    if (tieDialog) {
        var sel by remember { mutableStateOf(setOf<Long>()) }
        AlertDialog(
            onDismissRequest = { tieDialog = false },
            title = { Text("Qui est à égalité ?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    alive.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { sel = if (p.id in sel) sel - p.id else sel + p.id }.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(p.id in sel, { sel = if (p.id in sel) sel - p.id else sel + p.id })
                            Avatar(vm.avatarOf(p.id), p.name, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(p.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ vm.declareTie(sel); tieDialog = false }, enabled = sel.size >= 2) { Text("Valider") } },
            dismissButton = { TextButton({ tieDialog = false }) { Text("Annuler") } }
        )
    }
    peek?.let { p ->
        AlertDialog(
            onDismissRequest = { peek = null },
            icon = { Avatar(vm.avatarOf(p.id), p.name, 56.dp) },
            title = { Text(if (peekShown) p.name else "Revoir le mot de ${p.name}") },
            text = {
                if (peekShown) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { WordContent(p) }
                else Text("Seul(e) ${p.name} doit regarder l'écran.")
            },
            confirmButton = {
                if (peekShown) TextButton({ peek = null }) { Text("Cacher") }
                else TextButton({ peekShown = true }) { Text("Afficher") }
            },
            dismissButton = { if (!peekShown) TextButton({ peek = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun RevealScreen(vm: GameViewModel) {
    val p = vm.eliminated ?: return
    val scope = rememberCoroutineScope()
    // phase : 0 = vote annoncé, 1 = roulette des rôles, 2 = révélation
    var phase by remember(p.id) { mutableIntStateOf(if (vm.revealDone) 2 else 0) }
    var shown by remember(p.id) { mutableStateOf(if (vm.revealDone) p.role else Role.CIVIL) }
    val avatarScale = remember(p.id) { androidx.compose.animation.core.Animatable(if (vm.revealDone) 1f else .3f) }
    val cardScale = remember(p.id) { androidx.compose.animation.core.Animatable(if (vm.revealDone) 1f else 0f) }
    val flash = remember(p.id) { androidx.compose.animation.core.Animatable(0f) }
    val shake = remember(p.id) { androidx.compose.animation.core.Animatable(0f) }
    var job by remember(p.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // battement de cœur pendant le suspense
    val beat = rememberInfiniteTransition(label = "beat")
    val pulse by beat.animateFloat(1f, 1.07f, infiniteRepeatable(
        keyframes { durationMillis = 900; 1f at 0; 1.07f at 120; 1f at 260; 1.05f at 380; 1f at 560 }), label = "pulse")

    suspend fun finale() {
        shown = p.role; phase = 2
        val caught = p.role != Role.CIVIL
        vm.narrator.say(when (p.role) {
            Role.CIVIL -> "Un civil ! Oups, c'était un innocent."
            Role.UNDERCOVER -> "Undercover ! Imposteur démasqué !"
            Role.MRWHITE -> "Mister White ! Il est démasqué."
        })
        vm.sfx.play(if (caught) Sfx.Sound.CAUGHT else Sfx.Sound.FAIL)
        vm.sfx.vibrate(if (caught) longArrayOf(60, 50, 60, 50, 300) else longArrayOf(400))
        scope.launch { flash.snapTo(.85f); flash.animateTo(0f, tween(700)) }
        scope.launch { if (!caught) { repeat(4) { shake.animateTo(14f, tween(45)); shake.animateTo(-14f, tween(45)) }; shake.animateTo(0f, tween(45)) } }
        cardScale.snapTo(.4f)
        cardScale.animateTo(1f, spring(dampingRatio = .38f, stiffness = 260f))
        vm.markRevealDone()
    }

    LaunchedEffect(p.id) {
        if (vm.revealDone) return@LaunchedEffect
        job = launch {
            vm.narrator.say("Le groupe a voté contre ${p.name}. Son rôle est…")
            avatarScale.animateTo(1f, spring(dampingRatio = .55f, stiffness = 120f))
            kotlinx.coroutines.delay(500)
            vm.sfx.play(Sfx.Sound.DRUM)
            phase = 1
            cardScale.animateTo(1f, tween(250))
            // la roulette ralentit petit à petit… et s'arrête sur le vrai rôle
            val roles = Role.entries
            var idx = Random.nextInt(roles.size)
            var d = 70L
            while (d < 430) {
                idx = (idx + 1) % roles.size; shown = roles[idx]
                vm.sfx.play(Sfx.Sound.TAP); vm.sfx.vibrate(longArrayOf(12))
                kotlinx.coroutines.delay(d); d = (d * 1.17).toLong()
            }
            kotlinx.coroutines.delay(350)
            finale()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).graphicsLayer { translationX = shake.value * density },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Text(if (phase == 0) "Le groupe a voté contre…" else "Éliminé(e)", color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.graphicsLayer {
                val s = avatarScale.value * (if (phase < 2) pulse else 1f); scaleX = s; scaleY = s
            }) {
                Avatar(vm.avatarOf(p.id), p.name, 150.dp, ring = if (phase == 2) p.role.color() else Color.White, dim = phase == 2 && p.role == Role.CIVIL)
            }
            Spacer(Modifier.height(14.dp))
            Text(p.name, fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))

            // carte du rôle (roulette puis révélation)
            Box(
                Modifier.fillMaxWidth().height(150.dp).graphicsLayer { scaleX = cardScale.value; scaleY = cardScale.value; alpha = cardScale.value.coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (phase == 2) shown.color().copy(alpha = .18f) else Surf)
                    .border(3.dp, shown.color(), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (phase == 2) "C'était" else "Son rôle est…", color = Muted)
                    Text(
                        shown.label.uppercase(), fontSize = if (phase == 2) 36.sp else 30.sp, fontWeight = FontWeight.Black,
                        color = shown.color().copy(alpha = if (phase == 2) 1f else .75f)
                    )
                }
            }
            if (phase == 2) {
                Spacer(Modifier.height(14.dp))
                Text(
                    when (p.role) {
                        Role.CIVIL -> "Oups… c'était un innocent !"
                        Role.UNDERCOVER -> "Imposteur démasqué ! 🎯"
                        Role.MRWHITE -> "Mr. White est démasqué !"
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.role.color(), textAlign = TextAlign.Center
                )
                if (p.role == Role.MRWHITE && vm.mrWhiteGuessed) Text("A proposé « ${vm.lastGuess} » — raté !", color = Muted,
                    modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))
            when {
                phase < 2 -> TextButton({ job?.cancel(); scope.launch { finale() } }, Modifier.fillMaxWidth()) { Text("Passer le suspense", color = Muted) }
                p.role == Role.MRWHITE && !vm.mrWhiteGuessed -> BigButton("Dernière chance de Mr. White") { vm.openMrWhiteGuess() }
                else -> BigButton(if (vm.gameWillEnd()) "Voir le résultat" else "Tour suivant") { vm.afterReveal() }
            }
        }
        // éclair de couleur au moment de la révélation
        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(p.role.color().copy(alpha = flash.value * .6f)))
        if (phase == 2 && p.role != Role.CIVIL) Confetti(Modifier.fillMaxSize(), listOf(p.role.color(), Amber, Teal))
    }
}

@Composable
private fun MrWhiteScreen(vm: GameViewModel) {
    val p = vm.eliminated ?: return
    var guess by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Avatar(vm.avatarOf(p.id), p.name, 130.dp, ring = White)
        Spacer(Modifier.height(16.dp))
        Text(p.name, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("était MR. WHITE !", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Dernière chance : s'il devine le mot des Civils, il gagne la partie.", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            guess, { guess = it }, singleLine = true, label = { Text("Le mot des Civils est…") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(Modifier.weight(1f))
        BigButton("Valider", guess.isNotBlank()) { vm.mrWhiteGuess(guess) }
    }
}

@Composable
private fun EndScreen(vm: GameViewModel) {
    val (title, color) = when (vm.outcome) {
        Outcome.CIVILS -> "Les Civils gagnent !" to Civil
        Outcome.IMPOSTORS -> "Les imposteurs gagnent !" to Under
        Outcome.MRWHITE -> "Mr. White a trouvé le mot !" to White
        null -> "" to Amber
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (vm.outcome == Outcome.CIVILS) "🎉" else "🕵️", fontSize = 48.sp)
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = color, textAlign = TextAlign.Center)
            }
        }
        item {
            Card {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Mot des Civils", color = Muted, fontSize = 13.sp)
                        Text(vm.civilWord, fontWeight = FontWeight.Bold, color = Civil, fontSize = 18.sp)
                        Words.definition(vm.civilWord)?.let { Text(it, color = Muted, fontSize = 12.sp, lineHeight = 15.sp) }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Mot Undercover", color = Muted, fontSize = 13.sp)
                        Text(vm.undercoverWord, fontWeight = FontWeight.Bold, color = Under, fontSize = 18.sp)
                        Words.definition(vm.undercoverWord)?.let { Text(it, color = Muted, fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.End) }
                    }
                }
            }
        }
        item {
            Card {
                Text("Rôles", fontWeight = FontWeight.Bold, color = Muted)
                vm.players.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(vm.avatarOf(p.id), p.name, 38.dp, ring = p.role.color())
                        Spacer(Modifier.width(10.dp))
                        Text(p.name + (if (p.bodyguard) "  🛡️" else "") + if (!p.alive) "  ✕" else "", Modifier.weight(1f), color = if (p.alive) White else Muted)
                        Text(p.role.label, color = p.role.color(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Scoreboard(vm) }
        item {
            val ctx = LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ ShareImage.share(ctx, ShareImage.gameResult(ctx, vm), "partie", "🕵️ Résultat de notre partie d'Undercover !") },
                    Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("📤 Partager") }
                OutlinedButton({ vm.openPodium() }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("🏆 Podium") }
            }
            Spacer(Modifier.height(10.dp))
            BigButton("Rejouer") { vm.startGame() }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(vm::backToSetup, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Modifier les joueurs")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
        NewBadgesDialog(vm)
        Confetti(Modifier.fillMaxSize(), when (vm.outcome) {
            Outcome.CIVILS -> listOf(Civil, Amber, White, Color(0xFF5AA9FF))
            Outcome.MRWHITE -> listOf(White, Muted, Amber)
            else -> listOf(Under, Amber, White, Color(0xFFB38CFF))
        })
    }
}


@Composable
private fun SavedScreen(vm: GameViewModel) {
    val (g, t) = vm.lastSave ?: return
    val pop = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .45f, stiffness = 260f)) }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text("🛡️", fontSize = 72.sp, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        Text("SAUVÉ !", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Teal)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(vm.avatarOf(g.id), g.name, 96.dp, ring = Teal)
                Text(g.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text("Garde du corps", color = Teal, fontSize = 12.sp)
            }
            Icon(Icons.Default.Shield, null, tint = Teal, modifier = Modifier.padding(horizontal = 16.dp).size(36.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(vm.avatarOf(t.id), t.name, 96.dp, ring = White)
                Text(t.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text("Protégé", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Card {
            Text("${g.name} était bien le Garde du corps. Le vote est annulé et ${t.name} ne peut pas être éliminé au prochain tour.",
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), lineHeight = 21.sp)
        }
        Spacer(Modifier.weight(1f))
        BigButton("Tour suivant") { vm.afterSave() }
    }
}
