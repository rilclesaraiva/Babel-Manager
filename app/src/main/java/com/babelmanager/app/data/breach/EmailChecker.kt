package com.babelmanager.app.data.breach

import android.util.Patterns
import com.babelmanager.app.data.settings.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Verificação opcional de e-mails em vazamentos (XposedOrNot). Manual a qualquer momento; automática uma vez por dia
 * só se a pessoa ligar o ajuste. Os e-mails e os resultados ficam cifrados no aparelho (`EmailStore`).
 */
class EmailChecker(
    private val store: EmailStore,
    private val settings: SettingsStore,
    private val api: EmailBreaches = EmailBreaches(),
) {
    enum class State { Idle, Running, Failed }

    private val _items = MutableStateFlow(store.load())
    val items: StateFlow<List<WatchedEmail>> = _items.asStateFlow()
    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()
    @Volatile private var lastFailureAt = 0L

    /** Cadastra um e-mail. Falso se for inválido, repetido ou passar do limite. */
    @Synchronized
    fun add(raw: String): Boolean {
        val email = raw.trim().lowercase()
        val current = _items.value
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches() || current.size >= MAX_EMAILS || current.any { it.email == email }) return false
        persist(current + WatchedEmail(email))
        return true
    }

    @Synchronized
    fun remove(email: String) = persist(_items.value.filterNot { it.email == email })

    fun dueNow(now: Long, startOfToday: Long): Boolean {
        val s = settings.current
        return s.autoEmailCheck && _items.value.isNotEmpty() && s.lastEmailCheckAt < startOfToday &&
            now - lastFailureAt > RETRY_AFTER_FAILURE_MS
    }

    /** Verifica todos os e-mails, um de cada vez (o serviço limita o ritmo). Ignorada se já houver uma em andamento. */
    suspend fun checkAll() {
        if (!lock.tryLock()) return
        try {
            val emails = _items.value.map { it.email }
            if (emails.isEmpty()) return
            _state.value = State.Running
            var failed = false
            for ((n, email) in emails.withIndex()) {
                if (n > 0) delay(PAUSE_MS)
                val breaches = api.check(email)
                if (breaches == null) {
                    failed = true
                    continue
                }
                synchronized(this) {
                    persist(_items.value.map { if (it.email == email) it.copy(breaches = breaches, checkedAt = System.currentTimeMillis()) else it })
                }
            }
            if (failed) lastFailureAt = System.currentTimeMillis() else settings.update { it.copy(lastEmailCheckAt = System.currentTimeMillis()) }
            _state.value = if (failed) State.Failed else State.Idle
        } finally {
            lock.unlock()
        }
    }

    /** Esquece e-mails e resultados (ao excluir o cofre). */
    @Synchronized
    fun forget() {
        store.clear()
        _items.value = emptyList()
        settings.update { it.copy(lastEmailCheckAt = 0L) }
    }

    private fun persist(next: List<WatchedEmail>) {
        store.save(next)
        _items.value = next
    }

    companion object {
        const val MAX_EMAILS = 10
        private const val PAUSE_MS = 1_200L
        private const val RETRY_AFTER_FAILURE_MS = 30L * 60 * 1000
    }
}
