package com.babelmanager.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.LaunchedEffect
import com.babelmanager.app.data.vault.VaultRepository
import com.babelmanager.app.features.about.AboutScreen
import com.babelmanager.app.features.checkup.CheckupScreen
import com.babelmanager.app.features.generator.GeneratorScreen
import com.babelmanager.app.features.lock.LockScreen
import com.babelmanager.app.features.lock.LockViewModel
import com.babelmanager.app.features.settings.SettingsScreen
import com.babelmanager.app.features.settings.SettingsViewModel
import com.babelmanager.app.features.update.UpdatePrompt
import com.babelmanager.app.features.update.UpdateViewModel
import com.babelmanager.app.features.vault.EntryDetailScreen
import com.babelmanager.app.features.vault.EntryEditScreen
import com.babelmanager.app.features.vault.VaultListScreen
import com.babelmanager.app.features.vault.VaultViewModel

// Rotas guardadas como texto para sobreviver à rotação de tela.
private const val VAULT = "vault"
private const val GENERATOR = "generator"
private const val CHECKUP = "checkup"
private const val SETTINGS = "settings"
private const val ABOUT = "about"
private const val DETAIL = "detail:"
private const val EDIT = "edit:"
private val TABS = listOf(VAULT, GENERATOR, CHECKUP, SETTINGS)

/**
 * Ponto de entrada da interface: decide entre bloqueio e app, registra as rotas e
 * é o único lugar que conhece todas as features (features nunca importam umas às outras).
 */
@Composable
fun AppRoot(c: AppContainer) {
    val vaultState by c.vault.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()

    val updateVm: UpdateViewModel = viewModel(factory = viewModelFactory { initializer { UpdateViewModel(c.updates) } })
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { updateVm.onForeground() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (vaultState == VaultRepository.State.Unlocked) {
            MainContent(c, updateVm, updateState)
        } else {
            val lockVm: LockViewModel = viewModel(
                factory = viewModelFactory { initializer { LockViewModel(c.vault, c.backup, c.biometric) } },
            )
            LockScreen(
                vm = lockVm,
                biometricEnabled = settings.biometricEnabled,
                onBeforePicker = c.autoLock::suppressNext,
            )
        }
    }

    updateState.release?.let { release ->
        UpdatePrompt(
            release = release,
            onDismiss = updateVm::dismiss,
            onSnoozeWeek = { updateVm.snooze(7) },
            onSnoozeMonth = { updateVm.snooze(30) },
            onSkip = updateVm::skip,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainContent(c: AppContainer, updateVm: UpdateViewModel, updateState: UpdateViewModel.State) {
    val vaultVm: VaultViewModel = viewModel(factory = viewModelFactory { initializer { VaultViewModel(c.vault) } })
    val settingsVm: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(c.vault, c.backup, c.settings, c.biometric, c.autoLock) } },
    )

    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val entries by vaultVm.entries.collectAsStateWithLifecycle()
    val allEntries by vaultVm.allEntries.collectAsStateWithLifecycle()
    val query by vaultVm.query.collectAsStateWithLifecycle()

    var stack by rememberSaveable { mutableStateOf(arrayListOf(VAULT)) }
    val route = stack.last()

    fun push(next: String) { stack = ArrayList(stack + next) }
    fun pop() { if (stack.size > 1) stack = ArrayList(stack.dropLast(1)) }
    fun goToTab(tab: String) { stack = arrayListOf(tab) }

    BackHandler(enabled = stack.size > 1 || route != VAULT) {
        if (stack.size > 1) pop() else goToTab(VAULT)
    }

    when {
        route in TABS -> Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                ShortNavigationBar {
                    TABS.forEach { tab ->
                        ShortNavigationBarItem(
                            selected = route == tab,
                            onClick = { goToTab(tab) },
                            icon = { Icon(tabIcon(tab), contentDescription = null) },
                            label = { Text(stringResource(tabLabel(tab))) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (route) {
                    VAULT -> VaultListScreen(
                        entries = entries,
                        totalCount = allEntries.size,
                        query = query,
                        onQueryChange = vaultVm::setQuery,
                        showBackupBanner = settings.backupUri == null,
                        onOpenBackupSettings = { goToTab(SETTINGS) },
                        onOpen = { push(DETAIL + it.id) },
                        onAdd = { push(EDIT) },
                        onLock = vaultVm::lock,
                    )
                    GENERATOR -> GeneratorScreen(clipboardSeconds = settings.clipboardClearSeconds)
                    CHECKUP -> CheckupScreen(entries = allEntries, onOpen = { push(DETAIL + it.id) })
                    SETTINGS -> SettingsScreen(
                        vm = settingsVm,
                        updateEnabled = updateState.enabled,
                        updateChecking = updateState.checking,
                        updatePostpone = updateState.postpone,
                        updateMessages = updateVm.messages,
                        onCheckUpdates = updateVm::checkNow,
                        onUndoPostpone = updateVm::undoPostpone,
                        onAbout = { push(ABOUT) },
                    )
                }
            }
        }

        route.startsWith(DETAIL) -> {
            val id = route.removePrefix(DETAIL)
            EntryDetailScreen(
                entry = allEntries.firstOrNull { it.id == id },
                clipboardSeconds = settings.clipboardClearSeconds,
                onBack = ::pop,
                onEdit = { push(EDIT + it.id) },
                onDelete = { vaultVm.delete(it.id); pop() },
            )
        }

        route.startsWith(EDIT) -> {
            val id = route.removePrefix(EDIT)
            EntryEditScreen(
                initial = allEntries.firstOrNull { it.id == id },
                onSave = { vaultVm.save(it); pop() },
                onClose = ::pop,
            )
        }

        route == ABOUT -> AboutScreen(onBack = ::pop)
    }
}

private fun tabIcon(tab: String): ImageVector = when (tab) {
    VAULT -> Icons.Outlined.Key
    GENERATOR -> Icons.Outlined.Casino
    CHECKUP -> Icons.Outlined.VerifiedUser
    else -> Icons.Outlined.Settings
}

private fun tabLabel(tab: String): Int = when (tab) {
    VAULT -> R.string.tab_vault
    GENERATOR -> R.string.tab_generator
    CHECKUP -> R.string.tab_checkup
    else -> R.string.tab_settings
}
