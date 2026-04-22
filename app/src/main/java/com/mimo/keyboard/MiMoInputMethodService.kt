package com.mimo.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewParent
import android.view.ViewGroup
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.TextView
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
 * CRITICAL FIX: ComposeView inside InputMethodService requires ViewTreeLifecycleOwner
 * to be set on ALL ancestor views, including the system's parentPanel LinearLayout.
 *
 * The previous approach of setting ViewTreeLifecycleOwner only on the ComposeView
 * caused a crash: "ViewTreeLifecycleOwner not found from android.widget.LinearLayout"
 * because Compose's WindowRecomposer walks UP from the window root, not down from
 * the ComposeView.
 *
 * Solution: Return a plain FrameLayout from onCreateInputView(). When the FrameLayout
 * is attached to the window, propagate ViewTreeLifecycleOwner to all ancestor views
 * (including the system's LinearLayout), THEN add the ComposeView. This guarantees
 * that the LifecycleOwner is available BEFORE ComposeView.onAttachedToWindow fires.
 */
class MiMoInputMethodService : InputMethodService() {

    companion object {
        private const val TAG = "MiMoIME"
    }

    // Created once in onCreate, reused across multiple onCreateInputView calls
    private var viewModel: KeyboardViewModel? = null
    private val lifecycleOwner = ServiceLifecycleOwner()
    private var composeView: ComposeView? = null
    private var container: FrameLayout? = null

    // KeyboardSettings for user preferences
    private var keyboardSettings: KeyboardSettings? = null

    override fun onCreate() {
        super.onCreate()
        keyboardSettings = KeyboardSettings(this)
        viewModel = KeyboardViewModel(keyboardSettings)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        // If we already have a container, detach and reuse it
        val existingContainer = container
        if (existingContainer != null) {
            (existingContainer.parent as? ViewGroup)?.removeView(existingContainer)

            // Re-add attach listener to propagate owners again when re-attached
            ensureOwnersPropagated(existingContainer)

            if (existingContainer.parent == null) {
                return existingContainer
            }
        }

        // Ensure lifecycle is at least STARTED
        val currentState = lifecycleOwner.lifecycle.currentState
        if (!currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        if (!currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        val vm = viewModel ?: KeyboardViewModel(keyboardSettings).also { viewModel = it }
        val settings = keyboardSettings ?: KeyboardSettings(this).also { keyboardSettings = it }
        val density = resources.displayMetrics.density

        // Create a plain FrameLayout as the root view we return to the system.
        // We do NOT return a ComposeView directly — instead we add it later
        // AFTER propagating ViewTreeLifecycleOwner to ancestor views.
        val frame = FrameLayout(this).apply {
            minimumHeight = (270 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // Set owners on our container using extension function syntax
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }
        container = frame

        // When the FrameLayout is attached to the window, propagate owners
        // to ALL ancestor views (including the system's LinearLayout parentPanel),
        // THEN add the ComposeView. This ensures ViewTreeLifecycleOwner is
        // available BEFORE ComposeView.onAttachedToWindow fires.
        ensureOwnersPropagated(frame, vm, settings)

        return frame
    }

    /**
     * Installs an OnAttachStateChangeListener that:
     * 1. Propagates ViewTreeLifecycleOwner to all ancestor views
     * 2. Adds the ComposeView to the container
     *
     * This is the KEY fix for the "ViewTreeLifecycleOwner not found" crash.
     * The system wraps our view in a LinearLayout (parentPanel), and Compose's
     * WindowRecomposer searches for the LifecycleOwner starting from that
     * LinearLayout. Without setting it there, the search fails and crashes.
     */
    private fun ensureOwnersPropagated(
        frame: FrameLayout,
        vm: KeyboardViewModel? = null,
        settings: KeyboardSettings? = null
    ) {
        frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                try {
                    // Propagate ViewTreeLifecycleOwner to ALL ancestor views.
                    // The system adds our FrameLayout as a child of a LinearLayout
                    // (id=parentPanel). Compose's WindowRecomposer starts its search
                    // from the window root, so we MUST set the owner on every ancestor.
                    var currentParent: ViewParent? = v.parent
                    while (currentParent is View) {
                        val parentView = currentParent as View
                        parentView.setViewTreeLifecycleOwner(lifecycleOwner)
                        parentView.setViewTreeViewModelStoreOwner(lifecycleOwner)
                        parentView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                        currentParent = parentView.parent
                    }

                    // Now that owners are set on all ancestors, add the ComposeView.
                    // ComposeView.onAttachedToWindow will fire, and it will find
                    // the ViewTreeLifecycleOwner on the parent LinearLayout.
                    val existingCompose = composeView
                    if (existingCompose != null && existingCompose.parent == null) {
                        frame.addView(existingCompose)
                    } else if (composeView == null) {
                        val currentVm = vm
                            ?: viewModel
                            ?: KeyboardViewModel(keyboardSettings).also { viewModel = it }
                        val currentSettings = settings
                            ?: keyboardSettings
                            ?: KeyboardSettings(this@MiMoInputMethodService).also { keyboardSettings = it }

                        val newComposeView = ComposeView(this@MiMoInputMethodService).apply {
                            setContent {
                                HorizonKeyboardTheme {
                                    KeyboardScreen(viewModel = currentVm, settings = currentSettings)
                                }
                            }
                        }
                        composeView = newComposeView
                        frame.addView(newComposeView)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onViewAttachedToWindow", e)
                    // If ComposeView fails, show a plain error message
                    showErrorInView(frame, e)
                }

                frame.removeOnAttachStateChangeListener(this)
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }

    /**
     * Shows an error message inside the FrameLayout using a plain TextView.
     * This works even when Compose fails to initialize.
     */
    private fun showErrorInView(frame: FrameLayout, e: Exception) {
        try {
            frame.removeAllViews()
            val errorText = TextView(this@MiMoInputMethodService).apply {
                text = "Horizon KB Error:\n${e.javaClass.simpleName}: ${e.message}\n\nCheck logcat for details."
                setTextColor(0xFFFF453A.toInt()) // Red
                setTextSize(12f)
                setPadding(16, 16, 16, 16)
                setTextIsSelectable(true)
            }
            frame.addView(errorText)
        } catch (e2: Exception) {
            Log.e(TAG, "Failed to show error view", e2)
        }
    }

    override fun onStartInput(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)
        viewModel?.inputConnection = currentInputConnection

        if (!restarting) {
            viewModel?.reset()
            val ic = currentInputConnection
            if (ic != null) {
                viewModel?.syncFromInputConnection(ic)
            }
        } else {
            val ic = currentInputConnection
            if (ic != null) {
                viewModel?.syncFromInputConnection(ic)
            }
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        viewModel?.inputConnection = currentInputConnection

        currentInputConnection?.requestCursorUpdates(
            InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR
        )

        // Ensure lifecycle is RESUMED when keyboard is visible
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

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

        lifecycleOwner.performSave()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        lifecycleOwner.viewModelStore.clear()
        viewModel = null
        composeView = null
        container = null
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

        fun performSave() {
            savedStateController.performSave(Bundle())
        }
    }
}
