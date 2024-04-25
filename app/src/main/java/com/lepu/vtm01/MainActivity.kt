package com.lepu.vtm01

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import com.lepu.vtm01.type.Error

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private var connected = false
    private lateinit var viewModel: MainViewModel

    @OptIn(ExperimentalStdlibApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogUtil.init(this)
        viewModel = ViewModelProviders.of(this)[MainViewModel::class.java]
        viewModel.usbOperationError.observe(this) {
            when (it) {
                is Error.NoDeviceFoundError -> showMessage(getString(R.string.error_no_device))
                is Error.UsbConnectionError -> showMessage(getString(R.string.error_no_connection))
                is Error.ClaimInterfaceError -> showMessage(getString(R.string.error_claim_interface))
                is Error.ReadError -> showMessage(getString(R.string.error_read_report))
            }
            connected = false
            findViewById<TextView>(R.id.tv0).text = getString(R.string.disconnected)
        }

        viewModel.usbOperationSuccess.observe(this) {
            connected = true
            findViewById<TextView>(R.id.tv0).text = getString(R.string.connected)
        }
        viewModel.usbOperationRead.observe(this) {
            LogUtil.e(it.toHexString())
            findViewById<EditText>(R.id.et).setText(it.toHexString())
        }

        findViewById<TextView>(R.id.tv0).setOnClickListener {
            viewModel.connect()
        }
        findViewById<TextView>(R.id.tv1).setOnClickListener {
            if (connected) {
                viewModel.setCmd(byteArrayOf(
                    0xA5.toByte(),
                    0xE1.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte()
                ).getCRC())
            }
        }
    }


    private fun showMessage(message: String){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}


fun ByteArray.getCRC(): ByteArray {
    var crc = 0xFF
    this.forEach { b ->
        crc = crc xor (b.toInt() and 0xFF)
        for (i in 0 until 8) {
            crc = if (crc and 0x80 != 0) {
                crc shr 1 xor 0x07
            } else {
                crc shr 1
            }
        }
    }

    return this + (crc and 0xff).toByte()
}

fun ByteArray.checkCRC(): Boolean {
    return this.sliceArray(IntRange(0, this.size - 2)).getCRC().contentEquals(this)
}