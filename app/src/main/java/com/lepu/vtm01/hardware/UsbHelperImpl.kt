package com.lepu.vtm01.hardware

import android.content.Context
import android.hardware.usb.*
import com.lepu.vtm01.LogUtil
import com.lepu.vtm01.type.Empty
import com.lepu.vtm01.type.Result
import com.lepu.vtm01.type.Error
import java.nio.ByteBuffer

class UsbHelperImpl(context: Context) : UsbHelper {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private lateinit var usbInterface: UsbInterface
    private lateinit var inRequest: UsbRequest
    private lateinit var usbDevice: UsbDevice
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInEndpoint: UsbEndpoint? = null
    override fun enumerate(vid: Int, pid: Int): Result<Error, Empty> {
        usbDevice = findDevice(vid, pid) ?: return Result.Failure(Error.NoDeviceFoundError)
        usbInterface = usbDevice.getInterface(0)
        for (num in 0 until usbInterface.endpointCount) {
            if (usbInterface.getEndpoint(num).direction == UsbConstants.USB_DIR_IN)
                usbInEndpoint = usbInterface.getEndpoint(num)
        }
        return open()
    }

    override fun open(): Result<Error, Empty> {
        usbConnection = usbManager.openDevice(usbDevice)
        val result = usbConnection?.claimInterface(usbInterface, true)
        LogUtil.e(result.toString())
        inRequest = UsbRequest()
        inRequest.initialize(usbConnection, usbInEndpoint)
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

    @Suppress("DEPRECATION")
    override fun read(): Result<Error, ByteArray> {
        val buffer = ByteBuffer.allocate(64)
        val report = ByteArray(64)
        usbConnection?.let {
            if (inRequest.queue(buffer, 64)) {
                usbConnection?.requestWait()
                buffer.rewind()
                buffer.get(report, 0, report.size)
                buffer.clear()
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