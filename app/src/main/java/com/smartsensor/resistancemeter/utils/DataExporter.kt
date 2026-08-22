package com.smartsensor.resistancemeter.utils

import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.smartsensor.resistancemeter.model.ResistanceData
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class DataExporter {

    companion object {
        private const val FOLDER_NAME = "ResistanceMeter"
        private const val FILE_PREFIX = "resistance_data_"
        
        fun exportToCSV(context: Context, data: List<ResistanceData>): Boolean {
            return try {
                val file = createExportFile(context, "csv")
                val writer = FileWriter(file)
                
                // 写入CSV标题
                writer.append("Timestamp,Date,Time,Resistance(Ω),Status,AlarmTriggered\n")
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                
                // 写入数据
                data.forEach { resistanceData ->
                    val date = Date(resistanceData.timestamp)
                    writer.append("${resistanceData.timestamp},")
                    writer.append("${dateFormat.format(date)},")
                    writer.append("${timeFormat.format(date)},")
                    writer.append("${resistanceData.resistance},")
                    writer.append("${resistanceData.status},")
                    writer.append("${resistanceData.isAlarmTriggered}\n")
                }
                
                writer.close()
                
                Toast.makeText(context, "Data exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                true
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
        
        fun exportToJSON(context: Context, data: List<ResistanceData>): Boolean {
            return try {
                val file = createExportFile(context, "json")
                val writer = FileWriter(file)
                
                writer.append("{\n")
                writer.append("  \"exportInfo\": {\n")
                writer.append("    \"exportTime\": \"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\",\n")
                writer.append("    \"dataCount\": ${data.size},\n")
                writer.append("    \"deviceType\": \"ResistanceMeter\",\n")
                writer.append("    \"version\": \"1.0.0\"\n")
                writer.append("  },\n")
                writer.append("  \"data\": [\n")
                
                data.forEachIndexed { index, resistanceData ->
                    writer.append("    {\n")
                    writer.append("      \"timestamp\": ${resistanceData.timestamp},\n")
                    writer.append("      \"date\": \"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(resistanceData.timestamp))}\",\n")
                    writer.append("      \"resistance\": ${resistanceData.resistance},\n")
                    writer.append("      \"status\": ${resistanceData.status},\n")
                    writer.append("      \"isAlarmTriggered\": ${resistanceData.isAlarmTriggered}\n")
                    writer.append("    }")
                    if (index < data.size - 1) {
                        writer.append(",")
                    }
                    writer.append("\n")
                }
                
                writer.append("  ]\n")
                writer.append("}")
                
                writer.close()
                
                Toast.makeText(context, "Data exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                true
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
        
        fun exportToTXT(context: Context, data: List<ResistanceData>): Boolean {
            return try {
                val file = createExportFile(context, "txt")
                val writer = FileWriter(file)
                
                writer.append("Resistance Measurement Data Export Report\n")
                writer.append("${"=".repeat(50)}\n")
                writer.append("Export Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                writer.append("Data Points: ${data.size}\n")
                writer.append("Device Type: ResistanceMeter\n")
                writer.append("Software Version: 1.0.0\n")
                writer.append("${"=".repeat(50)}\n\n")
                
                writer.append("Data Format Description:\n")
                writer.append("Timestamp | Date Time | Resistance(Ω) | Status | AlarmTriggered\n")
                writer.append("${"-".repeat(70)}\n")
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                
                data.forEach { resistanceData ->
                    val dateTime = dateFormat.format(Date(resistanceData.timestamp))
                    writer.append("${resistanceData.timestamp} | $dateTime | ")
                    writer.append("${String.format("%.3f", resistanceData.resistance)} | ")
                    writer.append("${resistanceData.status} | ")
                    writer.append("${resistanceData.isAlarmTriggered}\n")
                }
                
                if (data.isNotEmpty()) {
                    writer.append("\nStatistics:\n")
                    writer.append("${"-".repeat(30)}\n")
                    
                    val resistanceValues = data.map { it.resistance }
                    val alarmCount = data.count { it.isAlarmTriggered }
                    
                    writer.append("Resistance Stats: Average=${String.format("%.3f", resistanceValues.average())}Ω, ")
                    writer.append("Min=${String.format("%.3f", resistanceValues.minOrNull() ?: 0f)}Ω, ")
                    writer.append("Max=${String.format("%.3f", resistanceValues.maxOrNull() ?: 0f)}Ω\n")
                    
                    writer.append("Alarm Events: $alarmCount out of ${data.size} (${String.format("%.1f", alarmCount * 100.0 / data.size)}%)\n")
                    
                    val timeSpan = data.last().timestamp - data.first().timestamp
                    val dataRate = if (timeSpan > 0) (data.size * 1000f) / timeSpan else 0f
                    writer.append("Sampling Rate: ${String.format("%.2f", dataRate)}Hz\n")
                    writer.append("Total Duration: ${String.format("%.2f", timeSpan / 1000f)}s\n")
                }
                
                writer.close()
                
                Toast.makeText(context, "Data exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                true
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
        
        private fun createExportFile(context: Context, extension: String): File {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "$FILE_PREFIX$timestamp.$extension"
            
            // Use Documents/ResistanceMeter directory
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val resistanceMeterDir = File(documentsDir, "ResistanceMeter")
            
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                if (!resistanceMeterDir.exists()) {
                    resistanceMeterDir.mkdirs()
                }
                return File(resistanceMeterDir, fileName)
            }
            
            // Fallback to internal storage if external is not available
            val internalDir = File(context.filesDir, "Exports")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
            
            return File(internalDir, fileName)
        }
        
        fun getExportFolder(context: Context): File {
            // Use Documents/ResistanceMeter directory
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val resistanceMeterDir = File(documentsDir, "ResistanceMeter")
            
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                return resistanceMeterDir
            }
            
            // Fallback to internal storage if external is not available
            return File(context.filesDir, "Exports")
        }
    }
}