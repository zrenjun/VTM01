package com.lepu.vtm01

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lepu.vtm01.type.LepuBleResponse.toUInt
import com.lepu.vtm01.type.LepuBleResponse.bytesToHex
import com.lepu.vtm01.device.CustomDeviceImpl
import com.lepu.vtm01.hardware.UsbHelperImpl
import com.lepu.vtm01.type.Empty
import com.lepu.vtm01.type.Error
import com.lepu.vtm01.type.LepuBleResponse
import com.lepu.vtm01.type.LepuDevice
import com.lepu.vtm01.type.Config
import com.lepu.vtm01.type.ResponseData
import com.lepu.vtm01.util.BleCRC
import com.lepu.vtm01.util.CmdUtil
import com.lepu.vtm01.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Vector
import kotlin.experimental.inv

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val customDevice = CustomDeviceImpl(UsbHelperImpl(application.applicationContext))
    val usbOperationError = MutableLiveData<Error>()
    val usbOperationSuccess = MutableLiveData<Empty>()
    val usbOperationRead = MutableLiveData<ResponseData>()
    private var pool: ByteArray? = null

    fun setCmd(byteArray: ByteArray) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val temp = byteArrayOf((byteArray.size+1).toByte()).plus(byteArray).getCRC()
                val data = ByteArray(64)
                System.arraycopy(temp, 0, data, 0, temp.size)
                LogUtil.e("setCmd : ${bytesToHex(data)}")
                customDevice.setCmd(data).handle(::handleError, ::handleCmd)
            }
        }
    }

    fun connect() {
        try {
            if (customDevice.isConnected().isSuccess){
                customDevice.disconnect()
            } else {
                customDevice.connect().handle(::handleError, ::handleConnect)
            }
        } catch (e: Exception) {
            LogUtil.e(e.message?:"")
            e.printStackTrace()
        }
    }

    private fun handleError(error: Error) {
        usbOperationError.postValue(error)
    }


    private fun handleCmd(success: Empty) {
        usbOperationSuccess.postValue(success)
        mReceiveBuffer.clear()
    }

    private var ticker: ReceiveChannel<Unit>? = null
    init {
        ticker = ticker( 10L, 0)
    }


    private fun handleConnect(success: Empty) {
        usbOperationSuccess.postValue(success)
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                for (event in ticker!!) {
                    customDevice.receive().handle(::handleError, ::handleRead)
                }
            }
        }
    }

    private var mReceiveBuffer = Vector<Byte>()

    @OptIn(ExperimentalStdlibApi::class)
    private fun handleRead(byteArray: ByteArray) {
//        if (byteArray[0] != 0x00.toByte()) {
//            LogUtil.e(byteArray.toHexString())
//            val data = byteArray.copyOfRange(1, byteArray[0].toInt() + 1)
//            data.forEach { mReceiveBuffer.add(it) }
//            var flag = mReceiveBuffer.size > 1
//            while (flag) {
//                if (mReceiveBuffer[0] == 0xa5.toByte() && (mReceiveBuffer[1] == 0xE1.toByte() || mReceiveBuffer[1] == 0x02.toByte())) {
//                    if (mReceiveBuffer.size >= 12) {
//                        usbOperationRead.postValue(mReceiveBuffer.toByteArray().copyOfRange(0, 12))
//                        mReceiveBuffer.clear()
//                        return
//                    }
//                    flag = false
//                } else {
//                    mReceiveBuffer.removeAt(0)
//                    flag = mReceiveBuffer.size >= 12
//                }
//            }
//        }
        LogUtil.e("handleRead : ${bytesToHex(byteArray)}")
        if (byteArray[0] != 0x00.toByte()) {
            val size = toUInt(byteArray.copyOfRange(0, 1))
            val data = byteArray.copyOfRange(1, size+1)
            LogUtil.e("Useful data : size : $size, ${bytesToHex(data)}")
            pool = add(pool, data)
            pool?.apply {
                pool = hasResponse(pool)
            }
        }
    }

    fun hasResponse(bytes: ByteArray?): ByteArray? {
        val bytesLeft: ByteArray? = bytes

        if (bytes == null || bytes.size < 8) {
            return bytes
        }

        loop@ for (i in 0 until bytes.size-7) {
            if (bytes[i] != 0xA5.toByte() || bytes[i+1] != bytes[i+2].inv()) {
                continue@loop
            }

            // need content length
            val len = toUInt(bytes.copyOfRange(i+5, i+7))
//            LepuBleLog.d(TAG, "want bytes length: $len")
            if (i+8+len > bytes.size) {
                continue@loop
            }

            val temp: ByteArray = bytes.copyOfRange(i, i+8+len)
            if (temp.size < 7) {
                continue@loop
            }
            if (temp.last() == BleCRC.calCRC8(temp)) {
                val bleResponse = LepuBleResponse.BleResponse(temp)
//                LepuBleLog.d(TAG, "get response: ${temp.toHex()}" )
                onResponseReceived(bleResponse)

                val tempBytes: ByteArray? = if (i+8+len == bytes.size) null else bytes.copyOfRange(i+8+len, bytes.size)

                return hasResponse(tempBytes)
            }
        }

        return bytesLeft
    }

    var fileSize = 0
    var offset = 0
    var fileContent = ByteArray(0)
    private fun onResponseReceived(response: LepuBleResponse.BleResponse) {
        LogUtil.e("onResponseReceived", "onResponseReceived bytes: ${bytesToHex(response.bytes)}, bytes.size: ${response.bytes.size}")
        when (response.cmd) {
            CmdUtil.ECHO -> {
                usbOperationRead.postValue(ResponseData(response.cmd, response.bytes))
                LogUtil.e("ECHO", "response : ${response.content.size}, ${bytesToHex(response.content)}")
            }
            CmdUtil.GET_INFO -> {
                val data = LepuDevice(response.content)
                usbOperationRead.postValue(ResponseData(response.cmd, data))
                LogUtil.e("GET_INFO", "response : ${response.content.size}, ${bytesToHex(response.content)}, $data")
            }
            CmdUtil.GET_CONFIG -> {
                val data = Config(response.content)
                usbOperationRead.postValue(ResponseData(response.cmd, data))
                LogUtil.e("GET_CONFIG", "response.content : ${response.content.size}, ${bytesToHex(response.content)}, $data")
            }
            CmdUtil.SET_CONFIG -> {
                usbOperationRead.postValue(ResponseData(response.cmd, response.bytes))
                LogUtil.e("SET_CONFIG", "response.content : ${response.content.size}, ${bytesToHex(response.content)}")
            }
            CmdUtil.RT_DATA -> {
                usbOperationRead.postValue(ResponseData(response.cmd, response.bytes))
                LogUtil.e("SET_CONFIG", "response.content : ${response.content.size}, ${bytesToHex(response.content)}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        customDevice.disconnect()
        ticker?.cancel()
    }
    fun add(ori: ByteArray?, add: ByteArray): ByteArray {
        if (ori == null) {
            return add
        }

        val new = ByteArray(ori.size + add.size)
        for ((index, value) in ori.withIndex()) {
            new[index] = value
        }

        for ((index, value) in add.withIndex()) {
            new[index + ori.size] = value
        }

        return new
    }
}
