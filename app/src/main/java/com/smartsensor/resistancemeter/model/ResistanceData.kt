package com.smartsensor.resistancemeter.model

data class ResistanceData(
    val resistance: Float,     // 单通道电阻值
    val timestamp: Long,       // 时间戳
    val status: Int,          // 设备状态
    val isAlarmTriggered: Boolean = false  // 是否触发报警
)