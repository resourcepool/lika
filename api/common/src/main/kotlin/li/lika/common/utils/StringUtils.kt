package li.lika.common.utils

import java.text.Normalizer
import kotlin.math.min

object StringUtils {

    val ideas = listOf("surprise", "seminar", "show", "teambuilding", "teamgathering", "gathering", "meeting", "opening", "party")

    /**
     * Normalize a specific string only [A-Za-z0-9]+
     */
    fun normalizeAlphaNumeric(word: String, replacement: String = "-"): String {
        return Normalizer
                .normalize(word.lowercase(), Normalizer.Form.NFD)
                .replace("[^a-zA-Z0-9\\s\\-]".toRegex(), "")
                .replace("\\s+".toRegex(), replacement)
                .replace("[/\\\\]".toRegex(), "")
                .lowercase()
    }

    fun suggestAlternatives(normalizedString: String, qty: Int = 10): List<String> {
        val results = ArrayList<String>(qty)
        val randomIdeas = ideas.shuffled()
        for (i in 0..min(randomIdeas.size-1, qty)) {
            results.add(normalizedString + "-" + randomIdeas[i])
        }
        if (randomIdeas.size < qty) {
            val seed = Math.floor(Math.random() * 1000)
            for (i in 0..(qty - randomIdeas.size)) {
                results.add(normalizedString + "-" + (seed + i))
            }
        }
        return results
    }

    fun getRandomString(length: Int): String {
        val charset = "abcdefghiklmnopqrstuvwxyz"
        return (1..length)
                .map { charset.random() }
                .joinToString("")
    }


    fun generateSlugs(qty: Int = 10): List<String> {
        val results = ArrayList<String>(qty)
        for (i in 0..qty) {
            results.add(getRandomString(6))
        }
        return results
    }
}
