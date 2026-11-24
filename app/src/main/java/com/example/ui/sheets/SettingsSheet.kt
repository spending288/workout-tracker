package com.example.ui.sheets

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.CustomPalette
import com.example.data.model.EffortScale
import com.example.data.model.ThemeMode
import com.example.data.model.WeightUnit
import com.example.domain.SampleDataRules
import com.example.ui.components.RestPickerInline
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.ThemeEngine
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class PaletteSlot(val label: String) {
    ACCENT("Accent"),
    CANVAS("Background"),
    SURFACE("Cards"),
    TEXT("Text")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onSelectWeightUnit: (WeightUnit) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportHistoryCsv: () -> Unit,
    onRestoreSampleRoutines: () -> Unit,
    onEraseAllData: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val systemDark = isSystemInDarkTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val editingDark = when (settings.theme.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val activePalette = ThemeEngine.resolveEffectivePalette(settings.theme, systemDark)

    var showCustomBuilder by remember { mutableStateOf(settings.theme.useCustomColors) }
    var activeSlot by remember { mutableStateOf(PaletteSlot.ACCENT) }
    var showEraseDialog by remember { mutableStateOf(false) }
    var eraseConfirmInput by remember { mutableStateOf("") }

    if (showEraseDialog) {
        val canErase = SampleDataRules.isEraseConfirmationValid(eraseConfirmInput)
        AlertDialog(
            onDismissRequest = {
                showEraseDialog = false
                eraseConfirmInput = ""
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = colors.danger
                )
            },
            title = {
                Text(
                    text = "Erase all data?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "This permanently deletes all routines, training plans, workout history, custom exercises, and preferences. Consider exporting a JSON backup first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    OutlinedButton(
                        onClick = { onExportBackup() },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .testTag("erase_dialog_export_first_button")
                    ) {
                        Icon(Icons.Rounded.Upload, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export backup first", color = colors.accent, style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedTextField(
                        value = eraseConfirmInput,
                        onValueChange = { eraseConfirmInput = it },
                        label = { Text("Type ERASE to confirm") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.danger,
                            unfocusedBorderColor = colors.borderStrong,
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("erase_confirmation_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseDialog = false
                        eraseConfirmInput = ""
                        onEraseAllData()
                    },
                    enabled = canErase,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger,
                        contentColor = Color.White,
                        disabledContainerColor = colors.surfaceAlt,
                        disabledContentColor = colors.textMuted
                    ),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("confirm_erase_all_button")
                ) {
                    Text("Erase All Data", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEraseDialog = false
                        eraseConfirmInput = ""
                    }
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Preferences", style = MaterialTheme.typography.headlineSmall, color = colors.text)
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }
            HorizontalDivider(color = colors.border)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Appearance Mode (Dark / Light / Auto)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ThemeMode.entries.forEach { m ->
                            val selected = settings.theme.mode == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (selected) colors.accent else Color.Transparent)
                                    .clickable {
                                        onUpdateSettings(settings.copy(theme = settings.theme.copy(mode = m)))
                                    }
                                    .testTag("theme_mode_${m.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = m.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) colors.accentFg else colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Color Scheme Presets (2-column grid of 6 presets + Customize toggle)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Color Scheme", style = MaterialTheme.typography.titleMedium, color = colors.text)
                        Surface(
                            onClick = { showCustomBuilder = !showCustomBuilder },
                            shape = CircleShape,
                            color = if (showCustomBuilder || settings.theme.useCustomColors) colors.accentSoft else colors.surfaceAlt,
                            border = BorderStroke(
                                1.dp,
                                if (showCustomBuilder || settings.theme.useCustomColors) colors.accent else colors.border
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Rounded.Palette, null, tint = colors.accent, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "Customize",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.text
                                )
                            }
                        }
                    }

                    ThemeEngine.PRESETS.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { preset ->
                                val isSelected = !settings.theme.useCustomColors && settings.theme.presetId == preset.id
                                val pal = if (editingDark) preset.dark else preset.light
                                Surface(
                                    onClick = {
                                        onUpdateSettings(
                                            settings.copy(
                                                theme = settings.theme.copy(
                                                    presetId = preset.id,
                                                    useCustomColors = false
                                                )
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) colors.accentSoft else colors.surfaceAlt,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) colors.accent else colors.border
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("preset_${preset.id.name.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = preset.id.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.text,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSelected) {
                                                Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(pal.canvas, pal.surface, pal.accent, pal.text).forEach { hex ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(ThemeEngine.parseHexColor(hex))
                                                        .border(1.dp, colors.borderStrong, CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Expandable Custom Color Builder
                    AnimatedVisibility(visible = showCustomBuilder) {
                        CustomColorBuilderCard(
                            editingDark = editingDark,
                            palette = activePalette,
                            activeSlot = activeSlot,
                            onSelectSlot = { activeSlot = it },
                            onPaletteChange = { updatedPalette ->
                                val nextTheme = if (editingDark) {
                                    settings.theme.copy(useCustomColors = true, customDark = updatedPalette)
                                } else {
                                    settings.theme.copy(useCustomColors = true, customLight = updatedPalette)
                                }
                                onUpdateSettings(settings.copy(theme = nextTheme))
                            },
                            onResetCustom = {
                                val nextTheme = if (editingDark) {
                                    settings.theme.copy(useCustomColors = false, customDark = null)
                                } else {
                                    settings.theme.copy(useCustomColors = false, customLight = null)
                                }
                                onUpdateSettings(settings.copy(theme = nextTheme))
                            }
                        )
                    }
                }

                // Weight Unit (Pounds / Kilograms)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Weight Unit", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(WeightUnit.LB to "Pounds (lb)", WeightUnit.KG to "Kilograms (kg)").forEach { (u, label) ->
                            val selected = settings.unit == u
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (selected) colors.accent else Color.Transparent)
                                    .clickable { onSelectWeightUnit(u) }
                                    .testTag("setting_weight_unit_${u.label}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) colors.accentFg else colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Effort Scale (RPE / RIR) + Ask for effort after each set
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Effort Scale", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            EffortScale.RPE to "RPE (6–10)",
                            EffortScale.RIR to "RIR (4+ to 0)"
                        ).forEach { (scale, label) ->
                            val selected = settings.effortScale == scale
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (selected) colors.accent else Color.Transparent)
                                    .clickable { onUpdateSettings(settings.copy(effortScale = scale)) }
                                    .testTag("setting_effort_scale_${scale.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) colors.accentFg else colors.textSecondary
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ask for effort after each set", style = MaterialTheme.typography.titleSmall, color = colors.text)
                                Text(
                                    text = if (settings.askEffortAfterSet) {
                                        "On · One-tap ${settings.effortScale.label} chips in the rest bar"
                                    } else {
                                        "Off · Tap the ${settings.effortScale.label} cell on any set to log"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = settings.askEffortAfterSet,
                                onCheckedChange = { onUpdateSettings(settings.copy(askEffortAfterSet = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.accentFg,
                                    checkedTrackColor = colors.accent
                                ),
                                modifier = Modifier.testTag("setting_ask_effort_after_set_switch")
                            )
                        }
                    }
                }

                // Default Rest Timer
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default Rest Timer", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    RestPickerInline(
                        restSeconds = settings.defaultRestSeconds,
                        onRestChange = { onUpdateSettings(settings.copy(defaultRestSeconds = it)) }
                    )
                }

                // Rest Complete Chime
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceAlt,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rest Complete Chime", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Text(
                                text = if (settings.soundEnabled) "On · Two-tone chime at 0:00" else "Muted · Silent countdown",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = settings.soundEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(soundEnabled = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentFg,
                                checkedTrackColor = colors.accent
                            ),
                            modifier = Modifier.testTag("setting_rest_chime_switch")
                        )
                    }
                }

                // Vibrate When Rest Ends
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceAlt,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibrate when rest ends", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Text(
                                text = if (settings.vibrateOnRestEnd) "On · Two strong pulses at 0:00" else "Off · No rest-end vibration",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = settings.vibrateOnRestEnd,
                            onCheckedChange = { onUpdateSettings(settings.copy(vibrateOnRestEnd = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentFg,
                                checkedTrackColor = colors.accent
                            ),
                            modifier = Modifier.testTag("setting_vibrate_rest_end_switch")
                        )
                    }
                }

                // Keep Screen On During Workouts
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceAlt,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keep screen on during workouts", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Text(
                                text = if (settings.keepScreenOnDuringWorkout) "On · Screen stays awake while logging" else "Off · Follows system screen timeout",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = settings.keepScreenOnDuringWorkout,
                            onCheckedChange = { onUpdateSettings(settings.copy(keepScreenOnDuringWorkout = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentFg,
                                checkedTrackColor = colors.accent
                            ),
                            modifier = Modifier.testTag("setting_keep_screen_on_switch")
                        )
                    }
                }

                // Data: Export/Import Backup, CSV Export, Sample Routines & Erase All Data
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Data", style = MaterialTheme.typography.titleMedium, color = colors.text)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Backup & Export", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Text(
                                text = "Save a complete JSON backup (routines, plans, history, custom exercises, and preferences) or export your logged sets as a spreadsheet CSV.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onExportBackup,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, colors.accent),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .testTag("export_backup_button")
                                ) {
                                    Icon(Icons.Rounded.Upload, null, tint = colors.accent, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export backup", color = colors.text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onImportBackup,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, colors.borderStrong),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .testTag("import_backup_button")
                                ) {
                                    Icon(Icons.Rounded.Download, null, tint = colors.text, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import backup", color = colors.text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = onExportHistoryCsv,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, colors.borderStrong),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .testTag("export_csv_button")
                            ) {
                                Icon(Icons.Rounded.TableChart, null, tint = colors.accent, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export history as CSV", color = colors.text, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // Device storage & sample/erase controls
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Device storage", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Text(
                                text = "Your data stays on this device and works 100% offline. Android's device backup may include it if you have backup turned on in your system settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            OutlinedButton(
                                onClick = onRestoreSampleRoutines,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, colors.borderStrong),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .testTag("restore_sample_routines_button")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore sample routines", color = colors.text, style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = {
                                    eraseConfirmInput = ""
                                    showEraseDialog = true
                                },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, colors.danger),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .testTag("erase_all_data_button")
                            ) {
                                Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Erase all data…", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomColorBuilderCard(
    editingDark: Boolean,
    palette: CustomPalette,
    activeSlot: PaletteSlot,
    onSelectSlot: (PaletteSlot) -> Unit,
    onPaletteChange: (CustomPalette) -> Unit,
    onResetCustom: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val currentHex = when (activeSlot) {
        PaletteSlot.ACCENT -> palette.accent
        PaletteSlot.CANVAS -> palette.canvas
        PaletteSlot.SURFACE -> palette.surface
        PaletteSlot.TEXT -> palette.text
    }
    var previewHex by remember(editingDark, activeSlot, currentHex) { mutableStateOf<String?>(null) }
    val previewPalette = remember(palette, activeSlot, previewHex) {
        val hex = previewHex ?: return@remember palette
        when (activeSlot) {
            PaletteSlot.ACCENT -> palette.copy(accent = hex)
            PaletteSlot.CANVAS -> palette.copy(canvas = hex)
            PaletteSlot.SURFACE -> palette.copy(surface = hex)
            PaletteSlot.TEXT -> palette.copy(text = hex)
        }
    }

    val surfaceColor = ThemeEngine.parseHexColor(previewPalette.surface)
    val textColor = ThemeEngine.parseHexColor(previewPalette.text)
    val contrast = ThemeEngine.contrastRatio(surfaceColor, textColor)
    val lowContrast = contrast < 4.5
    val currentColor = ThemeEngine.parseHexColor(currentHex)

    fun updateSlotColor(newColor: Color) {
        val hex = ThemeEngine.toHex(newColor)
        val next = when (activeSlot) {
            PaletteSlot.ACCENT -> palette.copy(accent = hex)
            PaletteSlot.CANVAS -> palette.copy(canvas = hex)
            PaletteSlot.SURFACE -> palette.copy(surface = hex)
            PaletteSlot.TEXT -> palette.copy(text = hex)
        }
        onPaletteChange(next)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceAlt)
            .border(1.dp, colors.borderStrong, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (editingDark) "Editing Dark Palette" else "Editing Light Palette",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text
                )
                Text(
                    text = String.format(Locale.US, "Contrast %.1f:1", contrast),
                    style = MonoTabularStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lowContrast) colors.danger else colors.ok
                    )
                )
            }
            TextButton(onClick = {
                previewHex = null
                onResetCustom()
            }) {
                Text("Reset", color = colors.textSecondary)
            }
        }

        if (lowContrast) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.dangerSurface)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.WarningAmber, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                Text(
                    text = "Text/Card contrast is below 4.5:1 WCAG AA readability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger
                )
            }
        }

        // 4-slot selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PaletteSlot.entries.forEach { slot ->
                val sel = activeSlot == slot
                val slotHex = when (slot) {
                    PaletteSlot.ACCENT -> previewPalette.accent
                    PaletteSlot.CANVAS -> previewPalette.canvas
                    PaletteSlot.SURFACE -> previewPalette.surface
                    PaletteSlot.TEXT -> previewPalette.text
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) colors.accentSoft else colors.surface)
                        .border(1.dp, if (sel) colors.accent else colors.border, RoundedCornerShape(12.dp))
                        .clickable { onSelectSlot(slot) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(ThemeEngine.parseHexColor(slotHex))
                            .border(1.dp, colors.borderStrong, CircleShape)
                    )
                    Text(
                        text = slot.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) colors.accentSoftFg else colors.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        ColorWheelPicker(
            slotKey = "${if (editingDark) "dark" else "light"}_${activeSlot.name}",
            color = currentColor,
            onPreviewColor = { previewHex = ThemeEngine.toHex(it) },
            onColorCommit = { committed ->
                previewHex = null
                updateSlotColor(committed)
            }
        )
    }
}

