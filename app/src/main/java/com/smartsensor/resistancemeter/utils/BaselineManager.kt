package com.smartsensor.resistancemeter.utils

import android.content.Context
import android.content.SharedPreferences
import com.smartsensor.resistancemeter.model.BaselineSettings
import kotlin.math.abs

class BaselineManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("baseline_prefs", Context.MODE_PRIVATE)
    
    // 设置当前电阻值为基线
    fun setBaseline(currentResistance: Float): BaselineSettings {
        val settings = BaselineSettings(
            baselineValue = currentResistance,
            alarmThreshold = getCurrentBaseline()?.alarmThreshold ?: 10f,
            isBaselineSet = true,
            baselineTimestamp = System.currentTimeMillis()
        )
        saveBaseline(settings)
        return settings
    }
    
    // 获取当前基线设置
    fun getCurrentBaseline(): BaselineSettings? {
        return if (prefs.getBoolean("is_baseline_set", false)) {
            BaselineSettings(
                baselineValue = prefs.getFloat("baseline_value", 0f),
                alarmThreshold = prefs.getFloat("alarm_threshold", 10f),
                isBaselineSet = prefs.getBoolean("is_baseline_set", false),
                baselineTimestamp = prefs.getLong("baseline_timestamp", 0L)
            )
        } else {
            null
        }
    }
    
    // 计算相对于基线的变化百分比
    fun calculateChangePercentage(currentValue: Float, baseline: Float): Float {
        if (baseline == 0f) return 0f
        return abs((currentValue - baseline) / baseline * 100)
    }
    
    // 重置基线设置
    fun resetBaseline() {
        prefs.edit().clear().apply()
    }
    
    // 设置报警阈值
    fun setAlarmThreshold(threshold: Float) {
        val currentBaseline = getCurrentBaseline()
        if (currentBaseline != null) {
            val updatedSettings = currentBaseline.copy(alarmThreshold = threshold)
            saveBaseline(updatedSettings)
        }
    }
    
    // 保存基线设置
    private fun saveBaseline(settings: BaselineSettings) {
        prefs.edit().apply {
            putFloat("baseline_value", settings.baselineValue)
            putFloat("alarm_threshold", settings.alarmThreshold)
            putBoolean("is_baseline_set", settings.isBaselineSet)
            putLong("baseline_timestamp", settings.baselineTimestamp)
            apply()
        }
    }
    
    // 获取基线历史记录（简化版本，可以扩展为数据库存储）
    fun getBaselineHistory(): List<BaselineSettings> {
        val current = getCurrentBaseline()
        return if (current != null) listOf(current) else emptyList()
    }
}