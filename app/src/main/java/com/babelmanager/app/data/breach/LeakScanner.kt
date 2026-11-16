package com.babelmanager.app.data.breach

import com.babelmanager.app.data.settings.SettingsStore
import com.babelmanager.app.data.vault.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Varredura de vazamentos (Pwned Passwords, k-anonymity) das senhas do cofre aberto. Só funciona com o cofre
 * aberto, porque é aí que as senhas existem; por isso a varredura automática roda no primeiro desbloqueio do dia.
 * Guarda só a hora da varredura e os ids das entradas afetadas, nunca senhas nem hashes.
 */
class LeakScanner(
    private val vault: VaultRepository,
    private val settings: SettingsStore,
    private val pwned: PwnedPasswords = PwnedPasswords(),
) {
    enum class State { Idle, Running, Failed }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()
    @Volatile private var lastFailureAt = 0L

    /** Verdadeiro se a varredura automática deve rodar agora (uma vez por dia, com pausa depois de uma falha). */
    fun dueNow(now: Long, startOfToday: Long): Boolean {
        val s = settings.current
        return s.autoLeakScan && s.lastLeakScanAt < startOfToday && now - lastFailureAt > RETRY_AFTER_FAILURE_MS
    }

    /** Roda a varredura. Ignorada se já houver uma em andamento ou o cofre estiver trancado. */
    suspend fun scan() {
        if (!lock.tryLock()) return
        try {
            val entries = vault.entries.value.filter { it.password.isNotEmpty() }
            if (vault.state.value != VaultRepository.State.Unlocked) return
            _state.value = State.Running
            val found = pwned.check(entries.map { it.password })
            if (found == null) {
                lastFailureAt = System.currentTimeMillis()
                _state.value = State.Failed
                return
            }
            val ids = entries.filter { it.password in found }.mapTo(HashSet()) { "${it.id}:${found.getValue(it.password)}" }
            settings.update { it.copy(lastLeakScanAt = System.currentTimeMillis(), leakedIds = ids) }
            _state.value = State.Idle
        } finally {
            lock.unlock()
        }
    }

    /** Esquece o resultado (ao excluir o cofre). */
    fun forget() {
        settings.update { it.copy(lastLeakScanAt = 0L, leakedIds = emptySet()) }
    }

    companion object {
        /** Lê o que foi guardado (`id:vezes`; o formato antigo, só o id, vale como 0) para um mapa id → vezes. */
        fun parse(stored: Set<String>): Map<String, Int> =
            stored.associate { it.substringBefore(':') to (it.substringAfter(':', "").toIntOrNull() ?: 0) }

        private const val RETRY_AFTER_FAILURE_MS = 30L * 60 * 1000
    }
}
