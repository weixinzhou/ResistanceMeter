package com.smartsensor.resistancemeter.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

object BluetoothUtils {

    private const val TAG = "BluetoothUtils"

    fun isBluetoothSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        return bluetoothManager?.adapter
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = getBluetoothAdapter(context)
        return adapter?.isEnabled == true
    }

    // 检查位置服务是否开启（仅Android 11及以下需要）
    fun isLocationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 不需要位置服务（如果使用 neverForLocation）
            return true
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        val requiredPermissions = getRequiredPermissions()
        val result = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        Log.d(TAG, "权限检查结果: $result")
        requiredPermissions.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "权限 $permission: $granted")
        }

        return result
    }

    fun requestPermissions(activity: Activity) {
        val requiredPermissions = getRequiredPermissions()
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }

        Log.d(TAG, "缺少的权限: $missingPermissions")

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                Constants.REQUEST_PERMISSIONS
            )
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            Log.d(TAG, "使用Android 12+权限模型")
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                // 注意：如果在manifest中使用了neverForLocation，就不需要位置权限
                // Manifest.permission.ACCESS_FINE_LOCATION
            ))

            // Android 14+ (API 34+) 需要前台服务权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
            }
        } else {
            // Android 11及以下
            Log.d(TAG, "使用传统蓝牙权限模型")
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ))

            // Android 6+ 需要位置权限进行BLE扫描
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 需要精确位置
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    // Android 6-9 可以使用粗略位置
                    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }

        Log.d(TAG, "所需权限: $permissions")
        return permissions
    }

    // 获取权限状态详情（用于调试）
    fun getPermissionStatus(context: Context): Map<String, Boolean> {
        val permissions = getRequiredPermissions()
        return permissions.associateWith { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 检查是否可以进行BLE扫描
    fun canStartBleScan(context: Context): Boolean {
        val hasPermissions = hasRequiredPermissions(context)
        val bluetoothEnabled = isBluetoothEnabled(context)
        val locationEnabled = isLocationEnabled(context)

        Log.d(TAG, "BLE扫描检查 - 权限:$hasPermissions, 蓝牙:$bluetoothEnabled, 位置:$locationEnabled")

        return hasPermissions && bluetoothEnabled && locationEnabled
    }
}