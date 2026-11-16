package com.babelmanager.app.data.vault

import com.babelmanager.app.data.crypto.VaultCrypto
import com.babelmanager.app.data.crypto.VaultFile
import com.babelmanager.app.data.crypto.WrongPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class VaultLockedException : IllegalStateException("Vault is locked")

/**
 * Cofre local. O arquivo em disco está sempre cifrado (AES-256-GCM, chave derivada da senha
 * mestra por PBKDF2). A chave e os itens só ficam em memória enquanto o cofre está aberto.
 */
class VaultRepository(
    private val file: File,
    private val kdfIterations: Int = VaultCrypto.DEFAULT_ITERATIONS,
) {
    enum class State { Missing, Locked, Unlocked }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(if (file.exists()) State.Locked else State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emite a cada gravação do arquivo do cofre (usado pelo backup automático). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private var key: ByteArray? = null
    private var salt = ByteArray(0)
    private var iterations = kdfIterations

    suspend fun create(password: CharArray) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val newSalt = VaultCrypto.newSalt()
            val newKey = VaultCrypto.deriveKey(password, newSalt, kdfIterations)
            write(emptyList(), newKey, newSalt, kdfIterations)
            install(newKey, newSalt, kdfIterations)
        }
        _entries.value = emptyList()
        _state.value = State.Unlocked
    }

    suspend fun unlock(password: CharArray) = mutex.withLock {
        val opened = withContext(Dispatchers.Default) {
            val vaultFile = VaultFile.parse(file.readBytes())
            val derived = VaultCrypto.deriveKey(password, vaultFile.salt, vaultFile.iterations)
            Opened(derived, vaultFile, VaultCodec.decode(VaultCrypto.open(derived, vaultFile)))
        }
        finishUnlock(opened)
    }

    /** Desbloqueio com a chave guardada pela biometria. */
    suspend fun unlockWithKey(keyBytes: ByteArray) = mutex.withLock {
        val opened = withContext(Dispatchers.Default) {
            val vaultFile = VaultFile.parse(file.readBytes())
            Opened(keyBytes.copyOf(), vaultFile, VaultCodec.decode(VaultCrypto.open(keyBytes, vaultFile)))
        }
        finishUnlock(opened)
    }

    /** Substitui o cofre atual pelo conteúdo de um backup (a senha é a do backup). */
    suspend fun restore(bytes: ByteArray, password: CharArray) = mutex.withLock {
        val opened = withContext(Dispatchers.Default) {
            val vaultFile = VaultFile.parse(bytes)
            val derived = VaultCrypto.deriveKey(password, vaultFile.salt, vaultFile.iterations)
            Opened(derived, vaultFile, VaultCodec.decode(VaultCrypto.open(derived, vaultFile)))
        }
        withContext(Dispatchers.IO) { writeAtomic(bytes) }
        finishUnlock(opened)
    }

    suspend fun upsert(entry: Entry) {
        val now = System.currentTimeMillis()
        mutate { list ->
            val index = list.indexOfFirst { it.id == entry.id }
            if (index >= 0) {
                list.toMutableList().also {
                    it[index] = entry.copy(createdAt = list[index].createdAt, updatedAt = now)
                }
            } else {
                list + entry.copy(createdAt = now, updatedAt = now)
            }
        }
    }

    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    /** Adiciona itens importados, ignorando os que já existem (mesmo site, usuário e senha). */
    suspend fun importEntries(imported: List<Entry>): Int {
        var added = 0
        mutate { list ->
            val known = list.mapTo(HashSet()) { Triple(it.url, it.username, it.password) }
            val now = System.currentTimeMillis()
            val fresh = imported.filter { known.add(Triple(it.url, it.username, it.password)) }
                .map { it.copy(createdAt = now, updatedAt = now) }
            added = fresh.size
            list + fresh
        }
        return added
    }

    /** Confere a senha mestra com o cofre aberto (sem alterar nada). */
    suspend fun verifyPassword(password: CharArray): Boolean = mutex.withLock {
        val currentKey = key ?: return@withLock false
        withContext(Dispatchers.Default) {
            MessageDigest.isEqual(VaultCrypto.deriveKey(password, salt, iterations), currentKey)
        }
    }

    suspend fun changePassword(current: CharArray, new: CharArray) = mutex.withLock {
        val currentKey = key ?: throw VaultLockedException()
        withContext(Dispatchers.Default) {
            val check = VaultCrypto.deriveKey(current, salt, iterations)
            if (!MessageDigest.isEqual(check, currentKey)) throw WrongPasswordException()
            val newSalt = VaultCrypto.newSalt()
            val newKey = VaultCrypto.deriveKey(new, newSalt, kdfIterations)
            write(_entries.value, newKey, newSalt, kdfIterations)
            install(newKey, newSalt, kdfIterations)
        }
    }

    fun lock() {
        wipeKey()
        _entries.value = emptyList()
        if (_state.value == State.Unlocked) _state.value = State.Locked
    }

    suspend fun deleteVault() = mutex.withLock {
        wipeKey()
        _entries.value = emptyList()
        withContext(Dispatchers.IO) { file.delete() }
        _state.value = State.Missing
    }

    /** Bytes do arquivo cifrado, para backup. Nunca o conteúdo aberto. */
    fun encryptedBytes(): ByteArray? = if (file.exists()) file.readBytes() else null

    /** Cópia da chave para a biometria guardá-la. Quem pedir deve apagá-la depois de usar. */
    fun exportKey(): ByteArray? = key?.copyOf()

    private class Opened(val key: ByteArray, val file: VaultFile, val entries: List<Entry>)

    private fun finishUnlock(opened: Opened) {
        install(opened.key, opened.file.salt, opened.file.iterations)
        _entries.value = opened.entries
        _state.value = State.Unlocked
    }

    private suspend fun mutate(transform: (List<Entry>) -> List<Entry>) = mutex.withLock {
        val currentKey = key ?: throw VaultLockedException()
        val next = transform(_entries.value)
        withContext(Dispatchers.Default) { write(next, currentKey, salt, iterations) }
        _entries.value = next
    }

    private fun install(newKey: ByteArray, newSalt: ByteArray, newIterations: Int) {
        key?.fill(0)
        key = newKey
        salt = newSalt
        iterations = newIterations
    }

    private fun wipeKey() {
        key?.fill(0)
        key = null
    }

    private fun write(entries: List<Entry>, key: ByteArray, salt: ByteArray, iterations: Int) {
        val sealed = VaultCrypto.seal(key, VaultCodec.encode(entries), salt, iterations)
        writeAtomic(sealed.toBytes())
        _changes.tryEmit(Unit)
    }

    private fun writeAtomic(bytes: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use {
            it.write(bytes)
            it.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 12
    }
}
