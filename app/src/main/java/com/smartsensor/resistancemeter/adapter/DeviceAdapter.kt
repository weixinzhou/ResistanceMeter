package com.smartsensor.resistancemeter.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartsensor.resistancemeter.R
import com.smartsensor.resistancemeter.databinding.ItemDeviceBinding
import com.smartsensor.resistancemeter.model.DeviceInfo
import com.smartsensor.resistancemeter.utils.DeviceFilter

class DeviceAdapter(
    private val onDeviceClick: (DeviceInfo) -> Unit
) : ListAdapter<DeviceInfo, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeviceClick(getItem(position))
                }
            }
        }

        fun bind(device: DeviceInfo) {
            android.util.Log.d("DeviceAdapter", "Binding device: ${device.name} (${device.address}) - connected: ${device.isConnected}")
            
            binding.apply {
                deviceName.text = DeviceFilter.getDisplayName(device.device)
                deviceAddress.text = device.address
                deviceRssi.text = "${device.rssi} dBm"
                deviceStatus.text = if (device.isConnected) "Connected" else "Not connected"

                android.util.Log.d("DeviceAdapter", "Setting status text to: ${deviceStatus.text}")

                // 根据连接状态改变背景色
                val backgroundColor = if (device.isConnected) {
                    ContextCompat.getColor(root.context, R.color.connected_background)
                } else {
                    ContextCompat.getColor(root.context, R.color.disconnected_background)
                }
                root.setBackgroundColor(backgroundColor)
            }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<DeviceInfo>() {
        override fun areItemsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem == newItem && oldItem.rssi == newItem.rssi && oldItem.isConnected == newItem.isConnected
        }
    }
}