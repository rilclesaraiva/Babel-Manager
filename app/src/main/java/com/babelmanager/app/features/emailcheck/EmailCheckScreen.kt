package com.babelmanager.app.features.emailcheck

import com.babelmanager.app.core.ui.trackFieldFocus
import com.babelmanager.app.core.ui.FieldShape
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babelmanager.app.R
import com.babelmanager.app.core.ui.PageScaffold
import com.babelmanager.app.core.ui.PageTitle
import com.babelmanager.app.data.breach.EmailChecker
import com.babelmanager.app.data.breach.WatchedEmail

/**
 * E-mails cadastrados para a verificação de vazamentos (XposedOrNot). Aqui o e-mail inteiro vai ao serviço, por isso
 * o texto explica isso antes de qualquer botão e a verificação diária vem desligada.
 */
@Composable
fun EmailCheckScreen(
    items: List<WatchedEmail>,
    state: EmailChecker.State,
    autoCheck: Boolean,
    onAutoCheck: (Boolean) -> Unit,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit,
    onCheck: () -> Unit,
    onBack: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val running = state == EmailChecker.State.Running
    PageScaffold(onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 24.dp),
        ) {
            PageTitle(stringResource(R.string.email_check_title), hasBack = true)
            Text(
                stringResource(R.string.email_check_privacy),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; invalid = false },
                    modifier = Modifier.weight(1f).trackFieldFocus(),
                    shape = FieldShape,
                    singleLine = true,
                    label = { Text(stringResource(R.string.email_check_field)) },
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.email_check_invalid, EmailChecker.MAX_EMAILS)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Button(onClick = { if (onAdd(text)) text = "" else invalid = true }, enabled = text.isNotBlank()) {
                    Text(stringResource(R.string.email_check_add))
                }
            }
            items.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.email, maxLines = 1) },
                    supportingContent = {
                        val breaches = item.breaches
                        when {
                            breaches == null -> Text(stringResource(R.string.email_check_unchecked))
                            breaches.isEmpty() -> Text(stringResource(R.string.email_check_clean))
                            else -> Column {
                                Text(
                                    pluralStringResource(R.plurals.email_check_found, breaches.size, breaches.size),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(breaches.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemove(item.email) }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.email_check_remove))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onCheck, enabled = items.isNotEmpty() && !running) {
                    Text(stringResource(R.string.email_check_button))
                }
                if (running) CircularProgressIndicator(Modifier.padding(start = 16.dp).size(24.dp), strokeWidth = 3.dp)
            }
            val lastCheck = items.maxOfOrNull { it.checkedAt } ?: 0L
            if (lastCheck > 0L) {
                Text(
                    stringResource(
                        R.string.email_check_last,
                        DateUtils.getRelativeTimeSpanString(lastCheck, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (state == EmailChecker.State.Failed) {
                Text(
                    stringResource(R.string.email_check_error),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.email_check_auto)) },
                supportingContent = { Text(stringResource(R.string.email_check_auto_summary)) },
                trailingContent = { Switch(checked = autoCheck, onCheckedChange = onAutoCheck) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.email_check_source),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
