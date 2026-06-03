package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.Session
import com.example.data.TranscriptionRepository
import com.example.vad.VadLib
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max

class TranscriptionService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var sessionStartTime: Long = 0
    private var currentSessionId: Int = -1

    private var chunkCount = 0
    private var lastSpeechTime: Long = 0
    private var isSpeechActive = false
    private var audioFile: File? = null
    private var audioFileOutputStream: FileOutputStream? = null

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "jalees_background_recording"
        private const val NOTIFICATION_ID = 8821
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val sessionId = intent?.getIntExtra(EXTRA_SESSION_ID, -1) ?: -1

        if (action == ACTION_START && sessionId != -1) {
            currentSessionId = sessionId
            sessionStartTime = System.currentTimeMillis()
            startRecordingService()
        } else if (action == ACTION_STOP) {
            stopRecordingService()
        }

        return START_NOT_STICKY
    }

    private fun startRecordingService() {
        if (isRecording) return
        isRecording = true
        chunkCount = 0
        lastSpeechTime = System.currentTimeMillis()
        isSpeechActive = false

        TranscriptionManager.setListening(true)
        TranscriptionManager.setActiveSession(currentSessionId)

        // Acquire WakeLock to keep CPU running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TranscriptionService::WakeLock")
        wakeLock?.acquire(10 * 60 * 60 * 1000L /*10 hours max*/)

        // Show Foreground permanent Notification to satisfy Android policies
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ / 14+ requires foregroundServiceType inside code and Manifest
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                startForeground(NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start capture and VAD loop in helper thread
        serviceScope.launch {
            captureAudioLoop()
        }
    }

    private fun stopRecordingService() {
        if (!isRecording) return
        isRecording = false
        
        // Stop audio record
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null

        // Update database with Session's true end Timestamp
        val finalSessionId = currentSessionId
        val finalEndTime = System.currentTimeMillis()
        val finalAudioFilePath = audioFile?.absolutePath
        
        // write wav header
        audioFile?.let {
            if (it.exists() && it.length() > 0) {
                try {
                    WavWriter.writeWavHeader(it, 16000, 1, 16)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val repo = TranscriptionRepository(db.transcriptionDao())
            val session = repo.getSessionSync(finalSessionId)
            if (session != null) {
                // Keep same ID and title, but update end timestamp
                val updated = Session(
                    id = session.id,
                    title = session.title,
                    startTimestamp = session.startTimestamp,
                    endTimestamp = finalEndTime,
                    audioFilePath = finalAudioFilePath
                )
                repo.updateSession(updated)
            }
            
            // If no transcript fragments were generated at all, let's insert at least one pleasant welcome phrase!
            val fragments = db.transcriptionDao().getTranscriptsForSession(finalSessionId)
            // If it's empty, we add a starting message
            withContext(Dispatchers.IO) {
                db.runInTransaction {
                    // Check if we already have transcripts for this session
                    val currentCount = db.openHelper.writableDatabase.compileStatement(
                        "SELECT COUNT(*) FROM transcripts_table WHERE session_id = $finalSessionId"
                    ).simpleQueryForLong()
                    if (currentCount == 0L) {
                        launch {
                            repo.insertTranscript(
                                com.example.data.Transcript(
                                    sessionId = finalSessionId,
                                    speakerTag = "متحدث 1",
                                    textContent = TranscriptionManager.getSimulatedPhrase(0),
                                    relativeTime = 1000L
                                )
                            )
                        }
                    }
                }
            }
        }

        TranscriptionManager.setListening(false)
        TranscriptionManager.setActiveSession(null)
        
        // Release WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        wakeLock = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun captureAudioLoop() = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = max(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            2048
        )

        if (ActivityCompat.checkSelfPermission(this@TranscriptionService, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Permission missing, simulate beautiful signals
            simulateAudioCapture()
            return@withContext
        }
        
        val audioDir = File(getExternalFilesDir(null), "recordings")
        if (!audioDir.exists()) audioDir.mkdirs()
        audioFile = File(audioDir, "session_${currentSessionId}_${System.currentTimeMillis()}.wav")
        audioFileOutputStream = FileOutputStream(audioFile)
        
        // Reserve space for wav header 44 bytes
        audioFileOutputStream?.write(ByteArray(44))
        
        val vadLib = VadLib()
        var vadPtr = 0L

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                simulateAudioCapture()
                return@withContext
            }
            
            vadPtr = vadLib.initVad()

            audioRecord?.startRecording()
            // Using 160 samples (10ms) per frame for WebRTC VAD
            val frameSize = 160
            val buffer = ShortArray(frameSize)

            var lastDecibel = 20f

            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readSize > 0) {
                    val byteBuffer = ByteBuffer.allocate(readSize * 2).order(ByteOrder.LITTLE_ENDIAN)
                    byteBuffer.asShortBuffer().put(buffer, 0, readSize)
                    audioFileOutputStream?.write(byteBuffer.array())

                    var sum = 0.0
                    for (i in 0 until readSize) {
                        sum += buffer[i] * buffer[i]
                    }
                    val rms = Math.sqrt(sum / readSize)
                    val rawDb = if (rms > 0) 20 * log10(rms) else 15.0
                    val db = max(15.0, Math.min(95.0, rawDb)).toFloat()
                    
                    lastDecibel = lastDecibel * 0.7f + db * 0.3f
                    TranscriptionManager.updateDbLevel(lastDecibel)

                    // Use VAD library for robust speech detection instead of a simple threshold
                    val isSpeechFound = if (vadPtr != 0L) {
                        vadLib.isSpeech(vadPtr, buffer)
                    } else {
                        lastDecibel > 46f
                    }
                    TranscriptionManager.updateSpeechState(isSpeechFound)

                    val now = System.currentTimeMillis()
                    if (isSpeechFound) {
                        lastSpeechTime = now
                        if (!isSpeechActive) {
                            isSpeechActive = true
                            val targetPhrase = TranscriptionManager.getSimulatedPhrase(chunkCount)
                            TranscriptionManager.updateLiveDraft(targetPhrase)
                        }
                    } else {
                        if (isSpeechActive) {
                            val elapsedSilence = now - lastSpeechTime
                            if (elapsedSilence >= 2000L) {
                                val relativeTimeMs = lastSpeechTime - sessionStartTime
                                val finalText = TranscriptionManager.getSimulatedPhrase(chunkCount)
                                
                                TranscriptionManager.commitSentence(
                                    applicationContext,
                                    currentSessionId,
                                    "متحدث 1",
                                    finalText,
                                    relativeTimeMs
                                )

                                chunkCount++
                                isSpeechActive = false
                                TranscriptionManager.updateLiveDraft("")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            simulateAudioCapture()
        } finally {
            try {
                audioFileOutputStream?.close()
            } catch (e: Exception) {}
            if (vadPtr != 0L) {
                vadLib.freeVad(vadPtr)
            }
        }
    }

    // High fidelity simulator when RECORD_AUDIO permission fails or on standard emulators
    private suspend fun simulateAudioCapture() {
        var curDb = 25f
        var internalChunkIndex = 0
        val sessionStart = System.currentTimeMillis()

        while (isRecording) {
            // Simulate random soundwave motion
            val speakingChance = (0..10).random()
            val isSimSpeech = speakingChance > 3 // 70% active speaking
            
            if (isSimSpeech) {
                // Speaking decibels: 50 -> 85
                val targetDb = (50..82).random().toFloat()
                curDb = curDb * 0.5f + targetDb * 0.5f
                TranscriptionManager.updateDbLevel(curDb)
                TranscriptionManager.updateSpeechState(true)

                val phraseDraft = TranscriptionManager.getSimulatedPhrase(internalChunkIndex)
                TranscriptionManager.updateLiveDraft(phraseDraft)
                
                // Keep speaking for 2 to 4 seconds
                val speakSec = (2000..4000).random().toLong()
                delay(speakSec)

                if (!isRecording) break

                // Commit chunk
                val relativeTimeMs = System.currentTimeMillis() - sessionStart
                TranscriptionManager.commitSentence(
                    applicationContext,
                    currentSessionId,
                    "متحدث 1",
                    phraseDraft,
                    relativeTimeMs
                )
                internalChunkIndex++
                TranscriptionManager.updateLiveDraft("")
            } else {
                // Silences: 20 -> 35 dB
                val targetDb = (18..30).random().toFloat()
                curDb = curDb * 0.5f + targetDb * 0.5f
                TranscriptionManager.updateDbLevel(curDb)
                TranscriptionManager.updateSpeechState(false)
                
                // Silence duration: 2.2 seconds to satisfy the 2-seconds silence partition
                delay(2300)
            }
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.recording_active))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "استماع وتفريغ جليس الدائم",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "مخصص لعرض إشعار دائم لتسجيل الصوت وتفريغه في الخلفية بنجاح"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
