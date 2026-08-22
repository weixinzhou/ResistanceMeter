package com.smartsensor.resistancemeter.fragment

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartsensor.resistancemeter.MainActivity
import com.smartsensor.resistancemeter.adapter.DeviceAdapter
import com.smartsensor.resistancemeter.databinding.FragmentConnectionBinding
import com.smartsensor.resistancemeter.model.DeviceInfo
import com.smartsensor.resistancemeter.service.BluetoothService
import com.smartsensor.resistancemeter.utils.BluetoothUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConnectionFragment : Fragment() {

    companion object {
        private const val TAG = "ConnectionFragment"
    }

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeviceAdapter
    private var bluetoothService: BluetoothService? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "ConnectionFragment onViewCreated")
        setupRecyclerView()
        setupClickListeners()

        // 等待服务就绪后再设置观察者
        waitForServiceAndSetupObservers()
        checkBluetoothStatus()
    }

    private fun waitForServiceAndSetupObservers() {
        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            Log.d(TAG, "等待BluetoothService就绪...")

            if (mainActivity.isServiceReady()) {
                Log.d(TAG, "服务已就绪，直接设置观察者")
                setupObservers()
            } else {
                Log.d(TAG, "服务未就绪，等待服务连接...")
                binding.statusText.text = "初始化蓝牙服务..."

                mainActivity.waitForService { service ->
                    Log.d(TAG, "服务就绪回调触发")
                    bluetoothService = service
                    setupObservers()
                    checkBluetoothStatus()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = DeviceAdapter { device ->
            connectToDevice(device)
        }

        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ConnectionFragment.adapter
        }
    }

    private fun setupClickListeners() {
        binding.scanButton.setOnClickListener {
            when {
                bluetoothService?.connectionState?.value == true -> disconnectDevice()
                else -> startScanWithChecks()
            }
        }
    }

    private fun setupObservers() {
        Log.d(TAG, "设置观察者")
        bluetoothService = (activity as? MainActivity)?.getBluetoothService()

        bluetoothService?.let { service ->
            Log.d(TAG, "BluetoothService不为null，设置LiveData观察者")

            service.scannedDevices.observe(viewLifecycleOwner) { devices ->
                Log.d(TAG, "Scanned device count: ${devices.size}")
                devices.forEachIndexed { index, device ->
                    Log.d(TAG, "  [$index] ${device.name} (${device.address}) - connected: ${device.isConnected}")
                }
                adapter.submitList(devices)
                val statusText = if (devices.isEmpty()) {
                    "No devices found"
                } else {
                    "Found ${devices.size} device(s)"
                }
                binding.statusText.text = statusText
            }

            service.connectionState.observe(viewLifecycleOwner) { connected ->
                if (connected) {
                    binding.statusText.text = "Device connected"
                    binding.scanButton.text = "Disconnect"
                } else {
                    binding.statusText.text = if (adapter.itemCount > 0) {
                        "Found ${adapter.itemCount} device(s)"
                    } else {
                        "Device not connected"
                    }
                    binding.scanButton.text = "Scan Devices"
                }
            }

            service.errorMessage.observe(viewLifecycleOwner) { error ->
                if (!error.isNullOrEmpty()) {
                    Log.e(TAG, "蓝牙错误: $error")
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            }
        } ?: run {
            Log.e(TAG, "BluetoothService仍然为null")
        }
    }

    private fun checkBluetoothStatus() {
        context?.let { ctx ->
            val permissionStatus = BluetoothUtils.getPermissionStatus(ctx)
            Log.d(TAG, "权限状态: $permissionStatus")

            when {
                !BluetoothUtils.isBluetoothSupported(ctx) -> {
                    binding.statusText.text = "Device does not support Bluetooth LE"
                    Log.e(TAG, "Device does not support Bluetooth LE")
                }
                !BluetoothUtils.hasRequiredPermissions(ctx) -> {
                    binding.statusText.text = "Bluetooth permissions required"
                    Log.w(TAG, "Missing Bluetooth permissions")
                }
                !BluetoothUtils.isBluetoothEnabled(ctx) -> {
                    binding.statusText.text = "Please enable Bluetooth"
                    Log.w(TAG, "Bluetooth not enabled")
                }
                !BluetoothUtils.isLocationEnabled(ctx) -> {
                    binding.statusText.text = "Please enable location services"
                    Log.w(TAG, "Location services not enabled")
                }
                bluetoothService == null -> {
                    binding.statusText.text = "Initializing Bluetooth service..."
                    Log.w(TAG, "Bluetooth service not ready")
                }
                else -> {
                    binding.statusText.text = "Tap 'Scan Devices' to start searching"
                    Log.d(TAG, "All conditions met, can start scanning")
                }
            }
        }
    }

    private fun startScanWithChecks() {
        Log.d(TAG, "开始扫描前检查...")

        context?.let { ctx ->
            // 1. 检查权限
            if (!BluetoothUtils.hasRequiredPermissions(ctx)) {
                Log.w(TAG, "权限不足，显示权限说明")
                showPermissionDialog()
                return
            }

            // 2. 检查蓝牙状态
            if (!BluetoothUtils.isBluetoothEnabled(ctx)) {
                Toast.makeText(ctx, "Please enable Bluetooth first", Toast.LENGTH_LONG).show()
                return
            }

            // 3. 检查位置服务
            if (!BluetoothUtils.isLocationEnabled(ctx)) {
                Log.w(TAG, "位置服务未启用")
                showLocationDialog()
                return
            }

            // 4. 检查服务状态
            if (bluetoothService == null) {
                Log.e(TAG, "BluetoothService is null")
                Toast.makeText(ctx, "Bluetooth service not ready, please wait...", Toast.LENGTH_SHORT).show()

                // 重新尝试获取服务
                waitForServiceAndSetupObservers()
                return
            }

            // 5. 开始扫描
            Log.d(TAG, "所有检查通过，开始扫描")
            startScan()
        }
    }

    private fun startScan() {
        bluetoothService?.let { service ->
            Log.d(TAG, "=== Sending scan command to BluetoothService ===")
            binding.statusText.text = "Scanning for devices..."
            binding.scanButton.isEnabled = false
            service.startScan()

            lifecycleScope.launch {
                delay(10000)
                if (isAdded && _binding != null) {
                    binding.scanButton.isEnabled = true
                    if (service.connectionState.value != true) {
                        binding.scanButton.text = "Scan Devices"
                    }
                }
            }
        } ?: run {
            Log.e(TAG, "*** BluetoothService still null ***")
            Toast.makeText(context, "Bluetooth service not ready", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permissions Required")
            .setMessage("App requires Bluetooth and location permissions to scan devices. Please grant relevant permissions in settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLocationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Location Services Required")
            .setMessage("Scanning Bluetooth devices requires location services to be enabled. Go to settings?")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun connectToDevice(device: DeviceInfo) {
        bluetoothService?.let { service ->
            binding.statusText.text = "Connecting to ${device.name}..."
            service.connectDevice(device)
        }
    }

    private fun disconnectDevice() {
        bluetoothService?.disconnect()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - 重新检查状态")
        checkBluetoothStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}