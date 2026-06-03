package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Session
import com.example.data.Transcript
import com.example.data.TranscriptionRepository
import com.example.service.TranscriptionManager
import com.example.service.TranscriptionService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed interface AppScreen {
    object Onboarding : AppScreen
    object Main : AppScreen
    object Settings : AppScreen
    data class Detail(val sessionId: Int) : AppScreen
}

class JaleesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TranscriptionRepository
    private val sharedPrefs = application.getSharedPreferences("jalees_prefs", Context.MODE_PRIVATE)

    // Current Screen Flow
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Onboarding)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Search Query Flow
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Session Detail Search Query Flow
    private val _detailSearchQuery = MutableStateFlow("")
    val detailSearchQuery: StateFlow<String> = _detailSearchQuery.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TranscriptionRepository(db.transcriptionDao())
        TranscriptionManager.init(application)

        // Check onboarding completion
        val isOnboardingDone = sharedPrefs.getBoolean("onboarding_complete_v1", false)
        if (isOnboardingDone) {
            _currentScreen.value = AppScreen.Main
            TranscriptionManager.setModelReadyDirectly(true)
        } else {
            _currentScreen.value = AppScreen.Onboarding
        }
    }

    // Sessions flow with live search filtering
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessions: StateFlow<List<Session>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allSessions
            } else {
                repository.searchSessionsWithFts(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Transcription service state relays
    val isListening: StateFlow<Boolean> = TranscriptionManager.isListening
    val activeSessionId: StateFlow<Int?> = TranscriptionManager.activeSessionId
    val isSpeechDetected: StateFlow<Boolean> = TranscriptionManager.isSpeechDetected
    val dbLevel: StateFlow<Float> = TranscriptionManager.dbLevel
    val liveDraftText: StateFlow<String> = TranscriptionManager.liveDraftText

    // Setup Model Progress States
    val modelProgress: StateFlow<Int> = TranscriptionManager.modelProgress
    val isModelReady: StateFlow<Boolean> = TranscriptionManager.isModelReady
    
    // Settings States
    private val _vadMode = MutableStateFlow(sharedPrefs.getInt("vad_mode", 3))
    val vadMode: StateFlow<Int> = _vadMode.asStateFlow()
    
    private val _isModelValid = MutableStateFlow(false)
    val isModelValid: StateFlow<Boolean> = _isModelValid.asStateFlow()
    
    fun setVadMode(mode: Int) {
        sharedPrefs.edit().putInt("vad_mode", mode).apply()
        _vadMode.value = mode
    }

    fun verifyModelIntegrity(context: Context) {
        val isValid = TranscriptionManager.verifyModelFile(context)
        _isModelValid.value = isValid
    }


    // Current Detail Session States
    private val _selectedSessionId = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTranscripts: StateFlow<List<Transcript>> = combine(
        _selectedSessionId.filterNotNull(),
        _detailSearchQuery
    ) { sessionId, query ->
        Pair(sessionId, query)
    }.flatMapLatest { (sessionId, query) ->
        repository.getTranscripts(sessionId).map { list ->
            if (query.isBlank()) {
                list
            } else {
                list.filter { it.textContent.contains(query, ignoreCase = true) }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedSession: StateFlow<Session?> = _selectedSessionId
        .filterNotNull()
        .flatMapLatest { id -> repository.getSession(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun completeOnboarding() {
        sharedPrefs.edit().putBoolean("onboarding_complete_v1", true).apply()
        _currentScreen.value = AppScreen.Main
    }

    fun startModelPreparation(context: Context) {
        TranscriptionManager.startModelDownload(context)
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen is AppScreen.Detail) {
            _selectedSessionId.value = screen.sessionId
            _detailSearchQuery.value = "" // clear previous searches
        }
    }

    fun goBack() {
        _currentScreen.value = AppScreen.Main
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateDetailSearchQuery(query: String) {
        _detailSearchQuery.value = query
    }

    // Records initiation and active Service start
    fun startNewListeningSession(context: Context) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("dd MMMM yyyy - hh:mm a", Locale("ar")).format(Date())
            val sessionName = "جلسة جليس - $dateStr"
            
            val newSession = Session(
                title = sessionName,
                startTimestamp = System.currentTimeMillis(),
                endTimestamp = System.currentTimeMillis() // temp
            )
            
            val insertedId = repository.insertSession(newSession).toInt()
            
            // Fire up background service
            val intent = Intent(context, TranscriptionService::class.java).apply {
                action = TranscriptionService.ACTION_START
                putExtra(TranscriptionService.EXTRA_SESSION_ID, insertedId)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stopCurrentListeningSession(context: Context) {
        val intent = Intent(context, TranscriptionService::class.java).apply {
            action = TranscriptionService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun renameSession(sessionId: Int, newTitle: String) {
        viewModelScope.launch {
            val session = repository.getSessionSync(sessionId)
            if (session != null) {
                val updated = session.copy(title = newTitle)
                repository.updateSession(updated)
            }
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                _currentScreen.value = AppScreen.Main
            }
        }
    }

    fun checkBatteryIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    fun openBatterySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                // Settings fallback
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }
}
