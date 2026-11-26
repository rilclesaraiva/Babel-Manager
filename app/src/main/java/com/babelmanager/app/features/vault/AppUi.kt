package com.babelmanager.app.features.vault

import com.babelmanager.app.core.ui.BabelSheet
import androidx.compose.material.icons.outlined.Search
import com.babelmanager.app.core.ui.trackFieldFocus
import com.babelmanager.app.core.ui.clearFocusOnTapOutside
import com.babelmanager.app.core.ui.ClearFocusWhenKeyboardCloses
import com.babelmanager.app.core.ui.FieldShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.babelmanager.app.R
import com.babelmanager.app.data.apps.AppInfo
import com.babelmanager.app.data.apps.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ícone do app, carregado fora da thread principal. */
@Composable
fun AppIcon(apps: InstalledApps, packageName: String, size: Dp = 40.dp) {
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { apps.icon(packageName)?.toBitmap(96, 96)?.asImageBitmap() }
    }
    val image = bitmap
    if (image != null) Image(image, contentDescription = null, modifier = Modifier.size(size))
    else Spacer(Modifier.size(size))
}

/** App escolhido para a entrada, com X para remover. */
@Composable
fun SelectedAppRow(apps: InstalledApps, packageName: String, onRemove: () -> Unit) {
    val name = remember(packageName) { apps.label(packageName) ?: packageName }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(apps, packageName)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.entry_app), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (name != packageName) {
                    Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.remove_app))
            }
        }
    }
}

/** Lista de apps instalados (ícone e nome) com busca. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(apps: InstalledApps, onPick: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { list = apps.launchable() }

    BabelSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = FieldShape,
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).trackFieldFocus(),
            )
            val all = list
            if (all == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val q = query.trim()
                val shown = if (q.isEmpty()) all else all.filter {
                    it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
                }
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.app_picker_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn {
                    items(shown, key = { it.packageName }) { app ->
                        ListItem(
                            leadingContent = { AppIcon(apps, app.packageName) },
                            headlineContent = { Text(app.label, maxLines = 1) },
                            supportingContent = { Text(app.packageName, maxLines = 1) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable { onPick(app) },
                        )
                    }
                }
            }
        }
    }
}
