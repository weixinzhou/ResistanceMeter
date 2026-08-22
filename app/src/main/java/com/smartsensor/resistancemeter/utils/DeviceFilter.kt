package com.smartsensor.resistancemeter.utils

import android.bluetooth.BluetoothDevice

object DeviceFilter {
    
    private val INVALID_DEVICE_NAMES = setOf(
        "unknown device",
        "unknown",
        "",
        " ",
        "null"
    )
    
    fun isValidDeviceName(deviceName: String?): Boolean {
        return when {
            deviceName.isNullOrBlank() -> false
            deviceName.trim().lowercase() in INVALID_DEVICE_NAMES -> false
            else -> true
        }
    }
    
    fun isValidDevice(device: BluetoothDevice): Boolean {
        return isValidDeviceName(device.name)
    }
    
    fun getDisplayName(device: BluetoothDevice): String {
        val deviceName = device.name
        return if (isValidDeviceName(deviceName)) {
            deviceName!!
        } else {
            "Unknown Device (${device.address})"
        }
    }
}