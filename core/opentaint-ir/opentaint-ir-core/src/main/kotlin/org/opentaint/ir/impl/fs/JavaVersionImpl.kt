package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.jvm.JavaVersion

private class JavaVersionImpl(override val majorVersion: Int) : JavaVersion

fun parseRuntimeVersion(version: String): JavaVersion {
    return when {
        version.startsWith("1.") -> org.opentaint.ir.impl.fs.JavaVersionImpl(version.substring(2, 3).toInt())
        else -> {
            val dot = version.indexOf(".")
            if (dot != -1) {
                org.opentaint.ir.impl.fs.JavaVersionImpl(version.substring(0, dot).toInt())
            } else {
                org.opentaint.ir.impl.fs.JavaVersionImpl(8)
            }
        }
    }
}
