package com.babelmanager.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babelmanager.app.core.ui.BabelTheme
import com.babelmanager.app.features.generator.GeneratorSection

/**
 * Painel rápido aberto pelo botão das configurações rápidas: o mesmo gerador do app (tamanho, tipos de caractere,
 * palavras) numa folha que sobe de baixo. Não toca no cofre nem pede desbloqueio; a senha gerada só existe na memória.
 * Roda em tarefa própria (sem afinidade com o app) e em instância única: tocar de novo no botão não abre outro.
 */
class QuickPanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sem animação de janela: quem anima é a folha, por dentro (senão aparece flutuando no meio e depois desce).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val container = (application as BabelApp).container
        setContent {
            val appearance by container.settings.settings.collectAsStateWithLifecycle()
            BabelTheme(appearance.themeMode, appearance.dynamicColor, appearance.siteIcons) {
                val context = LocalContext.current
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                fun dismiss() { visible.targetState = false }
                // Terminou de descer: fecha a atividade.
                LaunchedEffect(visible.isIdle, visible.currentState) {
                    if (visible.isIdle && !visible.currentState) finish()
                }
                BackHandler(onBack = ::dismiss)
                val scrim by animateFloatAsState(if (visible.targetState) 0.5f else 0f, tween(250), label = "scrim")
                val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrim))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AnimatedVisibility(
                        visibleState = visible,
                        enter = slideInVertically(tween(300)) { it },
                        exit = slideOutVertically(tween(250)) { it },
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxHeight)
                                // Toques na folha não fecham o painel.
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp),
                            ) {
                                Box(
                                    Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(vertical = 12.dp)
                                        .width(32.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                                )
                                Text(
                                    stringResource(R.string.quick_panel_title),
                                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                GeneratorSection(container.settings.current.clipboardClearSeconds)
                                TextButton(
                                    onClick = {
                                        startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        finish()
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) { Text(stringResource(R.string.quick_panel_open)) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
