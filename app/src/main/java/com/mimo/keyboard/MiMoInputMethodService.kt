package com.mimo.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mimo.keyboard.ui.KeyboardScreen
import com.mimo.keyboard.ui.theme.HorizonKeyboardTheme

/**
 * The main InputMethodService for the Horizon Keyboard.
 *
 * IMPORTANT: ComposeView inside InputMethodService requires careful lifecycle
 * management. The view must have ViewTreeLifecycleOwner, ViewTreeViewModelStoreOwner,
 * and ViewTreeSavedStateRegistryOwner set BEFORE setContent is called.
 *
 * The TerminalPanel uses a non-focusable text display instead of BasicTextField
 * to prevent recursive IME crashes.
 *
 * FIX LOG:
 * - Fixed: reset() now called on ALL field switches, not just restarting=true
 * - Fixed: inputConnection cleared in onFinishInput to prevent stale connection usage
 * - Fixed: SavedStateRegistryController.performSave() called on destroy
 */
class MiMoInputMethodService : InputMethodService() {

    // Created once in onCreate, reused across multiple onCreateInputView calls
    private var viewModel: KeyboardViewModel? = null
    private val lifecycleOwner = ServiceLifecycleOwner()
    private var composeView: ComposeView? = null

    // FIX: Create KeyboardSettings instance so it can be passed to the UI layer.
    // Previously, SettingsPanel wrote to KeyboardSettings but no other component
    // read from it — toggling haptics, suggestions, auto-capitalize had no effect.
    private var keyboardSettings: KeyboardSettings? = null

    override fun onCreate() {
        super.onCreate()
        keyboardSettings = KeyboardSettings(this)
        // FIX: Pass KeyboardSettings to ViewModel so it can respect user preferences
        // for auto-capitalize, auto-space, and show suggestions.
        viewModel = KeyboardViewModel(keyboardSettings)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        // If we already have a compose view, return it (Android may call this multiple times)
        val existingView = composeView
        if (existingView != null) {
            // If old ComposeView is still attached to a parent,
            // remove it first to prevent duplicate views and memory leaks
            if (existingView.parent is ViewGroup) {
                (existingView.parent as ViewGroup).removeView(existingView)
            }
            // Reuse the existing view if now detached
            if (existingView.parent == null) {
                return existingView
            }
        }

        // Ensure lifecycle is at least CREATED
        val currentState = lifecycleOwner.lifecycle.currentState
        if (!currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        // Move to STARTED if not already
        if (!currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        val vm = viewModel ?: KeyboardViewModel(keyboardSettings).also { viewModel = it }
        val settings = keyboardSettings ?: KeyboardSettings(this).also { keyboardSettings = it }

        val newView = ComposeView(this).apply {
            // Set minimum height to prevent ComposeView from
            // measuring to 0 on first layout pass (before composition completes).
            val density = resources.displayMetrics.density
            setMinimumHeight((270 * density).toInt())

            // Set layout params to ensure proper sizing
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

            // These MUST be set BEFORE setContent
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                HorizonKeyboardTheme {
                    // FIX: Pass KeyboardSettings so the UI can respect user preferences
                    // for haptics, suggestions, auto-capitalize, and auto-space.
                    KeyboardScreen(viewModel = vm, settings = settings)
                }
            }
        }

        composeView = newView

        // Move to RESUMED after view creation
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        return newView
    }

    override fun onStartInput(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)
        viewModel?.inputConnection = currentInputConnection

        // FIX: Reset ViewModel state on ALL field switches, not just when restarting=true.
        // Previously, switching between different input fields without restarting=true
        // would leak stale textValue and suggestions from the previous field.
        // This is safe because the InputConnection is refreshed for every new field.
        viewModel?.reset()
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        viewModel?.inputConnection = currentInputConnection

        // FIX: Request cursor updates so we know when the user taps to move
        // the cursor in the text field. Without this, suggestions become stale
        // after cursor movement because the ViewModel doesn't know the position changed.
        currentInputConnection?.requestCursorUpdates(
            CursorAnchorInfo.FLAG_GET_CURSOR_POSITION
        )

        // Ensure lifecycle is RESUMED when keyboard is visible
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    /**
     * FIX: Called when the cursor position changes in the input field.
     * Without this override, tapping in a text field to reposition the cursor
     * would not update the ViewModel's cursorPosition or textValue, causing
     * stale/wrong suggestions to be shown.
     *
     * We re-sync textValue and cursorPosition from the InputConnection here
     * so that suggestions are always based on the text around the actual cursor.
     */
    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        if (cursorAnchorInfo != null) {
            val ic = currentInputConnection
            if (ic != null) {
                viewModel?.syncFromInputConnection(ic)
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Pause lifecycle when keyboard is hidden
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // FIX: Clear the inputConnection reference when the input finishes
        // to prevent the ViewModel from using a stale/disconnected connection.
        viewModel?.inputConnection = null
    }

    override fun onDestroy() {
        // Properly stop lifecycle before destroy
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }

        // FIX: Perform save on SavedStateRegistry before destroy so that
        // Compose state that relies on saved state can be properly persisted.
        lifecycleOwner.performSave()

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        // Clear ViewModelStore to prevent memory leaks
        lifecycleOwner.viewModelStore.clear()
        viewModel = null
        composeView = null
        super.onDestroy()
    }

    /**
     * Custom LifecycleOwner for the InputMethodService.
     * Implements all three owner interfaces that ComposeView requires.
     *
     * FIX: Added performSave() method to properly save state before destroy.
     */
    private class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)

        init {
            savedStateController.performRestore(null)
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val viewModelStore: ViewModelStore
            get() = store

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        /**
         * FIX: Saves state before destroy. This ensures Compose components
         * that use rememberSaveable can properly persist their state.
         */
        fun performSave() {
            savedStateController.performSave(Bundle())
        }
    }
}
