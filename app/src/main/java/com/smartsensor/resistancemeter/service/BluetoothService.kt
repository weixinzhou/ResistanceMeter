package com.smartsensor.resistancemeter.service

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.smartsensor.resistancemeter.model.DeviceInfo
import com.smartsensor.resistancemeter.model.ResistanceData
import com.smartsensor.resistancemeter.utils.Constants
import com.smartsensor.resistancemeter.utils.DataParser
import java.util.*

class BluetoothService : Service() {

    companion object {
        private const val TAG = "BluetoothService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "bluetooth_service_channel"
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var leScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // LiveData for UI updates
    private val _scannedDevices = MutableLiveData<List<DeviceInfo>>(emptyList())
    val scannedDevices: LiveData<List<DeviceInfo>> = _scannedDevices

    private val _connectionState = MutableLiveData<Boolean>(false)
    val connectionState: LiveData<Boolean> = _connectionState

    private val _resistanceData = MutableLiveData<ResistanceData>()
    val resistanceData: LiveData<ResistanceData> = _resistanceData

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val deviceList = mutableListOf<DeviceInfo>()
    private var connectedDevice: DeviceInfo? = null
    
    // Auto-reconnect functionality
    private var isAutoReconnectEnabled = true
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private val baseReconnectDelayMs = 2000L // 基础延迟 2 秒
    private val maxReconnectDelayMs = 30000L // 最大延迟 30 秒
    private var reconnectRunnable: Runnable? = null

    // 计算指数退避延迟
    private fun getReconnectDelay(): Long {
        // 指数退避: 2s, 4s, 8s, 16s, 30s, 30s...
        val delay = baseReconnectDelayMs * (1 shl reconnectAttempts.coerceAtMost(4))
        return delay.coerceAtMost(maxReconnectDelayMs)
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 只有在有蓝牙权限时才启动前台服务
        if (hasBluetoothPermissions()) {
            startForeground(NOTIFICATION_ID, createNotification("Bluetooth service running"))
        } else {
            Log.w(TAG, "Bluetooth permissions not granted, waiting for permissions before starting foreground service")
        }
        initializeBluetooth()
    }

    // 检查是否有蓝牙权限
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasConnect = checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            hasScan && hasConnect
        } else {
            true // 低版本不需要运行时权限
        }
    }

    // 当权限被授予后，由外部调用此方法启动前台服务
    fun startForegroundServiceIfNeeded() {
        if (hasBluetoothPermissions()) {
            try {
                startForeground(NOTIFICATION_ID, createNotification("Bluetooth service running"))
                Log.d(TAG, "Foreground service started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service: ${e.message}")
            }
        } else {
            Log.w(TAG, "Cannot start foreground service: missing Bluetooth permissions")
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keep Bluetooth connection active in background"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String): android.app.Notification {
        val notificationIntent = Intent(this, com.smartsensor.resistancemeter.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Resistance Meter")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        leScanner = bluetoothAdapter?.bluetoothLeScanner

        Log.d(TAG, "蓝牙适配器初始化: ${bluetoothAdapter != null}")
        Log.d(TAG, "蓝牙扫描器初始化: ${leScanner != null}")
        Log.d(TAG, "蓝牙是否启用: ${bluetoothAdapter?.isEnabled}")
    }

    // 修复后的扫描方法
    fun startScan() {
        Log.d(TAG, "========== 开始扫描 ==========")

        if (isScanning) {
            Log.w(TAG, "扫描已在进行中，跳过")
            return
        }

        // 检查基本条件
        if (leScanner == null) {
            Log.e(TAG, "蓝牙扫描器为null")
            _errorMessage.postValue("蓝牙扫描器未初始化")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "蓝牙未启用")
            _errorMessage.postValue("请先启用蓝牙")
            return
        }

        // 保留已连接的设备，只清除未连接的设备
        val connectedDevices = deviceList.filter { it.isConnected }
        deviceList.clear()
        deviceList.addAll(connectedDevices)
        _scannedDevices.postValue(deviceList.toList())

        // 设置扫描超时
        handler.postDelayed({
            if (isScanning) {
                Log.d(TAG, "扫描超时，停止扫描。共发现设备: ${deviceList.size}")
                stopScan()
            }
        }, Constants.SCAN_PERIOD)

        // 扫描设置 - 使用最宽松的设置
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)  // 高功耗但快速
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)  // 所有匹配
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)  // 积极匹配
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)  // 最大匹配数
            .setReportDelay(0L)  // 立即报告
            .build()

        try {
            isScanning = true
            Log.d(TAG, "启动无过滤器扫描...")

            // 先尝试无过滤器扫描（能发现所有BLE设备）
            leScanner?.startScan(emptyList(), settings, scanCallback)
            Log.d(TAG, "扫描已启动，等待回调...")

        } catch (e: SecurityException) {
            Log.e(TAG, "扫描权限错误: ${e.message}")
            _errorMessage.postValue("扫描权限不足: ${e.message}")
            isScanning = false
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "扫描启动异常: ${e.message}")
            _errorMessage.postValue("扫描启动失败: ${e.message}")
            isScanning = false
            e.printStackTrace()
        }
    }

    fun stopScan() {
        Log.d(TAG, "========== 停止扫描 ==========")
        if (isScanning && leScanner != null) {
            try {
                isScanning = false
                leScanner?.stopScan(scanCallback)
                Log.d(TAG, "扫描已停止，共发现设备: ${deviceList.size}")
            } catch (e: Exception) {
                Log.e(TAG, "停止扫描失败: ${e.message}")
            }
        }
    }

    // 修复后的扫描回调
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            val deviceName = device.name ?: "Unknown"
            val deviceAddress = device.address

            Log.d(TAG, "===== 发现设备 =====")
            Log.d(TAG, "设备名称: $deviceName")
            Log.d(TAG, "设备地址: $deviceAddress")
            Log.d(TAG, "信号强度: $rssi dBm")
            Log.d(TAG, "回调类型: $callbackType")

            // 过滤无效设备名
            if (!DeviceInfo.isValidDevice(device)) {
                Log.d(TAG, "设备被过滤：设备名为空或为Unknown Device")
                return
            }

            // 记录广播数据
            val scanRecord = result.scanRecord
            if (scanRecord != null) {
                Log.d(TAG, "广播数据长度: ${scanRecord.bytes?.size}")
                Log.d(TAG, "设备类型: ${scanRecord.deviceName}")
                Log.d(TAG, "服务UUID: ${scanRecord.serviceUuids}")

                // 打印原始广播数据
                scanRecord.bytes?.let { bytes ->
                    val hexString = bytes.joinToString(" ") { "%02x".format(it) }
                    Log.d(TAG, "原始广播数据: $hexString")
                }
            }

            // 检查设备是否已存在
            val existingDevice = deviceList.find { it.address == deviceAddress }
            if (existingDevice != null) {
                existingDevice.updateRssi(rssi)
                Log.d(TAG, "更新现有设备信号强度")
            } else {
                val deviceInfo = DeviceInfo(device, rssi)
                deviceList.add(deviceInfo)
                Log.d(TAG, "添加新设备到列表，当前总数: ${deviceList.size}")
            }

            // 更新UI
            _scannedDevices.postValue(deviceList.toList())
            Log.d(TAG, "UI已更新，设备列表大小: ${deviceList.size}")
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            Log.d(TAG, "批量扫描结果: ${results.size} 个设备")
            val validResults = results.filter { DeviceInfo.isValidDevice(it.device) }
            Log.d(TAG, "过滤后有效设备: ${validResults.size} 个")
            for (result in validResults) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "===== 扫描失败 =====")
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "扫描已经启动"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "应用注册失败"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "设备不支持扫描功能"
                SCAN_FAILED_INTERNAL_ERROR -> "内部错误"
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "硬件资源不足"
                else -> "未知错误 (代码: $errorCode)"
            }
            Log.e(TAG, "扫描失败原因: $errorMsg")
            _errorMessage.postValue("蓝牙扫描失败: $errorMsg")
            isScanning = false
        }
    }

    // 连接设备
    fun connectDevice(deviceInfo: DeviceInfo) {
        Log.d(TAG, "尝试连接设备: ${deviceInfo.name} (${deviceInfo.address})")

        // 先关闭现有连接
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: Exception) {
            Log.e(TAG, "关闭旧连接失败: ${e.message}")
        }

        // 记录要连接的设备
        connectedDevice = deviceInfo

        val device = deviceInfo.device
        try {
            // 使用 autoConnect=true 提高连接稳定性
            // autoConnect=true: 后台自动重连，但连接速度较慢
            // autoConnect=false: 直接连接，速度快但不稳定
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(this, false, gattCallback)
            }
            Log.d(TAG, "GATT连接已发起 (autoConnect=false, TRANSPORT_LE)")
        } catch (e: SecurityException) {
            Log.e(TAG, "连接权限不足: ${e.message}")
            _errorMessage.postValue("连接权限不足")
        } catch (e: Exception) {
            Log.e(TAG, "连接失败: ${e.message}")
            _errorMessage.postValue("连接失败: ${e.message}")
        }
    }

    // GATT回调保持不变...
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            // 记录状态码，帮助诊断问题
            val statusMsg = when (status) {
                BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
                133 -> "GATT_ERROR (133 - 常见错误，通常需要重连)"
                8 -> "GATT_CONN_TIMEOUT (连接超时)"
                19 -> "GATT_CONN_TERMINATE_PEER_USER (远程设备断开)"
                22 -> "GATT_CONN_TERMINATE_LOCAL_HOST (本地断开)"
                34 -> "GATT_CONN_LMP_TIMEOUT (LMP超时)"
                else -> "Unknown ($status)"
            }
            Log.d(TAG, "onConnectionStateChange: status=$statusMsg, newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // 只有在 status 为 GATT_SUCCESS 时才认为连接成功
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "========== BLUETOOTH CONNECTED ==========")
                        Log.d(TAG, "Device connected successfully!")
                        _connectionState.postValue(true)
                        updateDeviceConnectionState(true)

                        // Reset reconnect attempts on successful connection
                        if (reconnectAttempts > 0) {
                            Log.d(TAG, "✓ Reconnection successful after ${reconnectAttempts} attempts")
                            reconnectAttempts = 0

                            // Update notification back to normal
                            val notification = createNotification("Bluetooth service running")
                            val notificationManager = getSystemService(NotificationManager::class.java)
                            notificationManager.notify(NOTIFICATION_ID, notification)
                        }

                        handler.postDelayed({
                            try {
                                Log.d(TAG, "开始服务发现")
                                gatt.discoverServices()
                            } catch (e: SecurityException) {
                                Log.e(TAG, "服务发现权限不足: ${e.message}")
                                _errorMessage.postValue("服务发现权限不足")
                            }
                        }, 600)
                        Log.d(TAG, "==========================================")
                    } else {
                        Log.e(TAG, "Connection established but with error status: $statusMsg")
                        // 连接状态异常，关闭并重连
                        gatt.close()
                        val deviceToReconnect = connectedDevice
                        if (isAutoReconnectEnabled && deviceToReconnect != null) {
                            handler.postDelayed({
                                startAutoReconnect(deviceToReconnect)
                            }, 1000)
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "========== BLUETOOTH DISCONNECTED ==========")
                    Log.d(TAG, "Disconnect reason: $statusMsg")
                    _connectionState.postValue(false)

                    // 更新设备列表中的连接状态
                    updateDeviceConnectionState(false)

                    // Store the disconnected device info for reconnection
                    val deviceToReconnect = connectedDevice

                    bluetoothGatt?.close()
                    bluetoothGatt = null

                    // 根据断开原因决定是否重连
                    val shouldReconnect = when (status) {
                        BluetoothGatt.GATT_SUCCESS -> true // 正常断开，可能是信号问题
                        133 -> true // 常见错误，重连通常能解决
                        8 -> true // 超时，可能是距离远了
                        19 -> false // 远程设备主动断开，不重连
                        22 -> false // 本地主动断开，不重连
                        else -> true // 其他错误，尝试重连
                    }

                    if (shouldReconnect && isAutoReconnectEnabled && deviceToReconnect != null) {
                        Log.d(TAG, "Starting auto-reconnect for device: ${deviceToReconnect.name}")
                        startAutoReconnect(deviceToReconnect)
                    } else {
                        if (!shouldReconnect) {
                            Log.d(TAG, "Not reconnecting due to disconnect reason: $statusMsg")
                        }
                        if (!isAutoReconnectEnabled) {
                            connectedDevice = null
                            Log.d(TAG, "Auto-reconnect disabled")
                        }
                    }
                    Log.d(TAG, "==========================================")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "服务发现成功")

                // 请求优化的连接参数以提高稳定性
                // Android 8.0+ (API 26+) 支持请求连接参数
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        // 请求高优先级连接（低延迟、高稳定性）
                        val success = gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        Log.d(TAG, "请求高优先级连接: ${if (success) "成功" else "失败"}")
                    } catch (e: Exception) {
                        Log.e(TAG, "请求连接优先级失败: ${e.message}")
                    }
                }

                val service = gatt.getService(Constants.SERVICE_UUID)
                if (service != null) {
                    characteristic = service.getCharacteristic(Constants.CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        try {
                            gatt.setCharacteristicNotification(characteristic, true)

                            val descriptor = characteristic?.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                            )
                            descriptor?.let {
                                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(it)
                            }

                            Log.d(TAG, "特征通知已启用")
                        } catch (e: SecurityException) {
                            Log.e(TAG, "特征操作权限不足: ${e.message}")
                            _errorMessage.postValue("特征操作权限不足")
                        }
                    } else {
                        Log.e(TAG, "未找到指定特征: ${Constants.CHARACTERISTIC_UUID}")
                        _errorMessage.postValue("未找到指定的蓝牙特征")
                    }
                } else {
                    Log.e(TAG, "未找到指定服务: ${Constants.SERVICE_UUID}")
                    _errorMessage.postValue("未找到指定的蓝牙服务")
                }
            } else {
                Log.e(TAG, "服务发现失败: $status")
                _errorMessage.postValue("蓝牙服务发现失败")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            if (data != null) {
                Log.d(TAG, "Received BLE data, length: ${data.size}")
                val parsedData = DataParser.parseResistanceData(data)
                if (parsedData != null) {
                    Log.d(TAG, "Data parsed: R=${parsedData.resistance}Ω")
                    _resistanceData.postValue(parsedData)
                } else {
                    Log.w(TAG, "Data parsing failed")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "数据发送成功")
            } else {
                Log.e(TAG, "数据发送失败: $status")
                _errorMessage.postValue("数据发送失败")
            }
        }
    }

    // 发送数据到ESP32
    fun sendData(data: ByteArray): Boolean {
        return try {
            if (bluetoothGatt != null && characteristic != null) {
                characteristic?.value = data
                bluetoothGatt?.writeCharacteristic(characteristic) ?: false
            } else {
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "发送数据权限不足: ${e.message}")
            _errorMessage.postValue("发送数据权限不足")
            false
        }
    }

    // 更新设备列表中的连接状态
    private fun updateDeviceConnectionState(connected: Boolean) {
        Log.d(TAG, "updateDeviceConnectionState called: connected=$connected")
        Log.d(TAG, "connectedDevice: ${connectedDevice?.name} (${connectedDevice?.address})")
        Log.d(TAG, "deviceList size: ${deviceList.size}")
        
        connectedDevice?.let { device ->
            // 找到设备列表中对应的设备并更新连接状态
            val deviceInList = deviceList.find { it.address == device.address }
            
            if (deviceInList != null) {
                Log.d(TAG, "Found device in list, updating connection state to $connected")
                deviceInList.setConnectionState(connected)
                Log.d(TAG, "Device ${deviceInList.name} isConnected now: ${deviceInList.isConnected}")
            } else {
                Log.w(TAG, "Device not found in list! Connected device address: ${device.address}")
                Log.d(TAG, "Available devices in list:")
                deviceList.forEachIndexed { index, dev ->
                    Log.d(TAG, "  [$index] ${dev.name} (${dev.address}) - connected: ${dev.isConnected}")
                }
                
                // 如果设备不在列表中，添加它
                Log.d(TAG, "Adding connected device to list")
                device.setConnectionState(connected)
                deviceList.add(device)
            }
            
            // 如果断开连接，清除所有设备的连接状态
            if (!connected) {
                Log.d(TAG, "Disconnected - clearing all device connection states")
                deviceList.forEach { it.setConnectionState(false) }
            }
            
            // 通知UI更新
            Log.d(TAG, "Posting updated device list to UI")
            _scannedDevices.postValue(deviceList.toList())
            Log.d(TAG, "Device connection state updated: ${device.name} = $connected")
        } ?: run {
            Log.w(TAG, "connectedDevice is null, cannot update connection state")
        }
    }

    // 断开连接
    fun disconnect() {
        try {
            isAutoReconnectEnabled = false // Disable auto-reconnect when manually disconnecting
            cancelAutoReconnect()
            bluetoothGatt?.disconnect()
        } catch (e: SecurityException) {
            Log.e(TAG, "断开连接权限不足: ${e.message}")
        }
    }
    
    // Auto-reconnect functionality
    private fun startAutoReconnect(targetDevice: DeviceInfo) {
        Log.d(TAG, "========== Starting Auto-Reconnect ==========")
        Log.d(TAG, "Target device: ${targetDevice.name} (${targetDevice.address})")
        Log.d(TAG, "Attempt: ${reconnectAttempts + 1}/$maxReconnectAttempts")

        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "Max reconnection attempts reached, giving up")
            _errorMessage.postValue("Unable to reconnect after $maxReconnectAttempts attempts")
            resetReconnectState()
            return
        }

        reconnectAttempts++
        val delayMs = getReconnectDelay()

        // Update notification to show reconnecting status
        val notification = createNotification("Reconnecting in ${delayMs/1000}s... (${reconnectAttempts}/$maxReconnectAttempts)")
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Cancel any pending reconnect
        cancelAutoReconnect()

        // Schedule reconnection attempt with exponential backoff
        reconnectRunnable = Runnable {
            Log.d(TAG, "Executing reconnection attempt ${reconnectAttempts}")
            attemptReconnection(targetDevice)
        }

        handler.postDelayed(reconnectRunnable!!, delayMs)
        Log.d(TAG, "Reconnect scheduled in ${delayMs}ms (exponential backoff)")
        Log.d(TAG, "============================================")
    }
    
    private fun attemptReconnection(targetDevice: DeviceInfo) {
        Log.d(TAG, "========== Attempting Reconnection ==========")
        Log.d(TAG, "Looking for device: ${targetDevice.name} (${targetDevice.address})")

        // 直接使用已知的 MAC 地址重连，不需要扫描
        try {
            if (bluetoothAdapter?.isEnabled == true) {
                Log.d(TAG, "Trying direct connection to known device...")
                val device = bluetoothAdapter?.getRemoteDevice(targetDevice.address)
                if (device != null) {
                    connectedDevice = targetDevice // Restore connected device info

                    // 先确保之前的连接已关闭
                    try {
                        bluetoothGatt?.disconnect()
                        bluetoothGatt?.close()
                        bluetoothGatt = null
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing previous GATT: ${e.message}")
                    }

                    // 延迟一下再重连，避免连接冲突
                    handler.postDelayed({
                        try {
                            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                            } else {
                                device.connectGatt(this, false, gattCallback)
                            }
                            Log.d(TAG, "Direct reconnection attempt initiated")
                        } catch (e: Exception) {
                            Log.e(TAG, "Direct reconnection failed: ${e.message}")
                            // 如果直连失败，尝试扫描
                            handler.postDelayed({
                                startReconnectScan(targetDevice)
                            }, 1000)
                        }
                    }, 500)
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct connection preparation failed: ${e.message}")
        }

        // If direct connection fails, start scanning as fallback
        Log.d(TAG, "Direct connection not possible, starting scan for device...")
        handler.postDelayed({
            startReconnectScan(targetDevice)
        }, 1000)
    }
    
    private fun startReconnectScan(targetDevice: DeviceInfo) {
        if (isScanning) {
            Log.d(TAG, "Already scanning, stopping current scan")
            stopScan()
        }
        
        Log.d(TAG, "Starting scan to find device: ${targetDevice.name}")
        
        // Set up a scan callback specifically for reconnection
        val reconnectScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceAddress = device?.address
                
                if (deviceAddress == targetDevice.address) {
                    Log.d(TAG, "✓ Found target device during reconnect scan!")
                    stopScan()
                    
                    // Attempt connection
                    try {
                        connectedDevice = targetDevice
                        bluetoothGatt = device.connectGatt(this@BluetoothService, false, gattCallback)
                        Log.d(TAG, "Reconnection attempt initiated")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Reconnection failed due to permissions: ${e.message}")
                        scheduleNextReconnectAttempt(targetDevice)
                    }
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Reconnect scan failed with error: $errorCode")
                scheduleNextReconnectAttempt(targetDevice)
            }
        }
        
        try {
            if (bluetoothAdapter?.isEnabled == true) {
                leScanner?.startScan(reconnectScanCallback)
                isScanning = true
                Log.d(TAG, "Reconnect scan started")
                
                // Stop scanning after 10 seconds if device not found
                handler.postDelayed({
                    if (isScanning) {
                        Log.d(TAG, "Reconnect scan timeout, device not found")
                        try {
                            leScanner?.stopScan(reconnectScanCallback)
                            isScanning = false
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Stop scan permission error: ${e.message}")
                        }
                        scheduleNextReconnectAttempt(targetDevice)
                    }
                }, 10000)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Scan start permission error: ${e.message}")
            scheduleNextReconnectAttempt(targetDevice)
        }
    }
    
    private fun scheduleNextReconnectAttempt(targetDevice: DeviceInfo) {
        if (reconnectAttempts < maxReconnectAttempts) {
            val delayMs = getReconnectDelay()
            Log.d(TAG, "Scheduling next reconnection attempt in ${delayMs}ms...")
            handler.postDelayed({
                startAutoReconnect(targetDevice)
            }, delayMs)
        } else {
            Log.e(TAG, "All reconnection attempts exhausted")
            resetReconnectState()
        }
    }
    
    private fun cancelAutoReconnect() {
        reconnectRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            reconnectRunnable = null
            Log.d(TAG, "Auto-reconnect cancelled")
        }
    }
    
    private fun resetReconnectState() {
        Log.d(TAG, "Resetting reconnect state")
        reconnectAttempts = 0
        cancelAutoReconnect()
        connectedDevice = null
        
        // Update notification back to normal
        val notification = createNotification("Bluetooth service running")
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    // Public methods to control auto-reconnect
    fun setAutoReconnectEnabled(enabled: Boolean) {
        isAutoReconnectEnabled = enabled
        Log.d(TAG, "Auto-reconnect ${if (enabled) "enabled" else "disabled"}")
        if (!enabled) {
            cancelAutoReconnect()
        }
    }
    
    fun isAutoReconnectEnabled(): Boolean = isAutoReconnectEnabled

    override fun onDestroy() {
        super.onDestroy()
        isAutoReconnectEnabled = false
        cancelAutoReconnect()
        disconnect()
        stopScan()
    }
}