package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.LocationScope
import org.opentaint.java.compilation.database.impl.fs.ByteCodeLoaderImpl

open class DummyCodeLocation(override val id: String) : ByteCodeLocation {

    override val scope: LocationScope
        get() = LocationScope.APP

    override fun isChanged() = false

    override fun createRefreshed() = this

    override suspend fun resolve(classFullName: String) = null

    override suspend fun loader() = ByteCodeLoaderImpl(this, emptyMap()) { emptyMap() }
}

