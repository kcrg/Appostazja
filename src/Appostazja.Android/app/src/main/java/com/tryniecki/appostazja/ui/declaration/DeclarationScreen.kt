package com.tryniecki.appostazja.ui.declaration

import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.AnimatedVisibility
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
    onPickParish: (ParishField) -> Unit,
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

    val actions = remember(viewModel) {
        DeclarationFormActions(
            onFullNameChange = viewModel::updateFullName,
            onHomeAddressChange = viewModel::updateHomeAddress,
            onBaptismDateChange = viewModel::updateBaptismDate,
            onBaptismDateUnknownChange = viewModel::updateBaptismDateUnknown,
            onBaptismYearChange = viewModel::updateBaptismYear,
            onBaptismParishChange = viewModel::updateBaptismParish,
            onResidenceParishChange = viewModel::updateResidenceParish,
            onResidenceSameAsBaptismChange = viewModel::updateResidenceSameAsBaptism,
            onMotivationPreset = viewModel::applyMotivationPreset,
            onMotivationChange = viewModel::updateMotivation,
            onPickParish = onPickParish,
        )
    }
    DeclarationScreen(
        state = state,
        isGenerating = isGenerating,
        snackbarHostState = snackbarHostState,
        actions = actions,
        onClear = viewModel::clear,
        // Test builds only: three taps on "Twoje dane" fill the form with sample data.
        onFillSample = if (BuildConfig.BUILD_TYPE != "release") viewModel::fillSample else null,
        onGenerate = { viewModel.buildDeclaration()?.let(::generate) },
        onOpenHistory = onOpenHistory,
    )
}

