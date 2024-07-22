package com.lepu.vtm01

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lepu.vtm01.device.CustomDeviceImpl
import com.lepu.vtm01.hardware.UsbHelperImpl
import com.lepu.vtm01.type.Empty
import com.lepu.vtm01.type.Error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Vector

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val customDevice = CustomDeviceImpl(UsbHelperImpl(application.applicationContext))
    val usbOperationError = MutableLiveData<Error>()
    val usbOperationSuccess = MutableLiveData<Empty>()
    val usbOperationRead = MutableLiveData<ByteArray>()

    fun setCmd(byteArray: ByteArray) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                customDevice.setCmd(byteArray).handle(::handleError, ::handleCmd)
            }
        }
    }

    fun connect() {
        try {
            if (customDevice.isConnected().isSuccess)
                customDevice.disconnect()
            else {
                customDevice.connect().handle(::handleError, ::handleConnect)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleError(error: Error) {
        usbOperationError.postValue(error)
    }


    private fun handleCmd(success: Empty) {
        usbOperationSuccess.postValue(success)
        mReceiveBuffer.clear()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                delay(10)
                customDevice.receive().handle(::handleError, ::handleRead)
            }
        }
    }

    private fun handleConnect(success: Empty) {
        usbOperationSuccess.postValue(success)
    }

    private var mReceiveBuffer = Vector<Byte>()

    @OptIn(ExperimentalStdlibApi::class)
    private fun handleRead(byteArray: ByteArray) {
        if (byteArray[0] != 0x00.toByte()) {
            LogUtil.e(byteArray.toHexString())
            val data = byteArray.copyOfRange(1, byteArray[0].toInt() + 1)
            data.forEach { mReceiveBuffer.add(it) }
            var flag = mReceiveBuffer.size > 1
            while (flag) {
                if (mReceiveBuffer[0] == 0xa5.toByte() && (mReceiveBuffer[1] == 0xE1.toByte() || mReceiveBuffer[1] == 0x02.toByte())) {
                    if (mReceiveBuffer.size >= 12) {
                        usbOperationRead.postValue(mReceiveBuffer.toByteArray().copyOfRange(0, 12))
                        mReceiveBuffer.clear()
                        return
                    }
                    flag = false
                } else {
                    mReceiveBuffer.removeAt(0)
                    flag = mReceiveBuffer.size >= 12
                }
            }
            if (!flag) {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        delay(10)
                        customDevice.receive().handle(::handleError, ::handleRead)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        customDevice.disconnect()
    }
}
