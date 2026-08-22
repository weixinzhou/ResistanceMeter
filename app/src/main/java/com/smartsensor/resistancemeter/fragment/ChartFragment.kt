package com.smartsensor.resistancemeter.fragment

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.smartsensor.resistancemeter.MainActivity
import com.smartsensor.resistancemeter.databinding.FragmentChartBinding
import com.smartsensor.resistancemeter.model.ResistanceData
import com.smartsensor.resistancemeter.service.BluetoothService
import com.smartsensor.resistancemeter.utils.Constants
import com.smartsensor.resistancemeter.utils.BaselineManager
import com.smartsensor.resistancemeter.utils.AlarmManager
import com.smartsensor.resistancemeter.ui.BaselineSettingsDialog
import com.smartsensor.resistancemeter.viewmodel.DataViewModel
import java.text.SimpleDateFormat
import java.util.*

class ChartFragment : Fragment() {

    companion object {
        private const val TAG = "ChartFragment"
    }

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

    private var bluetoothService: BluetoothService? = null
    private lateinit var baselineManager: BaselineManager
    private lateinit var alarmManager: AlarmManager
    private val dataViewModel: DataViewModel by activityViewModels()

    private val resistanceEntries = mutableListOf<Entry>()
    private var entryCount = 0
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        baselineManager = BaselineManager(requireContext())
        alarmManager = AlarmManager(requireContext())
        
        setupChart()
        setupDataObserver()
        setupBaselineControls()
        
        bluetoothService = (requireActivity() as MainActivity).getBluetoothService()
        setupBluetoothObserver()
        
