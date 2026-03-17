package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.jvm.ClassSource

class ClassVfsItem(
    override val name: String,
    packageNode: PackageVfsItem,
    internal val source: ClassSource
) : AbstractVfsItem<PackageVfsItem>(name, packageNode) {

    val location get() = source.location

}
