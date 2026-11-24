package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.model.SetTag
import com.example.data.model.WorkingSubTag
import com.example.domain.RepDayRules
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.ThemeEngine
import kotlin.math.abs

@Composable
fun SetTagBadge(
    tag: SetTag,
    subTag: WorkingSubTag?,
    modifier: Modifier = Modifier,
    useFullLabel: Boolean = false
) {
    val colors = LocalRepDayColors.current
    val tagColors = ThemeEngine.setTagColors(tag, subTag, colors)
    val label = if (useFullLabel) {
        RepDayRules.tagFullLabel(tag, subTag) ?: ""
    } else {
        RepDayRules.tagShortCode(tag, subTag) ?: ""
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tagColors.bg)
            .border(1.dp, tagColors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = if (useFullLabel) 8.dp else 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MonoTabularStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tagColors.text
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FloatingToastPill(
    message: String?,
    modifier: Modifier = Modifier
) {
    val colors = LocalRepDayColors.current
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.borderStrong),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RepDayConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.text,
        textContentColor = colors.textSecondary,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.text
            )
        },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) colors.danger else colors.accent,
                    contentColor = if (isDestructive) Color.White else colors.accentFg
                ),
                shape = CircleShape,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("confirm_dialog_primary")
            ) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("confirm_dialog_dismiss")
            ) {
                Text(dismissLabel, color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestPickerInline(
    restSeconds: Int,
    onRestChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minSeconds: Int = 15,
    maxSeconds: Int = 600
) {
    val colors = LocalRepDayColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceAlt)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = { onRestChange((restSeconds - 15).coerceIn(minSeconds, maxSeconds)) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.borderStrong),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(
                    "-15s",
                    style = MonoTabularStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.text
                )
            }
            Text(
                text = RepDayRules.formatRestTime(restSeconds),
                style = MonoTabularStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = colors.accent
            )
            OutlinedButton(
                onClick = { onRestChange((restSeconds + 15).coerceIn(minSeconds, maxSeconds)) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.borderStrong),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(
                    "+15s",
                    style = MonoTabularStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.text
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RepDayRules.REST_PRESETS_SECONDS.forEach { preset ->
                val selected = restSeconds == preset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accentSoft else colors.surface)
                        .border(
                            1.dp,
                            if (selected) colors.accent else colors.border,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onRestChange(preset) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = RepDayRules.formatRestTime(preset),
                        style = MonoTabularStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.accentSoftFg else colors.textSecondary
                        )
                    )
                }
            }
        }
    }
}

private enum class TargetMode {
    REPS_ONLY,
    RPE_ONLY,
    REPS_AND_RPE;

    fun label(scale: EffortScale): String = when (this) {
        REPS_ONLY -> "Reps Only"
        RPE_ONLY -> "${scale.label} Only"
        REPS_AND_RPE -> "Reps + ${scale.label}"
    }
}

