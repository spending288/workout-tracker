package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SetTag
import com.example.data.model.WorkingSubTag
import com.example.data.model.WorkoutSet
import com.example.domain.RepDayRules
import com.example.ui.components.RestPickerInline
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.ThemeEngine
import com.example.ui.util.FeedbackUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetTagRpeSheet(
    exerciseName: String,
    setNumber: Int,
    workoutSet: WorkoutSet,
    defaultRestSeconds: Int,
    isNextSetDrop: Boolean,
    onUpdate: (tag: SetTag?, subTag: WorkingSubTag?, restSeconds: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTag by remember(workoutSet.id) { mutableStateOf(workoutSet.tag) }
    var selectedSubTag by remember(workoutSet.id) { mutableStateOf(workoutSet.workingSubTag) }
    var selectedRest by remember(workoutSet.id) {
        mutableIntStateOf(
            if (isNextSetDrop) 0 else (workoutSet.restSeconds ?: defaultRestSeconds).coerceIn(15, 600)
        )
    }

    fun emitUpdate(
        nextTag: SetTag? = selectedTag,
        nextSubTag: WorkingSubTag? = selectedSubTag,
        nextRest: Int = selectedRest
    ) {
        selectedTag = nextTag
        selectedSubTag = nextSubTag
        selectedRest = nextRest
        onUpdate(nextTag, nextSubTag, if (isNextSetDrop) 0 else nextRest)
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Set $setNumber · $exerciseName",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text
                    )
                    Text(
                        text = "Changes apply immediately",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }

            // Set Tag Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set Tag", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    if (selectedTag != null) {
                        TextButton(
                            onClick = {
                                FeedbackUtils.triggerLightTick(context)
                                emitUpdate(nextTag = null, nextSubTag = null)
                            },
                            modifier = Modifier.testTag("clear_tag_button")
                        ) {
                            Text("Clear tag", color = colors.textSecondary)
                        }
                    }
                }

                SetTag.entries.forEach { tag ->
                    val isSelected = selectedTag == tag
                    val tagColors = ThemeEngine.setTagColors(tag, null, colors)
                    val subtitle = when (tag) {
                        SetTag.WARMUP -> "Acclimation / feeder set"
                        SetTag.WORKING -> "Primary hypertrophy or strength set"
                        SetTag.DROP -> "Immediately follows previous set (0:00 rest)"
                        SetTag.MYO -> "Activation set + short-rest mini-sets"
                        SetTag.PARTIALS -> "Extended ROM lengthened-position partials"
                    }

                    Surface(
                        onClick = {
                            FeedbackUtils.triggerLightTick(context)
                            if (selectedTag == tag) {
                                emitUpdate(nextTag = null, nextSubTag = null)
                            } else {
                                emitUpdate(
                                    nextTag = tag,
                                    nextSubTag = if (tag == SetTag.WORKING) selectedSubTag else null
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) tagColors.bg else colors.surfaceAlt,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) tagColors.border else colors.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("set_tag_option_${tag.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(tagColors.bg)
                                        .border(1.dp, tagColors.border, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = tag.shortCode,
                                        style = MonoTabularStyle.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tagColors.text
                                        )
                                    )
                                }
                                Column {
                                    Text(
                                        text = tag.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) tagColors.text else colors.text
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, null, tint = tagColors.text, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Indented 2-col Working Subtag selector
                    if (tag == SetTag.WORKING && selectedTag == SetTag.WORKING) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WorkingSubTag.entries.forEach { sub ->
                                val subSelected = selectedSubTag == sub
                                val subColors = ThemeEngine.setTagColors(SetTag.WORKING, sub, colors)
                                Surface(
                                    onClick = {
                                        FeedbackUtils.triggerLightTick(context)
                                        emitUpdate(nextSubTag = if (subSelected) null else sub)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (subSelected) subColors.bg else colors.surfaceAlt,
                                    border = BorderStroke(
                                        1.dp,
                                        if (subSelected) subColors.border else colors.border
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = sub.displayName,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (subSelected) subColors.text else colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Rest after this set
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rest after this set", style = MaterialTheme.typography.titleMedium, color = colors.text)
                if (isNextSetDrop) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.dropOrangeBg)
                            .border(1.dp, colors.dropOrangeBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.FlashOn, null, tint = colors.dropOrange, modifier = Modifier.size(22.dp))
                        Column {
                            Text(
                                text = "No rest after this set",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.dropOrange
                            )
                            Text(
                                text = "Next set is a Drop Set — go straight into it (0:00)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                } else {
                    RestPickerInline(
                        restSeconds = selectedRest,
                        onRestChange = { next ->
                            FeedbackUtils.triggerLightTick(context)
                            emitUpdate(nextRest = next)
                        }
                    )
                }
            }

            Button(
                onClick = onDismiss,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.accentFg
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("done_set_options_button")
            ) {
                Text("Done", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
