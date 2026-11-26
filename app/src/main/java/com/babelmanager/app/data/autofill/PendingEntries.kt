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
 * Entradas criadas por "Sugerir senha forte" com o cofre trancado. Sem desbloquear não há como gravar no cofre,
 * então ficam aqui, cifradas por uma chave do Keystore que não exige biometria (`babel_pending_key`), e entram no
 * cofre no próximo desbloqueio (o arquivo é apagado na hora). Só existem entre a sugestão e esse desbloqueio.
 */
class PendingEntries(private val file: File) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun add(entry: Entry): Boolean {
        val all = loadLocked() + entry
        val array = JSONArray()
        for (e in all) {
            array.put(
                JSONObject().put("id", e.id).put("title", e.title).put("username", e.username)
                    .put("password", e.password).put("url", e.url),
            )
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val sealed = cipher.iv + cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(sealed)
            tmp.renameTo(file).also { if (!it) tmp.delete() }
        } catch (e: GeneralSecurityException) {
            false
        }
    }

    /** Devolve as pendentes. Quem chama as grava no cofre e depois chama [clear]. */
    @Synchronized
    fun load(): List<Entry> = loadLocked()

    @Synchronized
    fun clear() {
        file.delete()
        runCatching { keyStore.deleteEntry(ALIAS) }
    }

    private fun loadLocked(): List<Entry> {
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
                    password = o.optString("password"),
                    url = o.optString("url"),
                )
            }
        } catch (e: GeneralSecurityException) {
            emptyList()
        } catch (e: org.json.JSONException) {
            emptyList()
        }
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
        const val ALIAS = "babel_pending_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
