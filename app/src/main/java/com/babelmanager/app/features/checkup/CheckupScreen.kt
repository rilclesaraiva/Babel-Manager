package com.babelmanager.app.features.checkup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.launch
import com.babelmanager.app.data.breach.LeakScanner
import android.text.format.DateUtils
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.babelmanager.app.data.vault.Entry

/**
 * Resultado do check-up (repetidas, fracas e vazadas), para a tela de ferramentas. Sem rolagem própria.
 * A varredura de vazamentos é feita por `LeakScanner`; aqui só se mostra o resultado e o botão "Verificar agora".
 */
@Composable
fun CheckupSection(
    entries: List<Entry>,
    onOpen: (Entry) -> Unit,
    lastScanAt: Long,
    leakedIds: Set<String>,
    scanState: LeakScanner.State,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = remember(entries) { PasswordHealth.analyze(entries) }
    // Entrada alterada depois da varredura: o resultado dela já não vale.
    val leakedCounts = remember(leakedIds) { LeakScanner.parse(leakedIds) }
    val leaked = remember(entries, leakedCounts, lastScanAt) {
        entries.filter { it.id in leakedCounts && it.updatedAt <= lastScanAt }.sortedBy { it.title.lowercase() }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SummaryCard(report)
        LeakCheck(
            scanState = scanState,
            enabled = report.checked > 0,
            lastScanAt = lastScanAt,
            leaked = leaked,
            leakedCounts = leakedCounts,
            onScan = onScan,
            onOpen = onOpen,
        )
        if (report.reusedGroups.isNotEmpty()) {
            SectionHeader(
                pluralStringResource(R.plurals.checkup_reused, report.reusedCount, report.reusedCount),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.checkup_reused_hint),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.reusedGroups.forEach { group ->
                Text(
                    pluralStringResource(R.plurals.checkup_same_password, group.size, group.size),
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                group.forEach { EntryRow(it, onOpen) }
            }
        }
        if (report.weak.isNotEmpty()) {
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
            report.weak.forEach { EntryRow(it, onOpen) }
        }
        Text(
            stringResource(R.string.checkup_local_note),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun LeakCheck(
    scanState: LeakScanner.State,
    enabled: Boolean,
    lastScanAt: Long,
    leaked: List<Entry>,
    leakedCounts: Map<String, Int>,
    onScan: () -> Unit,
    onOpen: (Entry) -> Unit,
) {
    SectionHeader(stringResource(R.string.checkup_leak_title), modifier = Modifier.padding(top = 8.dp))
    Text(
        stringResource(R.string.checkup_leak_hint),
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = if (lastScanAt > 0L) {
            stringResource(
                R.string.checkup_last_scan,
                DateUtils.getRelativeTimeSpanString(lastScanAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
            )
        } else {
            stringResource(R.string.checkup_never_scanned)
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
    )
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onScan, enabled = enabled && scanState != LeakScanner.State.Running) {
            Text(stringResource(R.string.checkup_leak_button))
        }
        if (scanState == LeakScanner.State.Running) {
            CircularProgressIndicator(modifier = Modifier.padding(start = 16.dp).size(24.dp), strokeWidth = 3.dp)
        }
    }
    if (scanState == LeakScanner.State.Failed) {
        Text(
            stringResource(R.string.checkup_leak_error),
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (lastScanAt > 0L) {
        if (leaked.isEmpty()) {
            Text(stringResource(R.string.checkup_leak_none), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            Text(
                pluralStringResource(R.plurals.checkup_leaked, leaked.size, leaked.size),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.checkup_leak_found_hint),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            leaked.forEach { EntryRow(it, onOpen, seenTimes = leakedCounts[it.id] ?: 0) }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, onOpen: (Entry) -> Unit, seenTimes: Int = 0) {
    ListItem(
        headlineContent = { Text(entry.title, maxLines = 1) },
        supportingContent = {
            Column {
                if (entry.username.isNotEmpty()) Text(entry.username, maxLines = 1)
                if (seenTimes > 0) {
                    Text(
                        stringResource(R.string.checkup_leak_seen, seenTimes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        leadingContent = { EntryAvatar(entry.title, url = entry.url) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable { onOpen(entry) },
    )
}
