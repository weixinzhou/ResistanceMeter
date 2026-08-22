package com.smartsensor.resistancemeter.model

class DataPacket(
    private val header: Byte,
    private val cmd: Byte,
    private val data: ByteArray?
) {
    private val length: Byte = (data?.size ?: 0).toByte()
    private val checksum: Byte = calculateChecksum()

    private fun calculateChecksum(): Byte {
        var sum: Byte = 0
        sum = (sum + header).toByte()
        sum = (sum + cmd).toByte()
        sum = (sum + length).toByte()
        data?.forEach { byte ->
            sum = (sum + byte).toByte()
        }
        return (sum.toInt().inv() + 1).toByte()
    }

    fun toByteArray(): ByteArray {
        val totalLength = 4 + (data?.size ?: 0)
        val packet = ByteArray(totalLength)
        packet[0] = header
        packet[1] = cmd
        packet[2] = length
        data?.copyInto(packet, 3)
        packet[totalLength - 1] = checksum
        return packet
    }

    // Getters
    fun getHeader() = header
    fun getCmd() = cmd
    fun getLength() = length
    fun getData() = data
    fun getChecksum() = checksum
}