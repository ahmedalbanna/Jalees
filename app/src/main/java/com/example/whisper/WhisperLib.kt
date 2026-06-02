package com.example.whisper

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A wrapper class for interacting with the Whisper.cpp native library via JNI.
 */
class WhisperLib {

    companion object {
        init {
            // Load the native library when the class is loaded
            System.loadLibrary("whisper_jni")
        }
    }

    /**
     * Initializes the Whisper context with the given model path.
     * @param modelPath The absolute path to the Whisper GGML model file on the device.
     * @return the context pointer address (0L if failed).
     */
    external fun initContext(modelPath: String): Long

    /**
     * Frees the Whisper context and releases memory.
     * @param contextPtr The pointer to the context returned by initContext.
     */
    external fun freeContext(contextPtr: Long)

    /**
     * Transcribes an array of floating-point audio samples (16kHz, mono).
     * @param contextPtr The pointer to the Whisper context.
     * @param audioData The audio samples.
     * @return The transcribed text as a String.
     */
    external fun transcribeAudio(contextPtr: Long, audioData: FloatArray): String
    
    // Asynchronous wrapper for suspending functions in coroutines
    suspend fun transcribeAudioAsync(contextPtr: Long, audioData: FloatArray): String {
        return withContext(Dispatchers.Default) {
            transcribeAudio(contextPtr, audioData)
        }
    }
}
