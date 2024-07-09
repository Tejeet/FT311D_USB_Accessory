package com.tejeet.ft311gpio.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import com.tejeet.ft311gpio.R
import com.tejeet.ft311gpio.databinding.ActivityHomeBinding
import com.tejeet.ft311gpio.utils.accessories.AccessoryInterface
import com.tejeet.ft311gpio.utils.accessories.FT311GPIOInterface

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "mytag"
        private val GPIO_KEYS = listOf("6", "5", "4", "3", "2", "1", "0")
    }

    private lateinit var binding: ActivityHomeBinding

    private val gpioDirectionMap: LinkedHashMap<String, String> = linkedMapOf()
    private val gpioStateMap: LinkedHashMap<String, String> = linkedMapOf()
    private var mReadPortText: String? = null

    private val mHandler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == FT311GPIOInterface.MSG_WHAT_GPIO_DATA && mReadPortText != null) {
            mReadPortText = (
                    String.format(
                        "%7s",
                        Integer.toBinaryString((msg.obj as Byte).toInt())
                    ).replace(' ', '0'))
        } else if (msg.what < AccessoryInterface.MSG_WHAT_ACCESSORY_ROW_DATA) {
            getString(R.string.device_connection_disconnected).also {
                binding.tvConnectionStatus.text = it
            }
            Log.d(TAG, "Disconnected USB")
            binding.llConnectionView.background =
                getDrawable(this@HomeActivity, R.drawable.design_disconnected)
        } else {
            Log.d(TAG, "Other MSG ${msg.what}")
        }
        true
    }
    private val mFT311GPIOInterface: FT311GPIOInterface = FT311GPIOInterface(mHandler)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initIOStatus()
        initUI()
    }

    override fun onResume() {
        super.onResume()
        mFT311GPIOInterface.create(application)
        mFT311GPIOInterface.resetPort()
    }

    override fun onPause() {
        super.onPause()
        mFT311GPIOInterface.resetPort()
        mFT311GPIOInterface.destroy(application)
    }

    private fun initIOStatus() {
        GPIO_KEYS.forEach { key ->
            gpioDirectionMap[key] = "0"
            gpioStateMap[key] = "0"
        }
    }

    private fun toggleAllIOs() {
        mFT311GPIOInterface.configPort("1111111".toByte(2))
        Handler(Looper.getMainLooper()).postDelayed({ mFT311GPIOInterface.writePort("1111111".toByte(2)) }, 50)
    }

    private fun initUI() {
        val gpioControls = listOf(
            binding.gpio1Control,
            binding.gpio2Control,
            binding.gpio3Control,
            binding.gpio4Control,
            binding.gpio5Control,
            binding.gpio6Control,
            binding.gpio7Control
        )

        gpioControls.forEachIndexed { index, button ->
            button.setOnClickListener {
                toggleButtonState(index)
                writeIOState()
            }
        }

        val gpioDirectionViews = listOf(
            binding.gpio1State,
            binding.gpio2State,
            binding.gpio3State,
            binding.gpio4State,
            binding.gpio5State,
            binding.gpio6State,
            binding.gpio7State
        )

        gpioDirectionViews.forEachIndexed { index, switch ->
            val key = GPIO_KEYS[index]
            switch.setOnCheckedChangeListener { _, isChecked ->
                gpioDirectionMap[key] = if (isChecked) "1" else "0"
                configureIOState()
            }
        }

        binding.btnRead.setOnClickListener {
            Log.d(TAG, "Direction Status: ${getDirectionStatusAll()}")
        }

        binding.btnSend.setOnClickListener {
            Log.d(TAG, "Writing to GPIO : ${getLevelStatusAll()}")
            writeIOState()
        }
    }

    private fun toggleButtonState(index: Int) {
        val key = GPIO_KEYS[index]
        val isStateZero = gpioStateMap[key] == "0"
        val stateText = if (isStateZero) getText(R.string.output) else getText(R.string.input)
        val stateBackground = if (isStateZero)
            R.drawable.design_output_state else R.drawable.design_input_state

        gpioStateMap[key] = if (isStateZero) "1" else "0"
        with(binding) {
            when (index) {
                0 -> gpio1Control
                1 -> gpio2Control
                2 -> gpio3Control
                3 -> gpio4Control
                4 -> gpio5Control
                5 -> gpio6Control
                6 -> gpio7Control
                else -> null
            }?.apply {
                text = stateText
                background = getDrawable(this@HomeActivity, stateBackground)
            }
        }
    }

    private fun configureIOState() {
        mFT311GPIOInterface.configPort(getDirectionStatusAll().toByte(2))
    }

    private fun writeIOState() {
        mFT311GPIOInterface.writePort(getLevelStatusAll().toByte(2))
    }

    private fun getDirectionStatusAll(): String {
        return GPIO_KEYS.joinToString("") { gpioDirectionMap[it] ?: "0" }
    }

    private fun getLevelStatusAll(): String {
        return GPIO_KEYS.joinToString("") { gpioStateMap[it] ?: "0" }
    }
}
