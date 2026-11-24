package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActiveExercise
import com.example.data.model.ActiveWorkout
import com.example.data.model.AppSettings
import com.example.data.model.CompletedSet
import com.example.data.model.EffortScale
import com.example.data.model.SetTag
import com.example.data.model.WeightUnit
import com.example.data.model.WorkingSubTag
import com.example.data.model.WorkoutSet
import com.example.domain.PreviousExerciseSession
import com.example.domain.RepDayRules
import com.example.ui.components.EffortChipRow
import com.example.ui.components.RepDayConfirmDialog
import com.example.ui.sheets.SetTagRpeSheet
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.ThemeEngine
import com.example.ui.util.FeedbackUtils
import com.example.ui.viewmodel.EffortPromptTarget
import com.example.ui.viewmodel.RestTimerUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private data class SetEditTarget(
    val exerciseId: String,
    val exerciseName: String,
    val setIndex: Int,
    val set: WorkoutSet,
    val isNextDrop: Boolean,
    val exerciseDefaultRest: Int
)

@Composable
fun ActiveWorkoutScreen(
    workout: ActiveWorkout,
    lastSessionByExercise: Map<String, PreviousExerciseSession>,
    exerciseLibrary: List<String>,
    customExercises: List<String>,
    settings: AppSettings,
    pendingEffortPrompt: EffortPromptTarget?,
    elapsedSecondsFlow: StateFlow<Long>,
    restTimerFlow: StateFlow<RestTimerUiState>,
    onMinimize: () -> Unit,
    onOpenPlates: (Double?) -> Unit,
    onStartManualRest: (Int) -> Unit,
    onAdjustRest: (Int) -> Unit,
    onSelectRestPreset: (Int) -> Unit,
    onToggleSound: () -> Unit,
    onDismissRest: () -> Unit,
    onUpdateWeight: (exerciseId: String, setId: String, weight: String) -> Unit,
    onUpdateReps: (exerciseId: String, setId: String, reps: String) -> Unit,
    onFillFromPrev: (exerciseId: String, setId: String, weight: Double, reps: Int) -> Unit,
    onToggleSetDone: (exerciseId: String, setId: String, prev: CompletedSet?, prevUnit: WeightUnit?) -> Boolean,
    onUpdateSetTagAndRest: (exerciseId: String, setId: String, tag: SetTag?, subTag: WorkingSubTag?, restSeconds: Int) -> Unit,
    onSetRpe: (exerciseId: String, setId: String, rpe: Double?, dismissPrompt: Boolean) -> Unit,
    onDismissEffortPrompt: () -> Unit,
    onCycleExerciseRest: (exerciseId: String) -> Unit,
    onAddSet: (exerciseId: String) -> Unit,
    onRemoveLastSet: (exerciseId: String) -> Unit,
    onReorderExercises: (from: Int, to: Int) -> Unit,
    onAddExercise: (String) -> Unit,
    onSwapExercise: (exerciseId: String, newName: String) -> Unit,
    onSwapExerciseKeepCompleted: (exerciseId: String, newName: String) -> Unit,
    onRemoveExercise: (exerciseId: String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onFinishWorkout: (notes: String, saveAsRoutine: Boolean, newRoutineName: String) -> Unit,
    onDiscardWorkout: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isLiveWorkout = workout.routineId == null
    val sessionUnit = workout.resolvedUnit(settings.unit)
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, settings.keepScreenOnDuringWorkout) {
        val window = activity?.window
        if (settings.keepScreenOnDuringWorkout) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler(onBack = onMinimize)

    // Routine exercises start collapsed; newly added mid-workout exercises auto-expand
    val expandedExercises = remember(workout.id) { mutableStateMapOf<String, Boolean>() }
    var knownExerciseIds by remember(workout.id) {
        mutableStateOf(workout.exercises.map { it.id }.toSet())
    }
    LaunchedEffect(workout.exercises) {
        val currentIds = workout.exercises.map { it.id }
        val newlyAdded = currentIds.filterNot { it in knownExerciseIds }
        if (newlyAdded.isNotEmpty()) {
            newlyAdded.forEach { expandedExercises[it] = true }
            val lastNewIdx = workout.exercises.indexOfLast { it.id in newlyAdded }
            if (lastNewIdx >= 0) {
                listState.animateScrollToItem(lastNewIdx + 1)
            }
        }
        knownExerciseIds = currentIds.toSet()
    }
    var isReorderMode by remember { mutableStateOf(false) }
    var setEditTarget by remember { mutableStateOf<SetEditTarget?>(null) }
    var showAddExerciseSheet by remember { mutableStateOf(false) }
    var swapTargetExercise by remember { mutableStateOf<ActiveExercise?>(null) }
    var pendingSwapChoice by remember { mutableStateOf<Pair<ActiveExercise, String>?>(null) }
    var showFinishSheet by remember { mutableStateOf(false) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val totalSets = remember(workout) { workout.exercises.sumOf { it.sets.size } }
    val completedSets = remember(workout) { workout.exercises.sumOf { ex -> ex.sets.count { it.completed } } }
    val liveVolume = remember(workout) { RepDayRules.computeLiveVolume(workout) }

    if (showDiscardConfirm) {
        RepDayConfirmDialog(
            title = "Discard active workout?",
            body = "All sets logged in this session will be permanently lost.",
            confirmLabel = "Discard Workout",
            onConfirm = {
                showDiscardConfirm = false
                showFinishSheet = false
                onDiscardWorkout()
            },
            onDismiss = { showDiscardConfirm = false }
        )
    }

    setEditTarget?.let { target ->
        val liveExercise = workout.exercises.find { it.id == target.exerciseId }
        val liveSet = liveExercise?.sets?.find { it.id == target.set.id } ?: target.set
        val liveIsNextDrop = liveExercise?.let {
            RepDayRules.isPrecedingDropSetActive(it.sets, target.setIndex)
        } ?: target.isNextDrop
        SetTagRpeSheet(
            exerciseName = target.exerciseName,
            setNumber = target.setIndex + 1,
            workoutSet = liveSet,
            defaultRestSeconds = target.exerciseDefaultRest,
            isNextSetDrop = liveIsNextDrop,
            onUpdate = { tag, subTag, rest ->
                onUpdateSetTagAndRest(target.exerciseId, target.set.id, tag, subTag, rest)
            },
            onDismiss = { setEditTarget = null }
        )
    }

    if (showAddExerciseSheet) {
        AddExerciseMidWorkoutSheet(
            exerciseLibrary = exerciseLibrary,
            customExercises = customExercises,
            currentExerciseNames = workout.exercises.map { it.name }.toSet(),
            onAdd = { name ->
                onAddExercise(name)
                showAddExerciseSheet = false
            },
            onDismiss = { showAddExerciseSheet = false }
        )
    }

    swapTargetExercise?.let { targetEx ->
        SwapExerciseSheet(
            exercise = targetEx,
            canRemove = workout.exercises.size > 1 || isLiveWorkout,
            exerciseLibrary = exerciseLibrary,
            customExercises = customExercises,
            currentExerciseNames = workout.exercises.map { it.name }.toSet(),
            onSwap = { newName ->
                val cleaned = RepDayRules.normalizeExerciseName(newName)
                if (cleaned.isNotEmpty()) {
                    if (targetEx.sets.any { it.completed }) {
                        pendingSwapChoice = targetEx to cleaned
                    } else {
                        onSwapExercise(targetEx.id, cleaned)
                    }
                }
                swapTargetExercise = null
            },
            onRemove = {
                onRemoveExercise(targetEx.id)
                swapTargetExercise = null
            },
            onDismiss = { swapTargetExercise = null }
        )
    }

    pendingSwapChoice?.let { (oldEx, newName) ->
        val completedCount = oldEx.sets.count { it.completed }
        AlertDialog(
            onDismissRequest = { pendingSwapChoice = null },
            containerColor = colors.surface,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = "You've logged $completedCount ${if (completedCount == 1) "set" else "sets"} of ${oldEx.name}.",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "How should we handle those completed sets when switching to $newName?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Button(
                        onClick = {
                            pendingSwapChoice = null
                            onSwapExerciseKeepCompleted(oldEx.id, newName)
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.accentFg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("swap_keep_completed_button")
                    ) {
                        Text(
                            text = "Keep them as ${oldEx.name}, switch the rest",
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            pendingSwapChoice = null
                            onSwapExercise(oldEx.id, newName)
                        },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("swap_rename_all_button")
                    ) {
                        Text(
                            text = "They were actually $newName",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.text,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { pendingSwapChoice = null },
                    modifier = Modifier.testTag("swap_cancel_button")
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (showNotesSheet) {
        ActiveWorkoutNotesSheet(
            notes = workout.notes,
            onNotesChange = onUpdateNotes,
            onDismiss = { showNotesSheet = false }
        )
    }

    if (showFinishSheet) {
        val defaultLiveName = remember(workout.startedAt) {
            RepDayRules.formatLiveWorkoutDefaultName(workout.startedAt)
        }
        FinishWorkoutSheet(
            isLiveWorkout = isLiveWorkout,
            defaultLiveWorkoutName = defaultLiveName,
            elapsedSecondsFlow = elapsedSecondsFlow,
            completedSets = completedSets,
            uncheckedSets = (totalSets - completedSets).coerceAtLeast(0),
            totalVolume = liveVolume,
            unitLabel = sessionUnit.label,
            initialNotes = workout.notes,
            onKeepLifting = { showFinishSheet = false },
            onSave = { notes, saveAsRoutine, newRoutineName ->
                showFinishSheet = false
                onFinishWorkout(notes, saveAsRoutine, newRoutineName)
            },
            onRequestDiscard = { showDiscardConfirm = true }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
    ) {
        // Sticky Top App Bar
        Surface(
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt)
                        .testTag("minimize_workout_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Minimize workout",
                        tint = colors.text
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workout.routineName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ActiveWorkoutElapsedSubtitle(
                        elapsedSecondsFlow = elapsedSecondsFlow,
                        completedSets = completedSets,
                        totalSets = totalSets
                    )
                }

                // Manual Rest Timer Pill
                TopRestTimerPill(
                    restTimerFlow = restTimerFlow,
                    defaultRestSeconds = settings.defaultRestSeconds,
                    onAdjustRest = onAdjustRest,
                    onStartManualRest = onStartManualRest
                )

                Button(
                    onClick = { showFinishSheet = true },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("finish_workout_button")
                ) {
                    Text("Finish", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Main Content (with optional right sidebar on wide screens)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val showRightSidebar = maxWidth >= 840.dp

            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Toolbar row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    isReorderMode -> "Drag handle or tap arrows to reorder"
                                    workout.exercises.isEmpty() -> "Live session · Add exercises as you go"
                                    else -> "${workout.exercises.size} exercises · Tap to expand"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!showRightSidebar) {
                                    val hasNotes = workout.notes.isNotBlank()
                                    Surface(
                                        onClick = { showNotesSheet = true },
                                        shape = CircleShape,
                                        color = if (hasNotes) colors.accentSoft else colors.surface,
                                        border = BorderStroke(1.dp, if (hasNotes) colors.accent else colors.border),
                                        modifier = Modifier
                                            .heightIn(min = 38.dp)
                                            .testTag("toolbar_notes_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.EditNote,
                                                null,
                                                tint = if (hasNotes) colors.accent else colors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Notes",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (hasNotes) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (hasNotes) colors.accentSoftFg else colors.text
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    onClick = { onOpenPlates(null) },
                                    shape = CircleShape,
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, colors.border),
                                    modifier = Modifier
                                        .heightIn(min = 38.dp)
                                        .testTag("toolbar_plates_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.FitnessCenter, null, tint = colors.accent, modifier = Modifier.size(15.dp))
                                        Text("Plates", style = MaterialTheme.typography.labelMedium, color = colors.text)
                                    }
                                }
                                if (workout.exercises.size > 1) {
                                    Surface(
                                        onClick = { isReorderMode = !isReorderMode },
                                        shape = CircleShape,
                                        color = if (isReorderMode) colors.accent else colors.surface,
                                        border = BorderStroke(1.dp, if (isReorderMode) colors.accent else colors.border),
                                        modifier = Modifier
                                            .heightIn(min = 38.dp)
                                            .testTag("toolbar_reorder_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.SwapVert,
                                                null,
                                                tint = if (isReorderMode) colors.accentFg else colors.textSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = if (isReorderMode) "Done" else "Reorder",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isReorderMode) colors.accentFg else colors.text
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Exercises Stream or Reorder Rows
                    itemsIndexed(workout.exercises, key = { _, ex -> ex.id }) { index, exercise ->
                        if (isReorderMode) {
                            ReorderExerciseRow(
                                index = index,
                                totalCount = workout.exercises.size,
                                exercise = exercise,
                                onMoveUp = { onReorderExercises(index, index - 1) },
                                onMoveDown = { onReorderExercises(index, index + 1) }
                            )
                        } else {
                            val prevSession = remember(exercise.name, lastSessionByExercise) {
                                RepDayRules.findPreviousExerciseSession(exercise.name, lastSessionByExercise)
                            }
                            val isExpanded = expandedExercises[exercise.id] == true
                            ActiveExerciseCard(
                                index = index,
                                exercise = exercise,
                                prevSession = prevSession,
                                sessionUnit = sessionUnit,
                                settings = settings,
                                isExpanded = isExpanded,
                                onToggleExpand = { expandedExercises[exercise.id] = !isExpanded },
                                onOpenPlatesForExercise = { seed -> onOpenPlates(seed) },
                                onSwap = { swapTargetExercise = exercise },
                                onCycleRest = { onCycleExerciseRest(exercise.id) },
                                onEditSetOptions = { setIdx, set ->
                                    setEditTarget = SetEditTarget(
                                        exerciseId = exercise.id,
                                        exerciseName = exercise.name,
                                        setIndex = setIdx,
                                        set = set,
                                        isNextDrop = RepDayRules.isPrecedingDropSetActive(exercise.sets, setIdx),
                                        exerciseDefaultRest = exercise.restSeconds ?: settings.defaultRestSeconds
                                    )
                                },
                                onSetRpe = { setId, rpe -> onSetRpe(exercise.id, setId, rpe, false) },
                                onFillFromPrev = { setId, w, r -> onFillFromPrev(exercise.id, setId, w, r) },
                                onUpdateWeight = { setId, w -> onUpdateWeight(exercise.id, setId, w) },
                                onUpdateReps = { setId, r -> onUpdateReps(exercise.id, setId, r) },
                                onToggleDone = { setId, prevSet, prevUnit ->
                                    val justCompletedExercise = onToggleSetDone(exercise.id, setId, prevSet, prevUnit)
                                    if (justCompletedExercise) {
                                        expandedExercises[exercise.id] = false
                                        // Find next incomplete exercise: forward first, then wrap from top
                                        val forwardIdx = ((index + 1) until workout.exercises.size)
                                            .firstOrNull { i -> workout.exercises[i].sets.any { !it.completed } }
                                        val wrapIdx = (0 until index)
                                            .firstOrNull { i -> workout.exercises[i].sets.any { !it.completed } }
                                        val nextIdx = forwardIdx ?: wrapIdx
                                        if (nextIdx != null) {
                                            val nextEx = workout.exercises[nextIdx]
                                            expandedExercises[nextEx.id] = true
                                            val reducedMotion = FeedbackUtils.isReducedMotionEnabled(context)
                                            scope.launch {
                                                val targetItem = nextIdx + 1 // +1 for toolbar item
                                                if (reducedMotion) {
                                                    listState.scrollToItem(targetItem)
                                                } else {
                                                    listState.animateScrollToItem(targetItem)
                                                }
                                            }
                                        }
                                    }
                                },
                                onAddSet = { onAddSet(exercise.id) },
                                onRemoveSet = { onRemoveLastSet(exercise.id) }
                            )
                        }
                    }

                    // Empty Live Workout State
                    if (workout.exercises.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentSoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FlashOn,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Text(
                                        text = "Start lifting on the fly",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = colors.text,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Add exercises as you go without setting up a routine first. When you finish, you can save this workout as a routine or just log it to history.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { showAddExerciseSheet = true },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.accent,
                                            contentColor = colors.accentFg
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("live_add_first_exercise_button")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add First Exercise", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    // Stream Footer
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (workout.exercises.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showAddExerciseSheet = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, colors.borderStrong),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("active_add_exercise_button")
                                ) {
                                    Icon(Icons.Rounded.Add, null, tint = colors.accent)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Exercise", color = colors.text, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            TextButton(
                                onClick = { showDiscardConfirm = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .testTag("active_discard_workout_button")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Discard Workout", color = colors.danger, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // Tablet / Landscape Right Sidebar
                if (showRightSidebar) {
                    ActiveWorkoutSidebar(
                        workout = workout,
                        elapsedSecondsFlow = elapsedSecondsFlow,
                        completedSets = completedSets,
                        totalSets = totalSets,
                        liveVolume = liveVolume,
                        unitLabel = sessionUnit.label,
                        onJumpToExercise = { idx, exId ->
                            expandedExercises[exId] = true
                            scope.launch { listState.animateScrollToItem(idx + 1) }
                        },
                        onUpdateNotes = onUpdateNotes,
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                            .padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
                    )
                }
            }
        }

        // Docked Rest Timer Bar + Post-Set Effort Strip (isolated leaf collector)
        RestTimerBarHost(
            restTimerFlow = restTimerFlow,
            soundEnabled = settings.soundEnabled,
            effortScale = settings.effortScale,
            pendingEffortPrompt = pendingEffortPrompt,
            onToggleSound = onToggleSound,
            onSelectPreset = onSelectRestPreset,
            onAdjust = onAdjustRest,
            onDismiss = onDismissRest,
            onSelectEffort = { exId, setId, rpe -> onSetRpe(exId, setId, rpe, true) },
            onDismissEffortPrompt = onDismissEffortPrompt
        )
    }
}

@Composable
private fun ActiveWorkoutElapsedSubtitle(
    elapsedSecondsFlow: StateFlow<Long>,
    completedSets: Int,
    totalSets: Int
) {
    val colors = LocalRepDayColors.current
    val elapsedSeconds by elapsedSecondsFlow.collectAsStateWithLifecycle()
    Text(
        text = "${RepDayRules.formatElapsed(elapsedSeconds)} · $completedSets/$totalSets sets",
        style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.textSecondary)
    )
}

@Composable
private fun TopRestTimerPill(
    restTimerFlow: StateFlow<RestTimerUiState>,
    defaultRestSeconds: Int,
    onAdjustRest: (Int) -> Unit,
    onStartManualRest: (Int) -> Unit
) {
    val colors = LocalRepDayColors.current
    val restTimer by restTimerFlow.collectAsStateWithLifecycle()
    Surface(
        onClick = {
            if (restTimer.isActive && !restTimer.isComplete) {
                onAdjustRest(30)
            } else {
                onStartManualRest(defaultRestSeconds)
            }
        },
        shape = CircleShape,
        color = if (restTimer.isActive) colors.accentSoft else colors.surfaceAlt,
        border = BorderStroke(1.dp, if (restTimer.isActive) colors.accent else colors.border),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .testTag("top_rest_timer_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = "Start rest timer",
                tint = if (restTimer.isActive) colors.accent else colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (restTimer.isActive && !restTimer.isComplete) {
                    RepDayRules.formatRestTime(restTimer.remainingSeconds)
                } else {
                    RepDayRules.formatRestTime(defaultRestSeconds)
                },
                style = MonoTabularStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (restTimer.isActive) colors.accentSoftFg else colors.text
                )
            )
        }
    }
}

@Composable
private fun RestTimerBarHost(
    restTimerFlow: StateFlow<RestTimerUiState>,
    soundEnabled: Boolean,
    effortScale: EffortScale,
    pendingEffortPrompt: EffortPromptTarget?,
    onToggleSound: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onAdjust: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSelectEffort: (exerciseId: String, setId: String, rpe: Double?) -> Unit,
    onDismissEffortPrompt: () -> Unit
) {
    val restTimer by restTimerFlow.collectAsStateWithLifecycle()
    val colors = LocalRepDayColors.current
    val context = LocalContext.current
    if (restTimer.isActive) {
        RestTimerBar(
            restTimer = restTimer,
            soundEnabled = soundEnabled,
            effortScale = effortScale,
            pendingEffortPrompt = pendingEffortPrompt,
            onToggleSound = onToggleSound,
            onSelectPreset = onSelectPreset,
            onAdjust = onAdjust,
            onDismiss = onDismiss,
            onSelectEffort = onSelectEffort,
            onDismissEffortPrompt = onDismissEffortPrompt
        )
    } else if (pendingEffortPrompt != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "How hard? Set ${pendingEffortPrompt.setNumber} · ${pendingEffortPrompt.exerciseName}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismissEffortPrompt,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("dismiss_effort_prompt_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss effort prompt",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            EffortChipRow(
                scale = effortScale,
                selectedRpe = pendingEffortPrompt.currentRpe,
                targetRpe = pendingEffortPrompt.targetRpe,
                onSelectRpe = { next ->
                    FeedbackUtils.triggerLightTick(context)
                    onSelectEffort(pendingEffortPrompt.exerciseId, pendingEffortPrompt.setId, next)
                },
                testTagPrefix = "rest_bar_effort"
            )
        }
    } else {
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ReorderExerciseRow(
    index: Int,
    totalCount: Int,
    exercise: ActiveExercise,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val context = LocalContext.current
    val done = exercise.sets.count { it.completed }
    val allDone = done == exercise.sets.size && exercise.sets.isNotEmpty()
    var dragAccum by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.borderStrong),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceAlt)
                    .pointerInput(index, totalCount) {
                        detectDragGestures(
                            onDragStart = {
                                dragAccum = 0f
                                FeedbackUtils.triggerLightTick(context)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccum += dragAmount.y
                                val threshold = 72f
                                if (dragAccum < -threshold && index > 0) {
                                    dragAccum = 0f
                                    onMoveUp()
                                } else if (dragAccum > threshold && index < totalCount - 1) {
                                    dragAccum = 0f
                                    onMoveDown()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DragHandle, contentDescription = "Drag to reorder", tint = colors.textSecondary)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1}. ${exercise.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "$done/${exercise.sets.size} sets",
                        style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.textMuted)
                    )
                    if (allDone) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.ok
                        )
                    }
                }
            }

            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = "Move up",
                    tint = if (index > 0) colors.text else colors.textMuted
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = index < totalCount - 1,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    contentDescription = "Move down",
                    tint = if (index < totalCount - 1) colors.text else colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun ActiveExerciseCard(
    index: Int,
    exercise: ActiveExercise,
    prevSession: PreviousExerciseSession?,
    sessionUnit: WeightUnit,
    settings: AppSettings,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenPlatesForExercise: (Double?) -> Unit,
    onSwap: () -> Unit,
    onCycleRest: () -> Unit,
    onEditSetOptions: (Int, WorkoutSet) -> Unit,
    onSetRpe: (setId: String, rpe: Double?) -> Unit,
    onFillFromPrev: (setId: String, weight: Double, reps: Int) -> Unit,
    onUpdateWeight: (setId: String, weight: String) -> Unit,
    onUpdateReps: (setId: String, reps: String) -> Unit,
    onToggleDone: (setId: String, prevSet: CompletedSet?, prevUnit: WeightUnit?) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val context = LocalContext.current
    val doneCount = exercise.sets.count { it.completed }
    val allDone = exercise.sets.isNotEmpty() && doneCount == exercise.sets.size
    val chevronRot by animateFloatAsState(if (isExpanded) 180f else 0f, label = "ex_chev")
    val summary = RepDayRules.formatExerciseTargetSummary(
        exercise.sets.size,
        exercise.repRange,
        exercise.targetRpe,
        settings.effortScale
    )
    val exRest = exercise.restSeconds ?: settings.defaultRestSeconds
    var openEffortSetId by remember(exercise.id) { mutableStateOf<String?>(null) }

    val prevConverted = prevSession != null && prevSession.unit != sessionUnit
    val prevApprox = if (prevConverted) "≈" else ""

    val condensedPrev = remember(prevSession, sessionUnit) {
        val top = prevSession?.sets?.maxByOrNull { it.weight }
        top?.let {
            val wTxt = RepDayRules.formatConvertedWeight(it.weight, prevSession.unit, sessionUnit)
            "Last $prevApprox$wTxt${sessionUnit.label}×${it.reps}"
        }
    }

    val seedWeightForPlates = remember(exercise.sets, prevSession, sessionUnit) {
        exercise.sets.firstOrNull { !it.completed && it.weight.toDoubleOrNull() != null }?.weight?.toDoubleOrNull()
            ?: exercise.sets.lastOrNull { it.weight.toDoubleOrNull() != null }?.weight?.toDoubleOrNull()
            ?: prevSession?.sets?.maxByOrNull { it.weight }?.let {
                RepDayRules.convertWeightForInput(it.weight, prevSession.unit, sessionUnit)
            }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (allDone) 1.5.dp else 1.dp,
            color = if (allDone) colors.ok.copy(alpha = 0.6f) else colors.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_exercise_card_${exercise.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Collapsed / Expandable Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (allDone) colors.okSurface else colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    if (allDone) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.ok, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MonoTabularStyle.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$summary · $doneCount/${exercise.sets.size} done",
                            style = MonoTabularStyle.copy(
                                fontSize = 11.sp,
                                color = if (allDone) colors.ok else colors.textSecondary
                            )
                        )
                        if (condensedPrev != null && !isExpanded) {
                            Text(
                                text = "· $condensedPrev",
                                style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.textMuted),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = colors.textSecondary,
                    modifier = Modifier.rotate(chevronRot)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = colors.border)

                    // Action strip: full previous history line + Plates, Swap, Exercise Rest cycle
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val prevHistoryLine = if (prevSession != null) {
                            val rel = RepDayRules.formatRelativeDate(prevSession.completedAt)
                            val setsTxt = prevSession.sets.joinToString(", ") { s ->
                                val tagCode = RepDayRules.tagShortCode(s.tag, s.workingSubTag)?.let { "$it " } ?: ""
                                val effort = s.rpe?.let {
                                    if (settings.effortScale == EffortScale.RIR) " (${RepDayRules.rpeToRirLabel(it)})"
                                    else " @${RepDayRules.formatRpeNumber(it)}"
                                } ?: ""
                                val wTxt = RepDayRules.formatConvertedWeight(s.weight, prevSession.unit, sessionUnit)
                                "$tagCode$prevApprox$wTxt×${s.reps}$effort"
                            }
                            val loggedSuffix = if (prevConverted) " (logged in ${prevSession.unit.label})" else ""
                            "Last ($rel): $setsTxt ${sessionUnit.label}$loggedSuffix"
                        } else {
                            "First time logging this exercise"
                        }
                        Text(
                            text = prevHistoryLine,
                            style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.textMuted)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SmallActionPill(
                                label = "Plates",
                                icon = Icons.Rounded.FitnessCenter,
                                onClick = { onOpenPlatesForExercise(seedWeightForPlates) }
                            )
                            SmallActionPill(
                                label = "Swap",
                                icon = Icons.Rounded.SwapHoriz,
                                onClick = onSwap
                            )
                            SmallActionPill(
                                label = "Rest ${RepDayRules.formatRestTime(exRest)}",
                                icon = Icons.Rounded.Timer,
                                onClick = onCycleRest
                            )
                        }
                    }

                    // 6-Column Header: Set | Prev | lb/kg | Reps | RPE/RIR | Done
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GridHeaderLabel("Set", Modifier.width(46.dp))
                        GridHeaderLabel("Prev", Modifier.weight(1.1f))
                        GridHeaderLabel(sessionUnit.label, Modifier.weight(1f))
                        GridHeaderLabel("Reps", Modifier.weight(0.9f))
                        GridHeaderLabel(settings.effortScale.label, Modifier.width(44.dp))
                        GridHeaderLabel("Done", Modifier.width(46.dp))
                    }

                    // Set Rows
                    exercise.sets.forEachIndexed { setIdx, set ->
                        val prevSet = prevSession?.sets?.getOrNull(setIdx)
                        val nextIsDrop = RepDayRules.isPrecedingDropSetActive(exercise.sets, setIdx)
                        val setRest = set.restSeconds ?: exRest
                        val rowBg = if (set.completed) colors.okSurface else Color.Transparent
                        val inputBg = if (set.completed) colors.okInput else colors.surfaceAlt
                        val inputTextColor = if (set.completed) colors.okFg else colors.text

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(rowBg)
                                .padding(horizontal = 4.dp, vertical = 5.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 1. Set / Tag Cell (46dp)
                                val tagStyle = set.tag?.let { ThemeEngine.setTagColors(it, set.workingSubTag, colors) }
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tagStyle?.bg ?: colors.surfaceAlt)
                                        .border(
                                            1.dp,
                                            tagStyle?.border ?: colors.border,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onEditSetOptions(setIdx, set) }
                                        .testTag("set_tag_btn_${set.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = RepDayRules.tagShortCode(set.tag, set.workingSubTag) ?: "${setIdx + 1}",
                                        style = MonoTabularStyle.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tagStyle?.text ?: colors.textSecondary
                                        )
                                    )
                                }

                                // 2. Prev Cell (tappable auto-fill, converted to session unit)
                                val prevUnit = prevSession?.unit ?: sessionUnit
                                val prevDisplayWeight = prevSet?.let {
                                    RepDayRules.formatConvertedWeight(it.weight, prevUnit, sessionUnit)
                                }
                                val prevInputWeight = prevSet?.let {
                                    RepDayRules.convertWeightForInput(it.weight, prevUnit, sessionUnit)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .heightIn(min = 46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = prevSet != null) {
                                            if (prevSet != null && prevInputWeight != null) {
                                                onFillFromPrev(set.id, prevInputWeight, prevSet.reps)
                                            }
                                        }
                                        .padding(horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (prevSet != null && prevDisplayWeight != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$prevApprox$prevDisplayWeight×${prevSet.reps}",
                                                style = MonoTabularStyle.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.textSecondary
                                                ),
                                                maxLines = 1
                                            )
                                            val prevMeta = listOfNotNull(
                                                RepDayRules.tagShortCode(prevSet.tag, prevSet.workingSubTag),
                                                prevSet.rpe?.let {
                                                    if (settings.effortScale == EffortScale.RIR) {
                                                        RepDayRules.rpeToRirLabel(it, compact = true) + " RIR"
                                                    } else {
                                                        "@${RepDayRules.formatRpeNumber(it)}"
                                                    }
                                                }
                                            ).joinToString(" ")
                                            if (prevMeta.isNotEmpty()) {
                                                Text(
                                                    text = prevMeta,
                                                    style = MonoTabularStyle.copy(fontSize = 9.sp, color = colors.textMuted),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "—",
                                            style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.textMuted)
                                        )
                                    }
                                }

                                // 3. Weight Input
                                CompactNumericCell(
                                    value = set.weight,
                                    placeholder = prevInputWeight?.let { RepDayRules.formatNumber(it) } ?: "0",
                                    isDecimal = true,
                                    bg = inputBg,
                                    textColor = inputTextColor,
                                    placeholderColor = colors.textMuted,
                                    borderColor = if (set.completed) colors.ok.copy(alpha = 0.45f) else colors.border,
                                    onValueChange = { onUpdateWeight(set.id, it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("set_weight_${set.id}")
                                )

                                // 4. Reps Input
                                val repsPlaceholder = when {
                                    prevSet != null -> prevSet.reps.toString()
                                    !set.targetRepRange.isNullOrBlank() -> {
                                        RepDayRules.parseRepBounds(set.targetRepRange)?.first?.toString() ?: "Reps"
                                    }
                                    else -> "Reps"
                                }
                                CompactNumericCell(
                                    value = set.reps,
                                    placeholder = repsPlaceholder,
                                    isDecimal = false,
                                    bg = inputBg,
                                    textColor = inputTextColor,
                                    placeholderColor = colors.textMuted,
                                    borderColor = if (set.completed) colors.ok.copy(alpha = 0.45f) else colors.border,
                                    onValueChange = { onUpdateReps(set.id, it) },
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(46.dp)
                                        .testTag("set_reps_${set.id}")
                                )

                                // 5. Effort Cell (RPE or RIR) — opens inline one-tap chip strip
                                val isEffortOpen = openEffortSetId == set.id
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (set.rpe != null || isEffortOpen) colors.accentSoft else colors.surfaceAlt)
                                        .border(
                                            1.dp,
                                            if (set.rpe != null || isEffortOpen) colors.accent else colors.border,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            openEffortSetId = if (isEffortOpen) null else set.id
                                        }
                                        .testTag("set_rpe_${set.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when {
                                            set.rpe != null -> RepDayRules.formatEffortValue(set.rpe, settings.effortScale, compact = true)
                                            !set.targetRpe.isNullOrBlank() -> RepDayRules.formatEffortTarget(set.targetRpe, settings.effortScale, compact = true) ?: "—"
                                            else -> "—"
                                        },
                                        style = MonoTabularStyle.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (set.rpe != null) FontWeight.Bold else FontWeight.Medium,
                                            color = if (set.rpe != null) colors.accentSoftFg else colors.textMuted
                                        ),
                                        maxLines = 1
                                    )
                                }

                                // 6. Done Checkbox (46dp)
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (set.completed) colors.ok else colors.surfaceAlt)
                                        .border(
                                            1.dp,
                                            if (set.completed) colors.ok else colors.borderStrong,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onToggleDone(set.id, prevSet, prevSession?.unit) }
                                        .testTag("set_done_${set.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = if (set.completed) "Mark set incomplete" else "Mark set complete",
                                        tint = if (set.completed) Color(0xFF0E1013) else colors.textMuted,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Inline one-tap effort chips beneath this set row
                            AnimatedVisibility(visible = openEffortSetId == set.id) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceAlt)
                                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val targetLabel = RepDayRules.formatEffortTarget(set.targetRpe, settings.effortScale)
                                        Text(
                                            text = if (targetLabel != null) {
                                                "${settings.effortScale.label} · Target $targetLabel"
                                            } else {
                                                "Log ${settings.effortScale.label}"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.textSecondary
                                        )
                                        Text(
                                            text = "Tap again to clear",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textMuted
                                        )
                                    }
                                    EffortChipRow(
                                        scale = settings.effortScale,
                                        selectedRpe = set.rpe,
                                        targetRpe = set.targetRpe,
                                        onSelectRpe = { next ->
                                            FeedbackUtils.triggerLightTick(context)
                                            onSetRpe(set.id, next)
                                        },
                                        showDescription = true,
                                        testTagPrefix = "inline_effort_${set.id}"
                                    )
                                }
                            }

                            // Quiet Unboxed Set Caption
                            val captionParts = ArrayList<String>()
                            RepDayRules.tagFullLabel(set.tag, set.workingSubTag)?.let { captionParts.add(it) }
                            val isRpeOnly = set.targetRepRange.isNullOrBlank() && !set.targetRpe.isNullOrBlank()
                            if (!set.targetRepRange.isNullOrBlank() && set.targetRepRange != exercise.repRange) {
                                captionParts.add("Target ${set.targetRepRange} reps")
                            }
                            if (!set.targetRpe.isNullOrBlank()) {
                                val targetStr = RepDayRules.formatEffortTarget(set.targetRpe, settings.effortScale)
                                if (targetStr != null) {
                                    captionParts.add(if (isRpeOnly) "Lift to $targetStr" else "Target $targetStr")
                                }
                            }
                            if (set.rpe != null) {
                                captionParts.add("Logged ${RepDayRules.formatEffortValue(set.rpe, settings.effortScale)}")
                            }
                            if (nextIsDrop) {
                                captionParts.add("No rest · Drop set next")
                            } else if (setRest != exRest) {
                                captionParts.add("Rest ${RepDayRules.formatRestTime(setRest)}")
                            }

                            if (captionParts.isNotEmpty()) {
                                Text(
                                    text = captionParts.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (nextIsDrop) colors.dropOrange else colors.textMuted,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }

                    // Per-Exercise Footer: + Add Set & - Remove Set
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAddSet,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.borderStrong),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Set", style = MaterialTheme.typography.labelMedium, color = colors.text)
                        }
                        if (exercise.sets.size > 1) {
                            OutlinedButton(
                                onClick = onRemoveSet,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.border),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Icon(Icons.Rounded.Remove, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Remove Set", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridHeaderLabel(text: String, modifier: Modifier = Modifier) {
    val colors = LocalRepDayColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
private fun CompactNumericCell(
    value: String,
    placeholder: String,
    isDecimal: Boolean,
    bg: Color,
    textColor: Color,
    placeholderColor: Color,
    borderColor: Color,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MonoTabularStyle.copy(fontSize = 14.sp, color = placeholderColor),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MonoTabularStyle.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SmallActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = LocalRepDayColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.surfaceAlt,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.heightIn(min = 36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
        }
    }
}

@Composable
private fun ActiveWorkoutSidebar(
    workout: ActiveWorkout,
    elapsedSecondsFlow: StateFlow<Long>,
    completedSets: Int,
    totalSets: Int,
    liveVolume: Int,
    unitLabel: String,
    onJumpToExercise: (Int, String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRepDayColors.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Session Progress", style = MaterialTheme.typography.titleMedium, color = colors.text)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SidebarElapsedTile(elapsedSecondsFlow = elapsedSecondsFlow)
                Column {
                    Text("Sets", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(
                        "$completedSets/$totalSets",
                        style = MonoTabularStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.ok)
                    )
                }
                Column {
                    Text("Volume", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(
                        "${RepDayRules.formatVolume(liveVolume)} $unitLabel",
                        style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                }
            }
            HorizontalDivider(color = colors.border)
            Text("Exercises", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            workout.exercises.forEachIndexed { idx, ex ->
                val done = ex.sets.count { it.completed }
                val allDone = done == ex.sets.size && ex.sets.isNotEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onJumpToExercise(idx, ex.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${idx + 1}. ${ex.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allDone) colors.ok else colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$done/${ex.sets.size}",
                        style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.textMuted)
                    )
                }
            }
            HorizontalDivider(color = colors.border)
            OutlinedTextField(
                value = workout.notes,
                onValueChange = onUpdateNotes,
                label = { Text("Session Notes") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimerBar(
    restTimer: RestTimerUiState,
    soundEnabled: Boolean,
    effortScale: EffortScale,
    pendingEffortPrompt: EffortPromptTarget?,
    onToggleSound: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onAdjust: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSelectEffort: (exerciseId: String, setId: String, rpe: Double?) -> Unit,
    onDismissEffortPrompt: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val context = LocalContext.current
    var showCustomize by remember { mutableStateOf(false) }
    val progress = if (restTimer.isComplete || restTimer.totalSeconds <= 0) {
        1f
    } else {
        (1f - restTimer.remainingSeconds.toFloat() / restTimer.totalSeconds.toFloat()).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated)
            .navigationBarsPadding()
    ) {
        AnimatedVisibility(visible = showCustomize) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(1.dp, colors.border)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Customize Rest Timer", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = onToggleSound, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = if (soundEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                                contentDescription = if (soundEnabled) "Mute chime" else "Unmute chime",
                                tint = if (soundEnabled) colors.accent else colors.textMuted
                            )
                        }
                        IconButton(onClick = { showCustomize = false }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RepDayRules.REST_PRESETS_SECONDS.forEach { preset ->
                        Surface(
                            onClick = {
                                onSelectPreset(preset)
                                showCustomize = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surfaceAlt,
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text(
                                text = RepDayRules.formatRestTime(preset),
                                style = MonoTabularStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.text),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAdjust(-15) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-15s", style = MonoTabularStyle.copy(fontWeight = FontWeight.Bold), color = colors.text)
                    }
                    OutlinedButton(
                        onClick = { onAdjust(15) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+15s", style = MonoTabularStyle.copy(fontWeight = FontWeight.Bold), color = colors.text)
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            color = if (restTimer.isComplete) colors.ok else colors.accent,
            trackColor = colors.border,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
        )

        AnimatedVisibility(visible = pendingEffortPrompt != null) {
            pendingEffortPrompt?.let { prompt ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "How hard? Set ${prompt.setNumber} · ${prompt.exerciseName}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissEffortPrompt,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("dismiss_effort_prompt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss effort prompt",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    EffortChipRow(
                        scale = effortScale,
                        selectedRpe = prompt.currentRpe,
                        targetRpe = prompt.targetRpe,
                        onSelectRpe = { next ->
                            FeedbackUtils.triggerLightTick(context)
                            onSelectEffort(prompt.exerciseId, prompt.setId, next)
                        },
                        testTagPrefix = "rest_bar_effort"
                    )
                    HorizontalDivider(color = colors.border)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showCustomize = !showCustomize }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (restTimer.isComplete) colors.okSurface else colors.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (restTimer.isComplete) Icons.Rounded.Check else Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = if (restTimer.isComplete) colors.ok else colors.accent
                    )
                }
                Column {
                    Text(
                        text = if (restTimer.isComplete) "Done" else RepDayRules.formatRestTime(restTimer.remainingSeconds),
                        style = MonoTabularStyle.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (restTimer.isComplete) colors.ok else colors.text
                        )
                    )
                    Text(
                        text = if (restTimer.isComplete) {
                            "Ready for next set"
                        } else if (restTimer.exerciseName.isNotBlank()) {
                            "Rest · ${restTimer.exerciseName}"
                        } else {
                            "Rest timer · Tap to customize"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = { onAdjust(30) },
                shape = CircleShape,
                border = BorderStroke(1.dp, colors.borderStrong),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text("+30s", style = MonoTabularStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text)
            }

            Button(
                onClick = onDismiss,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (restTimer.isComplete) colors.ok else colors.surfaceAlt,
                    contentColor = if (restTimer.isComplete) Color(0xFF0E1013) else colors.text
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text(if (restTimer.isComplete) "Dismiss" else "Skip", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddExerciseMidWorkoutSheet(
    exerciseLibrary: List<String>,
    customExercises: List<String>,
    currentExerciseNames: Set<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val suggestions = remember(query, exerciseLibrary, currentExerciseNames) {
        RepDayRules.searchExercises(query, exerciseLibrary, currentExerciseNames, limit = 10, omitExactMatch = true)
    }
    val isBrandNew = query.trim().isNotEmpty() && !RepDayRules.isKnownInLibrary(query, customExercises)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Add Exercise to Workout", style = MaterialTheme.typography.titleLarge, color = colors.text)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Exercise name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.borderStrong
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onAdd(query) },
                    enabled = query.trim().isNotEmpty(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    ),
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text("Add")
                }
            }
            if (isBrandNew) {
                Text(
                    text = "New exercise — will be saved to your library",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accentSoftFg
                )
            }
            if (suggestions.isNotEmpty()) {
                Text("Quick pick", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { name ->
                        Surface(
                            onClick = { onAdd(name) },
                            shape = CircleShape,
                            color = colors.surfaceAlt,
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.text,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SwapExerciseSheet(
    exercise: ActiveExercise,
    canRemove: Boolean,
    exerciseLibrary: List<String>,
    customExercises: List<String>,
    currentExerciseNames: Set<String>,
    onSwap: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val suggestions = remember(query, exercise.name, exerciseLibrary, currentExerciseNames) {
        RepDayRules.suggestSwapAlternatives(exercise.name, query, exerciseLibrary, currentExerciseNames, limit = 10)
    }
    val isBrandNew = query.trim().isNotEmpty() && !RepDayRules.isKnownInLibrary(query, customExercises)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text("Swap \"${exercise.name}\"", style = MaterialTheme.typography.titleLarge, color = colors.text)
                Text(
                    text = "Choose a replacement movement for this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Replacement movement") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.borderStrong
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onSwap(query) },
                    enabled = query.trim().isNotEmpty(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    ),
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text("Swap")
                }
            }
            if (isBrandNew) {
                Text(
                    text = "New exercise — will be saved to your library",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accentSoftFg
                )
            }
            if (suggestions.isNotEmpty()) {
                Text("Suggested alternatives", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { alt ->
                        Surface(
                            onClick = { onSwap(alt) },
                            shape = CircleShape,
                            color = colors.surfaceAlt,
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text(
                                text = alt,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.text,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            if (canRemove) {
                HorizontalDivider(color = colors.border)
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove Exercise from Session", color = colors.danger, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SidebarElapsedTile(elapsedSecondsFlow: StateFlow<Long>) {
    val colors = LocalRepDayColors.current
    val elapsedSeconds by elapsedSecondsFlow.collectAsStateWithLifecycle()
    Column {
        Text("Elapsed", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        Text(
            RepDayRules.formatElapsed(elapsedSeconds),
            style = MonoTabularStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text)
        )
    }
}

@Composable
private fun FinishSheetElapsedTile(elapsedSecondsFlow: StateFlow<Long>) {
    val colors = LocalRepDayColors.current
    val elapsedSeconds by elapsedSecondsFlow.collectAsStateWithLifecycle()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            RepDayRules.formatElapsed(elapsedSeconds),
            style = MonoTabularStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.text)
        )
        Text("Duration", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkoutNotesSheet(
    notes: String,
    onNotesChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Workout Notes", style = MaterialTheme.typography.titleLarge, color = colors.text)
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("workout_notes_done_button")
                ) {
                    Text("Done", color = colors.accent, style = MaterialTheme.typography.labelLarge)
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                placeholder = { Text("Cues, energy, soreness, tweaks…") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.borderStrong,
                    focusedContainerColor = colors.surfaceAlt,
                    unfocusedContainerColor = colors.surfaceAlt
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workout_notes_sheet_input")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishWorkoutSheet(
    isLiveWorkout: Boolean,
    defaultLiveWorkoutName: String,
    elapsedSecondsFlow: StateFlow<Long>,
    completedSets: Int,
    uncheckedSets: Int,
    totalVolume: Int,
    unitLabel: String,
    initialNotes: String,
    onKeepLifting: () -> Unit,
    onSave: (notes: String, saveAsRoutine: Boolean, newRoutineName: String) -> Unit,
    onRequestDiscard: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }
    var saveAsRoutine by remember { mutableStateOf(false) }
    var newRoutineName by remember { mutableStateOf("") }

    val canSave = completedSets > 0 && (!isLiveWorkout || !saveAsRoutine || newRoutineName.trim().isNotEmpty())

    ModalBottomSheet(
        onDismissRequest = onKeepLifting,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Finish Workout", style = MaterialTheme.typography.headlineSmall, color = colors.text)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surfaceAlt,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FinishSheetElapsedTile(elapsedSecondsFlow = elapsedSecondsFlow)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            completedSets.toString(),
                            style = MonoTabularStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.ok)
                        )
                        Text("Sets Logged", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${RepDayRules.formatVolume(totalVolume)} $unitLabel",
                            style = MonoTabularStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        )
                        Text("Volume", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    }
                }
            }

            if (completedSets > 0 && uncheckedSets > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.dropOrangeBg,
                    border = BorderStroke(1.dp, colors.dropOrangeBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("finish_unchecked_sets_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = colors.dropOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$uncheckedSets unchecked ${if (uncheckedSets == 1) "set" else "sets"} won't be saved",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.dropOrange
                        )
                    }
                }
            }

            if (isLiveWorkout) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (saveAsRoutine) colors.accentSoft.copy(alpha = 0.55f) else colors.surfaceAlt,
                    border = BorderStroke(
                        1.dp,
                        if (saveAsRoutine) colors.accent else colors.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { saveAsRoutine = !saveAsRoutine },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Save as a Routine",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.text
                                )
                                Text(
                                    text = if (saveAsRoutine) {
                                        "Creates a reusable routine from your logged sets"
                                    } else {
                                        "Otherwise logs as \"$defaultLiveWorkoutName\". You can always add it as a routine later from History."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = saveAsRoutine,
                                onCheckedChange = { saveAsRoutine = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.accentFg,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.textSecondary,
                                    uncheckedTrackColor = colors.surface,
                                    uncheckedBorderColor = colors.borderStrong
                                ),
                                modifier = Modifier.testTag("live_save_as_routine_switch")
                            )
                        }

                        AnimatedVisibility(visible = saveAsRoutine) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = newRoutineName,
                                    onValueChange = { newRoutineName = it },
                                    label = { Text("Routine Name *") },
                                    placeholder = { Text("e.g. Upper Push, Full Body") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.borderStrong
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("live_finish_routine_name_input")
                                )
                                if (newRoutineName.trim().isEmpty()) {
                                    Text(
                                        text = "Enter a name to save this live workout as a routine.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Workout Notes (optional)") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.borderStrong
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onKeepLifting,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Keep Lifting", color = colors.text)
                }
                Button(
                    onClick = { onSave(notes, isLiveWorkout && saveAsRoutine, newRoutineName) },
                    enabled = canSave,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    ),
                    modifier = Modifier
                        .weight(1.15f)
                        .heightIn(min = 48.dp)
                        .testTag("confirm_save_workout_button")
                ) {
                    Text(
                        text = if (isLiveWorkout && saveAsRoutine) "Save & Add Routine" else "Save Workout",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (completedSets == 0) {
                Text(
                    text = "Check off at least 1 set to save this workout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }

            TextButton(
                onClick = onRequestDiscard,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
            ) {
                Text("Discard Workout", color = colors.danger, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
