package com.babelmanager.app.data.breach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Verifica senhas contra a base pública "Have I Been Pwned: Pwned Passwords" com k-anonymity: de cada senha só os
 * 5 primeiros caracteres do hash SHA-1 vão para a rede; a comparação do resto é feita aqui. A senha, o hash completo
 * e qualquer dado do cofre nunca saem do aparelho. `Add-Padding` esconde quantos resultados correspondem de fato.
 */
class PwnedPasswords {

    /** Quantas vezes cada senha aparece em vazamentos conhecidos (só as que aparecem). Null se a consulta falhar. */
    suspend fun check(passwords: Collection<String>): Map<String, Int>? = withContext(Dispatchers.IO) {
        val hashes = passwords.distinct().associateWith { sha1(it) }
        val result = HashMap<String, Int>()
        try {
            for ((prefix, group) in hashes.entries.groupBy { it.value.substring(0, 5) }) {
                val counts = range(prefix) ?: return@withContext null
                for ((password, hash) in group) counts[hash.substring(5)]?.let { if (it > 0) result[password] = it }
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun range(prefix: String): Map<String, Int>? {
        val connection = URL("https://api.pwnedpasswords.com/range/$prefix").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Add-Padding", "true")
            connection.setRequestProperty("User-Agent", "BabelManager")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.trim().split(':')
                    if (parts.size == 2) parts[0].uppercase() to (parts[1].toIntOrNull() ?: 0) else null
                }.toMap()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
