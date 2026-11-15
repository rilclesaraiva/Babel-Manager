package com.babelmanager.app.features.update

import androidx.compose.foundation.layout.Box
import com.babelmanager.app.core.ui.FloatingCircleButton
import com.babelmanager.app.core.ui.PageTitle
import com.babelmanager.app.core.ui.PageScaffold
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.babelmanager.app.R
import com.babelmanager.app.data.update.ReleaseInfo

/** Painel em tela cheia, no topo de qualquer tela do app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePrompt(
    release: ReleaseInfo,
    onDismiss: () -> Unit,
    onSnoozeWeek: () -> Unit,
    onSnoozeMonth: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            PageScaffold(
                onBack = onDismiss,
                backIcon = Icons.Outlined.Close,
                backDescription = stringResource(R.string.not_now),
                floatingActions = {
                    Box {
                        FloatingCircleButton(
                            icon = Icons.Outlined.Notifications,
                            contentDescription = stringResource(R.string.update_remind_menu),
                            onClick = { menu = true },
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.update_snooze_week)) },
                                onClick = { menu = false; onSnoozeWeek() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.update_snooze_month)) },
                                onClick = { menu = false; onSnoozeMonth() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.update_skip_version)) },
                                onClick = { menu = false; onSkip() },
                            )
                        }
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PageTitle(
                        title = stringResource(R.string.update_available),
                        hasBack = true,
                        endInset = 72.dp,
                        containerPadding = 20.dp,
                    )
                    Text(release.version, style = MaterialTheme.typography.titleLarge)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReleaseNotes(release.notes)
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                        Button(onClick = {
                            val target = release.downloadUrl ?: release.pageUrl
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                        }) {
                            Text(stringResource(R.string.update_download))
                        }
                    }
                }
            }
        }
    }
}

/** Mostra a seção do CHANGELOG: títulos `###`, itens `-` e destaques `>`. */
@Composable
private fun ReleaseNotes(notes: String) {
    for (raw in notes.lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith(">") -> Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    line.removePrefix(">").trim(),
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            line.startsWith("#") -> Text(
                line.trimStart('#').trim(),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            line.startsWith("- ") || line.startsWith("* ") -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium)
                Text(line.drop(2), style = MaterialTheme.typography.bodyMedium)
            }
            else -> Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
