package com.lepu.vtm01.type

import com.lepu.vtm01.type.LepuBleResponse.toUInt

class Config() {

    var bytes = ByteArray(0)
    var type = 0
    // reserved 11
    var algAvgTime = 3
    // reserved 28

    constructor(bytes: ByteArray) : this() {
        this.bytes = bytes
        var index = 0
        index += 11
        algAvgTime = toUInt(bytes.copyOfRange(index, index+1))
    }

    fun getDataBytes() : ByteArray {
        var data = byteArrayOf(type.toByte())
            .plus(ByteArray(3))
        when (type) {
            13 -> {
                data = data.plus(algAvgTime.toByte()).plus(ByteArray(3))
            }
        }
        return data
    }
}