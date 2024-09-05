package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import java.io.File

class ByteCodeLocationImpl(override val file: File): ByteCodeLocation {

    override val isJar: Boolean
        get() = file.isFile

    override val version: String
        get() = file.name // todo check last modified date
}