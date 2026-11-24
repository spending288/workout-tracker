package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.CompletedWorkout
import com.example.data.model.EffortScale
import com.example.data.model.Routine
import com.example.data.model.RoutineSortField
import com.example.data.model.SortDirection
import com.example.domain.RepDayRules
import com.example.ui.components.RepDayConfirmDialog
import com.example.ui.components.SetTagBadge
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle

@Composable
fun RoutinesScreen(
    routines: List<Routine>,
    history: List<CompletedWorkout>,
    settings: AppSettings,
    isWideLayout: Boolean,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onOpenSettings: () -> Unit,
    onNewRoutine: () -> Unit,
    onLoadSamples: () -> Unit,
    onStartLiveWorkout: () -> Unit,
    onStartRoutine: (Routine) -> Unit,
    onEditRoutine: (Routine) -> Unit,
    onDuplicateRoutine: (Routine) -> Unit,
    onDeleteRoutine: (Routine) -> Unit,
    onViewRoutineHistory: (Routine) -> Unit,
    onSortFieldClick: (RoutineSortField) -> Unit,
    onToggleSortDirection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRepDayColors.current
    val sortedRoutines = remember(routines, history, settings.routineSort) {
        RepDayRules.sortRoutines(routines, history, settings.routineSort)
    }
    var pendingDelete by remember { mutableStateOf<Routine?>(null) }

    pendingDelete?.let { target ->
        RepDayConfirmDialog(
            title = "Delete \"${target.name}\"?",
            body = "Past completed workout history will remain intact. Any training plan slots linked to this routine will keep their labels as planned placeholders.",
            confirmLabel = "Delete Routine",
            onConfirm = {
                onDeleteRoutine(target)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 720.dp) 2 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = bottomContentPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(columns) }) {
                RoutinesHeader(
                    routineCount = routines.size,
                    settings = settings,
                    showCreateButton = isWideLayout,
                    onOpenSettings = onOpenSettings,
                    onNewRoutine = onNewRoutine
                )
            }

            item(span = { GridItemSpan(columns) }) {
                LiveWorkoutQuickStartCard(onStartLiveWorkout = onStartLiveWorkout)
            }

            if (routines.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    RoutineSortBar(
                        field = settings.routineSort.field,
                        direction = settings.routineSort.direction,
                        onSelectField = onSortFieldClick,
                        onToggleDirection = onToggleSortDirection
                    )
                }
            }

            if (sortedRoutines.isEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    EmptyRoutinesCard(
                        onNewRoutine = onNewRoutine,
                        onLoadSamples = onLoadSamples
                    )
                }
            } else {
                items(sortedRoutines, key = { it.id }) { routine ->
                    val (lastCompletedIso, completedCount) = remember(routine, history) {
                        var count = 0
                        var latestMs = Long.MIN_VALUE
                        var latestIso: String? = null
                        for (w in history) {
                            if (RepDayRules.matchesRoutineHistory(w, routine)) {
                                count++
                                if (w.completedAtMs > latestMs) {
                                    latestMs = w.completedAtMs
                                    latestIso = w.completedAt
                                }
                            }
                        }
                        latestIso to count
                    }
                    RoutineCard(
                        routine = routine,
                        lastCompletedIso = lastCompletedIso,
                        completedCount = completedCount,
                        defaultRestSeconds = settings.defaultRestSeconds,
                        effortScale = settings.effortScale,
                        onStart = { onStartRoutine(routine) },
                        onHistory = { onViewRoutineHistory(routine) },
                        onDuplicate = { onDuplicateRoutine(routine) },
                        onEdit = { onEditRoutine(routine) },
                        onDelete = { pendingDelete = routine }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutinesHeader(
    routineCount: Int,
    settings: AppSettings,
    showCreateButton: Boolean,
    onOpenSettings: () -> Unit,
    onNewRoutine: () -> Unit
) {
    val colors = LocalRepDayColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Routines",
                style = MaterialTheme.typography.displaySmall,
                color = colors.text
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$routineCount saved · ${RepDayRules.formatRestTime(settings.defaultRestSeconds)} default rest · ${settings.unit.label}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showCreateButton) {
                Button(
                    onClick = onNewRoutine,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    ),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("tablet_new_routine_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Routine", style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, CircleShape)
                    .testTag("routines_preferences_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Preferences",
                    tint = colors.text
                )
            }
        }
    }
}

