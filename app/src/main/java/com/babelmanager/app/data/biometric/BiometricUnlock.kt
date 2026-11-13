package com.babelmanager.app.data.biometric

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.babelmanager.app.R
import com.babelmanager.app.data.settings.SettingsStore
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chave do Android Keystore que só é liberada com biometria forte. Ela cifra a chave do cofre;
 * o resultado cifrado fica nas preferências. Se as digitais/rostos cadastrados mudarem, o
 * Keystore invalida a chave e a pessoa volta a usar a senha mestra.
 */
class BiometricKeyStore {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun newEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher
    }

    /** Nulo se a chave não existe mais ou foi invalidada. */
    fun newDecryptCipher(iv: ByteArray): Cipher? {
        return try {
            val key = keyStore.getKey(ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            delete()
            null
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    fun delete() {
        runCatching { keyStore.deleteEntry(ALIAS) }
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ALIAS = "babel_biometric_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

class BiometricUnlock(
    private val settings: SettingsStore,
    private val keyStore: BiometricKeyStore,
) {
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Pede a biometria e entrega a chave do cofre (a quem chamou cabe apagá-la da memória). */
    fun unlock(activity: FragmentActivity, onKey: (ByteArray) -> Unit, onFailure: () -> Unit) {
        val stored = settings.loadBiometric()
        val cipher = stored?.let { keyStore.newDecryptCipher(it.first) }
        if (stored == null || cipher == null) {
            disable()
            onFailure()
            return
        }
        prompt(activity, cipher, onFailure) { result ->
            try {
                onKey(result.doFinal(stored.second))
            } catch (e: GeneralSecurityException) {
                onFailure()
            }
        }
    }

    /** Liga o desbloqueio biométrico guardando [vaultKey] cifrada pela chave do Keystore. */
    fun enable(activity: FragmentActivity, vaultKey: ByteArray, onDone: () -> Unit, onFailure: () -> Unit) {
        val cipher = try {
            keyStore.newEncryptCipher()
        } catch (e: GeneralSecurityException) {
            vaultKey.fill(0)
            onFailure()
            return
        }
        prompt(
            activity,
            cipher,
            onFailure = { vaultKey.fill(0); onFailure() },
        ) { result ->
            try {
                settings.saveBiometric(result.iv, result.doFinal(vaultKey))
                onDone()
            } catch (e: GeneralSecurityException) {
                onFailure()
            } finally {
                vaultKey.fill(0)
            }
        }
    }

    fun disable() {
        settings.clearBiometric()
        keyStore.delete()
    }

    private fun prompt(
        activity: FragmentActivity,
        cipher: Cipher,
        onFailure: () -> Unit,
        onSuccess: (Cipher) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticated = result.cryptoObject?.cipher
                if (authenticated == null) onFailure() else onSuccess(authenticated)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_title))
            .setSubtitle(activity.getString(R.string.biometric_subtitle))
            .setNegativeButtonText(activity.getString(R.string.biometric_use_password))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
