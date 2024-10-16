package com.lepu.vtm01.type

import com.lepu.vtm01.util.CmdUtil

object LepuBleResponse {
    class BleResponse(val bytes: ByteArray) {
        var cmd: Int
        var pkgType: Int
        var pkgNo: Int
        var len: Int
        var content: ByteArray

        init {
            when (toUInt(bytes.copyOfRange(0, 1))) {
                0xA5 -> {
                    cmd = (bytes[1].toUInt() and 0xFFu).toInt()
                    pkgType = (bytes[3].toUInt() and 0xFFu).toInt()
                    pkgNo = (bytes[4].toUInt() and 0xFFu).toInt()
                    len = toUInt(bytes.copyOfRange(5, 7))
                    content = bytes.copyOfRange(7, 7+len)
                }
                0x55 -> {
                    cmd = 0
                    pkgType = 0
                    pkgNo = toUInt(bytes.copyOfRange(3, 5))
                    len = toUInt(bytes.copyOfRange(5, 7))
                    content = bytes.copyOfRange(7, 7+len)
                }
                else -> {
                    cmd = 0
                    pkgType = 0
                    pkgNo = 0
                    len = 0
                    content = ByteArray(0)
                }
            }
        }
        override fun toString(): String {
            return """
                BleResponse : 
                cmd : $cmd
                pkgType : $pkgType
                pkgNo : $pkgNo
                len : $len
                content : ${content.size}
            """.trimIndent()
        }
    }

    class FileList(val bytes: ByteArray) {
        var size: Int
        var fileNames = mutableListOf<String>()
        init {
            var index = 0
            size = toUInt(bytes.copyOfRange(index, index+1))
            index++
            for (i in 0 until size) {
                fileNames.add(CmdUtil.trimStr(String(bytes.copyOfRange(index, index + 16))))
                index += 16
            }
        }
        override fun toString(): String {
            return """
                FileList : 
                size : $size
                fileNames : $fileNames
            """.trimIndent()
        }
    }
    fun toUInt(bytes: ByteArray): Int {
        var result : UInt = 0u
        for (i in bytes.indices) {
            result = result or ((bytes[i].toUInt() and 0xFFu) shl 8*i)
        }

        return result.toInt()
    }

    fun toLong(bytes: ByteArray): Long {
        var result : Long = 0
        for (i in bytes.indices) {
            result = result or ((bytes[i].toLong() and 0xFF) shl 8*i)
        }
        return result
    }

    val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY.get(v ushr 4)
            hexChars[j * 2 + 1] = HEX_ARRAY.get(v and 0x0F)
        }
        return String(hexChars)
    }
}