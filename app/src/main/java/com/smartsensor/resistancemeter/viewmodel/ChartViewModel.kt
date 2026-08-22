package com.smartsensor.resistancemeter.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.smartsensor.resistancemeter.model.ResistanceData
import com.smartsensor.resistancemeter.utils.Constants
import kotlinx.coroutines.launch

class ChartViewModel : ViewModel() {

    private val _resistanceEntries = MutableLiveData<List<Entry>>(emptyList())
    val resistanceEntries: LiveData<List<Entry>> = _resistanceEntries

    private val resistanceList = mutableListOf<Entry>()
    private var entryCount = 0

    fun addDataPoint(data: ResistanceData) {
        viewModelScope.launch {
            val x = entryCount++.toFloat()

            // 只有电阻值在合理范围内才添加到图表数据中
            if (data.resistance <= 10000) {
                resistanceList.add(Entry(x, data.resistance))
            }

            // 限制数据点数量
            if (resistanceList.size > Constants.CHART_MAX_ENTRIES) {
                resistanceList.removeAt(0)
                // 重新计算X值
                resistanceList.forEachIndexed { index, entry -> entry.x = index.toFloat() }
            }

            _resistanceEntries.value = resistanceList.toList()
        }
    }

    fun clearData() {
        viewModelScope.launch {
            resistanceList.clear()
            entryCount = 0
            _resistanceEntries.value = emptyList()
        }
    }
}