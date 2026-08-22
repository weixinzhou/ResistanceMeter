package com.smartsensor.resistancemeter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smartsensor.resistancemeter.model.AlarmRecord
import com.smartsensor.resistancemeter.model.ResistanceData
import com.smartsensor.resistancemeter.utils.AutoSaveManager
import kotlinx.coroutines.launch
import java.util.*

class DataViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentData = MutableLiveData<ResistanceData>()
    val currentData: LiveData<ResistanceData> = _currentData

    private val _historicalData = MutableLiveData<List<ResistanceData>>(emptyList())
    val historicalData: LiveData<List<ResistanceData>> = _historicalData

    private val _dataStatistics = MutableLiveData<DataStatistics>()
    val dataStatistics: LiveData<DataStatistics> = _dataStatistics

    private val dataHistory = mutableListOf<ResistanceData>()
    private val maxHistorySize = 100000 // 最大存储100,000条历史数据 (约10-20小时连续采集)
    
    // 自动保存管理器
    private val autoSaveManager = AutoSaveManager(application)
    
    // 暴露自动保存相关的LiveData
    val autoSaveEnabled: LiveData<Boolean> = autoSaveManager.isEnabled
    val lastSaveTime: LiveData<Long> = autoSaveManager.lastSaveTime
    val currentBatchInfo: LiveData<AutoSaveManager.BatchInfo> = autoSaveManager.currentBatchInfo
    val saveStatus: LiveData<AutoSaveManager.SaveStatus> = autoSaveManager.saveStatus

    fun addData(data: ResistanceData) {
        viewModelScope.launch {
            android.util.Log.d("DataViewModel", "========== DataViewModel.addData ==========")
            android.util.Log.d("DataViewModel", "Received: R=${data.resistance}Ω at ${data.timestamp}")
            android.util.Log.d("DataViewModel", "Current history size: ${dataHistory.size}")
            android.util.Log.d("DataViewModel", "AutoSave manager enabled: ${autoSaveManager.isAutoSaveEnabled()}")
            
            _currentData.value = data

            // 添加到历史数据
            dataHistory.add(data)
            if (dataHistory.size > maxHistorySize) {
                dataHistory.removeAt(0) // 移除最旧的数据
            }

            _historicalData.value = dataHistory.toList()
            android.util.Log.d("DataViewModel", "History updated - total points: ${dataHistory.size}")

            // 添加到自动保存管理器
            android.util.Log.d("DataViewModel", "Forwarding to AutoSaveManager...")
            autoSaveManager.addResistanceData(data)
            android.util.Log.d("DataViewModel", "Data forwarded to AutoSaveManager")

            // 更新统计信息
            updateStatistics()
            android.util.Log.d("DataViewModel", "========== DataViewModel.addData Complete ==========")
        }
    }
    
    fun addAlarmData(alarm: AlarmRecord) {
        viewModelScope.launch {
            // 添加到自动保存管理器
            autoSaveManager.addAlarmData(alarm)
        }
    }

    private fun updateStatistics() {
        if (dataHistory.isNotEmpty()) {
            val resistanceValues = dataHistory.map { it.resistance }

            val stats = DataStatistics(
                resistanceStats = ChannelStatistics(
                    average = resistanceValues.average().toFloat(),
                    min = resistanceValues.minOrNull() ?: 0f,
                    max = resistanceValues.maxOrNull() ?: 0f,
                    standardDeviation = calculateStandardDeviation(resistanceValues)
                ),
                totalSamples = dataHistory.size,
                dataRate = calculateDataRate()
            )

            _dataStatistics.value = stats
        }
    }

    private fun calculateStandardDeviation(values: List<Float>): Float {
        if (values.isEmpty()) return 0f

        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }

    private fun calculateDataRate(): Float {
        if (dataHistory.size < 2) return 0f

        val timeSpan = dataHistory.last().timestamp - dataHistory.first().timestamp
        return if (timeSpan > 0) {
            (dataHistory.size * 1000f) / timeSpan // 每秒采样数
        } else {
            0f
        }
    }

    fun clearData() {
        viewModelScope.launch {
            dataHistory.clear()
            _historicalData.value = emptyList()
            _dataStatistics.value = null
        }
    }

    fun exportData(): List<ResistanceData> {
        android.util.Log.d("DataViewModel", "Export data called, returning ${dataHistory.size} data points")
        return dataHistory.toList()
    }
    
    // 自动保存相关方法
    fun setAutoSaveEnabled(enabled: Boolean) {
        autoSaveManager.setAutoSaveEnabled(enabled)
    }
    
    fun setSaveInterval(minutes: Int) {
        autoSaveManager.setSaveInterval(minutes)
    }
    
    fun setMaxDataPoints(maxPoints: Int) {
        autoSaveManager.setMaxDataPoints(maxPoints)
    }
    
    fun setMaxFiles(maxFiles: Int) {
        autoSaveManager.setMaxFiles(maxFiles)
    }
    
    fun forceSave() {
        viewModelScope.launch {
            autoSaveManager.forceSave()
        }
    }
    
    fun getStorageInfo() = autoSaveManager.getStorageInfo()
    
    fun getAutoSaveFiles() = autoSaveManager.getAutoSaveFiles()
    
    override fun onCleared() {
        super.onCleared()
        autoSaveManager.cleanup()
    }

    // 数据统计模型
    data class DataStatistics(
        val resistanceStats: ChannelStatistics,
        val totalSamples: Int,
        val dataRate: Float
    )

    data class ChannelStatistics(
        val average: Float,
        val min: Float,
        val max: Float,
        val standardDeviation: Float
    )
}