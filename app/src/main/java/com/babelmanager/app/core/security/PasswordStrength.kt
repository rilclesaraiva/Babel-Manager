package com.babelmanager.app.core.security

import kotlin.math.log2

enum class StrengthLevel {
    Weak, Fair, Strong, VeryStrong;

    companion object {
        fun fromBits(bits: Double): StrengthLevel = when {
            bits < 45 -> Weak
            bits < 65 -> Fair
            bits < 90 -> Strong
            else -> VeryStrong
        }
    }
}

data class Strength(val bits: Double, val level: StrengthLevel)

/**
 * Estimativa simples de força, feita no aparelho. É uma heurística (tamanho, variedade de
 * caracteres, repetição e sequências), não uma garantia: palavras de dicionário e padrões
 * pessoais não são detectados.
 */
object PasswordStrength {
    private val common = setOf(
        "password", "password1", "123456", "12345678", "123456789", "qwerty", "qwertyuiop",
        "abc123", "senha", "senha123", "admin", "letmein", "iloveyou", "111111", "123123",
        "welcome", "654321", "1q2w3e4r", "mudar123",
    )

    fun estimate(password: String): Strength {
        if (password.isEmpty()) return Strength(0.0, StrengthLevel.Weak)
        if (password.lowercase() in common) return Strength(5.0, StrengthLevel.Weak)

        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { it.code < 128 && !it.isLetterOrDigit() }) pool += 33
        if (password.any { it.code >= 128 }) pool += 40
        if (pool == 0) pool = 10

        // Cada caractere vale 1; repetido ou em sequência (abc, 123) vale só 0,3.
        val seen = HashSet<Char>()
        var effective = 0.0
        var previous: Char? = null
        for (c in password) {
            val sequential = previous != null && kotlin.math.abs(c.code - previous.code) == 1
            effective += if (!seen.add(c) || sequential) 0.3 else 1.0
            previous = c
        }

        val bits = effective * log2(pool.toDouble())
        val level = StrengthLevel.fromBits(bits)
        return Strength(bits, if (password.length < 8) StrengthLevel.Weak else level)
    }
}
