package com.lepu.vtm01.hardware

import android.content.Context
import android.hardware.usb.*
import com.lepu.vtm01.LogUtil
import com.lepu.vtm01.type.Empty
import com.lepu.vtm01.type.Result
import com.lepu.vtm01.type.Error

class UsbHelperImpl(context: Context) : UsbHelper {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private lateinit var usbInterface: UsbInterface
    private lateinit var usbDevice: UsbDevice
    private var usbConnection: UsbDeviceConnection? = null

    override fun enumerate(vid: Int, pid: Int): Result<Error, Empty> {
        usbDevice = findDevice(vid, pid) ?: return Result.Failure(Error.NoDeviceFoundError)
        usbInterface = usbDevice.getInterface(0)
        return open()
    }

    override fun open(): Result<Error, Empty> {
        usbConnection = usbManager.openDevice(usbDevice)
        val result = usbConnection?.claimInterface(usbInterface, true)
        LogUtil.e(result.toString())
        return if (result == null || result == false) Result.Failure(Error.ClaimInterfaceError) else Result.Success(
            Empty()
        )
    }

    override fun close() {
        usbConnection?.let {
            it.releaseInterface(usbInterface)
            it.close()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun write(report: ByteArray): Result<Error, Empty> {
        LogUtil.e(report.toHexString())
        //如果是HID的设备类请求，只有10100001(0xA1)和00100001(0x21)两种
        val result =
            usbConnection?.controlTransfer(0x21, 0x09, 0x0200, 0, report, 64, 3000)
        LogUtil.e(result.toString())
        if (result == null || result < 0)
            return Result.Failure(Error.UsbConnectionError)
        return Result.Success(Empty())
    }

    override fun read(): Result<Error, ByteArray> {
        val report = ByteArray(64)
        usbConnection?.let {
            val result = it.controlTransfer(0xA1, 0x01, 0x0102, 0, report, 64, 3000)
            if (result > 0){
                LogUtil.e(result.toString())
            }
        } ?: return Result.Failure(Error.UsbConnectionError)
        return Result.Success(report)
    }

    override fun isConnected(): Result<Error, Empty> {
        usbConnection ?: return Result.Failure(Error.UsbConnectionError)
        return Result.Success(Empty())
    }

    private fun findDevice(vid: Int, pid: Int): UsbDevice? {
        usbManager.deviceList.values.forEach { device ->
            if ((device.vendorId == vid) and (device.productId == pid)) {
                return device
            }
        }
        return null
    }
}