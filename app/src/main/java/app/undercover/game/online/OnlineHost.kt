package app.undercover.game.online

import app.undercover.game.Outcome
import app.undercover.game.Role
import app.undercover.game.RolePreset
import java.text.Normalizer
import kotlin.random.Random

/**
 * Moteur d'une partie en ligne, exécuté sur le téléphone de l'hôte.
 * Il reçoit les actions des joueurs et diffuse l'état public ([onState]) et les secrets ([onSecret]).
 * Aucune dépendance Android : testable sur JVM.
 */
class OnlineHost(
    val hostId: String,
    hostName: String,
    private val pickPair: (spicy: Boolean) -> Pair<String, String>,
    private val onState: (OState) -> Unit,
    private val onSecret: (playerId: String, OSecret) -> Unit,
    private val rnd: Random = Random.Default,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    var state = OState(hostId = hostId, players = listOf(OPlayer(hostId, hostName.trim().take(16), COLORS[0])))
        private set
    var preset: RolePreset = RolePreset.BALANCED

    private val roles = mutableMapOf<String, Role>()
    private val secrets = mutableMapOf<String, OSecret>()
    private val votes = mutableMapOf<String, String>()
    private val lastSeen = mutableMapOf<String, Long>()
    private var gameId = 0L

    init { publish() }

    // ------------------------------------------------------------ réglages (salon)

    fun setClueMode(m: ClueMode) { if (state.phase == OPhase.LOBBY) { state = state.copy(clueMode = m); publish() } }
    fun setAdult(adult: Boolean, spicy: Boolean) {
        if (state.phase == OPhase.LOBBY) { state = state.copy(adult = adult || spicy, spicy = spicy); publish() }
    }
    fun setRolePreset(p: RolePreset) { if (state.phase == OPhase.LOBBY) { preset = p; publish() } }
    fun rolesFor(n: Int): Pair<Int, Int> = (if (preset == RolePreset.MANUAL) RolePreset.BALANCED else preset).roles(n)

    // ------------------------------------------------------------ actions des joueurs

    fun handle(a: OAction) {
        lastSeen[a.from] = now()
        when (a.type) {
            OAction.JOIN -> join(a.from, a.name)
            OAction.PING -> markConnected(a.from)
            OAction.LEAVE -> leave(a.from)
            OAction.READY -> ready(a.from)
            OAction.CLUE -> clue(a.from, a.text)
            OAction.DONE -> clue(a.from, "✓")
            OAction.VOTE -> vote(a.from, a.target)
            OAction.GUESS -> guess(a.from, a.text)
        }
    }

    private fun join(id: String, rawName: String) {
        val existing = state.player(id)
        if (existing != null) {
            // reconnexion : on renvoie son secret
            update(id) { it.copy(connected = true) }
            secrets[id]?.let { onSecret(id, it) }
            publish(); return
        }
        if (state.phase != OPhase.LOBBY || state.players.size >= MAX_PLAYERS) return
        var name = rawName.trim().take(16).ifEmpty { "Joueur" }
        val taken = state.players.map { it.name.lowercase() }.toSet()
        if (name.lowercase() in taken) { var i = 2; while ("$name $i".lowercase() in taken) i++; name = "$name $i" }
        state = state.copy(players = state.players + OPlayer(id, name, COLORS[state.players.size % COLORS.size]))
        publish()
    }

    private fun markConnected(id: String) {
        if (state.player(id)?.connected == false) { update(id) { it.copy(connected = true) }; publish() }
    }

    private fun leave(id: String) {
        if (id == hostId) return
        state = if (state.phase == OPhase.LOBBY) state.copy(players = state.players.filter { it.id != id })
        else state.copy(players = state.players.map { if (it.id == id) it.copy(connected = false) else it })
        reevaluate(); publish()
    }

    // ------------------------------------------------------------ déroulement

    /** Lance la partie depuis le salon. Renvoie un message d'erreur, ou null si c'est parti. */
    fun start(): String? {
        if (state.phase != OPhase.LOBBY && state.phase != OPhase.END) return "Partie déjà en cours"
        val ps = state.players.filter { it.connected }
        if (ps.size < 3) return "Il faut au moins 3 joueurs connectés"
        val (u, w) = rolesFor(ps.size)
        val (civil, under) = pickPair(state.spicy)
        val deck = (List(u) { Role.UNDERCOVER } + List(w) { Role.MRWHITE } + List(ps.size - u - w) { Role.CIVIL }).shuffled(rnd)
        gameId++
        roles.clear(); secrets.clear(); votes.clear()
        ps.forEachIndexed { i, p ->
            roles[p.id] = deck[i]
            val s = OSecret(gameId, deck[i], when (deck[i]) { Role.CIVIL -> civil; Role.UNDERCOVER -> under; Role.MRWHITE -> null })
            secrets[p.id] = s; onSecret(p.id, s)
        }
        words = civil to under
        state = state.copy(
            phase = OPhase.DEAL, players = ps.map { it.copy(alive = true, ready = false, voted = false, revealedRole = null) },
            undercovers = u, mrWhites = w, round = 0, order = emptyList(), speaker = 0, clues = emptyMap(), history = emptyList(),
            tie = emptyList(), elimId = null, mrWhiteGuess = null, outcome = null, civilWord = null, undercoverWord = null, message = null
        )
        publish(); return null
    }

    private var words: Pair<String, String> = "" to ""

    private fun ready(id: String) {
        if (state.phase != OPhase.DEAL) return
        update(id) { it.copy(ready = true) }
        if (state.players.filter { it.connected }.all { it.ready }) startRound() else publish()
    }

    private fun startRound() {
        val alive = state.players.filter { it.alive }
        val starters = alive.filter { roles[it.id] != Role.MRWHITE && it.connected }.ifEmpty { alive }
        val first = starters.random(rnd)
        val i = alive.indexOf(first)
        val order = (alive.drop(i) + alive.take(i)).map { it.id }
        state = state.copy(
            phase = OPhase.CLUES, round = state.round + 1, order = order, speaker = 0, clues = emptyMap(),
            tie = emptyList(), elimId = null, players = state.players.map { it.copy(voted = false) }, message = null
        )
        skipDisconnectedSpeakers()
        publish()
    }

    private fun clue(id: String, text: String) {
        if (state.phase != OPhase.CLUES || state.currentSpeakerId != id) return
        val t = text.trim().replace("\n", " ").take(40).ifEmpty { "…" }
        state = state.copy(clues = state.clues + (id to t), speaker = state.speaker + 1)
        skipDisconnectedSpeakers()
        publish()
    }

    /** L'hôte fait passer le joueur dont c'est le tour (absent, bloqué…). */
    fun skipSpeaker() {
        val id = state.currentSpeakerId ?: return
        if (state.phase != OPhase.CLUES) return
        state = state.copy(clues = state.clues + (id to "—"), speaker = state.speaker + 1)
        skipDisconnectedSpeakers(); publish()
    }

    private fun skipDisconnectedSpeakers() {
        while (state.phase == OPhase.CLUES) {
            val id = state.currentSpeakerId
            if (id == null) { openVote(); return }
            if (state.player(id)?.connected == true) return
            state = state.copy(clues = state.clues + (id to "(absent)"), speaker = state.speaker + 1)
        }
    }

    private fun openVote() {
        votes.clear()
        state = state.copy(phase = OPhase.VOTE, players = state.players.map { it.copy(voted = false) })
        refreshVoteCount()
    }

    private fun voters() = state.players.filter { it.alive && it.connected }

    private fun refreshVoteCount() {
        state = state.copy(votesDone = votes.size, votesNeeded = voters().size)
    }

    private fun vote(from: String, target: String) {
        if (state.phase != OPhase.VOTE || from in votes) return
        val voter = state.player(from) ?: return
        val t = state.player(target) ?: return
        if (!voter.alive || !t.alive || from == target) return
        if (state.tie.isNotEmpty() && target !in state.tie) return
        votes[from] = target
        update(from) { it.copy(voted = true) }
        refreshVoteCount()
        if (voters().all { it.id in votes }) tally() else publish()
    }

    private fun tally() {
        val counts = votes.values.groupingBy { it }.eachCount()
        val max = counts.values.maxOrNull() ?: return
        val leaders = counts.filterValues { it == max }.keys.toList()
        when {
            leaders.size == 1 -> eliminate(leaders[0], null)
            state.tie.isEmpty() -> {
                votes.clear()
                state = state.copy(
                    tie = leaders, players = state.players.map { it.copy(voted = false) },
                    message = "Égalité entre ${leaders.joinToString(", ") { state.player(it)?.name ?: "?" }} : nouveau vote entre eux !"
                )
                refreshVoteCount(); publish()
            }
            else -> eliminate(leaders.random(rnd), "Encore égalité : le sort a désigné un joueur.")
        }
    }

    private fun eliminate(id: String, msg: String?) {
        val role = roles[id] ?: Role.CIVIL
        val hist = state.history + state.clues.map { (pid, c) -> Triple(state.round, pid, c) }
        state = state.copy(
            phase = OPhase.REVEAL, elimId = id, tie = emptyList(), history = hist, message = msg, mrWhiteGuess = null,
            players = state.players.map { if (it.id == id) it.copy(alive = false, revealedRole = role) else it }
        )
        votes.clear(); refreshVoteCount(); publish()
    }

    /** L'hôte passe à la suite après la révélation. */
    fun advance() {
        if (state.phase != OPhase.REVEAL) return
        val id = state.elimId
        if (id != null && roles[id] == Role.MRWHITE && state.mrWhiteGuess == null && state.player(id)?.connected == true) {
            state = state.copy(phase = OPhase.MRWHITE, message = null); publish(); return
        }
        checkEndOrNext()
    }

    private fun guess(from: String, text: String) {
        if (state.phase != OPhase.MRWHITE || from != state.elimId) return
        val g = text.trim().take(40)
        if (normalize(g) == normalize(words.first) && g.isNotBlank()) {
            state = state.copy(mrWhiteGuess = g); finish(Outcome.MRWHITE)
        } else {
            state = state.copy(phase = OPhase.REVEAL, mrWhiteGuess = g.ifEmpty { "…" }, message = "« ${g.ifEmpty { "…" }} » : raté !")
            publish()
        }
    }

    private fun checkEndOrNext() {
        val alive = state.players.filter { it.alive }
        val civils = alive.count { roles[it.id] == Role.CIVIL }
        val impostors = alive.size - civils
        when {
            impostors == 0 -> finish(Outcome.CIVILS)
            civils <= 1 -> finish(Outcome.IMPOSTORS)
            else -> startRound()
        }
    }

    private fun finish(o: Outcome) {
        state = state.copy(
            phase = OPhase.END, outcome = o, civilWord = words.first, undercoverWord = words.second, tie = emptyList(),
            players = state.players.map { it.copy(revealedRole = roles[it.id] ?: it.revealedRole) }
        )
        publish()
    }

    /** Retour au salon avec les mêmes joueurs (les absents sont retirés). */
    fun restart() {
        roles.clear(); secrets.clear(); votes.clear()
        state = state.copy(
            phase = OPhase.LOBBY, players = state.players.filter { it.connected }.map { OPlayer(it.id, it.name, it.color) },
            round = 0, order = emptyList(), clues = emptyMap(), history = emptyList(), tie = emptyList(), elimId = null,
            mrWhiteGuess = null, outcome = null, civilWord = null, undercoverWord = null, message = null, votesDone = 0, votesNeeded = 0
        )
        publish()
    }

    fun kick(id: String) { if (id != hostId) leave(id) }

    // ------------------------------------------------------------ présence

    /** À appeler régulièrement : détecte les joueurs déconnectés (sans nouvelles depuis 30 s). */
    fun tick() {
        val t = now()
        var changed = false
        state = state.copy(players = state.players.map { p ->
            val on = p.id == hostId || (t - (lastSeen[p.id] ?: t)) < 30_000
            if (on != p.connected) { changed = true; p.copy(connected = on) } else p
        })
        if (changed) { reevaluate(); publish() }
    }

    /** Après un changement de présence : débloque la partie si besoin. */
    private fun reevaluate() {
        when (state.phase) {
            OPhase.DEAL -> if (state.players.filter { it.connected }.all { it.ready }) startRound()
            OPhase.CLUES -> skipDisconnectedSpeakers()
            OPhase.VOTE -> { votes.keys.retainAll(voters().map { it.id }.toSet()); refreshVoteCount(); if (voters().isNotEmpty() && voters().all { it.id in votes }) tally() }
            OPhase.MRWHITE -> if (state.player(state.elimId)?.connected != true) checkEndOrNext()
            else -> {}
        }
    }

    // ------------------------------------------------------------ outils

    private fun update(id: String, f: (OPlayer) -> OPlayer) {
        state = state.copy(players = state.players.map { if (it.id == id) f(it) else it })
    }

    private fun publish() {
        state = state.copy(seq = state.seq + 1)
        onState(state)
    }

    fun roleOf(id: String) = roles[id]

    companion object {
        const val MAX_PLAYERS = 20
        val COLORS = listOf(0xFFFFB020, 0xFF3DDC97, 0xFF5AA9FF, 0xFFFF7AB6, 0xFFB38CFF, 0xFFFF8A4C, 0xFF4FD1C5, 0xFFE8EAED).map { it.toInt() }

        fun normalize(s: String) = Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("^(le |la |les |l'|un |une |des )"), "")
            .replace(Regex("[^a-z0-9]"), "")
    }
}
