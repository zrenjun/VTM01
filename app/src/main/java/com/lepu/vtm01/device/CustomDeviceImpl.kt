package com.lepu.vtm01.device

import com.lepu.vtm01.hardware.UsbHelper
import com.lepu.vtm01.type.Empty
import com.lepu.vtm01.type.Result
import com.lepu.vtm01.type.Error

class CustomDeviceImpl(private val usbHelper: UsbHelper) : CustomDevice {

    companion object {
        const val VENDOR_ID = 0x1915
        const val PRODUCT_ID = 0xF33F
    }

    override fun connect(): Result<Error, Empty> =
        usbHelper.enumerate(VENDOR_ID, PRODUCT_ID)

    override fun disconnect() {
        usbHelper.close()
    }

    override fun isConnected(): Result<Error, Empty> = usbHelper.isConnected()

    override fun setCmd(byteArray: ByteArray) = usbHelper.write(byteArray)

    override fun receive(): Result<Error, ByteArray> = usbHelper.read()
}