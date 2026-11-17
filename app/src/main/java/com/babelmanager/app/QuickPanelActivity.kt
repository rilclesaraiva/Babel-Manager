package com.babelmanager.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babelmanager.app.core.ui.BabelTheme
import com.babelmanager.app.features.generator.GeneratorSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Painel rápido aberto pelo botão das configurações rápidas: o mesmo gerador do app (tamanho, tipos de caractere,
 * palavras) num painel que sobe de baixo (`ModalBottomSheet`, como no BoomListener e no Intruphon). Não toca no cofre nem pede desbloqueio; a senha gerada só existe na memória.
 * Roda em tarefa própria (sem afinidade com o app) e em instância única: tocar de novo no botão não abre outro.
 */
class QuickPanelActivity : ComponentActivity() {

    // O sistema abre a atividade com a animação do botão crescendo (e a janela de abertura); se o painel começasse a subir
    // já nessa hora, ficaria escondido atrás dela e a pessoa só o veria pronto. Ele só entra depois que a janela tem foco.
    private var windowFocused by mutableStateOf(false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) windowFocused = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val container = (application as BabelApp).container
        setContent {
            val appearance by container.settings.settings.collectAsStateWithLifecycle()
            BabelTheme(appearance.themeMode, appearance.dynamicColor, appearance.siteIcons) {
                var ready by remember { mutableStateOf(false) }
                LaunchedEffect(windowFocused) {
                    if (windowFocused) {
                        delay(REVEAL_DELAY_MS)
                        ready = true
                    }
                }
                if (ready) {
                    QuickPanelSheet(
                        clipboardSeconds = container.settings.current.clipboardClearSeconds,
                        onOpenApp = {
                            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        },
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    private companion object {
        /** Folga depois do foco para a animação de abertura do sistema terminar antes de o painel subir. */
        const val REVEAL_DELAY_MS = 350L
    }
}

/**
 * Mesmo painel dos outros apps do mantenedor (BoomListener, Intruphon): `ModalBottomSheet` do Material 3 sobre a janela
 * transparente. O voltar é nosso: o painel desce e só então a atividade fecha (o do M3 encolheria com a animação do sistema).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickPanelSheet(clipboardSeconds: Int, onOpenApp: () -> Unit, onDismiss: () -> Unit) {
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
        val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.quick_panel_title),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            GeneratorSection(clipboardSeconds)
            TextButton(onClick = onOpenApp, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.quick_panel_open))
            }
        }
    }
}
