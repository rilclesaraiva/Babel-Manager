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
        val none = File(dir, "$host.none")
        if (none.exists() && System.currentTimeMillis() - none.lastModified() < RETRY_MS) return null
        for (path in PATHS) {
            val bitmap = download("https://$host$path") ?: continue
            File(dir, "$host.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        none.writeText("")
        return null
    }

    private fun download(address: String): Bitmap? {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "BabelManager")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = connection.inputStream.use { readLimited(it, MAX_BYTES + 1) }
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.takeIf { it.width >= 16 && it.height >= 16 }
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
        val PATHS = listOf("/apple-touch-icon.png", "/favicon.ico")
        const val MAX_BYTES = 512 * 1024
        const val TIMEOUT_MS = 6_000
        const val RETRY_MS = 7L * 24 * 60 * 60 * 1000
    }
}
