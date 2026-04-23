package com.mimo.keyboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mimo.keyboard.ui.theme.HorizonColors
import com.mimo.keyboard.ui.theme.HorizonKeyboardTheme

/**
 * Settings Activity for the Horizon Keyboard.
 *
 * This activity serves as the app's launcher entry point.
 * It guides users through the two-step setup process:
 *
 * 1. Enable Horizon Keyboard in Input Method settings
 * 2. Select Horizon Keyboard as the active input method
 *
 * CRASH SAFETY: A global uncaught exception handler is installed
 * that shows any crash error directly on screen so users can
 * read and copy the error message.
 */
class MiMoSettingsActivity : ComponentActivity() {

    companion object {
        // Global error message that persists across activity recreation
        var lastCrashError: String? = null
    }

    // Permission request launcher for microphone access
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // After the user responds, finish this activity to return to the keyboard
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install crash handler that shows errors on screen
        installCrashHandler()

        // Check if this activity was launched to request mic permission
        val requestMicPermission = intent?.getBooleanExtra("request_mic_permission", false) ?: false
        if (requestMicPermission) {
            // Directly request the microphone permission
            requestMicPermissionAndFinish()
            return
        }

        // Check if there was a previous crash
        val crashError = lastCrashError
        if (crashError != null) {
            lastCrashError = null
            showErrorScreen(crashError)
            return
        }

        try {
            setContent {
                HorizonKeyboardTheme {
                    SettingsScreen()
                }
            }
        } catch (e: Exception) {
            // If Compose fails entirely, show error in a plain Android view
            showErrorScreen(formatException(e))
        }
    }

    /**
     * Requests the RECORD_AUDIO permission and finishes the activity.
     * If the permission is already granted, just finishes.
     * This is called when the keyboard's voice panel needs mic access.
     */
    private fun requestMicPermissionAndFinish() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            // Already granted, just finish
            finish()
            return
        }

        // Request the permission — the launcher callback will finish() the activity
        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Shows the error in a plain Android TextView (no Compose dependency)
     * so the user can read and copy the error message.
     */
    private fun showErrorScreen(errorText: String) {
        try {
            val scrollView = ScrollView(this)
            val textView = TextView(this).apply {
                text = "ERROR (you can select & copy):\n\n$errorText"
                setTextColor(0xFFFF453A.toInt()) // Red
                setTextSize(14f)
                setPadding(48, 48, 48, 48)
                setTextIsSelectable(true) // Allow copy
            }
            scrollView.addView(textView)
            setContentView(scrollView)
        } catch (e2: Exception) {
            // Absolute fallback - should never happen
            setContentView(TextView(this).apply {
                text = "Fatal: ${e2.message}"
                setTextIsSelectable(true)
            })
        }
    }

    private fun formatException(e: Throwable): String {
        val sb = StringBuilder()
        sb.append(e.javaClass.simpleName)
        sb.append(": ")
        sb.append(e.message)
        sb.append("\n\n")
        for (element in e.stackTrace.take(15)) {
            sb.append("  at ")
            sb.append(element.toString())
            sb.append("\n")
        }
        val cause = e.cause
        if (cause != null) {
            sb.append("\nCaused by: ")
            sb.append(cause.javaClass.simpleName)
            sb.append(": ")
            sb.append(cause.message)
            sb.append("\n")
            for (element in cause.stackTrace.take(10)) {
                sb.append("  at ")
                sb.append(element.toString())
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    private fun installCrashHandler() {
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Save the error so we can show it on next launch
            lastCrashError = formatException(throwable)
            // Let the default handler do its thing (write to logcat etc.)
            currentHandler?.uncaughtException(thread, throwable)
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current

    // Track keyboard status
    var isKeyboardEnabled by remember { mutableStateOf(false) }
    var isKeyboardSelected by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Check keyboard status safely
    fun checkStatus() {
        try {
            isKeyboardEnabled = isKeyboardEnabled(context)
            isKeyboardSelected = isKeyboardSelected(context)
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Status check error: ${e.message}"
        }
    }

    // Check on first composition
    LaunchedEffect(Unit) {
        checkStatus()
    }

    // Re-check periodically while screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            checkStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HorizonColors.PureBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Horizon",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = HorizonColors.TextPrimary,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Keyboard",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = HorizonColors.Accent,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Show error if any
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                fontSize = 12.sp,
                color = HorizonColors.Error,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Overall status indicator
        val statusText = when {
            isKeyboardEnabled && isKeyboardSelected ->
                "Keyboard active and ready!"
            isKeyboardEnabled ->
                "Keyboard enabled but not selected. Tap Step 2."
            else ->
                "Follow the steps below to set up."
        }

        val statusColor = when {
            isKeyboardEnabled && isKeyboardSelected -> HorizonColors.TerminalGreen
            isKeyboardEnabled -> HorizonColors.Accent
            else -> HorizonColors.TextMuted
        }

        Text(
            text = statusText,
            fontSize = 13.sp,
            color = statusColor,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Step 1: Enable keyboard
        SettingsCard(
            stepNumber = "1",
            title = "Enable Keyboard",
            description = "Open Input Method settings and enable Horizon Keyboard",
            isComplete = isKeyboardEnabled,
            onClick = {
                try {
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    errorMessage = "Cannot open settings: ${e.message}"
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 2: Select keyboard
        SettingsCard(
            stepNumber = "2",
            title = "Select Keyboard",
            description = "Choose Horizon as your active input method",
            isComplete = isKeyboardSelected,
            onClick = {
                try {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e2: Exception) {
                        errorMessage = "Cannot open picker: ${e2.message}"
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "v1.8.0",
            fontSize = 12.sp,
            color = HorizonColors.TextExtraMuted,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Checks if the Horizon Keyboard IME is enabled in system settings.
 */
private fun isKeyboardEnabled(context: Context): Boolean {
    return try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    } catch (e: Exception) {
        false
    }
}

/**
 * Checks if the Horizon Keyboard IME is currently selected as the active input method.
 */
private fun isKeyboardSelected(context: Context): Boolean {
    return try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val selectedId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        imm.enabledInputMethodList.any {
            it.packageName == context.packageName && it.id == selectedId
        }
    } catch (e: Exception) {
        false
    }
}

@Composable
private fun SettingsCard(
    stepNumber: String,
    title: String,
    description: String,
    isComplete: Boolean = false,
    onClick: () -> Unit
) {
    val cardBackground = if (isComplete) {
        HorizonColors.Accent.copy(alpha = 0.15f)
    } else {
        HorizonColors.KeyboardSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBackground, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step number circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isComplete) HorizonColors.TerminalGreen else HorizonColors.Accent,
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isComplete) "\u2713" else stepNumber,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HorizonColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = if (isComplete) "$title \u2713" else title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = HorizonColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = HorizonColors.TextMuted,
                lineHeight = 18.sp
            )
        }
    }
}
