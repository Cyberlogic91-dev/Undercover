package app.undercover.game.online

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographie du mode en ligne (API Java standard, disponible sur Android 8+ et sur JVM).
 *
 * - Clé de salle : PBKDF2-HMAC-SHA256 (lent) à partir du code → chiffre l'enveloppe et nomme les sujets.
 * - Chaque appareil a une paire de clés EC P-256 :
 *     · l'hôte signe l'état de la partie (ECDSA) → impossible de se faire passer pour lui ;
 *     · hôte ↔ joueur partagent une clé par ECDH + HKDF → les secrets (mot, rôle) et les actions
 *       (votes, indices) sont lisibles uniquement par l'hôte et le joueur concerné.
 */
object OnlineCrypto {
    private val rng = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    const val KDF_ITERATIONS = 120_000

    /** (clé de salle AES-256, identifiant de sujet) dérivés lentement du code. */
    fun roomSecrets(code: String): Pair<SecretKeySpec, String> {
        val spec = PBEKeySpec(code.toCharArray(), "undercover-cyberlogic/room/v2".toByteArray(), KDF_ITERATIONS, 512)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val key = SecretKeySpec(raw.copyOfRange(0, 32), "AES")
        val topic = raw.copyOfRange(32, 48).joinToString("") { "%02x".format(it) }
        return key to topic
    }

    // ------------------------------------------------------------ AES-GCM

    fun seal(key: SecretKeySpec, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        aad?.let { c.updateAAD(it) }
        return iv + c.doFinal(plain)
    }

    fun open(key: SecretKeySpec, data: ByteArray, aad: ByteArray? = null): ByteArray? = runCatching {
        if (data.size < 12 + 16) return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        aad?.let { c.updateAAD(it) }
        c.doFinal(data, 12, data.size - 12)
    }.getOrNull()

    // ------------------------------------------------------------ clés EC P-256

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), rng) }.generateKeyPair()

    fun encodePublic(k: PublicKey): String = b64.encodeToString(k.encoded)
    fun decodePublic(s: String): PublicKey? = runCatching {
        val bytes = unb64.decode(s)
        if (bytes.size > 200) return null
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }.getOrNull()

    fun encodePair(kp: KeyPair) = b64.encodeToString(kp.private.encoded) + ":" + b64.encodeToString(kp.public.encoded)
    fun decodePair(s: String): KeyPair? = runCatching {
        val (a, b) = s.split(":")
        val kf = KeyFactory.getInstance("EC")
        KeyPair(kf.generatePublic(X509EncodedKeySpec(unb64.decode(b))), kf.generatePrivate(PKCS8EncodedKeySpec(unb64.decode(a))))
    }.getOrNull()

    fun sign(priv: PrivateKey, data: ByteArray): String =
        b64.encodeToString(Signature.getInstance("SHA256withECDSA").apply { initSign(priv) ; update(data) }.sign())

    fun verify(pub: PublicKey, data: ByteArray, sig: String): Boolean = runCatching {
        Signature.getInstance("SHA256withECDSA").apply { initVerify(pub); update(data) }.verify(unb64.decode(sig))
    }.getOrDefault(false)

    /** Clé partagée hôte ↔ joueur (ECDH + HKDF-SHA256), liée à la salle. */
    fun pairKey(mine: PrivateKey, theirs: PublicKey, roomTopic: String): SecretKeySpec {
        val ka = KeyAgreement.getInstance("ECDH").apply { init(mine); doPhase(theirs, true) }
        val shared = ka.generateSecret()
        return SecretKeySpec(hkdf(shared, roomTopic.toByteArray(), "undercover/pair/v2".toByteArray()), "AES")
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val prk = hmac(salt, ikm)
        return hmac(prk, info + byteArrayOf(1)).copyOf(32)
    }

    fun b64(bytes: ByteArray): String = b64.encodeToString(bytes)
    fun unb64(s: String): ByteArray? = runCatching { unb64.decode(s) }.getOrNull()
}
