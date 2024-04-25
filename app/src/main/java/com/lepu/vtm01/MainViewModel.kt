package com.lepu.vtm01

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lepu.vtm01.device.CustomDeviceImpl
import com.lepu.vtm01.hardware.UsbHelperImpl
import com.lepu.vtm01.type.Empty
import com.lepu.vtm01.type.Error
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val customDevice = CustomDeviceImpl(UsbHelperImpl(application.applicationContext))
    val usbOperationError = MutableLiveData<Error>()
    val usbOperationSuccess = MutableLiveData<Empty>()
    val usbOperationRead = MutableLiveData<ByteArray>()

    fun setCmd(byteArray: ByteArray) {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
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
        ticker = ticker( 100L, 0)
    }
    private fun handleConnect(success: Empty) {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                for (event in ticker!!) {
                    customDevice.receive().handle(::handleError, ::handleRead)
                }
            }
        }
        usbOperationSuccess.postValue(success)
    }

    private fun handleRead(byteArray: ByteArray) {
        if (byteArray[0] != 0x00.toByte()) {
            usbOperationRead.postValue(byteArray)
        }
    }

    override fun onCleared() {
        super.onCleared()
        customDevice.disconnect()
        ticker?.cancel()
    }
}
