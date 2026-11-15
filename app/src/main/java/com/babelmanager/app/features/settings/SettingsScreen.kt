package com.babelmanager.app.features.settings

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import com.babelmanager.app.core.ui.PageTitle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import android.view.autofill.AutofillManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import com.babelmanager.app.BuildConfig
import com.babelmanager.app.core.ui.GroupItem
import com.babelmanager.app.core.ui.GroupLabel
import com.babelmanager.app.core.ui.GroupedItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babelmanager.app.R
import com.babelmanager.app.core.ui.PasswordField
import com.babelmanager.app.core.ui.SectionHeader
import com.babelmanager.app.core.ui.TabScaffold
import com.babelmanager.app.core.ui.UiMessage
import com.babelmanager.app.core.ui.findActivity
import com.babelmanager.app.core.ui.getString
import com.babelmanager.app.data.update.Postpone
import com.babelmanager.app.data.update.PostponeKind
import kotlinx.coroutines.flow.Flow
import java.text.DateFormat
import java.util.Date

private val autoLockOptions = listOf(
    0 to R.string.autolock_immediately,
    30 to R.string.autolock_30s,
    60 to R.string.autolock_1m,
    300 to R.string.autolock_5m,
    900 to R.string.autolock_15m,
)

private val clipboardOptions = listOf(
    0 to R.string.clipboard_never,
    15 to R.string.clipboard_15s,
    30 to R.string.clipboard_30s,
    60 to R.string.clipboard_60s,
)

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    updateEnabled: Boolean,
    updateChecking: Boolean,
    updatePostpone: Postpone,
    updateMessages: Flow<UiMessage>,
    onCheckUpdates: () -> Unit,
    onUndoPostpone: () -> Unit,
    onSnooze: (Int) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNotices by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context.findActivity()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val biometricAvailable = remember { vm.biometricAvailable(context) }

    // O ajuste do sistema fica fora do app: reler ao voltar para mostrar se já está ativo.
    val autofillManager = remember { context.getSystemService(AutofillManager::class.java) }
    var autofillOn by remember { mutableStateOf(autofillManager?.hasEnabledAutofillServices() == true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        autofillOn = autofillManager?.hasEnabledAutofillServices() == true
    }

    var showAutoLock by rememberSaveable { mutableStateOf(false) }
    var showClipboard by rememberSaveable { mutableStateOf(false) }
    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    var showExportWarning by rememberSaveable { mutableStateOf(false) }
    var showDeleteVault by rememberSaveable { mutableStateOf(false) }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(context.getString(it)) } }
    LaunchedEffect(updateMessages) { updateMessages.collect { snackbar.showSnackbar(context.getString(it)) } }

    val backupLocationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) vm.setBackupLocation(uri) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }

    TabScaffold(
        modifier = modifier,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()))
                .verticalScroll(rememberScrollState()),
        ) {
            PageTitle(stringResource(R.string.tab_settings))
            // ── Segurança ──
            SectionHeader(stringResource(R.string.settings_security))
            SettingsItem(
                title = stringResource(R.string.settings_autolock),
                summary = stringResource(autoLockOptions.first { it.first == settings.autoLockSeconds }.second),
                onClick = { showAutoLock = true },
            )
            SettingsItem(
                title = stringResource(R.string.settings_biometric),
                summary = stringResource(
                    if (biometricAvailable) R.string.settings_biometric_summary else R.string.settings_biometric_unavailable,
                ),
                trailing = {
                    Switch(
                        checked = settings.biometricEnabled,
                        enabled = biometricAvailable,
                        onCheckedChange = { on ->
                            if (on) activity?.let(vm::enableBiometric) else vm.disableBiometric()
                        },
                    )
                },
            )
            SettingsItem(
                title = stringResource(R.string.settings_block_screenshots),
                summary = stringResource(R.string.settings_block_screenshots_summary),
                trailing = {
                    Switch(checked = settings.blockScreenshots, onCheckedChange = vm::setBlockScreenshots)
                },
            )
            SettingsItem(
                title = stringResource(R.string.settings_clipboard),
                summary = stringResource(clipboardOptions.first { it.first == settings.clipboardClearSeconds }.second),
                onClick = { showClipboard = true },
            )
            SettingsItem(
                title = stringResource(R.string.settings_autofill),
                summary = stringResource(if (autofillOn) R.string.settings_autofill_on else R.string.settings_autofill_off),
                onClick = {
                    vm.beforePicker()
                    try {
                        context.startActivity(
                            Intent(
                                SystemSettings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    } catch (e: ActivityNotFoundException) {
                        // Aparelho sem essa tela de ajustes: nada a fazer.
                    }
                },
            )
            SettingsItem(
                title = stringResource(R.string.settings_change_password),
                onClick = { showChangePassword = true },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Backup ──
            SectionHeader(stringResource(R.string.settings_backup))
            SettingsItem(
                title = stringResource(R.string.settings_backup_location),
                summary = settings.backupUri?.let { Uri.decode(Uri.parse(it).lastPathSegment ?: it) }
                    ?: stringResource(R.string.settings_backup_not_set),
                onClick = {
                    vm.beforePicker()
                    backupLocationPicker.launch("babelmanager-backup.babel")
                },
            )
            if (settings.backupUri != null) {
                SettingsItem(
                    title = stringResource(R.string.settings_backup_auto),
                    summary = stringResource(R.string.settings_backup_auto_summary),
                    trailing = { Switch(checked = settings.autoBackup, onCheckedChange = vm::setAutoBackup) },
                )
                SettingsItem(
                    title = stringResource(R.string.settings_backup_now),
                    summary = when {
                        settings.lastBackupFailed -> stringResource(R.string.settings_backup_failed)
                        settings.lastBackupAt > 0 -> stringResource(
                            R.string.settings_backup_last,
                            DateUtils.getRelativeTimeSpanString(settings.lastBackupAt).toString(),
                        )
                        else -> stringResource(R.string.settings_backup_never)
                    },
                    onClick = vm::backupNow,
                )
                SettingsItem(
                    title = stringResource(R.string.settings_backup_stop),
                    onClick = vm::clearBackupLocation,
                )
            }
            SettingsItem(
                title = stringResource(R.string.settings_restore),
                summary = stringResource(R.string.settings_restore_summary),
                onClick = {
                    vm.beforePicker()
                    restorePicker.launch(arrayOf("*/*"))
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Importar e exportar ──
            SectionHeader(stringResource(R.string.settings_data))
            SettingsItem(
                title = stringResource(R.string.settings_import),
                summary = stringResource(R.string.settings_import_summary),
                onClick = {
                    vm.beforePicker()
                    importPicker.launch(arrayOf("*/*"))
                },
            )
            SettingsItem(
                title = stringResource(R.string.settings_export_csv),
                summary = stringResource(R.string.settings_export_csv_summary),
                onClick = { showExportWarning = true },
            )
            SettingsItem(
                title = stringResource(R.string.settings_delete_vault),
                onClick = { showDeleteVault = true },
            )

            // ── Atualizações e Sobre: cartões agrupados ──
            GroupLabel(stringResource(R.string.settings_system))
            GroupedItems(
                modifier = Modifier.padding(horizontal = 16.dp),
                items = listOf(
                    GroupItem(
                        title = stringResource(R.string.settings_check_updates),
                        summary = stringResource(
                            when {
                                !updateEnabled -> R.string.update_not_configured
                                updateChecking -> R.string.update_checking
                                else -> R.string.settings_check_updates_summary
                            },
                        ),
                        icon = Icons.Outlined.Download,
                        onClick = if (updateEnabled && !updateChecking) onCheckUpdates else null,
                    ),
                    GroupItem(
                        title = stringResource(R.string.settings_update_notices),
                        summary = when (updatePostpone.kind) {
                            PostponeKind.None -> stringResource(R.string.settings_update_notices_on)
                            PostponeKind.Snoozed -> stringResource(
                                R.string.settings_update_notices_snoozed,
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(updatePostpone.untilMillis)),
                            )
                            PostponeKind.Skipped -> stringResource(
                                R.string.settings_update_notices_skipped,
                                updatePostpone.version.orEmpty(),
                            )
                        },
                        icon = if (updatePostpone.kind == PostponeKind.None) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                        onClick = { showNotices = true },
                    ),
                ),
            )

            GroupLabel(stringResource(R.string.settings_about_section))
            GroupedItems(
                modifier = Modifier.padding(horizontal = 16.dp),
                items = listOf(
                    GroupItem(
                        title = stringResource(R.string.about_title),
                        summary = stringResource(R.string.about_entry_summary, BuildConfig.VERSION_NAME),
                        icon = Icons.Outlined.Info,
                        onClick = onAbout,
                        trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                    ),
                ),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showNotices) {
        val now = System.currentTimeMillis()
        val current = when (updatePostpone.kind) {
            PostponeKind.None -> 0
            PostponeKind.Snoozed -> if (updatePostpone.untilMillis - now <= 8L * 24 * 3600 * 1000) 7 else 30
            PostponeKind.Skipped -> -1
        }
        NoticesSheet(
            selected = current,
            onSelect = { days ->
                showNotices = false
                if (days == 0) onUndoPostpone() else onSnooze(days)
            },
            onDismiss = { showNotices = false },
        )
    }

    if (showAutoLock) {
        ChoiceDialog(
            title = stringResource(R.string.settings_autolock),
            options = autoLockOptions.map { it.first to stringResource(it.second) },
            selected = settings.autoLockSeconds,
            onSelect = { vm.setAutoLock(it); showAutoLock = false },
            onDismiss = { showAutoLock = false },
        )
    }
    if (showClipboard) {
        ChoiceDialog(
            title = stringResource(R.string.settings_clipboard),
            options = clipboardOptions.map { it.first to stringResource(it.second) },
            selected = settings.clipboardClearSeconds,
            onSelect = { vm.setClipboardClear(it); showClipboard = false },
            onDismiss = { showClipboard = false },
        )
    }
    if (showChangePassword) {
        ChangePasswordDialog(
            onConfirm = { current, new, confirm ->
                vm.changePassword(current, new, confirm)
                showChangePassword = false
            },
            onDismiss = { showChangePassword = false },
        )
    }
    restoreUri?.let { uri ->
        var password by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(R.string.restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.restore_replace_warning))
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.backup_password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.isNotEmpty(),
                    onClick = { vm.restore(uri, password); restoreUri = null },
                ) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text(stringResource(R.string.export_warning_title)) },
            text = { Text(stringResource(R.string.export_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    vm.beforePicker()
                    exportPicker.launch("babelmanager-passwords.csv")
                }) { Text(stringResource(R.string.export_anyway)) }
            },
            dismissButton = { TextButton(onClick = { showExportWarning = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showDeleteVault) {
        AlertDialog(
            onDismissRequest = { showDeleteVault = false },
            title = { Text(stringResource(R.string.delete_vault_title)) },
            text = { Text(stringResource(R.string.delete_vault_body)) },
            confirmButton = {
                TextButton(onClick = { showDeleteVault = false; vm.deleteVault() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteVault = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, onClick = { onSelect(value) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChangePasswordDialog(
    onConfirm: (current: String, new: String, confirm: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, stringResource(R.string.current_master_password))
                PasswordField(new, { new = it }, stringResource(R.string.new_master_password))
                PasswordField(confirm, { confirm = it }, stringResource(R.string.confirm_master_password))
                Text(
                    stringResource(R.string.change_password_biometric_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.isNotEmpty() && new.isNotEmpty(),
                onClick = { onConfirm(current, new, confirm) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Painel que sobe de baixo (como nos outros apps do mantenedor) com as opções de aviso de atualização. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticesSheet(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.settings_update_notices),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            listOf(
                0 to R.string.update_notices_normal,
                7 to R.string.update_notices_week,
                30 to R.string.update_notices_month,
            ).forEach { (days, label) ->
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (days == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(days) }
                        .padding(vertical = 20.dp),
                )
            }
        }
    }
}
