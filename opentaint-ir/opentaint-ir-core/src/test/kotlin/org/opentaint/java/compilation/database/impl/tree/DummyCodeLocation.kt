package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.ApiLevel
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.fs.ByteCodeLoader
import java.io.InputStream

open class DummyCodeLocation(override val version: String) : ByteCodeLocation {
        override val apiLevel: ApiLevel
                get() = ApiLevel.ASM8

        override val currentVersion: String
                get() = version
        override suspend fun resolve(classFullName: String): InputStream? {
                TODO("Not yet implemented")
        }

        override suspend fun loader() = ByteCodeLoader(this, emptyList(), emptyList())
}

