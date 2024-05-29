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
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
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
        if (customDevice.isConnected().isSuccess)
            customDevice.disconnect()
        else {
            customDevice.connect().handle(::handleError, ::handleConnect)
        }
    }

    private fun handleError(error: Error) {
        usbOperationError.postValue(error)
    }

    private fun handleCmd(success: Empty) {
        usbOperationSuccess.postValue(success)
    }


    private var ticker: ReceiveChannel<Unit>? = null

    init {
        ticker = ticker(100L, 0)
    }

    private fun handleConnect(success: Empty) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (event in ticker!!) {
                    customDevice.receive().handle(::handleError, ::handleRead)
                }
            }
        }
        usbOperationSuccess.postValue(success)
    }

    private var mReceiveBuffer = Vector<Byte>()

    @OptIn(ExperimentalStdlibApi::class)
    private fun handleRead(byteArray: ByteArray) {
        if (byteArray[0] != 0x00.toByte()) {
            LogUtil.e(byteArray.copyOfRange(0,13).toHexString())
            byteArray.copyOfRange(1, byteArray[0].toInt() + 1).forEach {
                mReceiveBuffer.add(it)
            }
            if (mReceiveBuffer[0] == 0xa5.toByte()) {
                if (mReceiveBuffer.size >= 12){
                    usbOperationRead.postValue(mReceiveBuffer.toByteArray().copyOfRange(0, 12))
                    mReceiveBuffer.clear()
                }else{
                    LogUtil.e(mReceiveBuffer.toByteArray().toHexString())
                }
            }else{
                mReceiveBuffer.clear()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        customDevice.disconnect()
        ticker?.cancel()
    }
}
