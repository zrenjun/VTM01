package com.lepu.vtm01.type

sealed class Error {
    data object UsbConnectionError : Error()
    data object ClaimInterfaceError : Error()
    data object NoDeviceFoundError : Error()
    data object ReadError : Error()
}