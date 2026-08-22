package com.smartsensor.resistancemeter

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.smartsensor.resistancemeter.databinding.ActivityMainBinding
import com.smartsensor.resistancemeter.fragment.*
import com.smartsensor.resistancemeter.service.BluetoothService
import com.smartsensor.resistancemeter.utils.BluetoothUtils
import com.smartsensor.resistancemeter.utils.Constants
import com.smartsensor.resistancemeter.viewmodel.DataViewModel

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private var bluetoothService: BluetoothService? = null
    private var serviceBound = false

    // ViewModels
    private val dataViewModel: DataViewModel by viewModels()

    // 添加LiveData来通知Fragment服务状态
    private val _serviceReady = MutableLiveData<Boolean>(false)
    val serviceReady get() = _serviceReady.value ?: false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "服务连接成功")
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            serviceBound = true
            _serviceReady.value = true
            Log.d(TAG, "BluetoothService已就绪")

            // 如果权限已授予，启动前台服务
            if (BluetoothUtils.hasRequiredPermissions(this@MainActivity)) {
                bluetoothService?.startForegroundServiceIfNeeded()
            }

            // 连接BluetoothService的数据流到DataViewModel
            setupDataObserver()

            // 监听连接状态变化
            setupConnectionStateObserver()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "服务连接断开")
            serviceBound = false
            bluetoothService = null
            _serviceReady.value = false
        }
    }

    // 权限请求结果处理
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "权限请求结果: $permissions")

        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            // 权限授予后，启动前台服务
            bluetoothService?.startForegroundServiceIfNeeded()
            checkBluetoothAfterPermission()
        } else {
            val deniedPermissions = permissions.entries.filter { !it.value }.map { it.key }
            Log.w(TAG, "被拒绝的权限: $deniedPermissions")
            showPermissionDeniedDialog(deniedPermissions)
        }
    }

    // 蓝牙启用请求结果处理
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "蓝牙已启用")
        } else {
            Toast.makeText(this, "需要启用蓝牙才能使用此应用", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Keep screen on to prevent doze mode issues (for testing)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Log.d(TAG, "MainActivity onCreate")
        initializeViews()
        checkBluetoothSupport()

        // 先绑定服务，再请求权限
        bindBluetoothService()
        requestPermissions()
    }

    private fun initializeViews() {
        val adapter = FragmentAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Connection"
                1 -> "Chart"
                2 -> "Control"
                3 -> "Settings"
                else -> ""
            }
        }.attach()
    }

    private fun checkBluetoothSupport() {
        if (!BluetoothUtils.isBluetoothSupported(this)) {
            Toast.makeText(this, "Device does not support Bluetooth LE", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.d(TAG, "Device supports Bluetooth LE")
    }

    private fun bindBluetoothService() {
        Log.d(TAG, "开始绑定BluetoothService")
        val intent = Intent(this, BluetoothService::class.java)

        // 先启动服务，再绑定
        startService(intent)
        val bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "服务绑定结果: $bound")
    }

    private fun requestPermissions() {
        Log.d(TAG, "检查权限状态")
        val permissionStatus = BluetoothUtils.getPermissionStatus(this)
        Log.d(TAG, "当前权限状态: $permissionStatus")

        if (!BluetoothUtils.hasRequiredPermissions(this)) {
            Log.d(TAG, "需要请求权限")
            showPermissionExplanationDialog()
        } else {
            Log.d(TAG, "权限已授予")
            checkBluetoothAfterPermission()
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("App requires Bluetooth and nearby devices permissions to scan and connect to ESP32-S3 devices.\n\nOn Android 12+ devices, these permissions appear as 'Nearby devices' permission.")
            .setPositiveButton("Grant Permission") { _, _ ->
                BluetoothUtils.requestPermissions(this)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Cannot use Bluetooth functionality without permissions", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog(deniedPermissions: List<String>) {
        val message = "The following permissions were denied, app may not function properly:\n" +
                deniedPermissions.joinToString("\n") {
                    when {
                        it.contains("BLUETOOTH") -> "• Bluetooth permission (Nearby devices)"
                        it.contains("LOCATION") -> "• Location permission"
                        else -> "• $it"
                    }
                } + "\n\nRecommend going to settings to grant permissions manually."

        AlertDialog.Builder(this)
            .setTitle("Insufficient Permissions")
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Continue") { _, _ ->
                checkBluetoothAfterPermission()
            }
            .show()
    }

    private fun checkBluetoothAfterPermission() {
        if (!BluetoothUtils.isBluetoothEnabled(this)) {
            Log.d(TAG, "蓝牙未启用，请求启用")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            Log.d(TAG, "蓝牙已启用")
        }
    }
    
    private fun setupDataObserver() {
        Log.d(TAG, "========== Setting up BluetoothService data observer ==========")
        bluetoothService?.resistanceData?.observe(this) { resistanceData ->
            Log.d(TAG, "MainActivity received data: R=${resistanceData.resistance}Ω at ${resistanceData.timestamp}")
            Log.d(TAG, "Forwarding to DataViewModel...")
            dataViewModel.addData(resistanceData)
            Log.d(TAG, "Data forwarded to DataViewModel successfully")
        }
        Log.d(TAG, "Data observer setup complete")
    }
    
    private fun setupConnectionStateObserver() {
        Log.d(TAG, "========== Setting up connection state observer ==========")
        bluetoothService?.connectionState?.observe(this) { connected ->
            Log.d(TAG, "========== CONNECTION STATE CHANGE ==========")
            Log.d(TAG, "Connected: $connected")
            Log.d(TAG, "Service bound: $serviceBound")
            
            if (connected) {
                Log.d(TAG, "Connection established - monitoring for data flow...")
                // Start a timer to check if data flows within 10 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "10 seconds after connection - checking if data is flowing...")
                    Log.d(TAG, "Current total data points in DataViewModel: ${dataViewModel.historicalData.value?.size ?: 0}")
                }, 10000)
            } else {
                Log.d(TAG, "Connection lost - data flow will stop")
            }
            Log.d(TAG, "============================================")
        }
        Log.d(TAG, "Connection state observer setup complete")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        // 停止服务
        val intent = Intent(this, BluetoothService::class.java)
        stopService(intent)
    }

    // 处理权限请求结果的回调（兼容旧版本）
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.REQUEST_PERMISSIONS) {
            val results = permissions.zip(grantResults.toTypedArray()).toMap()
            Log.d(TAG, "权限请求结果（兼容回调）: $results")

            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                checkBluetoothAfterPermission()
            } else {
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }
                showPermissionDeniedDialog(deniedPermissions)
            }
        }
    }

    // 提供给Fragment的方法
    fun getBluetoothService(): BluetoothService? {
        Log.d(TAG, "getBluetoothService() 被调用 - serviceBound: $serviceBound, service: ${bluetoothService != null}")
        return if (serviceBound) bluetoothService else null
    }

    // 检查服务是否就绪
    fun isServiceReady(): Boolean {
        return serviceBound && bluetoothService != null
    }

    // 等待服务就绪的方法
    fun waitForService(callback: (BluetoothService) -> Unit) {
        if (isServiceReady()) {
            callback(bluetoothService!!)
        } else {
            // 设置一个短暂的检查循环
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var attempts = 0
            val maxAttempts = 50 // 最多等待5秒

            val checkRunnable = object : Runnable {
                override fun run() {
                    if (isServiceReady()) {
                        Log.d(TAG, "服务就绪，执行回调")
                        callback(bluetoothService!!)
                    } else if (attempts < maxAttempts) {
                        attempts++
                        handler.postDelayed(this, 100) // 每100ms检查一次
                    } else {
                        Log.e(TAG, "等待服务超时")
                        Toast.makeText(this@MainActivity, "蓝牙服务启动超时", Toast.LENGTH_LONG).show()
                    }
                }
            }

            handler.post(checkRunnable)
        }
    }

    private class FragmentAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ConnectionFragment()
                1 -> ChartFragment()
                2 -> ControlFragment()
                3 -> SettingsFragment()
                else -> ConnectionFragment()
            }
        }

        override fun getItemCount(): Int = 4
    }
}