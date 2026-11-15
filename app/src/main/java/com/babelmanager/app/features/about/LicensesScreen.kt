package com.babelmanager.app.features.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babelmanager.app.R
import com.babelmanager.app.core.ui.GroupItem
import com.babelmanager.app.core.ui.GroupedItems

private class Credit(val name: String, val license: String, val url: String)

// Nomes próprios e licenças: não traduzem.
private val CREDITS = listOf(
    Credit("Babel Manager", "GNU General Public License v3.0", "https://www.gnu.org/licenses/gpl-3.0.html"),
    Credit("AndroidX (Activity, Core, Lifecycle, Fragment, Biometric, Autofill)", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    Credit("Jetpack Compose and Material 3", "Apache License 2.0", "https://developer.android.com/jetpack/compose"),
    Credit("Material Icons", "Apache License 2.0", "https://github.com/google/material-design-icons"),
    Credit("Kotlin and kotlinx.coroutines", "Apache License 2.0", "https://kotlinlang.org"),
    Credit("GitHub (update check and developer photo)", "GitHub Terms of Service", "https://docs.github.com/en/site-policy/github-terms/github-terms-of-service"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.about_licenses)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.about_licenses_body),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GroupedItems(
                modifier = Modifier.padding(top = 8.dp),
                items = CREDITS.map { credit ->
                    GroupItem(
                        title = credit.name,
                        summary = credit.license,
                        icon = Icons.Outlined.Description,
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(credit.url))) } },
                    )
                },
            )
        }
    }
}
