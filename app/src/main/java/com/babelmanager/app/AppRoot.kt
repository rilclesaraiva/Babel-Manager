package com.babelmanager.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.LaunchedEffect
import com.babelmanager.app.data.vault.VaultRepository
import com.babelmanager.app.features.about.AboutScreen
import com.babelmanager.app.features.about.LicensesScreen
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

// Rotas da navegação (NavHost). A home é o cofre; ferramentas e Ajustes abrem por cima dela.
private const val VAULT = "vault"
private const val TOOLS = "tools"
private const val SETTINGS = "settings"
private const val ABOUT = "about"
private const val LICENSES = "licenses"
private const val DETAIL = "detail/{id}"
private const val EDIT = "edit?id={id}"

private fun detailRoute(id: String) = "detail/$id"
private fun editRoute(id: String?) = if (id == null) "edit" else "edit?id=$id"

/** Só navega com a tela de cima parada (RESUMED): toque duplo não abre nem fecha duas vezes. */
private fun NavController.goOnce(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navigate(route)
}

private fun NavController.popOnce() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

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

    val nav = rememberNavController()

    // Telas de dentro entram deslizando por cima da anterior (que recua um pouco e esmaece) e saem de volta.
    // O "voltar" preditivo do sistema usa as mesmas animações de saída: a tela acompanha o dedo.
    // Movimento sempre pelo esquema de movimento do tema (nada de tempos escritos à mão).
    val slideSpec: FiniteAnimationSpec<IntOffset> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val fadeSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec()
    val forwardEnter = slideInHorizontally(slideSpec) { it }
    val forwardExit = slideOutHorizontally(slideSpec) { -it / 4 } + fadeOut(fadeSpec, targetAlpha = 0.4f)
    val backEnter = slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(fadeSpec, initialAlpha = 0.4f)
    val backExit = slideOutHorizontally(slideSpec) { it }

    NavHost(
        navController = nav,
        startDestination = VAULT,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { forwardEnter },
        exitTransition = { forwardExit },
        popEnterTransition = { backEnter },
        popExitTransition = { backExit },
    ) {
        composable(VAULT) {
            VaultListScreen(
                entries = entries,
                totalCount = allEntries.size,
                query = query,
                onQueryChange = vaultVm::setQuery,
                showBackupBanner = settings.backupUri == null,
                onOpenBackupSettings = { nav.goOnce(SETTINGS) },
                onOpen = { nav.goOnce(detailRoute(it.id)) },
                onAdd = { nav.goOnce(editRoute(null)) },
                onLock = vaultVm::lock,
                onOpenTools = { nav.goOnce(TOOLS) },
                onOpenSettings = { nav.goOnce(SETTINGS) },
            )
        }
        composable(TOOLS) {
            ToolsScreen(
                clipboardSeconds = settings.clipboardClearSeconds,
                entries = allEntries,
                onOpen = { nav.goOnce(detailRoute(it.id)) },
                onBack = { nav.popOnce() },
            )
        }
        composable(SETTINGS) {
            SettingsScreen(
                vm = settingsVm,
                updateEnabled = updateState.enabled,
                updateChecking = updateState.checking,
                updatePostpone = updateState.postpone,
                updateMessages = updateVm.messages,
                onCheckUpdates = updateVm::checkNow,
                onUndoPostpone = updateVm::undoPostpone,
                onSnooze = updateVm::snooze,
                onAbout = { nav.goOnce(ABOUT) },
                onBack = { nav.popOnce() },
            )
        }
        composable(
            DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id")
            EntryDetailScreen(
                entry = allEntries.firstOrNull { it.id == id },
                clipboardSeconds = settings.clipboardClearSeconds,
                onBack = { nav.popOnce() },
                onEdit = { nav.goOnce(editRoute(it.id)) },
                onDelete = { vaultVm.delete(it.id); nav.popOnce() },
            )
        }
        composable(
            EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val id = entry.arguments?.getString("id")
            EntryEditScreen(
                initial = allEntries.firstOrNull { it.id == id },
                onSave = { vaultVm.save(it); nav.popOnce() },
                onClose = { nav.popOnce() },
            )
        }
        composable(ABOUT) { AboutScreen(onBack = { nav.popOnce() }, onLicenses = { nav.goOnce(LICENSES) }) }
        composable(LICENSES) { LicensesScreen(onBack = { nav.popOnce() }) }
    }
}
