package com.babelmanager.app.data.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.babelmanager.app.data.autofill.AppUri
import com.babelmanager.app.data.autofill.EntryMatcher
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ícone (favicon) do site de uma entrada. Baixado do próprio site (`https://<domínio>/...`), uma vez, e guardado
 * em `filesDir/site-icons` (não no cache: "limpar cache" não apaga). Só o domínio da entrada vai na requisição.
 */
class SiteIcons(context: Context) {
    private val dir = File(context.filesDir, "site-icons").apply { mkdirs() }

    /** Domínio válido para buscar, ou null (app, IP, "localhost", texto sem ponto...). */
    fun hostFor(url: String): String? {
        if (url.isBlank() || AppUri.packageOf(url) != null) return null
        val host = EntryMatcher.hostOf(url)
        val ok = host.length in 4..253 && '.' in host &&
            host.all { it.isLetterOrDigit() || it == '.' || it == '-' } &&
            !host.startsWith('.') && !host.endsWith('.') &&
            !host.all { it.isDigit() || it == '.' }
        return if (ok) host else null
    }

    /** Ícone guardado no aparelho (sem rede), ou null. */
    fun cached(host: String): Bitmap? {
        val file = File(dir, "$host.png")
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    /** Baixa e guarda. Quem não tem ícone é lembrado por 7 dias para não repetir a consulta. */
    fun fetch(host: String): Bitmap? {
        cached(host)?.let { return it }
        val miss = File(dir, "$host.miss")
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < RETRY_MS) return null
        val candidates = linkedSetOf<String>()
        candidates += declaredIcons(host)
        candidates += "https://$host/apple-touch-icon.png"
        candidates += "https://$host/favicon.ico"
        for (address in candidates.take(MAX_TRIES)) {
            val bitmap = downloadImage(address) ?: continue
            File(dir, "$host.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        miss.writeText("")
        return null
    }

    /** Ícones que a página inicial declara (`<link rel="icon">`, `apple-touch-icon`), do melhor para o pior. */
    private fun declaredIcons(host: String): List<String> {
        val page = download("https://$host/", HTML_BYTES) ?: return emptyList()
        val html = String(page.bytes, Charsets.UTF_8)
        return LINK_TAG.findAll(html).mapNotNull { tag ->
            val attrs = ATTR.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val rel = attrs["rel"]?.lowercase() ?: return@mapNotNull null
            val href = attrs["href"] ?: return@mapNotNull null
            if ("icon" !in rel || "mask-icon" in rel) return@mapNotNull null
            if (href.substringBefore('?').endsWith(".svg", ignoreCase = true) || attrs["type"]?.contains("svg") == true) return@mapNotNull null
            val resolved = runCatching { URL(page.finalUrl, href.trim()) }.getOrNull() ?: return@mapNotNull null
            if (resolved.protocol != "https" || hostFor(resolved.toString()) == null) return@mapNotNull null
            val size = attrs["sizes"]?.substringBefore('x')?.trim()?.toIntOrNull() ?: 0
            val score = (if ("apple-touch" in rel) 1000 else 0) + size.coerceAtMost(512)
            score to resolved.toString()
        }.sortedByDescending { it.first }.map { it.second }.distinct().toList()
    }

    private class Download(val bytes: ByteArray, val finalUrl: URL)

    private fun downloadImage(address: String): Bitmap? {
        val d = download(address, MAX_BYTES) ?: return null
        return BitmapFactory.decodeByteArray(d.bytes, 0, d.bytes.size)?.takeIf { it.width >= 16 && it.height >= 16 }
    }

    private fun download(address: String, limit: Int): Download? {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            // Muitos sites recusam clientes sem navegador; o pedido leva só o endereço, sem cookies.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = connection.inputStream.use { readLimited(it, limit) }
            if (bytes.isEmpty()) null else Download(bytes, connection.url)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Apaga os ícones guardados (para a pessoa que desliga a opção). */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        val ATTR = Regex("([a-zA-Z-]+)\\s*=\\s*[\"']([^\"']*)[\"']")
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        const val MAX_TRIES = 4
        const val HTML_BYTES = 256 * 1024
        const val MAX_BYTES = 512 * 1024
        const val TIMEOUT_MS = 6_000
        const val RETRY_MS = 7L * 24 * 60 * 60 * 1000
    }
}
