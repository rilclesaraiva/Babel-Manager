package com.babelmanager.app.data.importer

import com.babelmanager.app.data.vault.Entry
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.util.UUID

data class ImportResult(val entries: List<Entry>, val skipped: Int)

class ImportFormatException : Exception("Unrecognized file format")

/**
 * Lê exportações em CSV (Google Gerenciador de Senhas, Chrome, Bitwarden, Proton Pass e
 * parecidos, reconhecidos pelos nomes das colunas) e JSON do Bitwarden.
 */
object PasswordImporter {
    private val titleKeys = listOf("name", "title", "login_name")
    private val urlKeys = listOf("url", "login_uri", "website", "web site", "uri")
    private val userKeys = listOf("username", "login_username", "login", "user", "email")
    private val passKeys = listOf("password", "login_password")
    private val noteKeys = listOf("note", "notes", "extra", "comment")

    fun parse(text: String, newId: () -> String = { UUID.randomUUID().toString() }): ImportResult {
        val clean = text.removePrefix("﻿").trim()
        if (clean.isEmpty()) throw ImportFormatException()
        return if (clean.startsWith("{")) parseBitwardenJson(clean, newId) else parseCsvText(clean, newId)
    }

    private fun parseCsvText(text: String, newId: () -> String): ImportResult {
        val rows = parseCsv(text)
        if (rows.size < 2) throw ImportFormatException()
        val header = rows.first().map { it.trim().lowercase() }
        fun index(keys: List<String>) = keys.firstNotNullOfOrNull { k -> header.indexOf(k).takeIf { it >= 0 } }

        val passIndex = index(passKeys) ?: throw ImportFormatException()
        val titleIndex = index(titleKeys)
        val urlIndex = index(urlKeys)
        val noteIndex = index(noteKeys)
        // "username" tem prioridade; "email" só entra se não houver nome de usuário preenchido.
        val userIndexes = userKeys.mapNotNull { k -> header.indexOf(k).takeIf { it >= 0 } }

        val entries = ArrayList<Entry>()
        var skipped = 0
        for (row in rows.drop(1)) {
            fun cell(i: Int?) = if (i != null && i < row.size) row[i].trim() else ""
            val password = if (passIndex < row.size) row[passIndex] else ""
            val username = userIndexes.map { cell(it) }.firstOrNull { it.isNotEmpty() } ?: ""
            if (password.isEmpty() && username.isEmpty()) {
                skipped++
                continue
            }
            val url = cell(urlIndex)
            entries += Entry(
                id = newId(),
                title = cell(titleIndex).ifEmpty { hostOf(url) }.ifEmpty { username },
                username = username,
                password = password,
                url = url,
                note = if (noteIndex != null && noteIndex < row.size) row[noteIndex] else "",
            )
        }
        return ImportResult(entries, skipped)
    }

    private fun parseBitwardenJson(text: String, newId: () -> String): ImportResult {
        try {
            val items = JSONObject(text).getJSONArray("items")
            val entries = ArrayList<Entry>()
            var skipped = 0
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val login = item.optJSONObject("login")
                if (login == null) {
                    skipped++
                    continue
                }
                val uris = login.optJSONArray("uris")
                val url = uris?.optJSONObject(0)?.optString("uri").orEmpty()
                val username = login.optString("username").takeIf { it != "null" }.orEmpty()
                val password = login.optString("password").takeIf { it != "null" }.orEmpty()
                if (username.isEmpty() && password.isEmpty()) {
                    skipped++
                    continue
                }
                entries += Entry(
                    id = newId(),
                    title = item.optString("name").ifEmpty { hostOf(url) }.ifEmpty { username },
                    username = username,
                    password = password,
                    url = url,
                    note = item.optString("notes").takeIf { it != "null" }.orEmpty(),
                )
            }
            return ImportResult(entries, skipped)
        } catch (e: JSONException) {
            throw ImportFormatException()
        }
    }

    internal fun hostOf(url: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            val withScheme = if ("://" in url) url else "https://$url"
            URI(withScheme).host?.removePrefix("www.").orEmpty()
        }.getOrDefault("")
    }

    /** CSV conforme a RFC 4180: aspas duplas, "" como aspas literais e quebras de linha dentro de campos. */
    internal fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field.setLength(0) }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.setLength(0)
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotEmpty() }) rows.add(row)
        }
        return rows
    }
}

object PasswordExporter {
    /** CSV no formato do Chrome/Google, que o Bitwarden e outros também importam. SEM criptografia. */
    fun toCsv(entries: List<Entry>): String = buildString {
        append("name,url,username,password,note\n")
        for (e in entries) {
            append(listOf(e.title, e.url, e.username, e.password, e.note).joinToString(",") { quote(it) })
            append('\n')
        }
    }

    private fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
}
