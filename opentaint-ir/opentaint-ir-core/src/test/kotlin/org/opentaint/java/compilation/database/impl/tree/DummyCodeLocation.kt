package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.ApiLevel
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.fs.ByteCodeLoaderImpl

open class DummyCodeLocation(override val version: String) : ByteCodeLocation {
    override val apiLevel = ApiLevel.ASM8

    override val currentVersion: String
        get() = version

    override fun refreshed() = this

    override suspend fun resolve(classFullName: String) = null

    override suspend fun loader() = ByteCodeLoaderImpl(this, emptyList(), emptyList())
}

