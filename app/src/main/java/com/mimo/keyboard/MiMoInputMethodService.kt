package com.mimo.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
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
 */
class MiMoInputMethodService : InputMethodService() {

    // Created once in onCreate, reused across multiple onCreateInputView calls
    private var viewModel: KeyboardViewModel? = null
    private val lifecycleOwner = ServiceLifecycleOwner()
    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        viewModel = KeyboardViewModel()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        // If we already have a compose view, return it (Android may call this multiple times)
        val existingView = composeView
        if (existingView != null) {
            // BUG FIX #5: If old ComposeView is still attached to a parent,
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

        val vm = viewModel ?: KeyboardViewModel().also { viewModel = it }

        val newView = ComposeView(this).apply {
            // BUG FIX #4: Set minimum height to prevent ComposeView from
            // measuring to 0 on first layout pass (before composition completes).
            // This ensures the keyboard is always visible, even on devices
            // where the InputMethodService parent doesn't re-layout after
            // ComposeView's first composition.
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
                    KeyboardScreen(viewModel = vm)
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

        // BUG FIX #1: Reset ViewModel state when switching to a new input field.
        // Without this, textValue and suggestions from the previous field leak
        // into the new field, causing wrong suggestions and stale terminal text.
        if (restarting) {
            viewModel?.reset()
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        viewModel?.inputConnection = currentInputConnection

        // Ensure lifecycle is RESUMED when keyboard is visible
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
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
        // Don't fully reset — just clear the suggestion state
        // Text state is managed by the InputConnection, not us
    }

    override fun onDestroy() {
        // Properly stop lifecycle before destroy
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
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
    }
}
