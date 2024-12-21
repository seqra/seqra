package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.RegisteredLocation

abstract class AbstractVfsItem<T : AbstractVfsItem<T>>(open val name: String?, val parent: T?) {

    val fullName: String by lazy {
        val reversedNames = arrayListOf<String>()
        var node: AbstractVfsItem<*>? = this
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

class RemoveLocationsVisitor(
    private val locations: List<RegisteredLocation>,
    private val ignoredPackages: List<String> = emptyList()
) : VfsVisitor {

    override fun visitPackage(packageItem: PackageVfsItem) {
        val name = packageItem.fullName + "."
        if (ignoredPackages.any { name.startsWith(it) }) {
            return
        }
        locations.forEach {
            packageItem.removeClasses(it.id)
        }
    }
}