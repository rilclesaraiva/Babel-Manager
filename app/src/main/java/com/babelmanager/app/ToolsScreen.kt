package com.babelmanager.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babelmanager.app.core.ui.PageScaffold
import com.babelmanager.app.core.ui.PageTitle
import com.babelmanager.app.core.ui.SectionHeader
import com.babelmanager.app.data.breach.LeakScanner
import com.babelmanager.app.data.vault.Entry
import com.babelmanager.app.features.checkup.CheckupSection
import com.babelmanager.app.features.generator.GeneratorSection

/**
 * Área de ferramentas: gerador de senhas e check-up numa tela só. Fica na entrada (e não numa feature)
 * porque junta duas features, e features não importam umas às outras.
 */
@Composable
fun ToolsScreen(
    clipboardSeconds: Int,
    entries: List<Entry>,
    onOpen: (Entry) -> Unit,
    lastScanAt: Long,
    leakedIds: Set<String>,
    scanState: LeakScanner.State,
    onScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(onBack = onBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            PageTitle(stringResource(R.string.tools_title), hasBack = true)
            SectionHeader(stringResource(R.string.tab_generator))
            GeneratorSection(clipboardSeconds)
            SectionHeader(stringResource(R.string.tab_checkup), modifier = Modifier.padding(top = 16.dp))
            CheckupSection(
                entries = entries,
                onOpen = onOpen,
                lastScanAt = lastScanAt,
                leakedIds = leakedIds,
                scanState = scanState,
                onScan = onScan,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
