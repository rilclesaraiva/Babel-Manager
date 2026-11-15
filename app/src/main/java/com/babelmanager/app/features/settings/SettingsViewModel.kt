package com.babelmanager.app.features.settings

import android.content.Context
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babelmanager.app.R
import com.babelmanager.app.core.ui.UiMessage
import com.babelmanager.app.data.backup.BackupRepository
import com.babelmanager.app.data.biometric.BiometricUnlock
import com.babelmanager.app.data.crypto.InvalidVaultException
import com.babelmanager.app.data.crypto.WrongPasswordException
import com.babelmanager.app.data.importer.ImportFormatException
import com.babelmanager.app.data.importer.PasswordExporter
import com.babelmanager.app.data.importer.PasswordImporter
import com.babelmanager.app.data.session.AutoLock
import com.babelmanager.app.data.settings.Settings
import com.babelmanager.app.data.settings.SettingsStore
import com.babelmanager.app.data.apps.SiteIcons
import com.babelmanager.app.data.autofill.AutofillDiagnostics
import com.babelmanager.app.data.vault.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val vault: VaultRepository,
    private val backup: BackupRepository,
    private val store: SettingsStore,
    private val biometric: BiometricUnlock,
    private val autoLock: AutoLock,
) : ViewModel() {

    val settings: StateFlow<Settings> = store.settings

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun biometricAvailable(context: Context): Boolean = biometric.isAvailable(context)

    /** O seletor de arquivos do sistema tira o app da tela por um instante: não travar por isso. */
    fun beforePicker() = autoLock.suppressNext()

    fun setAutoLock(seconds: Int) = store.update { it.copy(autoLockSeconds = seconds) }
    fun setClipboardClear(seconds: Int) = store.update { it.copy(clipboardClearSeconds = seconds) }
    fun setBlockScreenshots(value: Boolean) = store.update { it.copy(blockScreenshots = value) }
    fun setAutoBackup(value: Boolean) = store.update { it.copy(autoBackup = value) }
    fun setLockedSuggestions(value: Boolean) = store.update { it.copy(lockedSuggestions = value) }
    fun setThemeMode(mode: Int) = store.update { it.copy(themeMode = mode) }
    fun setSiteIcons(context: android.content.Context, value: Boolean) {
        if (!value) SiteIcons(context.applicationContext).clear()
        store.update { it.copy(siteIcons = value) }
    }
    fun setDynamicColor(value: Boolean) = store.update { it.copy(dynamicColor = value) }
    fun setAutofillDiagnostics(value: Boolean) {
        if (!value) AutofillDiagnostics.clear()
        store.update { it.copy(autofillDiagnostics = value) }
    }

    fun enableBiometric(activity: FragmentActivity) {
        val key = vault.exportKey() ?: return
        biometric.enable(
            activity,
            key,
            onDone = { say(R.string.msg_biometric_enabled) },
            onFailure = { say(R.string.msg_biometric_failed) },
        )
    }

    fun disableBiometric() = biometric.disable()

    fun setBackupLocation(uri: Uri) {
        backup.setLocation(uri)
        say(R.string.msg_backup_location_set)
    }

    fun clearBackupLocation() = backup.clearLocation()

    fun backupNow() {
        viewModelScope.launch {
            say(if (backup.backupNow()) R.string.msg_backup_done else R.string.msg_backup_failed)
        }
    }

    fun restore(uri: Uri, password: String) {
        viewModelScope.launch {
            try {
                vault.restore(backup.readBytes(uri), password.toCharArray())
                biometric.disable() // a chave do cofre mudou
                say(R.string.msg_restore_done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: WrongPasswordException) {
                say(R.string.error_backup_wrong_password)
            } catch (e: InvalidVaultException) {
                say(R.string.error_invalid_backup)
            } catch (e: Exception) {
                say(R.string.error_generic)
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = String(backup.readBytes(uri), Charsets.UTF_8)
                val result = PasswordImporter.parse(text)
                val added = vault.importEntries(result.entries)
                _messages.tryEmit(
                    UiMessage(R.string.msg_import_done, listOf(added, result.entries.size - added + result.skipped)),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportFormatException) {
                say(R.string.error_import_format)
            } catch (e: Exception) {
                say(R.string.error_generic)
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                backup.writeBytes(uri, PasswordExporter.toCsv(vault.entries.value).toByteArray(Charsets.UTF_8))
                say(R.string.msg_export_done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(R.string.error_generic)
            }
        }
    }

    fun changePassword(current: String, new: String, confirm: String) {
        when {
            new.length < VaultRepository.MIN_PASSWORD_LENGTH -> say(R.string.error_password_short)
            new != confirm -> say(R.string.error_password_mismatch)
            else -> viewModelScope.launch {
                try {
                    vault.changePassword(current.toCharArray(), new.toCharArray())
                    biometric.disable() // a chave do cofre mudou
                    say(R.string.msg_password_changed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: WrongPasswordException) {
                    say(R.string.error_wrong_password)
                } catch (e: Exception) {
                    say(R.string.error_generic)
                }
            }
        }
    }

    fun deleteVault() {
        viewModelScope.launch {
            vault.deleteVault()
            biometric.disable()
        }
    }

    private fun say(id: Int) {
        _messages.tryEmit(UiMessage(id))
    }
}