/**
 * Reusable SetTargetControl implementing Section 5.2:
 * 3-Way Target Mode (Reps Only | RPE/RIR Only | Reps + RPE/RIR),
 * Exact vs Range toggles for both Reps (1..100) and Effort (RPE 6.0..10.0 or RIR 0..4),
 * strictly enforcing min < max so ranges can never be reversed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetTargetControl(
    repRange: String?,
    targetRpe: String?,
    onTargetChange: (newRepRange: String?, newTargetRpe: String?) -> Unit,
    modifier: Modifier = Modifier,
    effortScale: EffortScale = EffortScale.RPE
) {
    val colors = LocalRepDayColors.current
    val initialMode = when {
        !repRange.isNullOrBlank() && !targetRpe.isNullOrBlank() -> TargetMode.REPS_AND_RPE
        repRange.isNullOrBlank() && !targetRpe.isNullOrBlank() -> TargetMode.RPE_ONLY
        else -> TargetMode.REPS_ONLY
    }
    var mode by remember(repRange, targetRpe) { mutableStateOf(initialMode) }

    val repBounds = RepDayRules.parseRepBounds(repRange) ?: (8 to 10)
    var isRepRangeMode by remember(repRange) { mutableStateOf(repBounds.first != repBounds.second) }

    val rpeBounds = RepDayRules.parseRpeBounds(targetRpe) ?: (8.0 to 8.0)
    val rirBounds = RepDayRules.rpeTargetToRirBounds(targetRpe) ?: (2 to 2)
    var isRpeRangeMode by remember(targetRpe, effortScale) {
        mutableStateOf(
            if (effortScale == EffortScale.RIR) rirBounds.first != rirBounds.second
            else rpeBounds.first != rpeBounds.second
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceAlt)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 3-way target mode segmented bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TargetMode.entries.forEach { m ->
                val selected = mode == m
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) colors.accent else Color.Transparent)
                        .clickable {
                            mode = m
                            val effectiveRep = repRange?.ifBlank { null } ?: "8-10"
                            val effectiveRpe = targetRpe?.ifBlank { null } ?: "8"
                            when (m) {
                                TargetMode.REPS_ONLY -> onTargetChange(effectiveRep, null)
                                TargetMode.RPE_ONLY -> onTargetChange(null, effectiveRpe)
                                TargetMode.REPS_AND_RPE -> onTargetChange(effectiveRep, effectiveRpe)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = m.label(effortScale),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) colors.accentFg else colors.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        if (mode == TargetMode.RPE_ONLY) {
            Text(
                text = "Log reps achieved during workout",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }

        // Reps Control
        if (mode == TargetMode.REPS_ONLY || mode == TargetMode.REPS_AND_RPE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reps", style = MaterialTheme.typography.labelLarge, color = colors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniToggleChip("Exact Target", !isRepRangeMode) {
                            isRepRangeMode = false
                            val exact = repBounds.first.coerceIn(1, 100)
                            onTargetChange(exact.toString(), if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                        }
                        MiniToggleChip("Rep Range", isRepRangeMode) {
                            isRepRangeMode = true
                            val lo = repBounds.first.coerceIn(1, 98)
                            val hi = maxOf(lo + 2, repBounds.second).coerceAtMost(100)
                            onTargetChange("$lo-$hi", if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                        }
                    }
                }

                if (!isRepRangeMode) {
                    val cur = repBounds.first.coerceIn(1, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StepperIconButton(Icons.Rounded.Remove, "Decrease reps", enabled = cur > 1) {
                            onTargetChange((cur - 1).toString(), if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                        }
                        Text(
                            text = "$cur reps",
                            style = MonoTabularStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            color = colors.text
                        )
                        StepperIconButton(Icons.Rounded.Add, "Increase reps", enabled = cur < 100) {
                            onTargetChange((cur + 1).toString(), if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RepDayRules.REP_EXACT_PRESETS.forEach { p ->
                            PresetChip("$p", cur == p) {
                                onTargetChange("$p", if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                            }
                        }
                    }
                } else {
                    val lo = repBounds.first.coerceIn(1, 99)
                    val hi = maxOf(lo + 1, repBounds.second).coerceIn(2, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Min", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StepperIconButton(Icons.Rounded.Remove, "Decrease min reps", enabled = lo > 1) {
                                    val nextLo = (lo - 1).coerceAtLeast(1)
                                    onTargetChange("$nextLo-$hi", if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                                }
                                Text(
                                    "$lo",
                                    style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = colors.text
                                )
                                StepperIconButton(Icons.Rounded.Add, "Increase min reps", enabled = lo < 99) {
                                    val nextLo = (lo + 1).coerceAtMost(99)
                                    val nextHi = if (nextLo >= hi) (nextLo + 1).coerceAtMost(100) else hi
                                    onTargetChange("$nextLo-$nextHi", if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Max", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StepperIconButton(Icons.Rounded.Remove, "Decrease max reps", enabled = hi > 2) {
                                    val nextHi = (hi - 1).coerceAtLeast(2)
                                    val nextLo = if (nextHi <= lo) (nextHi - 1).coerceAtLeast(1) else lo
                                    onTargetChange("$nextLo-$nextHi", if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                                }
                                Text(
                                    "$hi",
                                    style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = colors.text
                                )
                                StepperIconButton(Icons.Rounded.Add, "Increase max reps", enabled = hi < 100) {
                                    val nextHi = (hi + 1).coerceAtMost(100)
                                    onTargetChange("$lo-$nextHi", if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                                }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RepDayRules.REP_RANGE_PRESETS.forEach { p ->
                            PresetChip(p, "$lo-$hi" == p) {
                                onTargetChange(p, if (mode == TargetMode.REPS_AND_RPE) targetRpe ?: "8" else null)
                            }
                        }
                    }
                }
            }
        }

        // Effort Control (RPE or RIR)
        if (mode == TargetMode.RPE_ONLY || mode == TargetMode.REPS_AND_RPE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (effortScale == EffortScale.RIR) "Target RIR" else "Target RPE",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.text
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniToggleChip(
                            label = if (effortScale == EffortScale.RIR) "Exact RIR" else "Exact RPE",
                            selected = !isRpeRangeMode
                        ) {
                            isRpeRangeMode = false
                            val exact = if (effortScale == EffortScale.RIR) {
                                RepDayRules.rirTargetToRpeString(rirBounds.first)
                            } else {
                                RepDayRules.formatRpeNumber(rpeBounds.first)
                            }
                            onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, exact)
                        }
                        MiniToggleChip(
                            label = if (effortScale == EffortScale.RIR) "RIR Range" else "RPE Range",
                            selected = isRpeRangeMode
                        ) {
                            isRpeRangeMode = true
                            val rangeStr = if (effortScale == EffortScale.RIR) {
                                val lo = rirBounds.first.coerceIn(0, 3)
                                val hi = maxOf(lo + 1, rirBounds.second).coerceAtMost(4)
                                RepDayRules.rirTargetToRpeString(lo, hi)
                            } else {
                                val lo = rpeBounds.first.coerceIn(6.0, 9.5)
                                val hi = maxOf(lo + 0.5, rpeBounds.second).coerceAtMost(10.0)
                                RepDayRules.normalizeRpeTarget(lo, hi)
                            }
                            onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, rangeStr)
                        }
                    }
                }

                if (effortScale == EffortScale.RIR) {
                    if (!isRpeRangeMode) {
                        val curRir = rirBounds.first.coerceIn(0, 4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StepperIconButton(Icons.Rounded.Remove, "Decrease RIR", enabled = curRir > 0) {
                                val next = RepDayRules.rirTargetToRpeString(curRir - 1)
                                onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, next)
                            }
                            Text(
                                text = "${RepDayRules.formatRirBound(curRir)} RIR",
                                style = MonoTabularStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                color = colors.accent
                            )
                            StepperIconButton(Icons.Rounded.Add, "Increase RIR", enabled = curRir < 4) {
                                val next = RepDayRules.rirTargetToRpeString(curRir + 1)
                                onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, next)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RepDayRules.RIR_CHOICES.forEach { rir ->
                                PresetChip(RepDayRules.formatRirBound(rir), curRir == rir) {
                                    onTargetChange(
                                        if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null,
                                        RepDayRules.rirTargetToRpeString(rir)
                                    )
                                }
                            }
                        }
                    } else {
                        val lo = rirBounds.first.coerceIn(0, 3)
                        val hi = maxOf(lo + 1, rirBounds.second).coerceIn(1, 4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Min RIR", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StepperIconButton(Icons.Rounded.Remove, "Decrease min RIR", enabled = lo > 0) {
                                        val nextLo = (lo - 1).coerceAtLeast(0)
                                        onTargetChange(
                                            if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null,
                                            RepDayRules.rirTargetToRpeString(nextLo, hi)
                                        )
                                    }
                                    Text(
                                        RepDayRules.formatRirBound(lo),
                                        style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                        color = colors.accent
                                    )
                                    StepperIconButton(Icons.Rounded.Add, "Increase min RIR", enabled = lo < 3) {
                                        val nextLo = (lo + 1).coerceAtMost(3)
                                        val nextHi = if (nextLo >= hi) (nextLo + 1).coerceAtMost(4) else hi
                                        onTargetChange(
                                            if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null,
                                            RepDayRules.rirTargetToRpeString(nextLo, nextHi)
                                        )
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Max RIR", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StepperIconButton(Icons.Rounded.Remove, "Decrease max RIR", enabled = hi > 1) {
                                        val nextHi = (hi - 1).coerceAtLeast(1)
                                        val nextLo = if (nextHi <= lo) (nextHi - 1).coerceAtLeast(0) else lo
                                        onTargetChange(
                                            if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null,
                                            RepDayRules.rirTargetToRpeString(nextLo, nextHi)
                                        )
                                    }
                                    Text(
                                        RepDayRules.formatRirBound(hi),
                                        style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                        color = colors.accent
                                    )
                                    StepperIconButton(Icons.Rounded.Add, "Increase max RIR", enabled = hi < 4) {
                                        val nextHi = (hi + 1).coerceAtMost(4)
                                        onTargetChange(
                                            if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null,
                                            RepDayRules.rirTargetToRpeString(lo, nextHi)
                                        )
                                    }
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RepDayRules.RIR_RANGE_PRESETS.forEach { (pLo, pHi) ->
                                val label = "${RepDayRules.formatRirBound(pLo)}–${RepDayRules.formatRirBound(pHi)}"
                                PresetChip(label, lo == pLo && hi == pHi) {
                                    onTargetChange(
                                        if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null,
                                        RepDayRules.rirTargetToRpeString(pLo, pHi)
                                    )
                                }
                            }
                        }
                    }
                } else if (!isRpeRangeMode) {
                    val cur = rpeBounds.first
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StepperIconButton(Icons.Rounded.Remove, "Decrease RPE", enabled = cur > 6.0) {
                            val next = RepDayRules.formatRpeNumber(cur - 0.5)
                            onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, next)
                        }
                        Text(
                            text = "RPE ${RepDayRules.formatRpeNumber(cur)}",
                            style = MonoTabularStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            color = colors.accent
                        )
                        StepperIconButton(Icons.Rounded.Add, "Increase RPE", enabled = cur < 10.0) {
                            val next = RepDayRules.formatRpeNumber(cur + 0.5)
                            onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, next)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RepDayRules.RPE_EXACT_PRESETS.forEach { p ->
                            PresetChip(p, RepDayRules.formatRpeNumber(cur) == p) {
                                onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, p)
                            }
                        }
                    }
                } else {
                    val lo = rpeBounds.first.coerceIn(6.0, 9.5)
                    val hi = maxOf(lo + 0.5, rpeBounds.second).coerceIn(6.5, 10.0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Min RPE", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StepperIconButton(Icons.Rounded.Remove, "Decrease min RPE", enabled = lo > 6.0) {
                                    val nextLo = RepDayRules.snapRpe(lo - 0.5)
                                    val str = RepDayRules.normalizeRpeTarget(nextLo, hi)
                                    onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, str)
                                }
                                Text(
                                    RepDayRules.formatRpeNumber(lo),
                                    style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = colors.accent
                                )
                                StepperIconButton(Icons.Rounded.Add, "Increase min RPE", enabled = lo < 9.5) {
                                    val nextLo = RepDayRules.snapRpe(lo + 0.5).coerceAtMost(9.5)
                                    val nextHi = if (nextLo >= hi) RepDayRules.snapRpe(nextLo + 0.5) else hi
                                    val str = RepDayRules.normalizeRpeTarget(nextLo, nextHi)
                                    onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, str)
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Max RPE", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StepperIconButton(Icons.Rounded.Remove, "Decrease max RPE", enabled = hi > 6.5) {
                                    val nextHi = RepDayRules.snapRpe(hi - 0.5).coerceAtLeast(6.5)
                                    val nextLo = if (nextHi <= lo) RepDayRules.snapRpe(nextHi - 0.5) else lo
                                    val str = RepDayRules.normalizeRpeTarget(nextLo, nextHi)
                                    onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, str)
                                }
                                Text(
                                    RepDayRules.formatRpeNumber(hi),
                                    style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = colors.accent
                                )
                                StepperIconButton(Icons.Rounded.Add, "Increase max RPE", enabled = hi < 10.0) {
                                    val nextHi = RepDayRules.snapRpe(hi + 0.5)
                                    val str = RepDayRules.normalizeRpeTarget(lo, nextHi)
                                    onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, str)
                                }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RepDayRules.RPE_RANGE_PRESETS.forEach { p ->
                            PresetChip(p, RepDayRules.normalizeRpeTarget(lo, hi) == p) {
                                onTargetChange(if (mode == TargetMode.REPS_AND_RPE) repRange ?: "8-10" else null, p)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One-tap effort chip strip used inside RestTimerBar and inline beneath active workout sets.
 * Outlines chips matching [targetRpe], fills the currently selected value, and tapping the
 * selected value clears it.
 */
