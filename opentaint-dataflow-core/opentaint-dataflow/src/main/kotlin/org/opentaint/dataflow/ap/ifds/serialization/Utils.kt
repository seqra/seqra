package org.opentaint.dataflow.ap.ifds.serialization

import java.io.DataInputStream
import java.io.DataOutputStream

inline fun <reified T: Enum<T>> DataOutputStream.writeEnum(value: T) {
    write(value.ordinal)
}

inline fun <reified T: Enum<T>> DataInputStream.readEnum(): T {
    return T::class.java.enumConstants[read()]
}