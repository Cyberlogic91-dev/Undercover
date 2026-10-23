package app.undercover.game

import android.content.Context

/** Une paire de mots. [hard] = mots très proches (difficile). */
data class WordPair(val a: String, val b: String, val category: String, val hard: Boolean) {
    /** Clé stable, indépendante de l'ordre, pour l'anti-doublon. */
    val key get() = listOf(a.lowercase(), b.lowercase()).sorted().joinToString("|")
}

enum class Difficulty(val label: String) { EASY("Facile"), MIXED("Mixte"), HARD("Difficile") }

/**
 * Base de mots chargée depuis assets/words.txt.
 * Format : « # Catégorie » (ou « #! Catégorie » pour une catégorie adulte 18+),
 * puis une paire par ligne « Mot1|Mot2 », suivie de « ! » si elle est difficile.
 */
object Words {
    var pairs: List<WordPair> = emptyList(); private set
    var categories: List<String> = emptyList(); private set
    var adultCategories: Set<String> = emptySet(); private set
    /** Définitions courtes, pour le bouton « i ». */
    private var defs: Map<String, String> = emptyMap()

    fun definition(word: String): String? = defs[word] ?: defs[word.lowercase()]

    fun load(ctx: Context) {
        if (pairs.isNotEmpty()) return
        val list = mutableListOf<WordPair>()
        val cats = mutableListOf<String>()
        val adult = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        var cat = "Divers"
        ctx.assets.open("words.txt").bufferedReader().useLines { lines ->
            lines.map { it.trim() }.forEach { line ->
                when {
                    line.startsWith("#!") -> { cat = line.removePrefix("#!").trim(); cats += cat; adult += cat }
                    line.startsWith("#") -> { cat = line.removePrefix("#").trim(); cats += cat }
                    line.contains('|') -> {
                        val hard = line.endsWith("!")
                        val p = line.removeSuffix("!").split('|')
                        if (p.size == 2 && p[0].isNotBlank() && p[1].isNotBlank()) {
                            val w = WordPair(p[0].trim(), p[1].trim(), cat, hard)
                            if (seen.add(w.key)) list += w
                        }
                    }
                }
            }
        }
        pairs = list; categories = cats; adultCategories = adult
        defs = runCatching {
            val m = HashMap<String, String>()
            ctx.assets.open("definitions.txt").bufferedReader().useLines { lines ->
                lines.forEach { l ->
                    val i = l.indexOf('|')
                    if (i > 0) { val w = l.substring(0, i).trim(); val d = l.substring(i + 1).trim(); m[w] = d; m.putIfAbsent(w.lowercase(), d) }
                }
            }
            m
        }.getOrDefault(emptyMap())
    }

    /** Catégories de la « Partie coquine » : uniquement les mots coquins. */
    val spicyCategories: Set<String> get() = adultCategories.filter { it.contains("Coquin", ignoreCase = true) }.toSet().ifEmpty { adultCategories }

    fun visibleCategories(adultMode: Boolean) = categories.filter { adultMode || it !in adultCategories }

    /** Paires correspondant aux réglages ; si rien ne correspond, on élargit progressivement. */
    fun filter(cats: Set<String>, diff: Difficulty, adultMode: Boolean): List<WordPair> {
        val allowed = pairs.filter { adultMode || it.category !in adultCategories }
        val byCat = allowed.filter { it.category in cats }.ifEmpty { allowed }
        val byDiff = when (diff) {
            Difficulty.EASY -> byCat.filter { !it.hard }
            Difficulty.HARD -> byCat.filter { it.hard }
            Difficulty.MIXED -> byCat
        }
        return byDiff.ifEmpty { byCat }
    }
}
