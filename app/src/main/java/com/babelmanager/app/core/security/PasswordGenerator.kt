package com.babelmanager.app.core.security

import java.security.SecureRandom
import kotlin.math.log2

enum class Separator(val value: String) {
    Hyphen("-"), Space(" "), Period("."), Comma(","), Underscore("_"), None("")
}

data class RandomOptions(
    val length: Int = 16,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
)

data class MemorableOptions(
    val words: Int = 6,
    val capitalize: Boolean = true,
    val numbers: Boolean = true,
    val separator: Separator = Separator.Hyphen,
)

class PasswordGenerator(
    private val random: SecureRandom = SecureRandom(),
    private val words: List<String> = WordList.words,
) {
    fun random(options: RandomOptions): String {
        val sets = randomSets(options)
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val all = sets.joinToString("")
        val chars = CharArray(length)
        // Garante ao menos um caractere de cada grupo escolhido.
        sets.forEachIndexed { i, set -> chars[i] = set[random.nextInt(set.length)] }
        for (i in sets.size until length) chars[i] = all[random.nextInt(all.length)]
        // Fisher-Yates
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return String(chars)
    }

    fun memorable(options: MemorableOptions): String {
        val count = options.words.coerceIn(MIN_WORDS, MAX_WORDS)
        return List(count) {
            var word = words[random.nextInt(words.size)]
            if (options.capitalize) word = word.replaceFirstChar { c -> c.uppercase() }
            if (options.numbers) word += random.nextInt(10)
            word
        }.joinToString(options.separator.value)
    }

    fun randomEntropyBits(options: RandomOptions): Double {
        val size = randomSets(options).sumOf { it.length }
        return options.length.coerceIn(MIN_LENGTH, MAX_LENGTH) * log2(size.toDouble())
    }

    fun memorableEntropyBits(options: MemorableOptions): Double {
        val count = options.words.coerceIn(MIN_WORDS, MAX_WORDS)
        return count * log2(words.size.toDouble()) + if (options.numbers) count * log2(10.0) else 0.0
    }

    private fun randomSets(options: RandomOptions): List<String> =
        buildList {
            if (options.lower) add(LOWER)
            if (options.upper) add(UPPER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }.ifEmpty { listOf(LOWER) }

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64
        const val MIN_WORDS = 3
        const val MAX_WORDS = 10
        private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
        private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val DIGITS = "23456789"
        private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?"
    }
}
