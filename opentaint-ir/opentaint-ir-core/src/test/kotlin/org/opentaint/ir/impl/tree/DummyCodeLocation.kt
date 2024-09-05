package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import java.io.File

open class DummyCodeLocation(override val version: String) : ByteCodeLocation{
        override val isJar = false
        override val file = File("")
}

