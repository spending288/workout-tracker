package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.SecureFlagPolicy

/**
 * Sheet properties for editors that can hold unsaved changes (routine, plan, history workout).
 *
 * On Android 13+ with predictive back (the default for apps targeting Android 16), Material 3's
 * ModalBottomSheet registers its own high-priority back callback. That callback runs before the
 * keyboard's own "hide" handling and before any BackHandler inside the sheet, and it can call an
 * out-of-date dismiss lambda. The result on a Pixel: minimizing the keyboard or pressing back
 * closed the editor without the "Discard changes?" prompt. Older emulators don't use that path,
 * so it looked fine there.
 *
 * Turning off the sheet's own back handling lets the keyboard hide normally and routes back
 * presses to [EditorSheetBackHandler] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun editorSheetProperties(): ModalBottomSheetProperties =
    ModalBottomSheetProperties(
        securePolicy = SecureFlagPolicy.Inherit,
        shouldDismissOnBackPress = false
    )

/**
 * Back handling for editor sheets. Place it inside the ModalBottomSheet content and pair it with
 * [editorSheetProperties].
 *
 * - Keyboard open: back only hides the keyboard.
 * - Keyboard closed: back calls [onCloseRequest], which should ask before discarding changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorSheetBackHandler(onCloseRequest: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    val latestOnCloseRequest by rememberUpdatedState(onCloseRequest)
    BackHandler {
        if (imeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            focusManager.clearFocus()
            latestOnCloseRequest()
        }
    }
}
