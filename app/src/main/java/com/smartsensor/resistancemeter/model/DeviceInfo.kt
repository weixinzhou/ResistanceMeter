package com.smartsensor.resistancemeter.model

import android.bluetooth.BluetoothDevice
import com.smartsensor.resistancemeter.utils.DeviceFilter

data class DeviceInfo(
    val device: BluetoothDevice,
    var rssi: Int,
    val name: String = device.name ?: "Unknown Device",
    val address: String = device.address,
    var isConnected: Boolean = false
) {
    fun updateRssi(newRssi: Int) {
        rssi = newRssi
    }

    fun setConnectionState(connected: Boolean) {
        isConnected = connected
    }
    
    companion object {
        fun isValidDevice(device: BluetoothDevice): Boolean {
            return DeviceFilter.isValidDevice(device)
        }
        
        fun createIfValid(device: BluetoothDevice, rssi: Int): DeviceInfo? {
            return if (isValidDevice(device)) {
                DeviceInfo(device, rssi)
            } else {
                null
            }
        }
    }
}