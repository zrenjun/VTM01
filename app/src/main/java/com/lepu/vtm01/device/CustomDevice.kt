package com.lepu.vtm01.device

import com.lepu.vtm01.type.Empty
import com.lepu.vtm01.type.Result
import com.lepu.vtm01.type.Error
import io.reactivex.Observable

interface CustomDevice {

    fun connect(): Result<Error, Empty>

    fun disconnect()

    fun isConnected(): Result<Error, Empty>

    fun setCmd(byteArray: ByteArray): Result<Error, Empty>

    fun receive(): Observable<Result<Error, ByteArray>>
}