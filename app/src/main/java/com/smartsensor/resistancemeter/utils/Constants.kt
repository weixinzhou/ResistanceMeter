package com.smartsensor.resistancemeter.utils

import java.util.UUID

object Constants {
    // 蓝牙相关常量
    val SERVICE_UUID: UUID = UUID.fromString("ab0b0000-0bb0-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("ab0b0001-0bb0-1000-8000-00805f9b34fb")

    // 数据包相关常量
    const val PACKET_HEADER: Byte = 0xAA.toByte()
    const val CMD_RESISTANCE_DATA: Byte = 0x01
    const val CMD_STATUS_QUERY: Byte = 0x02
    const val CMD_CONFIG_SET: Byte = 0x03
    const val CMD_CALIBRATION: Byte = 0x04
    const val CMD_RESET: Byte = 0x05

    // 蓝牙扫描设置
    const val SCAN_PERIOD = 10000L // 10秒
    const val DEVICE_NAME_PREFIX = "Smart_sensor"

    // 权限请求码
    const val REQUEST_ENABLE_BT = 1
    const val REQUEST_PERMISSIONS = 2

    // 数据采样设置
    const val CHART_MAX_ENTRIES = 1000 // 图表最大显示点数（增加10倍以显示更多历史数据）
    const val DATA_UPDATE_INTERVAL = 100L // 100ms

    // 基线和报警相关常量
    const val DEFAULT_ALARM_THRESHOLD = 10.0f // 默认报警阈值（百分比）
    const val MIN_ALARM_THRESHOLD = 1.0f // 最小报警阈值
    const val MAX_ALARM_THRESHOLD = 50.0f // 最大报警阈值
    const val ALARM_VIBRATION_DURATION = 500L // 报警振动时长(ms)
    
    // SharedPreferences键名
    const val PREFS_BASELINE_VALUE = "baseline_value"
    const val PREFS_BASELINE_TIMESTAMP = "baseline_timestamp"
    const val PREFS_BASELINE_SET = "baseline_set"
    const val PREFS_ALARM_THRESHOLD = "alarm_threshold"
    const val PREFS_ALARM_ENABLED = "alarm_enabled"
}