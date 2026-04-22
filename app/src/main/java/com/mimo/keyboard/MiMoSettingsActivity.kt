package com.mimo.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
 */
class MiMoSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HorizonKeyboardTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current

    // Re-check status every time the composition activates
    var isKeyboardEnabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var isKeyboardSelected by remember { mutableStateOf(isKeyboardSelected(context)) }

    // Re-check when activity resumes (user returns from system settings)
    // Using DisposableEffect with lifecycle events for compatibility
    val lifecycleOwner = remember {
        object : androidx.lifecycle.LifecycleOwner {
            override val lifecycle = androidx.lifecycle.LifecycleRegistry(this)
        }
    }

    DisposableEffect(lifecycleOwner) {
        // Re-check immediately when this effect enters composition
        isKeyboardEnabled = isKeyboardEnabled(context)
        isKeyboardSelected = isKeyboardSelected(context)
        onDispose { }
    }

    // Also re-check periodically while the screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            isKeyboardEnabled = isKeyboardEnabled(context)
            isKeyboardSelected = isKeyboardSelected(context)
            kotlinx.coroutines.delay(1000)
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
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
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
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "v1.4.0",
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
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabledInputMethods = imm.enabledInputMethodList
    return enabledInputMethods.any {
        it.packageName == context.packageName
    }
}

/**
 * Checks if the Horizon Keyboard IME is currently selected as the active input method.
 */
private fun isKeyboardSelected(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabledInputMethods = imm.enabledInputMethodList
    val selectedId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD
    )
    return enabledInputMethods.any {
        it.packageName == context.packageName && it.id == selectedId
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
