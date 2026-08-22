package com.smartsensor.resistancemeter.fragment

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.smartsensor.resistancemeter.R
import com.smartsensor.resistancemeter.databinding.FragmentSettingsBinding
import com.smartsensor.resistancemeter.utils.DataExporter
import com.smartsensor.resistancemeter.viewmodel.DataViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferences: SharedPreferences
    private val dataViewModel: DataViewModel by activityViewModels()
    private var selectedExportFormat = 0
    private var bluetoothService: com.smartsensor.resistancemeter.service.BluetoothService? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            performDataExport()
        } else {
            Toast.makeText(context, "Storage permission required to export data", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        setupExportFormatSpinner()
        setupClickListeners()
        setupAutoSaveObservers()
        loadSettings()
    }

    private fun setupExportFormatSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.export_formats,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.exportFormatSpinner.adapter = adapter
        
        binding.exportFormatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedExportFormat = position
                preferences.edit().putInt("export_format", position).apply()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            saveButton.setOnClickListener { saveSettings() }
            exportButton.setOnClickListener { exportData() }
            
            // 自动保存相关按钮
            autoSaveSwitch.setOnCheckedChangeListener { _, isChecked ->
                dataViewModel.setAutoSaveEnabled(isChecked)
            }
            
            // 自动重连按钮
            autoConnectSwitch.setOnCheckedChangeListener { _, isChecked ->
                bluetoothService?.setAutoReconnectEnabled(isChecked)
                preferences.edit().putBoolean("auto_reconnect_enabled", isChecked).apply()
            }
            
            saveIntervalEdit.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    try {
                        val interval = saveIntervalEdit.text.toString().toInt()
                        if (interval in 1..60) {
                            dataViewModel.setSaveInterval(interval)
                            binding.saveStatusText.text = "Save interval updated to $interval minutes"
                            binding.saveStatusText.setTextColor(0xFF4CAF50.toInt())
                        } else {
                            binding.saveStatusText.text = "Invalid interval: must be 1-60 minutes"
                            binding.saveStatusText.setTextColor(0xFFF44336.toInt())
                            // 恢复之前的值
                            val autoSavePrefs = requireContext().getSharedPreferences("auto_save_settings", 0)
                            saveIntervalEdit.setText(autoSavePrefs.getInt("save_interval_minutes", 5).toString())
                        }
                    } catch (e: NumberFormatException) {
                        // 忽略无效输入
                    }
                }
            }
            
            maxDataPointsEdit.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    try {
                        val maxPoints = maxDataPointsEdit.text.toString().toInt()
                        if (maxPoints in 100..10000) {
                            dataViewModel.setMaxDataPoints(maxPoints)
                            binding.saveStatusText.text = "Max data points updated to $maxPoints"
                            binding.saveStatusText.setTextColor(0xFF4CAF50.toInt())
                        } else {
                            binding.saveStatusText.text = "Invalid max points: must be 100-10000"
                            binding.saveStatusText.setTextColor(0xFFF44336.toInt())
                            // 恢复之前的值
                            val autoSavePrefs = requireContext().getSharedPreferences("auto_save_settings", 0)
                            maxDataPointsEdit.setText(autoSavePrefs.getInt("max_data_points", 1000).toString())
                        }
                    } catch (e: NumberFormatException) {
                        // 忽略无效输入
                    }
                }
            }
            
            maxFilesEdit.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    try {
                        val maxFiles = maxFilesEdit.text.toString().toInt()
                        if (maxFiles >= 0) {
                            dataViewModel.setMaxFiles(maxFiles)
                            val statusText = if (maxFiles == 0) {
                                "Max files updated to unlimited"
                            } else {
                                "Max files updated to $maxFiles"
                            }
                            binding.saveStatusText.text = statusText
                            binding.saveStatusText.setTextColor(0xFF4CAF50.toInt())
                        } else {
                            binding.saveStatusText.text = "Invalid max files: must be >= 0"
                            binding.saveStatusText.setTextColor(0xFFF44336.toInt())
                            // 恢复之前的值
                            val autoSavePrefs = requireContext().getSharedPreferences("auto_save_settings", 0)
                            maxFilesEdit.setText(autoSavePrefs.getInt("max_files", 0).toString())
                        }
                    } catch (e: NumberFormatException) {
                        // 忽略无效输入
                    }
                }
            }
        }
    }
    
    private fun setupAutoSaveObservers() {
        // 观察自动保存状态
        dataViewModel.autoSaveEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.autoSaveSwitch.isChecked = enabled
            binding.autoSaveStatusText.text = if (enabled) "Enabled" else "Disabled"
        }
        
        // 观察最后保存时间
        dataViewModel.lastSaveTime.observe(viewLifecycleOwner) { timestamp ->
            if (timestamp > 0) {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                binding.lastSaveTimeText.text = "Last saved: ${format.format(Date(timestamp))}"
            } else {
                binding.lastSaveTimeText.text = "Last saved: None"
            }
        }
        
        // 观察当前批次信息
        dataViewModel.currentBatchInfo.observe(viewLifecycleOwner) { batchInfo ->
            if (batchInfo != null) {
                val durationSec = batchInfo.durationMs / 1000
                binding.currentBatchText.text = 
                    "Current batch: ${batchInfo.dataCount} data points, ${batchInfo.alarmCount} alarms, ${durationSec}s duration"
            } else {
                binding.currentBatchText.text = "Current batch: No data"
            }
        }
        
        // 观察保存状态
        dataViewModel.saveStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is com.smartsensor.resistancemeter.utils.AutoSaveManager.SaveStatus.Saving -> {
                    binding.saveStatusText.text = "Saving..."
                    binding.saveStatusText.setTextColor(0xFF2196F3.toInt())
                }
                is com.smartsensor.resistancemeter.utils.AutoSaveManager.SaveStatus.Success -> {
                    binding.saveStatusText.text = "Save successful"
                    binding.saveStatusText.setTextColor(0xFF4CAF50.toInt())
                    // 移除Toast，状态已在UI中显示
                    updateStorageInfo() // 更新存储信息
                }
                is com.smartsensor.resistancemeter.utils.AutoSaveManager.SaveStatus.Error -> {
                    binding.saveStatusText.text = "Save failed: ${status.message}"
                    binding.saveStatusText.setTextColor(0xFFF44336.toInt())
                    // 移除Toast，错误信息已在UI中显示
                }
            }
        }
        
        // 显示存储信息
        updateStorageInfo()
    }

    private fun loadSettings() {
        // Get BluetoothService from MainActivity
        bluetoothService = (activity as? com.smartsensor.resistancemeter.MainActivity)?.getBluetoothService()
        
        binding.apply {
            // 加载自动重连设置
            val autoReconnectEnabled = preferences.getBoolean("auto_reconnect_enabled", true)
            autoConnectSwitch.isChecked = autoReconnectEnabled
            bluetoothService?.setAutoReconnectEnabled(autoReconnectEnabled)
            
            samplingRateEdit.setText(preferences.getInt("sampling_rate", 10).toString())
            chartMaxEntriesEdit.setText(preferences.getInt("chart_max_entries", 100).toString())
            
            selectedExportFormat = preferences.getInt("export_format", 0)
            exportFormatSpinner.setSelection(selectedExportFormat)
            
            // 加载自动保存设置
            val autoSavePrefs = requireContext().getSharedPreferences("auto_save_settings", 0)
            saveIntervalEdit.setText(autoSavePrefs.getInt("save_interval_minutes", 5).toString())
            maxDataPointsEdit.setText(autoSavePrefs.getInt("max_data_points", 1000).toString())
            maxFilesEdit.setText(autoSavePrefs.getInt("max_files", 0).toString())
        }
    }
    
    private fun updateStorageInfo() {
        lifecycleScope.launch {
            val storageInfo = dataViewModel.getStorageInfo()
            binding.storageInfoText.text = 
                "Saved ${storageInfo.fileCount} files, total size: ${"%.2f".format(storageInfo.getTotalSizeMB())}MB"
                
            val files = dataViewModel.getAutoSaveFiles()
            if (files.isNotEmpty()) {
                val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                val recentFiles = files.take(3).joinToString("\n") { file ->
                    "• ${file.name.substringAfter("resistance_data_").substringBefore(".csv")}"
                }
                binding.recentFilesText.text = "Recent files:\n$recentFiles"
            } else {
                binding.recentFilesText.text = "No auto-save files yet"
            }
        }
    }

    private fun saveSettings() {
        try {
            val editor = preferences.edit()
            binding.apply {
                editor.putBoolean("auto_connect", autoConnectSwitch.isChecked)
                editor.putInt("sampling_rate", samplingRateEdit.text.toString().toInt())
                editor.putInt("chart_max_entries", chartMaxEntriesEdit.text.toString().toInt())
            }
            editor.apply()

            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
        } catch (e: NumberFormatException) {
            Toast.makeText(context, "Please enter valid numbers", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportData() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            performDataExport()
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                performDataExport()
            } else {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun performDataExport() {
        android.util.Log.d("SettingsFragment", "Starting data export...")
        val data = dataViewModel.exportData()
        android.util.Log.d("SettingsFragment", "Got ${data.size} data points from ViewModel")
        
        if (data.isEmpty()) {
            Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val success = when (selectedExportFormat) {
                0 -> DataExporter.exportToCSV(requireContext(), data)
                1 -> DataExporter.exportToJSON(requireContext(), data)
                2 -> DataExporter.exportToTXT(requireContext(), data)
                else -> false
            }
            
            if (!success) {
                Toast.makeText(context, "Export failed, please check storage permissions", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}