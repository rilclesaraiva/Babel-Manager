package com.babelmanager.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.babelmanager.app.data.settings.SettingsStore
import com.babelmanager.app.data.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Cópia do cofre CIFRADO no local que a pessoa escolher (Storage Access Framework: pasta do
 * aparelho, Google Drive, cartão, etc.). O app nunca envia nada por conta própria: quem
 * grava no local escolhido é o provedor de documentos do Android.
 */
class BackupRepository(
    private val context: Context,
    private val vault: VaultRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val writeMutex = Mutex()

    /** Liga o backup automático: a cada gravação do cofre, copia para o local escolhido. */
    fun start() {
        scope.launch {
            vault.changes.conflate().collect {
                val s = settings.current
                if (s.autoBackup && s.backupUri != null) backupNow()
            }
        }
    }

    suspend fun backupNow(): Boolean = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val uri = settings.current.backupUri?.let(Uri::parse) ?: return@withContext false
            val bytes = vault.encryptedBytes() ?: return@withContext false
            val ok = try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("No output stream")
                stream.use { it.write(bytes) }
                true
            } catch (e: Exception) {
                false
            }
            settings.update {
                it.copy(
                    lastBackupAt = if (ok) System.currentTimeMillis() else it.lastBackupAt,
                    lastBackupFailed = !ok,
                )
            }
            ok
        }
    }

    fun setLocation(uri: Uri) {
        releasePermission(settings.current.backupUri)
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Alguns provedores não oferecem permissão persistente; a escrita imediata ainda funciona.
        }
        settings.update { it.copy(backupUri = uri.toString(), lastBackupFailed = false) }
        scope.launch { backupNow() }
    }

    fun clearLocation() {
        releasePermission(settings.current.backupUri)
        settings.update { it.copy(backupUri = null, lastBackupFailed = false) }
    }

    suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
        stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_FILE_BYTES) throw IOException("File too large")
            }
            out.toByteArray()
        }
    }

    suspend fun writeBytes(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open file")
        stream.use { it.write(bytes) }
    }

    private fun releasePermission(uriString: String?) {
        if (uriString == null) return
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // Permissão já inexistente.
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
    }
}
