package com.babelmanager.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.babelmanager.app.R

// Padrão de topo das telas (o mesmo do BoomListener e do Joule Arc): nada de barra presa.
// O título é a primeira linha da página e rola junto; só o voltar (e, se houver, as ações)
// ficam parados no canto, por cima da página. O título começa depois do voltar
// ([BackButtonInset]) para o botão nunca ficar por cima dele.

/** Largura que o título deixa livre à esquerda para não ficar por baixo do [BackButton] (8 + 48 + folga). */
val BackButtonInset = 72.dp

/** Botão redondo, levemente transparente, parado sobre a página. */
@Composable
fun FloatingCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription)
        }
    }
}

/**
 * Título da página: primeira linha do conteúdo que rola. Já desce abaixo da barra de status.
 *
 * @param hasBack a tela tem voltar parado no canto: o título começa depois dele.
 * @param endInset espaço livre à direita para ações paradas no canto (ex.: Salvar).
 * @param containerPadding recuo horizontal que o contêiner já aplicou (descontado do título).
 * @param actions ações que rolam junto com o título (telas de aba, sem voltar).
 */
@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
    hasBack: Boolean = false,
    endInset: Dp = 16.dp,
    containerPadding: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 80.dp)
            .padding(
                start = ((if (hasBack) BackButtonInset else 16.dp) - containerPadding).coerceAtLeast(0.dp),
                end = (endInset - containerPadding).coerceAtLeast(0.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/**
 * Tela de dentro (com voltar): o voltar fica parado no canto e as [floatingActions]
 * no canto oposto. O conteúdo ocupa a tela toda e recebe só a folga de baixo (barra de
 * gestos) para somar ao fim da rolagem; o próprio conteúdo começa com um [PageTitle].
 */
@Composable
fun PageScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack,
    backDescription: String = stringResource(R.string.back),
    floatingActions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            content(PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            FloatingCircleButton(
                icon = backIcon,
                contentDescription = backDescription,
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            )
            Row(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { floatingActions() }
        }
    }
}
