package com.babelmanager.app.features.checkup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babelmanager.app.R
import com.babelmanager.app.core.ui.EntryAvatar
import com.babelmanager.app.core.ui.SectionHeader
import com.babelmanager.app.core.ui.TabScaffold
import com.babelmanager.app.data.vault.Entry

@Composable
fun CheckupScreen(
    entries: List<Entry>,
    onOpen: (Entry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = remember(entries) { PasswordHealth.analyze(entries) }

    TabScaffold(title = stringResource(R.string.tab_checkup), modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                SummaryCard(report)
            }
            if (report.reusedGroups.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader(
                            pluralStringResource(R.plurals.checkup_reused, report.reusedCount, report.reusedCount),
                        )
                        Text(
                            stringResource(R.string.checkup_reused_hint),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                report.reusedGroups.forEach { group ->
                    item {
                        Text(
                            pluralStringResource(R.plurals.checkup_same_password, group.size, group.size),
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    items(group, key = { "reused-${it.id}" }) { EntryRow(it, onOpen) }
                }
            }
            if (report.weak.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader(
                            pluralStringResource(R.plurals.checkup_weak, report.weak.size, report.weak.size),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            stringResource(R.string.checkup_weak_hint),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(report.weak, key = { "weak-${it.id}" }) { EntryRow(it, onOpen) }
            }
            item {
                Text(
                    stringResource(R.string.checkup_local_note),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(report: HealthReport) {
    val ok = !report.hasIssues
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                contentDescription = null,
            )
            Text(
                text = pluralStringResource(R.plurals.checkup_checked, report.checked, report.checked),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(
                    when {
                        report.checked == 0 -> R.string.checkup_nothing
                        ok -> R.string.checkup_all_good
                        else -> R.string.checkup_issues
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, onOpen: (Entry) -> Unit) {
    ListItem(
        headlineContent = { Text(entry.title, maxLines = 1) },
        supportingContent = { if (entry.username.isNotEmpty()) Text(entry.username, maxLines = 1) },
        leadingContent = { EntryAvatar(entry.title) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable { onOpen(entry) },
    )
}
