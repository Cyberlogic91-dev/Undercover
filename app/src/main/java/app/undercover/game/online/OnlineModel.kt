package app.undercover.game.online

import app.undercover.game.Outcome
import app.undercover.game.Role
import org.json.JSONArray
import org.json.JSONObject

/** Phases d'une partie en ligne. */
enum class OPhase { LOBBY, DEAL, CLUES, VOTE, REVEAL, MRWHITE, END }

/** Indices écrits dans l'appli, ou donnés à l'oral (visio). */
enum class ClueMode(val label: String) { WRITTEN("Écrits"), ORAL("À l'oral") }

data class OPlayer(
    val id: String,
    val name: String,
    val color: Int,
    val alive: Boolean = true,
    val connected: Boolean = true,
    val ready: Boolean = false,
    val voted: Boolean = false,
    /** Rôle : connu de tous seulement une fois éliminé ou en fin de partie. */
    val revealedRole: Role? = null
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", name).put("c", color).put("a", alive)
        .put("o", connected).put("r", ready).put("v", voted).put("rr", revealedRole?.name ?: "")

    companion object {
        fun fromJson(j: JSONObject) = OPlayer(
            j.getString("id"), j.getString("n"), j.getInt("c"), j.getBoolean("a"), j.getBoolean("o"),
            j.getBoolean("r"), j.getBoolean("v"), j.optString("rr").ifEmpty { null }?.let { Role.valueOf(it) }
        )
    }
}

/** État public de la partie, diffusé par l'hôte à tous les joueurs. */
data class OState(
    val seq: Long = 0,
    val phase: OPhase = OPhase.LOBBY,
    val hostId: String = "",
    val players: List<OPlayer> = emptyList(),
    val clueMode: ClueMode = ClueMode.WRITTEN,
    val adult: Boolean = false,
    val spicy: Boolean = false,
    val undercovers: Int = 0,
    val mrWhites: Int = 0,
    val round: Int = 0,
    val order: List<String> = emptyList(),
    val speaker: Int = 0,
    /** Indices du tour en cours, par joueur (mode écrit) ; en mode oral, « ✓ » quand c'est fait. */
    val clues: Map<String, String> = emptyMap(),
    /** Indices des tours précédents : liste de (tour, joueur, indice). */
    val history: List<Triple<Int, String, String>> = emptyList(),
    val tie: List<String> = emptyList(),
    val votesDone: Int = 0,
    val votesNeeded: Int = 0,
    val elimId: String? = null,
    val mrWhiteGuess: String? = null,
    val outcome: Outcome? = null,
    val civilWord: String? = null,
    val undercoverWord: String? = null,
    val message: String? = null
) {
    fun player(id: String?) = players.firstOrNull { it.id == id }
    val alive get() = players.filter { it.alive }
    val currentSpeakerId get() = order.getOrNull(speaker)

    fun toJson(): JSONObject = JSONObject()
        .put("seq", seq).put("ph", phase.name).put("h", hostId)
        .put("p", JSONArray(players.map { it.toJson() }))
        .put("cm", clueMode.name).put("ad", adult).put("sp", spicy)
        .put("uc", undercovers).put("mw", mrWhites).put("rd", round)
        .put("or", JSONArray(order)).put("sk", speaker)
        .put("cl", JSONObject(clues as Map<*, *>))
        .put("hi", JSONArray(history.map { (r, id, c) -> JSONArray().put(r).put(id).put(c) }))
        .put("ti", JSONArray(tie)).put("vd", votesDone).put("vn", votesNeeded)
        .put("el", elimId ?: "").put("mg", mrWhiteGuess ?: "").put("oc", outcome?.name ?: "")
        .put("cw", civilWord ?: "").put("uw", undercoverWord ?: "").put("msg", message ?: "")

    companion object {
        fun fromJson(j: JSONObject): OState {
            val p = j.getJSONArray("p"); val or = j.getJSONArray("or"); val ti = j.getJSONArray("ti")
            val cl = j.getJSONObject("cl"); val hi = j.getJSONArray("hi")
            return OState(
                j.getLong("seq"), OPhase.valueOf(j.getString("ph")), j.getString("h"),
                (0 until p.length()).map { OPlayer.fromJson(p.getJSONObject(it)) },
                ClueMode.valueOf(j.getString("cm")), j.getBoolean("ad"), j.getBoolean("sp"),
                j.getInt("uc"), j.getInt("mw"), j.getInt("rd"),
                (0 until or.length()).map { or.getString(it) }, j.getInt("sk"),
                cl.keys().asSequence().associateWith { cl.getString(it) },
                (0 until hi.length()).map { hi.getJSONArray(it).let { a -> Triple(a.getInt(0), a.getString(1), a.getString(2)) } },
                (0 until ti.length()).map { ti.getString(it) }, j.getInt("vd"), j.getInt("vn"),
                j.optString("el").ifEmpty { null }, j.optString("mg").ifEmpty { null },
                j.optString("oc").ifEmpty { null }?.let { Outcome.valueOf(it) },
                j.optString("cw").ifEmpty { null }, j.optString("uw").ifEmpty { null },
                j.optString("msg").ifEmpty { null }
            )
        }
    }
}

/** Information privée envoyée à un seul joueur : son rôle et son mot. */
data class OSecret(val round: Long, val role: Role, val word: String?) {
    fun toJson(): JSONObject = JSONObject().put("g", round).put("r", role.name).put("w", word ?: "")
    companion object {
        fun fromJson(j: JSONObject) = OSecret(j.getLong("g"), Role.valueOf(j.getString("r")), j.optString("w").ifEmpty { null })
    }
}

/** Action d'un joueur, envoyée à l'hôte. */
data class OAction(val type: String, val from: String, val name: String = "", val text: String = "", val target: String = "") {
    fun toJson(): JSONObject = JSONObject().put("t", type).put("f", from).put("n", name).put("x", text).put("g", target)
    companion object {
        fun fromJson(j: JSONObject) = OAction(j.getString("t"), j.getString("f"), j.optString("n"), j.optString("x"), j.optString("g"))
        const val JOIN = "join"
        const val PING = "ping"
        const val LEAVE = "leave"
        const val READY = "ready"
        const val CLUE = "clue"
        const val DONE = "done"     // mode oral : « j'ai donné mon indice »
        const val VOTE = "vote"
        const val GUESS = "guess"
    }
}
