package com.tejeet.ft311gpio.utils

object ByteUtils {
    fun highMask(value: Byte, mask: Int): Byte {
        return (value.toInt() or mask).toByte()
    }

    fun lowMask(value: Byte, mask: Int): Byte {
        return (value.toInt() and mask.inv()).toByte()
    }

    fun high(value: Byte, bit: Int): Byte {
        return highMask(value, 1 shl bit)
    }

    @JvmStatic
    fun low(value: Byte, bit: Int): Byte {
        return lowMask(value, 1 shl bit)
    }

    fun bit(value: Byte, bit: Int): Byte {
        return ((value.toInt() shr bit) and 1).toByte()
    }

    fun shiftInMsb(bytes: ByteArray, size: Int): Long {
        var size = size
        val BYTES_IN_LONG = java.lang.Long.SIZE / java.lang.Byte.SIZE
        size = if (size > BYTES_IN_LONG) BYTES_IN_LONG else size
        var value: Long = 0
        for (i in 0 until size) {
            value = (value shl java.lang.Byte.SIZE) or (bytes[i].toInt() and 0xff).toLong()
        }
        return value
    }

    fun shiftInLsb(bytes: ByteArray, size: Int): Long {
        var size = size
        val BYTES_IN_LONG = java.lang.Long.SIZE / java.lang.Byte.SIZE
        size = if (size > BYTES_IN_LONG) BYTES_IN_LONG else size
        var value: Long = 0
        for (i in 0 until size) {
            value = value or ((bytes[i].toInt() and 0xff) shl (java.lang.Byte.SIZE * i)).toLong()
        }
        return value
    }
}
