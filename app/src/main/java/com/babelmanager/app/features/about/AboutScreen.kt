package com.babelmanager.app.features.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.babelmanager.app.BuildConfig
import com.babelmanager.app.R
import com.babelmanager.app.core.ui.EntryAvatar
import com.babelmanager.app.core.ui.SectionHeader
import com.babelmanager.app.data.update.UpdateRepository

private val repoConfigured: Boolean get() = BuildConfig.REPO != UpdateRepository.PLACEHOLDER

private val repoUrl: String get() = "https://github.com/${BuildConfig.REPO}"

private val pagesUrl: String
    get() {
        val (owner, name) = BuildConfig.REPO.split('/', limit = 2)
        return "https://$owner.github.io/$name"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Cabeçalho: ícone, nome, versão e tipo de build lidos do build.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EntryAvatar(stringResource(R.string.app_name), size = 72)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            if (repoConfigured) {
                SectionHeader(stringResource(R.string.about_developer))
                LinkItem(context, BuildConfig.DEVELOPER, "https://github.com/${BuildConfig.DEVELOPER}")

                SectionHeader(stringResource(R.string.about_project))
                LinkItem(context, stringResource(R.string.about_source), repoUrl)
                LinkItem(context, stringResource(R.string.about_whats_new), "$repoUrl/blob/main/CHANGELOG.md")
                LinkItem(context, stringResource(R.string.about_report), "$repoUrl/issues")

                SectionHeader(stringResource(R.string.about_legal))
                LinkItem(context, stringResource(R.string.about_privacy), "$pagesUrl/privacy")
                LinkItem(context, stringResource(R.string.about_terms), "$pagesUrl/terms")
            }

            SectionHeader(stringResource(R.string.about_licenses))
            Text(
                stringResource(R.string.about_licenses_body),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (repoConfigured) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(
                    stringResource(R.string.about_support_invite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open(context, repoUrl) }
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LinkItem(context: Context, label: String, url: String) {
    ListItem(
        headlineContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable { open(context, url) },
    )
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
