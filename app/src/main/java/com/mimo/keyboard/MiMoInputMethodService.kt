package com.mimo.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
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
    private var container: ViewGroup? = null

    // KeyboardSettings for user preferences
    private var keyboardSettings: KeyboardSettings? = null

    // VoiceRecognizer for voice typing
    private var voiceRecognizer: VoiceRecognizer? = null

    // Callback for requesting mic permission from the keyboard UI
    var onRequestMicPermission: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        keyboardSettings = KeyboardSettings(this)
        viewModel = KeyboardViewModel(keyboardSettings)
        voiceRecognizer = VoiceRecognizer(this, viewModel!!)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // Set up the mic permission request callback
        onRequestMicPermission = {
            requestMicPermission()
        }
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

        // Create a FrameLayout that wraps the ComposeView.
        // The ComposeView content controls its own height (WRAP_CONTENT),
        // so the keyboard is only as tall as it needs to be.
        // Gravity.BOTTOM ensures the keyboard anchors to the bottom.
        val frame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }
        container = frame

        // When the FrameLayout is attached to the window, propagate owners
        // to ALL ancestor views, THEN add the ComposeView.
        ensureOwnersPropagated(frame, vm, settings)

        return frame
    }

    /**
     * Installs an OnAttachStateChangeListener that:
     * 1. Propagates ViewTreeLifecycleOwner to all ancestor views
     * 2. Adds the ComposeView to the container
     */
    private fun ensureOwnersPropagated(
        frame: ViewGroup,
        vm: KeyboardViewModel? = null,
        settings: KeyboardSettings? = null
    ) {
        frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                try {
                    // Propagate ViewTreeLifecycleOwner to ALL ancestor views.
                    var currentParent: ViewParent? = v.parent
                    while (currentParent is View) {
                        val parentView = currentParent as View
                        parentView.setViewTreeLifecycleOwner(lifecycleOwner)
                        parentView.setViewTreeViewModelStoreOwner(lifecycleOwner)
                        parentView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                        currentParent = parentView.parent
                    }

                    // Now that owners are set on all ancestors, add the ComposeView.
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
                                    KeyboardScreen(
                                        viewModel = currentVm,
                                        settings = currentSettings,
                                        voiceRecognizer = voiceRecognizer,
                                        inputMethodService = this@MiMoInputMethodService
                                    )
                                }
                            }
                            // WRAP_CONTENT so the ComposeView is only as tall as its content
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.BOTTOM
                            )
                        }
                        composeView = newComposeView
                        frame.addView(newComposeView)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onViewAttachedToWindow", e)
                    showErrorInView(frame, e)
                }

                frame.removeOnAttachStateChangeListener(this)
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }

    /**
     * Shows an error message inside the FrameLayout using a plain TextView.
     */
    private fun showErrorInView(frame: ViewGroup, e: Exception) {
        try {
            frame.removeAllViews()
            val errorText = TextView(this@MiMoInputMethodService).apply {
                text = "Horizon KB Error:\n${e.javaClass.simpleName}: ${e.message}\n\nCheck logcat for details."
                setTextColor(0xFFFF453A.toInt())
                setTextSize(12f)
                setPadding(16, 16, 16, 16)
                setTextIsSelectable(true)
            }
            frame.addView(errorText)
        } catch (e2: Exception) {
            Log.e(TAG, "Failed to show error view", e2)
        }
    }

    /**
     * Requests microphone permission by launching the settings activity
     * which handles the runtime permission request.
     * InputMethodService cannot directly use requestPermissions()
     * since it's not an Activity, so we delegate to our settings activity.
     */
    private fun requestMicPermission() {
        try {
            val intent = Intent(this, MiMoSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("request_mic_permission", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch permission request activity", e)
            // Fallback: open app settings
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open app settings too", e2)
            }
        }
    }

    /**
     * Prevent fullscreen mode — the keyboard must stay at the bottom,
     * not take over the entire screen.
     */
    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    /**
     * Compute insets so the system knows exactly where the keyboard
     * content is. This is critical for touch event delivery — if the
     * touchable region doesn't cover the toolbar, taps on Translate/
     * Voice/Clipboard/Settings icons will be routed to the app behind
     * the keyboard instead of the keyboard itself.
     *
     * We use the INPUT VIEW's actual position on screen to compute
     * insets, which is more reliable than estimating heights.
     */
    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (outInsets != null) {
            // Use our container view's actual screen position to compute insets.
            // This is critical for touch event delivery — if the touchable region
            // doesn't cover the toolbar, taps on Translate/Voice/Clipboard/Settings
            // icons will be routed to the app behind the keyboard instead.
            val containerView = container
            if (containerView != null && containerView.isShown) {
                val location = IntArray(2)
                containerView.getLocationOnScreen(location)
                val viewTop = location[1]

                outInsets.contentTopInsets = viewTop
                outInsets.visibleTopInsets = viewTop

                // Touchable region covers from top of our view to bottom of screen.
                // This ensures the toolbar receives touch events.
                outInsets.touchableRegion.set(
                    0, viewTop,
                    resources.displayMetrics.widthPixels,
                    resources.displayMetrics.heightPixels
                )
            }
            // If container not yet laid out, let super's default handle it
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
        // Stop voice recognition when keyboard hides
        voiceRecognizer?.stopListening()
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
        // Destroy voice recognizer
        voiceRecognizer?.destroy()
        voiceRecognizer = null

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
