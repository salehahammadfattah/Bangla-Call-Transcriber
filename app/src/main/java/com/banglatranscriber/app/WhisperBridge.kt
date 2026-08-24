package com.banglatranscriber.app

/**
 * whisper.cpp (C++) এর সাথে যোগাযোগের জন্য JNI wrapper।
 * নেটিভ ফাংশনগুলো jni_bridge.cpp-এ বাস্তবায়িত।
 */
class WhisperBridge {

    private var nativeHandle: Long = 0

    companion object {
        init {
            System.loadLibrary("bangla_transcriber")
        }
    }

    /** মডেল ফাইলের পূর্ণ পাথ দিয়ে whisper.cpp initialize করে। সফল হলে true। */
    fun loadModel(modelPath: String): Boolean {
        nativeHandle = nativeInit(modelPath)
        return nativeHandle != 0L
    }

    /**
     * ১৬kHz, মনো, float32 PCM অডিও চাংক ট্রান্সক্রাইব করে।
     * numSamples = samples.size হলেই যথেষ্ট, তবে স্পষ্টতার জন্য আলাদা প্যারামিটার।
     */
    fun transcribe(samples: FloatArray): String {
        if (nativeHandle == 0L) return ""
        return nativeTranscribe(samples, samples.size)
    }

    fun release() {
        nativeRelease()
        nativeHandle = 0
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(audioData: FloatArray, numSamples: Int): String
    private external fun nativeRelease()
}
