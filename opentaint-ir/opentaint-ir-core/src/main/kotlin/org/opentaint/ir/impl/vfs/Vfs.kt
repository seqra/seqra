package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.RegisteredLocation

abstract class AbstractVfsItem<T : AbstractVfsItem<T>>(open val name: String?, val parent: T?) {

    val fullName: String by lazy(LazyThreadSafetyMode.NONE) {
        val reversedNames = arrayListOf(name)
        var node: T? = parent
        while (node != null) {
            node.name?.let {
                reversedNames.add(it)
            }
            node = node.parent
        }
        reversedNames.reversed().joinToString(".")
    }
}

interface VfsVisitor {

    fun visitPackage(packageItem: PackageVfsItem) {}
}

class RemoveLocationsVisitor(private val locations: Set<RegisteredLocation>) : VfsVisitor {
    override fun visitPackage(packageItem: PackageVfsItem) {
        locations.forEach {
            packageItem.dropLocation(it.id)
        }
    }

}