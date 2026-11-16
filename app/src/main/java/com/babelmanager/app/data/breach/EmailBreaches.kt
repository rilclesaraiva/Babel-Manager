package com.babelmanager.app.data.breach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Consulta opcional de e-mails no XposedOrNot (API pública, sem chave). Diferente da verificação de senhas, **o
 * e-mail inteiro vai para esse serviço**: não existe versão parcial. Por isso só roda com e-mails que a pessoa
 * cadastrou de propósito e nunca envia senha, usuário de outra entrada nem qualquer dado do cofre.
 */
class EmailBreaches {

    /** Nomes dos vazamentos em que o e-mail aparece (vazio = nenhum). Null se a consulta falhar. */
    suspend fun check(email: String): List<String>? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.xposedornot.com/v1/check-email/" + URLEncoder.encode(email, "UTF-8"))
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "BabelManager")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> emptyList()
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    names(JSONObject(body).opt("breaches")).distinct()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    // A resposta traz listas aninhadas ([["Adobe"], ["LinkedIn"]]): achata tudo em nomes.
    private fun names(node: Any?): List<String> = when (node) {
        is JSONArray -> (0 until node.length()).flatMap { names(node.opt(it)) }
        is String -> listOf(node).filter { it.isNotBlank() }
        else -> emptyList()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
