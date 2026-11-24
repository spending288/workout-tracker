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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CompletedWorkout
import com.example.data.model.EffortScale
import com.example.data.model.Routine
import com.example.data.model.WeightUnit
import com.example.domain.RepDayRules
import com.example.ui.components.RepDayConfirmDialog
import com.example.ui.components.SetTagBadge
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.viewmodel.HistoryRoutineFilter
import kotlin.math.roundToInt

private data class FilterChipEntry(
    val routineId: String?,
    val name: String,
    val count: Int,
    val isLiveWorkoutsOnly: Boolean = false
)

@Composable
fun HistoryScreen(
    history: List<CompletedWorkout>,
    routines: List<Routine>,
    unit: WeightUnit,
    effortScale: EffortScale = EffortScale.RPE,
    activeFilter: HistoryRoutineFilter?,
    showSampleHistoryBanner: Boolean,
    sampleHistoryCount: Int,
    bottomContentPadding: Dp,
    onSelectFilter: (HistoryRoutineFilter?) -> Unit,
    onOpenSettings: () -> Unit,
    onEditWorkout: (CompletedWorkout) -> Unit,
    onRepeatWorkout: (CompletedWorkout) -> Unit,
    onSaveAsRoutine: (CompletedWorkout) -> Unit,
    onStartLiveWorkout: () -> Unit,
    onStartRoutine: (Routine) -> Unit,
    onDeleteWorkout: (CompletedWorkout) -> Unit,
    onRemoveSampleHistory: () -> Unit,
    onDismissSampleHistoryBanner: () -> Unit,
    onGoToRoutines: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRepDayColors.current
    var pendingDelete by remember { mutableStateOf<CompletedWorkout?>(null) }

    val sortedAllHistory = remember(history) {
        history.sortedByDescending { it.completedAtMs }
    }

    val filterChips = remember(routines, sortedAllHistory) {
        val entries = ArrayList<FilterChipEntry>()
        val seenKeys = HashSet<String>()
        val savedRoutineIds = routines.map { it.id }.toSet()
        routines.sortedBy { it.name.lowercase() }.forEach { r ->
            val count = sortedAllHistory.count { RepDayRules.matchesRoutineHistory(it, r) }
            entries.add(FilterChipEntry(r.id, r.name, count))
            seenKeys.add(RepDayRules.exerciseKey(r.name))
        }
        val liveCount = sortedAllHistory.count { w ->
            w.routineId == null && RepDayRules.isLiveWorkoutName(w.routineName)
        }
        if (liveCount > 0) {
            entries.add(FilterChipEntry(null, "Live Workouts", liveCount, isLiveWorkoutsOnly = true))
        }
        sortedAllHistory.forEach { w ->
            if (w.routineId != null && w.routineId in savedRoutineIds) return@forEach
            if (w.routineId == null && RepDayRules.isLiveWorkoutName(w.routineName)) return@forEach
            val key = RepDayRules.exerciseKey(w.routineName)
            if (seenKeys.add(key)) {
                val count = sortedAllHistory.count { RepDayRules.exerciseKey(it.routineName) == key }
                entries.add(FilterChipEntry(null, w.routineName, count))
            }
        }
        entries
    }

    val filteredHistory = remember(sortedAllHistory, activeFilter) {
        when {
            activeFilter == null -> sortedAllHistory
            activeFilter.isLiveWorkoutsOnly -> sortedAllHistory.filter {
                it.routineId == null && RepDayRules.isLiveWorkoutName(it.routineName)
            }
            else -> sortedAllHistory.filter {
                RepDayRules.matchesRoutineFilter(it, activeFilter.routineId, activeFilter.routineName)
            }
        }
    }

    val totalVolume = remember(filteredHistory, unit) {
        RepDayRules.computeTotalHistoryVolume(filteredHistory, unit)
    }
    val hasConvertedTotal = remember(filteredHistory, unit) {
        filteredHistory.any { it.unit != unit }
    }
    val avgMinutes = remember(filteredHistory) {
        if (filteredHistory.isEmpty()) 0
        else (filteredHistory.map { it.durationSeconds }.average() / 60.0).roundToInt()
    }

    val expandedMap = remember(activeFilter) { mutableStateMapOf<String, Boolean>() }

    pendingDelete?.let { target ->
        RepDayConfirmDialog(
            title = "Delete workout log?",
            body = "Remove \"${target.routineName}\" from ${RepDayRules.formatCompletedDateTime(target.completedAt)}? This cannot be undone.",
            confirmLabel = "Delete Log",
            onConfirm = {
                onDeleteWorkout(target)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.text
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (activeFilter != null) {
                            "Filtered: ${activeFilter.routineName}"
                        } else {
                            "${sortedAllHistory.size} logged sessions"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeFilter != null) {
                        Surface(
                            onClick = { onSelectFilter(null) },
                            shape = CircleShape,
                            color = colors.accentSoft,
                            border = BorderStroke(1.dp, colors.accent),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("history_clear_filter")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Close, null, tint = colors.accentSoftFg, modifier = Modifier.size(15.dp))
                                Text("Clear Filter", style = MaterialTheme.typography.labelMedium, color = colors.accentSoftFg)
                            }
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .border(1.dp, colors.border, CircleShape)
                            .testTag("history_preferences_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Preferences", tint = colors.text)
                    }
                }
            }
        }

        // One-time dismissible banner for existing installs that still have the 2 seeded sample workouts
        if (showSampleHistoryBanner) {
            item(key = "sample_history_banner") {
                SampleHistoryCleanupBanner(
                    sampleCount = sampleHistoryCount,
                    onRemove = onRemoveSampleHistory,
                    onDismiss = onDismissSampleHistoryBanner
                )
            }
        }

        // Horizontal Routine Filter Chips
        if (filterChips.isNotEmpty() || sortedAllHistory.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        val allSelected = activeFilter == null
                        HistoryFilterPill(
                            label = "All Workouts (${sortedAllHistory.size})",
                            selected = allSelected,
                            onClick = { onSelectFilter(null) }
                        )
                    }
                    items(filterChips, key = { "${if (it.isLiveWorkoutsOnly) "live" else it.routineId ?: "hist"}_${it.name}" }) { entry ->
                        val isSelected = activeFilter != null && (
                            (entry.isLiveWorkoutsOnly && activeFilter.isLiveWorkoutsOnly) ||
                                (!entry.isLiveWorkoutsOnly && !activeFilter.isLiveWorkoutsOnly && (
                                    (entry.routineId != null && activeFilter.routineId == entry.routineId) ||
                                        RepDayRules.exerciseKey(activeFilter.routineName) == RepDayRules.exerciseKey(entry.name)
                                    ))
                            )
                        HistoryFilterPill(
                            label = "${entry.name} (${entry.count})",
                            selected = isSelected,
                            onClick = {
                                if (isSelected) onSelectFilter(null)
                                else onSelectFilter(
                                    HistoryRoutineFilter(
                                        routineId = entry.routineId,
                                        routineName = entry.name,
                                        isLiveWorkoutsOnly = entry.isLiveWorkoutsOnly
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // 3-Column Computed Overview Strip
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OverviewStatColumn(
                        label = "Workouts",
                        value = filteredHistory.size.toString(),
                        valueColor = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    OverviewStatColumn(
                        label = "Total Volume",
                        value = "${if (hasConvertedTotal) "≈" else ""}${RepDayRules.formatVolume(totalVolume)} ${unit.label}",
                        valueColor = colors.accent,
                        modifier = Modifier.weight(1.3f)
                    )
                    OverviewStatColumn(
                        label = "Avg Duration",
                        value = if (avgMinutes > 0) "$avgMinutes min" else "—",
                        valueColor = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Empty States or Workout Cards
        if (filteredHistory.isEmpty()) {
            item {
                if (activeFilter != null) {
                    val matchingRoutine = routines.find {
                        (activeFilter.routineId != null && it.id == activeFilter.routineId) ||
                            RepDayRules.exerciseKey(it.name) == RepDayRules.exerciseKey(activeFilter.routineName)
                    }
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "No logs for \"${activeFilter.routineName}\" yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.text
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (matchingRoutine != null) {
                                    Button(
                                        onClick = { onStartRoutine(matchingRoutine) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.accent,
                                            contentColor = colors.accentFg
                                        ),
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Start ${matchingRoutine.name}")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onSelectFilter(null) },
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, colors.borderStrong),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("Show All History", color = colors.text)
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
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
                                Icon(Icons.Rounded.History, null, tint = colors.accent, modifier = Modifier.size(28.dp))
                            }
                            Text("No completed workouts yet", style = MaterialTheme.typography.titleLarge, color = colors.text)
                            Text(
                                "Start a live workout right away or launch a saved routine to build your training log.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onStartLiveWorkout,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.accentFg
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Rounded.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Live Workout", style = MaterialTheme.typography.labelLarge)
                                }
                                OutlinedButton(
                                    onClick = onGoToRoutines,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, colors.borderStrong),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Routines", style = MaterialTheme.typography.labelLarge, color = colors.text)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            itemsIndexed(filteredHistory, key = { _, w -> w.id }) { _, workout ->
                // Set-by-set breakdown starts collapsed; it only opens when tapped.
                val isExpanded = expandedMap[workout.id] ?: false
                val hasSavedRoutine = remember(workout, routines) {
                    routines.any { RepDayRules.matchesRoutineHistory(workout, it) }
                }
                CompletedWorkoutCard(
                    workout = workout,
                    displayUnit = unit,
                    effortScale = effortScale,
                    hasSavedRoutine = hasSavedRoutine,
                    isExpanded = isExpanded,
                    onToggleExpand = { expandedMap[workout.id] = !isExpanded },
                    onEdit = { onEditWorkout(workout) },
                    onRepeat = { onRepeatWorkout(workout) },
                    onSaveAsRoutine = { onSaveAsRoutine(workout) },
                    onDelete = { pendingDelete = workout }
                )
            }
        }
    }
}

@Composable
private fun HistoryFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalRepDayColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.accent else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.border),
        modifier = Modifier.heightIn(min = 38.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) colors.accentFg else colors.textSecondary
            )
        }
    }
}

@Composable
private fun OverviewStatColumn(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalRepDayColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = value,
            style = MonoTabularStyle.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted
        )
    }
}

