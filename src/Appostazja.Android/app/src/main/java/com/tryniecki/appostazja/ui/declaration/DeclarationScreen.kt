package com.tryniecki.appostazja.ui.declaration

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.tryniecki.appostazja.BuildConfig
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.pdf.ApostasyPdfGenerator
import com.tryniecki.appostazja.pdf.BaptismCertificateRequest
import com.tryniecki.appostazja.pdf.DeclarationArchive
import com.tryniecki.appostazja.pdf.DocumentKind
import com.tryniecki.appostazja.pdf.certificateRequestEmail
import com.tryniecki.appostazja.pdf.certificateRequestText
import com.tryniecki.appostazja.security.SecureDraftStore
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.appostazja.ui.components.FabMorphRole
import com.tryniecki.appostazja.ui.components.fabMorph
import com.tryniecki.appostazja.ui.components.fabMorphShape
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.composables.icons.tabler.outline.R as Tabler

@Composable
fun DeclarationRoute(
    draftStore: SecureDraftStore,
    pdfGenerator: ApostasyPdfGenerator,
    archive: DeclarationArchive,
    onOpenPreview: (declarationId: String) -> Unit,
    onOpenHistory: () -> Unit,
    parishHandoff: ParishHandoff,
    onPickParish: (ParishField) -> Unit,
) {
    val viewModel: DeclarationViewModel = viewModel(factory = DeclarationViewModel.factory(draftStore, parishHandoff))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appliedParish by viewModel.appliedParish.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // A church sent from the map or the search: say which field it filled, with an undo.
    appliedParish?.let { applied ->
        LaunchedEffect(applied) {
            val message = resources.getString(
                when (applied.pick.field) {
                    ParishField.BAPTISM -> R.string.declaration_parish_filled_baptism
                    ParishField.RESIDENCE -> R.string.declaration_parish_filled_residence
                },
                applied.pick.churchName,
            )
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = resources.getString(R.string.action_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoParish(applied)
            else viewModel.parishMessageShown(applied)
        }
    }
    var isGenerating by remember { mutableStateOf(false) }

    val errorMessage = stringResource(R.string.declaration_save_error)
    // Generated PDFs go to the encrypted archive first and open in the in-app preview, which saves or shares them.
    fun generate(kind: DocumentKind, write: (OutputStream) -> Unit) {
        scope.launch {
            isGenerating = true
            val result = runCatching {
                val pdf = withContext(Dispatchers.IO) { ByteArrayOutputStream().also(write).toByteArray() }
                archive.save(pdf, kind)
            }
            isGenerating = false
            result
                .onSuccess { onOpenPreview(it.id) }
                .onFailure { snackbarHostState.showSnackbar(errorMessage) }
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.flushDraft() }
    }

    // Back walks the wizard before it leaves the screen.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.isLoaded &&
            (state.flow == DocumentFlow.CERTIFICATE_REQUEST || state.step.ordinal > 0),
        onBackCompleted = { viewModel.back() },
    )

    DeclarationScreen(
        state = state,
        isGenerating = isGenerating,
        snackbarHostState = snackbarHostState,
        actions = viewModel,
        onPickParish = onPickParish,
        onBack = { viewModel.back() },
        onClear = viewModel::clear,
        // Test builds only: three taps on a step title fill the form with sample data.
        onFillSample = if (BuildConfig.BUILD_TYPE != "release") viewModel::fillSample else null,
        onGenerateDeclaration = {
            viewModel.buildDeclaration()?.let { declaration ->
                generate(DocumentKind.DECLARATION) { pdfGenerator.generate(declaration, it) }
            }
        },
        onGenerateRequest = {
            viewModel.buildCertificateRequest()?.let { request ->
                generate(DocumentKind.CERTIFICATE_REQUEST) { pdfGenerator.generate(request, it) }
            }
        },
        onEmailRequest = { viewModel.buildCertificateRequest()?.let { emailRequest(context, it) } },
        onOpenHistory = onOpenHistory,
    )
}

