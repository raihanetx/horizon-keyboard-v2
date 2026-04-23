package com.mimo.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Wrapper around Android's built-in SpeechRecognizer for voice typing.
 *
 * Supports English (en-US) and Bangla (bn-BD) via the voiceLanguage setting.
 * Uses the device's built-in speech recognition engine — no third-party
 * services required.
 *
 * Usage:
 * 1. Call startListening() to begin voice recognition
 * 2. Callbacks update the ViewModel's voice state
 * 3. Call stopListening() to end the session
 * 4. Call destroy() in the service's onDestroy()
 *
 * Note: RECORD_AUDIO permission must be granted before calling startListening().
 * The caller is responsible for checking/requesting the permission.
 */
class VoiceRecognizer(
    private val context: Context,
    private val viewModel: KeyboardViewModel
) {
    companion object {
        private const val TAG = "VoiceRecognizer"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts listening for voice input using Android's built-in SpeechRecognizer.
     * Uses the language currently set in viewModel.voiceLanguage.
     */
    fun startListening() {
        if (!isAvailable) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }

        // Stop any existing session
        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(HorizonRecognitionListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, viewModel.voiceLanguage)
                // Also set the preferred language as a hint
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, viewModel.voiceLanguage)
                // Partial results for live feedback
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Max results
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Call package for the recognizer
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            viewModel.setVoiceListening(true)
            Log.d(TAG, "Started listening with language: ${viewModel.voiceLanguage}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            viewModel.setVoiceListening(false)
        }
    }

    /**
     * Stops listening and cleans up the current session.
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
        speechRecognizer = null
        viewModel.setVoiceListening(false)
    }

    /**
     * Fully destroys the recognizer. Call this in the service's onDestroy().
     */
    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognition", e)
        }
        speechRecognizer = null
        viewModel.setVoiceListening(false)
    }

    /**
     * RecognitionListener that bridges Android's SpeechRecognizer callbacks
     * to the ViewModel's voice typing state.
     */
    private inner class HorizonRecognitionListener : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            viewModel.setVoiceListening(true)
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Could be used for volume animation — currently not needed
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Not used
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected")
            viewModel.setVoiceListening(false)
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                else -> "Unknown error ($error)"
            }
            Log.e(TAG, "Speech recognition error: $errorMessage")
            viewModel.setVoiceListening(false)
            viewModel.setVoiceRecognizedText("")
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val bestMatch = matches?.firstOrNull() ?: ""
            Log.d(TAG, "Final result: $bestMatch")

            if (bestMatch.isNotEmpty()) {
                viewModel.commitVoiceResult(bestMatch)
            }
            viewModel.setVoiceListening(false)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull() ?: ""
            if (partialText.isNotEmpty()) {
                viewModel.setVoiceRecognizedText(partialText)
            }
            Log.d(TAG, "Partial result: $partialText")
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Not used
        }
    }
}
