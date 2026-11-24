package com.example.ui.sheets

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlanBlock
import com.example.data.model.PlanWorkoutSlot
import com.example.data.model.Routine
import com.example.data.model.TrainingPlan
import com.example.domain.RepDayRules
import com.example.ui.components.RepDayConfirmDialog
import com.example.ui.components.StepperIconButton
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.viewmodel.PlanEditorRequest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanEditorSheet(
    request: PlanEditorRequest,
    routines: List<Routine>,
    onSave: (TrainingPlan) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val initial = request.initialPlan

    var name by remember(request) { mutableStateOf(initial?.name ?: "") }
    var description by remember(request) { mutableStateOf(initial?.description ?: "") }
    var useBlocks by remember(request) { mutableStateOf(initial?.useBlocks ?: true) }
    var blocks by remember(request) {
        mutableStateOf(
            if (initial != null && initial.blocks.isNotEmpty()) {
                initial.blocks
            } else {
                listOf(
                    PlanBlock(
                        id = RepDayRules.newId("blk"),
                        name = "Block 1",
                        weeks = 4,
                        notes = null,
                        workouts = emptyList()
                    )
                )
            }
        )
    }

    val placeholderInputs = remember(request) { mutableStateMapOf<String, String>() }
    var openLinkSlotId by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDropBlocksConfirm by remember { mutableStateOf(false) }

    fun snapshotSig(n: String, d: String, ub: Boolean, blks: List<PlanBlock>): String {
        return buildString {
            append(n.trim()).append('|').append(d.trim()).append('|').append(ub)
            blks.forEach { b ->
                append("||").append(b.name.trim()).append(':').append(b.weeks).append(':').append(b.notes.orEmpty().trim())
                b.workouts.forEach { w ->
                    append(';').append(w.routineId).append(',').append(w.label.trim()).append(',').append(w.timesPerWeek)
                }
            }
        }
    }

    val initialSig = remember(request) { snapshotSig(name, description, useBlocks, blocks) }
    val isDirty = request.isDuplicateDraft || snapshotSig(name, description, useBlocks, blocks) != initialSig

    fun attemptClose() {
        if (isDirty) showDiscardDialog = true else onDismiss()
    }

    val latestIsDirty = rememberUpdatedState(isDirty)
    val confirmSheetValueChange: (SheetValue) -> Boolean = remember {
        { target ->
            if (target == SheetValue.Hidden && latestIsDirty.value) {
                showDiscardDialog = true
                false
            } else true
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmSheetValueChange
    )

    val effectiveBlocks = if (useBlocks) blocks else blocks.take(1)
    val totalWeeks = effectiveBlocks.sumOf { it.weeks }
    val totalSessions = effectiveBlocks.sumOf { b -> b.weeks * b.workouts.sumOf { it.timesPerWeek } }
    val canSave = RepDayRules.isPlanValidToSave(name, useBlocks, blocks)
    val droppedBlockNames = RepDayRules.droppedBlocksForSingleRepeating(useBlocks, blocks)

    fun commitSave() {
        val nowMs = System.currentTimeMillis()
        val now = RepDayRules.formatIso(nowMs)
        val finalBlocks = (if (useBlocks) blocks else blocks.take(1)).mapIndexed { idx, b ->
            b.copy(
                name = b.name.trim().ifEmpty { "Block ${idx + 1}" },
                workouts = b.workouts.map { slot ->
                    if (slot.routineId == null) slot.copy(label = slot.label.trim()) else slot
                }
            )
        }
        val preserveExisting = initial != null && !request.isDuplicateDraft
        val plan = TrainingPlan(
            id = if (preserveExisting) initial.id else RepDayRules.newId("plan"),
            name = name.trim(),
            description = description.trim().ifBlank { null },
            useBlocks = useBlocks,
            blocks = finalBlocks,
            startedAt = if (preserveExisting) initial.startedAt else null,
            isActive = if (preserveExisting) initial.isActive else false,
            createdAt = if (preserveExisting) initial.createdAt else now,
            updatedAt = now,
            updatedAtMs = nowMs,
            pausedAt = if (preserveExisting) initial.pausedAt else null,
            pausedDurationMs = if (preserveExisting) initial.pausedDurationMs else 0L
        )
        onSave(plan)
    }

    val sheetTitle = when {
        request.isDuplicateDraft -> "Duplicate Plan"
        initial != null -> "Edit Plan"
        else -> "New Training Plan"
    }
    val submitLabel = when {
        request.isDuplicateDraft -> "Save Copy"
        initial != null -> "Save Changes"
        else -> "Create Plan"
    }

    if (showDiscardDialog) {
        RepDayConfirmDialog(
            title = "Discard unsaved changes?",
            body = "Your training plan edits have not been saved. Discard them and close?",
            confirmLabel = "Discard Changes",
            dismissLabel = "Keep Editing",
            onConfirm = {
                showDiscardDialog = false
                onDismiss()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    if (showDropBlocksConfirm) {
        val countLabel = if (droppedBlockNames.size == 1) "1 extra block" else "${droppedBlockNames.size} extra blocks"
        RepDayConfirmDialog(
            title = "Remove $countLabel?",
            body = "Saving as Single Repeating will permanently remove: ${droppedBlockNames.joinToString(", ")}. Continue?",
            confirmLabel = "Remove & Save",
            dismissLabel = "Keep Editing",
            onConfirm = {
                showDropBlocksConfirm = false
                commitSave()
            },
            onDismiss = { showDropBlocksConfirm = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { attemptClose() },
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        BackHandler { attemptClose() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(sheetTitle, style = MaterialTheme.typography.headlineSmall, color = colors.text)
                    val subtitle = if (useBlocks) {
                        "$totalWeeks weeks total · ${effectiveBlocks.size} blocks · $totalSessions planned sessions"
                    } else {
                        "$totalWeeks weeks total · $totalSessions planned sessions"
                    }
                    Text(
                        text = subtitle,
                        style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.textSecondary)
                    )
                }
                IconButton(onClick = { attemptClose() }, modifier = Modifier.size(44.dp)) {
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
                            label = { Text("Plan Name *") },
                            placeholder = { Text("e.g. 12-Week Hypertrophy Block") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.borderStrong
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plan_name_input")
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Goal / Description (optional)") },
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.borderStrong
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Structure Toggle
                        Text("Plan Structure", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceAlt)
                                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(false to "Single Repeating", true to "Training Blocks").forEach { (isMulti, label) ->
                                val selected = useBlocks == isMulti
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (selected) colors.accent else Color.Transparent)
                                        .clickable { useBlocks = isMulti },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) colors.accentFg else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Per-Block Editors
                itemsIndexed(effectiveBlocks, key = { _, b -> b.id }) { blockIdx, block ->
                    val startWeek = effectiveBlocks.take(blockIdx).sumOf { it.weeks } + 1
                    val endWeek = startWeek + block.weeks - 1
                    val sessionsPerWk = block.workouts.sumOf { it.timesPerWeek }

                    fun updateBlock(transform: (PlanBlock) -> PlanBlock) {
                        blocks = blocks.map { if (it.id == block.id) transform(it) else it }
                    }

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
                            if (useBlocks) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Weeks $startWeek–$endWeek · $sessionsPerWk sessions/wk",
                                        style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accentSoftFg)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = {
                                                if (blockIdx > 0) {
                                                    val m = blocks.toMutableList()
                                                    val item = m.removeAt(blockIdx)
                                                    m.add(blockIdx - 1, item)
                                                    blocks = m
                                                }
                                            },
                                            enabled = blockIdx > 0,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.ArrowUpward, "Move block up", tint = if (blockIdx > 0) colors.textSecondary else colors.borderStrong, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (blockIdx < blocks.lastIndex) {
                                                    val m = blocks.toMutableList()
                                                    val item = m.removeAt(blockIdx)
                                                    m.add(blockIdx + 1, item)
                                                    blocks = m
                                                }
                                            },
                                            enabled = blockIdx < blocks.lastIndex,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.ArrowDownward, "Move block down", tint = if (blockIdx < blocks.lastIndex) colors.textSecondary else colors.borderStrong, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                val clone = block.copy(
                                                    id = RepDayRules.newId("blk"),
                                                    name = "${block.name} (Copy)",
                                                    workouts = block.workouts.map { it.copy(id = RepDayRules.newId("slot")) }
                                                )
                                                val m = blocks.toMutableList()
                                                m.add(blockIdx + 1, clone)
                                                blocks = m
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, "Duplicate block", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (blocks.size > 1) {
                                                    blocks = blocks.filterNot { it.id == block.id }
                                                }
                                            },
                                            enabled = blocks.size > 1,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, "Delete block", tint = if (blocks.size > 1) colors.danger else colors.borderStrong, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = block.name,
                                    onValueChange = { n -> updateBlock { it.copy(name = n) } },
                                    label = { Text("Block Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.borderStrong,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Duration in weeks (1..52) + quick presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Duration", style = MaterialTheme.typography.labelLarge, color = colors.text)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StepperIconButton(Icons.Rounded.Remove, "Fewer weeks", enabled = block.weeks > 1) {
                                        updateBlock { it.copy(weeks = (it.weeks - 1).coerceAtLeast(1)) }
                                    }
                                    Text(
                                        text = "${block.weeks}w",
                                        style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
                                    )
                                    StepperIconButton(Icons.Rounded.Add, "More weeks", enabled = block.weeks < 52) {
                                        updateBlock { it.copy(weeks = (it.weeks + 1).coerceAtMost(52)) }
                                    }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(2, 4, 6, 8, 12).forEach { w ->
                                    val sel = block.weeks == w
                                    Surface(
                                        onClick = { updateBlock { it.copy(weeks = w) } },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (sel) colors.accentSoft else colors.surface,
                                        border = BorderStroke(1.dp, if (sel) colors.accent else colors.border)
                                    ) {
                                        Text(
                                            "${w}w",
                                            style = MonoTabularStyle.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (sel) colors.accentSoftFg else colors.textSecondary
                                            ),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            if (useBlocks) {
                                OutlinedTextField(
                                    value = block.notes.orEmpty(),
                                    onValueChange = { n -> updateBlock { it.copy(notes = n.ifBlank { null }) } },
                                    label = { Text("Block Notes (optional)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.borderStrong,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Weekly Workouts List
                            Text("Weekly Workouts", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            if (block.workouts.isEmpty()) {
                                Text(
                                    text = "Add at least one saved routine or future placeholder below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.danger
                                )
                            } else {
                                block.workouts.forEach { slot ->
                                    val linkedRoutine = routines.find { it.id == slot.routineId }
                                    val isLinkOpen = openLinkSlotId == slot.id

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.surface)
                                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                if (linkedRoutine != null) {
                                                    Text(linkedRoutine.name, style = MaterialTheme.typography.titleSmall, color = colors.text)
                                                    Text(
                                                        "${linkedRoutine.exercises.size} exercises",
                                                        style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.textMuted)
                                                    )
                                                } else {
                                                    val isLabelBlank = slot.label.trim().isEmpty()
                                                    OutlinedTextField(
                                                        value = slot.label,
                                                        onValueChange = { nextLabel ->
                                                            updateBlock { b ->
                                                                b.copy(workouts = b.workouts.map {
                                                                    if (it.id == slot.id) it.copy(label = nextLabel) else it
                                                                })
                                                            }
                                                        },
                                                        placeholder = { Text("Workout name *") },
                                                        isError = isLabelBlank,
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    Text(
                                                        text = if (isLabelBlank) "Enter a name for this planned workout" else "Planned · not built yet",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isLabelBlank) colors.danger else colors.textMuted
                                                    )
                                                }
                                            }

                                            // ×/wk stepper (1..7)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        updateBlock { b ->
                                                            b.copy(workouts = b.workouts.map {
                                                                if (it.id == slot.id) it.copy(timesPerWeek = (it.timesPerWeek - 1).coerceAtLeast(1)) else it
                                                            })
                                                        }
                                                    },
                                                    enabled = slot.timesPerWeek > 1,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Remove, "Decrease frequency", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                                }
                                                Text(
                                                    text = "${slot.timesPerWeek}×/wk",
                                                    style = MonoTabularStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accentSoftFg)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        updateBlock { b ->
                                                            b.copy(workouts = b.workouts.map {
                                                                if (it.id == slot.id) it.copy(timesPerWeek = (it.timesPerWeek + 1).coerceAtMost(7)) else it
                                                            })
                                                        }
                                                    },
                                                    enabled = slot.timesPerWeek < 7,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Add, "Increase frequency", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            IconButton(
                                                onClick = { openLinkSlotId = if (isLinkOpen) null else slot.id },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Rounded.Link, "Link or unlink routine", tint = if (isLinkOpen) colors.accent else colors.textSecondary, modifier = Modifier.size(18.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    updateBlock { b -> b.copy(workouts = b.workouts.filterNot { it.id == slot.id }) }
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Rounded.DeleteOutline, "Remove workout slot", tint = colors.danger, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        AnimatedVisibility(visible = isLinkOpen) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(colors.surfaceAlt)
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text("Swap routine or convert to placeholder:", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (slot.routineId != null) {
                                                        Surface(
                                                            onClick = {
                                                                updateBlock { b ->
                                                                    b.copy(workouts = b.workouts.map {
                                                                        if (it.id == slot.id) it.copy(routineId = null) else it
                                                                    })
                                                                }
                                                                openLinkSlotId = null
                                                            },
                                                            shape = CircleShape,
                                                            color = colors.surface,
                                                            border = BorderStroke(1.dp, colors.borderStrong)
                                                        ) {
                                                            Text("Make Placeholder", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                                        }
                                                    }
                                                    routines.forEach { r ->
                                                        Surface(
                                                            onClick = {
                                                                updateBlock { b ->
                                                                    b.copy(workouts = b.workouts.map {
                                                                        if (it.id == slot.id) it.copy(routineId = r.id, label = r.name) else it
                                                                    })
                                                                }
                                                                openLinkSlotId = null
                                                            },
                                                            shape = CircleShape,
                                                            color = if (slot.routineId == r.id) colors.accentSoft else colors.surface,
                                                            border = BorderStroke(1.dp, if (slot.routineId == r.id) colors.accent else colors.border)
                                                        ) {
                                                            Text(r.name, style = MaterialTheme.typography.labelSmall, color = colors.text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Quick-Add Saved Routines
                            if (routines.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Quick-Add Saved Routines", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        routines.forEach { r ->
                                            val existing = block.workouts.find { it.routineId == r.id }
                                            Surface(
                                                onClick = {
                                                    updateBlock { b ->
                                                        val found = b.workouts.find { it.routineId == r.id }
                                                        if (found != null) {
                                                            b.copy(workouts = b.workouts.map {
                                                                if (it.id == found.id) it.copy(timesPerWeek = (it.timesPerWeek + 1).coerceAtMost(7)) else it
                                                            })
                                                        } else {
                                                            b.copy(workouts = b.workouts + PlanWorkoutSlot(RepDayRules.newId("slot"), r.id, r.name, null, 1))
                                                        }
                                                    }
                                                },
                                                shape = CircleShape,
                                                color = if (existing != null) colors.accentSoft else colors.surface,
                                                border = BorderStroke(1.dp, if (existing != null) colors.accent else colors.borderStrong)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (existing != null) {
                                                        Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                                    }
                                                    Text(
                                                        text = if (existing != null) "${r.name} (${existing.timesPerWeek}×)" else r.name,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                        color = if (existing != null) colors.accentSoftFg else colors.text
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Schedule Future Placeholder
                            val draftPlaceholder = placeholderInputs[block.id].orEmpty()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = draftPlaceholder,
                                    onValueChange = { placeholderInputs[block.id] = it },
                                    placeholder = { Text("Future placeholder (e.g. Legs B)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.borderStrong,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val clean = draftPlaceholder.trim()
                                        if (clean.isNotEmpty()) {
                                            updateBlock { b ->
                                                b.copy(workouts = b.workouts + PlanWorkoutSlot(RepDayRules.newId("slot"), null, clean, null, 1))
                                            }
                                            placeholderInputs[block.id] = ""
                                        }
                                    },
                                    enabled = draftPlaceholder.trim().isNotEmpty(),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentSoft,
                                        contentColor = colors.accentSoftFg
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Add Planned", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }

                if (useBlocks) {
                    item {
                        OutlinedButton(
                            onClick = {
                                blocks = blocks + PlanBlock(
                                    id = RepDayRules.newId("blk"),
                                    name = "Block ${blocks.size + 1}",
                                    weeks = 4,
                                    notes = null,
                                    workouts = emptyList()
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, colors.borderStrong),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = colors.accent)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Block", color = colors.text, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Footer
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
                        if (droppedBlockNames.isNotEmpty()) {
                            showDropBlocksConfirm = true
                        } else {
                            commitSave()
                        }
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
                        .testTag("plan_editor_submit")
                ) {
                    Text(submitLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
