#include <jni.h>
#include <android/log.h>

#define TAG "VadJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// Placeholder struct for VAD context
struct vad_context {
    int mode;
};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_vad_VadLib_initVad(JNIEnv *env, jobject thiz, jint mode) {
    LOGI("Initializing WebRTC VAD context with mode %d", mode);
    
    // In actual implementation:
    // WebRtcVad_Create(&vad_inst);
    // WebRtcVad_Init(vad_inst);
    // WebRtcVad_set_mode(vad_inst, mode);
    
    struct vad_context * ctx = new vad_context;
    // ensure mode is within a valid range in real scenario
    ctx->mode = mode;
    
    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_vad_VadLib_freeVad(JNIEnv *env, jobject thiz, jlong vad_ptr) {
    if (vad_ptr == 0) return;
    
    LOGI("Freeing VAD context");
    struct vad_context * ctx = reinterpret_cast<struct vad_context *>(vad_ptr);
    
    // In actual implementation:
    // WebRtcVad_Free(vad_inst);
    
    delete ctx;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_vad_VadLib_isSpeech(JNIEnv *env, jobject thiz, jlong vad_ptr,
                                     jshortArray audio_frame) {
    if (vad_ptr == 0) return JNI_FALSE;
    
    jsize len = env->GetArrayLength(audio_frame);
    jshort *samples = env->GetShortArrayElements(audio_frame, nullptr);
    
    // In actual implementation:
    // int is_speech = WebRtcVad_Process(vad_inst, 16000, samples, len);
    
    // Mock VAD logic: detect speech if energy is above a certain threshold
    long long energy = 0;
    for(int i = 0; i < len; i++) {
        energy += samples[i] * samples[i];
    }
    
    env->ReleaseShortArrayElements(audio_frame, samples, 0);
    
    // We expect frames to be 10ms (160 samples at 16kHz)
    // Using a simulation threshold
    return (energy > 500000) ? JNI_TRUE : JNI_FALSE;
}
