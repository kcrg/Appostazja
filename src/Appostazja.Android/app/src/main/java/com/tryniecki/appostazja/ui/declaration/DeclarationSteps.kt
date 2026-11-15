package com.tryniecki.appostazja.ui.declaration

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.pdf.CertificateDelivery
import com.tryniecki.appostazja.pdf.DECLARATION_DATE
import com.tryniecki.appostazja.pdf.baptismDateLine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.composables.icons.tabler.outline.R as Tabler

/** What the fields of the wizard report back; the ViewModel implements it. */
internal interface DeclarationFormActions {
    fun answerLivesInBaptismParish(value: Boolean)
    fun answerHasBaptismCertificate(value: Boolean)
    fun updateFullName(value: String)
    fun updateHomeAddress(value: String)
    fun updateBaptismDate(value: LocalDate)
    fun updateBaptismDateUnknown(value: Boolean)
    fun updateBaptismYear(value: String)
    fun updateBaptismParish(value: String)
    fun updateResidenceParish(value: String)
    fun updateMotivation(value: String)
    /** [preset] is the chosen ready-made reason, [presets] all of them, as the screen shows them. */
    fun applyMotivationPreset(preset: String, presets: Collection<String>)
    fun updateRequestAnnotatedCertificate(value: Boolean)
    fun updateBirthDate(value: LocalDate?)
    fun updateBirthPlace(value: String)
    fun updateFatherName(value: String)
    fun updateMotherName(value: String)
    fun updateCertificateDelivery(value: CertificateDelivery)
    fun updateContact(value: String)
    fun goTo(step: DeclarationStep)
    fun openCertificateRequest()
}

/** What every step gets besides the state: callbacks, which fields to mark and where focus goes. */
internal class StepContext(
    val actions: DeclarationFormActions,
    val missing: Set<FormField>,
    val focusRequesters: Map<FormField, FocusRequester>,
    /** Opens the church search; the church picked there fills this field. */
    val onPickParish: (ParishField) -> Unit,
    /** Test builds only: three taps on a step title fill the form with sample data. */
    val onFillSample: (() -> Unit)?,
) {
    fun Modifier.focusFor(field: FormField) = focusRequester(focusRequesters.getValue(field))
}

@Composable
internal fun DeclarationStepContent(state: DeclarationUiState, step: DeclarationStep, context: StepContext) {
    StepColumn {
        when (step) {
            DeclarationStep.SITUATION -> SituationStep(state, context)
            DeclarationStep.PERSONAL -> PersonalStep(state, context)
            DeclarationStep.BAPTISM -> BaptismStep(state, context)
            DeclarationStep.MOTIVATION -> MotivationStep(state, context)
        }
    }
}

@Composable
private fun SituationStep(state: DeclarationUiState, context: StepContext) {
    StepHeader(
        title = stringResource(R.string.wizard_situation_title),
        body = stringResource(R.string.wizard_situation_body),
        onFillSample = context.onFillSample,
    )
    if (FormField.SITUATION in context.missing) {
        Text(
            stringResource(R.string.wizard_answer_required),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Question(
        title = stringResource(R.string.wizard_lives_in_baptism_parish),
        support = stringResource(R.string.wizard_lives_in_baptism_parish_support),
    ) {
        ChoiceRow(
            selected = state.livesInBaptismParish == true,
            onClick = { context.actions.answerLivesInBaptismParish(true) },
            title = stringResource(R.string.wizard_lives_yes),
        )
        ChoiceRow(
            selected = state.livesInBaptismParish == false,
            onClick = { context.actions.answerLivesInBaptismParish(false) },
            title = stringResource(R.string.wizard_lives_no),
        )
    }
    AnimatedVisibility(visible = state.livesInBaptismParish == false) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Question(
                title = stringResource(R.string.wizard_has_certificate),
                support = stringResource(R.string.wizard_has_certificate_support),
            ) {
                ChoiceRow(
                    selected = state.hasBaptismCertificate == true,
                    onClick = { context.actions.answerHasBaptismCertificate(true) },
                    title = stringResource(R.string.wizard_has_certificate_yes),
                )
                ChoiceRow(
                    selected = state.hasBaptismCertificate == false,
                    onClick = { context.actions.answerHasBaptismCertificate(false) },
                    title = stringResource(R.string.wizard_has_certificate_no),
                )
            }
            AnimatedVisibility(visible = state.hasBaptismCertificate == false) {
                CertificateRequestPrompt(onOpen = context.actions::openCertificateRequest)
            }
        }
    }
}

