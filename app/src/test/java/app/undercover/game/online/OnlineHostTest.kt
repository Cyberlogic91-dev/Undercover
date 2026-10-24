package app.undercover.game.online

import app.undercover.game.Outcome
import app.undercover.game.Role
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class OnlineHostTest {
    private var clock = 0L
    private val secrets = mutableMapOf<String, OSecret>()
    private val states = mutableListOf<OState>()

    private fun host(seed: Int = 1) = OnlineHost("H", "Hôte", { _ -> "Café" to "Thé" },
        { states += OState.fromJson(it.toJson()) },   // passe par le JSON : vérifie aussi la sérialisation
        { id, s -> secrets[id] = OSecret.fromJson(s.toJson()) }, Random(seed), { clock })

    private fun OnlineHost.act(type: String, from: String, text: String = "", target: String = "", name: String = "") =
        handle(OAction.fromJson(OAction(type, from, name, text, target).toJson()))

    private fun lobby(h: OnlineHost, n: Int) { (1 until n).forEach { h.act(OAction.JOIN, "P$it", name = "Joueur $it") } }

    @Test fun lobbyAndDeal() {
        val h = host(); lobby(h, 5)
        assertEquals(5, h.state.players.size)
        assertNull(h.start())
        assertEquals(OPhase.DEAL, h.state.phase)
        assertEquals(5, secrets.size)
        assertEquals(1, h.state.undercovers); assertEquals(1, h.state.mrWhites)
        secrets.forEach { (id, s) ->
            when (s.role) {
                Role.CIVIL -> assertEquals("Café", s.word)
                Role.UNDERCOVER -> assertEquals("Thé", s.word)
                Role.MRWHITE -> assertNull(s.word)
            }
        }
        // le mot ne fuit pas dans l'état public
        assertFalse(states.last().toJson().toString().contains("Café"))
    }

    @Test fun duplicateNamesAndTooFewPlayers() {
        val h = host(); h.act(OAction.JOIN, "A", name = "Léa"); assertNotNull(h.start())
        h.act(OAction.JOIN, "B", name = "léa")
        assertEquals("léa 2", h.state.player("B")!!.name)
    }

    private fun playUntilEnd(h: OnlineHost, pickTarget: (OnlineHost, String) -> String): OState {
        var guard = 0
        while (h.state.phase != OPhase.END && guard++ < 5000) {
            val s = h.state
            when (s.phase) {
                OPhase.DEAL -> s.players.forEach { h.act(OAction.READY, it.id) }
                OPhase.CLUES -> h.act(if (s.clueMode == ClueMode.WRITTEN) OAction.CLUE else OAction.DONE, s.currentSpeakerId!!, text = "indice")
                OPhase.VOTE -> s.players.filter { it.alive && it.connected && !it.voted }.forEach { h.act(OAction.VOTE, it.id, target = pickTarget(h, it.id)) }
                OPhase.REVEAL -> h.advance()
                OPhase.MRWHITE -> h.act(OAction.GUESS, s.elimId!!, text = "mauvais mot")
                else -> fail("phase inattendue ${s.phase}")
            }
        }
        return h.state
    }

    @Test fun civilsWinWhenVotingImpostors() {
        val h = host(); lobby(h, 6); h.start()
        val end = playUntilEnd(h) { hh, voter ->
            val alive = hh.state.players.filter { it.alive && it.id != voter }
            (alive.firstOrNull { hh.roleOf(it.id) != Role.CIVIL } ?: alive.first()).id
        }
        assertEquals(Outcome.CIVILS, end.outcome)
        assertEquals("Café", end.civilWord); assertEquals("Thé", end.undercoverWord)
        assertTrue(end.players.all { it.revealedRole != null })
    }

    @Test fun impostorsWinWhenVotingCivils() {
        val h = host(); lobby(h, 5); h.start()
        val end = playUntilEnd(h) { hh, voter ->
            val alive = hh.state.players.filter { it.alive && it.id != voter }
            (alive.firstOrNull { hh.roleOf(it.id) == Role.CIVIL } ?: alive.first()).id
        }
        assertEquals(Outcome.IMPOSTORS, end.outcome)
    }

    @Test fun mrWhiteCanWinByGuessing() {
        val h = host(3); lobby(h, 5); h.start()
        h.state.players.forEach { h.act(OAction.READY, it.id) }
        while (h.state.phase == OPhase.CLUES) h.act(OAction.CLUE, h.state.currentSpeakerId!!, text = "x")
        val mw = h.state.players.first { h.roleOf(it.id) == Role.MRWHITE }.id
        h.state.players.filter { it.id != mw }.forEach { h.act(OAction.VOTE, it.id, target = mw) }
        h.act(OAction.VOTE, mw, target = h.state.players.first { it.id != mw }.id)
        assertEquals(OPhase.REVEAL, h.state.phase); assertEquals(mw, h.state.elimId)
        h.advance(); assertEquals(OPhase.MRWHITE, h.state.phase)
        h.act(OAction.GUESS, "H".takeIf { it != mw } ?: "P1", text = "café") // un autre joueur ne peut pas répondre
        assertEquals(OPhase.MRWHITE, h.state.phase)
        h.act(OAction.GUESS, mw, text = "le Cafe")
        assertEquals(Outcome.MRWHITE, h.state.outcome)
    }

    @Test fun tieTriggersRevoteThenDraw() {
        val h = host(); lobby(h, 4); h.start()
        h.state.players.forEach { h.act(OAction.READY, it.id) }
        while (h.state.phase == OPhase.CLUES) h.act(OAction.CLUE, h.state.currentSpeakerId!!, text = "x")
        // H et P1 votent l'un pour l'autre, P2 → H, P3 → P1 : égalité 2-2
        h.act(OAction.VOTE, "H", target = "P1"); h.act(OAction.VOTE, "P1", target = "H")
        h.act(OAction.VOTE, "P2", target = "H"); h.act(OAction.VOTE, "P3", target = "P1")
        assertEquals(listOf("H", "P1").sorted(), h.state.tie.sorted())
        assertEquals(OPhase.VOTE, h.state.phase)
        h.act(OAction.VOTE, "P2", target = "P3") // hors égalité : refusé
        assertEquals(0, h.state.votesDone)
        h.act(OAction.VOTE, "H", target = "P1"); h.act(OAction.VOTE, "P1", target = "H")
        h.act(OAction.VOTE, "P2", target = "H"); h.act(OAction.VOTE, "P3", target = "P1")
        assertEquals(OPhase.REVEAL, h.state.phase)
        assertTrue(h.state.elimId in listOf("H", "P1"))
    }

    @Test fun oralModeAndSpeakerOrder() {
        val h = host(); lobby(h, 4); h.setClueMode(ClueMode.ORAL); h.start()
        h.state.players.forEach { h.act(OAction.READY, it.id) }
        val first = h.state.currentSpeakerId!!
        assertNotEquals(Role.MRWHITE, h.roleOf(first))
        val other = h.state.players.first { it.id != first }.id
        h.act(OAction.DONE, other)               // pas son tour : ignoré
        assertEquals(0, h.state.speaker)
        h.act(OAction.DONE, first); assertEquals("✓", h.state.clues[first])
        h.skipSpeaker(); assertEquals(2, h.state.speaker)
    }

    @Test fun disconnectedPlayersAreSkippedAndDoNotBlockVote() {
        val h = host(); lobby(h, 5); h.start()
        h.state.players.forEach { h.act(OAction.READY, it.id) }
        clock = 40_000                                   // personne n'a donné de nouvelles…
        listOf("H", "P1", "P2").forEach { h.act(OAction.PING, it) }  // …sauf 3 joueurs
        h.tick()
        assertEquals(2, h.state.players.count { !it.connected })
        while (h.state.phase == OPhase.CLUES) h.act(OAction.CLUE, h.state.currentSpeakerId!!, text = "x")
        assertEquals(OPhase.VOTE, h.state.phase)
        assertEquals(3, h.state.votesNeeded)
    }

    @Test fun reconnectResendsSecretAndRestartKeepsPlayers() {
        val h = host(); lobby(h, 4); h.start()
        val s = secrets["P2"]; secrets.remove("P2")
        h.act(OAction.JOIN, "P2", name = "Joueur 2")
        assertEquals(s, secrets["P2"])
        playUntilEnd(h) { hh, v -> hh.state.players.first { it.alive && it.id != v }.id }
        h.restart()
        assertEquals(OPhase.LOBBY, h.state.phase); assertEquals(4, h.state.players.size)
        assertTrue(h.state.players.all { it.alive && it.revealedRole == null })
    }

    @Test fun manyRandomGamesAlwaysFinish() {
        repeat(300) { seed ->
            secrets.clear()
            val h = host(seed); lobby(h, 3 + seed % 18)
            h.setClueMode(if (seed % 2 == 0) ClueMode.WRITTEN else ClueMode.ORAL)
            assertNull(h.start())
            val rnd = Random(seed)
            val end = playUntilEnd(h) { hh, v -> hh.state.players.filter { it.alive && it.id != v && (hh.state.tie.isEmpty() || it.id in hh.state.tie) }.random(rnd).id }
            assertEquals("seed $seed", OPhase.END, end.phase)
            assertNotNull(end.outcome)
        }
    }
}
