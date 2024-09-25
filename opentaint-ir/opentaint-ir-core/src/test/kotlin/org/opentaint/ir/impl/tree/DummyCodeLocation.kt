package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.LocationScope
import org.opentaint.ir.impl.fs.ByteCodeLoaderImpl
import java.net.URL

open class DummyCodeLocation(override val id: String) : ByteCodeLocation {

    override val scope: LocationScope
        get() = LocationScope.APP

    override val locationURL: URL
        get() = TODO()

    override fun isChanged() = false

    override fun createRefreshed() = this

    override suspend fun resolve(classFullName: String) = null

    override suspend fun loader() = ByteCodeLoaderImpl(this, emptyMap()) { emptyMap() }
}

