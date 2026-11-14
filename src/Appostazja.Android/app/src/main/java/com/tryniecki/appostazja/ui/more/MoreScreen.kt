package com.tryniecki.appostazja.ui.more

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.api.model.DatasetStatusDto
import com.tryniecki.appostazja.browser.rememberOpenCustomTab
import com.tryniecki.appostazja.ui.formatApiTimestamp
import com.tryniecki.appostazja.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import java.util.Locale

private const val GITHUB_URL = "https://github.com/kcrg/Appostazja"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    themeMode: ThemeMode,
    loadDatasetStatus: suspend () -> DatasetStatusDto,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val openCustomTab = rememberOpenCustomTab()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val email = stringResource(R.string.more_contact_email)
    val dataSourceUrl = stringResource(R.string.map_data_source_url)
    val datasetStatus by produceState<DatasetStatusDto?>(initialValue = null) {
        value = try {
            loadDatasetStatus()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null // The source description is still useful without the update date.
        }
    }
    val dataSourceBody = datasetStatus?.let { dataSourceDescription(it, LocalLocale.current.platformLocale) }
        ?: stringResource(R.string.more_data_source_body)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }, scrollBehavior = scrollBehavior) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoreSection(stringResource(R.string.more_data_source)) {
                LinkRow(
                    title = stringResource(R.string.more_data_source_title),
                    body = dataSourceBody,
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_database_outline,
                    onClick = { openCustomTab(dataSourceUrl) },
                )
            }

            MoreSection(stringResource(R.string.more_privacy)) {
                ListItem(
                    leadingContent = {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_info_circle_outline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = listItemColors(),
                ) {
                    Text(
                        stringResource(R.string.more_privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            MoreSection(stringResource(R.string.more_theme)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                        ) {
                            Text(stringResource(mode.labelResId))
                        }
                    }
                }
            }

            MoreSection(stringResource(R.string.more_project)) {
                LinkRow(
                    title = stringResource(R.string.more_github),
                    body = GITHUB_URL,
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_brand_github_outline,
                    onClick = { openCustomTab(GITHUB_URL) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    title = stringResource(R.string.more_contact),
                    body = email,
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_mail_outline,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            uriHandler.openUri("mailto:$email")
                        }
                    },
                )
            }
        }
    }
}

/** When the server last confirmed the dataset against the source, falling back to its last change. */
@Composable
private fun dataSourceDescription(status: DatasetStatusDto, locale: Locale): String? {
    val updated = (status.lastCheckedAtUtc ?: status.lastChangedAtUtc)?.let { formatApiTimestamp(it, locale) }
    return updated?.let { stringResource(R.string.more_data_updated, it) }
}

@Composable
private fun MoreSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun LinkRow(
    title: String,
    body: String,
    icon: Int,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        supportingContent = { Text(body) },
        leadingContent = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_chevron_right_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        // Multi-line items align to the top by default; keep the chevron centred on the whole row.
        verticalAlignment = Alignment.CenterVertically,
        colors = listItemColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(title)
    }
}

@Composable
private fun listItemColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)
