package com.babelmanager.app

import android.app.Application
import android.content.Context
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

    init {
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
