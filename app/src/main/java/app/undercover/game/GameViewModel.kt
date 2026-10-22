package app.undercover.game

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer

enum class Role(val label: String) { CIVIL("Civil"), UNDERCOVER("Undercover"), MRWHITE("Mr. White") }

/** Emplacement de joueur dans la configuration (id stable pour lier la photo). */
data class Slot(val id: Long, val name: String)

data class Player(
    val id: Long, val name: String, val role: Role, val word: String?,
    val alive: Boolean = true, val bodyguard: Boolean = false, val elimRound: Int = 0
)

/** Une carte du paquet distribué. */
data class GameCard(val role: Role, val word: String?, val bodyguard: Boolean = false)

enum class Screen { PODIUM, THANKS, TUTORIAL, SAVED, MENU, SETUP, RULES, SETTINGS, STATS, DISTRIBUTE, PLAY, REVEAL, MRWHITE_GUESS, END }

enum class Outcome { CIVILS, IMPOSTORS, MRWHITE }

/** Configurations de rôles proposées automatiquement selon le nombre de joueurs. */
enum class RolePreset(val emoji: String, val label: String, val desc: String) {
    BALANCED("⚖️", "Équilibré", "La config recommandée : Undercover et Mr. White dosés selon le nombre de joueurs"),
    BEGINNER("🌱", "Débutant", "Un seul Undercover, pas de Mr. White : idéal pour découvrir"),
    CLASSIC("🎭", "Sans Mr. White", "Uniquement des Undercover, pour des parties de pur bluff"),
    CHAOS("🔥", "Chaos", "Un maximum d'imposteurs : personne n'est à l'abri"),
    MANUAL("✋", "Manuel", "Tu choisis toi-même le nombre de chaque rôle");

    /** Nombre (Undercover, Mr. White) pour [n] joueurs. */
    fun roles(n: Int): Pair<Int, Int> {
        val max = ((n - 1) / 2).coerceAtLeast(1)
        return when (this) {
            BALANCED -> {
                val total = ((n + 1) / 3).coerceIn(1, max)
                val w = when { n >= 13 -> 2; n >= 5 -> 1; else -> 0 }.coerceAtMost(total - 1)
                (total - w) to w
            }
            BEGINNER -> 1 to 0
            CLASSIC -> ((n + 1) / 3).coerceIn(1, max) to 0
            CHAOS -> {
                val total = max
                val w = if (total >= 2) (total + 1) / 3 else 0
                (total - w) to w
            }
            MANUAL -> 1 to 0
        }
    }
}

/** Une partie terminée, pour l'historique. */
data class GameRecord(
    val time: Long, val outcome: Outcome, val civilWord: String, val undercoverWord: String,
    val players: List<Triple<String, Role, Boolean>>, // nom, rôle, gagnant
    val elim: List<Int> = emptyList(),  // tour d'élimination par joueur (0 = jamais éliminé)
    val saver: String? = null,          // Garde du corps qui a sauvé quelqu'un
    val rounds: Int = 0
)

