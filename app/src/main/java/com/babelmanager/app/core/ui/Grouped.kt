package com.babelmanager.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Título de grupo em caixa alta, na cor de destaque (Atualizações, Sobre, Legal…). */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Círculo com ícone, usado à esquerda dos itens agrupados. */
@Composable
fun IconBadge(icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier, size: Int = 48) {
    Surface(modifier = modifier.size(size.dp), shape = CircleShape, color = container, contentColor = content) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size((size / 2).dp))
        }
    }
}

class GroupItem(
    val title: String,
    val icon: ImageVector,
    val summary: String? = null,
    val badgeContainer: Color? = null,
    val badgeContent: Color? = null,
    val onClick: (() -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null,
)

/** Cartões arredondados e colados: cantos grandes nas pontas do grupo, pequenos entre os itens. */
@Composable
fun GroupedItems(items: List<GroupItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            val big = 28.dp
            val small = 6.dp
            val shape = RoundedCornerShape(
                topStart = if (index == 0) big else small,
                topEnd = if (index == 0) big else small,
                bottomStart = if (index == items.lastIndex) big else small,
                bottomEnd = if (index == items.lastIndex) big else small,
            )
            val body: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IconBadge(
                        icon = item.icon,
                        container = item.badgeContainer ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = item.badgeContent ?: MaterialTheme.colorScheme.onSurface,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        if (item.summary != null) {
                            Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item.trailing?.invoke()
                }
            }
            val click = item.onClick
            if (click != null) {
                Surface(onClick = click, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) { body() }
            } else {
                Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) { body() }
            }
        }
    }
}
