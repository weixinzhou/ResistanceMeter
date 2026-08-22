package com.smartsensor.resistancemeter.model

data class BaselineSettings(
    val baselineValue: Float = 0f,     // 基线电阻值
    val alarmThreshold: Float = 10f,   // 报警阈值百分比
    val isBaselineSet: Boolean = false, // 是否已设置基线
    val baselineTimestamp: Long = 0L   // 基线设置时间
)