package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.ClassSource

class ClassVfsItem(
    override val name: String,
    packageNode: PackageVfsItem,
    internal val source: ClassSource
) : AbstractVfsItem<PackageVfsItem>(name, packageNode) {

    override val fullName: String
        get() = super.fullName!!

    val location get() = source.location

}