package com.babelmanager.app.features.update

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babelmanager.app.R
import com.babelmanager.app.core.ui.UiMessage
import com.babelmanager.app.data.update.CheckOutcome
import com.babelmanager.app.data.update.Postpone
import com.babelmanager.app.data.update.ReleaseInfo
import com.babelmanager.app.data.update.UpdateRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Decide quando consultar e o que mostrar:
 * - abrindo o app do zero: sempre consulta;
 * - voltando ao app: no máximo a cada 10 minutos;
 * - a última versão nova já guardada aparece na hora, sem esperar a rede;
 * - "Agora não" vale só para esta sessão; o sino adia ou pula e fica guardado.
 */
class UpdateViewModel(
    private val repo: UpdateRepository,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    data class State(
        /** Versão a mostrar no painel; nulo se não há nada a mostrar agora. */
        val release: ReleaseInfo? = null,
        val checking: Boolean = false,
        val postpone: Postpone = Postpone(),
        val enabled: Boolean = false,
    )

    private val _state = MutableStateFlow(State(enabled = repo.enabled))
    val state: StateFlow<State> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 2)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var lastCheck: Long? = null
    private var dismissedThisSession = false
    private var forced = false

    init {
        refresh()
    }

    /** Chamado sempre que o app volta para a tela (inclusive na abertura). */
    fun onForeground() {
        refresh()
        val last = lastCheck
        if (last == null || clock() - last >= MIN_INTERVAL_MS) check(manual = false)
    }

    /** Consulta manual (Ajustes): ignora adiamentos e "Agora não". */
    fun checkNow() = check(manual = true)

    fun dismiss() {
        dismissedThisSession = true
        forced = false
        refresh()
    }

    fun snooze(days: Int) {
        repo.snooze(days)
        dismiss()
    }

    fun skip() {
        _state.value.release?.let { repo.skip(it.version) }
        dismiss()
    }

    fun undoPostpone() {
        repo.clearPostpone()
        dismissedThisSession = false
        refresh()
    }

    private fun check(manual: Boolean) {
        if (!repo.enabled) {
            if (manual) _messages.tryEmit(UiMessage(R.string.update_not_configured))
            return
        }
        if (_state.value.checking) return
        if (manual) {
            dismissedThisSession = false
            forced = true
        }
        _state.value = _state.value.copy(checking = true)
        viewModelScope.launch {
            val outcome = repo.check()
            // Só conta como consulta feita se o GitHub respondeu.
            if (outcome == CheckOutcome.Updated || outcome == CheckOutcome.NotModified || outcome == CheckOutcome.NoRelease) {
                lastCheck = clock()
            }
            _state.value = _state.value.copy(checking = false)
            refresh()
            if (manual) {
                when {
                    outcome == CheckOutcome.Failed -> _messages.tryEmit(UiMessage(R.string.update_check_failed))
                    repo.cachedNewer() == null -> {
                        forced = false
                        _messages.tryEmit(UiMessage(R.string.update_up_to_date))
                    }
                }
            }
        }
    }

    private fun refresh() {
        val release = when {
            forced -> repo.cachedNewer()
            dismissedThisSession -> null
            else -> repo.visibleUpdate()
        }
        _state.value = _state.value.copy(release = release, postpone = repo.postpone(), enabled = repo.enabled)
    }

    private companion object {
        const val MIN_INTERVAL_MS = 10 * 60 * 1000L
    }
}
