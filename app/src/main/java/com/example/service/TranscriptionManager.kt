package com.example.service

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.Session
import com.example.data.Transcript
import com.example.data.TranscriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TranscriptionManager {
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Int?>(null)
    val activeSessionId: StateFlow<Int?> = _activeSessionId.asStateFlow()

    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    private val _dbLevel = MutableStateFlow(20f)
    val dbLevel: StateFlow<Float> = _dbLevel.asStateFlow()

    private val _liveDraftText = MutableStateFlow("")
    val liveDraftText: StateFlow<String> = _liveDraftText.asStateFlow()

    // Setup / Download Model States
    private val _modelProgress = MutableStateFlow(0)
    val modelProgress: StateFlow<Int> = _modelProgress.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val managerScope = CoroutineScope(Dispatchers.Default)
    private var repository: TranscriptionRepository? = null

    // Sample beautiful Arabic paragraphs for simulation fallbacks
    private val arabicPhrases = listOf(
        "السلام عليكم ورحمة الله وبركاته، أهلاً بحضوركم الكريم في هذا الاجتماع المبارك.",
        "اليوم سنناقش خطة العمل المقترحة لبقية الربع السنوي الثاني.",
        "من الضروري التركيز على تفعيل الأنظمة المحلية لضمان أمان البيانات وخصوصية المستخدم.",
        "سنبدأ بمراجعة المهام السابقة والتحديات الفنية التي واجهتنا أثناء تصفية الصمت.",
        "أقترح أن نقوم بتقسيم الملف الصوتي إلى مقاطع منفصلة عند فترات الصمت التي تزيد عن ثانيتين.",
        "نظام تفريغ البيانات محلي 100% دون الحاجة إلى الاتصال بأي خادم سحابي خارجي.",
        "تخزين النصوص يتم في قاعدة بيانات محلية آمنة ومزودة ببحث فوري في أجزاء من الثانية.",
        "هناك تحسن ملحوظ في أداء معالجة الصوت والذكاء الاصطناعي مع الحجم المكبوس للنموذج.",
        "يرجى توثيق مخرجات هذا النقاش وإرسال التوصيات لجميع أعضاء الفريق.",
        "نشكر الجميع على الحضور وعلى هذه المداخلات القيمة والمثمرة، نلتقي قريبًا."
    )

    fun init(context: Context) {
        if (repository == null) {
            val db = AppDatabase.getDatabase(context)
            repository = TranscriptionRepository(db.transcriptionDao())
        }
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
        if (!listening) {
            _liveDraftText.value = ""
            _isSpeechDetected.value = false
            _dbLevel.value = 20f
        }
    }

    fun setActiveSession(sessionId: Int?) {
        _activeSessionId.value = sessionId
    }

    fun updateSpeechState(detected: Boolean) {
        _isSpeechDetected.value = detected
    }

    fun updateDbLevel(level: Float) {
        _dbLevel.value = level
    }

    fun updateLiveDraft(text: String) {
        _liveDraftText.value = text
    }

    fun startModelDownload() {
        if (_isModelReady.value) return
        managerScope.launch {
            _modelProgress.value = 0
            for (i in 1..100) {
                kotlinx.coroutines.delay(25) // total ~2.5 secs
                _modelProgress.value = i
            }
            _isModelReady.value = true
        }
    }

    fun setModelReadyDirectly(ready: Boolean) {
        _isModelReady.value = ready
    }

    // Commits a newly transcribed sentence to the local Room database
    fun commitSentence(context: Context, sessionId: Int, speaker: String, text: String, relativeTimeMs: Long) {
        managerScope.launch {
            init(context)
            val repo = repository ?: return@launch
            
            // Check if session still exists
            val session = repo.getSessionSync(sessionId)
            if (session != null) {
                val transcript = Transcript(
                    sessionId = sessionId,
                    speakerTag = speaker,
                    textContent = text,
                    relativeTime = relativeTimeMs
                )
                repo.insertTranscript(transcript)
            }
        }
    }

    fun getSimulatedPhrase(index: Int): String {
        if (arabicPhrases.isEmpty()) return ""
        return arabicPhrases[index % arabicPhrases.size]
    }
}