@Composable
private fun PersonalStep(state: DeclarationUiState, context: StepContext) = with(context) {
    StepHeader(title = stringResource(R.string.declaration_your_data), onFillSample = onFillSample)
    FullNameField(state, context)
    HomeAddressField(state, context)
}

@Composable
private fun BaptismStep(state: DeclarationUiState, context: StepContext) = with(context) {
    StepHeader(title = stringResource(R.string.declaration_baptism_data), onFillSample = onFillSample)
    BaptismDateFields(state, context)
    ParishTextField(
        value = state.baptismParish,
        onValueChange = actions::updateBaptismParish,
        label = stringResource(R.string.declaration_baptism_parish),
        onPick = { onPickParish(ParishField.BAPTISM) },
        modifier = Modifier.focusFor(FormField.BAPTISM_PARISH),
        error = errorFor(FormField.BAPTISM_PARISH),
    )
    if (state.livesInBaptismParish != true) {
        ParishTextField(
            value = state.residenceParish,
            onValueChange = actions::updateResidenceParish,
            label = stringResource(R.string.declaration_residence_parish),
            onPick = { onPickParish(ParishField.RESIDENCE) },
            modifier = Modifier.focusFor(FormField.RESIDENCE_PARISH),
            error = errorFor(FormField.RESIDENCE_PARISH),
            support = stringResource(R.string.declaration_residence_parish_support),
        )
    }
}

@Composable
private fun MotivationStep(state: DeclarationUiState, context: StepContext) = with(context) {
    StepHeader(title = stringResource(R.string.declaration_motivation), onFillSample = onFillSample)
    val presets = MotivationPreset.entries.map { stringResource(it.label) to stringResource(it.text) }
    val presetTexts = presets.map { it.second }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (label, text) ->
            FilterChip(
                selected = state.motivation.trim() == text,
                onClick = { actions.applyMotivationPreset(text, presetTexts) },
                label = { Text(label) },
            )
        }
    }
    val motivationSupport = stringResource(R.string.declaration_motivation_support)
    OutlinedTextField(
        value = state.motivation,
        onValueChange = actions::updateMotivation,
        label = { Text(stringResource(R.string.declaration_motivation_hint)) },
        modifier = Modifier.fillMaxWidth().focusFor(FormField.MOTIVATION),
        minLines = 5,
        maxLines = 10,
        isError = FormField.MOTIVATION in missing,
        supportingText = { Text(errorFor(FormField.MOTIVATION) ?: motivationSupport) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    CheckboxRow(
        checked = state.requestAnnotatedCertificate,
        onCheckedChange = actions::updateRequestAnnotatedCertificate,
        title = stringResource(R.string.wizard_annotated_certificate),
        support = stringResource(R.string.wizard_annotated_certificate_support),
    )
    Summary(state, context)
    SubmissionChecklist(state, onOpenRequest = actions::openCertificateRequest)
}

/** What goes into the declaration, with a way back to each part. */
@Composable
private fun Summary(state: DeclarationUiState, context: StepContext) {
    val notGiven = stringResource(R.string.wizard_summary_missing)
    fun orMissing(value: String) = value.trim().ifEmpty { notGiven }
    val baptismDate = state.baptismDateOrNull()?.let(::baptismDateLine) ?: notGiven
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.wizard_summary_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SummaryRow(
                title = stringResource(R.string.declaration_your_data),
                lines = listOf(orMissing(state.fullName), orMissing(state.homeAddress)),
                onEdit = { context.actions.goTo(DeclarationStep.PERSONAL) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SummaryRow(
                title = stringResource(R.string.declaration_baptism_data),
                lines = listOf(baptismDate, orMissing(state.baptismParish)),
                onEdit = { context.actions.goTo(DeclarationStep.BAPTISM) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SummaryRow(
                title = stringResource(R.string.wizard_summary_submitted_to),
                lines = listOf(orMissing(state.effectiveResidenceParish)),
                onEdit = {
                    context.actions.goTo(
                        if (state.livesInBaptismParish == true) DeclarationStep.SITUATION else DeclarationStep.BAPTISM,
                    )
                },
            )
        }
    }
}

@Composable
private fun SummaryRow(title: String, lines: List<String>, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.wizard_summary_edit)) }
    }
}