@Composable
private fun SampleHistoryCleanupBanner(
    sampleCount: Int,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val headline = if (sampleCount == 1) {
        "Remove the sample workout?"
    } else {
        "Remove the $sampleCount sample workouts?"
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.accentSoft,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sample_history_banner")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text
                    )
                    Text(
                        text = "Sample workouts from an earlier install can affect your \"Prev\" set weights and volume totals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("dismiss_sample_workouts_banner")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss sample workout banner",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRemove,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    ),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("remove_sample_workouts_button")
                ) {
                    Text("Remove", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text("Keep", color = colors.text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CompletedWorkoutCard(
    workout: CompletedWorkout,
    displayUnit: WeightUnit,
    effortScale: EffortScale = EffortScale.RPE,
    hasSavedRoutine: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onRepeat: () -> Unit,
    onSaveAsRoutine: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "history_chevron"
    )
    val totalSets = remember(workout) { workout.exercises.sumOf { it.sets.size } }
    val durationMin = (workout.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val isConverted = workout.unit != displayUnit
    val approx = if (isConverted) "≈" else ""
    val displayTotalVolume = remember(workout, displayUnit) {
        RepDayRules.convertVolume(workout.totalVolume, workout.unit, displayUnit)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${workout.id}")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workout.routineName,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = RepDayRules.formatCompletedDateTime(workout.completedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                Surface(
                    onClick = onRepeat,
                    shape = CircleShape,
                    color = colors.accentSoft,
                    border = BorderStroke(1.dp, colors.accent),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("repeat_workout_${workout.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Replay, null, tint = colors.accentSoftFg, modifier = Modifier.size(16.dp))
                        Text(
                            "Repeat",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.accentSoftFg
                        )
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt)
                        .testTag("edit_workout_${workout.id}")
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Edit workout",
                        tint = colors.text,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt)
                        .testTag("delete_workout_${workout.id}")
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete log",
                        tint = colors.danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Unboxed stats strip
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$durationMin min · $approx${RepDayRules.formatVolume(displayTotalVolume)} ${displayUnit.label} volume · $totalSets sets across ${workout.exercises.size} exercises",
                    style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.textSecondary)
                )
                if (isConverted) {
                    Text(
                        text = "logged in ${workout.unit.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                }
            }

            if (!hasSavedRoutine) {
                Surface(
                    onClick = onSaveAsRoutine,
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceAlt,
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("save_history_as_routine_${workout.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Add as Routine",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = colors.accentSoftFg
                            )
                        }
                        Text(
                            text = "Save template",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                    }
                }
            }

            if (!workout.notes.isNullOrBlank()) {
                Text(
                    text = workout.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }

            // Always-visible compact exercise summary
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                workout.exercises.forEach { ex ->
                    val compactSets = ex.sets.joinToString(" · ") { s ->
                        val code = RepDayRules.tagShortCode(s.tag, s.workingSubTag)?.let { "$it " } ?: ""
                        val effort = s.rpe?.let {
                            if (effortScale == EffortScale.RIR) " · ${RepDayRules.rpeToRirLabel(it)}"
                            else " @${RepDayRules.formatRpeNumber(it)}"
                        } ?: ""
                        val weightTxt = RepDayRules.formatConvertedWeight(s.weight, workout.unit, displayUnit)
                        "$code$approx$weightTxt ${displayUnit.label} × ${s.reps}$effort"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ex.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.42f)
                        )
                        Text(
                            text = compactSets,
                            style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.textMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.58f)
                        )
                    }
                }
            }

            // Expandable set-by-set breakdown
            HorizontalDivider(color = colors.border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isExpanded) "Hide set-by-set breakdown" else "View set-by-set breakdown",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useTwoCols = maxWidth >= 600.dp
                    val rows = if (useTwoCols) workout.exercises.chunked(2) else workout.exercises.map { listOf(it) }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { rowEx ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowEx.forEach { ex ->
                                    val rawExVol = ex.sets.sumOf { it.weight * it.reps }.roundToInt()
                                    val exVol = RepDayRules.convertVolume(rawExVol, workout.unit, displayUnit)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.surfaceAlt)
                                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = ex.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.text,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "$approx${RepDayRules.formatVolume(exVol)} ${displayUnit.label}",
                                                style = MonoTabularStyle.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.accentSoftFg
                                                )
                                            )
                                        }
                                        ex.sets.forEachIndexed { idx, s ->
                                            val weightTxt = RepDayRules.formatConvertedWeight(s.weight, workout.unit, displayUnit)
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
                                                        text = "${idx + 1}",
                                                        style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.textMuted)
                                                    )
                                                    if (s.tag != null) {
                                                        SetTagBadge(tag = s.tag, subTag = s.workingSubTag, useFullLabel = true)
                                                    }
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "$approx$weightTxt ${displayUnit.label} × ${s.reps}",
                                                        style = MonoTabularStyle.copy(
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colors.text
                                                        )
                                                    )
                                                    if (s.rpe != null) {
                                                        Text(
                                                            text = RepDayRules.formatEffortValue(s.rpe, effortScale),
                                                            style = MonoTabularStyle.copy(
                                                                fontSize = 11.sp,
                                                                color = colors.accentSoftFg
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (useTwoCols && rowEx.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
