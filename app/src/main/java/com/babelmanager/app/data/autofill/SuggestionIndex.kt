package com.babelmanager.app.data.autofill

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.babelmanager.app.data.vault.Entry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Índice opcional para o preenchimento mostrar as sugestões com o cofre trancado. Guarda só título,
 * usuário e site (**nunca a senha**), cifrado por uma chave do Android Keystore que não exige
 * biometria (o app a usa sem desbloquear o cofre). A senha continua só no cofre, que abre
 * com a biometria ou a senha mestra.
 */
class SuggestionIndex(private val file: File) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun save(entries: List<Entry>) {
        val array = JSONArray()
        for (e in entries) {
            array.put(
                JSONObject().put("id", e.id).put("title", e.title).put("username", e.username).put("url", e.url),
            )
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val sealed = cipher.iv + cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(sealed)
            if (!tmp.renameTo(file)) {
                tmp.delete()
            }
        } catch (e: GeneralSecurityException) {
            clear()
        }
    }

    /** Entradas sem senha (campo vazio); lista vazia se não há índice ou ele não abre. */
    @Synchronized
    fun load(): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            val bytes = file.readBytes()
            if (bytes.size <= IV_SIZE) return emptyList()
            val secret = keyStore.getKey(ALIAS, null) as? SecretKey ?: return emptyList()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, bytes, 0, IV_SIZE))
            }
            val array = JSONArray(String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8))
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Entry(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    username = o.optString("username"),
                    url = o.optString("url"),
                )
            }
        } catch (e: GeneralSecurityException) {
            emptyList()
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }

    @Synchronized
    fun clear() {
        if (!file.exists()) return
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
        const val ALIAS = "babel_index_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