/** Everything the fields of the form report back. */
internal class DeclarationFormActions(
    val onFullNameChange: (String) -> Unit,
    val onHomeAddressChange: (String) -> Unit,
    val onBaptismDateChange: (LocalDate) -> Unit,
    val onBaptismDateUnknownChange: (Boolean) -> Unit,
    val onBaptismYearChange: (String) -> Unit,
    val onBaptismParishChange: (String) -> Unit,
    val onResidenceParishChange: (String) -> Unit,
    val onResidenceSameAsBaptismChange: (Boolean) -> Unit,
    val onMotivationPreset: (String, Collection<String>) -> Unit,
    val onMotivationChange: (String) -> Unit,
    /** Opens the church search; the church picked there fills this field. */
    val onPickParish: (ParishField) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeclarationScreen(
    state: DeclarationUiState,
    isGenerating: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: DeclarationFormActions,
    onClear: () -> Unit,
    onFillSample: (() -> Unit)?,
    onGenerate: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // After a tap on "Generuj PDF" with something missing, the missing fields are marked until filled in.
    var showMissing by rememberSaveable { mutableStateOf(false) }
    val focusRequesters = remember { FormField.entries.associateWith { FocusRequester() } }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.declaration_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    TextButton(
                        onClick = {
                            showMissing = false
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
                // Stays enabled with fields missing: a tap shows what is left instead of doing nothing.
                Button(
                    onClick = {
                        val missing = state.missingFields().firstOrNull()
                        if (missing == null) {
                            onGenerate()
                        } else {
                            showMissing = true
                            // Focusing a field scrolls it into view (and opens the keyboard for text).
                            runCatching { focusRequesters.getValue(missing).requestFocus() }
                        }
                    },
                    enabled = state.isLoaded && !isGenerating,
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
                    actions = actions,
                    missing = if (showMissing) state.missingFields().toSet() else emptySet(),
                    focusRequesters = focusRequesters,
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
    actions: DeclarationFormActions,
    missing: Set<FormField>,
    focusRequesters: Map<FormField, FocusRequester>,
    onFillSample: (() -> Unit)?,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val required = stringResource(R.string.declaration_field_required)
    fun Modifier.focusFor(field: FormField) = focusRequester(focusRequesters.getValue(field))
    // A missing field says why under it.
    fun errorText(field: FormField, message: String = required): (@Composable () -> Unit)? =
        if (field in missing) ({ Text(message) }) else null

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
                onValueChange = actions.onFullNameChange,
                label = { Text(stringResource(R.string.declaration_full_name)) },
                modifier = Modifier.fillMaxWidth().focusFor(FormField.FULL_NAME),
                singleLine = true,
                isError = FormField.FULL_NAME in missing,
                supportingText = errorText(FormField.FULL_NAME),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = state.homeAddress,
                onValueChange = actions.onHomeAddressChange,
                label = { Text(stringResource(R.string.declaration_home_address)) },
                modifier = Modifier.fillMaxWidth().focusFor(FormField.HOME_ADDRESS),
                isError = FormField.HOME_ADDRESS in missing,
                supportingText = errorText(FormField.HOME_ADDRESS),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
        }

        FormSection(title = stringResource(R.string.declaration_baptism_data)) {
            if (state.baptismDateUnknown) {
                val invalidYear = stringResource(R.string.declaration_baptism_year_invalid)
                val yearSupport = stringResource(R.string.declaration_baptism_year_support)
                OutlinedTextField(
                    value = state.baptismYear,
                    onValueChange = actions.onBaptismYearChange,
                    label = { Text(stringResource(R.string.declaration_baptism_year)) },
                    modifier = Modifier.fillMaxWidth().focusFor(FormField.BAPTISM_DATE),
                    singleLine = true,
                    isError = FormField.BAPTISM_DATE in missing,
                    supportingText = errorText(FormField.BAPTISM_DATE, invalidYear) ?: { Text(yearSupport) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )
            } else {
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
                        modifier = Modifier.fillMaxWidth().focusFor(FormField.BAPTISM_DATE),
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
            }
            CheckboxRow(
                checked = state.baptismDateUnknown,
                onCheckedChange = actions.onBaptismDateUnknownChange,
                title = stringResource(R.string.declaration_baptism_date_unknown),
                support = stringResource(R.string.declaration_baptism_date_unknown_support),
            )
            ParishTextField(
                value = state.baptismParish,
                onValueChange = actions.onBaptismParishChange,
                label = stringResource(R.string.declaration_baptism_parish),
                onPick = { actions.onPickParish(ParishField.BAPTISM) },
                modifier = Modifier.focusFor(FormField.BAPTISM_PARISH),
                isError = FormField.BAPTISM_PARISH in missing,
                supportingText = errorText(FormField.BAPTISM_PARISH),
            )
        }

        FormSection(title = stringResource(R.string.declaration_submission)) {
            CheckboxRow(
                checked = state.residenceSameAsBaptism,
                onCheckedChange = actions.onResidenceSameAsBaptismChange,
                title = stringResource(R.string.declaration_same_parish),
                support = stringResource(R.string.declaration_same_parish_support),
            )
            AnimatedVisibility(visible = !state.residenceSameAsBaptism) {
                ParishTextField(
                    value = state.residenceParish,
                    onValueChange = actions.onResidenceParishChange,
                    label = stringResource(R.string.declaration_residence_parish),
                    onPick = { actions.onPickParish(ParishField.RESIDENCE) },
                    modifier = Modifier.focusFor(FormField.RESIDENCE_PARISH),
                    isError = FormField.RESIDENCE_PARISH in missing,
                    supportingText = errorText(FormField.RESIDENCE_PARISH),
                )
            }
        }

        FormSection(title = stringResource(R.string.declaration_motivation)) {
            val presets = MotivationPreset.entries.map { stringResource(it.label) to stringResource(it.text) }
            val presetTexts = presets.map { it.second }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, text) ->
                    FilterChip(
                        selected = state.motivation.trim() == text,
                        onClick = { actions.onMotivationPreset(text, presetTexts) },
                        label = { Text(label) },
                    )
                }
            }
            val motivationSupport = stringResource(R.string.declaration_motivation_support)
            OutlinedTextField(
                value = state.motivation,
                onValueChange = actions.onMotivationChange,
                label = { Text(stringResource(R.string.declaration_motivation_hint)) },
                modifier = Modifier.fillMaxWidth().focusFor(FormField.MOTIVATION),
                minLines = 5,
                maxLines = 10,
                isError = FormField.MOTIVATION in missing,
                supportingText = errorText(FormField.MOTIVATION) ?: { Text(motivationSupport) },
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
                actions.onBaptismDateChange(it)
            },
        )
    }
}


/** A checkbox with a title and a line of explanation; the whole row toggles it. */
@Composable
private fun CheckboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, support: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the click, so the box itself is only an indicator.
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(support, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A parish field with a button that finds the church in the search instead of typing it. */
@Composable
private fun ParishTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onPick: () -> Unit,
    modifier: Modifier,
    isError: Boolean,
    supportingText: (@Composable () -> Unit)?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        isError = isError,
        supportingText = supportingText,
        trailingIcon = {
            IconButton(onClick = onPick) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_search_outline),
                    contentDescription = stringResource(R.string.declaration_pick_parish),
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next,
        ),
    )
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
