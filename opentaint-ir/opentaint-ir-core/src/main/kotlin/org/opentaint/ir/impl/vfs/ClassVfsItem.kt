package org.opentaint.opentaint-ir.impl.vfs

import org.opentaint.opentaint-ir.api.ClassSource

class ClassVfsItem(
    override val name: String,
    packageNode: PackageVfsItem,
    internal val source: ClassSource
) : AbstractVfsItem<PackageVfsItem>(name, packageNode) {

    val location get() = source.location

}