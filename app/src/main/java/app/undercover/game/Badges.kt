package app.undercover.game

/** Un badge à débloquer, calculé à partir de l'historique des parties d'un joueur. */
data class Badge(val id: String, val emoji: String, val name: String, val desc: String, val unlocked: (PlayerHistory) -> Boolean)

/** Une participation d'un joueur à une partie. */
data class Participation(val role: Role, val won: Boolean, val elimRound: Int, val saved: Boolean, val time: Long, val rounds: Int)

/** Toutes les participations d'un joueur, de la plus ancienne à la plus récente. */
class PlayerHistory(val name: String, val games: List<Participation>) {
    val wins get() = games.count { it.won }
    fun winsAs(r: Role) = games.count { it.role == r && it.won }
    fun maxStreak(): Int {
        var best = 0; var cur = 0
        games.forEach { if (it.won) { cur++; best = maxOf(best, cur) } else cur = 0 }
        return best
    }
}

object Badges {
    val all = listOf(
        Badge("first", "🎉", "Bienvenue", "Jouer sa première partie") { it.games.isNotEmpty() },
        Badge("regular", "🎲", "Habitué", "Jouer 10 parties") { it.games.size >= 10 },
        Badge("veteran", "🏅", "Vétéran", "Jouer 50 parties") { it.games.size >= 50 },
        Badge("liar", "🤥", "Menteur pro", "Gagner 3 fois en Undercover") { it.winsAs(Role.UNDERCOVER) >= 3 },
        Badge("master", "👑", "Maître du bluff", "Gagner 10 fois en Undercover") { it.winsAs(Role.UNDERCOVER) >= 10 },
        Badge("ghost", "👻", "Fantôme", "Gagner en Mr. White") { it.winsAs(Role.MRWHITE) >= 1 },
        Badge("pillar", "🏛️", "Pilier du village", "Gagner 10 fois en Civil") { it.winsAs(Role.CIVIL) >= 10 },
        Badge("streak", "🔥", "En feu", "Gagner 3 parties d'affilée") { it.maxStreak() >= 3 },
        Badge("allround", "🎯", "Caméléon", "Gagner dans les 3 rôles") { h -> Role.entries.all { h.winsAs(it) >= 1 } },
        Badge("survivor", "🧊", "Intouchable", "Finir 5 parties sans être éliminé") { h -> h.games.count { it.elimRound == 0 } >= 5 },
        Badge("martyr", "😇", "Martyr", "Être éliminé au 1er tour en étant Civil, 3 fois") { h ->
            h.games.count { it.role == Role.CIVIL && it.elimRound == 1 } >= 3
        },
        Badge("hero", "🛡️", "Héros", "Sauver un joueur en Garde du corps") { h -> h.games.any { it.saved } },
        Badge("lucky", "🍀", "Miraculé", "Gagner en Undercover dans une partie de 4 tours ou plus") { h ->
            h.games.any { it.role == Role.UNDERCOVER && it.won && it.rounds >= 4 }
        }
    )

    fun historyOf(name: String, records: List<GameRecord>): PlayerHistory =
        PlayerHistory(name, records.sortedBy { it.time }.mapNotNull { g ->
            val i = g.players.indexOfFirst { it.first == name }
            if (i < 0) null else {
                val (_, r, w) = g.players[i]
                Participation(r, w, g.elim.getOrElse(i) { 0 }, g.saver == name, g.time, g.rounds)
            }
        })

    fun unlocked(name: String, records: List<GameRecord>): Set<String> {
        val h = historyOf(name, records)
        return all.filter { it.unlocked(h) }.map { it.id }.toSet()
    }

    fun byId(id: String) = all.first { it.id == id }
}
