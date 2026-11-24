package com.example.ui

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AutoBackupWriter
import com.example.data.model.ActiveWorkout
import com.example.domain.BackupImportMode
import com.example.domain.BackupValidationResult
import com.example.domain.RecordDecodeRules
import com.example.domain.RepDayRules
import com.example.ui.components.FloatingToastPill
import com.example.ui.components.RepDayConfirmDialog
import com.example.ui.screens.ActiveWorkoutScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PlansScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.sheets.HistoryWorkoutEditorSheet
import com.example.ui.sheets.PlanEditorSheet
import com.example.ui.sheets.PlateCalculatorSheet
import com.example.ui.sheets.RoutineEditorSheet
import com.example.ui.sheets.SettingsSheet
import com.example.ui.theme.LocalRepDayColors
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.RepDayTheme
import com.example.ui.viewmodel.PendingWorkoutStart
import com.example.ui.viewmodel.PrimaryTab
import com.example.ui.viewmodel.RepDayViewModel
import com.example.ui.viewmodel.RestTimerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class NavDest(
    val tab: PrimaryTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val PrimaryDestinations = listOf(
    NavDest(PrimaryTab.ROUTINES, "Routines", Icons.Rounded.FitnessCenter, Icons.Outlined.FitnessCenter),
    NavDest(PrimaryTab.PLANS, "Plans", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    NavDest(PrimaryTab.HISTORY, "History", Icons.Rounded.History, Icons.Outlined.History)
)

@Composable
fun RepDayApp(
    openActiveWorkoutSignal: Int = 0,
    viewModel: RepDayViewModel = viewModel(
        factory = RepDayViewModel.factory(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(openActiveWorkoutSignal) {
        if (openActiveWorkoutSignal > 0) {
            viewModel.openActiveWorkoutFromNotification()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = viewModel.buildBackupJson()
        coroutineScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: error("Unable to open destination file")
                }.isSuccess
            }
            viewModel.showToast(if (ok) "Backup exported" else "Failed to export backup")
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val csv = viewModel.buildHistoryCsv()
        coroutineScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(csv.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: error("Unable to open destination file")
                }.isSuccess
            }
            viewModel.showToast(if (ok) "History exported as CSV" else "Failed to export CSV")
        }
    }

    // Folder picker for automatic backups. Access is kept across restarts.
    val autoBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (AutoBackupWriter.takeFolderAccess(context, uri)) {
            viewModel.onAutoBackupFolderChosen(uri.toString())
        } else {
            viewModel.showToast("Couldn't get access to that folder. Try another one.")
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
            }
            if (content == null) {
                viewModel.showToast("Could not read selected file")
            } else {
                viewModel.stageBackupImportJson(content)
            }
        }
    }

    RepDayTheme(themeConfig = state.settings.theme) {
        val colors = LocalRepDayColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
        ) {
            if (state.isActiveWorkoutFullScreen && state.activeWorkout != null) {
                val active = state.activeWorkout!!
                ActiveWorkoutScreen(
                    workout = active,
                    lastSessionByExercise = state.lastSessionByExercise,
                    history = state.history,
                    exerciseUsage = state.exerciseUsage,
                    exerciseLibrary = state.exerciseLibrary,
                    customExercises = state.customExercises,
                    settings = state.settings,
                    pendingEffortPrompt = state.pendingEffortPrompt,
                    elapsedSecondsFlow = viewModel.elapsedSeconds,
                    restTimerFlow = viewModel.restTimer,
                    onMinimize = viewModel::minimizeActiveWorkout,
                    onOpenPlates = viewModel::openPlateCalculator,
                    onStartManualRest = { sec -> viewModel.startRestTimer(sec, active.routineName) },
                    onAdjustRest = viewModel::adjustRestTimer,
                    onSelectRestPreset = viewModel::selectRestTimerPresetAndSaveDefault,
                    onToggleSound = viewModel::toggleSoundEnabled,
                    onDismissRest = viewModel::dismissRestTimer,
                    onUpdateWeight = viewModel::updateSetWeight,
                    onUpdateReps = viewModel::updateSetReps,
                    onFillFromPrev = viewModel::fillSetFromPrevious,
                    onToggleSetDone = viewModel::toggleSetCompleted,
                    onUpdateSetTagAndRest = viewModel::updateSetTagAndRest,
                    onSetRpe = viewModel::setActiveSetRpe,
                    onDismissEffortPrompt = viewModel::dismissEffortPrompt,
                    onCycleExerciseRest = viewModel::cycleExerciseRest,
                    onAddSet = viewModel::addSetToActiveExercise,
                    onRemoveLastSet = viewModel::removeLastSetFromActiveExercise,
                    onReorderExercises = viewModel::reorderActiveExercises,
                    onAddExercise = viewModel::addExerciseToActiveWorkout,
                    onSwapExercise = viewModel::swapActiveExercise,
                    onSwapExerciseKeepCompleted = viewModel::swapActiveExerciseKeepCompleted,
                    onRemoveExercise = viewModel::removeExerciseFromActiveWorkout,
                    onUpdateNotes = viewModel::updateActiveWorkoutNotes,
                    onFinishWorkout = viewModel::finishActiveWorkout,
                    onDiscardWorkout = viewModel::discardActiveWorkout,
                    onSetLoadType = viewModel::setExerciseLoadType,
                    onAnswerScreenOnPrompt = viewModel::answerScreenOnPrompt
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideLayout = maxWidth >= 720.dp
                    val hasMinimizedWorkout = state.activeWorkout != null && !state.isActiveWorkoutFullScreen
                    val showFabOnPhone = !isWideLayout && (state.selectedTab == PrimaryTab.ROUTINES || state.selectedTab == PrimaryTab.PLANS)

                    if (isWideLayout) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        ) {
                            WideNavigationSidebar(
                                selectedTab = state.selectedTab,
                                activeWorkout = state.activeWorkout,
                                elapsedSecondsFlow = viewModel.elapsedSeconds,
                                restTimerFlow = viewModel.restTimer,
                                onSelectTab = viewModel::selectTab,
                                onResumeWorkout = viewModel::resumeActiveWorkout,
                                onPrimaryCreate = {
                                    when (state.selectedTab) {
                                        PrimaryTab.PLANS -> viewModel.openNewPlanEditor()
                                        else -> viewModel.openNewRoutineEditor()
                                    }
                                }
                            )
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                MainTabContent(
                                    state = state,
                                    viewModel = viewModel,
                                    isWideLayout = true,
                                    bottomContentPadding = 28.dp
                                )
                            }
                        }
                    } else {
                        Scaffold(
                            containerColor = colors.canvas,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.canvas)
                                ) {
                                    AnimatedVisibility(
                                        visible = hasMinimizedWorkout,
                                        enter = fadeIn() + slideInVertically { it },
                                        exit = fadeOut() + slideOutVertically { it }
                                    ) {
                                        state.activeWorkout?.let { active ->
                                            MinimizedWorkoutResumeBar(
                                                workout = active,
                                                elapsedSecondsFlow = viewModel.elapsedSeconds,
                                                restTimerFlow = viewModel.restTimer,
                                                onResume = viewModel::resumeActiveWorkout,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    NavigationBar(
                                        containerColor = colors.surface,
                                        tonalElevation = 0.dp
                                    ) {
                                        PrimaryDestinations.forEach { dest ->
                                            val selected = state.selectedTab == dest.tab
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { viewModel.selectTab(dest.tab) },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                        contentDescription = dest.label
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = dest.label,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                    )
                                                },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = colors.accent,
                                                    selectedTextColor = colors.accent,
                                                    indicatorColor = colors.accentSoft,
                                                    unselectedIconColor = colors.textMuted,
                                                    unselectedTextColor = colors.textMuted
                                                ),
                                                modifier = Modifier.testTag("nav_${dest.tab.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            },
                            floatingActionButton = {
                                if (showFabOnPhone) {
                                    ExtendedFloatingActionButton(
                                        onClick = {
                                            if (state.selectedTab == PrimaryTab.PLANS) {
                                                viewModel.openNewPlanEditor()
                                            } else {
                                                viewModel.openNewRoutineEditor()
                                            }
                                        },
                                        containerColor = colors.accent,
                                        contentColor = colors.accentFg,
                                        shape = CircleShape,
                                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                        text = {
                                            Text(
                                                text = if (state.selectedTab == PrimaryTab.PLANS) "New Plan" else "New Routine",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        },
                                        modifier = Modifier.testTag("primary_create_fab")
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            ) {
                                MainTabContent(
                                    state = state,
                                    viewModel = viewModel,
                                    isWideLayout = false,
                                    bottomContentPadding = if (showFabOnPhone) 88.dp else 24.dp
                                )
                            }
                        }
                    }
                }
            }

            // Replace Active Workout Confirmation Guard
            state.pendingReplaceWorkout?.let { pending ->
                val targetLabel = when (pending) {
                    is PendingWorkoutStart.FromRoutine -> "\"${pending.routine.name}\""
                    PendingWorkoutStart.LiveWorkout -> "a live workout"
                }
                RepDayConfirmDialog(
                    title = "Replace active workout?",
                    body = "Starting $targetLabel will discard your current active workout (${state.activeWorkout?.routineName ?: "session"}). Continue?",
                    confirmLabel = "Replace Workout",
                    onConfirm = viewModel::confirmReplaceActiveWorkout,
                    onDismiss = viewModel::cancelReplaceActiveWorkout
                )
            }

            if (state.showNotificationPermissionPrompt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissNotificationPermissionPrompt,
                    containerColor = colors.surfaceElevated,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = colors.accent
                        )
                    },
                    title = {
                        Text(
                            text = "Rest timer notifications",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.text
                        )
                    },
                    text = {
                        Text(
                            text = "Allow notifications so your rest timer counts down and alerts you with the screen locked.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.dismissNotificationPermissionPrompt()
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.accentFg
                            ),
                            modifier = Modifier.testTag("allow_notifications_button")
                        ) {
                            Text("Allow", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = viewModel::dismissNotificationPermissionPrompt,
                            modifier = Modifier.testTag("skip_notifications_button")
                        ) {
                            Text("Not now", color = colors.textSecondary)
                        }
                    }
                )
            }

            // Modal Bottom Sheets
            state.routineEditor?.let { req ->
                RoutineEditorSheet(
                    request = req,
                    defaultRestSeconds = state.settings.defaultRestSeconds,
                    effortScale = state.settings.effortScale,
                    exerciseLibrary = state.exerciseLibrary,
                    customExercises = state.customExercises,
                    onSave = { routine -> viewModel.saveRoutine(routine, req) },
                    onDismiss = viewModel::closeRoutineEditor,
                    exerciseUsage = state.exerciseUsage,
                    loadTypes = state.settings.exerciseLoadTypes,
                    onSetLoadType = viewModel::setExerciseLoadType
                )
            }

            state.planEditor?.let { req ->
                PlanEditorSheet(
                    request = req,
                    routines = state.routines,
                    onSave = viewModel::savePlan,
                    onDismiss = viewModel::closePlanEditor
                )
            }

            state.editingHistoryWorkout?.let { workout ->
                HistoryWorkoutEditorSheet(
                    workout = workout,
                    exerciseLibrary = state.exerciseLibrary,
                    effortScale = state.settings.effortScale,
                    exerciseLoadTypes = state.settings.exerciseLoadTypes,
                    onSave = viewModel::saveEditedHistoryWorkout,
                    onDismiss = viewModel::closeEditHistoryWorkout
                )
            }

            if (state.showSettingsSheet) {
                SettingsSheet(
                    settings = state.settings,
                    onUpdateSettings = viewModel::updateSettings,
                    onSelectWeightUnit = viewModel::requestWeightUnitChange,
                    onExportBackup = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportBackupLauncher.launch("repday-backup-$stamp.json")
                    },
                    onImportBackup = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onExportHistoryCsv = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportCsvLauncher.launch("repday-history-$stamp.csv")
                    },
                    onRestoreSampleRoutines = viewModel::loadSampleRoutinesAndPlan,
                    onEraseAllData = viewModel::eraseAllData,
                    onDismiss = viewModel::closeSettings,
                    onSetBodyWeight = viewModel::setBodyWeight,
                    autoBackupFolderLabel = remember(state.settings.autoBackupFolderUri) {
                        AutoBackupWriter.folderDisplayName(context, state.settings.autoBackupFolderUri)
                    },
                    onChooseAutoBackupFolder = { autoBackupFolderLauncher.launch(null) },
                    onSetAutoBackupEnabled = viewModel::setAutoBackupEnabled,
                    onBackupNow = viewModel::backUpNow
                )
            }

            state.pendingUnitChange?.let { targetUnit ->
                val currentSessionUnit = state.activeWorkout?.resolvedUnit(state.settings.unit) ?: state.settings.unit
                AlertDialog(
                    onDismissRequest = viewModel::dismissPendingUnitChange,
                    containerColor = colors.surface,
                    shape = RoundedCornerShape(28.dp),
                    title = {
                        Text(
                            text = "Convert the workout in progress to ${targetUnit.label}?",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.text
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Convert updates entered weights in your active workout to ${targetUnit.label} (rounded to the nearest 0.5). Keep leaves the current workout in ${currentSessionUnit.label} while switching the app preference to ${targetUnit.label}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            Button(
                                onClick = viewModel::confirmConvertActiveWorkoutUnit,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.accentFg
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .testTag("convert_active_workout_unit_button")
                            ) {
                                Text("Convert to ${targetUnit.label}", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = viewModel::confirmKeepActiveWorkoutUnit,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, colors.borderStrong),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .testTag("keep_active_workout_unit_button")
                            ) {
                                Text("Keep in ${currentSessionUnit.label}", color = colors.text, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissPendingUnitChange) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                    }
                )
            }

            state.pendingBackupImport?.let { validBackup ->
                BackupImportReviewDialog(
                    validation = validBackup,
                    onMerge = { viewModel.confirmBackupImport(BackupImportMode.MERGE) },
                    onReplace = { viewModel.confirmBackupImport(BackupImportMode.REPLACE) },
                    onDismiss = viewModel::dismissBackupImport
                )
            }

            state.backupImportError?.let { errorMsg ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissBackupImport,
                    containerColor = colors.surface,
                    shape = RoundedCornerShape(28.dp),
                    title = {
                        Text("Invalid backup file", style = MaterialTheme.typography.titleLarge, color = colors.text)
                    },
                    text = {
                        Text(errorMsg, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    },
                    confirmButton = {
                        Button(
                            onClick = viewModel::dismissBackupImport,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.accentFg
                            )
                        ) {
                            Text("OK", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
            }

            if (state.showPlateCalculator) {
                val activeSessionUnit = state.activeWorkout?.resolvedUnit(state.settings.unit) ?: state.settings.unit
                PlateCalculatorSheet(
                    initialUnit = activeSessionUnit,
                    plateSettings = state.settings.plateCalculator,
                    seedWeight = state.plateCalcSeedWeight,
                    onSettingsChange = viewModel::updatePlateCalcSettings,
                    onDismiss = viewModel::closePlateCalculator
                )
            }

            // Centered Top Floating Toast Pill
            FloatingToastPill(
                message = state.toastMessage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun MainTabContent(
    state: com.example.ui.viewmodel.RepDayUiState,
    viewModel: RepDayViewModel,
    isWideLayout: Boolean,
    bottomContentPadding: androidx.compose.ui.unit.Dp
) {
    val colors = LocalRepDayColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = state.showUnreadableRecordsNotice) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.dangerSurface,
                border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("unreadable_records_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = RecordDecodeRules.formatUnreadableBannerMessage(state.unreadableRecordCount),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.danger,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = viewModel::dismissUnreadableRecordsNotice,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("dismiss_unreadable_records_banner")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss unreadable records notice",
                            tint = colors.danger,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.selectedTab) {
        PrimaryTab.ROUTINES -> RoutinesScreen(
            routines = state.routines,
            history = state.history,
            settings = state.settings,
            isWideLayout = isWideLayout,
            bottomContentPadding = bottomContentPadding,
            onOpenSettings = viewModel::openSettings,
            onNewRoutine = viewModel::openNewRoutineEditor,
            onLoadSamples = viewModel::loadSampleRoutinesAndPlan,
            onStartLiveWorkout = viewModel::requestStartLiveWorkout,
            onStartRoutine = viewModel::requestStartRoutine,
            onEditRoutine = viewModel::openEditRoutineEditor,
            onDuplicateRoutine = viewModel::openDuplicateRoutineEditor,
            onDeleteRoutine = viewModel::deleteRoutine,
            onViewRoutineHistory = { r -> viewModel.openHistoryForRoutine(r.id, r.name) },
            onSortFieldClick = viewModel::updateRoutineSortField,
            onToggleSortDirection = viewModel::toggleRoutineSortDirection
        )

        PrimaryTab.PLANS -> PlansScreen(
            plans = state.plans,
            routines = state.routines,
            history = state.history,
            isWideLayout = isWideLayout,
            bottomContentPadding = bottomContentPadding,
            onOpenSettings = viewModel::openSettings,
            onNewPlan = viewModel::openNewPlanEditor,
            onLoadSamples = viewModel::loadSampleRoutinesAndPlan,
            onToggleActive = viewModel::togglePlanActive,
            onRestartPlan = viewModel::restartPlanFromWeekOne,
            onDuplicatePlan = viewModel::openDuplicatePlanEditor,
            onEditPlan = viewModel::openEditPlanEditor,
            onDeletePlan = viewModel::deletePlan,
            onStartRoutine = viewModel::requestStartRoutine,
            onViewRoutineHistory = { r -> viewModel.openHistoryForRoutine(r.id, r.name) },
            onLinkRoutineToSlot = viewModel::linkRoutineToPlanSlot,
            onBuildRoutineForSlot = viewModel::openBuildRoutineForPlanSlot
        )

        PrimaryTab.HISTORY -> HistoryScreen(
            history = state.history,
            routines = state.routines,
            unit = state.settings.unit,
            effortScale = state.settings.effortScale,
            activeFilter = state.historyFilter,
            showSampleHistoryBanner = state.showSampleHistoryBanner,
            sampleHistoryCount = state.sampleHistoryCount,
            bottomContentPadding = bottomContentPadding,
            onSelectFilter = viewModel::setHistoryFilter,
            onOpenSettings = viewModel::openSettings,
            onEditWorkout = viewModel::openEditHistoryWorkout,
            onRepeatWorkout = viewModel::requestRepeatHistoryWorkout,
            onSaveAsRoutine = viewModel::openSaveHistoryAsRoutineEditor,
            onStartLiveWorkout = viewModel::requestStartLiveWorkout,
            onStartRoutine = viewModel::requestStartRoutine,
            onDeleteWorkout = viewModel::deleteHistoryWorkout,
            onRemoveSampleHistory = viewModel::removeSampleHistoryWorkouts,
            onDismissSampleHistoryBanner = viewModel::dismissSampleHistoryBanner,
            onGoToRoutines = { viewModel.selectTab(PrimaryTab.ROUTINES) }
        )
            }
        }
    }
}

@Composable
private fun BackupImportReviewDialog(
    validation: BackupValidationResult.Valid,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRepDayColors.current
    var confirmReplaceStep by remember(validation) { mutableStateOf(false) }

    if (confirmReplaceStep) {
        RepDayConfirmDialog(
            title = "Replace all existing data?",
            body = "This replaces all current routines, plans, workout history, custom exercises, and preferences with the selected backup (${validation.routineCount} routines, ${validation.planCount} plans, ${validation.historyCount} workouts). Any active workout will be cleared.",
            confirmLabel = "Replace All Data",
            dismissLabel = "Back",
            isDestructive = true,
            onConfirm = onReplace,
            onDismiss = { confirmReplaceStep = false }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.surface,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text("Import backup", style = MaterialTheme.typography.titleLarge, color = colors.text)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Backup validated (format v${validation.backup.formatVersion}). Choose how to import:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            BackupCountRow("Routines", validation.routineCount)
                            BackupCountRow("Training plans", validation.planCount)
                            BackupCountRow("Logged workouts", validation.historyCount)
                            BackupCountRow("Custom exercises", validation.customExerciseCount)
                        }
                    }
                    Text(
                        text = "Merge keeps your existing data and skips duplicate IDs. Replace overwrites all data on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onMerge,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.accentFg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("backup_import_merge_button")
                        ) {
                            Text("Merge (skip duplicates)", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = { confirmReplaceStep = true },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, colors.danger),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("backup_import_replace_button")
                        ) {
                            Text("Replace all data", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun BackupCountRow(label: String, count: Int) {
    val colors = LocalRepDayColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Text(
            count.toString(),
            style = MonoTabularStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text)
        )
    }
}

@Composable
private fun WideNavigationSidebar(
    selectedTab: PrimaryTab,
    activeWorkout: ActiveWorkout?,
    elapsedSecondsFlow: StateFlow<Long>,
    restTimerFlow: StateFlow<RestTimerUiState>,
    onSelectTab: (PrimaryTab) -> Unit,
    onResumeWorkout: () -> Unit,
    onPrimaryCreate: () -> Unit
) {
    val colors = LocalRepDayColors.current
    Surface(
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RepDay",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
                PrimaryDestinations.forEach { dest ->
                    val selected = selectedTab == dest.tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) colors.accentSoft else colors.surface)
                            .clickable { onSelectTab(dest.tab) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = null,
                            tint = if (selected) colors.accent else colors.textSecondary
                        )
                        Text(
                            text = dest.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) colors.accentSoftFg else colors.textSecondary
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activeWorkout != null) {
                    MinimizedWorkoutResumeBar(
                        workout = activeWorkout,
                        elapsedSecondsFlow = elapsedSecondsFlow,
                        restTimerFlow = restTimerFlow,
                        onResume = onResumeWorkout,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = onPrimaryCreate,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (selectedTab == PrimaryTab.PLANS) "New Plan" else "New Routine",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimizedWorkoutResumeBar(
    workout: ActiveWorkout,
    elapsedSecondsFlow: StateFlow<Long>,
    restTimerFlow: StateFlow<RestTimerUiState>,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRepDayColors.current
    Surface(
        onClick = onResume,
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.5.dp, colors.accent),
        shadowElevation = 6.dp,
        modifier = modifier.testTag("minimized_workout_resume_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = colors.accentFg, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.routineName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MinimizedWorkoutTimerSubtitle(
                    elapsedSecondsFlow = elapsedSecondsFlow,
                    restTimerFlow = restTimerFlow
                )
            }
            Text(
                text = "Resume",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent
            )
        }
    }
}

@Composable
private fun MinimizedWorkoutTimerSubtitle(
    elapsedSecondsFlow: StateFlow<Long>,
    restTimerFlow: StateFlow<RestTimerUiState>
) {
    val colors = LocalRepDayColors.current
    val elapsedSeconds by elapsedSecondsFlow.collectAsStateWithLifecycle()
    val restTimer by restTimerFlow.collectAsStateWithLifecycle()
    val restSuffix = if (restTimer.isActive && !restTimer.isComplete) {
        " · Rest ${RepDayRules.formatRestTime(restTimer.remainingSeconds)}"
    } else ""
    Text(
        text = "${RepDayRules.formatElapsed(elapsedSeconds)}$restSuffix",
        style = MonoTabularStyle.copy(fontSize = 12.sp, color = colors.accentSoftFg),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
