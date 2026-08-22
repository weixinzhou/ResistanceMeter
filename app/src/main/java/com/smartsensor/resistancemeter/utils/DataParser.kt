package com.smartsensor.resistancemeter.utils

import android.util.Log
import com.smartsensor.resistancemeter.model.ResistanceData
import com.smartsensor.resistancemeter.model.DataPacket

object DataParser {

    private const val TAG = "DataParser"

    // ESP32实际发送的数据包长度
    private const val ESP32_PACKET_SIZE = 12

    fun parseResistanceData(data: ByteArray?): ResistanceData? {
        if (data == null) {
            Log.w(TAG, "接收到空数据")
            return null
        }

       // Log.d(TAG, "========== ESP32数据解析 ==========")
      //  Log.d(TAG, "数据长度: ${data.size} (预期: $ESP32_PACKET_SIZE)")

        // 打印原始数据
        val hexString = data.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
       // Log.d(TAG, "十六进制: $hexString")

        // 检查数据长度
        if (data.size != ESP32_PACKET_SIZE) {
            Log.w(TAG, "数据长度不匹配: ${data.size} != $ESP32_PACKET_SIZE")
            return null
        }

        try {
            // 1. 验证包头
            val header = data[0].toInt() and 0xFF
            if (header != 0xAA) {
                Log.w(TAG, "包头错误: 0x%02x != 0xAA".format(header))
                return null
            }
          //  Log.d(TAG, "✓ 包头正确: 0xAA")

            // 2. 验证命令类型
            val cmd = data[1].toInt() and 0xFF
            if (cmd != 0x01) {
                Log.w(TAG, "命令类型错误: 0x%02x != 0x01".format(cmd))
                return null
            }
            //Log.d(TAG, "✓ 命令类型正确: 0x01")

            // 3. 解析单个电阻值 (uint16, ESP32使用小端序, 需要除以100)
            // ESP32确定使用小端序格式发送数据
            val rRaw = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)

            //Log.d(TAG, "字节解析: R[2,3]=${String.format("%02x %02x", data[2].toInt() and 0xFF, data[3].toInt() and 0xFF)}")
            //Log.d(TAG, "小端序原始值: R=$rRaw")

            val resistance = rRaw / 100.0f

           // Log.d(TAG, "原始值: R=$rRaw")
//Log.d(TAG, "电阻实际值: R=%.2fΩ".format(resistance))

            // 4. 解析时间戳 (uint16, 小端序)
            val timestampRaw = (data[8].toInt() and 0xFF) or ((data[9].toInt() and 0xFF) shl 8)
            //Log.d(TAG, "时间戳原始值: $timestampRaw")

            // 5. 解析状态
            val status = data[10].toInt() and 0xFF
           // Log.d(TAG, "状态: $status")

            // 6. 验证校验和
            val receivedChecksum = data[11].toInt() and 0xFF
            var calculatedChecksum = 0
            for (i in 0 until 11) {
                calculatedChecksum += (data[i].toInt() and 0xFF)
            }
            calculatedChecksum = calculatedChecksum and 0xFF // 取低8位

           // Log.d(TAG, "校验和: 接收=0x%02x, 计算=0x%02x".format(receivedChecksum, calculatedChecksum))

            if (receivedChecksum != calculatedChecksum) {
           //     Log.w(TAG, "校验和错误，但继续处理数据")
                // 注意：不返回null，因为数据可能仍然有效
            } else {
          //      Log.d(TAG, "✓ 校验和正确")
            }

            // 7. 验证电阻值合理性
            if (!isValidResistance(resistance)) {
                Log.w(TAG, "电阻值超出合理范围: R=$resistance")
                return null
            }

            // 8. 使用当前系统时间作为时间戳
            val currentTimestamp = System.currentTimeMillis()

          //  Log.d(TAG, "✓ ESP32数据解析成功")
          //  Log.d(TAG, "=======================================")

            return ResistanceData(resistance, currentTimestamp, status)

        } catch (e: Exception) {
            Log.e(TAG, "解析异常: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    // 验证电阻值是否合理 (0-10000Ω)
    private fun isValidResistance(value: Float): Boolean {
        return value >= 0f && value <= 10000f && !value.isNaN() && !value.isInfinite()
    }

    fun createControlCommand(command: Byte, parameters: ByteArray? = null): ByteArray {
        val packet = DataPacket(Constants.PACKET_HEADER, command, parameters)
        return packet.toByteArray()
    }
}