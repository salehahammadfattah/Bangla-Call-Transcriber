package com.banglatranscriber.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.banglatranscriber.app.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRunning = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startTranscription()
        } else {
            binding.statusText.text = "মাইক্রোফোন পারমিশন ছাড়া অ্যাপ কাজ করবে না"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.transcriptText.text = ""
        binding.statusText.text = "মডেল প্রস্তুত করা হচ্ছে..."
        copyModelFromAssetsIfNeeded()

        TranscriptionService.onTranscriptUpdate = { newText ->
            runOnUiThread {
                binding.transcriptText.append("$newText\n")
                binding.scrollView.post {
                    binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
                }
            }
        }

        binding.toggleButton.setOnClickListener {
            if (!isRunning) {
                checkModelThenStart()
            } else {
                stopTranscription()
            }
        }
    }

    /**
     * APK-এর assets/models/bangla-whisper.bin ফাইলটা অ্যাপ প্রথমবার চালু হলে
     * internal storage-এ কপি করে রাখে, যাতে whisper.cpp সরাসরি ফাইল-পাথ দিয়ে
     * মডেল লোড করতে পারে (assets থেকে সরাসরি নেটিভ কোডে পড়া জটিল, তাই এই ধাপ)।
     */
    private fun copyModelFromAssetsIfNeeded() {
        val destFile = File(filesDir, "models/bangla-whisper.bin")
        if (destFile.exists() && destFile.length() > 0) {
            binding.statusText.text = "শুরু করতে নিচের বাটনে চাপুন। কল চলাকালীন স্পিকার অন রাখুন।"
            return
        }

        Thread {
            try {
                destFile.parentFile?.mkdirs()
                assets.open("models/bangla-whisper.bin").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 1 shl 20)
                    }
                }
                runOnUiThread {
                    binding.statusText.text = "শুরু করতে নিচের বাটনে চাপুন। কল চলাকালীন স্পিকার অন রাখুন।"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.statusText.text = "⚠️ মডেল ফাইল assets-এ পাওয়া যায়নি। " +
                        "GitHub Actions বিল্ড লগ চেক করুন মডেল কনভার্সন সফল হয়েছে কিনা।"
                }
            }
        }.start()
    }

    private fun checkModelThenStart() {
        val modelFile = File(filesDir, "models/bangla-whisper.bin")
        if (!modelFile.exists()) {
            binding.statusText.text =
                "মডেল ফাইল পাওয়া যায়নি।\nফোনের ${modelFile.absolutePath} এ GGML মডেল কপি করুন " +
                "(README.md দেখুন — HuggingFace থেকে ডাউনলোড করে GGML-এ কনভার্ট করতে হবে)।"
            return
        }
        ensurePermissionsThenStart()
    }

    private fun ensurePermissionsThenStart() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startTranscription()
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    private fun startTranscription() {
        val modelPath = File(filesDir, "models/bangla-whisper.bin").absolutePath
        val intent = Intent(this, TranscriptionService::class.java)
        intent.putExtra("model_path", modelPath)
        ContextCompat.startForegroundService(this, intent)
        isRunning = true
        binding.toggleButton.text = "বন্ধ করুন"
        binding.statusText.text = "শোনা হচ্ছে... কথা বললে টেক্সট নিচে দেখা যাবে।"
    }

    private fun stopTranscription() {
        stopService(Intent(this, TranscriptionService::class.java))
        isRunning = false
        binding.toggleButton.text = "শুরু করুন"
        binding.statusText.text = "থেমে গেছে।"
    }

    override fun onDestroy() {
        TranscriptionService.onTranscriptUpdate = null
        super.onDestroy()
    }
}
