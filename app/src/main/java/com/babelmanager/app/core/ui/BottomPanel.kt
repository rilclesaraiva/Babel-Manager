package com.babelmanager.app.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babelmanager.app.R
import kotlinx.coroutines.launch

/** O conteúdo de um [BabelSheet]: uma coluna que também sabe fechar o painel descendo. */
interface BabelSheetScope : ColumnScope {
    /** Fecha o painel com a animação de descer (e chama o `onDismiss` no fim). */
    fun dismiss()
}

private class BabelSheetScopeImpl(column: ColumnScope, private val close: () -> Unit) :
    BabelSheetScope, ColumnScope by column {
    override fun dismiss() = close()
}

/**
 * O único jeito de perguntar ou avisar algo no app: um painel que sobe de baixo, como no BoomListener e no Intruphon
 * (nada de janelas no meio da tela). O voltar é nosso: o painel desce e só então fecha, em vez de encolher com a
 * animação do sistema. Rola se o conteúdo for alto, sobe junto com o teclado e nunca chega ao topo da tela.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabelSheet(
    onDismiss: () -> Unit,
    content: @Composable BabelSheetScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val hideThenDismiss: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() } }
    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(onBack = hideThenDismiss)
        ClearFocusWhenKeyboardCloses()
        val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
        Column(
            Modifier
                .heightIn(max = maxHeight)
                .clearFocusOnTapOutside()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            val sheetScope = remember(this, hideThenDismiss) { BabelSheetScopeImpl(this, hideThenDismiss) }
            sheetScope.content()
        }
    }
}

/**
 * Pergunta com confirmação, em painel: título, explicação, [content] opcional (campos, avisos), o botão que confirma
 * (em vermelho se [destructive]) e "Cancelar". Confirmar chama [onConfirm] e fecha o painel descendo.
 */
@Composable
fun ConfirmPanel(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    BabelSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
            Button(
                onClick = {
                    onConfirm()
                    this@BabelSheet.dismiss()
                },
                enabled = confirmEnabled,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(confirmLabel) }
            TextButton(onClick = { this@BabelSheet.dismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