@Composable
private fun LiveWorkoutQuickStartCard(
    onStartLiveWorkout: () -> Unit
) {
    val colors = LocalRepDayColors.current
    Surface(
        onClick = onStartLiveWorkout,
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("start_live_workout_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FlashOn,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Live Workout",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Text(
                    text = "Start empty and add exercises as you lift",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            Button(
                onClick = onStartLiveWorkout,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.accentFg
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("start_live_workout_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Start", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun RoutineSortBar(
    field: RoutineSortField,
    direction: SortDirection,
    onSelectField: (RoutineSortField) -> Unit,
    onToggleDirection: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val dirLabel = when (field) {
        RoutineSortField.NAME -> if (direction == SortDirection.ASC) "A–Z" else "Z–A"
        RoutineSortField.CREATED_AT -> if (direction == SortDirection.DESC) "Newest" else "Oldest"
        RoutineSortField.LAST_COMPLETED_AT -> if (direction == SortDirection.DESC) "Most recent" else "Least recent"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            RoutineSortField.entries.forEach { f ->
                val selected = field == f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) colors.accentSoft else Color.Transparent)
                        .clickable { onSelectField(f) }
                        .testTag("sort_field_${f.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = f.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) colors.accentSoftFg else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            onClick = onToggleDirection,
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .heightIn(min = 44.dp)
                .testTag("sort_direction_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (direction == SortDirection.ASC) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = "Toggle sort direction",
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = dirLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyRoutinesCard(
    onNewRoutine: () -> Unit,
    onLoadSamples: () -> Unit
) {
    val colors = LocalRepDayColors.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
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
                    imageVector = Icons.Rounded.FitnessCenter,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "No routines yet",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text
            )
            Text(
                text = "Build your first reusable workout template with custom sets, RPE targets, and drop-set rest rules.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
            Button(
                onClick = onNewRoutine,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.accentFg
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("empty_create_routine_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create Routine", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onLoadSamples,
                shape = CircleShape,
                border = BorderStroke(1.dp, colors.borderStrong),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("routines_load_samples_button")
            ) {
                Text("Load sample routines & plan", style = MaterialTheme.typography.labelLarge, color = colors.text)
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    lastCompletedIso: String?,
    completedCount: Int,
    defaultRestSeconds: Int,
    effortScale: EffortScale = EffortScale.RPE,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalRepDayColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    var exercisesExpanded by remember { mutableStateOf(false) }
    val expandedExerciseIds = remember { mutableStateMapOf<String, Boolean>() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (exercisesExpanded) 180f else 0f,
        label = "routine_chevron"
    )

    val totalSets = remember(routine) {
        routine.exercises.sumOf { it.plannedSets?.size ?: it.targetSets }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("routine_card_${routine.id}")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Name + compact Start accent button + ⋮ overflow menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onStart,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("start_routine_${routine.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Start", style = MaterialTheme.typography.labelLarge)
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, CircleShape)
                            .testTag("routine_more_${routine.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More actions for ${routine.name}",
                            tint = colors.textSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = colors.surfaceElevated
                    ) {
                        DropdownMenuItem(
                            text = { Text("History", color = colors.text) },
                            leadingIcon = { Icon(Icons.Rounded.History, null, tint = colors.textSecondary) },
                            onClick = {
                                menuExpanded = false
                                onHistory()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate", color = colors.text) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = colors.textSecondary) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit", color = colors.text) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = colors.textSecondary) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        HorizontalDivider(color = colors.border)
                        DropdownMenuItem(
                            text = { Text("Delete", color = colors.danger) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = colors.danger) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (!routine.focus.isNullOrBlank()) {
                Text(
                    text = routine.focus,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentSoftFg
                )
            }

            if (!routine.description.isNullOrBlank()) {
                Text(
                    text = routine.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }

            // Metadata strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${routine.exercises.size} exercises · $totalSets sets · ",
                    style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.textMuted)
                )
                if (lastCompletedIso != null && completedCount > 0) {
                    Text(
                        text = "Last done ${RepDayRules.formatRelativeDate(lastCompletedIso)} ($completedCount)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.accentSoftFg,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onHistory)
                            .padding(vertical = 2.dp)
                    )
                } else {
                    Text(
                        text = "Not logged yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }

            // Collapsible Exercises Toggle
            HorizontalDivider(color = colors.border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { exercisesExpanded = !exercisesExpanded }
                    .padding(vertical = 6.dp)
                    .testTag("toggle_routine_exercises_${routine.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (exercisesExpanded) {
                        "Hide exercises (${routine.exercises.size})"
                    } else {
                        "Show exercises (${routine.exercises.size})"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }

            AnimatedVisibility(visible = exercisesExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    routine.exercises.forEachIndexed { idx, ex ->
                        val planned = remember(ex, defaultRestSeconds) {
                            RepDayRules.expandPlannedSets(ex, defaultRestSeconds)
                        }
                        val tagSummary = remember(ex.plannedSets) {
                            RepDayRules.summarizeSetTags(ex.plannedSets)
                        }
                        val targetSummary = remember(ex, effortScale) {
                            RepDayRules.formatExerciseTargetSummary(
                                planned.size,
                                ex.repRange,
                                ex.targetRpe,
                                effortScale
                            )
                        }
                        val isSetPreviewOpen = expandedExerciseIds[ex.id] == true

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceAlt)
                                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                .clickable { expandedExerciseIds[ex.id] = !isSetPreviewOpen }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        style = MonoTabularStyle.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textMuted
                                        )
                                    )
                                    Column {
                                        Text(
                                            text = ex.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.text
                                        )
                                        if (tagSummary.isNotEmpty()) {
                                            Text(
                                                text = tagSummary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textMuted
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = targetSummary,
                                    style = MonoTabularStyle.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.accentSoftFg
                                    )
                                )
                            }

                            AnimatedVisibility(visible = isSetPreviewOpen) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, start = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    planned.forEachIndexed { setIdx, ps ->
                                        val beforeDrop = RepDayRules.isPrecedingDropSet(planned, setIdx)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Set ${setIdx + 1}",
                                                    style = MonoTabularStyle.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colors.textSecondary
                                                    )
                                                )
                                                if (ps.tag != null) {
                                                    SetTagBadge(
                                                        tag = ps.tag,
                                                        subTag = ps.workingSubTag,
                                                        useFullLabel = true
                                                    )
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = RepDayRules.formatSetTargetLabel(ps.repRange, ps.targetRpe, effortScale),
                                                    style = MonoTabularStyle.copy(
                                                        fontSize = 11.sp,
                                                        color = colors.textSecondary
                                                    )
                                                )
                                                if (beforeDrop || ps.restSeconds == 0) {
                                                    Text(
                                                        text = "No rest",
                                                        style = MonoTabularStyle.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = colors.dropOrange
                                                        )
                                                    )
                                                } else {
                                                    Text(
                                                        text = "Rest ${RepDayRules.formatRestTime(ps.restSeconds)}",
                                                        style = MonoTabularStyle.copy(
                                                            fontSize = 11.sp,
                                                            color = colors.textMuted
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