/** How the declaration is submitted, after points 1–3 of the decree; the certificate only when it is needed. */
@Composable
private fun SubmissionChecklist(state: DeclarationUiState, onOpenRequest: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.wizard_checklist_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ChecklistItem(Tabler.drawable.tabler_ic_signature_outline, stringResource(R.string.wizard_checklist_sign))
            ChecklistItem(Tabler.drawable.tabler_ic_building_church_outline, stringResource(R.string.wizard_checklist_in_person))
            ChecklistItem(Tabler.drawable.tabler_ic_id_outline, stringResource(R.string.wizard_checklist_id))
            if (state.needsBaptismCertificate) {
                ChecklistItem(Tabler.drawable.tabler_ic_paperclip_outline, stringResource(R.string.wizard_checklist_certificate))
                if (state.hasBaptismCertificate != true) {
                    FilledTonalButton(onClick = onOpenRequest, modifier = Modifier.padding(start = 36.dp)) {
                        Text(stringResource(R.string.wizard_request_open))
                    }
                }
            }
            ChecklistItem(Tabler.drawable.tabler_ic_users_outline, stringResource(R.string.wizard_checklist_no_witnesses))
            ChecklistItem(Tabler.drawable.tabler_ic_messages_outline, stringResource(R.string.wizard_checklist_conversation))
        }
    }
}

@Composable
private fun ChecklistItem(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun CertificateRequestPrompt(onOpen: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.wizard_request_prompt_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(R.string.wizard_request_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.wizard_request_open)) }
        }
    }
}

