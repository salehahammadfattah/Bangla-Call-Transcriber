// JNI ব্রিজ: Kotlin <-> whisper.cpp
// এই ফাইলটা মডেল লোড করা এবং অডিও চাংক ট্রান্সক্রাইব করার কাজ করে।

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "whisper.h"

#define LOG_TAG "BanglaTranscriberNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static struct whisper_context *g_ctx = nullptr;

extern "C" JNIEXPORT jlong JNICALL
Java_com_banglatranscriber_app_WhisperBridge_nativeInit(
        JNIEnv *env, jobject /*thiz*/, jstring modelPath) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("মডেল লোড হচ্ছে: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    // GPU নেই ধরে নিয়ে CPU-তে চালানো; ফোনে GPU/NNAPI সাপোর্ট থাকলে এখানে true করা যায়
    cparams.use_gpu = false;

    g_ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (g_ctx == nullptr) {
        LOGE("মডেল লোড ব্যর্থ হয়েছে");
        return 0;
    }
    LOGI("মডেল সফলভাবে লোড হয়েছে");
    return reinterpret_cast<jlong>(g_ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_banglatranscriber_app_WhisperBridge_nativeTranscribe(
        JNIEnv *env, jobject /*thiz*/, jfloatArray audioData, jint numSamples) {

    if (g_ctx == nullptr) {
        return env->NewStringUTF("");
    }

    jfloat *samples = env->GetFloatArrayElements(audioData, nullptr);

    struct whisper_full_params wparams =
            whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    // বাংলা ভাষা ফোর্স করা (মডেল বাংলায় ফাইন-টিউনড হলেও এটা নির্দিষ্ট করে দেওয়া ভালো)
    wparams.language = "bn";
    wparams.translate = false;
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.no_context = true;
    wparams.single_segment = true; // ছোট চাংকের জন্য
    wparams.n_threads = 4;

    int result = whisper_full(g_ctx, wparams, samples, numSamples);
    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);

    if (result != 0) {
        LOGE("ট্রান্সক্রিপশন ব্যর্থ, কোড: %d", result);
        return env->NewStringUTF("");
    }

    std::string fullText;
    const int numSegments = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < numSegments; i++) {
        fullText += whisper_full_get_segment_text(g_ctx, i);
    }

    return env->NewStringUTF(fullText.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_banglatranscriber_app_WhisperBridge_nativeRelease(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    if (g_ctx != nullptr) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
        LOGI("মডেল আনলোড করা হয়েছে");
    }
}
