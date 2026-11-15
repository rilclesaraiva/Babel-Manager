package com.babelmanager.app.features.vault

import com.babelmanager.app.core.ui.PageTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.layout.statusBarsPadding
import com.babelmanager.app.core.ui.FloatingCircleButton
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.babelmanager.app.R
import com.babelmanager.app.core.ui.EntryAvatar
import com.babelmanager.app.core.ui.TabScaffold
import com.babelmanager.app.data.vault.Entry

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VaultListScreen(
    entries: List<Entry>,
    totalCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    showBackupBanner: Boolean,
    onOpenBackupSettings: () -> Unit,
    onOpen: (Entry) -> Unit,
    onAdd: () -> Unit,
    onLock: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }

    TabScaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_password))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                // Bloqueio e Ajustes ficam parados no canto (flutuantes); o título deixa a folga deles.
                PageTitle(stringResource(R.string.app_name), endInset = 120.dp)
            }
            item {
                // Cartão grande de destaque (como os da Play Store): abre gerador e check-up.
                Surface(
                    onClick = onOpenTools,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(Modifier.fillMaxWidth().height(168.dp).padding(24.dp)) {
                        Icon(
                            Icons.Outlined.Build,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.TopStart).size(40.dp),
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                        )
                        Column(Modifier.align(Alignment.BottomStart)) {
                            Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.tools_summary), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                )
            }
            if (showBackupBanner && !bannerDismissed) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.backup_banner_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.backup_banner_body),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { bannerDismissed = true }) {
                                    Text(stringResource(R.string.not_now))
                                }
                                TextButton(onClick = onOpenBackupSettings) {
                                    Text(stringResource(R.string.choose_location))
                                }
                            }
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(if (totalCount == 0) R.string.vault_empty else R.string.no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(entries, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.title, maxLines = 1) },
                    supportingContent = {
                        if (entry.username.isNotEmpty()) Text(entry.username, maxLines = 1)
                    },
                    leadingContent = { EntryAvatar(entry.title, url = entry.url) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable { onOpen(entry) },
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingCircleButton(Icons.Outlined.Lock, stringResource(R.string.lock_now), onLock)
            FloatingCircleButton(Icons.Outlined.Settings, stringResource(R.string.tab_settings), onOpenSettings)
        }
        }
    }
}
