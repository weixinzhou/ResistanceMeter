package com.smartsensor.resistancemeter.model

data class AlarmRecord(
    val timestamp: Long,               // 报警时间
    val resistanceValue: Float,       // 触发报警的电阻值
    val baselineValue: Float,         // 当时的基线值
    val thresholdPercentage: Float,   // 阈值百分比
    val exceedancePercentage: Float   // 实际超出百分比
)