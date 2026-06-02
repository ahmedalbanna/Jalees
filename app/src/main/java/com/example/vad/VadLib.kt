package com.example.vad

class VadLib {
    companion object {
        init {
            System.loadLibrary("vad_jni")
        }
    }

    /**
     * Initializes the VAD context.
     * @return a pointer to the VAD instance.
     */
    external fun initVad(): Long

    /**
     * Frees the VAD context.
     * @param vadPtr pointer to VAD instance.
     */
    external fun freeVad(vadPtr: Long)

    /**
     * Processes a 10ms, 20ms or 30ms frame of 16kHz audio to detect voice.
     * @param vadPtr pointer to VAD instance.
     * @param audioFrame 16-bit PCM audio frame.
     * @return true if voice is detected, false otherwise.
     */
    external fun isSpeech(vadPtr: Long, audioFrame: ShortArray): Boolean
}
