package com.smartsensor.resistancemeter.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.smartsensor.resistancemeter.model.DataBatch
import com.smartsensor.resistancemeter.model.AlarmRecord
import com.smartsensor.resistancemeter.model.ResistanceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class AutoSaveFileManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AutoSaveFileManager"
    }
    
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    private fun getAutoSaveDirectory(): File {
        Log.d(TAG, "========== Getting AutoSave Directory ==========")
        
        // Use Documents/ResistanceMeter/AutoSave directory
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val resistanceMeterDir = File(documentsDir, "ResistanceMeter")
        val autoSaveDir = File(resistanceMeterDir, "AutoSave")
        Log.d(TAG, "Documents dir: $documentsDir")
        Log.d(TAG, "ResistanceMeter dir: $resistanceMeterDir")
        Log.d(TAG, "AutoSave dir: $autoSaveDir")
        
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            Log.d(TAG, "External storage mounted")
            if (!autoSaveDir.exists()) {
                Log.d(TAG, "AutoSave dir doesn't exist, creating...")
                val created = autoSaveDir.mkdirs()
                Log.d(TAG, "AutoSave dir creation result: $created")
            } else {
                Log.d(TAG, "AutoSave dir already exists")
            }
            
            Log.d(TAG, "AutoSave dir exists: ${autoSaveDir.exists()}")
            Log.d(TAG, "AutoSave dir readable: ${autoSaveDir.canRead()}")
            Log.d(TAG, "AutoSave dir writable: ${autoSaveDir.canWrite()}")
            Log.d(TAG, "AutoSave dir path: ${autoSaveDir.absolutePath}")
            
            return autoSaveDir
        }
        
        Log.w(TAG, "External storage not available, using internal storage")
        
        // Fallback to internal storage if external is not available
        val internalDir = File(context.filesDir, "AutoSave")
        Log.d(TAG, "Internal dir: $internalDir")
        
        if (!internalDir.exists()) {
            Log.d(TAG, "Internal dir doesn't exist, creating...")
            val created = internalDir.mkdirs()
            Log.d(TAG, "Internal dir creation result: $created")
        } else {
            Log.d(TAG, "Internal dir already exists")
        }
        
        Log.d(TAG, "Internal dir exists: ${internalDir.exists()}")
        Log.d(TAG, "Internal dir readable: ${internalDir.canRead()}")
        Log.d(TAG, "Internal dir writable: ${internalDir.canWrite()}")
        Log.d(TAG, "Internal dir path: ${internalDir.absolutePath}")
        
        return internalDir
    }
    
    private fun generateFileName(startTime: Long, endTime: Long): String {
        val startTimeStr = dateFormat.format(Date(startTime))
        val endTimeStr = dateFormat.format(Date(endTime))
        return "resistance_data_${startTimeStr}_to_${endTimeStr}.csv"
    }
    
    suspend fun saveDataBatch(dataBatch: DataBatch): Result<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "========== Starting AutoSave Process ==========")
        try {
            Log.d(TAG, "DataBatch info:")
            Log.d(TAG, "  - Start time: ${dataBatch.startTime}")
            Log.d(TAG, "  - End time: ${dataBatch.endTime}")
            Log.d(TAG, "  - Data points: ${dataBatch.dataPoints.size}")
            Log.d(TAG, "  - Alarm events: ${dataBatch.alarmEvents.size}")
            Log.d(TAG, "  - Is empty: ${dataBatch.isEmpty()}")
            
            if (dataBatch.isEmpty()) {
                Log.w(TAG, "Data batch is empty, skipping save")
                return@withContext Result.failure(Exception("Data batch is empty, skipping save"))
            }
            
            val directory = getAutoSaveDirectory()
            Log.d(TAG, "Got directory: ${directory.absolutePath}")
            
            // Validate directory is writable
            Log.d(TAG, "Validating directory permissions...")
            Log.d(TAG, "Directory exists: ${directory.exists()}")
            Log.d(TAG, "Directory canWrite: ${directory.canWrite()}")
            
            if (!directory.exists() || !directory.canWrite()) {
                Log.e(TAG, "Save directory is not writable: ${directory.absolutePath}")
                return@withContext Result.failure(Exception("Save directory is not writable: ${directory.absolutePath}"))
            }
            
            val fileName = generateFileName(dataBatch.startTime, dataBatch.endTime)
            Log.d(TAG, "Generated filename: $fileName")
            
            val file = File(directory, fileName)
            Log.d(TAG, "Full file path: ${file.absolutePath}")
            Log.d(TAG, "File parent exists: ${file.parentFile?.exists()}")
            
            Log.d(TAG, "Creating FileWriter and writing data...")
            
            FileWriter(file).use { writer ->
                Log.d(TAG, "FileWriter created successfully")
                
                // 写入CSV头部
                Log.d(TAG, "Writing CSV header...")
                writer.write("Timestamp,Type,Value,Unit,AlarmTriggered,BaselineValue,ThresholdPercentage\n")
                
                // 合并并排序所有数据点
                Log.d(TAG, "Preparing data for writing...")
                val allEvents = mutableListOf<Pair<Long, String>>()
                
                // 创建线程安全的副本以避免 ConcurrentModificationException
                val dataPointsCopy = synchronized(dataBatch) {
                    dataBatch.dataPoints.toList()
                }
                val alarmEventsCopy = synchronized(dataBatch) {
                    dataBatch.alarmEvents.toList()
                }
                
                // 添加阻值数据
                Log.d(TAG, "Adding ${dataPointsCopy.size} resistance data points...")
                dataPointsCopy.forEach { data ->
                    allEvents.add(data.timestamp to formatResistanceDataRow(data))
                }
                
                // 添加报警事件
                Log.d(TAG, "Adding ${alarmEventsCopy.size} alarm events...")
                alarmEventsCopy.forEach { alarm ->
                    allEvents.add(alarm.timestamp to formatAlarmDataRow(alarm))
                }
                
                Log.d(TAG, "Total events to write: ${allEvents.size}")
                
                // 按时间戳排序并写入
                Log.d(TAG, "Sorting and writing events...")
                allEvents.sortedBy { it.first }.forEach { (_, row) ->
                    writer.write(row)
                }
                
                Log.d(TAG, "Data writing completed")
            }
            
            Log.d(TAG, "File written successfully")
            Log.d(TAG, "File size: ${file.length()} bytes")
            Log.d(TAG, "File exists after write: ${file.exists()}")
            Log.d(TAG, "File readable: ${file.canRead()}")
            
            Log.d(TAG, "✓ AutoSave completed successfully: ${file.absolutePath}")
            Result.success(file.absolutePath)
        } catch (e: SecurityException) {
            Log.e(TAG, "✗ SecurityException during save", e)
            Log.e(TAG, "SecurityException details: ${e.message}")
            Log.e(TAG, "SecurityException cause: ${e.cause}")
            Result.failure(Exception("Storage permission denied: ${e.message}"))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "✗ IOException during save", e)
            Log.e(TAG, "IOException details: ${e.message}")
            Log.e(TAG, "IOException cause: ${e.cause}")
            Result.failure(Exception("File write error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "✗ Unexpected exception during save", e)
            Log.e(TAG, "Exception details: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Result.failure(Exception("Save failed: ${e.message}"))
        }
    }
    
    private fun formatResistanceDataRow(data: ResistanceData): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(data.timestamp))
        return "$timestamp,Resistance,${data.resistance},Ω,${data.isAlarmTriggered},,\n"
    }
    
    private fun formatAlarmDataRow(alarm: AlarmRecord): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(alarm.timestamp))
        return "$timestamp,Alarm,${alarm.resistanceValue},Ω,true,${alarm.baselineValue},${alarm.thresholdPercentage}\n"
    }
    
    fun getAutoSaveFiles(): List<File> {
        val directory = getAutoSaveDirectory()
        return directory.listFiles { file ->
            file.isFile && file.name.startsWith("resistance_data_") && file.name.endsWith(".csv")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    suspend fun deleteOldFiles(maxFiles: Int = 50): Int = withContext(Dispatchers.IO) {
        val files = getAutoSaveFiles()
        if (files.size <= maxFiles) return@withContext 0
        
        var deletedCount = 0
        files.drop(maxFiles).forEach { file ->
            if (file.delete()) {
                deletedCount++
            }
        }
        deletedCount
    }
    
    fun getStorageInfo(): StorageInfo {
        val directory = getAutoSaveDirectory()
        val files = getAutoSaveFiles()
        val totalSize = files.sumOf { it.length() }
        
        return StorageInfo(
            fileCount = files.size,
            totalSizeBytes = totalSize,
            directoryPath = directory.absolutePath
        )
    }
    
    data class StorageInfo(
        val fileCount: Int,
        val totalSizeBytes: Long,
        val directoryPath: String
    ) {
        fun getTotalSizeMB(): Double {
            return totalSizeBytes / (1024.0 * 1024.0)
        }
    }
}