package app.undercover.game.online

import app.undercover.game.Role
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Parties et attaques à travers un vrai relais MQTT local
 * (lancer un relais puis : UC_TEST_BROKER=tcp://127.0.0.1:18830).
 */
class OnlineNetTest {
    private val brokerUrl: String? = System.getenv("UC_TEST_BROKER")
    private fun brokers() = listOf(brokerUrl!!)

    /** Hôte réel : renvoie (moteur, réseau, code, actions reçues). */
    private class Host(val engine: OnlineHost, val net: OnlineNet, val code: String, val actions: MutableList<OAction>)

    private fun startHost(brokers: List<String>, lock: Any = Any(), onEnd: () -> Unit = {}): Host {
        val keys = OnlineCrypto.newKeyPair()
        val code = OnlineNet.codeFor(keys)
        val actions = CopyOnWriteArrayList<OAction>()
        lateinit var engine: OnlineHost
        val net = OnlineNet(code, "H", true, object : OnlineNet.Listener {
            override fun onAction(a: OAction) { actions += a; synchronized(lock) { engine.handle(a) } }
        }, keys, brokers)
        engine = OnlineHost("H", "Hôte", { "Café" to "Thé" }, { s -> net.sendState(s); if (s.phase == OPhase.END) onEnd() },
            { id, s -> net.sendSecret(id, s) })
        net.connect()
        Thread.sleep(1800)      // PBKDF2 + connexion
        net.sendState(engine.state)
        return Host(engine, net, code, actions)
    }

    private fun guest(code: String, id: String, keys: java.security.KeyPair = OnlineCrypto.newKeyPair(), l: OnlineNet.Listener = object : OnlineNet.Listener {}) =
        OnlineNet(code, id, false, l, keys, brokers()).also { it.connect() }

    @Test fun codeIsBoundToHostKey() {
        val k = OnlineCrypto.newKeyPair()
        val c = OnlineNet.codeFor(k)
        assertTrue(OnlineNet.isValidCode(c))
        assertEquals(c, OnlineNet.codeFor(k))
        assertNotEquals(c, OnlineNet.codeFor(OnlineCrypto.newKeyPair()))
        assertEquals(c, OnlineNet.normalizeCode(OnlineNet.display(c).lowercase()))
    }

    @Test fun fullGameOverMqtt() {
        assumeTrue("relais de test absent", brokerUrl != null)
        val ended = CountDownLatch(1)
        val lock = Any()
        val h = startHost(listOf("tcp://127.0.0.1:1", brokerUrl!!), lock) { ended.countDown() }  // le 1er relais échoue
        assertTrue("hôte connecté via le relais de secours", h.net.isConnected)

        val secrets = ConcurrentHashMap<String, OSecret>()
        val ids = listOf("A", "B", "C", "D")
        val nets = ids.associateWith { id ->
            lateinit var net: OnlineNet
            net = guest(h.code, id, l = object : OnlineNet.Listener {
                override fun onSecret(s: OSecret) { secrets[id] = s }
                override fun onState(s: OState) = clientPlay(id, s, net, secrets)
            })
            net
        }
        Thread.sleep(2500)
        nets.forEach { (id, n) -> assertTrue("$id a vérifié l'hôte", n.hostKnown); n.sendAction(OAction(OAction.JOIN, id, name = "Joueur $id")) }
        Thread.sleep(1500)
        synchronized(lock) {
            assertEquals(5, h.engine.state.players.size)
            assertNull(h.engine.start())
            h.engine.handle(OAction(OAction.READY, "H"))
        }
        val hostLoop = Thread {
            while (ended.count > 0) {
                synchronized(lock) {
                    val s = h.engine.state
                    when (s.phase) {
                        OPhase.CLUES -> if (s.currentSpeakerId == "H") h.engine.handle(OAction(OAction.CLUE, "H", text = "chaud"))
                        OPhase.VOTE -> if (s.player("H")?.let { it.alive && !it.voted } == true)
                            s.players.firstOrNull { it.alive && it.id != "H" && (s.tie.isEmpty() || it.id in s.tie) }?.let { h.engine.handle(OAction(OAction.VOTE, "H", target = it.id)) }
                        OPhase.REVEAL -> h.engine.advance()
                        OPhase.MRWHITE -> if (s.elimId == "H") h.engine.handle(OAction(OAction.GUESS, "H", text = "rien"))
                        else -> {}
                    }
                }
                Thread.sleep(150)
            }
        }.apply { isDaemon = true; start() }

        assertTrue("la partie va jusqu'au bout", ended.await(60, TimeUnit.SECONDS))
        ids.forEach { id ->
            val s = secrets[id]; assertNotNull("secret de $id", s)
            assertEquals(h.engine.roleOf(id), s!!.role)
            if (s.role == Role.CIVIL) assertEquals("Café", s.word)
        }
        hostLoop.interrupt()
        nets.values.forEach { it.close() }; h.net.close()
    }

    private val acted = ConcurrentHashMap<String, Long>()

    private fun clientPlay(id: String, s: OState, net: OnlineNet, secrets: Map<String, OSecret>) {
        val me = s.player(id) ?: return
        val key = "$id:${s.phase}:${s.round}:${s.speaker}:${s.tie.size}:${me.voted}"
        if (acted.putIfAbsent(key, s.seq) != null) return
        when (s.phase) {
            OPhase.DEAL -> if (!me.ready && secrets[id] != null) net.sendAction(OAction(OAction.READY, id))
            OPhase.CLUES -> if (s.currentSpeakerId == id) net.sendAction(OAction(OAction.CLUE, id, text = "indice de $id"))
            OPhase.VOTE -> if (me.alive && !me.voted) s.players.firstOrNull { it.alive && it.id != id && (s.tie.isEmpty() || it.id in s.tie) }
                ?.let { net.sendAction(OAction(OAction.VOTE, id, target = it.id)) }
            OPhase.MRWHITE -> if (s.elimId == id) net.sendAction(OAction(OAction.GUESS, id, text = "je sais pas"))
            else -> {}
        }
    }

    /** Attaque : un intrus (qui connaît le code) se fait passer pour le joueur A. */
    @Test fun impersonationIsRejected() {
        assumeTrue(brokerUrl != null)
        val h = startHost(brokers())
        val a = guest(h.code, "A"); Thread.sleep(2200)
        a.sendAction(OAction(OAction.JOIN, "A", name = "Alice")); Thread.sleep(800)
        assertEquals("Alice", h.engine.state.player("A")?.name)

        val pirate = guest(h.code, "A"); Thread.sleep(2200)         // même identifiant, autre clé
        pirate.sendAction(OAction(OAction.CLUE, "A", text = "PIRATE"))
        pirate.sendAction(OAction(OAction.JOIN, "A", name = "PIRATE"))
        Thread.sleep(1000)
        assertTrue("aucune action du pirate n'est acceptée", h.actions.none { it.text == "PIRATE" || it.name == "PIRATE" })
        a.close(); pirate.close(); h.net.close()
    }

    /** Attaque : un joueur tente de lire le mot secret d'un autre. */
    @Test fun otherPlayersCannotReadSecrets() {
        assumeTrue(brokerUrl != null)
        val h = startHost(brokers())
        val gotA = CountDownLatch(1)
        val a = guest(h.code, "A", l = object : OnlineNet.Listener { override fun onSecret(s: OSecret) { gotA.countDown() } })
        val b = guest(h.code, "B"); val c = guest(h.code, "C")
        Thread.sleep(2200)
        a.sendAction(OAction(OAction.JOIN, "A", name = "A")); b.sendAction(OAction(OAction.JOIN, "B", name = "B")); c.sendAction(OAction(OAction.JOIN, "C", name = "C"))
        Thread.sleep(1000)
        assertNull(h.engine.start())
        assertTrue("A reçoit son propre secret", gotA.await(5, TimeUnit.SECONDS))
        // l'espion s'abonne au sujet du secret de A (même identifiant), mais sans la clé de A
        val leaked = CountDownLatch(1)
        val spy = guest(h.code, "A", l = object : OnlineNet.Listener { override fun onSecret(s: OSecret) { leaked.countDown() } })
        assertFalse("le secret de A reste illisible pour les autres", leaked.await(4, TimeUnit.SECONDS))
        listOf(a, b, c, spy).forEach { it.close() }; h.net.close()
    }

    /** Attaque : quelqu'un qui connaît le code publie un faux état en se faisant passer pour l'hôte. */
    @Test fun fakeHostIsIgnored() {
        assumeTrue(brokerUrl != null)
        val h = startHost(brokers())
        val seen = CopyOnWriteArrayList<OState>()
        val g = guest(h.code, "G", l = object : OnlineNet.Listener { override fun onState(s: OState) { seen += s } })
        Thread.sleep(2500)
        assertTrue(seen.any { it.hostId == "H" })
        val evil = OnlineNet(h.code, "EVIL", true, object : OnlineNet.Listener {}, OnlineCrypto.newKeyPair(), brokers())
        evil.connect(); Thread.sleep(1800)
        evil.sendState(OState(seq = 9999, hostId = "EVIL", message = "piraté"))
        Thread.sleep(1500)
        assertTrue("le faux état n'est jamais accepté", seen.none { it.hostId == "EVIL" })
        // un retardataire arrive APRÈS le faux état (conservé sur le relais) : il ne doit pas s'y fier
        val late = CopyOnWriteArrayList<OState>()
        val g2 = guest(h.code, "L", l = object : OnlineNet.Listener { override fun onState(s: OState) { late += s } })
        Thread.sleep(2500)
        h.net.sendState(h.engine.state); Thread.sleep(800)
        assertTrue("le retardataire ignore le faux hôte", late.none { it.hostId == "EVIL" })
        assertTrue("le retardataire suit le vrai hôte", late.any { it.hostId == "H" })
        g.close(); g2.close(); evil.close(); h.net.close()
    }

    @Test fun wrongCodeCannotRead() {
        assumeTrue(brokerUrl != null)
        val h = startHost(brokers())
        val wrong = OnlineNet.codeFor(OnlineCrypto.newKeyPair())
        val seen = CountDownLatch(1)
        val g = guest(wrong, "Y", l = object : OnlineNet.Listener { override fun onState(s: OState) { seen.countDown() } })
        Thread.sleep(2200)
        g.sendAction(OAction(OAction.JOIN, "Y", name = "Intrus"))
        assertFalse("un mauvais code ne voit rien", seen.await(2, TimeUnit.SECONDS))
        assertTrue(h.actions.isEmpty())
        g.close(); h.net.close()
    }
}
