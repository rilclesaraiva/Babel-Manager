package com.babelmanager.app.data.session

import android.os.SystemClock
import com.babelmanager.app.data.settings.SettingsStore
import com.babelmanager.app.data.vault.VaultRepository

/**
 * Trava o cofre quando o app fica em segundo plano por mais tempo que o escolhido
 * (0 = travar assim que sair do app).
 */
class AutoLock(
    private val settings: SettingsStore,
    private val vault: VaultRepository,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var stoppedAt: Long? = null
    private var skipNext = false

    fun onStop() {
        stoppedAt = clock()
    }

    fun onStart() {
        val stopped = stoppedAt ?: return
        stoppedAt = null
        if (skipNext) {
            skipNext = false
            return
        }
        if (clock() - stopped >= settings.current.autoLockSeconds * 1000L) vault.lock()
    }

    /**
     * Para quem oferece dados sem abrir a tela (serviço de preenchimento): tranca se o app já ficou
     * fora tempo demais. Não mexe no relógio, então o próximo [onStart] segue valendo.
     */
    fun checkExpired() {
        val stopped = stoppedAt ?: return
        if (clock() - stopped >= settings.current.autoLockSeconds * 1000L) vault.lock()
    }

    /** Chamado antes de abrir o seletor de arquivos do sistema, que tira o app da tela por um instante. */
    fun suppressNext() {
        skipNext = true
    }
}
