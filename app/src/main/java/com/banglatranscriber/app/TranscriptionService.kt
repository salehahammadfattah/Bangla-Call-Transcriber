package com.banglatranscriber.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

/**
 * ফোরগ্রাউন্ড সার্ভিস — মাইক্রোফোন থেকে অডিও ধরে (স্পিকার মোডে চলা কলের আওয়াজসহ),
 * প্রতি কয়েক সেকেন্ড অন্তর চাংক করে whisper.cpp দিয়ে অফলাইনে বাংলায় ট্রান্সক্রাইব করে,
 * এবং ফলাফল broadcast/callback দিয়ে UI-তে পাঠায়।
 *
 * ব্যবহারকারীকে কল চলাকালীন স্পিকার অন রাখতে বলা হয়, কারণ Android/iOS সরাসরি
 * ফোন-কলের অডিও থার্ড-পার্টি অ্যাপকে অ্যাক্সেস করতে দেয় না।
 */
class TranscriptionService : Service() {

    companion object {
        const val CHANNEL_ID = "transcription_channel"
        const val NOTIFICATION_ID = 1
        const val SAMPLE_RATE = 16000
        const val CHUNK_SECONDS = 4 // প্রতি ৪ সেকেন্ড অডিও একবারে ট্রান্সক্রাইব করা হবে

        var onTranscriptUpdate: ((String) -> Unit)? = null
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private lateinit var whisper: WhisperBridge
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        whisper = WhisperBridge()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelPath = intent?.getStringExtra("model_path")
            ?: File(filesDir, "models/bangla-whisper.bin").absolutePath

        startForeground(
            NOTIFICATION_ID,
            buildNotification("ট্রান্সক্রিপশন চলছে..."),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        )

        serviceScope.launch {
            val loaded = whisper.loadModel(modelPath)
            if (!loaded) {
                onTranscriptUpdate?.invoke("⚠️ মডেল লোড করা যায়নি। মডেল ফাইল আছে কিনা যাচাই করুন: $modelPath")
                stopSelf()
                return@launch
            }
            startCapture()
        }

        return START_STICKY
    }

    @Suppress("MissingPermission") // RECORD_AUDIO পারমিশন Activity থেকে চেক করা হয়
    private fun startCapture() {
        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, SAMPLE_RATE * 2) // নিরাপদ মার্জিন

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // ভয়েস-কল অডিওর জন্য অপ্টিমাইজড সোর্স
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            onTranscriptUpdate?.invoke("⚠️ মাইক্রোফোন চালু করা যায়নি")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        val chunkSamples = SAMPLE_RATE * CHUNK_SECONDS
        val pcmBuffer = ShortArray(chunkSamples)

        serviceScope.launch {
            while (isRecording) {
                var offset = 0
                while (offset < chunkSamples && isRecording) {
                    val read = audioRecord?.read(pcmBuffer, offset, chunkSamples - offset) ?: -1
                    if (read > 0) offset += read else break
                }
                if (offset > 0) {
                    val floatSamples = FloatArray(offset) { i -> pcmBuffer[i] / 32768.0f }
                    val text = whisper.transcribe(floatSamples)
                    if (text.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            onTranscriptUpdate?.invoke(text.trim())
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "লাইভ ট্রান্সক্রিপশন", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("বাংলা কল ট্রান্সক্রাইবার")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        whisper.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
