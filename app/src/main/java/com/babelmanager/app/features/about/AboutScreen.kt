package com.babelmanager.app.features.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.babelmanager.app.BuildConfig
import com.babelmanager.app.R
import com.babelmanager.app.core.ui.EntryAvatar
import com.babelmanager.app.core.ui.GroupItem
import com.babelmanager.app.core.ui.GroupLabel
import com.babelmanager.app.core.ui.PageScaffold
import com.babelmanager.app.core.ui.PageTitle
import com.babelmanager.app.core.ui.GroupedItems
import com.babelmanager.app.data.about.AvatarStore
import com.babelmanager.app.data.update.UpdateRepository

private val repoConfigured: Boolean get() = BuildConfig.REPO != UpdateRepository.PLACEHOLDER

private val repoUrl: String get() = "https://github.com/${BuildConfig.REPO}"

private val pagesUrl: String
    get() {
        val (owner, name) = BuildConfig.REPO.split('/', limit = 2)
        return "https://$owner.github.io/$name"
    }

@Composable
fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    PageScaffold(onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 24.dp),
        ) {
            PageTitle(stringResource(R.string.about_screen_title), hasBack = true)
            // Cabeçalho: ícone, nome, versão e tipo de build (lidos do build) e a descrição.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(colorResource(R.color.ic_launcher_background)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize().scale(1.35f))
                }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(BuildConfig.VERSION_NAME)
                    Pill(BuildConfig.BUILD_TYPE.uppercase())
                }
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (repoConfigured) {
                GroupLabel(stringResource(R.string.about_developer))
                DeveloperCard(context, Modifier.padding(horizontal = 24.dp))

                GroupLabel(stringResource(R.string.about_project))
                GroupedItems(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    items = listOf(
                        GroupItem(
                            title = stringResource(R.string.about_source),
                            summary = repoUrl.removePrefix("https://"),
                            icon = Icons.Outlined.Code,
                            badgeContainer = colors.secondaryContainer,
                            badgeContent = colors.onSecondaryContainer,
                            onClick = { open(context, repoUrl) },
                        ),
                        GroupItem(
                            title = stringResource(R.string.about_whats_new),
                            summary = stringResource(R.string.about_whats_new_summary),
                            icon = Icons.Outlined.Verified,
                            badgeContainer = colors.tertiaryContainer,
                            badgeContent = colors.onTertiaryContainer,
                            onClick = { open(context, "$repoUrl/blob/main/CHANGELOG.md") },
                        ),
                        GroupItem(
                            title = stringResource(R.string.about_report),
                            summary = stringResource(R.string.about_report_summary),
                            icon = Icons.Outlined.BugReport,
                            badgeContainer = colors.primaryContainer,
                            badgeContent = colors.onPrimaryContainer,
                            onClick = { open(context, "$repoUrl/issues") },
                        ),
                    ),
                )
            }

            GroupLabel(stringResource(R.string.about_legal))
            GroupedItems(
                modifier = Modifier.padding(horizontal = 24.dp),
                items = buildList {
                    if (repoConfigured) {
                        add(
                            GroupItem(
                                title = stringResource(R.string.about_privacy),
                                summary = stringResource(R.string.about_privacy_summary),
                                icon = Icons.Outlined.Lock,
                                onClick = { open(context, "$pagesUrl/privacy") },
                            ),
                        )
                        add(
                            GroupItem(
                                title = stringResource(R.string.about_terms),
                                summary = stringResource(R.string.about_terms_summary),
                                icon = Icons.Outlined.Description,
                                onClick = { open(context, "$pagesUrl/terms") },
                            ),
                        )
                    }
                    add(
                        GroupItem(
                            title = stringResource(R.string.about_licenses),
                            summary = stringResource(R.string.about_licenses_summary),
                            icon = Icons.Outlined.Description,
                            onClick = onLicenses,
                        ),
                    )
                },
            )

            if (repoConfigured) {
                GroupedItems(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                    items = listOf(
                        GroupItem(
                            title = stringResource(R.string.about_support_title),
                            summary = stringResource(R.string.about_support_body),
                            icon = Icons.Outlined.Favorite,
                            badgeContainer = colors.tertiaryContainer,
                            badgeContent = colors.onTertiaryContainer,
                            onClick = { open(context, repoUrl) },
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Foto, nome e link de quem mantém o projeto. A foto vem do GitHub; sem ela, mostra a inicial. */
@Composable
private fun DeveloperCard(context: Context, modifier: Modifier = Modifier) {
    val store = remember { AvatarStore(context.cacheDir, BuildConfig.DEVELOPER, "BabelManager/${BuildConfig.VERSION_NAME}") }
    val avatar by produceState<ImageBitmap?>(null) { value = store.load()?.asImageBitmap() }
    Surface(
        onClick = { open(context, "https://github.com/${BuildConfig.DEVELOPER}") },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val photo = avatar
            if (photo != null) {
                Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(CircleShape))
            } else {
                EntryAvatar(BuildConfig.DEVELOPER, size = 52)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(BuildConfig.DEVELOPER, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.about_made_in_brazil) + " · github.com/" + BuildConfig.DEVELOPER,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