/** Statistiques d'un joueur. */
data class PlayerStats(
    val name: String, val games: Int, val wins: Int,
    val civilWins: Int, val undercoverWins: Int, val mrWhiteWins: Int,
    val undercoverGames: Int, val mrWhiteGames: Int
) { val rate get() = if (games == 0) 0 else wins * 100 / games }

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("undercover", Context.MODE_PRIVATE)
    private val avatarDir = File(app.filesDir, "avatars").apply { mkdirs() }
    private val wordsLoaded = Words.load(app)

    // ---- configuration ----
    var slots by mutableStateOf(loadSlots()); private set
    val names get() = slots.map { it.name }
    var undercovers by mutableIntStateOf(prefs.getInt("uc", 1)); private set
    var mrWhites by mutableIntStateOf(prefs.getInt("mw", 0)); private set
    var preset by mutableStateOf(runCatching { RolePreset.valueOf(prefs.getString("preset", "BALANCED")!!) }.getOrDefault(RolePreset.BALANCED)); private set

    fun choosePreset(p: RolePreset) {
        preset = p; prefs.edit().putString("preset", p.name).apply()
        applyPreset(); save()
    }
    private fun applyPreset() {
        if (preset == RolePreset.MANUAL) return
        val (u, w) = preset.roles(slots.size); undercovers = u; mrWhites = w
    }
    /** Le nombre de joueurs a changé : on recalcule les rôles en mode automatique. */
    private fun rolesChanged() { if (preset == RolePreset.MANUAL) clampRoles() else applyPreset() }
    /** Aperçu de la config proposée pour le nombre de joueurs actuel. */
    fun presetPreview(p: RolePreset): String {
        val (u, w) = p.roles(slots.size)
        return buildString {
            append("$u Undercover"); if (w > 0) append(" · $w Mr. White")
        }
    }

    /** Photos des joueurs, par id d'emplacement. */
    val avatars = mutableStateMapOf<Long, ImageBitmap>()

    // ---- réglages ----
    val sfx = Sfx(app)
    var timerSeconds by mutableIntStateOf(prefs.getInt("timer", 120)); private set
    var difficulty by mutableStateOf(runCatching { Difficulty.valueOf(prefs.getString("diff", "MIXED")!!) }.getOrDefault(Difficulty.MIXED)); private set
    // On mémorise les catégories DÉCOCHÉES : une nouvelle catégorie est donc cochée par défaut
    private var categoriesOff by mutableStateOf(prefs.getStringSet("catsOff", null)?.toSet() ?: emptySet())

    // ---- mode adulte 18+ ----
    var adultMode by mutableStateOf(prefs.getBoolean("adult", false)); private set
    /** Partie lancée depuis le bouton « mode coquin » : uniquement les catégories adultes. */
    var spicyGame by mutableStateOf(false); private set
    fun changeAdultMode(on: Boolean) {
        adultMode = on; prefs.edit().putBoolean("adult", on).apply()
        if (!on) spicyGame = false
    }
    fun startSpicySetup() { if (adultMode) { spicyGame = true; screen = Screen.SETUP } }
    fun startNormalSetup() { spicyGame = false; screen = Screen.SETUP }

    /** Catégories effectivement jouées. */
    val categories: Set<String> get() =
        if (spicyGame) Words.adultCategories
        else Words.visibleCategories(adultMode).filter { it !in categoriesOff }.toSet()
    val visibleCategories get() = Words.visibleCategories(adultMode)
    /** Sélection des réglages (hors partie coquine). */
    val chosenCategories: Set<String> get() = visibleCategories.filter { it !in categoriesOff }.toSet()
    var soundOn by mutableStateOf(prefs.getBoolean("sound", true)); private set
    var vibrationOn by mutableStateOf(prefs.getBoolean("vibe", true)); private set

    var bodyguardOn by mutableStateOf(prefs.getBoolean("guard", false)); private set

    // ---- narrateur vocal ----
    val narrator = Narrator(app)
    var narratorOn by mutableStateOf(prefs.getBoolean("narrator", false)); private set
    fun changeNarrator(on: Boolean) {
        narratorOn = on; narrator.enabled = on; prefs.edit().putBoolean("narrator", on).apply()
        if (on) narrator.say("Bonjour ! Je serai votre narrateur pour cette partie.")
    }
    val narratorReady get() = narrator.ready

    // ---- version et mises à jour (GitHub) ----
    val appVersion = Updater.currentVersion(app)
    val appCode = Updater.currentCode(app)
    var autoUpdate by mutableStateOf(prefs.getBoolean("autoUpdate", true)); private set
    fun changeAutoUpdate(on: Boolean) { autoUpdate = on; prefs.edit().putBoolean("autoUpdate", on).apply() }

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val release: Release) : UpdateState
        data class Downloading(val release: Release, val progress: Float) : UpdateState
        data class Ready(val release: Release, val file: File) : UpdateState
        data class Failed(val message: String) : UpdateState
    }
    var update by mutableStateOf<UpdateState>(UpdateState.Idle); private set
    /** Affiche la fenêtre « nouvelle version » sur le menu. */
    var showUpdateDialog by mutableStateOf(false)
    private var skippedVersion = prefs.getString("skipVersion", "") ?: ""

    fun checkUpdate(manual: Boolean) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading) return
        update = UpdateState.Checking
        viewModelScope.launch {
            val r = Updater.latest()
            prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            update = when {
                r == null -> if (manual) UpdateState.Failed("Impossible de joindre GitHub. Vérifie ta connexion.") else UpdateState.Idle
                Updater.isNewer(r.version, appVersion) -> {
                    if (manual || r.version != skippedVersion) showUpdateDialog = true
                    UpdateState.Available(r)
                }
                else -> if (manual) UpdateState.UpToDate else UpdateState.Idle
            }
        }
    }

    /** Vérification discrète au démarrage, au plus toutes les 6 heures. */
    fun autoCheckUpdate() {
        if (!autoUpdate) return
        if (System.currentTimeMillis() - prefs.getLong("lastCheck", 0) < 6 * 3600_000L) return
        checkUpdate(manual = false)
    }

    fun skipUpdate(r: Release) { skippedVersion = r.version; prefs.edit().putString("skipVersion", r.version).apply(); showUpdateDialog = false }

    fun downloadUpdate(r: Release) {
        val ctx = getApplication<Application>()
        update = UpdateState.Downloading(r, 0f)
        viewModelScope.launch {
            update = runCatching {
                val f = Updater.download(ctx, r) { p -> update = UpdateState.Downloading(r, p) }
                UpdateState.Ready(r, f)
            }.getOrElse { UpdateState.Failed(it.message ?: "Échec du téléchargement") }
            (update as? UpdateState.Ready)?.let { installUpdate() }
        }
    }

    /** Lance l'installation (demande d'abord l'autorisation si nécessaire). */
    fun installUpdate() {
        val ctx = getApplication<Application>()
        val ready = update as? UpdateState.Ready ?: return
        if (!Updater.canInstall(ctx)) { Updater.openInstallPermission(ctx); return }
        Updater.install(ctx, ready.file)
    }

    // ---- thème ----
    var themeChoice by mutableStateOf(runCatching { ThemeChoice.valueOf(prefs.getString("theme", "AUTO")!!) }.getOrDefault(ThemeChoice.AUTO)); private set
    val theme get() = themeChoice.resolve()
    fun changeTheme(t: ThemeChoice) { themeChoice = t; prefs.edit().putString("theme", t.name).apply() }
    fun changeBodyguard(on: Boolean) { bodyguardOn = on; prefs.edit().putBoolean("guard", on).apply() }

    fun changeTimer(sec: Int) { timerSeconds = sec; prefs.edit().putInt("timer", sec).apply() }
    fun changeDifficulty(d: Difficulty) { difficulty = d; prefs.edit().putString("diff", d.name).apply() }
    fun toggleCategory(c: String) {
        val off = if (c in categoriesOff) categoriesOff - c else categoriesOff + c
        if (visibleCategories.none { it !in off }) return // au moins une catégorie
        categoriesOff = off; prefs.edit().putStringSet("catsOff", off).apply()
    }
    fun allCategories(on: Boolean) {
        categoriesOff = if (on) emptySet() else Words.categories.drop(1).toSet()
        prefs.edit().putStringSet("catsOff", categoriesOff).apply()
    }
    fun changeSound(on: Boolean) { soundOn = on; sfx.soundOn = on; prefs.edit().putBoolean("sound", on).apply(); if (on) sfx.play(Sfx.Sound.TAP) }
    fun changeVibration(on: Boolean) { vibrationOn = on; sfx.vibrationOn = on; prefs.edit().putBoolean("vibe", on).apply(); if (on) sfx.vibrate(longArrayOf(60)) }
    private val pool get() = Words.filter(categories, difficulty, adultMode || spicyGame)
    val availablePairs get() = pool.size
    /** Paires de la sélection actuelle encore jamais jouées. */
    val freshPairs get() = pool.count { it.key !in playedPairs }
    val totalFresh get() = Words.pairs.count { it.key !in playedPairs && (adultMode || it.category !in Words.adultCategories) }

    // ---- anti-doublon persistant ----
    private var playedPairs by mutableStateOf(prefs.getStringSet("played", null)?.toSet() ?: emptySet())
    private fun savePlayed() = prefs.edit().putStringSet("played", playedPairs).apply()
    fun resetPlayed() { playedPairs = emptySet(); savePlayed() }
    val playedCount get() = playedPairs.size
    /** Vrai si la sélection a été épuisée et vient d'être remise à zéro. */
    var poolRecycled by mutableStateOf(false); private set

    // ---- chrono de débat ----
    var timerLeft by mutableIntStateOf(0); private set
    var timerRunning by mutableStateOf(false); private set
    private var timerJob: Job? = null

    fun startTimer() {
        if (timerSeconds == 0) return
        if (timerLeft <= 0) timerLeft = timerSeconds
        timerRunning = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (timerLeft > 0) {
                delay(1000); timerLeft--
                if (timerLeft in 1..5) sfx.play(Sfx.Sound.TAP)
            }
            timerRunning = false
            sfx.play(Sfx.Sound.ALARM); sfx.vibrate(longArrayOf(300, 150, 300, 150, 500))
            narrator.say("Temps écoulé ! À vous de voter.")
        }
    }
    fun pauseTimer() { timerJob?.cancel(); timerRunning = false }
    fun resetTimer() { timerJob?.cancel(); timerRunning = false; timerLeft = timerSeconds }

    // ---- historique ----
    var history by mutableStateOf(loadHistory()); private set

    // ---- groupes enregistrés ----
    var groups by mutableStateOf(loadGroups()); private set

    // ---- partie ----
    var screen by mutableStateOf(if (prefs.getBoolean("tutoSeen", false)) Screen.MENU else Screen.TUTORIAL)
    private var returnTo = Screen.MENU
    fun finishTutorial() { prefs.edit().putBoolean("tutoSeen", true).apply(); screen = returnTo }

    /** Ouvre un écran secondaire (règles, réglages, stats) en mémorisant d'où l'on vient. */
    fun open(s: Screen) { returnTo = screen; screen = s }
    fun back() { screen = returnTo }
    fun goMenu() { pauseTimer(); screen = Screen.MENU }
    var players by mutableStateOf(listOf<Player>()); private set
    var distribIndex by mutableIntStateOf(0); private set
    var revealed by mutableStateOf(false); private set
    var civilWord by mutableStateOf(""); private set
    var undercoverWord by mutableStateOf(""); private set
    var starter by mutableStateOf(""); private set
    var round by mutableIntStateOf(1); private set
    var eliminated by mutableStateOf<Player?>(null); private set
    var outcome by mutableStateOf<Outcome?>(null); private set
    var lastGuess by mutableStateOf(""); private set
    val scores = mutableStateMapOf<String, Int>()
    // championnat de la soirée (déclarés avant init, qui les charge)
    var eveningGames by mutableIntStateOf(prefs.getInt("eveGames", 0)); private set
    private val eveningWins = mutableStateMapOf<String, Int>()


    init {
        narrator.enabled = narratorOn
        loadEvening()
        applyPreset()
        sfx.soundOn = soundOn; sfx.vibrationOn = vibrationOn
        viewModelScope.launch(Dispatchers.IO) { sfx.load() }
        loadAvatars(slots.map { it.id })
    }

    override fun onCleared() { sfx.release(); narrator.release() }

    private fun loadAvatars(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        ids.filter { it !in avatars }.forEach { id ->
            val f = avatarFile(id)
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { b ->
                withContext(Dispatchers.Main) { avatars[id] = b.asImageBitmap() }
            }
        }
    }

    val maxImpostors get() = ((slots.size - 1) / 2).coerceAtLeast(1)
    val canStart get() = slots.size >= 3 && names.map { it.trim().lowercase() }.toSet().size == slots.size &&
            names.all { it.isNotBlank() } && undercovers + mrWhites in 1..maxImpostors

    fun avatarOf(id: Long) = avatars[id]
    fun avatarOfName(name: String) = slots.firstOrNull { it.name.trim() == name }?.let { avatars[it.id] }

    // ---- édition des joueurs ----
    private fun newId(): Long {
        val used = (slots.map { it.id } + groups.flatMap { g -> g.second.map { it.id } }).maxOrNull() ?: 0L
        val id = maxOf(prefs.getLong("nextId", 1L), used + 1)
        prefs.edit().putLong("nextId", id + 1).apply()
        return id
    }

    fun addPlayer() { slots = slots + Slot(newId(), "Joueur ${slots.size + 1}"); rolesChanged(); save() }
    fun removePlayer(i: Int) {
        if (slots.size <= 3) return
        val s = slots[i]
        // La photo est conservée si le joueur fait partie d'un groupe enregistré
        if (groups.none { g -> g.second.any { it.id == s.id } }) { avatarFile(s.id).delete(); avatars.remove(s.id) }
        slots = slots.toMutableList().also { it.removeAt(i) }
        rolesChanged(); save()
    }
    fun renamePlayer(i: Int, n: String) {
        slots = slots.toMutableList().also { it[i] = it[i].copy(name = n.replace("\n", "").replace("\t", "").take(20)) }; save()
    }
    private fun toManual() { if (preset != RolePreset.MANUAL) { preset = RolePreset.MANUAL; prefs.edit().putString("preset", "MANUAL").apply() } }
    fun changeUndercovers(v: Int) { toManual(); undercovers = v.coerceIn(0, maxImpostors); if (undercovers + mrWhites > maxImpostors) mrWhites = maxImpostors - undercovers; save() }
    fun changeMrWhites(v: Int) { toManual(); mrWhites = v.coerceIn(0, maxImpostors); if (undercovers + mrWhites > maxImpostors) undercovers = maxImpostors - mrWhites; save() }
    private fun clampRoles() {
        if (undercovers + mrWhites > maxImpostors) { mrWhites = 0; undercovers = undercovers.coerceAtMost(maxImpostors) }
        if (undercovers + mrWhites == 0) undercovers = 1
    }
    private fun save() = prefs.edit()
        .putString("slots", slots.joinToString("\n") { "${it.id}\t${it.name}" })
        .putInt("uc", undercovers).putInt("mw", mrWhites).apply()

    private fun loadSlots(): List<Slot> {
        prefs.getString("slots", null)?.let { raw ->
            val l = raw.split("\n").mapNotNull { line ->
                val p = line.split("\t", limit = 2)
                p.getOrNull(0)?.toLongOrNull()?.let { Slot(it, p.getOrElse(1) { "" }) }
            }
            if (l.size >= 3) return l
        }
        val old = prefs.getString("names", null)?.split("\n")?.filter { it.isNotBlank() }
            ?: listOf("Joueur 1", "Joueur 2", "Joueur 3", "Joueur 4")
        return old.mapIndexed { i, n -> Slot(i + 1L, n) }
    }

    // ---- photos ----
    var pendingSlot: Long = -1L; private set
    private var pendingCameraUri: Uri? = null

    private fun avatarFile(id: Long) = File(avatarDir, "$id.jpg")

    fun selectSlot(id: Long) { pendingSlot = id }

    /** Prépare un fichier temporaire pour l'appareil photo et renvoie son Uri. */
    fun prepareCamera(id: Long): Uri {
        pendingSlot = id
        val ctx = getApplication<Application>()
        val f = File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "shot_$id.jpg")
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f).also { pendingCameraUri = it }
    }

    fun onCameraResult(ok: Boolean) {
        val uri = pendingCameraUri
        if (ok && uri != null) setPhoto(pendingSlot, uri)
        pendingCameraUri = null
    }

    fun setPhoto(id: Long, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val bmp = runCatching { decodeSquare(ctx, uri, 512) }.getOrNull() ?: return@launch
        runCatching { avatarFile(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
        withContext(Dispatchers.Main) { avatars[id] = bmp.asImageBitmap() }
    }

    fun removePhoto(id: Long) { avatarFile(id).delete(); avatars.remove(id) }

    // ---- groupes ----
    private fun loadGroups(): List<Pair<String, List<Slot>>> = runCatching {
        val o = JSONObject(prefs.getString("groups", "{}")!!)
        o.keys().asSequence().map { k ->
            val a = o.getJSONArray(k)
            k to (0 until a.length()).map { i -> a.getJSONArray(i).let { Slot(it.getLong(0), it.getString(1)) } }
        }.sortedBy { it.first.lowercase() }.toList()
    }.getOrDefault(emptyList())

    private fun saveGroups() {
        val o = JSONObject()
        groups.forEach { (n, l) -> o.put(n, JSONArray(l.map { JSONArray().put(it.id).put(it.name) })) }
        prefs.edit().putString("groups", o.toString()).apply()
    }

    fun saveGroup(name: String) {
        val n = name.trim().take(30); if (n.isEmpty()) return
        groups = (groups.filter { it.first != n } + (n to slots)).sortedBy { it.first.lowercase() }
        saveGroups()
    }

    fun loadGroup(name: String) {
        val g = groups.firstOrNull { it.first == name } ?: return
        slots = g.second
        rolesChanged(); save(); loadAvatars(slots.map { it.id })
    }

    fun deleteGroup(name: String) { groups = groups.filter { it.first != name }; saveGroups() }

    private fun decodeSquare(ctx: Context, uri: Uri, size: Int): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val raw = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val side = minOf(raw.width, raw.height)
        val m = Matrix().apply {
            val scale = size.toFloat() / side
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side, m, true)
    }

    // ---- déroulement ----
    fun startGame() {
        if (!canStart) return
        val pool = this.pool
        var fresh = pool.filter { it.key !in playedPairs }
        poolRecycled = fresh.isEmpty()
        if (fresh.isEmpty()) {
            // Tous les mots de cette sélection ont été joués : on recycle uniquement cette sélection
            playedPairs = playedPairs - pool.map { it.key }.toSet(); fresh = pool
        }
        val pick = fresh.random()
        playedPairs = playedPairs + pick.key; savePlayed()
        val (a, b) = if (Math.random() < 0.5) pick.a to pick.b else pick.b to pick.a
        civilWord = a; undercoverWord = b

        // Paquet de cartes face cachée : chaque joueur en choisit une, comme dans le vrai jeu
        val civils = slots.size - undercovers - mrWhites
        deck = buildList {
            repeat(undercovers) { add(GameCard(Role.UNDERCOVER, b)) }
            repeat(mrWhites) { add(GameCard(Role.MRWHITE, null)) }
            // Garde du corps : toujours un Civil
            repeat(civils) { i -> add(GameCard(Role.CIVIL, a, bodyguard = bodyguardOn && i == 0)) }
        }.shuffled()
        takenCards = emptySet(); cardOwners = emptyMap()
        bodyguardUsed = false; protectedId = null; lastSave = null
        players = slots.map { Player(it.id, it.name.trim(), Role.CIVIL, null) }
        players.forEach { scores.putIfAbsent(it.name, 0) }
        distribIndex = 0; revealed = false; round = 1; outcome = null; eliminated = null; tied = emptySet()
        lastSave = null; newBadges = emptyList()
        screen = Screen.DISTRIBUTE
        narrator.say("Nouvelle partie ! ${players[0].name}, choisis une carte.")
    }

    var deck by mutableStateOf(listOf<GameCard>()); private set
    var takenCards by mutableStateOf(setOf<Int>()); private set
    var cardOwners by mutableStateOf(mapOf<Int, Long>()); private set

    /** Le joueur courant retourne la carte n° [card]. */
    fun pickCard(card: Int) {
        if (revealed || card in takenCards || card !in deck.indices) return
        val c = deck[card]
        takenCards = takenCards + card
        cardOwners = cardOwners + (card to players[distribIndex].id)
        players = players.mapIndexed { i, p -> if (i == distribIndex) p.copy(role = c.role, word = c.word, bodyguard = c.bodyguard) else p }
        revealed = true
        sfx.play(Sfx.Sound.FLIP); sfx.vibrate(longArrayOf(30))
    }
    fun nextDistribution() {
        revealed = false
        sfx.play(Sfx.Sound.TAP)
        if (distribIndex < players.lastIndex) {
            distribIndex++
            narrator.say("${players[distribIndex].name}, à toi de choisir une carte.")
        } else {
            pickStarter(); resetTimer(); screen = Screen.PLAY
            narrator.say("Tout le monde connaît son mot. Tour 1 : $starter commence. Un seul indice chacun !")
        }
    }

    private fun pickStarter() {
        // Mr. White ne commence jamais
        starter = players.filter { it.alive && it.role != Role.MRWHITE }.random().name
    }

    /** Ordre de parole du tour : sens des places, en partant du premier joueur. */
    fun speakingOrder(): List<Player> {
        val alive = players.filter { it.alive }
        val start = alive.indexOfFirst { it.name == starter }.coerceAtLeast(0)
        return alive.drop(start) + alive.take(start)
    }

    // ---- égalité au vote ----
    var tied by mutableStateOf(setOf<Long>()); private set
    fun declareTie(ids: Set<Long>) { if (ids.size >= 2) tied = ids }
    fun cancelTie() { tied = emptySet() }
    fun drawTie() { tied.filter { it != protectedId }.ifEmpty { null }?.random()?.let { eliminate(it) } }

    // ---- Garde du corps ----
    var bodyguardUsed by mutableStateOf(false); private set
    /** Joueur sauvé : il ne peut pas être éliminé pendant le tour suivant. */
    var protectedId by mutableStateOf<Long?>(null); private set
    /** Dernier sauvetage (garde, protégé) pour l'écran d'annonce. */
    var lastSave by mutableStateOf<Pair<Player, Player>?>(null); private set

    /**
     * [claimerId] affirme être le Garde du corps et veut sauver [targetId].
     * Renvoie true si c'est bien lui : le vote est annulé et on passe au tour suivant.
     */
    fun bodyguardSave(claimerId: Long, targetId: Long): Boolean {
        val g = players.firstOrNull { it.id == claimerId } ?: return false
        val t = players.firstOrNull { it.id == targetId } ?: return false
        if (!g.bodyguard || bodyguardUsed || !g.alive || claimerId == targetId) return false
        bodyguardUsed = true; tied = emptySet(); pauseTimer()
        protectedId = targetId
        lastSave = g to t
        sfx.play(Sfx.Sound.WIN); sfx.vibrate(longArrayOf(60, 60, 120))
        round++; pickStarter(); resetTimer()
        screen = Screen.SAVED
        narrator.say("Coup de théâtre ! ${g.name} était le garde du corps, et sauve ${t.name} !")
        return true
    }
    fun afterSave() { screen = Screen.PLAY }

    fun eliminate(id: Long) {
        if (id == protectedId) return
        tied = emptySet(); pauseTimer()
        val p = players.first { it.id == id }
        players = players.map { if (it.id == id) it.copy(alive = false, elimRound = round) else it }
        eliminated = p.copy(alive = false)
        revealDone = false; mrWhiteGuessed = false
        screen = Screen.REVEAL
    }

    /** L'animation de suspense de l'élimination courante a déjà été jouée. */
    var revealDone by mutableStateOf(false); private set
    var mrWhiteGuessed by mutableStateOf(false); private set
    fun markRevealDone() { revealDone = true }
    fun openMrWhiteGuess() { screen = Screen.MRWHITE_GUESS; narrator.say("Mister White, dernière chance : quel est le mot des civils ?") }

    fun mrWhiteGuess(guess: String) {
        mrWhiteGuessed = true
        lastGuess = guess.trim()
        if (normalize(guess) == normalize(civilWord)) finish(Outcome.MRWHITE)
        else { screen = Screen.REVEAL; narrator.say("Raté ! Ce n'était pas le bon mot.") }
    }

    /** Après l'affichage du rôle éliminé : partie terminée ou nouveau tour. */
    fun afterReveal() {
        val alive = players.filter { it.alive }
        val civils = alive.count { it.role == Role.CIVIL }
        val impostors = alive.size - civils
        when {
            impostors == 0 -> finish(Outcome.CIVILS)
            civils <= 1 -> finish(Outcome.IMPOSTORS)
            else -> {
                round++; protectedId = null; pickStarter(); resetTimer(); screen = Screen.PLAY
                narrator.say("Tour $round : $starter commence.")
            }
        }
    }

    fun gameWillEnd(): Boolean {
        val alive = players.filter { it.alive }
        val civils = alive.count { it.role == Role.CIVIL }
        return alive.size == civils || civils <= 1
    }

    private fun finish(o: Outcome) {
        outcome = o
        sfx.play(when (theme) {
            SeasonTheme.CHRISTMAS -> Sfx.Sound.WIN_XMAS
            SeasonTheme.HALLOWEEN -> Sfx.Sound.WIN_HALLOWEEN
            SeasonTheme.SUMMER -> Sfx.Sound.WIN_SUMMER
            else -> Sfx.Sound.WIN
        }); sfx.vibrate(longArrayOf(80, 80, 80, 80, 200))
        narrator.say(when (o) {
            Outcome.CIVILS -> "Victoire des civils ! Tous les imposteurs ont été démasqués."
            Outcome.IMPOSTORS -> "Les imposteurs l'emportent ! Ils ont berné tout le monde."
            Outcome.MRWHITE -> "Incroyable ! Mister White a trouvé le mot et gagne la partie !"
        })
        recordGame(o)
        players.forEach { p ->
            val pts = when (o) {
                Outcome.CIVILS -> if (p.role == Role.CIVIL) 2 else 0
                Outcome.IMPOSTORS -> when (p.role) { Role.UNDERCOVER -> 10; Role.MRWHITE -> 6; else -> 0 }
                Outcome.MRWHITE -> if (p.role == Role.MRWHITE) 6 else 0
            }
            scores[p.name] = (scores[p.name] ?: 0) + pts
            if (isWinner(p, o)) eveningWins[p.name] = (eveningWins[p.name] ?: 0) + 1
        }
        eveningGames++
        saveEvening()
        screen = Screen.END
    }

    private fun isWinner(p: Player, o: Outcome) = when (o) {
        Outcome.CIVILS -> p.role == Role.CIVIL
        Outcome.IMPOSTORS -> p.role != Role.CIVIL
        Outcome.MRWHITE -> p.role == Role.MRWHITE
    }

    private fun recordGame(o: Outcome) {
        val before = players.associate { it.name to Badges.unlocked(it.name, history) }
        val r = GameRecord(System.currentTimeMillis(), o, civilWord, undercoverWord,
            players.map { Triple(it.name, it.role, isWinner(it, o)) },
            players.map { it.elimRound }, lastSave?.first?.name, round)
        history = (listOf(r) + history).take(500)
        saveHistory()
        // badges débloqués par cette partie
        newBadges = players.mapNotNull { p ->
            val gained = Badges.unlocked(p.name, history) - (before[p.name] ?: emptySet())
            if (gained.isEmpty()) null else p.name to gained.map { Badges.byId(it) }
        }
    }

    /** Badges gagnés à la dernière partie : joueur -> badges. */
    var newBadges by mutableStateOf<List<Pair<String, List<Badge>>>>(emptyList()); private set
    fun dismissBadges() { newBadges = emptyList() }
    fun badgesOf(name: String) = Badges.unlocked(name, history)

    // ---- championnat de la soirée ----
    fun eveningWinsOf(n: String) = eveningWins[n] ?: 0
    private fun saveEvening() {
        val o = JSONObject()
        scores.forEach { (k, v) -> o.put(k, JSONArray().put(v).put(eveningWins[k] ?: 0)) }
        prefs.edit().putString("evening", o.toString()).putInt("eveGames", eveningGames).apply()
    }
    private fun loadEvening() = runCatching {
        val o = JSONObject(prefs.getString("evening", "{}")!!)
        o.keys().forEach { k -> val a = o.getJSONArray(k); scores[k] = a.getInt(0); eveningWins[k] = a.optInt(1) }
    }
    /** Classement de la soirée : (nom, points, victoires), du premier au dernier. */
    fun eveningRanking() = scores.entries.map { Triple(it.key, it.value, eveningWins[it.key] ?: 0) }
        .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })
    fun newEvening() { scores.clear(); eveningWins.clear(); eveningGames = 0; saveEvening() }
    fun openPodium() { returnTo = screen; screen = Screen.PODIUM; narrator.say("Voici le podium de la soirée !") }

    private fun saveHistory() {
        val a = JSONArray()
        history.forEach { g ->
            a.put(JSONObject().put("t", g.time).put("o", g.outcome.name).put("c", g.civilWord).put("u", g.undercoverWord)
                .put("p", JSONArray(g.players.map { (n, r, w) -> JSONArray().put(n).put(r.name).put(w) }))
                .put("e", JSONArray(g.elim)).put("s", g.saver ?: "").put("r", g.rounds))
        }
        prefs.edit().putString("history", a.toString()).apply()
    }

    private fun loadHistory(): List<GameRecord> = runCatching {
        val a = JSONArray(prefs.getString("history", "[]")!!)
        (0 until a.length()).map { i ->
            val g = a.getJSONObject(i)
            val ps = g.getJSONArray("p")
            GameRecord(g.getLong("t"), Outcome.valueOf(g.getString("o")), g.getString("c"), g.getString("u"),
                (0 until ps.length()).map { j -> ps.getJSONArray(j).let { Triple(it.getString(0), Role.valueOf(it.getString(1)), it.getBoolean(2)) } },
                g.optJSONArray("e")?.let { e -> (0 until e.length()).map { e.getInt(it) } } ?: emptyList(),
                g.optString("s").ifEmpty { null }, g.optInt("r"))
        }
    }.getOrDefault(emptyList())

    fun clearHistory() { history = emptyList(); saveHistory() }

    fun playerStats(): List<PlayerStats> = history.flatMap { g -> g.players }
        .groupBy { it.first }
        .map { (n, l) ->
            PlayerStats(
                n, l.size, l.count { it.third },
                l.count { it.second == Role.CIVIL && it.third },
                l.count { it.second == Role.UNDERCOVER && it.third },
                l.count { it.second == Role.MRWHITE && it.third },
                l.count { it.second == Role.UNDERCOVER }, l.count { it.second == Role.MRWHITE }
            )
        }.sortedWith(compareByDescending<PlayerStats> { it.wins }.thenByDescending { it.rate })

    fun backToSetup() { pauseTimer(); screen = Screen.SETUP }
    fun resetScores() = newEvening()

    private fun normalize(s: String) = Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("^(le |la |les |l'|un |une |des )"), "")
        .replace(Regex("[^a-z0-9]"), "")
}
