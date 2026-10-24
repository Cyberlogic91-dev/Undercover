package app.undercover.game.online

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.undercover.game.RolePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/** État et actions du mode en ligne, côté téléphone (hôte ou invité). */
class OnlineViewModel(app: Application) : AndroidViewModel(app) {
    enum class Role { NONE, HOST, GUEST }
    enum class Link { IDLE, CONNECTING, ONLINE, RECONNECTING, FAILED }

    private val prefs = app.getSharedPreferences("undercover_online", Context.MODE_PRIVATE)
    val myId: String = prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }
    var myName by mutableStateOf(prefs.getString("name", "") ?: ""); private set
    fun changeName(n: String) { myName = n.take(16); prefs.edit().putString("name", myName).apply() }

    var role by mutableStateOf(Role.NONE); private set
    var code by mutableStateOf(""); private set
    var link by mutableStateOf(Link.IDLE); private set
    var broker by mutableStateOf<String?>(null); private set
    var state by mutableStateOf<OState?>(null); private set
    var secret by mutableStateOf<OSecret?>(null); private set
    var hostGone by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var adultConfirmed by mutableStateOf(false)

    private var host: OnlineHost? = null
    private var net: OnlineNet? = null
    private var loop: Job? = null

    val isHost get() = role == Role.HOST
    val me get() = state?.player(myId)
    val joined get() = me != null

    private fun onMain(f: () -> Unit) { viewModelScope.launch(Dispatchers.Main) { f() } }

    // ------------------------------------------------------------ créer / rejoindre

    /** Crée une partie ; [pickPair] tire une paire de mots (catégories, difficulté, mots déjà joués…). */
    fun create(pickPair: (spicy: Boolean) -> Pair<String, String>) {
        if (role != Role.NONE) return
        val c = OnlineNet.newCode()
        reset(); role = Role.HOST; code = c; link = Link.CONNECTING; adultConfirmed = true
        val n = OnlineNet(c, myId, true, object : OnlineNet.Listener {
            override fun onAction(a: OAction) = onMain { host?.handle(a) }
            override fun onConnection(connected: Boolean, broker: String?) = onMain { connectionChanged(connected, broker) }
        })
        net = n
        host = OnlineHost(myId, myName.ifBlank { "Hôte" }, { spicy -> pickPair(spicy) },
            { s -> state = s; net?.sendState(s) },
            { id, s -> if (id == myId) secret = s else net?.sendSecret(id, s) })
        n.connect()
        loop = viewModelScope.launch { while (isActive) { delay(5000); host?.tick() } }
    }

    fun join(rawCode: String) {
        if (role != Role.NONE) return
        val c = rawCode.trim().uppercase()
        if (!OnlineNet.isValidCode(c)) { error = "Code invalide : 6 lettres ou chiffres"; return }
        reset(); role = Role.GUEST; code = c; link = Link.CONNECTING
        val n = OnlineNet(c, myId, false, object : OnlineNet.Listener {
            override fun onState(s: OState) = onMain {
                if (s.seq >= (state?.seq ?: -1) || s.hostId != state?.hostId) state = s
                if (s.phase == OPhase.LOBBY) secret = null
            }
            override fun onSecret(s: OSecret) = onMain { secret = s }
            override fun onHostGone() = onMain { if (state != null) hostGone = true }
            override fun onConnection(connected: Boolean, broker: String?) = onMain { connectionChanged(connected, broker) }
        })
        net = n
        n.connect()
        // on (re)demande à rejoindre tant qu'on n'apparaît pas dans la liste, puis on signale sa présence
        loop = viewModelScope.launch {
            var tries = 0
            while (isActive) {
                if (link == Link.ONLINE) {
                    if (!joined) {
                        net?.sendAction(OAction(OAction.JOIN, myId, name = myName.ifBlank { "Joueur" }))
                        tries++
                        if (tries == 6 && state == null) error = "Aucune partie trouvée avec ce code. Vérifie-le avec l'hôte."
                        if (tries > 0 && state != null && state?.phase != OPhase.LOBBY && !joined)
                            error = "La partie a déjà commencé. Attends la suivante !"
                    } else net?.sendAction(OAction(OAction.PING, myId))
                }
                delay(if (joined) 8000 else 2500)
            }
        }
    }

    private fun connectionChanged(connected: Boolean, b: String?) {
        broker = b
        link = when {
            connected -> Link.ONLINE
            b == null -> Link.FAILED
            else -> Link.RECONNECTING
        }
        if (connected && role == Role.HOST) host?.let { net?.sendState(it.state) }
        if (link == Link.FAILED) error = "Impossible de joindre les relais en ligne. Vérifie ta connexion internet."
    }

    // ------------------------------------------------------------ actions

    private fun act(type: String, text: String = "", target: String = "") {
        val a = OAction(type, myId, myName, text, target)
        if (isHost) host?.handle(a) else net?.sendAction(a)
    }

    fun ready() = act(OAction.READY)
    fun sendClue(t: String) = act(OAction.CLUE, text = t)
    fun doneSpeaking() = act(OAction.DONE)
    fun vote(target: String) = act(OAction.VOTE, target = target)
    fun guess(t: String) = act(OAction.GUESS, text = t)

    // réservé à l'hôte
    fun start(): String? = host?.start()
    fun advance() = host?.advance()
    fun skipSpeaker() = host?.skipSpeaker()
    fun restart() = host?.restart()
    fun kick(id: String) = host?.kick(id)
    fun setClueMode(m: ClueMode) = host?.setClueMode(m)
    fun setSpicy(adult: Boolean, spicy: Boolean) = host?.setAdult(adult, spicy)
    fun setPreset(p: RolePreset) = host?.setRolePreset(p)
    val preset get() = host?.preset ?: RolePreset.BALANCED
    fun rolesPreview(n: Int) = host?.rolesFor(n) ?: (1 to 0)

    /** Quitte la partie (l'hôte la ferme pour tout le monde). */
    fun leave() {
        if (role == Role.GUEST) net?.sendAction(OAction(OAction.LEAVE, myId))
        net?.close()
        reset()
    }

    private fun reset() {
        loop?.cancel(); loop = null
        net = null; host = null
        role = Role.NONE; code = ""; link = Link.IDLE; broker = null
        state = null; secret = null; hostGone = false; error = null; adultConfirmed = false
    }

    override fun onCleared() { net?.close() }
}
