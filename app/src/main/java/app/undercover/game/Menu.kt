package app.undercover.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Logo texte : UNDER en blanc, COVER en turquoise. */
@Composable
internal fun Wordmark(size: Int = 40) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White)) { append("UNDER") }
            withStyle(SpanStyle(color = Teal)) { append("COVER") }
        },
        fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
    )
}

// ================================================================ Menu principal

@Composable
internal fun MenuScreen(vm: GameViewModel) {
    var showGroups by remember { mutableStateOf(false) }
    if (showGroups) GroupsDialog(vm) { showGroups = false }
    LaunchedEffect(Unit) { vm.autoCheckUpdate() }
    UpdateDialog(vm)

    val float = rememberInfiniteTransition(label = "float")
    val dy by float.animateFloat(-6f, 6f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
    val tilt by float.animateFloat(-3f, 3f, infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
    val pulse by float.animateFloat(1f, 1.035f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Box(contentAlignment = Alignment.Center) {
            // halo lumineux derrière le logo
            Box(Modifier.size(190.dp).clip(CircleShape).background(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(Teal.copy(alpha = .28f), Color.Transparent))
            ))
            Image(
                painterResource(R.drawable.ic_logo), "Logo Undercover",
                Modifier.size(180.dp).graphicsLayer { translationY = dy * density; rotationZ = tilt }
            )
        }
        Wordmark(42)
        Text("Démasque l'imposteur… ou bluffe jusqu'au bout", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        when (vm.theme) {
            SeasonTheme.HALLOWEEN -> "🎃 Joyeux Halloween ! 🦇"
            SeasonTheme.CHRISTMAS -> "🎄 Joyeuses fêtes ! ❄️"
            SeasonTheme.SUMMER -> "☀️ Bon été ! 🏖️"
            else -> null
        }?.let { Text(it, color = vm.theme.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(26.dp))

        Box(Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }) {
            BigButton("▶  JOUER") { vm.startNormalSetup() }
        }
        if (vm.adultMode) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFE5245E), Color(0xFFFF6A3D))))
                    .clickable { vm.startSpicySetup() },
                contentAlignment = Alignment.Center
            ) { Text("🔥  PARTIE COQUINE  🔞", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color.White) }
        }
        if (vm.eveningGames > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Amber.copy(alpha = .15f))
                    .border(1.5.dp, Amber, RoundedCornerShape(18.dp)).clickable { vm.openPodium() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏆", fontSize = 26.sp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Championnat de la soirée", fontWeight = FontWeight.Bold, color = Amber)
                    val lead = vm.eveningRanking().firstOrNull()
                    Text("${vm.eveningGames} partie(s)" + (lead?.let { " · en tête : ${it.first} (${it.second} pts)" } ?: ""), fontSize = 12.sp, color = White)
                }
                Text("›", fontSize = 26.sp, color = Amber)
            }
        }
        Text("${vm.slots.size} joueurs prêts · ${vm.totalFresh} paires de mots inédites", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(22.dp))

        val catLabel = if (vm.chosenCategories.size == vm.visibleCategories.size) "Toutes catégories" else "${vm.chosenCategories.size} catégories"
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuTile(Icons.Default.Groups, "Groupes",
                if (vm.groups.isEmpty()) "Enregistre ta bande" else "${vm.groups.size} groupe(s)",
                Color(0xFFB38CFF), Modifier.weight(1f)) { showGroups = true }
            MenuTile(Icons.Default.EmojiEvents, "Statistiques",
                if (vm.history.isEmpty()) "Aucune partie" else "${vm.history.size} partie(s)",
                Amber, Modifier.weight(1f)) { vm.open(Screen.STATS) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuTile(Icons.Default.Tune, "Réglages", "${vm.difficulty.label} · $catLabel",
                Mint, Modifier.weight(1f)) { vm.open(Screen.SETTINGS) }
            MenuTile(Icons.Default.MenuBook, "Règles", "Comment jouer",
                Color(0xFF5AA9FF), Modifier.weight(1f)) { vm.open(Screen.RULES) }
        }
        if (vm.bodyguardOn) {
            Spacer(Modifier.height(14.dp))
            Text("🛡️ Rôle spécial actif : Garde du corps", color = Teal, fontSize = 13.sp)
        }
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.clip(CircleShape).background(Surf).clickable { vm.open(Screen.THANKS) }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("❤️", fontSize = 14.sp); Spacer(Modifier.width(8.dp))
            Text("Remerciements", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text("Développé par CYBERLOGIC", color = Muted.copy(alpha = .7f), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MenuTile(icon: ImageVector, title: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Surf)
            .border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(sub, color = Muted, fontSize = 12.sp, maxLines = 1)
    }
}

// ================================================================ Intro animée

@Composable
internal fun IntroOverlay(theme: SeasonTheme, onDone: () -> Unit) {
    val logoScale = remember { Animatable(.2f) }
    val logoRot = remember { Animatable(-25f) }
    val textAlpha = remember { Animatable(0f) }
    val tagAlpha = remember { Animatable(0f) }
    val all = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = .5f, stiffness = 180f)) }
        launch { logoRot.animateTo(0f, spring(dampingRatio = .55f, stiffness = 140f)) }
        delay(450); textAlpha.animateTo(1f, tween(450))
        tagAlpha.animateTo(1f, tween(350))
        delay(700); all.animateTo(0f, tween(350))
        onDone()
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = all.value }.background(Bg).clickable { onDone() },
        contentAlignment = Alignment.Center
    ) {
        AuroraBackground(theme)
        ThemeParticles(theme)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.ic_logo), null,
                Modifier.size(210.dp).graphicsLayer { scaleX = logoScale.value; scaleY = logoScale.value; rotationZ = logoRot.value }
            )
            Box(Modifier.graphicsLayer { alpha = textAlpha.value; translationY = (1 - textAlpha.value) * 30f }) { Wordmark(44) }
            Text("Qui est l'imposteur ?", color = Muted, fontSize = 16.sp, modifier = Modifier.graphicsLayer { alpha = tagAlpha.value })
            Spacer(Modifier.height(28.dp))
            Text("Développé par CYBERLOGIC", color = Muted.copy(alpha = .7f), fontSize = 12.sp, modifier = Modifier.graphicsLayer { alpha = tagAlpha.value })
        }
    }
}
