package com.babelmanager.app.features.lock

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babelmanager.app.data.backup.BackupRepository
import com.babelmanager.app.data.biometric.BiometricUnlock
import com.babelmanager.app.data.crypto.InvalidVaultException
import com.babelmanager.app.data.crypto.WrongPasswordException
import com.babelmanager.app.data.vault.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LockViewModel(
    private val vault: VaultRepository,
    private val backup: BackupRepository,
    private val biometric: BiometricUnlock,
) : ViewModel() {

    enum class Error { TooShort, Mismatch, WrongPassword, InvalidBackup, Failed }

    data class Ui(val busy: Boolean = false, val error: Error? = null)

    val state: StateFlow<VaultRepository.State> = vault.state

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun clearError() {
        if (_ui.value.error != null) _ui.value = Ui(busy = _ui.value.busy)
    }

    fun create(password: String, confirm: String) {
        when {
            password.length < VaultRepository.MIN_PASSWORD_LENGTH -> _ui.value = Ui(error = Error.TooShort)
            password != confirm -> _ui.value = Ui(error = Error.Mismatch)
            else -> runBusy { vault.create(password.toCharArray()) }
        }
    }

    fun unlock(password: String) {
        if (password.isEmpty()) return
        runBusy { vault.unlock(password.toCharArray()) }
    }

    fun unlockWithBiometrics(activity: FragmentActivity) {
        biometric.unlock(
            activity,
            onKey = { key ->
                viewModelScope.launch {
                    try {
                        vault.unlockWithKey(key)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _ui.value = Ui(error = Error.Failed)
                    } finally {
                        key.fill(0)
                    }
                }
            },
            onFailure = { /* Cancelou ou falhou: a pessoa pode usar a senha mestra. */ },
        )
    }

    fun restore(uri: Uri, password: String) {
        runBusy(invalidVaultError = Error.InvalidBackup) {
            val bytes = backup.readBytes(uri)
            vault.restore(bytes, password.toCharArray())
        }
    }

    private fun runBusy(invalidVaultError: Error = Error.Failed, block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.value = Ui(busy = true)
        viewModelScope.launch {
            _ui.value = try {
                block()
                Ui()
            } catch (e: CancellationException) {
                throw e
            } catch (e: WrongPasswordException) {
                Ui(error = Error.WrongPassword)
            } catch (e: InvalidVaultException) {
                Ui(error = invalidVaultError)
            } catch (e: Exception) {
                Ui(error = Error.Failed)
            }
        }
    }
}
