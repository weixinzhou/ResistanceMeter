package com.smartsensor.resistancemeter.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.smartsensor.resistancemeter.model.BaselineSettings
import com.smartsensor.resistancemeter.utils.BaselineManager
import com.smartsensor.resistancemeter.utils.AlarmManager
import java.text.SimpleDateFormat
import java.util.*

class BaselineSettingsDialog : DialogFragment() {
    
    private lateinit var baselineManager: BaselineManager
    private lateinit var alarmManager: AlarmManager
    private var onSettingsChanged: ((BaselineSettings?) -> Unit)? = null
    private var currentResistanceValue: Float = 0f
    
    // UI元素
    private lateinit var baselineValueText: TextView
    private lateinit var baselineTimestampText: TextView
    private lateinit var setBaselineButton: Button
    private lateinit var resetBaselineButton: Button
    private lateinit var alarmThresholdSeekBar: SeekBar
    private lateinit var alarmThresholdText: TextView
    private lateinit var alarmEnabledSwitch: Switch
    private lateinit var testAlarmButton: Button
    
    companion object {
        fun newInstance(
            currentResistance: Float,
            onSettingsChanged: (BaselineSettings?) -> Unit
        ): BaselineSettingsDialog {
            return BaselineSettingsDialog().apply {
                this.currentResistanceValue = currentResistance
                this.onSettingsChanged = onSettingsChanged
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        baselineManager = BaselineManager(requireContext())
        alarmManager = AlarmManager(requireContext())
        
        val scrollView = ScrollView(requireContext())
        val mainLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        
        // 创建UI元素
        createBaselineSection(mainLayout)
        createAlarmSection(mainLayout)
        
        scrollView.addView(mainLayout)
        loadCurrentSettings()
        setupListeners()
        
        return AlertDialog.Builder(requireContext())
            .setTitle("Baseline and Alarm Settings")
            .setView(scrollView)
            .setPositiveButton("OK") { _, _ ->
                saveSettings()
                onSettingsChanged?.invoke(baselineManager.getCurrentBaseline())
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
    
    private fun createBaselineSection(parent: LinearLayout) {
        // Baseline settings title
        val baselineTitle = TextView(requireContext()).apply {
            text = "Baseline Settings"
            textSize = 18f
            setPadding(0, 0, 0, 16)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        parent.addView(baselineTitle)
        
        // Current resistance value display
        val currentValueText = TextView(requireContext()).apply {
            text = "Current Resistance: ${String.format("%.2f", currentResistanceValue)} Ω"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        parent.addView(currentValueText)
        
        // Baseline value display
        baselineValueText = TextView(requireContext()).apply {
            text = "Baseline: Not Set"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        parent.addView(baselineValueText)
        
        // Baseline set time display
        baselineTimestampText = TextView(requireContext()).apply {
            text = "Set Time: --"
            textSize = 12f
            setPadding(0, 0, 0, 16)
            setTextColor(0xFF666666.toInt())
        }
        parent.addView(baselineTimestampText)
        
        // Button layout
        val buttonLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        // Set baseline button
        setBaselineButton = Button(requireContext()).apply {
            text = "Set Current as Baseline"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 16
            }
        }
        buttonLayout.addView(setBaselineButton)
        
        // Reset baseline button
        resetBaselineButton = Button(requireContext()).apply {
            text = "Reset Baseline"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        buttonLayout.addView(resetBaselineButton)
        
        parent.addView(buttonLayout)
        addSeparator(parent)
    }
    
    private fun createAlarmSection(parent: LinearLayout) {
        // Alarm settings title
        val alarmTitle = TextView(requireContext()).apply {
            text = "Alarm Settings"
            textSize = 18f
            setPadding(0, 16, 0, 16)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        parent.addView(alarmTitle)
        
        // Alarm enable switch
        alarmEnabledSwitch = Switch(requireContext()).apply {
            text = "Enable Alarm Function"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        parent.addView(alarmEnabledSwitch)
        
        // Alarm threshold setting
        val thresholdLabel = TextView(requireContext()).apply {
            text = "Alarm Threshold (% change from baseline)"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        parent.addView(thresholdLabel)
        
        alarmThresholdText = TextView(requireContext()).apply {
            text = "10%"
            textSize = 16f
            setPadding(0, 0, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        parent.addView(alarmThresholdText)
        
        alarmThresholdSeekBar = SeekBar(requireContext()).apply {
            max = 300 // 0-300%
            progress = 10 // Default 10%
            setPadding(0, 0, 0, 16)
        }
        parent.addView(alarmThresholdSeekBar)
        
        // Test alarm button
        testAlarmButton = Button(requireContext()).apply {
            text = "Test Alarm Sound"
            setPadding(0, 16, 0, 0)
        }
        parent.addView(testAlarmButton)
    }
    
    private fun addSeparator(parent: LinearLayout) {
        val separator = View(requireContext()).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            )
            params.setMargins(0, 24, 0, 24)
            layoutParams = params
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
        parent.addView(separator)
    }
    
    private fun loadCurrentSettings() {
        val currentBaseline = baselineManager.getCurrentBaseline()
        
        if (currentBaseline != null && currentBaseline.isBaselineSet) {
            baselineValueText.text = "Baseline: ${String.format("%.2f", currentBaseline.baselineValue)} Ω"
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            baselineTimestampText.text = "Set Time: ${dateFormat.format(Date(currentBaseline.baselineTimestamp))}"
            
            alarmThresholdSeekBar.progress = currentBaseline.alarmThreshold.toInt()
            alarmThresholdText.text = "${currentBaseline.alarmThreshold.toInt()}%"
        } else {
            baselineValueText.text = "Baseline: Not Set"
            baselineTimestampText.text = "Set Time: --"
        }
        
        alarmEnabledSwitch.isChecked = alarmManager.isAlarmEnabled()
        updateAlarmUIEnabled(alarmEnabledSwitch.isChecked)
    }
    
    private fun setupListeners() {
        // Set baseline button
        setBaselineButton.setOnClickListener {
            val newBaseline = baselineManager.setBaseline(currentResistanceValue)
            loadCurrentSettings()
            Toast.makeText(context, "Baseline set successfully", Toast.LENGTH_SHORT).show()
        }
        
        // Reset baseline button
        resetBaselineButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Confirm Reset")
                .setMessage("Are you sure you want to reset the baseline?")
                .setPositiveButton("Reset") { _, _ ->
                    baselineManager.resetBaseline()
                    loadCurrentSettings()
                    Toast.makeText(context, "Baseline reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // 报警开关
        alarmEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateAlarmUIEnabled(isChecked)
        }
        
        // 阈值滑块
        alarmThresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val threshold = if (progress < 1) 1 else progress
                alarmThresholdText.text = "${threshold}%"
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Test alarm button
        testAlarmButton.setOnClickListener {
            alarmManager.testAlarm()
            Toast.makeText(context, "Test alarm triggered", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateAlarmUIEnabled(enabled: Boolean) {
        alarmThresholdSeekBar.isEnabled = enabled
        testAlarmButton.isEnabled = enabled
    }
    
    private fun saveSettings() {
        // 保存报警阈值
        val threshold = if (alarmThresholdSeekBar.progress < 1) 1f else alarmThresholdSeekBar.progress.toFloat()
        baselineManager.setAlarmThreshold(threshold)
        
        // 保存报警开关状态
        alarmManager.setAlarmEnabled(alarmEnabledSwitch.isChecked)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        if (::alarmManager.isInitialized) {
            alarmManager.release()
        }
    }
}