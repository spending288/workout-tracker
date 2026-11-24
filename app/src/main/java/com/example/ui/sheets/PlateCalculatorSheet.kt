package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlateCalculatorSettings
import com.example.data.model.WeightUnit
import com.example.domain.PlateCount
import com.example.domain.RepDayRules
import com.example.ui.components.StepperIconButton
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlateCalculatorSheet(
    initialUnit: WeightUnit,
    plateSettings: PlateCalculatorSettings,
    seedWeight: Double?,
    onSettingsChange: (PlateCalculatorSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var localUnit by remember(initialUnit) { mutableStateOf(initialUnit) }

    val defaultWeight = seedWeight?.takeIf { it > 0.0 } ?: if (initialUnit == WeightUnit.LB) 185.0 else 80.0
    var targetWeightText by remember(seedWeight, initialUnit) {
        mutableStateOf(RepDayRules.formatNumber(defaultWeight))
    }
    var barWeightText by remember(localUnit, plateSettings.barWeightLb, plateSettings.barWeightKg) {
        mutableStateOf(
            RepDayRules.formatNumber(
                if (localUnit == WeightUnit.LB) plateSettings.barWeightLb else plateSettings.barWeightKg
            )
        )
    }

    val targetWeight = targetWeightText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val stepSmall = if (localUnit == WeightUnit.LB) 5.0 else 2.5
    val quickNudges = if (localUnit == WeightUnit.LB) listOf(-10.0, -5.0, 5.0, 10.0) else listOf(-5.0, -2.5, 2.5, 5.0)
    val barPresets = if (localUnit == WeightUnit.LB) RepDayRules.BAR_PRESETS_LB else RepDayRules.BAR_PRESETS_KG
    val allPlates = if (localUnit == WeightUnit.LB) RepDayRules.ALL_PLATES_LB else RepDayRules.ALL_PLATES_KG
    val enabledPlates = if (localUnit == WeightUnit.LB) plateSettings.availablePlatesLb else plateSettings.availablePlatesKg

    val result = remember(targetWeight, localUnit, plateSettings) {
        RepDayRules.calculatePlates(targetWeight, localUnit, plateSettings)
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with local-only lb / kg unit switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Plate Calculator", style = MaterialTheme.typography.titleLarge, color = colors.text)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, CircleShape)
                            .padding(3.dp)
                    ) {
                        WeightUnit.entries.forEach { u ->
                            val selected = localUnit == u
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) colors.accent else Color.Transparent)
                                    .clickable {
                                        if (u != localUnit) {
                                            if (targetWeight > 0.0) {
                                                val converted = RepDayRules.convertWeightForInput(
                                                    value = targetWeight,
                                                    from = localUnit,
                                                    to = u
                                                )
                                                targetWeightText = RepDayRules.formatNumber(converted)
                                            }
                                            localUnit = u
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("plate_calc_unit_${u.label}")
                            ) {
                                Text(
                                    text = u.label,
                                    style = MonoTabularStyle.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) colors.accentFg else colors.textSecondary
                                    )
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }
            }

            // Target Total Weight input + steppers + quick nudges
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Target Total Weight (${localUnit.label})", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StepperIconButton(Icons.Rounded.Remove, "Decrease weight") {
                        val next = (targetWeight - stepSmall).coerceAtLeast(0.0)
                        targetWeightText = RepDayRules.formatNumber(next)
                    }
                    OutlinedTextField(
                        value = targetWeightText,
                        onValueChange = { raw ->
                            targetWeightText = RepDayRules.sanitizeDecimalInput(raw, maxLength = 8)
                        },
                        singleLine = true,
                        textStyle = MonoTabularStyle.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.borderStrong,
                            focusedContainerColor = colors.surfaceAlt,
                            unfocusedContainerColor = colors.surfaceAlt
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("plate_calc_target_input")
                    )
                    StepperIconButton(Icons.Rounded.Add, "Increase weight") {
                        val next = (targetWeight + stepSmall).coerceAtMost(2000.0)
                        targetWeightText = RepDayRules.formatNumber(next)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickNudges.forEach { delta ->
                        val sign = if (delta > 0) "+" else ""
                        Surface(
                            onClick = {
                                val next = (targetWeight + delta).coerceAtLeast(0.0)
                                targetWeightText = RepDayRules.formatNumber(next)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surfaceAlt,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "$sign${RepDayRules.formatNumber(delta)}",
                                    style = MonoTabularStyle.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.text
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Visual Barbell / Sleeve Diagram Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceAlt)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Achieved Total", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        Text(
                            text = "${RepDayRules.formatNumber(result.achievedTotal)} ${localUnit.label}",
                            style = MonoTabularStyle.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.text
                            )
                        )
                    }

                    when {
                        result.isBelowBar -> {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.dangerSurface)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Below bar weight",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.danger
                                )
                            }
                        }
                        result.isExactMatch -> {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.okSurface)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Exact match",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.ok
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.dropOrangeBg)
                                    .border(1.dp, colors.dropOrangeBorder, CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Not exact",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.dropOrange
                                )
                            }
                        }
                    }
                }

                if (!result.isBelowBar && !result.isExactMatch) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            onClick = { targetWeightText = RepDayRules.formatNumber(result.achievedTotal) },
                            shape = CircleShape,
                            color = colors.dropOrangeBg,
                            border = BorderStroke(1.dp, colors.dropOrangeBorder),
                            modifier = Modifier.testTag("plate_calc_use_below")
                        ) {
                            Text(
                                text = "Short by ${RepDayRules.formatNumber(result.shortBy)} · Use ${RepDayRules.formatNumber(result.achievedTotal)}",
                                style = MonoTabularStyle.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.dropOrange
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        result.overOption?.let { over ->
                            Surface(
                                onClick = { targetWeightText = RepDayRules.formatNumber(over.totalWeight) },
                                shape = CircleShape,
                                color = colors.accentSoft,
                                border = BorderStroke(1.dp, colors.accent),
                                modifier = Modifier.testTag("plate_calc_use_above")
                            ) {
                                Text(
                                    text = "Over by ${RepDayRules.formatNumber(over.diffFromTarget)} · Use ${RepDayRules.formatNumber(over.totalWeight)}",
                                    style = MonoTabularStyle.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accentSoftFg
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                BarbellSleeveCanvas(
                    plates = result.platesPerSide,
                    unit = localUnit,
                    includeBar = plateSettings.includeBar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                )

                val sideLabel = if (plateSettings.perSideMode) "Per side:" else "Single sleeve:"
                val platesFormula = if (result.platesPerSide.isEmpty()) {
                    "No plates"
                } else {
                    result.platesPerSide.joinToString(" + ") {
                        "${it.count}×${RepDayRules.formatNumber(it.plate)}${localUnit.label}"
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "$sideLabel $platesFormula",
                        style = MonoTabularStyle.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentSoftFg
                        )
                    )
                    Text(
                        text = "Bar ${RepDayRules.formatNumber(result.barUsed)} + Plates ${RepDayRules.formatNumber(result.platesTotalWeight)} ${localUnit.label}",
                        style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.textSecondary)
                    )
                    if (!result.isBelowBar && !result.isExactMatch) {
                        result.overOption?.let { over ->
                            val overFormula = if (over.platesPerSide.isEmpty()) {
                                "No plates"
                            } else {
                                over.platesPerSide.joinToString(" + ") {
                                    "${it.count}×${RepDayRules.formatNumber(it.plate)}${localUnit.label}"
                                }
                            }
                            Text(
                                text = "Next up (${RepDayRules.formatNumber(over.totalWeight)} ${localUnit.label}): $overFormula",
                                style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.textMuted)
                            )
                        }
                    }
                }
            }

            // Equipment Toggles (persisted)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceAlt)
                    .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Include Bar", style = MaterialTheme.typography.titleSmall, color = colors.text)
                        Text(
                            text = "Turn off for plate-loaded machines",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Switch(
                        checked = plateSettings.includeBar,
                        onCheckedChange = { onSettingsChange(plateSettings.copy(includeBar = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accentFg,
                            checkedTrackColor = colors.accent
                        )
                    )
                }

                if (plateSettings.includeBar) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = barWeightText,
                            onValueChange = { raw ->
                                val cleaned = RepDayRules.sanitizeDecimalInput(raw, maxLength = 6)
                                barWeightText = cleaned
                                val parsed = cleaned.toDoubleOrNull()?.coerceIn(0.0, 200.0)
                                if (parsed != null) {
                                    val updated = if (localUnit == WeightUnit.LB) {
                                        plateSettings.copy(barWeightLb = parsed)
                                    } else {
                                        plateSettings.copy(barWeightKg = parsed)
                                    }
                                    onSettingsChange(updated)
                                }
                            },
                            label = { Text("Bar (${localUnit.label})") },
                            singleLine = true,
                            textStyle = MonoTabularStyle.copy(fontSize = 14.sp, color = colors.text),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(108.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            barPresets.forEach { preset ->
                                val curBar = if (localUnit == WeightUnit.LB) plateSettings.barWeightLb else plateSettings.barWeightKg
                                val selected = curBar == preset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) colors.accentSoft else colors.surface)
                                        .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(10.dp))
                                        .clickable {
                                            barWeightText = RepDayRules.formatNumber(preset)
                                            val updated = if (localUnit == WeightUnit.LB) {
                                                plateSettings.copy(barWeightLb = preset)
                                            } else {
                                                plateSettings.copy(barWeightKg = preset)
                                            }
                                            onSettingsChange(updated)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = RepDayRules.formatNumber(preset),
                                        style = MonoTabularStyle.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) colors.accentSoftFg else colors.textSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Sleeves Toggle: 2 Sides vs 1 Sleeve
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sleeves", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(true to "2 Sides", false to "1 Sleeve").forEach { (perSide, label) ->
                            val selected = plateSettings.perSideMode == perSide
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selected) colors.accent else Color.Transparent)
                                    .clickable { onSettingsChange(plateSettings.copy(perSideMode = perSide)) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
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

            // Available Plates Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Available Plates (${localUnit.label})", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    if (enabledPlates.size < allPlates.size) {
                        TextButton(
                            onClick = {
                                val updated = if (localUnit == WeightUnit.LB) {
                                    plateSettings.copy(availablePlatesLb = allPlates)
                                } else {
                                    plateSettings.copy(availablePlatesKg = allPlates)
                                }
                                onSettingsChange(updated)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Select all", color = colors.accent)
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allPlates.forEach { plate ->
                        val isEnabled = enabledPlates.contains(plate)
                        val plateColor = plateVisualColor(plate, localUnit)
                        Surface(
                            onClick = {
                                if (isEnabled && enabledPlates.size <= 1) return@Surface
                                val next = if (isEnabled) enabledPlates - plate else (enabledPlates + plate).sortedDescending()
                                val updated = if (localUnit == WeightUnit.LB) {
                                    plateSettings.copy(availablePlatesLb = next)
                                } else {
                                    plateSettings.copy(availablePlatesKg = next)
                                }
                                onSettingsChange(updated)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isEnabled) colors.surfaceElevated else colors.surfaceAlt,
                            border = BorderStroke(
                                width = if (isEnabled) 1.5.dp else 1.dp,
                                color = if (isEnabled) plateColor else colors.border
                            ),
                            modifier = Modifier.heightIn(min = 42.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isEnabled) plateColor else colors.borderStrong)
                                )
                                Text(
                                    text = RepDayRules.formatNumber(plate),
                                    style = MonoTabularStyle.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEnabled) colors.text else colors.textMuted
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

private fun plateVisualColor(plate: Double, unit: WeightUnit): Color {
    return if (unit == WeightUnit.LB) {
        when {
            plate >= 45.0 -> Color(0xFFE04848) // Red
            plate >= 35.0 -> Color(0xFF3E83F7) // Blue
            plate >= 25.0 -> Color(0xFFE8BD4A) // Gold
            plate >= 10.0 -> Color(0xFF34B36E) // Green
            plate >= 5.0 -> Color(0xFF7B8794)  // Slate
            else -> Color(0xFFC9D1D9)          // Silver
        }
    } else {
        when {
            plate >= 25.0 -> Color(0xFFE04848)
            plate >= 20.0 -> Color(0xFF3E83F7)
            plate >= 15.0 -> Color(0xFFE8BD4A)
            plate >= 10.0 -> Color(0xFF34B36E)
            plate >= 5.0 -> Color(0xFF7B8794)
            else -> Color(0xFFC9D1D9)
        }
    }
}

private fun plateHeightFraction(plate: Double, unit: WeightUnit): Float {
    return if (unit == WeightUnit.LB) {
        when {
            plate >= 45.0 -> 0.92f
            plate >= 35.0 -> 0.80f
            plate >= 25.0 -> 0.68f
            plate >= 10.0 -> 0.54f
            plate >= 5.0 -> 0.42f
            else -> 0.34f
        }
    } else {
        when {
            plate >= 25.0 -> 0.92f
            plate >= 20.0 -> 0.86f
            plate >= 15.0 -> 0.74f
            plate >= 10.0 -> 0.60f
            plate >= 5.0 -> 0.46f
            else -> 0.34f
        }
    }
}

@Composable
private fun BarbellSleeveCanvas(
    plates: List<PlateCount>,
    unit: WeightUnit,
    includeBar: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalRepDayColors.current
    val expanded = remember(plates) {
        plates.flatMap { pc -> List(pc.count) { pc.plate } }.take(10)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        val shaftColor = if (colors.isDark) Color(0xFF8C949E) else Color(0xFF6B7280)
        val collarColor = if (colors.isDark) Color(0xFFB5BDC7) else Color(0xFF4B5563)

        // Draw barbell shaft or machine peg
        val shaftHeight = h * 0.11f
        val startX = if (includeBar) 0f else w * 0.14f
        drawRoundRect(
            color = shaftColor,
            topLeft = Offset(startX, centerY - shaftHeight / 2f),
            size = Size(w - startX - 8f, shaftHeight),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Draw collar
        val collarX = if (includeBar) w * 0.18f else w * 0.14f
        val collarW = w * 0.05f
        val collarH = h * 0.30f
        drawRoundRect(
            color = collarColor,
            topLeft = Offset(collarX, centerY - collarH / 2f),
            size = Size(collarW, collarH),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Draw stacked plates (up to 10)
        var cursorX = collarX + collarW + 6f
        val maxPlateWidth = ((w - cursorX - 16f) / 10f).coerceIn(14f, 26f)
        val gap = 4f
        for (plate in expanded) {
            val pColor = plateVisualColor(plate, unit)
            val pHeight = h * plateHeightFraction(plate, unit)
            val pWidth = if (plate >= (if (unit == WeightUnit.LB) 25.0 else 15.0)) maxPlateWidth else maxPlateWidth * 0.78f
            if (cursorX + pWidth > w - 6f) break
            drawRoundRect(
                color = pColor,
                topLeft = Offset(cursorX, centerY - pHeight / 2f),
                size = Size(pWidth, pHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )
            cursorX += pWidth + gap
        }
    }
}
