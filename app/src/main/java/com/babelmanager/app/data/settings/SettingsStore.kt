package com.babelmanager.app.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64

data class Settings(
    val autoLockSeconds: Int = 60,
    val blockScreenshots: Boolean = true,
    val clipboardClearSeconds: Int = 30,
    val backupUri: String? = null,
    val autoBackup: Boolean = true,
    val lastBackupAt: Long = 0L,
    val lastBackupFailed: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockedSuggestions: Boolean = false,
)

/** Preferências no aparelho. A biometria guarda aqui só a chave já cifrada pelo Keystore. */
class SettingsStore(private val prefs: SharedPreferences) {
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    @Synchronized
    fun update(block: (Settings) -> Settings) {
        val next = block(_settings.value).copy(biometricEnabled = _settings.value.biometricEnabled)
        prefs.edit()
            .putInt(AUTO_LOCK, next.autoLockSeconds)
            .putBoolean(BLOCK_SCREENSHOTS, next.blockScreenshots)
            .putInt(CLIPBOARD, next.clipboardClearSeconds)
            .putString(BACKUP_URI, next.backupUri)
            .putBoolean(AUTO_BACKUP, next.autoBackup)
            .putLong(LAST_BACKUP, next.lastBackupAt)
            .putBoolean(LAST_BACKUP_FAILED, next.lastBackupFailed)
            .putBoolean(LOCKED_SUGGESTIONS, next.lockedSuggestions)
            .apply()
        _settings.value = next
    }

    @Synchronized
    fun saveBiometric(iv: ByteArray, ciphertext: ByteArray) {
        val b64 = Base64.getEncoder()
        prefs.edit()
            .putString(BIO_IV, b64.encodeToString(iv))
            .putString(BIO_CT, b64.encodeToString(ciphertext))
            .apply()
        _settings.value = _settings.value.copy(biometricEnabled = true)
    }

    fun loadBiometric(): Pair<ByteArray, ByteArray>? {
        val iv = prefs.getString(BIO_IV, null) ?: return null
        val ct = prefs.getString(BIO_CT, null) ?: return null
        val b64 = Base64.getDecoder()
        return runCatching { b64.decode(iv) to b64.decode(ct) }.getOrNull()
    }

    @Synchronized
    fun clearBiometric() {
        prefs.edit().remove(BIO_IV).remove(BIO_CT).apply()
        _settings.value = _settings.value.copy(biometricEnabled = false)
    }

    private fun read() = Settings(
        autoLockSeconds = prefs.getInt(AUTO_LOCK, 60),
        blockScreenshots = prefs.getBoolean(BLOCK_SCREENSHOTS, true),
        clipboardClearSeconds = prefs.getInt(CLIPBOARD, 30),
        backupUri = prefs.getString(BACKUP_URI, null),
        autoBackup = prefs.getBoolean(AUTO_BACKUP, true),
        lastBackupAt = prefs.getLong(LAST_BACKUP, 0L),
        lastBackupFailed = prefs.getBoolean(LAST_BACKUP_FAILED, false),
        biometricEnabled = prefs.contains(BIO_CT),
        lockedSuggestions = prefs.getBoolean(LOCKED_SUGGESTIONS, false),
    )

    private companion object {
        const val AUTO_LOCK = "auto_lock_seconds"
        const val BLOCK_SCREENSHOTS = "block_screenshots"
        const val CLIPBOARD = "clipboard_clear_seconds"
        const val BACKUP_URI = "backup_uri"
        const val AUTO_BACKUP = "auto_backup"
        const val LAST_BACKUP = "last_backup_at"
        const val LAST_BACKUP_FAILED = "last_backup_failed"
        const val LOCKED_SUGGESTIONS = "locked_suggestions"
        const val BIO_IV = "bio_iv"
        const val BIO_CT = "bio_ct"
    }
}
