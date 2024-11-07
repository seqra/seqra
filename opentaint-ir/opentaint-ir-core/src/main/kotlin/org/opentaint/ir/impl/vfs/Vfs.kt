package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.RegisteredLocation

abstract class AbstractVfsItem<T : AbstractVfsItem<T>>(open val name: String?, val parent: T?) {

    open val fullName: String? by lazy(LazyThreadSafetyMode.NONE) {
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

class RemoveLocationsVisitor(private val locations: List<RegisteredLocation>) : VfsVisitor {

    override fun visitPackage(packageItem: PackageVfsItem) {
        if (packageItem.fullName?.startsWith("java.") == true) {
            return
        }
        locations.forEach {
            packageItem.removeClasses(it.id)
        }
    }
}

fun main() {
    println(PackageVfsItem(null, null).fullName)
}