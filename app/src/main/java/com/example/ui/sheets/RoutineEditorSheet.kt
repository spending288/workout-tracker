package com.example.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EffortScale
import com.example.data.model.LoadType
import com.example.data.model.PlannedSet
import com.example.data.model.Routine
import com.example.data.model.RoutineExercise
import com.example.data.model.SetTag
import com.example.data.model.WorkingSubTag
import com.example.domain.ExerciseUsage
import com.example.domain.LoadTypeRules
import com.example.domain.RepDayRules
import com.example.ui.components.RepDayConfirmDialog
import com.example.ui.components.RestPickerInline
import com.example.ui.components.SetTagBadge
import com.example.ui.components.SetTargetControl
import com.example.ui.components.StepperIconButton
import com.example.ui.components.EditorSheetBackHandler
import com.example.ui.components.editorSheetProperties
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.ThemeEngine
import com.example.ui.viewmodel.RoutineEditorRequest

private data class EditableExercise(
    val id: String,
    val name: String,
    val restSeconds: Int,
    val sets: List<PlannedSet>
)

private enum class SetDrawerKind {
    TAG, TARGET, REST
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorSheet(
    request: RoutineEditorRequest,
    defaultRestSeconds: Int,
    effortScale: EffortScale = EffortScale.RPE,
    exerciseLibrary: List<String>,
    customExercises: List<String>,
    onSave: (Routine) -> Unit,
    onDismiss: () -> Unit,
    exerciseUsage: Map<String, ExerciseUsage> = emptyMap(),
    loadTypes: Map<String, LoadType> = emptyMap(),
    onSetLoadType: (exerciseName: String, type: LoadType) -> Unit = { _, _ -> }
) {
    val colors = LocalRepDayColors.current
    val initial = request.initialRoutine
    // Weight type choices made in this editor, by exercise row id. Applied only when saving.
    val pendingLoadTypes = remember(request) { mutableStateMapOf<String, LoadType>() }

    var name by remember(request) { mutableStateOf(initial?.name ?: request.prefilledName ?: "") }
    var focus by remember(request) { mutableStateOf(initial?.focus ?: "") }
    var description by remember(request) { mutableStateOf(initial?.description ?: "") }
    var exercises by remember(request) {
        mutableStateOf(
            if (initial != null && initial.exercises.isNotEmpty()) {
                initial.exercises.map { ex ->
                    EditableExercise(
                        id = ex.id,
                        name = ex.name,
                        restSeconds = ex.restSeconds ?: defaultRestSeconds,
                        sets = RepDayRules.expandPlannedSets(ex, defaultRestSeconds)
                    )
                }
            } else {
                listOf(
                    EditableExercise(
                        id = RepDayRules.newId("ex"),
                        name = "",
                        restSeconds = defaultRestSeconds,
                        sets = List(3) {
                            PlannedSet(RepDayRules.newId("ps"), null, null, "8-10", null, defaultRestSeconds)
                        }
                    )
                )
            }
        )
    }

    fun snapshotSignature(n: String, f: String, d: String, exs: List<EditableExercise>): String {
        return buildString {
            append(n.trim()).append('|').append(f.trim()).append('|').append(d.trim())
            exs.forEach { e ->
                append("||").append(e.name.trim()).append(':').append(e.restSeconds)
                e.sets.forEach { s ->
                    append(';').append(s.tag).append(',').append(s.workingSubTag)
                        .append(',').append(s.repRange).append(',').append(s.targetRpe)
                        .append(',').append(s.restSeconds)
                }
            }
        }
    }

    val initialSignature = remember(request) { snapshotSignature(name, focus, description, exercises) }
    val isDirty = request.isDuplicateDraft ||
        snapshotSignature(name, focus, description, exercises) != initialSignature ||
        pendingLoadTypes.isNotEmpty()

    var showDiscardDialog by remember { mutableStateOf(false) }

    // Read through remembered state so even an older copy of this function (which the bottom
    // sheet can hold on to) sees the current unsaved-changes status.
    val latestIsDirtyForClose = rememberUpdatedState(isDirty)
    val latestOnDismiss = rememberUpdatedState(onDismiss)
    fun attemptClose() {
        if (latestIsDirtyForClose.value) showDiscardDialog = true else latestOnDismiss.value()
    }

    val latestIsDirty = rememberUpdatedState(isDirty)
    val confirmSheetValueChange: (SheetValue) -> Boolean = remember {
        { targetValue ->
            if (targetValue == SheetValue.Hidden && latestIsDirty.value) {
                showDiscardDialog = true
                false
            } else {
                true
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmSheetValueChange
    )

    // Per-set setup ALWAYS starts collapsed on Create, Edit, and Duplicate
    val expandedPerSetIds = remember(request) { mutableStateMapOf<String, Boolean>() }
    val openSummaryTargetIds = remember(request) { mutableStateMapOf<String, Boolean>() }
    val openSummaryRestIds = remember(request) { mutableStateMapOf<String, Boolean>() }
    val activeSetDrawer = remember(request) { mutableStateMapOf<String, Pair<String, SetDrawerKind>?>() }
    var focusedExerciseId by remember { mutableStateOf<String?>(null) }

    val canSave = name.trim().isNotEmpty() && exercises.any { it.name.trim().isNotEmpty() }

    val sheetTitle = when {
        request.isFromHistoryDraft -> "Save as Routine"
        request.isDuplicateDraft -> "Duplicate Routine"
        initial != null -> "Edit Routine"
        else -> "New Routine"
    }
    val submitLabel = when {
        request.isFromHistoryDraft -> "Save Routine"
        request.isDuplicateDraft -> "Save Copy"
        initial != null -> "Save Changes"
        else -> "Create Routine"
    }

    if (showDiscardDialog) {
        RepDayConfirmDialog(
            title = "Discard unsaved changes?",
            body = "Your routine edits have not been saved. Discard them and close?",
            confirmLabel = "Discard Changes",
            dismissLabel = "Keep Editing",
            onConfirm = {
                showDiscardDialog = false
                onDismiss()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { attemptClose() },
        sheetState = sheetState,
        properties = editorSheetProperties(),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        EditorSheetBackHandler(onCloseRequest = { attemptClose() })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Sticky Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(sheetTitle, style = MaterialTheme.typography.headlineSmall, color = colors.text)
                    Text(
                        text = "${exercises.size} exercises · ${exercises.sumOf { it.sets.size }} total sets",
                        style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.textSecondary)
                    )
                }
                IconButton(
                    onClick = { attemptClose() },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("routine_editor_close")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }
            HorizontalDivider(color = colors.border)

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Routine Name *") },
                            placeholder = { Text("e.g. Push Day, Lower Strength") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.borderStrong
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("routine_name_input")
                        )
                        OutlinedTextField(
                            value = focus,
                            onValueChange = { focus = it },
                            label = { Text("Muscle Group Focus (optional)") },
                            placeholder = { Text("e.g. Chest, Shoulders & Triceps") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.borderStrong
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (optional)") },
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.borderStrong
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Text("Exercises", style = MaterialTheme.typography.titleMedium, color = colors.text)
                }

                itemsIndexed(exercises, key = { _, e -> e.id }) { exIdx, ex ->
                    val isPerSetExpanded = expandedPerSetIds[ex.id] == true
                    val isSummaryTargetOpen = openSummaryTargetIds[ex.id] == true
                    val isSummaryRestOpen = openSummaryRestIds[ex.id] == true
                    val openSetDrawer = activeSetDrawer[ex.id]

                    val isTargetVaried = remember(ex.sets) {
                        val first = ex.sets.firstOrNull()
                        ex.sets.any { it.repRange != first?.repRange || it.targetRpe != first?.targetRpe }
                    }
                    val derivedEx = remember(ex) {
                        RepDayRules.withDerivedSummary(
                            RoutineExercise(ex.id, ex.name, ex.sets.size, "", null, ex.restSeconds, ex.sets)
                        )
                    }
                    val suggestions = remember(ex.name, exerciseLibrary, focusedExerciseId) {
                        if (focusedExerciseId == ex.id || ex.name.isNotBlank()) {
                            RepDayRules.searchExercises(
                                ex.name, exerciseLibrary, limit = 8, omitExactMatch = true, usage = exerciseUsage
                            )
                        } else emptyList()
                    }
                    val isBrandNew = ex.name.trim().isNotEmpty() && !RepDayRules.isKnownInLibrary(ex.name, customExercises)

                    fun updateThisExercise(transform: (EditableExercise) -> EditableExercise) {
                        exercises = exercises.map { if (it.id == ex.id) transform(it) else it }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Top Row: Index + Exercise Name + Up/Down/Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${exIdx + 1}.",
                                    style = MonoTabularStyle.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent
                                    )
                                )
                                OutlinedTextField(
                                    value = ex.name,
                                    onValueChange = { nextName ->
                                        focusedExerciseId = ex.id
                                        updateThisExercise { it.copy(name = nextName) }
                                    },
                                    placeholder = { Text("Exercise name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.borderStrong,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("editor_exercise_name_$exIdx")
                                )
                                IconButton(
                                    onClick = {
                                        if (exIdx > 0) {
                                            val m = exercises.toMutableList()
                                            val item = m.removeAt(exIdx)
                                            m.add(exIdx - 1, item)
                                            exercises = m
                                        }
                                    },
                                    enabled = exIdx > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.ArrowUpward,
                                        contentDescription = "Move exercise up",
                                        tint = if (exIdx > 0) colors.textSecondary else colors.borderStrong,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (exIdx < exercises.lastIndex) {
                                            val m = exercises.toMutableList()
                                            val item = m.removeAt(exIdx)
                                            m.add(exIdx + 1, item)
                                            exercises = m
                                        }
                                    },
                                    enabled = exIdx < exercises.lastIndex,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.ArrowDownward,
                                        contentDescription = "Move exercise down",
                                        tint = if (exIdx < exercises.lastIndex) colors.textSecondary else colors.borderStrong,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (exercises.size > 1) {
                                            exercises = exercises.filterNot { it.id == ex.id }
                                        }
                                    },
                                    enabled = exercises.size > 1,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove exercise",
                                        tint = if (exercises.size > 1) colors.danger else colors.borderStrong,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (isBrandNew) {
                                Text(
                                    text = "New exercise — will be saved to your library",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.accentSoftFg
                                )
                            }

                            // Autocomplete chips
                            if (focusedExerciseId == ex.id && suggestions.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    suggestions.forEach { sName ->
                                        Surface(
                                            onClick = {
                                                updateThisExercise { it.copy(name = sName) }
                                                focusedExerciseId = null
                                            },
                                            shape = CircleShape,
                                            color = colors.surface,
                                            border = BorderStroke(1.dp, colors.borderStrong)
                                        ) {
                                            Text(
                                                text = sName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.text,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // 3-Column Exercise Summary Controls: Sets | Reps/RPE | Default Rest
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Sets stepper (1..20)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surface)
                                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Sets", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        StepperIconButton(Icons.Rounded.Remove, "Fewer sets", enabled = ex.sets.size > 1) {
                                            updateThisExercise { cur ->
                                                val trimmed = cur.sets.dropLast(1)
                                                cur.copy(sets = RepDayRules.enforceDropSetRestRules(trimmed, cur.restSeconds, previousSets = cur.sets))
                                            }
                                        }
                                        Text(
                                            text = "${ex.sets.size}",
                                            style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
                                        )
                                        StepperIconButton(Icons.Rounded.Add, "More sets", enabled = ex.sets.size < 20) {
                                            updateThisExercise { cur ->
                                                val last = cur.sets.last()
                                                val nextTag = if (last.tag == SetTag.DROP) SetTag.WORKING else last.tag
                                                val nextRest = if (last.restSeconds == 0) cur.restSeconds else last.restSeconds
                                                val cloned = last.copy(
                                                    id = RepDayRules.newId("ps"),
                                                    tag = nextTag,
                                                    workingSubTag = if (nextTag == SetTag.WORKING) last.workingSubTag else null,
                                                    restSeconds = nextRest
                                                )
                                                cur.copy(sets = RepDayRules.enforceDropSetRestRules(cur.sets + cloned, cur.restSeconds, previousSets = cur.sets))
                                            }
                                        }
                                    }
                                }

                                // Reps / RPE summary button
                                Column(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSummaryTargetOpen) colors.accentSoft else colors.surface)
                                        .border(
                                            1.dp,
                                            if (isSummaryTargetOpen) colors.accent else colors.border,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            openSummaryTargetIds[ex.id] = !isSummaryTargetOpen
                                            if (!isSummaryTargetOpen) openSummaryRestIds[ex.id] = false
                                        }
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isTargetVaried) "Target (varied)" else "Reps / ${effortScale.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textMuted,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = RepDayRules.formatSetTargetLabel(
                                            derivedEx.repRange.ifBlank { null },
                                            derivedEx.targetRpe,
                                            effortScale
                                        ),
                                        style = MonoTabularStyle.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accentSoftFg
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Default Rest button
                                Column(
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSummaryRestOpen) colors.accentSoft else colors.surface)
                                        .border(
                                            1.dp,
                                            if (isSummaryRestOpen) colors.accent else colors.border,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            openSummaryRestIds[ex.id] = !isSummaryRestOpen
                                            if (!isSummaryRestOpen) openSummaryTargetIds[ex.id] = false
                                        }
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Rest", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                    Text(
                                        text = RepDayRules.formatRestTime(ex.restSeconds),
                                        style = MonoTabularStyle.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.text
                                        )
                                    )
                                }
                            }

                            // Inline Uniform Target Drawer
                            AnimatedVisibility(visible = isSummaryTargetOpen) {
                                val firstSet = ex.sets.firstOrNull()
                                SetTargetControl(
                                    repRange = firstSet?.repRange,
                                    targetRpe = firstSet?.targetRpe,
                                    effortScale = effortScale,
                                    onTargetChange = { newRep, newRpe ->
                                        updateThisExercise { cur ->
                                            cur.copy(sets = cur.sets.map { it.copy(repRange = newRep, targetRpe = newRpe) })
                                        }
                                    }
                                )
                            }

                            // Inline Exercise Default Rest Drawer
                            AnimatedVisibility(visible = isSummaryRestOpen) {
                                RestPickerInline(
                                    restSeconds = ex.restSeconds,
                                    onRestChange = { newRest ->
                                        updateThisExercise { cur ->
                                            val oldRest = cur.restSeconds
                                            val updatedSets = cur.sets.mapIndexed { idx, s ->
                                                val beforeDrop = RepDayRules.isPrecedingDropSet(cur.sets, idx)
                                                when {
                                                    beforeDrop -> s.copy(restSeconds = 0)
                                                    s.restSeconds == oldRest -> s.copy(restSeconds = newRest)
                                                    else -> s
                                                }
                                            }
                                            cur.copy(restSeconds = newRest, sets = updatedSets)
                                        }
                                    }
                                )
                            }

                            // Expandable Per-Set Setup Toggle
                            val tagSum = RepDayRules.summarizeSetTags(ex.sets)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surface)
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                    .clickable { expandedPerSetIds[ex.id] = !isPerSetExpanded }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = if (isPerSetExpanded) "Hide advanced" else "Advanced",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.accentSoftFg
                                    )
                                    if (!isPerSetExpanded) {
                                        Text(
                                            text = "Weight type, set types, per-set targets & rest",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (tagSum.isNotEmpty() && !isPerSetExpanded) {
                                        Text(
                                            text = tagSum,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textMuted,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isPerSetExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Expanded 5-Column Per-Set Table
                            AnimatedVisibility(visible = isPerSetExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Weight type for this exercise (saved per exercise name, used everywhere)
                                    val currentLoadType = pendingLoadTypes[ex.id]
                                        ?: LoadTypeRules.resolve(ex.name, loadTypes)
                                    Text("Weight type", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surface)
                                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        LoadType.entries.forEach { option ->
                                            val selected = option == currentLoadType
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (selected) colors.accent else Color.Transparent)
                                                    .clickable { pendingLoadTypes[ex.id] = option }
                                                    .testTag("editor_load_type_${option.name.lowercase()}_$exIdx"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = LoadTypeRules.shortLabel(option),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) colors.accentFg else colors.textSecondary,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = currentLoadType.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("#", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.width(24.dp))
                                        Text("Tag", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.weight(0.9f))
                                        Text("Target", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.weight(1.2f))
                                        Text("Rest", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.weight(0.85f))
                                        Text("", modifier = Modifier.width(68.dp))
                                    }

                                    ex.sets.forEachIndexed { setIdx, ps ->
                                        val beforeDrop = RepDayRules.isPrecedingDropSet(ex.sets, setIdx)
                                        val drawerKind = if (openSetDrawer?.first == ps.id) openSetDrawer.second else null

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.surface)
                                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "${setIdx + 1}",
                                                    style = MonoTabularStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary),
                                                    modifier = Modifier.width(24.dp)
                                                )

                                                // Tag cell
                                                Box(
                                                    modifier = Modifier
                                                        .weight(0.9f)
                                                        .heightIn(min = 36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (drawerKind == SetDrawerKind.TAG) colors.accentSoft else colors.surfaceAlt)
                                                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            activeSetDrawer[ex.id] = if (drawerKind == SetDrawerKind.TAG) null else (ps.id to SetDrawerKind.TAG)
                                                        }
                                                        .padding(horizontal = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (ps.tag != null) {
                                                        SetTagBadge(ps.tag, ps.workingSubTag)
                                                    } else {
                                                        Text("Tag", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                                    }
                                                }

                                                // Target cell
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1.2f)
                                                        .heightIn(min = 36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (drawerKind == SetDrawerKind.TARGET) colors.accentSoft else colors.surfaceAlt)
                                                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            activeSetDrawer[ex.id] = if (drawerKind == SetDrawerKind.TARGET) null else (ps.id to SetDrawerKind.TARGET)
                                                        }
                                                        .padding(horizontal = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = RepDayRules.formatSetTargetLabel(ps.repRange, ps.targetRpe, effortScale),
                                                        style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.text),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                // Rest cell (locked orange badge before Drop Set)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(0.85f)
                                                        .heightIn(min = 36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            when {
                                                                beforeDrop -> colors.dropOrangeBg
                                                                drawerKind == SetDrawerKind.REST -> colors.accentSoft
                                                                else -> colors.surfaceAlt
                                                            }
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (beforeDrop) colors.dropOrangeBorder else colors.border,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable(enabled = !beforeDrop) {
                                                            activeSetDrawer[ex.id] = if (drawerKind == SetDrawerKind.REST) null else (ps.id to SetDrawerKind.REST)
                                                        }
                                                        .padding(horizontal = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (beforeDrop || ps.restSeconds == 0) "No rest" else RepDayRules.formatRestTime(ps.restSeconds),
                                                        style = MonoTabularStyle.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (beforeDrop) colors.dropOrange else colors.textSecondary
                                                        ),
                                                        maxLines = 1
                                                    )
                                                }

                                                // Duplicate Set & Remove Set pair
                                                Row(
                                                    modifier = Modifier.width(68.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            if (ex.sets.size < 20) {
                                                                updateThisExercise { cur ->
                                                                    val list = cur.sets.toMutableList()
                                                                    val wasZeroedByDrop = RepDayRules.isPrecedingDropSet(cur.sets, setIdx) && ps.restSeconds == 0
                                                                    val clone = ps.copy(
                                                                        id = RepDayRules.newId("ps"),
                                                                        restSeconds = if (wasZeroedByDrop) cur.restSeconds else ps.restSeconds
                                                                    )
                                                                    list.add(setIdx + 1, clone)
                                                                    cur.copy(sets = RepDayRules.enforceDropSetRestRules(list, cur.restSeconds, previousSets = cur.sets))
                                                                }
                                                            }
                                                        },
                                                        enabled = ex.sets.size < 20,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.ContentCopy, "Duplicate set", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            if (ex.sets.size > 1) {
                                                                updateThisExercise { cur ->
                                                                    val list = cur.sets.toMutableList().apply { removeAt(setIdx) }
                                                                    cur.copy(sets = RepDayRules.enforceDropSetRestRules(list, cur.restSeconds, previousSets = cur.sets))
                                                                }
                                                            }
                                                        },
                                                        enabled = ex.sets.size > 1,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.DeleteOutline,
                                                            "Remove set",
                                                            tint = if (ex.sets.size > 1) colors.danger else colors.borderStrong,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (beforeDrop) {
                                                Text(
                                                    text = "No rest preceding Drop Set (Set ${setIdx + 2})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.dropOrange
                                                )
                                            }

                                            // Inline Drawers per set
                                            if (drawerKind == SetDrawerKind.TAG) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Surface(
                                                            onClick = {
                                                                updateThisExercise { cur ->
                                                                    val updated = cur.sets.map { if (it.id == ps.id) it.copy(tag = null, workingSubTag = null) else it }
                                                                    cur.copy(sets = RepDayRules.enforceDropSetRestRules(updated, cur.restSeconds, previousSets = cur.sets))
                                                                }
                                                            },
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = if (ps.tag == null) colors.accentSoft else colors.surfaceAlt,
                                                            border = BorderStroke(1.dp, if (ps.tag == null) colors.accent else colors.border)
                                                        ) {
                                                            Text("None", style = MaterialTheme.typography.labelSmall, color = colors.text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                                                        }
                                                        SetTag.entries.forEach { t ->
                                                            val sel = ps.tag == t
                                                            val tc = ThemeEngine.setTagColors(t, null, colors)
                                                            Surface(
                                                                onClick = {
                                                                    updateThisExercise { cur ->
                                                                        val updated = cur.sets.map {
                                                                            if (it.id == ps.id) {
                                                                                it.copy(
                                                                                    tag = t,
                                                                                    workingSubTag = if (t == SetTag.WORKING) it.workingSubTag else null
                                                                                )
                                                                            } else it
                                                                        }
                                                                        cur.copy(sets = RepDayRules.enforceDropSetRestRules(updated, cur.restSeconds, previousSets = cur.sets))
                                                                    }
                                                                },
                                                                shape = RoundedCornerShape(10.dp),
                                                                color = if (sel) tc.bg else colors.surfaceAlt,
                                                                border = BorderStroke(1.dp, if (sel) tc.border else colors.border)
                                                            ) {
                                                                Text(
                                                                    t.displayName,
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                                    color = if (sel) tc.text else colors.textSecondary,
                                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    if (ps.tag == SetTag.WORKING) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            WorkingSubTag.entries.forEach { sub ->
                                                                val sel = ps.workingSubTag == sub
                                                                val sc = ThemeEngine.setTagColors(SetTag.WORKING, sub, colors)
                                                                Surface(
                                                                    onClick = {
                                                                        updateThisExercise { cur ->
                                                                            val updated = cur.sets.map {
                                                                                if (it.id == ps.id) it.copy(workingSubTag = if (sel) null else sub) else it
                                                                            }
                                                                            cur.copy(sets = updated)
                                                                        }
                                                                    },
                                                                    shape = RoundedCornerShape(10.dp),
                                                                    color = if (sel) sc.bg else colors.surfaceAlt,
                                                                    border = BorderStroke(1.dp, if (sel) sc.border else colors.border)
                                                                ) {
                                                                    Text(
                                                                        sub.displayName,
                                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                        color = if (sel) sc.text else colors.textSecondary,
                                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            if (drawerKind == SetDrawerKind.TARGET) {
                                                SetTargetControl(
                                                    repRange = ps.repRange,
                                                    targetRpe = ps.targetRpe,
                                                    effortScale = effortScale,
                                                    onTargetChange = { newRep, newRpe ->
                                                        updateThisExercise { cur ->
                                                            cur.copy(sets = cur.sets.map {
                                                                if (it.id == ps.id) it.copy(repRange = newRep, targetRpe = newRpe) else it
                                                            })
                                                        }
                                                    }
                                                )
                                            }

                                            if (drawerKind == SetDrawerKind.REST && !beforeDrop) {
                                                RestPickerInline(
                                                    restSeconds = ps.restSeconds.coerceAtLeast(15),
                                                    onRestChange = { newRest ->
                                                        updateThisExercise { cur ->
                                                            cur.copy(sets = cur.sets.map {
                                                                if (it.id == ps.id) it.copy(restSeconds = newRest) else it
                                                            })
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // + Add Set button
                                    OutlinedButton(
                                        onClick = {
                                            if (ex.sets.size < 20) {
                                                updateThisExercise { cur ->
                                                    val last = cur.sets.last()
                                                    val nextTag = if (last.tag == SetTag.DROP) SetTag.WORKING else last.tag
                                                    val nextRest = if (last.restSeconds == 0) cur.restSeconds else last.restSeconds
                                                    val added = last.copy(
                                                        id = RepDayRules.newId("ps"),
                                                        tag = nextTag,
                                                        workingSubTag = if (nextTag == SetTag.WORKING) last.workingSubTag else null,
                                                        restSeconds = nextRest
                                                    )
                                                    cur.copy(sets = RepDayRules.enforceDropSetRestRules(cur.sets + added, cur.restSeconds, previousSets = cur.sets))
                                                }
                                            }
                                        },
                                        enabled = ex.sets.size < 20,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, colors.borderStrong),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Add, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Add Set", color = colors.text)
                                    }
                                }
                            }
                        }
                    }
                }

                // + Add Exercise button
                item {
                    OutlinedButton(
                        onClick = {
                            val newId = RepDayRules.newId("ex")
                            exercises = exercises + EditableExercise(
                                id = newId,
                                name = "",
                                restSeconds = defaultRestSeconds,
                                sets = List(3) {
                                    PlannedSet(RepDayRules.newId("ps"), null, null, "8-10", null, defaultRestSeconds)
                                }
                            )
                            focusedExerciseId = newId
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("editor_add_exercise_button")
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = colors.accent)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Exercise", color = colors.text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Sticky Footer
            HorizontalDivider(color = colors.border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { attemptClose() },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = colors.text)
                }
                Button(
                    onClick = {
                        val validExercises = exercises.filter { it.name.trim().isNotEmpty() }.map { e ->
                            val cleanSets = RepDayRules.enforceDropSetRestRules(e.sets, e.restSeconds)
                            RepDayRules.withDerivedSummary(
                                RoutineExercise(
                                    id = e.id,
                                    name = RepDayRules.normalizeExerciseName(e.name),
                                    targetSets = cleanSets.size,
                                    repRange = "",
                                    targetRpe = null,
                                    restSeconds = e.restSeconds,
                                    plannedSets = cleanSets
                                )
                            )
                        }
                        val nowMs = System.currentTimeMillis()
                        val now = RepDayRules.formatIso(nowMs)
                        val isCreatingNew = initial == null || request.isDuplicateDraft || request.isFromHistoryDraft
                        val existingCreatedMs = if (!isCreatingNew) {
                            initial!!.createdAtMs.takeIf { it > 0L }
                                ?: RepDayRules.parseIsoToMillis(initial.createdAt)
                                ?: nowMs
                        } else {
                            nowMs
                        }
                        val result = Routine(
                            id = if (!isCreatingNew) initial!!.id else RepDayRules.newId("rt"),
                            name = name.trim(),
                            focus = focus.trim().ifBlank { null },
                            description = description.trim().ifBlank { null },
                            exercises = validExercises,
                            createdAt = if (!isCreatingNew) initial!!.createdAt else now,
                            updatedAt = now,
                            createdAtMs = existingCreatedMs,
                            updatedAtMs = nowMs
                        )
                        // Save weight type choices for exercises still in the routine.
                        exercises.forEach { e ->
                            val chosen = pendingLoadTypes[e.id]
                            if (chosen != null && e.name.isNotBlank()) onSetLoadType(e.name, chosen)
                        }
                        onSave(result)
                    },
                    enabled = canSave,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .heightIn(min = 48.dp)
                        .testTag("routine_editor_submit")
                ) {
                    Text(submitLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
