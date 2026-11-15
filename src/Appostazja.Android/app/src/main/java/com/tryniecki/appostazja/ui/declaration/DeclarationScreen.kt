package com.tryniecki.appostazja.ui.declaration

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.tryniecki.appostazja.BuildConfig
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.pdf.ApostasyDeclaration
import com.tryniecki.appostazja.pdf.ApostasyPdfGenerator
import com.tryniecki.appostazja.pdf.DeclarationArchive
import java.io.ByteArrayOutputStream
import androidx.compose.material3.FloatingActionButton
import com.tryniecki.appostazja.security.SecureDraftStore
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.appostazja.ui.components.FabMorphRole
import com.tryniecki.appostazja.ui.components.fabMorph
import com.tryniecki.appostazja.ui.components.fabMorphShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeclarationRoute(
    draftStore: SecureDraftStore,
    pdfGenerator: ApostasyPdfGenerator,
    archive: DeclarationArchive,
    onOpenPreview: (declarationId: String) -> Unit,
    onOpenHistory: () -> Unit,
    parishHandoff: ParishHandoff,
) {
    val viewModel: DeclarationViewModel = viewModel(factory = DeclarationViewModel.factory(draftStore, parishHandoff))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appliedParish by viewModel.appliedParish.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // A church sent from the map: say which field it filled, with an undo in case it was the wrong one.
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
    fun generate(declaration: ApostasyDeclaration) {
        scope.launch {
            isGenerating = true
            val result = runCatching {
                val pdf = withContext(Dispatchers.IO) {
                    ByteArrayOutputStream().also { pdfGenerator.generate(declaration, it) }.toByteArray()
                }
                archive.save(pdf)
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

    DeclarationScreen(
        state = state,
        isGenerating = isGenerating,
        snackbarHostState = snackbarHostState,
        onFullNameChange = viewModel::updateFullName,
        onHomeAddressChange = viewModel::updateHomeAddress,
        onBaptismDateChange = viewModel::updateBaptismDate,
        onBaptismParishChange = viewModel::updateBaptismParish,
        onResidenceParishChange = viewModel::updateResidenceParish,
        onMotivationChange = viewModel::updateMotivation,
        onClear = viewModel::clear,
        // Test builds only: three taps on "Twoje dane" fill the form with sample data.
        onFillSample = if (BuildConfig.BUILD_TYPE != "release") viewModel::fillSample else null,
        onGenerate = { viewModel.buildDeclaration()?.let(::generate) },
        onOpenHistory = onOpenHistory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeclarationScreen(
    state: DeclarationUiState,
    isGenerating: Boolean,
    snackbarHostState: SnackbarHostState,
    onFullNameChange: (String) -> Unit,
    onHomeAddressChange: (String) -> Unit,
    onBaptismDateChange: (LocalDate) -> Unit,
    onBaptismParishChange: (String) -> Unit,
    onResidenceParishChange: (String) -> Unit,
    onMotivationChange: (String) -> Unit,
    onClear: () -> Unit,
    onFillSample: (() -> Unit)?,
    onGenerate: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.declaration_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    TextButton(onClick = onClear, enabled = state.isLoaded && !isGenerating) {
                        Text(stringResource(R.string.declaration_clear))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenHistory,
                shape = fabMorphShape(),
                modifier = Modifier.fabMorph(DECLARATION_HISTORY_MORPH_KEY, FabMorphRole.Fab),
            ) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_history_outline),
                    contentDescription = stringResource(R.string.declaration_history_title),
                )
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = onGenerate,
                    enabled = state.canGenerate && !isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (isGenerating) {
                        ExpressiveLoadingIndicator(Modifier.size(24.dp))
                    } else {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_file_type_pdf_outline),
                            contentDescription = null,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.declaration_generate))
                    }
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = state.isLoaded,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "declaration-load",
        ) { loaded ->
            if (!loaded) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ExpressiveLoadingIndicator(Modifier.size(40.dp))
                }
            } else {
                DeclarationForm(
                    state = state,
                    onFullNameChange = onFullNameChange,
                    onHomeAddressChange = onHomeAddressChange,
                    onBaptismDateChange = onBaptismDateChange,
                    onBaptismParishChange = onBaptismParishChange,
                    onResidenceParishChange = onResidenceParishChange,
                    onMotivationChange = onMotivationChange,
                    onFillSample = onFillSample,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeclarationForm(
    state: DeclarationUiState,
    onFullNameChange: (String) -> Unit,
    onHomeAddressChange: (String) -> Unit,
    onBaptismDateChange: (LocalDate) -> Unit,
    onBaptismParishChange: (String) -> Unit,
    onResidenceParishChange: (String) -> Unit,
    onMotivationChange: (String) -> Unit,
    onFillSample: (() -> Unit)?,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Keeps the focused field above the keyboard; the window does not resize edge-to-edge.
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        FormSection(
            title = stringResource(R.string.declaration_your_data),
            titleModifier = onFillSample?.let { Modifier.onTripleTap(it) } ?: Modifier,
        ) {
            OutlinedTextField(
                value = state.fullName,
                onValueChange = onFullNameChange,
                label = { Text(stringResource(R.string.declaration_full_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = state.homeAddress,
                onValueChange = onHomeAddressChange,
                label = { Text(stringResource(R.string.declaration_home_address)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
        }

        FormSection(title = stringResource(R.string.declaration_baptism_data)) {
            Box {
                OutlinedTextField(
                    value = state.baptismDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.declaration_baptism_date)) },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_calendar_outline),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                // A read-only field swallows taps; this layer opens the picker from anywhere on it.
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(role = Role.Button) { showDatePicker = true },
                )
            }
            OutlinedTextField(
                value = state.baptismParish,
                onValueChange = onBaptismParishChange,
                label = { Text(stringResource(R.string.declaration_baptism_parish)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
        }

        FormSection(title = stringResource(R.string.declaration_submission)) {
            OutlinedTextField(
                value = state.residenceParish,
                onValueChange = onResidenceParishChange,
                label = { Text(stringResource(R.string.declaration_residence_parish)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
        }

        FormSection(title = stringResource(R.string.declaration_motivation)) {
            OutlinedTextField(
                value = state.motivation,
                onValueChange = onMotivationChange,
                label = { Text(stringResource(R.string.declaration_motivation_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10,
                supportingText = { Text(stringResource(R.string.declaration_motivation_support)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_info_circle_outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.declaration_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showDatePicker) {
        BaptismDatePicker(
            initialDate = state.baptismDate,
            onDismiss = { showDatePicker = false },
            onSelected = {
                showDatePicker = false
                onBaptismDateChange(it)
            },
        )
    }
}

@Composable
private fun FormSection(title: String, titleModifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            modifier = titleModifier,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

/** Runs [action] after three quick taps, with a confirming haptic; no ripple, as it is a hidden test shortcut. */
@Composable
private fun Modifier.onTripleTap(action: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTapMillis by remember { mutableLongStateOf(0L) }
    return pointerInput(action) {
        detectTapGestures {
            val now = System.currentTimeMillis()
            taps = if (now - lastTapMillis < TRIPLE_TAP_WINDOW_MS) taps + 1 else 1
            lastTapMillis = now
            if (taps == 3) {
                taps = 0
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                action()
            }
        }
    }
}

private const val TRIPLE_TAP_WINDOW_MS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaptismDatePicker(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (!selected.isAfter(today)) onSelected(selected)
                    }
                },
                enabled = state.selectedDateMillis?.let { millis ->
                    !Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
                } == true,
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
