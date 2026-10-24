package app.undercover.game.online

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Transport du mode en ligne : relais MQTT publics, messages chiffrés (AES-GCM) avec une clé
 * dérivée du code de partie. Le relais ne voit que des données illisibles.
 * Sans dépendance Android : testable sur JVM avec un relais local.
 */
class OnlineNet(
    code: String,
    private val selfId: String,
    private val isHost: Boolean,
    private val listener: Listener,
    private val brokers: List<String> = PUBLIC_BROKERS
) {
    interface Listener {
        fun onState(s: OState) {}
        fun onSecret(s: OSecret) {}
        fun onAction(a: OAction) {}
        fun onHostGone() {}
        fun onConnection(connected: Boolean, broker: String?) {}
    }

    private val code = code.trim().uppercase()
    private val key = SecretKeySpec(sha256("undercover-cyberlogic:key:${this.code}"), "AES")
    private val base = "uc-cyberlogic/v1/" + sha256("undercover-cyberlogic:topic:${this.code}").joinToString("") { "%02x".format(it) }.take(24)
    private val tState = "$base/state"
    private val tAct = "$base/act"
    private val tHost = "$base/host"
    private fun tSecret(id: String) = "$base/p/" + sha256("p:$id").joinToString("") { "%02x".format(it) }.take(16)

    private val io = Executors.newSingleThreadExecutor { Thread(it, "undercover-net").apply { isDaemon = true } }
    private var client: MqttClient? = null
    private val rng = SecureRandom()
    @Volatile var broker: String? = null; private set
    @Volatile private var closed = false
    private val sentSecrets = mutableSetOf<String>()

    /** Se connecte au premier relais disponible (dans un fil d'arrière-plan). */
    fun connect() = io.execute {
        for (b in brokers) {
            if (closed) return@execute
            try {
                val c = MqttClient(b, "uc-" + selfId.take(12) + "-" + rng.nextInt(1_000_000), MemoryPersistence())
                val opt = MqttConnectOptions().apply {
                    isCleanSession = true
                    isAutomaticReconnect = true
                    connectionTimeout = 8
                    keepAliveInterval = 20
                    if (isHost) setWill(tHost, seal("gone"), 1, true)
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
                if (isHost) publishRaw(tHost, seal("alive"), true)
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

    private fun receive(topic: String, payload: ByteArray) {
        if (payload.isEmpty()) return
        val text = open(payload) ?: return   // message illisible (autre partie, clé différente…) : ignoré
        runCatching {
            when {
                topic == tAct -> listener.onAction(OAction.fromJson(JSONObject(text)))
                topic == tState -> listener.onState(OState.fromJson(JSONObject(text)))
                topic == tHost -> if (text == "gone") listener.onHostGone()
                topic == tSecret(selfId) -> listener.onSecret(OSecret.fromJson(JSONObject(text)))
            }
        }
    }

    // ------------------------------------------------------------ envoi

    fun sendAction(a: OAction) = io.execute { publishRaw(tAct, seal(a.toJson().toString()), false) }
    fun sendState(s: OState) = io.execute { publishRaw(tState, seal(s.toJson().toString()), true) }
    fun sendSecret(playerId: String, s: OSecret) = io.execute {
        sentSecrets += playerId
        publishRaw(tSecret(playerId), seal(s.toJson().toString()), true)
    }

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
                    publishRaw(tHost, seal("gone"), true)
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

    // ------------------------------------------------------------ chiffrement

    private fun seal(text: String): ByteArray {
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + c.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    private fun open(data: ByteArray): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        String(c.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        /** Relais publics gratuits, essayés dans l'ordre. */
        val PUBLIC_BROKERS = listOf(
            "ssl://broker.hivemq.com:8883",
            "ssl://broker.emqx.io:8883",
            "ssl://test.mosquitto.org:8886"
        )

        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        fun newCode(rnd: SecureRandom = SecureRandom()) = (1..6).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")
        fun isValidCode(c: String) = c.trim().uppercase().let { it.length == 6 && it.all { ch -> ch in ALPHABET } }

        private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
    }
}
