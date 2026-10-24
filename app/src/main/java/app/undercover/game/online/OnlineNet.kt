package app.undercover.game.online

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.crypto.spec.SecretKeySpec

/**
 * Transport du mode en ligne via des relais MQTT publics.
 *
 * Sécurité (protocole v2) :
 *  - code de 8 caractères (≈ 40 bits) étiré par PBKDF2 → clé de salle + sujet imprévisible ;
 *  - l'état public est signé par l'hôte (ECDSA P-256) ; les invités épinglent sa clé à la première réception ;
 *  - secrets (rôle, mot) et actions (votes, indices) chiffrés de bout en bout hôte ↔ joueur (ECDH) :
 *    les autres joueurs ne peuvent ni les lire ni les falsifier ;
 *  - un joueur est lié à sa clé dès qu'il rejoint : personne ne peut voter à sa place ;
 *  - limites de taille et de débit côté hôte.
 * Sans dépendance Android : testable sur JVM avec un relais local.
 */
class OnlineNet(
    code: String,
    private val selfId: String,
    private val isHost: Boolean,
    private val listener: Listener,
    private val keys: KeyPair = OnlineCrypto.newKeyPair(),
    private val brokers: List<String> = PUBLIC_BROKERS
) {
    interface Listener {
        fun onState(s: OState) {}
        fun onSecret(s: OSecret) {}
        fun onAction(a: OAction) {}
        fun onHostGone() {}
        fun onConnection(connected: Boolean, broker: String?) {}
    }

    private val code = normalizeCode(code)
    private lateinit var roomKey: SecretKeySpec
    private lateinit var topicId: String
    private val base get() = "uc-cyberlogic/v2/$topicId"
    private val tState get() = "$base/s"
    private val tAct get() = "$base/a"
    private val tHost get() = "$base/h"
    private fun tSecret(id: String) = "$base/p/" + sha256hex("p:$id").take(16)
    private val myPub = OnlineCrypto.encodePublic(keys.public)

    private val io = Executors.newSingleThreadExecutor { Thread(it, "undercover-net").apply { isDaemon = true } }
    private var client: MqttClient? = null
    private val rng = SecureRandom()
    @Volatile var broker: String? = null; private set
    @Volatile private var closed = false

    // côté invité : clé de l'hôte épinglée
    @Volatile private var hostKey: PublicKey? = null
    private var hostKeyEnc: String? = null
    val hostKnown get() = hostKey != null

    // côté hôte : clé de chaque joueur, épinglée à son arrivée
    private val playerKeys = ConcurrentHashMap<String, String>()
    private val pairKeys = ConcurrentHashMap<String, SecretKeySpec>()
    private val rate = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val sentSecrets = mutableSetOf<String>()

    /** Dérive les clés puis se connecte au premier relais disponible (en arrière-plan). */
    fun connect() = io.execute {
        val (k, t) = OnlineCrypto.roomSecrets(code)
        roomKey = k; topicId = t
        for (b in brokers) {
            if (closed) return@execute
            try {
                val c = MqttClient(b, "uc-" + rng.nextLong().toULong().toString(36).take(12), MemoryPersistence())
                val opt = MqttConnectOptions().apply {
                    isCleanSession = true
                    isAutomaticReconnect = true
                    isHttpsHostnameVerificationEnabled = true
                    connectionTimeout = 8
                    keepAliveInterval = 20
                    if (isHost) setWill(tHost, hostMessage("gone"), 1, true)
                }
                c.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        io.execute { subscribe(c) }
                        listener.onConnection(true, b)
                    }
                    override fun connectionLost(cause: Throwable?) { if (!closed) listener.onConnection(false, b) }
                    override fun messageArrived(topic: String, message: MqttMessage) { receive(topic, message.payload) }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                c.connect(opt)
                client = c; broker = b
                if (isHost) publishRaw(tHost, hostMessage("alive"), true)
                return@execute
            } catch (e: Exception) {
                // relais suivant
            }
        }
        listener.onConnection(false, null)
    }

    private fun subscribe(c: MqttClient) {
        runCatching {
            if (isHost) c.subscribe(tAct, 1)
            else c.subscribe(arrayOf(tState, tSecret(selfId), tHost), intArrayOf(1, 1, 1))
        }
    }

    // ------------------------------------------------------------ réception

    private fun receive(topic: String, payload: ByteArray) {
        if (payload.isEmpty() || payload.size > MAX_PAYLOAD) return
        val plain = OnlineCrypto.open(roomKey, payload, topic.toByteArray()) ?: return
        val j = runCatching { JSONObject(String(plain, Charsets.UTF_8)) }.getOrNull() ?: return
        runCatching {
            when (topic) {
                tAct -> if (isHost) receiveAction(j)
                tState -> if (!isHost) receiveState(j)
                tHost -> if (!isHost) receiveHostMessage(j)
                tSecret(selfId) -> if (!isHost) receiveSecret(j)
            }
        }
    }

    /** Vérifie la signature de l'hôte et épingle sa clé à la première réception. */
    private fun checkHost(keyEnc: String, data: ByteArray, sig: String): Boolean {
        if (codeFor(keyEnc) != code) return false                       // la clé doit correspondre au code
        val pinned = hostKeyEnc
        if (pinned != null && pinned != keyEnc) return false
        val pub = (if (pinned != null) hostKey else OnlineCrypto.decodePublic(keyEnc)) ?: return false
        if (!OnlineCrypto.verify(pub, data, sig)) return false
        if (pinned == null) { hostKey = pub; hostKeyEnc = keyEnc }
        return true
    }

    private fun receiveState(j: JSONObject) {
        val s = j.getString("s")
        if (!checkHost(j.getString("k"), (tState + s).toByteArray(), j.getString("g"))) return
        listener.onState(OState.fromJson(JSONObject(s)))
    }

    private fun receiveHostMessage(j: JSONObject) {
        val m = j.getString("m")
        if (!checkHost(j.getString("k"), "$tHost:$m".toByteArray(), j.getString("g"))) return
        if (m == "gone") listener.onHostGone()
    }

    private fun receiveSecret(j: JSONObject) {
        val hk = hostKey ?: return
        val key = OnlineCrypto.pairKey(keys.private, hk, topicId)
        val plain = OnlineCrypto.open(key, OnlineCrypto.unb64(j.getString("c")) ?: return, selfId.toByteArray()) ?: return
        listener.onSecret(OSecret.fromJson(JSONObject(String(plain, Charsets.UTF_8))))
    }

    private fun receiveAction(j: JSONObject) {
        val from = j.getString("f").take(64)
        val keyEnc = j.getString("k")
        if (!allow(from)) return
        val pinned = playerKeys[from]
        if (pinned != null && pinned != keyEnc) return                    // usurpation : mauvaise clé
        val pub = OnlineCrypto.decodePublic(keyEnc) ?: return
        val key = if (pinned != null) pairKeys.getOrPut(from) { OnlineCrypto.pairKey(keys.private, pub, topicId) }
                  else OnlineCrypto.pairKey(keys.private, pub, topicId)
        val plain = OnlineCrypto.open(key, OnlineCrypto.unb64(j.getString("c")) ?: return, from.toByteArray()) ?: return
        val a = OAction.fromJson(JSONObject(String(plain, Charsets.UTF_8)))
        if (a.from != from) return
        if (pinned == null) {
            if (a.type != OAction.JOIN) return                            // il faut d'abord rejoindre
            playerKeys[from] = keyEnc; pairKeys[from] = key
        }
        listener.onAction(a)
    }

    /** Limite de débit par joueur : 40 messages par 10 secondes. */
    private fun allow(id: String): Boolean {
        val now = System.currentTimeMillis()
        val q = rate.getOrPut(id) { ArrayDeque() }
        synchronized(q) {
            while (q.isNotEmpty() && now - q.first() > 10_000) q.removeFirst()
            if (q.size >= 40) return false
            q.addLast(now); return true
        }
    }

    // ------------------------------------------------------------ envoi

    /** Envoie une action à l'hôte (chiffrée pour lui seul). Ignorée tant que l'hôte n'est pas connu. */
    fun sendAction(a: OAction) = io.execute {
        val hk = hostKey ?: return@execute
        val key = OnlineCrypto.pairKey(keys.private, hk, topicId)
        val inner = OnlineCrypto.seal(key, a.toJson().toString().toByteArray(), selfId.toByteArray())
        val env = JSONObject().put("f", selfId).put("k", myPub).put("c", OnlineCrypto.b64(inner))
        publishRaw(tAct, envelope(tAct, env), false)
    }

    fun sendState(s: OState) = io.execute {
        if (!::roomKey.isInitialized) return@execute
        val js = s.toJson().toString()
        val env = JSONObject().put("s", js).put("k", myPub).put("g", OnlineCrypto.sign(keys.private, (tState + js).toByteArray()))
        publishRaw(tState, envelope(tState, env), true)
    }

    fun sendSecret(playerId: String, s: OSecret) = io.execute {
        if (!::roomKey.isInitialized) return@execute
        val key = pairKeys[playerId] ?: return@execute
        sentSecrets += playerId
        val inner = OnlineCrypto.seal(key, s.toJson().toString().toByteArray(), playerId.toByteArray())
        val t = tSecret(playerId)
        publishRaw(t, envelope(t, JSONObject().put("c", OnlineCrypto.b64(inner))), true)
    }

    private fun hostMessage(m: String): ByteArray {
        val env = JSONObject().put("m", m).put("k", myPub).put("g", OnlineCrypto.sign(keys.private, "$tHost:$m".toByteArray()))
        return envelope(tHost, env)
    }

    /** Enveloppe chiffrée avec la clé de salle, liée au sujet (empêche de rejouer un message ailleurs). */
    private fun envelope(topic: String, j: JSONObject) = OnlineCrypto.seal(roomKey, j.toString().toByteArray(), topic.toByteArray())

    private fun publishRaw(topic: String, data: ByteArray, retained: Boolean) {
        val c = client ?: return
        runCatching { c.publish(topic, MqttMessage(data).apply { qos = 1; isRetained = retained }) }
    }

    /** Quitte la partie. L'hôte efface ses messages conservés sur le relais. */
    fun close() {
        closed = true
        io.execute {
            val c = client
            if (c != null) runCatching {
                if (isHost) {
                    publishRaw(tHost, hostMessage("gone"), true)
                    publishRaw(tState, ByteArray(0), true)
                    sentSecrets.forEach { publishRaw(tSecret(it), ByteArray(0), true) }
                    publishRaw(tHost, ByteArray(0), true)
                }
                c.disconnect(2000); c.close()
            }
            client = null
        }
        io.shutdown()
    }

    val isConnected get() = client?.isConnected == true

    companion object {
        /** Relais publics gratuits, essayés dans l'ordre. */
        val PUBLIC_BROKERS = listOf(
            "ssl://broker.hivemq.com:8883",
            "ssl://broker.emqx.io:8883",
            "ssl://test.mosquitto.org:8886"
        )
        const val MAX_PAYLOAD = 16 * 1024
        const val CODE_LENGTH = 8

        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        /**
         * Le code de partie est dérivé de la clé publique de l'hôte : un invité peut ainsi vérifier
         * que l'état reçu vient bien du créateur du code (trouver une autre clé donnant le même code
         * demanderait ~10¹² essais).
         */
        fun codeFor(publicKeyEnc: String): String {
            var n = java.math.BigInteger(1, MessageDigest.getInstance("SHA-256").digest("uc-host:$publicKeyEnc".toByteArray()))
            val base = java.math.BigInteger.valueOf(ALPHABET.length.toLong())
            return buildString { repeat(CODE_LENGTH) { val qr = n.divideAndRemainder(base); append(ALPHABET[qr[1].toInt()]); n = qr[0] } }
        }
        fun codeFor(keys: KeyPair) = codeFor(OnlineCrypto.encodePublic(keys.public))
        fun normalizeCode(c: String) = c.uppercase().filter { it.isLetterOrDigit() }
        fun isValidCode(c: String) = normalizeCode(c).let { it.length == CODE_LENGTH && it.all { ch -> ch in ALPHABET } }
        /** « K7P2QX4M » → « K7P2-QX4M », plus facile à dicter. */
        fun display(c: String) = normalizeCode(c).let { if (it.length == 8) it.take(4) + "-" + it.drop(4) else it }

        private fun sha256hex(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
