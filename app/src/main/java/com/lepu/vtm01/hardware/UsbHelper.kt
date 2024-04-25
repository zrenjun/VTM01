package com.lepu.vtm01.hardware

import com.lepu.vtm01.type.Empty
import com.lepu.vtm01.type.Result
import com.lepu.vtm01.type.Error

interface UsbHelper {

    fun enumerate(vid: Int, pid: Int, nInterface: Int): Result<Error, Empty>

    fun open(): Result<Error, Empty>

    fun close()

    fun isConnected(): Result<Error, Empty>

    fun write(report: ByteArray): Result<Error, Empty>

    fun read(size: Int): Result<Error, ByteArray>
}