package com.smartsensor.resistancemeter.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.smartsensor.resistancemeter.MainActivity
import com.smartsensor.resistancemeter.databinding.FragmentControlBinding
import com.smartsensor.resistancemeter.service.BluetoothService
import com.smartsensor.resistancemeter.viewmodel.DataViewModel

class ControlFragment : Fragment() {

    private var _binding: FragmentControlBinding? = null
    private val binding get() = _binding!!

    private var bluetoothService: BluetoothService? = null
    private val dataViewModel: DataViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupObservers()
        setupListeners()
        
        // 获取蓝牙服务引用
        bluetoothService = (requireActivity() as MainActivity).getBluetoothService()
    }


    private fun setupObservers() {
        // 观察数据统计
        dataViewModel.dataStatistics.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                // 更新统计显示（如果需要）
            }
        }
    }

    private fun setupListeners() {
        // Control fragment specific listeners can be added here
        // Currently no specific controls needed as baseline functionality moved to ChartFragment
    }

    fun setBluetoothService(service: BluetoothService?) {
        bluetoothService = service
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}