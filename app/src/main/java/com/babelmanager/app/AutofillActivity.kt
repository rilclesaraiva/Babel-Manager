package com.babelmanager.app

import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.lifecycleScope
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.babelmanager.app.core.ui.BabelTheme
import com.babelmanager.app.core.ui.EntryAvatar
import com.babelmanager.app.core.ui.SectionHeader
import com.babelmanager.app.data.autofill.EntryMatcher
import com.babelmanager.app.data.autofill.ParsedForm
import com.babelmanager.app.data.vault.Entry
import com.babelmanager.app.data.vault.VaultRepository
import com.babelmanager.app.features.lock.LockScreen
import com.babelmanager.app.features.lock.LockViewModel

/**
 * Aberta pelo [BabelAutofillService]. Se o cofre estiver trancado, pede o desbloqueio; depois
 * mostra as entradas (as que combinam com o site/app primeiro) e devolve a escolhida ao sistema.
 */
class AutofillActivity : FragmentActivity() {

    private val container: AppContainer
        get() = (application as BabelApp).container

    private var entryId: String? = null

    // Falso enquanto só o painel do sistema (digital/PIN) aparece: a tela fica invisível.
    private var showUi by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val usernameId = IntentCompat.getParcelableExtra(intent, EXTRA_USERNAME_ID, AutofillId::class.java)
        val passwordIds = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_PASSWORD_IDS, AutofillId::class.java).orEmpty()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val domain = intent.getStringExtra(EXTRA_DOMAIN)
        val pickAny = intent.getBooleanExtra(EXTRA_PICK_ANY, false)
        val form = ParsedForm(usernameId, passwordIds, pkg, domain)
        entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        val quick = entryId != null && !pickAny &&
            container.vault.state.value == VaultRepository.State.Locked && container.settings.current.biometricEnabled
        showUi = !quick

        setContent {
            val appearance by container.settings.settings.collectAsStateWithLifecycle()
            BabelTheme(appearance.themeMode, appearance.dynamicColor) {
                AutofillRoot(
                    c = container,
                    packageName = pkg,
                    webDomain = domain,
                    // Uma única sugestão certa: preenche sem mostrar lista (a não ser que a pessoa tenha pedido "Outras").
                    autoPick = !pickAny,
                    visible = showUi,
                    onPick = { entry -> deliver(entry, form) },
                    onCancel = ::finish,
                )
            }
        }
        if (quick) quickUnlock(form)
    }

    /**
     * Sugestão tocada com o cofre trancado: só o painel do sistema (digital ou senha/PIN do aparelho),
     * sem mostrar o app. Cancelou: fecha. Deu erro (ex.: chave invalidada): cai na tela de desbloqueio.
     */
    private fun quickUnlock(form: ParsedForm) {
        container.biometric.unlock(
            this,
            onKey = { key ->
                lifecycleScope.launch {
                    try {
                        container.vault.unlockWithKey(key)
                        val entry = container.vault.entries.value.firstOrNull { it.id == entryId }
                        if (entry != null) deliver(entry, form) else showUi = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showUi = true
                    } finally {
                        key.fill(0)
                    }
                }
            },
            onFailure = { showUi = true },
            onCanceled = {
                setResult(Activity.RESULT_CANCELED)
                finish()
            },
        )
    }

    override fun onStart() {
        super.onStart()
        // Mesma regra de trava do app: se ficou tempo demais fora, o cofre tranca antes de mostrar nada.
        container.autoLock.onStart()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.autoLock.onStop()
    }

    private fun deliver(entry: Entry, form: ParsedForm) {
        val dataset = AutofillResponses.entryDataset(this, entry, form, null, 0)
        if (dataset == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        // A autenticação é do item (dataset) tocado, então o resultado é um Dataset, preenchido direto.
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        finish()
    }

    companion object {
        const val EXTRA_USERNAME_ID = "babel.autofill.username_id"
        const val EXTRA_PASSWORD_IDS = "babel.autofill.password_ids"
        const val EXTRA_PACKAGE = "babel.autofill.package"
        const val EXTRA_DOMAIN = "babel.autofill.domain"
        const val EXTRA_PICK_ANY = "babel.autofill.pick_any"
        const val EXTRA_ENTRY_ID = "babel.autofill.entry_id"
    }
}

@Composable
private fun AutofillRoot(
    c: AppContainer,
    packageName: String?,
    webDomain: String?,
    autoPick: Boolean,
    visible: Boolean,
    onPick: (Entry) -> Unit,
    onCancel: () -> Unit,
) {
    val state by c.vault.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    // Só o painel do sistema na frente: a janela é transparente e nada é desenhado.
    if (!visible) return
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            VaultRepository.State.Unlocked -> {
                val entries by c.vault.entries.collectAsStateWithLifecycle()
                EntryChooser(entries, packageName, webDomain, autoPick, onPick)
            }
            VaultRepository.State.Locked -> {
                val lockVm: LockViewModel = viewModel(
                    factory = viewModelFactory { initializer { LockViewModel(c.vault, c.backup, c.biometric) } },
                )
                LockScreen(lockVm, settings.biometricEnabled, onBeforePicker = c.autoLock::suppressNext)
            }
            // Sem cofre ainda: não há o que preencher.
            VaultRepository.State.Missing -> LaunchedEffect(Unit) { onCancel() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryChooser(
    entries: List<Entry>,
    packageName: String?,
    webDomain: String?,
    autoPick: Boolean,
    onPick: (Entry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val (suggested, others) = remember(entries, packageName, webDomain) {
        EntryMatcher.split(entries, packageName, webDomain)
    }
    var autoPicked by remember { mutableStateOf(false) }
    LaunchedEffect(suggested, autoPick) {
        val only = suggested.singleOrNull()
        if (autoPick && only != null && !autoPicked) {
            autoPicked = true
            onPick(only)
        }
    }
    val q = query.trim()
    fun Entry.matchesQuery() = title.contains(q, ignoreCase = true) || username.contains(q, ignoreCase = true)
    val searchResults = if (q.isEmpty()) emptyList() else (suggested + others).filter { it.matchesQuery() }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.autofill_pick_title)) }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (q.isNotEmpty()) {
                if (searchResults.isEmpty()) item { EmptyHint() }
                items(searchResults, key = { it.id }) { ChooserRow(it, onPick) }
            } else {
                if (suggested.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.autofill_suggested)) }
                    items(suggested, key = { it.id }) { ChooserRow(it, onPick) }
                }
                item { SectionHeader(stringResource(R.string.autofill_all)) }
                if (others.isEmpty() && suggested.isEmpty()) item { EmptyHint() }
                items(others, key = { it.id }) { ChooserRow(it, onPick) }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Text(
        stringResource(R.string.autofill_empty),
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChooserRow(entry: Entry, onPick: (Entry) -> Unit) {
    ListItem(
        leadingContent = { EntryAvatar(entry.title, url = entry.url) },
        headlineContent = { Text(entry.title, maxLines = 1) },
        supportingContent = if (entry.username.isNotEmpty()) ({ Text(entry.username, maxLines = 1) }) else null,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable { onPick(entry) },
    )
}
