package com.smartsensor.resistancemeter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smartsensor.resistancemeter.model.DeviceInfo
import com.smartsensor.resistancemeter.model.ResistanceData
import kotlinx.coroutines.launch

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val _scannedDevices = MutableLiveData<List<DeviceInfo>>(emptyList())
    val scannedDevices: LiveData<List<DeviceInfo>> = _scannedDevices

    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _connectionState = MutableLiveData<Boolean>(false)
    val connectionState: LiveData<Boolean> = _connectionState

    private val _resistanceData = MutableLiveData<ResistanceData>()
    val resistanceData: LiveData<ResistanceData> = _resistanceData

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    fun updateScannedDevices(devices: List<DeviceInfo>) {
        viewModelScope.launch {
            _scannedDevices.value = devices
        }
    }

    fun updateScanningState(scanning: Boolean) {
        viewModelScope.launch {
            _isScanning.value = scanning
        }
    }

    fun updateConnectionState(connected: Boolean) {
        viewModelScope.launch {
            _connectionState.value = connected
        }
    }

    fun updateResistanceData(data: ResistanceData) {
        viewModelScope.launch {
            _resistanceData.value = data
        }
    }

    fun updateStatusMessage(message: String) {
        viewModelScope.launch {
            _statusMessage.value = message
        }
    }
}