/** Opens a mail app with the request written out; the parish address is the user's to fill in. */
private fun emailRequest(context: Context, request: BaptismCertificateRequest) {
    val text = certificateRequestText(request)
    val body = certificateRequestEmail(text, request.fullName)
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        .putExtra(Intent.EXTRA_SUBJECT, text.subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No mail app: the text is still useful in a web mail or a contact form.
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(text.subject, body))
        Toast.makeText(context, R.string.request_email_copied, Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeclarationScreen(
    state: DeclarationUiState,
    isGenerating: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: DeclarationFormActions,
    onPickParish: (ParishField) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onFillSample: (() -> Unit)?,
    onGenerateDeclaration: () -> Unit,
    onGenerateRequest: () -> Unit,
    onEmailRequest: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val inRequest = state.flow == DocumentFlow.CERTIFICATE_REQUEST
    val page = if (inRequest) REQUEST_PAGE else state.step.name
    // Missing fields are marked only on the page where the user tried to go on, until they are filled in.
    var errorsOn by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequesters = remember { FormField.entries.associateWith { FocusRequester() } }
    // Focus goes to a missing field once its page is on screen; it may have to be opened first.
    var pendingFocus by remember { mutableStateOf<FormField?>(null) }
    LaunchedEffect(pendingFocus, page) {
        val field = pendingFocus ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { focusRequesters.getValue(field).requestFocus() }
        pendingFocus = null
    }
    fun showMissing(field: FormField, onPage: String) {
        errorsOn = onPage
        pendingFocus = field
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (inRequest) R.string.request_title_short else R.string.declaration_title))
                },
                subtitle = {
                    if (!inRequest) {
                        Text(stringResource(R.string.wizard_step_of, state.step.ordinal + 1, DeclarationStep.entries.size))
                    }
                },
                navigationIcon = {
                    if (inRequest) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(Tabler.drawable.tabler_ic_arrow_left_outline),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    TextButton(
                        onClick = {
                            errorsOn = null
                            onClear()
                        },
                        enabled = state.isLoaded && !isGenerating,
                    ) {
                        Text(stringResource(R.string.declaration_clear))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!inRequest) {
                FloatingActionButton(
                    onClick = onOpenHistory,
                    shape = fabMorphShape(),
                    modifier = Modifier.fabMorph(DECLARATION_HISTORY_MORPH_KEY, FabMorphRole.Fab),
                ) {
                    Icon(
                        painter = painterResource(Tabler.drawable.tabler_ic_history_outline),
                        contentDescription = stringResource(R.string.declaration_history_title),
                    )
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val enabled = state.isLoaded && !isGenerating
                    when {
                        inRequest -> {
                            fun attempt(action: () -> Unit) {
                                val missing = state.requestMissingFields().firstOrNull()
                                if (missing == null) action() else showMissing(missing, REQUEST_PAGE)
                            }
                            OutlinedButton(onClick = { attempt(onEmailRequest) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.request_send_email))
                            }
                            GenerateButton(isGenerating, enabled, Modifier.weight(1f)) { attempt(onGenerateRequest) }
                        }
                        else -> {
                            if (state.step.ordinal > 0) {
                                OutlinedButton(onClick = onBack, enabled = enabled, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.action_back))
                                }
                            }
                            val last = state.step == DeclarationStep.entries.last()
                            if (last) {
                                // Stays enabled with fields missing: a tap shows what is left instead of doing nothing.
                                GenerateButton(isGenerating, enabled, Modifier.weight(1f)) {
                                    val missing = state.missingFields().firstOrNull()
                                    if (missing == null) {
                                        onGenerateDeclaration()
                                    } else {
                                        actions.goTo(missing.step)
                                        showMissing(missing, missing.step.name)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val missing = state.missingFields().firstOrNull { it.step == state.step }
                                        if (missing == null) {
                                            errorsOn = null
                                            actions.goTo(DeclarationStep.entries[state.step.ordinal + 1])
                                        } else {
                                            showMissing(missing, state.step.name)
                                        }
                                    },
                                    enabled = enabled,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.action_next))
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            if (!inRequest && state.isLoaded) {
                LinearProgressIndicator(
                    progress = { (state.step.ordinal + 1f) / DeclarationStep.entries.size },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            AnimatedContent(
                targetState = if (state.isLoaded) page else null,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val from = initialState?.let(::pageOrder)
                    val to = targetState?.let(::pageOrder)
                    if (from == null || to == null) {
                        fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                    } else {
                        // Forward slides in from the end, back from the start, like the screens of a tab.
                        val direction = if (to > from) 1 else -1
                        (slideInHorizontally(motion.defaultSpatialSpec()) { direction * it / 5 } +
                            fadeIn(motion.defaultEffectsSpec())) togetherWith
                            (slideOutHorizontally(motion.defaultSpatialSpec()) { -direction * it / 5 } +
                                fadeOut(motion.fastEffectsSpec()))
                    }
                },
                label = "declaration-page",
            ) { shown ->
                if (shown == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ExpressiveLoadingIndicator(Modifier.size(40.dp))
                    }
                } else {
                    val missing = when {
                        errorsOn != shown -> emptySet()
                        shown == REQUEST_PAGE -> state.requestMissingFields().toSet()
                        else -> state.missingFields().toSet()
                    }
                    val stepContext = StepContext(actions, missing, focusRequesters, onPickParish, onFillSample)
                    if (shown == REQUEST_PAGE) {
                        CertificateRequestContent(state, stepContext)
                    } else {
                        DeclarationStepContent(state, DeclarationStep.valueOf(shown), stepContext)
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerateButton(isGenerating: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        if (isGenerating) {
            ExpressiveLoadingIndicator(Modifier.size(24.dp))
        } else {
            Icon(painterResource(Tabler.drawable.tabler_ic_file_type_pdf_outline), contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.declaration_generate))
        }
    }
}

private const val REQUEST_PAGE = "REQUEST"

/** The order pages slide in; the certificate request comes after the step it was opened from. */
private fun pageOrder(page: String): Int =
    if (page == REQUEST_PAGE) DeclarationStep.entries.size else DeclarationStep.valueOf(page).ordinal