/** The certificate request: one page, sharing the person and baptism details with the declaration. */
@Composable
internal fun CertificateRequestContent(state: DeclarationUiState, context: StepContext) = with(context) {
    StepColumn {
        StepHeader(
            title = stringResource(R.string.request_title),
            body = stringResource(R.string.request_body),
            onFillSample = onFillSample,
        )
        ParishTextField(
            value = state.baptismParish,
            onValueChange = actions::updateBaptismParish,
            label = stringResource(R.string.declaration_baptism_parish),
            onPick = { onPickParish(ParishField.BAPTISM) },
            modifier = Modifier.focusFor(FormField.BAPTISM_PARISH),
            error = errorFor(FormField.BAPTISM_PARISH),
        )
        FullNameField(state, context)
        HomeAddressField(state, context)
        BaptismDateFields(state, context)

        SectionTitle(stringResource(R.string.request_record_details))
        Text(
            stringResource(R.string.request_record_details_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var pickBirthDate by remember { mutableStateOf(false) }
        DateField(
            text = state.birthDate?.format(DECLARATION_DATE).orEmpty(),
            label = stringResource(R.string.request_birth_date),
            onClick = { pickBirthDate = true },
            onClear = if (state.birthDate != null) ({ actions.updateBirthDate(null) }) else null,
        )
        if (pickBirthDate) {
            PickDateDialog(
                initialDate = state.birthDate ?: DEFAULT_BAPTISM_DATE,
                onDismiss = { pickBirthDate = false },
                onSelected = {
                    pickBirthDate = false
                    actions.updateBirthDate(it)
                },
            )
        }
        SimpleTextField(state.birthPlace, actions::updateBirthPlace, stringResource(R.string.request_birth_place))
        SimpleTextField(state.fatherName, actions::updateFatherName, stringResource(R.string.request_father_name))
        SimpleTextField(state.motherName, actions::updateMotherName, stringResource(R.string.request_mother_name))

        SectionTitle(stringResource(R.string.request_delivery))
        Column(Modifier.selectableGroup()) {
            ChoiceRow(
                selected = state.certificateDelivery == CertificateDelivery.POST,
                onClick = { actions.updateCertificateDelivery(CertificateDelivery.POST) },
                title = stringResource(R.string.request_delivery_post),
            )
            ChoiceRow(
                selected = state.certificateDelivery == CertificateDelivery.IN_PERSON,
                onClick = { actions.updateCertificateDelivery(CertificateDelivery.IN_PERSON) },
                title = stringResource(R.string.request_delivery_in_person),
            )
        }
        OutlinedTextField(
            value = state.contact,
            onValueChange = actions::updateContact,
            label = { Text(stringResource(R.string.request_contact)) },
            supportingText = { Text(stringResource(R.string.request_contact_support)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        )
        Text(
            stringResource(R.string.request_in_person_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FullNameField(state: DeclarationUiState, context: StepContext) = with(context) {
    OutlinedTextField(
        value = state.fullName,
        onValueChange = actions::updateFullName,
        label = { Text(stringResource(R.string.declaration_full_name)) },
        modifier = Modifier.fillMaxWidth().focusFor(FormField.FULL_NAME),
        singleLine = true,
        isError = FormField.FULL_NAME in missing,
        supportingText = errorFor(FormField.FULL_NAME)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
    )
}

@Composable
private fun HomeAddressField(state: DeclarationUiState, context: StepContext) = with(context) {
    val support = stringResource(R.string.declaration_home_address_support)
    OutlinedTextField(
        value = state.homeAddress,
        onValueChange = actions::updateHomeAddress,
        label = { Text(stringResource(R.string.declaration_home_address)) },
        modifier = Modifier.fillMaxWidth().focusFor(FormField.HOME_ADDRESS),
        isError = FormField.HOME_ADDRESS in missing,
        supportingText = { Text(errorFor(FormField.HOME_ADDRESS) ?: support) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
    )
}

/** The date picker field, or the optional year when the exact date is not known. */
@Composable
private fun BaptismDateFields(state: DeclarationUiState, context: StepContext) = with(context) {
    var pickDate by remember { mutableStateOf(false) }
    if (state.baptismDateUnknown) {
        val yearSupport = stringResource(R.string.declaration_baptism_year_support)
        val invalidYear = stringResource(R.string.declaration_baptism_year_invalid)
        OutlinedTextField(
            value = state.baptismYear,
            onValueChange = actions::updateBaptismYear,
            label = { Text(stringResource(R.string.declaration_baptism_year)) },
            modifier = Modifier.fillMaxWidth().focusFor(FormField.BAPTISM_DATE),
            singleLine = true,
            isError = FormField.BAPTISM_DATE in missing,
            supportingText = { Text(if (FormField.BAPTISM_DATE in missing) invalidYear else yearSupport) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
    } else {
        DateField(
            text = state.baptismDate.format(DECLARATION_DATE),
            label = stringResource(R.string.declaration_baptism_date),
            onClick = { pickDate = true },
            modifier = Modifier.focusFor(FormField.BAPTISM_DATE),
        )
    }
    CheckboxRow(
        checked = state.baptismDateUnknown,
        onCheckedChange = actions::updateBaptismDateUnknown,
        title = stringResource(R.string.declaration_baptism_date_unknown),
        support = stringResource(R.string.declaration_baptism_date_unknown_support),
    )
    if (pickDate) {
        PickDateDialog(
            initialDate = state.baptismDate,
            onDismiss = { pickDate = false },
            onSelected = {
                pickDate = false
                actions.updateBaptismDate(it)
            },
        )
    }
}

/** The message under a missing field, or null when it is fine. */
@Composable
private fun StepContext.errorFor(field: FormField): String? =
    if (field in missing) stringResource(R.string.declaration_field_required) else null

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Keeps the focused field above the keyboard; the window does not resize edge-to-edge.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepHeader(title: String, body: String? = null, onFillSample: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            modifier = onFillSample?.let { Modifier.onTripleTap(it) } ?: Modifier,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        body?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Question(title: String, support: String, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(support, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.selectableGroup()) { options() }
    }
}

/** One answer of a single-choice question; the whole row selects it. */
@Composable
private fun ChoiceRow(selected: Boolean, onClick: () -> Unit, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
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
    error: String?,
    support: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = (error ?: support)?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = onPick) {
                Icon(
                    painter = painterResource(Tabler.drawable.tabler_ic_search_outline),
                    contentDescription = stringResource(R.string.declaration_pick_parish),
                )
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
    )
}

@Composable
private fun SimpleTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
    )
}

/** A read-only field that opens a date picker; [onClear] empties an optional date. */
@Composable
private fun DateField(
    text: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    Box(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(painterResource(Tabler.drawable.tabler_ic_calendar_outline), contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        // A read-only field swallows taps; this layer opens the picker from anywhere on it.
        Box(
            Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(role = Role.Button, onClick = onClick),
        )
        if (onClear != null) {
            // Above the tap layer, in place of the calendar icon.
            IconButton(onClick = onClear, modifier = Modifier.align(Alignment.CenterEnd).padding(top = 8.dp)) {
                Icon(
                    painterResource(Tabler.drawable.tabler_ic_x_outline),
                    contentDescription = stringResource(R.string.search_clear),
                )
            }
        }
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
private fun PickDateDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    fun selected(): LocalDate? =
        state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { selected()?.takeIf { !it.isAfter(today) }?.let(onSelected) },
                enabled = selected()?.isAfter(today) == false,
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
