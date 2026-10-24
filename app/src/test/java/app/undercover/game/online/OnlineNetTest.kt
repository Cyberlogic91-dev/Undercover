package app.undercover.game.online

import app.undercover.game.Role
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Partie complète à travers un vrai relais MQTT (lancé localement : UC_TEST_BROKER=tcp://127.0.0.1:18830). */
class OnlineNetTest {
    @Test fun fullGameOverMqtt() {
        val brokerUrl = System.getenv("UC_TEST_BROKER")
        assumeTrue("relais de test absent", brokerUrl != null)
        val brokers = listOf("tcp://127.0.0.1:1", brokerUrl!!)   // le premier échoue : vérifie le relais de secours
        val code = OnlineNet.newCode()
        val ended = CountDownLatch(1)
        val lock = Any()

        // ---------- hôte
        lateinit var host: OnlineHost
        val hostNet = OnlineNet(code, "H", true, object : OnlineNet.Listener {
            override fun onAction(a: OAction) { synchronized(lock) { host.handle(a); maybeAdvance(host) } }
        }, brokers)
        host = OnlineHost("H", "Hôte", { "Café" to "Thé" }, { s ->
            hostNet.sendState(s); if (s.phase == OPhase.END) ended.countDown()
        }, { id, s -> hostNet.sendSecret(id, s) })
        hostNet.connect()
        Thread.sleep(1500)
        assertTrue("hôte connecté via le relais de secours", hostNet.isConnected)
        hostNet.sendState(host.state)

        // ---------- clients
        val secrets = ConcurrentHashMap<String, OSecret>()
        val ids = listOf("A", "B", "C", "D")
        val nets = ids.associateWith { id ->
            lateinit var net: OnlineNet
            net = OnlineNet(code, id, false, object : OnlineNet.Listener {
                override fun onSecret(s: OSecret) { secrets[id] = s }
                override fun onState(s: OState) = clientPlay(id, s, net, secrets)
            }, brokers)
            net
        }
        nets.values.forEach { it.connect() }
        Thread.sleep(2000)
        nets.forEach { (id, n) -> n.sendAction(OAction(OAction.JOIN, id, name = "Joueur $id")) }
        Thread.sleep(1500)
        synchronized(lock) {
            assertEquals(5, host.state.players.size)
            assertNull(host.start())
            host.handle(OAction(OAction.READY, "H"))
        }
        // l'hôte joue aussi : il agit à chaque changement d'état
        val hostLoop = Thread {
            while (ended.count > 0) {
                synchronized(lock) {
                    val s = host.state
                    when (s.phase) {
                        OPhase.CLUES -> if (s.currentSpeakerId == "H") host.handle(OAction(OAction.CLUE, "H", text = "chaud"))
                        OPhase.VOTE -> if (host.state.player("H")?.let { it.alive && !it.voted } == true)
                            s.players.firstOrNull { it.alive && it.id != "H" && (s.tie.isEmpty() || it.id in s.tie) }?.let { host.handle(OAction(OAction.VOTE, "H", target = it.id)) }
                        OPhase.REVEAL -> host.advance()
                        OPhase.MRWHITE -> if (s.elimId == "H") host.handle(OAction(OAction.GUESS, "H", text = "rien"))
                        else -> {}
                    }
                }
                Thread.sleep(150)
            }
        }.apply { isDaemon = true; start() }

        assertTrue("la partie va jusqu'au bout", ended.await(60, TimeUnit.SECONDS))
        // chaque client a reçu son propre secret, et le bon mot
        ids.forEach { id ->
            val s = secrets[id]; assertNotNull("secret de $id", s)
            assertEquals(host.roleOf(id), s!!.role)
            if (s.role == Role.CIVIL) assertEquals("Café", s.word)
        }
        hostLoop.interrupt()
        nets.values.forEach { it.close() }; hostNet.close()
    }

    private fun maybeAdvance(h: OnlineHost) {}

    private val acted = ConcurrentHashMap<String, Long>()

    /** Comportement automatique d'un joueur distant. */
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

    @Test fun wrongCodeCannotRead() {
        val brokerUrl = System.getenv("UC_TEST_BROKER")
        assumeTrue(brokerUrl != null)
        val got = CountDownLatch(1)
        val a = OnlineNet("AAAAAA", "X", true, object : OnlineNet.Listener { override fun onAction(a: OAction) { got.countDown() } }, listOf(brokerUrl!!))
        val b = OnlineNet("BBBBBB", "Y", false, object : OnlineNet.Listener {}, listOf(brokerUrl))
        a.connect(); b.connect(); Thread.sleep(1500)
        b.sendAction(OAction(OAction.JOIN, "Y", name = "Intrus"))
        assertFalse("une autre partie ne reçoit rien", got.await(2, TimeUnit.SECONDS))
        a.close(); b.close()
    }
}
