package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
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
import com.example.data.model.PlanBlock
import com.example.data.model.PlanWorkoutSlot
import com.example.data.model.Routine
import com.example.data.model.TrainingPlan
import com.example.domain.PlanProgressSummary
import com.example.domain.RepDayRules
import com.example.ui.components.RepDayConfirmDialog
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle

@Composable
fun PlansScreen(
    plans: List<TrainingPlan>,
    routines: List<Routine>,
    history: List<CompletedWorkout>,
    isWideLayout: Boolean,
    bottomContentPadding: Dp,
    onOpenSettings: () -> Unit,
    onNewPlan: () -> Unit,
    onLoadSamples: () -> Unit,
    onToggleActive: (TrainingPlan) -> Unit,
    onRestartPlan: (TrainingPlan) -> Unit,
    onDuplicatePlan: (TrainingPlan) -> Unit,
    onEditPlan: (TrainingPlan) -> Unit,
    onDeletePlan: (TrainingPlan) -> Unit,
    onStartRoutine: (Routine) -> Unit,
    onViewRoutineHistory: (Routine) -> Unit,
    onLinkRoutineToSlot: (planId: String, blockId: String, slotId: String, routine: Routine) -> Unit,
    onBuildRoutineForSlot: (planId: String, blockId: String, slotId: String, placeholderName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRepDayColors.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nowMs = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val waitMs = RepDayRules.millisUntilNextLocalMidnight(System.currentTimeMillis()) + 500L
            delay(waitMs)
            nowMs = System.currentTimeMillis()
        }
    }

    val orderedPlans = remember(plans) {
        plans.sortedWith(
            compareByDescending<TrainingPlan> { it.isActive }
                .thenByDescending { it.updatedAtMs }
        )
    }
    val activePlan = remember(orderedPlans) { orderedPlans.firstOrNull { it.isActive } }
    var pendingDelete by remember { mutableStateOf<TrainingPlan?>(null) }

    pendingDelete?.let { target ->
        RepDayConfirmDialog(
            title = "Delete \"${target.name}\"?",
            body = "Deleting this training plan will not delete any of your saved routines or workout history.",
            confirmLabel = "Delete Plan",
            onConfirm = {
                onDeletePlan(target)
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
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Plans",
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.text
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${plans.size} saved · Active: ${activePlan?.name ?: "None"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isWideLayout) {
                        Button(
                            onClick = onNewPlan,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.accentFg
                            ),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("tablet_new_plan_button")
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Plan", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .border(1.dp, colors.border, CircleShape)
                            .testTag("plans_preferences_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Preferences", tint = colors.text)
                    }
                }
            }
        }

        if (orderedPlans.isEmpty()) {
            item {
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
                            Icon(
                                Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text("No training plans yet", style = MaterialTheme.typography.titleLarge, color = colors.text)
                        Text(
                            "Organize routines into weekly splits or multi-block mesocycles with future placeholders.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                        Button(
                            onClick = onNewPlan,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.accentFg
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Plan", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onLoadSamples,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, colors.borderStrong),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("plans_load_samples_button")
                        ) {
                            Text("Load sample routines & plan", style = MaterialTheme.typography.labelLarge, color = colors.text)
                        }
                    }
                }
            }
        } else {
            items(orderedPlans, key = { it.id }) { plan ->
                val progress = remember(plan, history, routines, nowMs) {
                    RepDayRules.computePlanProgress(plan, history, routines, nowMs = nowMs)
                }
                PlanCard(
                    plan = plan,
                    progress = progress,
                    routines = routines,
                    onToggleActive = { onToggleActive(plan) },
                    onRestart = { onRestartPlan(plan) },
                    onDuplicate = { onDuplicatePlan(plan) },
                    onEdit = { onEditPlan(plan) },
                    onDelete = { pendingDelete = plan },
                    onStartRoutine = onStartRoutine,
                    onViewRoutineHistory = onViewRoutineHistory,
                    onLinkRoutineToSlot = { blockId, slotId, routine ->
                        onLinkRoutineToSlot(plan.id, blockId, slotId, routine)
                    },
                    onBuildRoutineForSlot = { blockId, slotId, label ->
                        onBuildRoutineForSlot(plan.id, blockId, slotId, label)
                    }
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: TrainingPlan,
    progress: PlanProgressSummary,
    routines: List<Routine>,
    onToggleActive: () -> Unit,
    onRestart: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStartRoutine: (Routine) -> Unit,
    onViewRoutineHistory: (Routine) -> Unit,
    onLinkRoutineToSlot: (blockId: String, slotId: String, routine: Routine) -> Unit,
    onBuildRoutineForSlot: (blockId: String, slotId: String, placeholderLabel: String) -> Unit
) {
    val colors = LocalRepDayColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    var blocksExpanded by remember { mutableStateOf(false) }
    var linkingSlotId by remember { mutableStateOf<String?>(null) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (blocksExpanded) 180f else 0f,
        label = "plan_chevron"
    )

    val effectiveBlocks = if (plan.useBlocks) plan.blocks else plan.blocks.take(1)
    val totalWorkoutSlots = effectiveBlocks.sumOf { it.workouts.size }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (plan.isActive) 1.5.dp else 1.dp,
            color = if (plan.isActive) colors.accent else colors.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plan_card_${plan.id}")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active status line + header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (plan.isActive) {
                        Text(
                            text = if (progress.allWeeksReached) {
                                "Active plan · All ${progress.totalWeeks} weeks reached"
                            } else {
                                "Active plan · Week ${progress.currentWeek} of ${progress.totalWeeks}"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.accent
                        )
                    } else if (progress.isPaused) {
                        Text(
                            text = "Paused · Week ${progress.currentWeek} of ${progress.totalWeeks}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textSecondary
                        )
                    }
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text
                    )
                }

                // Active / Set Active toggle pill
                Surface(
                    onClick = onToggleActive,
                    shape = CircleShape,
                    color = if (plan.isActive) colors.accentSoft else colors.surfaceAlt,
                    border = BorderStroke(1.dp, if (plan.isActive) colors.accent else colors.borderStrong),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("toggle_active_plan_${plan.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (plan.isActive) {
                            Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = when {
                                plan.isActive -> "Active"
                                progress.isPaused -> "Resume"
                                else -> "Set Active"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (plan.isActive) colors.accentSoftFg else colors.text
                        )
                    }
                }

                // Overflow ⋮ menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, CircleShape)
                            .testTag("plan_more_${plan.id}")
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More actions for ${plan.name}", tint = colors.textSecondary)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = colors.surfaceElevated
                    ) {
                        if (plan.isActive || progress.isPaused) {
                            DropdownMenuItem(
                                text = { Text("Restart from Week 1", color = colors.text) },
                                leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, tint = colors.textSecondary) },
                                onClick = {
                                    menuExpanded = false
                                    onRestart()
                                }
                            )
                        }
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

            if (!plan.description.isNullOrBlank()) {
                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }

            val summaryMeta = if (plan.useBlocks) {
                "${progress.totalWeeks} weeks · ${effectiveBlocks.size} blocks · ${progress.totalSessions} total sessions"
            } else {
                "${progress.totalWeeks} weeks · ${progress.totalSessions} total sessions"
            }
            Text(
                text = summaryMeta,
                style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.textMuted)
            )

            // Active Plan Progress Box
            if (plan.isActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceAlt)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (plan.useBlocks) {
                                progress.currentBlock?.name ?: "Week ${progress.currentWeek}"
                            } else {
                                "Week ${progress.currentWeek} of ${progress.totalWeeks}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${progress.weekSessionsDone}/${progress.weekSessionsTarget} sessions done this week",
                            style = MonoTabularStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (progress.weekSessionsTarget > 0 && progress.weekSessionsDone >= progress.weekSessionsTarget) {
                                    colors.ok
                                } else {
                                    colors.accentSoftFg
                                }
                            )
                        )
                    }

                    // Proportional horizontal segmented progress bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        progress.blockInfos.forEach { info ->
                            Box(
                                modifier = Modifier
                                    .weight(info.block.weeks.coerceAtLeast(1).toFloat())
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(colors.surface)
                                    .border(
                                        1.dp,
                                        if (info.isCurrent) colors.accent.copy(alpha = 0.7f) else colors.border,
                                        CircleShape
                                    )
                            ) {
                                if (info.fillFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(info.fillFraction)
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(colors.accent)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Blocks & Workouts Toggle
            HorizontalDivider(color = colors.border)
            val toggleText = if (plan.useBlocks) {
                if (blocksExpanded) "Hide blocks & workouts (${effectiveBlocks.size})" else "Show blocks & workouts (${effectiveBlocks.size})"
            } else {
                if (blocksExpanded) "Hide workouts ($totalWorkoutSlots)" else "Show workouts ($totalWorkoutSlots)"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { blocksExpanded = !blocksExpanded }
                    .padding(vertical = 6.dp)
                    .testTag("toggle_plan_blocks_${plan.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = toggleText,
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

            AnimatedVisibility(visible = blocksExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    progress.blockInfos.forEach { info ->
                        PlanBlockView(
                            plan = plan,
                            block = info.block,
                            startWeek = info.startWeek,
                            endWeek = info.endWeek,
                            isCurrentBlock = info.isCurrent,
                            routines = routines,
                            slotWeeklyDone = progress.slotWeeklyDone,
                            linkingSlotId = linkingSlotId,
                            onToggleLinkSlot = { id ->
                                linkingSlotId = if (linkingSlotId == id) null else id
                            },
                            onStartRoutine = onStartRoutine,
                            onViewRoutineHistory = onViewRoutineHistory,
                            onLinkRoutine = { slotId, routine ->
                                onLinkRoutineToSlot(info.block.id, slotId, routine)
                                linkingSlotId = null
                            },
                            onBuildRoutine = { slotId, label ->
                                onBuildRoutineForSlot(info.block.id, slotId, label)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanBlockView(
    plan: TrainingPlan,
    block: PlanBlock,
    startWeek: Int,
    endWeek: Int,
    isCurrentBlock: Boolean,
    routines: List<Routine>,
    slotWeeklyDone: Map<String, Int>,
    linkingSlotId: String?,
    onToggleLinkSlot: (String) -> Unit,
    onStartRoutine: (Routine) -> Unit,
    onViewRoutineHistory: (Routine) -> Unit,
    onLinkRoutine: (slotId: String, routine: Routine) -> Unit,
    onBuildRoutine: (slotId: String, label: String) -> Unit
) {
    val colors = LocalRepDayColors.current
    val sessionsPerWeek = block.workouts.sumOf { it.timesPerWeek }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceAlt)
            .border(
                1.dp,
                if (isCurrentBlock) colors.accent.copy(alpha = 0.6f) else colors.border,
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (plan.useBlocks) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (startWeek == endWeek) "Week $startWeek" else "Weeks $startWeek–$endWeek",
                            style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = colors.accentSoftFg
                        )
                        Text(
                            text = "${block.weeks}w · $sessionsPerWeek sessions/wk",
                            style = MonoTabularStyle.copy(fontSize = 11.sp),
                            color = colors.textMuted
                        )
                    }
                    if (isCurrentBlock) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.accentSoft)
                                .border(1.dp, colors.accent, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Current block",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.accentSoftFg
                            )
                        }
                    }
                }
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                if (!block.notes.isNullOrBlank()) {
                    Text(
                        text = block.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
        }

        block.workouts.forEach { slot ->
            val linkedRoutine = routines.find { it.id == slot.routineId }
            PlanWorkoutSlotRow(
                slot = slot,
                linkedRoutine = linkedRoutine,
                showWeeklyStatus = plan.isActive && isCurrentBlock,
                weeklyDone = slotWeeklyDone[slot.id] ?: 0,
                isLinkPickerOpen = linkingSlotId == slot.id,
                availableRoutines = routines,
                onToggleLinkPicker = { onToggleLinkSlot(slot.id) },
                onStartRoutine = onStartRoutine,
                onViewRoutineHistory = onViewRoutineHistory,
                onSelectRoutineToLink = { onLinkRoutine(slot.id, it) },
                onBuildRoutine = { onBuildRoutine(slot.id, slot.label) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanWorkoutSlotRow(
    slot: PlanWorkoutSlot,
    linkedRoutine: Routine?,
    showWeeklyStatus: Boolean,
    weeklyDone: Int,
    isLinkPickerOpen: Boolean,
    availableRoutines: List<Routine>,
    onToggleLinkPicker: () -> Unit,
    onStartRoutine: (Routine) -> Unit,
    onViewRoutineHistory: (Routine) -> Unit,
    onSelectRoutineToLink: (Routine) -> Unit,
    onBuildRoutine: () -> Unit
) {
    val colors = LocalRepDayColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = linkedRoutine?.name ?: slot.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "${slot.timesPerWeek}×/wk",
                        style = MonoTabularStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentSoftFg
                        )
                    )
                }
                if (linkedRoutine != null) {
                    val sets = linkedRoutine.exercises.sumOf { it.plannedSets?.size ?: it.targetSets }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${linkedRoutine.exercises.size} ex · $sets sets",
                            style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.textMuted)
                        )
                        if (showWeeklyStatus) {
                            val met = weeklyDone >= slot.timesPerWeek
                            Text(
                                text = "· $weeklyDone/${slot.timesPerWeek} this wk",
                                style = MonoTabularStyle.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (met) colors.ok else colors.textSecondary
                                )
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Not built yet · Planned placeholder",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (linkedRoutine != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { onViewRoutineHistory(linkedRoutine) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceAlt)
                    ) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = "History for ${linkedRoutine.name}",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Button(
                        onClick = { onStartRoutine(linkedRoutine) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.accentFg
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Start", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        if (linkedRoutine == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleLinkPicker,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Icon(Icons.Rounded.Link, null, tint = colors.text, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Link", style = MaterialTheme.typography.labelMedium, color = colors.text)
                }
                Button(
                    onClick = onBuildRoutine,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentSoft,
                        contentColor = colors.accentSoftFg
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Build Routine", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            AnimatedVisibility(visible = isLinkPickerOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceAlt)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Link an existing routine:",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    if (availableRoutines.isEmpty()) {
                        Text(
                            text = "No saved routines available yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableRoutines.forEach { r ->
                                Surface(
                                    onClick = { onSelectRoutineToLink(r) },
                                    shape = CircleShape,
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, colors.borderStrong)
                                ) {
                                    Text(
                                        text = r.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.text,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
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