@Composable
fun EffortChipRow(
    scale: EffortScale,
    selectedRpe: Double?,
    targetRpe: String? = null,
    onSelectRpe: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    showDescription: Boolean = false,
    testTagPrefix: String = "effort_chip"
) {
    val colors = LocalRepDayColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (scale == EffortScale.RIR) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RepDayRules.RIR_CHOICES.forEach { rir ->
                    val chipRpe = RepDayRules.rirToRpe(rir)
                    val isSelected = selectedRpe != null && abs(RepDayRules.snapRpe(selectedRpe) - chipRpe) < 0.01
                    val isTarget = RepDayRules.matchesEffortTargetChip(chipRpe, targetRpe)
                    val label = RepDayRules.formatRirBound(rir)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isSelected -> colors.accent
                                    isTarget -> colors.accentSoft
                                    else -> colors.surface
                                }
                            )
                            .border(
                                width = if (isSelected || isTarget) 1.5.dp else 1.dp,
                                color = if (isSelected || isTarget) colors.accent else colors.border,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectRpe(if (isSelected) null else chipRpe) }
                            .testTag("${testTagPrefix}_rir_$rir"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MonoTabularStyle.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || isTarget) FontWeight.Bold else FontWeight.SemiBold,
                                color = when {
                                    isSelected -> colors.accentFg
                                    isTarget -> colors.accentSoftFg
                                    else -> colors.text
                                }
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RepDayRules.RPE_VALUES.forEach { rpe ->
                    val isSelected = selectedRpe != null && abs(RepDayRules.snapRpe(selectedRpe) - rpe) < 0.01
                    val isTarget = RepDayRules.matchesEffortTargetChip(rpe, targetRpe)
                    val label = RepDayRules.formatRpeNumber(rpe)
                    Box(
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isSelected -> colors.accent
                                    isTarget -> colors.accentSoft
                                    else -> colors.surface
                                }
                            )
                            .border(
                                width = if (isSelected || isTarget) 1.5.dp else 1.dp,
                                color = if (isSelected || isTarget) colors.accent else colors.border,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectRpe(if (isSelected) null else rpe) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("${testTagPrefix}_rpe_$label"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MonoTabularStyle.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || isTarget) FontWeight.Bold else FontWeight.SemiBold,
                                color = when {
                                    isSelected -> colors.accentFg
                                    isTarget -> colors.accentSoftFg
                                    else -> colors.text
                                }
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (showDescription && selectedRpe != null) {
            Text(
                text = RepDayRules.formatEffortDescription(selectedRpe, scale),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accentSoftFg
            )
        }
    }
}

@Composable
private fun MiniToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalRepDayColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.accentSoftFg else colors.textSecondary
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalRepDayColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MonoTabularStyle.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.accentSoftFg else colors.textSecondary
            )
        )
    }
}

@Composable
fun StepperIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalRepDayColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) colors.surface else colors.surfaceAlt)
            .border(1.dp, if (enabled) colors.borderStrong else colors.border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.text else colors.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}
