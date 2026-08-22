package com.smartsensor.resistancemeter.model

data class DataBatch(
    val startTime: Long,
    var endTime: Long,
    val dataPoints: MutableList<ResistanceData>,
    val alarmEvents: MutableList<AlarmRecord>
) {
    @Synchronized
    fun addDataPoint(data: ResistanceData) {
        dataPoints.add(data)
        endTime = data.timestamp
    }
    
    @Synchronized
    fun addAlarmEvent(alarm: AlarmRecord) {
        alarmEvents.add(alarm)
        endTime = alarm.timestamp
    }
    
    @Synchronized
    fun isEmpty(): Boolean {
        return dataPoints.isEmpty() && alarmEvents.isEmpty()
    }
    
    @Synchronized
    fun getDataCount(): Int {
        return dataPoints.size
    }
    
    @Synchronized
    fun getDurationMs(): Long {
        return endTime - startTime
    }
}