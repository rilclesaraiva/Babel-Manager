package com.babelmanager.app.features.vault

import androidx.compose.material3.Button
import androidx.compose.foundation.layout.fillMaxSize
import com.babelmanager.app.core.ui.PageTitle
import com.babelmanager.app.core.ui.PageScaffold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babelmanager.app.R
import com.babelmanager.app.core.security.PasswordGenerator
import com.babelmanager.app.core.security.PasswordStrength
import com.babelmanager.app.core.security.RandomOptions
import com.babelmanager.app.core.ui.PasswordField
import com.babelmanager.app.core.ui.StrengthIndicator
import com.babelmanager.app.data.apps.InstalledApps
import com.babelmanager.app.data.autofill.AppUri
import com.babelmanager.app.data.vault.Entry
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    initial: Entry?,
    onSave: (Entry) -> Unit,
    onClose: () -> Unit,
) {
    val key = initial?.id ?: "new"
    var title by rememberSaveable(key) { mutableStateOf(initial?.title.orEmpty()) }
    var url by rememberSaveable(key) { mutableStateOf(initial?.url.orEmpty()) }
    var username by rememberSaveable(key) { mutableStateOf(initial?.username.orEmpty()) }
    var password by rememberSaveable(key) { mutableStateOf(initial?.password.orEmpty()) }
    var note by rememberSaveable(key) { mutableStateOf(initial?.note.orEmpty()) }
    val generator = remember { PasswordGenerator() }
    val context = LocalContext.current
    val apps = remember { InstalledApps(context.packageManager, context.packageName) }
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    val appPackage = AppUri.packageOf(url)

    PageScaffold(
        onBack = onClose,
        backIcon = Icons.Outlined.Close,
        backDescription = stringResource(R.string.cancel),
        floatingActions = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        Entry(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            username = username.trim(),
                            password = password,
                            url = url.trim(),
                            note = note,
                            createdAt = initial?.createdAt ?: 0L,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageTitle(
                title = stringResource(if (initial == null) R.string.add_password else R.string.edit_password),
                hasBack = true,
                endInset = 120.dp,
                containerPadding = 16.dp,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.entry_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            if (appPackage != null) {
                SelectedAppRow(apps, appPackage, onRemove = { url = "" })
            } else {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.website)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                OutlinedButton(onClick = { showAppPicker = true }) {
                    Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.select_app))
                }
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.password),
            )
            if (password.isNotEmpty()) StrengthIndicator(PasswordStrength.estimate(password))
            FilledTonalButton(onClick = { password = generator.random(RandomOptions()) }) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.suggest_strong_password))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.note)) },
                minLines = 3,
            )
        }
    }

    if (showAppPicker) {
        AppPickerSheet(
            apps = apps,
            onPick = { app ->
                url = AppUri.of(app.packageName)
                if (title.isBlank()) title = app.label
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
        )
    }
}
