package com.babelmanager.app.data.crypto

import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Wrong password or corrupted vault")
class InvalidVaultException(message: String) : Exception(message)

/**
 * Arquivo do cofre: JSON com os parâmetros de derivação de chave e o conteúdo cifrado.
 * Os parâmetros ficam no arquivo para que o algoritmo possa ser trocado no futuro
 * sem quebrar cofres antigos.
 */
class VaultFile(
    val iterations: Int,
    val salt: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    fun toBytes(): ByteArray {
        val b64 = Base64.getEncoder()
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put(
                "kdf",
                JSONObject()
                    .put("name", KDF_NAME)
                    .put("iterations", iterations)
                    .put("salt", b64.encodeToString(salt)),
            )
            .put(
                "cipher",
                JSONObject()
                    .put("name", CIPHER_NAME)
                    .put("iv", b64.encodeToString(iv)),
            )
            .put("data", b64.encodeToString(ciphertext))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    companion object {
        const val FORMAT = "babel-vault"
        const val VERSION = 1
        const val KDF_NAME = "pbkdf2-hmac-sha256"
        const val CIPHER_NAME = "aes-256-gcm"
        private const val MIN_ITERATIONS = 1_000
        private const val MAX_ITERATIONS = 10_000_000

        fun parse(bytes: ByteArray): VaultFile {
            try {
                val root = JSONObject(String(bytes, Charsets.UTF_8))
                if (root.optString("format") != FORMAT) throw InvalidVaultException("Not a Babel vault")
                if (root.optInt("version") != VERSION) throw InvalidVaultException("Unsupported vault version")
                val kdf = root.getJSONObject("kdf")
                val cipher = root.getJSONObject("cipher")
                if (kdf.optString("name") != KDF_NAME || cipher.optString("name") != CIPHER_NAME) {
                    throw InvalidVaultException("Unsupported algorithms")
                }
                val iterations = kdf.getInt("iterations")
                if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw InvalidVaultException("Invalid KDF cost")
                val b64 = Base64.getDecoder()
                return VaultFile(
                    iterations = iterations,
                    salt = b64.decode(kdf.getString("salt")),
                    iv = b64.decode(cipher.getString("iv")),
                    ciphertext = b64.decode(root.getString("data")),
                )
            } catch (e: JSONException) {
                throw InvalidVaultException("Corrupted vault file")
            } catch (e: IllegalArgumentException) {
                throw InvalidVaultException("Corrupted vault file")
            }
        }
    }
}

object VaultCrypto {
    /** OWASP recomenda 600.000 para PBKDF2-HMAC-SHA256. */
    const val DEFAULT_ITERATIONS = 600_000
    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(key: ByteArray, plaintext: ByteArray, salt: ByteArray, iterations: Int): VaultFile {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(iterations))
        return VaultFile(iterations, salt, iv, cipher.doFinal(plaintext))
    }

    fun open(key: ByteArray, file: VaultFile): ByteArray {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, file.iv))
            cipher.updateAAD(aad(file.iterations))
            return cipher.doFinal(file.ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: GeneralSecurityException) {
            throw InvalidVaultException("Could not decrypt vault")
        }
    }

    private fun aad(iterations: Int): ByteArray =
        "${VaultFile.FORMAT}/${VaultFile.VERSION}/${VaultFile.KDF_NAME}/$iterations".toByteArray(Charsets.UTF_8)
}
