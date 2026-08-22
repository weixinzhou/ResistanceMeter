package com.smartsensor.resistancemeter.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.smartsensor.resistancemeter.model.DataBatch
import com.smartsensor.resistancemeter.model.AlarmRecord
import com.smartsensor.resistancemeter.model.ResistanceData
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

class AutoSaveManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AutoSaveManager"
        private const val PREFS_NAME = "auto_save_settings"
        private const val KEY_ENABLED = "auto_save_enabled"
        private const val KEY_INTERVAL_MINUTES = "save_interval_minutes"
        private const val KEY_MAX_DATA_POINTS = "max_data_points"
        private const val KEY_LAST_SAVE_TIME = "last_save_time"
        private const val KEY_MAX_FILES = "max_files"
        
        private const val DEFAULT_INTERVAL_MINUTES = 5
        private const val DEFAULT_MAX_DATA_POINTS = 1000
        private const val DEFAULT_MAX_FILES = 0 // 0 = unlimited
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val fileManager = AutoSaveFileManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var currentBatch: DataBatch? = null
    private var saveJob: Job? = null
    
    // LiveData for UI updates
    private val _isEnabled = MutableLiveData(isAutoSaveEnabled())
    val isEnabled: LiveData<Boolean> = _isEnabled
    
    private val _lastSaveTime = MutableLiveData<Long>()
    val lastSaveTime: LiveData<Long> = _lastSaveTime
    
    private val _currentBatchInfo = MutableLiveData<BatchInfo>()
    val currentBatchInfo: LiveData<BatchInfo> = _currentBatchInfo
    
    private val _saveStatus = MutableLiveData<SaveStatus>()
    val saveStatus: LiveData<SaveStatus> = _saveStatus
    
    init {
        Log.d(TAG, "========== AutoSaveManager Initializing ==========")
        Log.d(TAG, "AutoSave enabled: ${isAutoSaveEnabled()}")
        Log.d(TAG, "Save interval: ${getSaveIntervalMinutes()} minutes")
        Log.d(TAG, "Max data points: ${getMaxDataPoints()}")
        
        _lastSaveTime.value = prefs.getLong(KEY_LAST_SAVE_TIME, 0)
        Log.d(TAG, "Last save time: ${_lastSaveTime.value}")
        
        startAutoSaveTimer()
        Log.d(TAG, "AutoSaveManager initialization complete")
    }
    
    fun addResistanceData(data: ResistanceData) {
        Log.d(TAG, "addResistanceData called - AutoSave enabled: ${isAutoSaveEnabled()}")
        if (!isAutoSaveEnabled()) {
            Log.d(TAG, "AutoSave disabled, skipping data add")
            return
        }
        
        if (currentBatch == null) {
            Log.d(TAG, "No current batch, starting new batch")
            Log.d(TAG, "AutoSave enabled: ${isAutoSaveEnabled()}")
            Log.d(TAG, "Data timestamp: ${data.timestamp}")
            startNewBatch(data.timestamp)
        }
        
        currentBatch?.let { batch ->
            Log.d(TAG, "Adding data point to batch (R=${data.resistance}Ω)")
            batch.addDataPoint(data)
            updateBatchInfo()
            
            Log.d(TAG, "Batch data count: ${batch.getDataCount()}, Max: ${getMaxDataPoints()}")
            
            // 检查是否达到最大数据点数
            if (batch.getDataCount() >= getMaxDataPoints()) {
                Log.d(TAG, "Batch full, triggering save...")
                scope.launch {
                    saveBatchAndStartNew()
                }
            }
        } ?: run {
            Log.w(TAG, "Current batch is null after creation attempt")
        }
    }
    
    fun addAlarmData(alarm: AlarmRecord) {
        if (!isAutoSaveEnabled()) return
        
        if (currentBatch == null) {
            startNewBatch(alarm.timestamp)
        }
        
        currentBatch?.addAlarmEvent(alarm)
        updateBatchInfo()
    }
    
    private fun startNewBatch(timestamp: Long) {
        Log.d(TAG, "Starting new batch at timestamp: $timestamp")
        currentBatch = DataBatch(
            startTime = timestamp,
            endTime = timestamp,
            dataPoints = mutableListOf(),
            alarmEvents = mutableListOf()
        )
        Log.d(TAG, "New batch created successfully")
        updateBatchInfo()
    }
    
    private fun updateBatchInfo() {
        currentBatch?.let { batch ->
            _currentBatchInfo.value = BatchInfo(
                startTime = batch.startTime,
                endTime = batch.endTime,
                dataCount = batch.getDataCount(),
                alarmCount = batch.alarmEvents.size,
                durationMs = batch.getDurationMs()
            )
        }
    }
    
    private fun startAutoSaveTimer() {
        Log.d(TAG, "========== Starting AutoSave Timer ==========")
        saveJob?.cancel()
        
        if (!isAutoSaveEnabled()) {
            Log.d(TAG, "AutoSave disabled, timer not started")
            return
        }
        
        val intervalMs = getSaveIntervalMinutes() * 60 * 1000L
        Log.d(TAG, "AutoSave interval: ${getSaveIntervalMinutes()} minutes ($intervalMs ms)")
        
        saveJob = scope.launch {
            Log.d(TAG, "AutoSave timer coroutine started")
            while (isActive) {
                Log.d(TAG, "AutoSave timer waiting for ${intervalMs}ms...")
                delay(intervalMs)
                Log.d(TAG, "AutoSave timer fired - checking batch...")
                if (currentBatch != null && !currentBatch!!.isEmpty()) {
                    Log.d(TAG, "Batch exists and not empty, saving...")
                    saveBatchAndStartNew()
                } else {
                    Log.d(TAG, "No batch or batch empty, skipping save")
                    Log.d(TAG, "Current batch is null: ${currentBatch == null}")
                    if (currentBatch != null) {
                        Log.d(TAG, "Batch is empty: ${currentBatch!!.isEmpty()}")
                        Log.d(TAG, "Batch data count: ${currentBatch!!.getDataCount()}")
                    }
                }
            }
        }
    }
    
    private suspend fun saveBatchAndStartNew() {
        Log.d(TAG, "========== AutoSave Triggered ==========")
        val batch = currentBatch ?: return
        
        Log.d(TAG, "Current batch info:")
        Log.d(TAG, "  - Data points: ${batch.dataPoints.size}")
        Log.d(TAG, "  - Alarm events: ${batch.alarmEvents.size}")
        Log.d(TAG, "  - Duration: ${batch.getDurationMs()} ms")
        Log.d(TAG, "  - Is empty: ${batch.isEmpty()}")
        
        if (batch.isEmpty()) {
            Log.d(TAG, "Batch is empty, skipping save")
            return
        }
        
        Log.d(TAG, "Setting save status to Saving...")
        _saveStatus.value = SaveStatus.Saving
        
        Log.d(TAG, "Calling fileManager.saveDataBatch()...")
        val result = fileManager.saveDataBatch(batch)
        
        Log.d(TAG, "Save result received, processing...")
        result.fold(
            onSuccess = { filePath ->
                Log.d(TAG, "✓ Save successful: $filePath")
                _saveStatus.value = SaveStatus.Success(filePath)
                _lastSaveTime.value = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis()).apply()
                
                // 清理旧文件
                val maxFiles = getMaxFiles()
                if (maxFiles > 0) {
                    Log.d(TAG, "Cleaning up old files (keeping max $maxFiles files)...")
                    fileManager.deleteOldFiles(maxFiles)
                } else {
                    Log.d(TAG, "File cleanup disabled - keeping all files")
                }
            },
            onFailure = { error ->
                Log.e(TAG, "✗ Save failed: ${error.message}")
                Log.e(TAG, "Error details:", error)
                _saveStatus.value = SaveStatus.Error(error.message ?: "Save failed")
            }
        )
        
        // 开始新的批次
        Log.d(TAG, "Clearing current batch after save")
        currentBatch = null
        _currentBatchInfo.value = null
        Log.d(TAG, "Current batch cleared, ready for new data")
    }
    
    suspend fun forceSave(): Boolean {
        if (currentBatch?.isEmpty() != false) return false
        
        saveBatchAndStartNew()
        return true
    }
    
    fun setAutoSaveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
        
        if (enabled) {
            startAutoSaveTimer()
        } else {
            saveJob?.cancel()
            // 保存当前批次
            if (currentBatch?.isEmpty() == false) {
                scope.launch {
                    saveBatchAndStartNew()
                }
            }
        }
    }
    
    fun setSaveInterval(minutes: Int) {
        prefs.edit().putInt(KEY_INTERVAL_MINUTES, minutes).apply()
        if (isAutoSaveEnabled()) {
            startAutoSaveTimer() // 重启定时器
        }
    }
    
    fun setMaxDataPoints(maxPoints: Int) {
        prefs.edit().putInt(KEY_MAX_DATA_POINTS, maxPoints).apply()
    }
    
    fun setMaxFiles(maxFiles: Int) {
        prefs.edit().putInt(KEY_MAX_FILES, maxFiles).apply()
    }
    
    fun isAutoSaveEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)
    fun getSaveIntervalMinutes(): Int = prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
    fun getMaxDataPoints(): Int = prefs.getInt(KEY_MAX_DATA_POINTS, DEFAULT_MAX_DATA_POINTS)
    fun getMaxFiles(): Int = prefs.getInt(KEY_MAX_FILES, DEFAULT_MAX_FILES)
    
    fun getStorageInfo() = fileManager.getStorageInfo()
    
    fun getAutoSaveFiles() = fileManager.getAutoSaveFiles()
    
    fun cleanup() {
        saveJob?.cancel()
        scope.cancel()
        
        // 保存当前批次
        if (currentBatch?.isEmpty() == false) {
            runBlocking {
                saveBatchAndStartNew()
            }
        }
    }
    
    data class BatchInfo(
        val startTime: Long,
        val endTime: Long,
        val dataCount: Int,
        val alarmCount: Int,
        val durationMs: Long
    )
    
    sealed class SaveStatus {
        object Saving : SaveStatus()
        data class Success(val filePath: String) : SaveStatus()
        data class Error(val message: String) : SaveStatus()
    }
}