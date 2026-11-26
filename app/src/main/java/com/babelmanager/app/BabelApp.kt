package com.babelmanager.app

import android.app.Application
import android.content.Context
import com.babelmanager.app.data.apps.AppIcons
import com.babelmanager.app.data.apps.SiteIcons
import com.babelmanager.app.data.breach.EmailChecker
import com.babelmanager.app.data.breach.EmailStore
import com.babelmanager.app.data.breach.LeakScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import com.babelmanager.app.data.autofill.PendingEntries
import com.babelmanager.app.data.autofill.SuggestionIndex
import com.babelmanager.app.data.backup.BackupRepository
import com.babelmanager.app.data.biometric.BiometricKeyStore
import com.babelmanager.app.data.biometric.BiometricUnlock
import com.babelmanager.app.data.session.AutoLock
import com.babelmanager.app.data.settings.SettingsStore
import com.babelmanager.app.data.update.UpdateRepository
import com.babelmanager.app.data.update.UpdateStore
import com.babelmanager.app.data.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

class BabelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Monta as dependências do app em um só lugar. */
class AppContainer(app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(app.getSharedPreferences("settings", Context.MODE_PRIVATE))
    val vault = VaultRepository(File(app.filesDir, "vault.babel"))
    val backup = BackupRepository(app, vault, settings, scope).also { it.start() }
    val biometric = BiometricUnlock(settings, BiometricKeyStore())
    val autoLock = AutoLock(settings, vault)
    val updates = UpdateRepository(
        repo = BuildConfig.REPO,
        installedVersion = BuildConfig.VERSION_NAME,
        store = UpdateStore(app.getSharedPreferences("updates", Context.MODE_PRIVATE)),
    )
    val suggestionIndex = SuggestionIndex(File(app.filesDir, "autofill_index.babel"))
    val pendingEntries = PendingEntries(File(app.filesDir, "pending_entries.babel"))
    val leakScanner = LeakScanner(vault, settings)
    val emailChecker = EmailChecker(EmailStore(File(app.filesDir, "emails.babel")), settings)

    init {
        // Excluir o cofre apaga também o que ele deixou para trás: resultado da varredura e ícones guardados.
        scope.launch {
            vault.state.collect { state ->
                if (state == VaultRepository.State.Missing) {
                    pendingEntries.clear()
                    leakScanner.forget()
                    emailChecker.forget()
                    SiteIcons(app).clear()
                    AppIcons.clearStored(app)
                }
            }
        }
        // Senhas sugeridas com o cofre trancado entram no cofre assim que ele abre.
        scope.launch {
            vault.state.collect { state ->
                if (state == VaultRepository.State.Unlocked) {
                    val pending = pendingEntries.load()
                    if (pending.isNotEmpty()) {
                        runCatching { pending.forEach { vault.upsert(it) } }.onSuccess { pendingEntries.clear() }
                    }
                }
            }
        }
        // Varredura de vazamentos: uma vez por dia, com o cofre aberto (primeiro desbloqueio depois da meia-noite).
        scope.launch {
            combine(
                vault.state,
                settings.settings.map { it.autoLeakScan }.distinctUntilChanged(),
                flow { while (true) { emit(Unit); delay(10 * 60 * 1000L) } },
            ) { state, _, _ -> state }.collect { state ->
                if (state == VaultRepository.State.Unlocked && vault.entries.value.isNotEmpty() &&
                    leakScanner.dueNow(System.currentTimeMillis(), startOfToday())
                ) leakScanner.scan()
            }
        }
        // Verificação de e-mails: só com o ajuste ligado, uma vez por dia (não depende do cofre aberto).
        scope.launch {
            combine(
                settings.settings.map { it.autoEmailCheck }.distinctUntilChanged(),
                flow { while (true) { emit(Unit); delay(10 * 60 * 1000L) } },
            ) { on, _ -> on }.collect { on ->
                if (on && emailChecker.dueNow(System.currentTimeMillis(), startOfToday())) emailChecker.checkAll()
            }
        }
        // Índice das sugestões com o cofre trancado: só existe com a opção e a biometria ligadas;
        // é atualizado a cada mudança com o cofre aberto (trancar não o esvazia).
        scope.launch {
            combine(vault.state, vault.entries, settings.settings) { state, entries, s ->
                Triple(state, entries, s.lockedSuggestions && s.biometricEnabled)
            }.collect { (state, entries, enabled) ->
                when {
                    !enabled || state == VaultRepository.State.Missing -> suggestionIndex.clear()
                    state == VaultRepository.State.Unlocked -> suggestionIndex.save(entries)
                }
            }
        }
    }
}

/** Meia-noite (início de hoje) no fuso do aparelho, em milissegundos. */
private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis
