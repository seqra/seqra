package org.opentaint.java.compilation.database.impl.fs

import org.opentaint.java.compilation.database.api.ByteCodeLocation

abstract class AbstractByteCodeLocation : ByteCodeLocation {

    override val id: String by lazy(LazyThreadSafetyMode.NONE) {
        getCurrentId()
    }

    abstract fun getCurrentId(): String

    override fun isChanged(): Boolean {
        return id != getCurrentId()
    }
}