@Composable
private fun ColorWheelPicker(
    slotKey: String,
    color: Color,
    onPreviewColor: (Color) -> Unit,
    onColorCommit: (Color) -> Unit
) {
    val colors = LocalRepDayColors.current
    val scope = rememberCoroutineScope()
    val latestOnPreview = rememberUpdatedState(onPreviewColor)
    val latestOnCommit = rememberUpdatedState(onColorCommit)

    val initialHsv = remember(slotKey) {
        FloatArray(3).also { AndroidColor.colorToHSV(color.toArgb(), it) }
    }
    var hue by remember(slotKey) { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember(slotKey) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(slotKey) { mutableFloatStateOf(initialHsv[2]) }
    var isDragging by remember(slotKey) { mutableStateOf(false) }
    var hexInput by remember(slotKey) { mutableStateOf(ThemeEngine.toHex(color)) }
    var lastCommittedHex by remember(slotKey) { mutableStateOf(ThemeEngine.toHex(color)) }
    var debounceJob by remember(slotKey) { mutableStateOf<Job?>(null) }

    DisposableEffect(slotKey) {
        onDispose {
            debounceJob?.cancel()
            debounceJob = null
        }
    }

    val externalHex = ThemeEngine.toHex(color)
    LaunchedEffect(slotKey, externalHex) {
        if (!isDragging && externalHex != lastCommittedHex) {
            val arr = FloatArray(3)
            AndroidColor.colorToHSV(color.toArgb(), arr)
            // Keep existing hue/saturation if external color is pure black.
            if (arr[2] > 0.001f) {
                hue = arr[0]
                sat = arr[1]
            }
            brightness = arr[2]
            hexInput = externalHex
            lastCommittedHex = externalHex
        }
    }

    val previewColor = remember(hue, sat, brightness) {
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, brightness)))
    }
    val rVal = (previewColor.red * 255f).roundToInt().coerceIn(0, 255)
    val gVal = (previewColor.green * 255f).roundToInt().coerceIn(0, 255)
    val bVal = (previewColor.blue * 255f).roundToInt().coerceIn(0, 255)

    fun commitColorNow(nextColor: Color) {
        debounceJob?.cancel()
        debounceJob = null
        val hex = ThemeEngine.toHex(nextColor)
        lastCommittedHex = hex
        hexInput = hex
        latestOnCommit.value(nextColor)
    }

    fun updateLocalHsv(h: Float, s: Float, v: Float, commitImmediately: Boolean) {
        hue = h
        sat = s
        brightness = v
        val nextColor = Color(AndroidColor.HSVToColor(floatArrayOf(h, s, v)))
        val hex = ThemeEngine.toHex(nextColor)
        hexInput = hex
        latestOnPreview.value(nextColor)
        if (commitImmediately) {
            commitColorNow(nextColor)
        } else {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(300L)
                lastCommittedHex = hex
                latestOnCommit.value(nextColor)
            }
        }
    }

    fun applyDirectColor(nextColor: Color) {
        val arr = FloatArray(3)
        AndroidColor.colorToHSV(nextColor.toArgb(), arr)
        if (arr[2] > 0.001f) {
            hue = arr[0]
            sat = arr[1]
        }
        brightness = arr[2]
        commitColorNow(nextColor)
    }

    val latestHue = rememberUpdatedState(hue)
    val latestSat = rememberUpdatedState(sat)
    val latestBrightness = rememberUpdatedState(brightness)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Circular Hue/Saturation Canvas Wheel with draggable reticle
        val sweepColors = remember {
            listOf(
                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
        }
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .pointerInput(slotKey) {
                    detectTapGestures { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = min(cx, cy)
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = hypot(dx, dy).coerceAtMost(radius)
                        val angleRad = atan2(dy, dx)
                        var deg = (angleRad * 180f / PI.toFloat())
                        if (deg < 0f) deg += 360f
                        val nextSat = (dist / radius).coerceIn(0f, 1f)
                        updateLocalHsv(deg, nextSat, latestBrightness.value, commitImmediately = true)
                    }
                }
                .pointerInput(slotKey) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            val current = Color(
                                AndroidColor.HSVToColor(
                                    floatArrayOf(latestHue.value, latestSat.value, latestBrightness.value)
                                )
                            )
                            commitColorNow(current)
                        },
                        onDragCancel = {
                            isDragging = false
                            val current = Color(
                                AndroidColor.HSVToColor(
                                    floatArrayOf(latestHue.value, latestSat.value, latestBrightness.value)
                                )
                            )
                            commitColorNow(current)
                        }
                    ) { change, _ ->
                        change.consume()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = min(cx, cy)
                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        val dist = hypot(dx, dy).coerceAtMost(radius)
                        val angleRad = atan2(dy, dx)
                        var deg = (angleRad * 180f / PI.toFloat())
                        if (deg < 0f) deg += 360f
                        val nextSat = (dist / radius).coerceIn(0f, 1f)
                        updateLocalHsv(deg, nextSat, latestBrightness.value, commitImmediately = false)
                    }
                }
        ) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) / 2f

                // Hue sweep
                drawCircle(
                    brush = Brush.sweepGradient(sweepColors, center = center),
                    radius = radius,
                    center = center
                )
                // Radial white center overlay for saturation
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                // Brightness dimming overlay
                if (brightness < 1f) {
                    drawCircle(
                        color = Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 1f)),
                        radius = radius,
                        center = center
                    )
                }

                // Reticle
                val rad = hue * PI.toFloat() / 180f
                val rx = center.x + cos(rad) * sat * radius
                val ry = center.y + sin(rad) * sat * radius
                drawCircle(
                    color = previewColor,
                    radius = 10.dp.toPx(),
                    center = Offset(rx, ry)
                )
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = Offset(rx, ry),
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f),
                    radius = 12.dp.toPx(),
                    center = Offset(rx, ry),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Brightness 0–100% Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Brightness", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Text(
                    "${(brightness * 100).roundToInt()}%",
                    style = MonoTabularStyle.copy(fontSize = 11.sp, color = colors.text)
                )
            }
            Slider(
                value = brightness,
                onValueChange = {
                    isDragging = true
                    updateLocalHsv(hue, sat, it, commitImmediately = false)
                },
                onValueChangeFinished = {
                    isDragging = false
                    val current = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, brightness)))
                    commitColorNow(current)
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent
                )
            )
        }

        // Hex input + swatch + R/G/B inputs
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(previewColor)
                    .border(1.dp, colors.borderStrong, RoundedCornerShape(10.dp))
            )
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    val cleaned = raw.uppercase(Locale.US).take(7)
                    hexInput = cleaned
                    val bare = cleaned.removePrefix("#")
                    if (bare.length == 6 && bare.all { it in '0'..'9' || it in 'A'..'F' }) {
                        applyDirectColor(ThemeEngine.parseHexColor("#$bare", previewColor))
                    }
                },
                label = { Text("Hex") },
                singleLine = true,
                textStyle = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.text),
                modifier = Modifier.weight(1.3f)
            )
            RgbSmallField("R", rVal, Modifier.weight(0.8f)) { nextR ->
                applyDirectColor(Color(nextR / 255f, gVal / 255f, bVal / 255f, 1f))
            }
            RgbSmallField("G", gVal, Modifier.weight(0.8f)) { nextG ->
                applyDirectColor(Color(rVal / 255f, nextG / 255f, bVal / 255f, 1f))
            }
            RgbSmallField("B", bVal, Modifier.weight(0.8f)) { nextB ->
                applyDirectColor(Color(rVal / 255f, gVal / 255f, nextB / 255f, 1f))
            }
        }
    }
}

@Composable
private fun RgbSmallField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    val colors = LocalRepDayColors.current
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(3)
            text = digits
            digits.toIntOrNull()?.coerceIn(0, 255)?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        textStyle = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.text),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
