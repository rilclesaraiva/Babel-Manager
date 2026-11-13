package com.babelmanager.app.features.lock

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babelmanager.app.R
import com.babelmanager.app.core.security.PasswordStrength
import com.babelmanager.app.core.ui.PasswordField
import com.babelmanager.app.core.ui.StrengthIndicator
import com.babelmanager.app.core.ui.findActivity
import com.babelmanager.app.data.vault.VaultRepository

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockScreen(
    vm: LockViewModel,
    biometricEnabled: Boolean,
    onBeforePicker: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val creating = state == VaultRepository.State.Missing

    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var restorePassword by rememberSaveable { mutableStateOf("") }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    // Abre a biometria uma vez, ao mostrar a tela de desbloqueio.
    LaunchedEffect(biometricEnabled, creating) {
        if (biometricEnabled && !creating && !autoPrompted && activity != null) {
            autoPrompted = true
            vm.unlockWithBiometrics(activity)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(40.dp))
                }
            }
            Text(
                text = stringResource(if (creating) R.string.lock_create_title else R.string.lock_unlock_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(if (creating) R.string.lock_create_hint else R.string.lock_unlock_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            PasswordField(
                value = password,
                onValueChange = { password = it; vm.clearError() },
                label = stringResource(R.string.master_password),
                isError = ui.error == LockViewModel.Error.WrongPassword || ui.error == LockViewModel.Error.TooShort,
                imeAction = if (creating) ImeAction.Next else ImeAction.Done,
                onDone = { if (!creating) vm.unlock(password) },
            )

            if (creating) {
                if (password.isNotEmpty()) StrengthIndicator(PasswordStrength.estimate(password))
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it; vm.clearError() },
                    label = stringResource(R.string.confirm_master_password),
                    isError = ui.error == LockViewModel.Error.Mismatch,
                    imeAction = ImeAction.Done,
                    onDone = { vm.create(password, confirm) },
                )
            }

            ui.error?.let {
                Text(
                    text = stringResource(errorText(it, creating)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            if (ui.busy) {
                LoadingIndicator()
            } else {
                Button(
                    onClick = { if (creating) vm.create(password, confirm) else vm.unlock(password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotEmpty(),
                ) {
                    Text(stringResource(if (creating) R.string.create_vault else R.string.unlock))
                }
                if (!creating && biometricEnabled && activity != null) {
                    FilledTonalButton(
                        onClick = { vm.unlockWithBiometrics(activity) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.unlock_biometric), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (creating) {
                    TextButton(onClick = {
                        onBeforePicker()
                        picker.launch(arrayOf("*/*"))
                    }) {
                        Text(stringResource(R.string.restore_from_backup))
                    }
                }
            }
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null; restorePassword = "" },
            title = { Text(stringResource(R.string.restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.restore_password_hint))
                    PasswordField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = stringResource(R.string.backup_password),
                        imeAction = ImeAction.Done,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = restorePassword.isNotEmpty(),
                    onClick = {
                        vm.restore(uri, restorePassword)
                        restoreUri = null
                        restorePassword = ""
                    },
                ) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreUri = null; restorePassword = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun errorText(error: LockViewModel.Error, creating: Boolean): Int = when (error) {
    LockViewModel.Error.TooShort -> R.string.error_password_short
    LockViewModel.Error.Mismatch -> R.string.error_password_mismatch
    LockViewModel.Error.WrongPassword ->
        if (creating) R.string.error_backup_wrong_password else R.string.error_wrong_password
    LockViewModel.Error.InvalidBackup -> R.string.error_invalid_backup
    LockViewModel.Error.Failed -> R.string.error_generic
}
