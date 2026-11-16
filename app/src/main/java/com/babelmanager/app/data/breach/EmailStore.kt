package com.babelmanager.app.data.breach

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Um e-mail cadastrado para a verificação e o último resultado (`breaches` nulo = ainda não verificado). */
data class WatchedEmail(val email: String, val breaches: List<String>? = null, val checkedAt: Long = 0L)

/**
 * Guarda os e-mails da verificação num arquivo cifrado por uma chave do Android Keystore (não exige biometria,
 * pois a verificação não depende do cofre aberto). Nada em texto aberto no disco; apagado ao excluir o cofre.
 */
class EmailStore(private val file: File) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun save(items: List<WatchedEmail>) {
        if (items.isEmpty()) return clear()
        val array = JSONArray()
        for (i in items) {
            val o = JSONObject().put("email", i.email).put("checkedAt", i.checkedAt)
            i.breaches?.let { o.put("breaches", JSONArray(it)) }
            array.put(o)
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val sealed = cipher.iv + cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(sealed)
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (e: GeneralSecurityException) {
            clear()
        }
    }

    @Synchronized
    fun load(): List<WatchedEmail> {
        if (!file.exists()) return emptyList()
        return try {
            val bytes = file.readBytes()
            if (bytes.size <= IV_SIZE) return emptyList()
            val secret = keyStore.getKey(ALIAS, null) as? SecretKey ?: return emptyList()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, bytes, 0, IV_SIZE))
            }
            val array = JSONArray(String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8))
            List(array.length()) { n ->
                val o = array.getJSONObject(n)
                val list = o.optJSONArray("breaches")
                WatchedEmail(
                    email = o.getString("email"),
                    breaches = list?.let { a -> List(a.length()) { a.getString(it) } },
                    checkedAt = o.optLong("checkedAt"),
                )
            }
        } catch (e: GeneralSecurityException) {
            emptyList()
        } catch (e: JSONException) {
            emptyList()
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        runCatching { keyStore.deleteEntry(ALIAS) }
    }

    private fun key(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ALIAS = "babel_email_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
