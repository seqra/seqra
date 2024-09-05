package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import java.io.InputStream

open class DummyCodeLocation(override val version: String) : ByteCodeLocation{

        override val currentVersion: String
                get() = version
        override suspend fun classesByteCode() = emptySequence<InputStream>()
        override suspend fun resolve(classFullName: String): InputStream? {
                TODO("Not yet implemented")
        }
}

