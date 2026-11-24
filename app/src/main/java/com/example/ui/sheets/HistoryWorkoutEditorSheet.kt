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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CompletedWorkout
import com.example.data.model.EffortScale
import com.example.data.model.SetTag
import com.example.data.model.WorkingSubTag
import com.example.domain.EditableCompletedExercise
import com.example.domain.EditableCompletedSet
import com.example.domain.HistoryWorkoutEditDraft
import com.example.domain.HistoryWorkoutEditRules
import com.example.domain.LoadTypeRules
import com.example.domain.RepDayRules
import com.example.ui.components.EffortChipRow
import com.example.ui.components.RepDayConfirmDialog
import com.example.ui.components.SetTagBadge
import com.example.ui.components.StepperIconButton
import com.example.ui.components.EditorSheetBackHandler
import com.example.ui.components.editorSheetProperties
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.ThemeEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private enum class HistorySetDrawerKind {
    TAG,
    RPE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryWorkoutEditorSheet(
    workout: CompletedWorkout,
    exerciseLibrary: List<String>,
    effortScale: EffortScale = EffortScale.RPE,
    onSave: (CompletedWorkout) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current

    var draft by remember(workout.id) {
        mutableStateOf(HistoryWorkoutEditRules.fromCompletedWorkout(workout))
    }
    var durationText by remember(workout.id) {
        mutableStateOf(draft.durationMinutes.toString())
    }
    val initialSignature = remember(workout.id) {
        HistoryWorkoutEditRules.snapshotSignature(draft)
    }
    val isDirty = HistoryWorkoutEditRules.isDirty(draft, initialSignature)
    val saveBlockedReason = HistoryWorkoutEditRules.validationError(draft)
    val canSave = saveBlockedReason == null

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var focusedExerciseId by remember { mutableStateOf<String?>(null) }
    val activeSetDrawer = remember(workout.id) {
        mutableStateMapOf<String, Pair<String, HistorySetDrawerKind>?>()
    }

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

    val liveVolume = remember(draft.exercises) {
        draft.exercises.sumOf { ex ->
            ex.sets.sumOf { s ->
                val w = s.weightText.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                val r = s.repsText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                LoadTypeRules.setVolume(w, r, ex.loadType, workout.bodyWeight)
            }
        }.roundToInt()
    }
    val liveSetCount = remember(draft.exercises) { draft.exercises.sumOf { it.sets.size } }

    if (showDiscardDialog) {
        RepDayConfirmDialog(
            title = "Discard unsaved changes?",
            body = "Your edits to this workout log have not been saved. Discard them and close?",
            confirmLabel = "Discard Changes",
            dismissLabel = "Keep Editing",
            onConfirm = {
                showDiscardDialog = false
                onDismiss()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    if (showDatePicker) {
        val localCal = remember(draft.startedAtMs) {
            Calendar.getInstance().apply { timeInMillis = draft.startedAtMs }
        }
        val initialUtcMillis = remember(draft.startedAtMs) {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(
                    localCal.get(Calendar.YEAR),
                    localCal.get(Calendar.MONTH),
                    localCal.get(Calendar.DAY_OF_MONTH)
                )
            }.timeInMillis
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMs ->
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = utcMs
                            }
                            val updatedLocal = Calendar.getInstance().apply {
                                timeInMillis = draft.startedAtMs
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            }
                            draft = draft.copy(startedAtMs = updatedLocal.timeInMillis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Set Date", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val localCal = remember(draft.startedAtMs) {
            Calendar.getInstance().apply { timeInMillis = draft.startedAtMs }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = localCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = localCal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = colors.surface,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text("Start Time", style = MaterialTheme.typography.titleLarge, color = colors.text)
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedLocal = Calendar.getInstance().apply {
                            timeInMillis = draft.startedAtMs
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        draft = draft.copy(startedAtMs = updatedLocal.timeInMillis)
                        showTimePicker = false
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    )
                ) {
                    Text("Set Time", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
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
                    Text("Edit Workout Log", style = MaterialTheme.typography.headlineSmall, color = colors.text)
                    Text(
                        text = "${draft.durationMinutes} min · ${RepDayRules.formatVolume(liveVolume)} ${workout.unit.label} · $liveSetCount sets",
                        style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.accentSoftFg)
                    )
                }
                IconButton(
                    onClick = { attemptClose() },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("history_editor_close_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }
            HorizontalDivider(color = colors.border)

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Session Metadata Card (Start Date/Time, Duration, Notes)
                item(key = "session_meta") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = draft.routineName,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.text
                            )

                            // Start Date & Start Time pickers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    onClick = { showDatePicker = true },
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, colors.borderStrong),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("history_editor_date_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.CalendarToday,
                                            contentDescription = "Start date",
                                            tint = colors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column {
                                            Text("Start Date", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                            Text(
                                                text = formatShortDate(draft.startedAtMs),
                                                style = MonoTabularStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    onClick = { showTimePicker = true },
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, colors.borderStrong),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("history_editor_time_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Schedule,
                                            contentDescription = "Start time",
                                            tint = colors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column {
                                            Text("Start Time", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                            Text(
                                                text = formatShortTime(draft.startedAtMs),
                                                style = MonoTabularStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Duration (minutes)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Duration (minutes)", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StepperIconButton(
                                        icon = Icons.Rounded.Remove,
                                        contentDescription = "Decrease duration by 5 minutes",
                                        enabled = draft.durationMinutes > 1
                                    ) {
                                        val next = (draft.durationMinutes - 5).coerceAtLeast(1)
                                        draft = draft.copy(durationMinutes = next)
                                        durationText = next.toString()
                                    }
                                    OutlinedTextField(
                                        value = durationText,
                                        onValueChange = { raw ->
                                            val digits = raw.filter { it.isDigit() }.take(4)
                                            durationText = digits
                                            val parsed = digits.toIntOrNull()
                                            if (parsed != null && parsed >= 1) {
                                                draft = draft.copy(durationMinutes = parsed.coerceAtMost(1440))
                                            }
                                        },
                                        singleLine = true,
                                        suffix = { Text("min", color = colors.textMuted) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = MonoTabularStyle.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.text,
                                            textAlign = TextAlign.Center
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.accent,
                                            unfocusedBorderColor = colors.borderStrong,
                                            focusedContainerColor = colors.surface,
                                            unfocusedContainerColor = colors.surface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("history_editor_duration_input")
                                    )
                                    StepperIconButton(
                                        icon = Icons.Rounded.Add,
                                        contentDescription = "Increase duration by 5 minutes",
                                        enabled = draft.durationMinutes < 1440
                                    ) {
                                        val next = (draft.durationMinutes + 5).coerceAtMost(1440)
                                        draft = draft.copy(durationMinutes = next)
                                        durationText = next.toString()
                                    }
                                }
                            }

                            // Notes
                            OutlinedTextField(
                                value = draft.notes,
                                onValueChange = { next -> draft = draft.copy(notes = next) },
                                label = { Text("Workout Notes") },
                                placeholder = { Text("How did the session feel?") },
                                minLines = 2,
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.borderStrong,
                                    focusedContainerColor = colors.surface,
                                    unfocusedContainerColor = colors.surface,
                                    focusedTextColor = colors.text,
                                    unfocusedTextColor = colors.text
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_editor_notes_input")
                            )
                        }
                    }
                }

                // Exercises list
                itemsIndexed(draft.exercises, key = { _, ex -> ex.id }) { exIdx, exercise ->
                    HistoryExerciseEditCard(
                        index = exIdx,
                        exercise = exercise,
                        unitLabel = LoadTypeRules.weightColumnLabel(workout.unit, exercise.loadType),
                        effortScale = effortScale,
                        exerciseLibrary = exerciseLibrary,
                        isSuggestionsOpen = focusedExerciseId == exercise.id,
                        openSetDrawer = activeSetDrawer[exercise.id],
                        onFocusSuggestions = { focusedExerciseId = if (it) exercise.id else null },
                        onToggleSetDrawer = { setId, kind ->
                            val cur = activeSetDrawer[exercise.id]
                            activeSetDrawer[exercise.id] = if (cur?.first == setId && cur.second == kind) null else (setId to kind)
                        },
                        onRenameExercise = { newName ->
                            draft = draft.copy(
                                exercises = draft.exercises.map {
                                    if (it.id == exercise.id) it.copy(name = newName) else it
                                }
                            )
                        },
                        onRemoveExercise = {
                            draft = draft.copy(exercises = draft.exercises.filterNot { it.id == exercise.id })
                            activeSetDrawer.remove(exercise.id)
                            if (focusedExerciseId == exercise.id) focusedExerciseId = null
                        },
                        onUpdateSet = { setId, transform ->
                            draft = draft.copy(
                                exercises = draft.exercises.map { ex ->
                                    if (ex.id != exercise.id) ex
                                    else ex.copy(
                                        sets = ex.sets.map { s -> if (s.id == setId) transform(s) else s }
                                    )
                                }
                            )
                        },
                        onAddSet = {
                            val last = exercise.sets.lastOrNull()
                            val newSet = EditableCompletedSet(
                                id = RepDayRules.newId("hs"),
                                weightText = last?.weightText.orEmpty(),
                                repsText = last?.repsText.orEmpty(),
                                rpe = last?.rpe,
                                tag = last?.tag,
                                workingSubTag = last?.workingSubTag
                            )
                            draft = draft.copy(
                                exercises = draft.exercises.map { ex ->
                                    if (ex.id == exercise.id) ex.copy(sets = ex.sets + newSet) else ex
                                }
                            )
                        },
                        onRemoveSet = { setId ->
                            draft = draft.copy(
                                exercises = draft.exercises.map { ex ->
                                    if (ex.id != exercise.id) ex
                                    else ex.copy(sets = ex.sets.filterNot { it.id == setId })
                                }
                            )
                            if (activeSetDrawer[exercise.id]?.first == setId) {
                                activeSetDrawer[exercise.id] = null
                            }
                        }
                    )
                }

                if (draft.exercises.isEmpty()) {
                    item(key = "empty_exercises_warning") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.dangerSurface,
                            border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "At least one exercise with a set is required to save this workout log.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.danger,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            // Sticky Footer
            HorizontalDivider(color = colors.border)
            // Say why Save is disabled. The empty-exercises case already has a banner in the list.
            if (saveBlockedReason != null && draft.exercises.isNotEmpty()) {
                Text(
                    text = saveBlockedReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                        .testTag("history_editor_save_blocked_reason")
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { attemptClose() },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("history_editor_cancel_button")
                ) {
                    Text("Cancel", color = colors.text, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        HistoryWorkoutEditRules.applyEdit(draft)?.let(onSave)
                    },
                    enabled = canSave,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg,
                        disabledContainerColor = colors.surfaceAlt,
                        disabledContentColor = colors.textMuted
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("history_editor_save_button")
                ) {
                    Text("Save Changes", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryExerciseEditCard(
    index: Int,
    exercise: EditableCompletedExercise,
    unitLabel: String,
    effortScale: EffortScale,
    exerciseLibrary: List<String>,
    isSuggestionsOpen: Boolean,
    openSetDrawer: Pair<String, HistorySetDrawerKind>?,
    onFocusSuggestions: (Boolean) -> Unit,
    onToggleSetDrawer: (setId: String, kind: HistorySetDrawerKind) -> Unit,
    onRenameExercise: (String) -> Unit,
    onRemoveExercise: () -> Unit,
    onUpdateSet: (setId: String, transform: (EditableCompletedSet) -> EditableCompletedSet) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (setId: String) -> Unit
) {
    val colors = LocalRepDayColors.current
    val suggestions = remember(exercise.name, exerciseLibrary, isSuggestionsOpen) {
        if (!isSuggestionsOpen || exercise.name.trim().isEmpty()) emptyList()
        else {
            val matches = RepDayRules.searchExercises(exercise.name, exerciseLibrary, limit = 6)
            if (matches.size == 1 && RepDayRules.exerciseKey(matches.first()) == RepDayRules.exerciseKey(exercise.name)) {
                emptyList()
            } else matches
        }
    }
    val isNewExerciseName = remember(exercise.name, exerciseLibrary) {
        val key = RepDayRules.exerciseKey(exercise.name)
        key.isNotEmpty() && exerciseLibrary.none { RepDayRules.exerciseKey(it) == key }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceAlt,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_edit_exercise_$index")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Exercise Name Row + Remove Exercise
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = exercise.name,
                    onValueChange = { next ->
                        onRenameExercise(next)
                        onFocusSuggestions(true)
                    },
                    label = { Text("Exercise ${index + 1}") },
                    singleLine = true,
                    trailingIcon = {
                        if (isNewExerciseName) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentSoft)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "New",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.accentSoftFg
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.borderStrong,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_edit_exercise_name_$index")
                )
                IconButton(
                    onClick = onRemoveExercise,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(1.dp, colors.border, CircleShape)
                        .testTag("history_remove_exercise_$index")
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove exercise",
                        tint = colors.danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Library Autocomplete Suggestions
            AnimatedVisibility(visible = suggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.borderStrong, RoundedCornerShape(12.dp))
                ) {
                    suggestions.forEachIndexed { idx, candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRenameExercise(candidate)
                                    onFocusSuggestions(false)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = candidate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Library",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted
                            )
                        }
                        if (idx < suggestions.lastIndex) {
                            HorizontalDivider(color = colors.border)
                        }
                    }
                }
            }

            // Column headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("SET", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.width(52.dp))
                Text(unitLabel.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("REPS", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(0.85f))
                Text(effortScale.label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.width(54.dp))
                Spacer(Modifier.width(36.dp))
            }

            // Set rows
            exercise.sets.forEachIndexed { setIdx, set ->
                val activeDrawerKind = if (openSetDrawer?.first == set.id) openSetDrawer.second else null
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Set # / Tag pill
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (activeDrawerKind == HistorySetDrawerKind.TAG) colors.accentSoft else colors.surface)
                                .border(
                                    1.dp,
                                    if (activeDrawerKind == HistorySetDrawerKind.TAG) colors.accent else colors.border,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onToggleSetDrawer(set.id, HistorySetDrawerKind.TAG) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (set.tag != null) {
                                SetTagBadge(tag = set.tag, subTag = set.workingSubTag, useFullLabel = false)
                            } else {
                                Text(
                                    text = "${setIdx + 1}",
                                    style = MonoTabularStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text)
                                )
                            }
                        }

                        // Weight numeric cell
                        HistoryNumericCell(
                            value = set.weightText,
                            placeholder = "0",
                            allowDecimal = true,
                            onValueChange = { next ->
                                onUpdateSet(set.id) { it.copy(weightText = next) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("history_edit_weight_${index}_$setIdx")
                        )

                        // Reps numeric cell
                        HistoryNumericCell(
                            value = set.repsText,
                            placeholder = "0",
                            allowDecimal = false,
                            onValueChange = { next ->
                                onUpdateSet(set.id) { it.copy(repsText = next) }
                            },
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("history_edit_reps_${index}_$setIdx")
                        )

                        // RPE pill
                        Box(
                            modifier = Modifier
                                .width(54.dp)
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (activeDrawerKind == HistorySetDrawerKind.RPE || set.rpe != null) colors.accentSoft
                                    else colors.surface
                                )
                                .border(
                                    1.dp,
                                    if (activeDrawerKind == HistorySetDrawerKind.RPE) colors.accent else colors.border,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onToggleSetDrawer(set.id, HistorySetDrawerKind.RPE) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = set.rpe?.let { RepDayRules.formatEffortValue(it, effortScale, compact = true) } ?: "—",
                                style = MonoTabularStyle.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (set.rpe != null) colors.accentSoftFg else colors.textMuted
                                ),
                                maxLines = 1
                            )
                        }

                        // Remove set button
                        IconButton(
                            onClick = { onRemoveSet(set.id) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("history_remove_set_${index}_$setIdx")
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove set ${setIdx + 1}",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Tag picker drawer
                    AnimatedVisibility(visible = activeDrawerKind == HistorySetDrawerKind.TAG) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.borderStrong, RoundedCornerShape(14.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Set Tag", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val tagChoices: List<Pair<SetTag, WorkingSubTag?>> = listOf(
                                    SetTag.WARMUP to null,
                                    SetTag.WORKING to WorkingSubTag.TOP,
                                    SetTag.WORKING to WorkingSubTag.BACKOFF,
                                    SetTag.WORKING to null,
                                    SetTag.DROP to null,
                                    SetTag.MYO to null,
                                    SetTag.PARTIALS to null
                                )
                                tagChoices.forEach { (t, sub) ->
                                    val selected = set.tag == t && set.workingSubTag == sub
                                    val label = RepDayRules.tagFullLabel(t, sub).orEmpty()
                                    val tagPalette = ThemeEngine.setTagColors(t, sub, colors)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) tagPalette.bg else colors.surfaceAlt)
                                            .border(
                                                1.dp,
                                                if (selected) tagPalette.border else colors.border,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                if (selected) {
                                                    onUpdateSet(set.id) { it.copy(tag = null, workingSubTag = null) }
                                                } else {
                                                    onUpdateSet(set.id) { it.copy(tag = t, workingSubTag = sub) }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (selected) tagPalette.text else colors.textSecondary
                                        )
                                    }
                                }
                                if (set.tag != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.dangerSurface)
                                            .border(1.dp, colors.danger.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                onUpdateSet(set.id) { it.copy(tag = null, workingSubTag = null) }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = "Clear Tag",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = colors.danger
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Effort picker drawer
                    AnimatedVisibility(visible = activeDrawerKind == HistorySetDrawerKind.RPE) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.borderStrong, RoundedCornerShape(14.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Effort (${effortScale.label})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary
                                )
                                if (set.rpe != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.dangerSurface)
                                            .border(1.dp, colors.danger.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                onUpdateSet(set.id) { it.copy(rpe = null) }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "Clear ${effortScale.label}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = colors.danger
                                        )
                                    }
                                }
                            }
                            EffortChipRow(
                                scale = effortScale,
                                selectedRpe = set.rpe,
                                onSelectRpe = { next ->
                                    onUpdateSet(set.id) { it.copy(rpe = next) }
                                },
                                showDescription = true,
                                testTagPrefix = "history_effort_${set.id}"
                            )
                        }
                    }
                }
            }

            // Add Set button
            OutlinedButton(
                onClick = onAddSet,
                shape = CircleShape,
                border = BorderStroke(1.dp, colors.borderStrong),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .testTag("history_add_set_$index")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Set", style = MaterialTheme.typography.labelLarge, color = colors.text)
            }
        }
    }
}

@Composable
private fun HistoryNumericCell(
    value: String,
    placeholder: String,
    allowDecimal: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRepDayColors.current
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.borderStrong, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MonoTabularStyle.copy(
                    fontSize = 14.sp,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                val filtered = if (allowDecimal) {
                    RepDayRules.sanitizeDecimalInput(raw, maxLength = 7)
                } else {
                    raw.filter { it.isDigit() }.take(4)
                }
                onValueChange(filtered)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            textStyle = MonoTabularStyle.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatShortDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(millis))

private fun formatShortTime(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.US).format(Date(millis))
