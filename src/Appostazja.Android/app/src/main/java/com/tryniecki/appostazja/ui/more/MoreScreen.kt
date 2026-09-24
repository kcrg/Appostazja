package com.tryniecki.appostazja.ui.more

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.browser.rememberOpenCustomTab
import com.tryniecki.appostazja.ui.theme.ThemeMode

private const val DATA_SOURCE_URL = "https://mapaapostazji.pl/"
private const val GITHUB_URL = "https://github.com/kcrg/Appostazja"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val openCustomTab = rememberOpenCustomTab()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val email = stringResource(R.string.more_contact_email)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoreSection(stringResource(R.string.more_data_source)) {
                LinkRow(
                    title = stringResource(R.string.more_data_source_title),
                    body = stringResource(R.string.more_data_source_body),
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_database_outline,
                    onClick = { openCustomTab(DATA_SOURCE_URL) },
                )
            }

            MoreSection(stringResource(R.string.more_privacy)) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.more_privacy_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_info_circle_outline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = listItemColors(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(stringResource(R.string.more_theme))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
        headlineContent = { Text(title) },
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
        colors = listItemColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun listItemColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)