        updateBaselineDisplay()
    }

    private fun setupChart() {
        Log.d(TAG, "设置图表")
        
        binding.chart.apply {
            description = Description().apply { text = "Real-time Resistance Chart" }
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            
            // 设置X轴
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < resistanceEntries.size) {
                            val timestamp = System.currentTimeMillis() - (resistanceEntries.size - index) * 1000L
                            timeFormat.format(Date(timestamp))
                        } else {
                            ""
                        }
                    }
                }
            }
            
            // 设置Y轴
            axisLeft.apply {
                setDrawGridLines(true)
                isEnabled = true
            }
            axisRight.isEnabled = false
            
            // 设置图例
            legend.isEnabled = true
            
            invalidate()
        }
    }

    private fun setupDataObserver() {
        // 观察数据变化
        dataViewModel.currentData.observe(viewLifecycleOwner) { data ->
            data?.let {
               // Log.d(TAG, "ChartFragment接收到电阻数据: R=${data.resistance}")
                
                // 检查是否需要触发报警
                val currentBaseline = baselineManager.getCurrentBaseline()
                val isAlarmTriggered = if (currentBaseline != null) {
                    alarmManager.checkAndTriggerAlarm(data, currentBaseline)
                } else {
                    false
                }
                updateData(data, isAlarmTriggered)
            }
        }
    }
    
    private fun setupBaselineControls() {
        // 基线设置按钮
        binding.baselineSettingsButton.setOnClickListener {
            val currentData = dataViewModel.currentData.value
            val currentResistance = currentData?.resistance ?: 0f
            
            val dialog = BaselineSettingsDialog.newInstance(currentResistance) { newBaseline ->
                updateBaselineDisplay()
            }
            dialog.show(parentFragmentManager, "BaselineSettingsDialog")
        }

        // 清除数据按钮
        binding.clearDataButton.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Confirm Clear")
                .setMessage("Are you sure you want to clear all data?")
                .setPositiveButton("Clear") { _, _ ->
                    dataViewModel.clearData()
                    alarmManager.clearAlarmHistory()
                    Toast.makeText(context, "Data cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun updateBaselineDisplay() {
        val baseline = baselineManager.getCurrentBaseline()
        if (baseline != null && baseline.isBaselineSet) {
            binding.baselineValueText.text = "Baseline: ${String.format("%.2f", baseline.baselineValue)} Ω"
        } else {
            binding.baselineValueText.text = "Baseline: Not Set"
        }
    }
    

    private fun setupBluetoothObserver() {
        bluetoothService?.let { service ->
            Log.d(TAG, "ChartFragment - BluetoothService不为null，设置LiveData观察者")

            service.connectionState.observe(viewLifecycleOwner) { connected ->
                Log.d(TAG, "连接状态变化: $connected")
                binding.status.text = if (connected) "已连接" else "未连接"
                if (!connected) {
                    clearChart()
                }
            }
        } ?: run {
            Log.e(TAG, "ChartFragment - BluetoothService仍然为null")
        }
    }

    private fun updateData(data: ResistanceData, isAlarmTriggered: Boolean = false) {
       // Log.d(TAG, "更新图表数据")

        // 更新数值显示
        binding.apply {
            try {
                // 电阻值显示
                resistanceValue.text = if (data.resistance > 10000) "--" else "%.2f Ω".format(data.resistance)
                timestamp.text = timeFormat.format(Date(data.timestamp))
                
                // 报警状态显示 (在电阻值显示区域)
                alarmStatus.text = if (isAlarmTriggered) "Alarm!" else "Normal"
                alarmStatus.setTextColor(if (isAlarmTriggered) Color.RED else Color.GREEN)
                
                // 报警状态显示 (在基线设置区域)
                alarmStatusText.text = if (isAlarmTriggered) "Status: ALARM!" else "Status: Normal"
                alarmStatusText.setTextColor(if (isAlarmTriggered) Color.RED else Color.GREEN)
                
                // 基线信息显示
                val baseline = baselineManager.getCurrentBaseline()
                if (baseline != null && baseline.isBaselineSet) {
                    baselineInfo.text = "%.2f Ω".format(baseline.baselineValue)
                } else {
                    baselineInfo.text = "Not Set"
                }
            } catch (e: Exception) {
                Log.w(TAG, "更新UI元素时出错: ${e.message}")
            }
        }

        // 添加数据到图表
        addDataToChart(data.resistance)
    }

    private fun addDataToChart(resistance: Float) {
        // 添加新数据点
        resistanceEntries.add(Entry(entryCount.toFloat(), resistance))
        entryCount++

        // 限制数据点数量
        if (resistanceEntries.size > Constants.CHART_MAX_ENTRIES) {
            resistanceEntries.removeAt(0)
            // 重新编号
            resistanceEntries.forEachIndexed { index, entry ->
                entry.x = index.toFloat()
            }
            entryCount = resistanceEntries.size
        }

        // 更新图表
        updateChart()
    }

    private fun updateChart() {
        val dataSets = mutableListOf<LineDataSet>()

        if (resistanceEntries.isNotEmpty()) {
            val resistanceDataSet = LineDataSet(resistanceEntries, "Resistance").apply {
                color = Color.BLUE
                setCircleColor(Color.BLUE)
                lineWidth = 2f
                circleRadius = 3f
                setDrawCircleHole(false)
                valueTextSize = 9f
                setDrawFilled(false)
            }
            dataSets.add(resistanceDataSet)
        }

        // 添加基线参考线（如果设置了基线）
        val baseline = baselineManager.getCurrentBaseline()
        if (baseline != null && baseline.isBaselineSet && resistanceEntries.isNotEmpty()) {
            val baselineEntries = mutableListOf<Entry>()
            for (i in 0 until resistanceEntries.size) {
                baselineEntries.add(Entry(i.toFloat(), baseline.baselineValue))
            }
            
            val baselineDataSet = LineDataSet(baselineEntries, "Baseline").apply {
                color = Color.GREEN
                lineWidth = 1f
                setDrawCircles(false)
                setDrawValues(false)
                enableDashedLine(10f, 5f, 0f)
            }
            dataSets.add(baselineDataSet)
        }

        val lineData = LineData(dataSets.map { it as com.github.mikephil.charting.interfaces.datasets.ILineDataSet })
        binding.chart.data = lineData
        binding.chart.notifyDataSetChanged()
        binding.chart.invalidate()

        // 自动滚动到最新数据
        binding.chart.moveViewToX(lineData.entryCount.toFloat())
    }

    private fun clearChart() {
        Log.d(TAG, "清除图表")
        resistanceEntries.clear()
        entryCount = 0
        binding.chart.clear()
        binding.apply {
            try {
                resistanceValue.text = "-- Ω"
                timestamp.text = "--:--:--"
                alarmStatus.text = "Disconnected"
                alarmStatus.setTextColor(Color.GRAY)
                alarmStatusText.text = "Status: Disconnected"
                alarmStatusText.setTextColor(Color.GRAY)
                baselineInfo.text = "--"
            } catch (e: Exception) {
                Log.w(TAG, "清除UI元素时出错: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::alarmManager.isInitialized) {
            alarmManager.release()
        }
        _binding = null
    }
}