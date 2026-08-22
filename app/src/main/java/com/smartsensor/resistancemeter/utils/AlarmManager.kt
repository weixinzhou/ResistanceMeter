package com.smartsensor.resistancemeter.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.smartsensor.resistancemeter.model.AlarmRecord
import com.smartsensor.resistancemeter.model.BaselineSettings
import com.smartsensor.resistancemeter.model.ResistanceData
import kotlin.math.abs

class AlarmManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    private val alarmRecords = mutableListOf<AlarmRecord>()
    private var isAlarmEnabled = true
    private val handler = Handler(Looper.getMainLooper())
    private var pendingVibrationRunnable: Runnable? = null
    private var lastAlarmState = false
    
    // 检查并触发报警
    fun checkAndTriggerAlarm(resistanceData: ResistanceData, baselineSettings: BaselineSettings?): Boolean {
        if (!isAlarmEnabled || baselineSettings == null || !baselineSettings.isBaselineSet) {
            cancelPendingVibration()
            lastAlarmState = false
            return false
        }
        
        val shouldAlarm = shouldTriggerAlarm(resistanceData.resistance, baselineSettings)
        
        if (shouldAlarm && !lastAlarmState) {
            // 新的报警触发
            triggerAlarm(resistanceData.resistance, baselineSettings)
        } else if (!shouldAlarm && lastAlarmState) {
            // 报警恢复，取消待定的振动
            cancelPendingVibration()
        }
        
        lastAlarmState = shouldAlarm
        return shouldAlarm
    }
    
    // 判断是否应该触发报警
    private fun shouldTriggerAlarm(currentValue: Float, baselineSettings: BaselineSettings): Boolean {
        val changePercentage = abs((currentValue - baselineSettings.baselineValue) / 
                                 baselineSettings.baselineValue * 100)
        return changePercentage >= baselineSettings.alarmThreshold
    }
    
    // 触发报警
    private fun triggerAlarm(currentValue: Float, baselineSettings: BaselineSettings) {
        playAlarmSound()
        scheduleVibration(currentValue, baselineSettings)
        recordAlarmEvent(currentValue, baselineSettings)
    }
    
    // 调度振动 - 2秒延迟
    private fun scheduleVibration(currentValue: Float, baselineSettings: BaselineSettings) {
        // 如果已经有待定的振动，不重复调度
        if (pendingVibrationRunnable != null) {
            return
        }
        
        pendingVibrationRunnable = Runnable {
            // 在延迟后检查是否仍处于报警状态
            if (lastAlarmState) {
                triggerVibration()
            }
            pendingVibrationRunnable = null
        }
        
        // 2秒后执行振动
        handler.postDelayed(pendingVibrationRunnable!!, 2000)
    }
    
    // 取消待定的振动
    private fun cancelPendingVibration() {
        pendingVibrationRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            pendingVibrationRunnable = null
        }
    }
    
    // 播放报警音效
    fun playAlarmSound() {
        try {
            mediaPlayer?.release()
            
            // 获取系统默认通知铃声
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            mediaPlayer = MediaPlayer().apply {
                // 设置音频属性 - 使用alarm音频流，确保音量足够大
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                
                // 设置数据源为系统铃声
                setDataSource(context, alarmUri)
                
                // 设置音量为最大
                setVolume(1.0f, 1.0f)
                
                // 设置循环播放3次
                isLooping = false
                
                // 异步准备并播放
                prepareAsync()
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.start()
                    
                    // 3秒后停止播放
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            if (mediaPlayer.isPlaying) {
                                mediaPlayer.stop()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, 3000)
                }
                
                setOnErrorListener { _, _, _ ->
                    // 如果播放失败，尝试使用系统通知音
                    try {
                        val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                        ringtone?.play()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 备用方案：使用Ringtone播放
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                ringtone?.play()
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    // 触发振动
    fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 创建更明显的振动模式：短-停-短-停-长
                val pattern = longArrayOf(0, 200, 100, 200, 100, 500)
                val vibrationEffect = android.os.VibrationEffect.createWaveform(
                    pattern, 
                    intArrayOf(0, 255, 0, 255, 0, 255), // 强度数组
                    -1 // 不重复
                )
                vibrator.vibrate(vibrationEffect)
            } else {
                // 对于旧版本Android，使用长振动模式
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 200, 100, 200, 100, 500)
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 备用方案：简单振动
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(1000)
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    // 记录报警事件
    private fun recordAlarmEvent(currentValue: Float, baselineSettings: BaselineSettings) {
        val exceedancePercentage = abs((currentValue - baselineSettings.baselineValue) / 
                                     baselineSettings.baselineValue * 100)
        
        val alarmRecord = AlarmRecord(
            timestamp = System.currentTimeMillis(),
            resistanceValue = currentValue,
            baselineValue = baselineSettings.baselineValue,
            thresholdPercentage = baselineSettings.alarmThreshold,
            exceedancePercentage = exceedancePercentage
        )
        
        alarmRecords.add(alarmRecord)
        
        // 限制记录数量，避免内存过大
        if (alarmRecords.size > 1000) {
            alarmRecords.removeAt(0)
        }
    }
    
    // 获取报警历史
    fun getAlarmHistory(): List<AlarmRecord> {
        return alarmRecords.toList()
    }
    
    // 清除报警历史
    fun clearAlarmHistory() {
        alarmRecords.clear()
    }
    
    // 启用/禁用报警
    fun setAlarmEnabled(enabled: Boolean) {
        isAlarmEnabled = enabled
    }
    
    // 获取报警启用状态
    fun isAlarmEnabled(): Boolean {
        return isAlarmEnabled
    }
    
    // 测试报警效果
    fun testAlarm() {
        playAlarmSound()
        triggerVibration()
    }
    
    // 释放资源
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}