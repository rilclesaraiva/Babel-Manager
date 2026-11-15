package com.babelmanager.app.data.about

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foto pública da pessoa que mantém o projeto, mostrada só na tela Sobre. Baixada de
 * `github.com/<usuário>.png` e guardada em cache por 7 dias; sem rede, usa o cache ou nada.
 * Nenhum dado do cofre participa desse pedido.
 */
class AvatarStore(private val cacheDir: File, private val user: String, private val userAgent: String) {

    suspend fun load(): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "avatar-$user.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS
        if (!fresh) {
            try {
                download(file)
            } catch (e: IOException) {
                // Sem rede ou GitHub fora do ar: segue com o cache, se existir.
            }
        }
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    private fun download(target: File) {
        val connection = URL("https://github.com/$user.png?size=160").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return
            val tmp = File(target.parentFile, target.name + ".tmp")
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) tmp.delete()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val TIMEOUT_MS = 8_000
    }
}
