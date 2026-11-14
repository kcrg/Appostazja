package com.tryniecki.appostazja.ui.declaration

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.rememberPdfViewerState
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.pdf.ArchivedDeclaration
import com.tryniecki.appostazja.pdf.DeclarationArchive
import com.tryniecki.appostazja.ui.components.EmptyState
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.appostazja.ui.components.FabMorphRole
import com.tryniecki.appostazja.ui.components.fabMorph
import com.tryniecki.appostazja.ui.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import com.composables.icons.tabler.outline.R as Tabler

/** Generated declarations, newest first; each opens in the preview or goes straight to the share sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeclarationHistoryRoute(
    archive: DeclarationArchive,
    onOpenPreview: (declarationId: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalLocale.current.platformLocale
    var version by remember { mutableIntStateOf(0) }
    val declarations by produceState<List<ArchivedDeclaration>?>(initialValue = null, version) {
        value = archive.list()
    }
    var pendingDelete by remember { mutableStateOf<ArchivedDeclaration?>(null) }
    val fileName = stringResource(R.string.declaration_file_name)
    val shareTitle = stringResource(R.string.declaration_share)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fabMorph(DECLARATION_HISTORY_MORPH_KEY, FabMorphRole.Screen)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.declaration_history_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val items = declarations
        when {
            items == null -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                ExpressiveLoadingIndicator(Modifier.size(40.dp))
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Tabler.drawable.tabler_ic_file_type_pdf_outline,
                    title = stringResource(R.string.declaration_history_empty_title),
                    body = stringResource(R.string.declaration_history_empty_body),
                )
            }
            else -> LazyColumn(contentPadding = innerPadding) {
                items(items, key = { it.id }) { declaration ->
                    ListItem(
                        onClick = { onOpenPreview(declaration.id) },
                        leadingContent = {
                            Icon(
                                painterResource(Tabler.drawable.tabler_ic_file_type_pdf_outline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        supportingContent = { Text(stringResource(R.string.declaration_history_tap_to_preview)) },
                        trailingContent = {
                            androidx.compose.foundation.layout.Row {
                                IconButton(onClick = {
                                    scope.launch {
                                        shareDeclaration(context, archive.plainCopy(declaration.id, fileName), shareTitle)
                                    }
                                }) {
                                    Icon(painterResource(Tabler.drawable.tabler_ic_share_outline), contentDescription = shareTitle)
                                }
                                IconButton(onClick = { pendingDelete = declaration }) {
                                    Icon(
                                        painterResource(Tabler.drawable.tabler_ic_trash_outline),
                                        contentDescription = stringResource(R.string.declaration_delete),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.animateItem(),
                    ) {
                        Text(formatGeneratedAt(declaration.createdAt, locale))
                    }
                }
            }
        }
    }

    pendingDelete?.let { declaration ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.declaration_delete_title)) },
            text = { Text(stringResource(R.string.declaration_delete_body, formatGeneratedAt(declaration.createdAt, locale))) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        archive.delete(declaration.id)
                        version++
                    }
                }) { Text(stringResource(R.string.declaration_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** One generated declaration in the androidx.pdf viewer, with saving a copy and sharing. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPdfApi::class)
@Composable
fun DeclarationPreviewRoute(
    archive: DeclarationArchive,
    declarationId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalLocale.current.platformLocale
    val snackbarHostState = remember { SnackbarHostState() }
    val fileName = stringResource(R.string.declaration_file_name)
    val shareTitle = stringResource(R.string.declaration_share)
    val savedMessage = stringResource(R.string.declaration_saved)
    val errorMessage = stringResource(R.string.declaration_save_error)

    val declaration by produceState<ArchivedDeclaration?>(initialValue = null, declarationId) {
        value = archive.find(declarationId)
    }
    var failed by remember { mutableStateOf(false) }
    val document by produceState<PdfDocument?>(initialValue = null, declarationId) {
        val opened = runCatching {
            val copy = archive.plainCopy(declarationId, fileName)
            SandboxedPdfLoader(context, Dispatchers.IO).openDocument(Uri.fromFile(copy))
        }.onFailure { failed = true }.getOrNull()
        value = opened
        awaitDispose { opened?.close() }
    }

    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { archive.export(declarationId, it) }
                        ?: error("Cannot open PDF destination.")
                }
            }
            snackbarHostState.showSnackbar(if (result.isSuccess) savedMessage else errorMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.declaration_title)) },
                subtitle = {
                    declaration?.let { Text(stringResource(R.string.declaration_generated_at, formatGeneratedAt(it.createdAt, locale))) }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { saveCopy.launch(fileName) }) {
                        Icon(
                            painterResource(Tabler.drawable.tabler_ic_download_outline),
                            contentDescription = stringResource(R.string.declaration_save_copy),
                        )
                    }
                    IconButton(onClick = {
                        scope.launch { shareDeclaration(context, archive.plainCopy(declarationId, fileName), shareTitle) }
                    }) {
                        Icon(painterResource(Tabler.drawable.tabler_ic_share_outline), contentDescription = shareTitle)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            val opened = document
            when {
                opened != null -> PdfViewer(
                    pdfDocument = opened,
                    state = rememberPdfViewerState(),
                    modifier = Modifier.fillMaxSize(),
                )
                failed -> EmptyState(
                    icon = Tabler.drawable.tabler_ic_file_type_pdf_outline,
                    title = stringResource(R.string.declaration_preview_error),
                    body = stringResource(R.string.declaration_preview_error_body),
                )
                else -> ExpressiveLoadingIndicator(Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            painterResource(Tabler.drawable.tabler_ic_arrow_left_outline),
            contentDescription = stringResource(R.string.action_back),
        )
    }
}

private fun formatGeneratedAt(instant: Instant, locale: Locale) = formatDateTime(instant, locale)

/** Hands the decrypted copy to another app through the FileProvider, with read access to that one file. */
private fun shareDeclaration(context: Context, file: java.io.File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.declarations", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    try {
        context.startActivity(Intent.createChooser(send, title))
    } catch (_: ActivityNotFoundException) {
        // Nothing can receive a PDF; there is nowhere else to send it.
    }
}

/** Shared between the form's FAB and the history screen it morphs into. */
internal const val DECLARATION_HISTORY_MORPH_KEY = "declaration-history"
