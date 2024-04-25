package com.lepu.vtm01.device

import com.lepu.vtm01.hardware.UsbHelper
import com.lepu.vtm01.type.Empty
import com.lepu.vtm01.type.Result
import com.lepu.vtm01.type.Error
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers

class CustomDeviceImpl(private val usbHelper: UsbHelper) : CustomDevice {

    companion object {
        const val VENDOR_ID = 0x1915
        const val PRODUCT_ID = 0xF34E
        const val CUSTOM_HID_INTERFACE = 0x00
        const val READ_SIZE = 64
    }

    override fun connect(): Result<Error, Empty> =
        usbHelper.enumerate(VENDOR_ID, PRODUCT_ID, CUSTOM_HID_INTERFACE)

    override fun disconnect() {
        usbHelper.close()
    }

    override fun isConnected(): Result<Error, Empty> = usbHelper.isConnected()

    override fun setCmd(byteArray: ByteArray) = usbHelper.write(byteArray)

    override fun receive(): Observable<Result<Error, ByteArray>> {
        return Observable.fromCallable { usbHelper.read(READ_SIZE) }.subscribeOn(Schedulers.io())
    }
}