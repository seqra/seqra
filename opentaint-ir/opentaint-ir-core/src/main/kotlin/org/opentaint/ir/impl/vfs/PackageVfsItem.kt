package org.opentaint.ir.impl.vfs

import java.util.concurrent.ConcurrentHashMap

class PackageVfsItem(folderName: String?, parent: PackageVfsItem?) :
    AbstractVfsItem<PackageVfsItem>(folderName, parent) {

    // folderName -> subpackage
    internal var subpackages = ConcurrentHashMap<String, PackageVfsItem>()

    // simpleName -> (locationId -> node)
    internal var classes = ConcurrentHashMap<String, ConcurrentHashMap<Long, ClassVfsItem>>()

    fun findPackageOrNull(subfolderName: String): PackageVfsItem? {
        return subpackages[subfolderName]
    }

    fun firstClassOrNull(className: String, locationId: Long): ClassVfsItem? {
        return classes[className]?.get(locationId)
    }

    fun filterClassNodes(className: String, predicate: (ClassVfsItem) -> Boolean): List<ClassVfsItem> {
        return classes[className].orEmpty().asSequence().filter { predicate(it.value) }.map { it.value }.toList()
    }

    fun firstClassOrNull(className: String, predicate: (Long) -> Boolean): ClassVfsItem? {
        val locationsClasses = classes.get(className) ?: return null
        return locationsClasses.asSequence().firstOrNull { predicate(it.key) }?.value
    }

    fun visit(visitor: VfsVisitor) {
        visitor.visitPackage(this)
        subpackages.values.forEach {
            visitor.visitPackage(it)
        }
    }

    fun forEachClasses(locationId: Long, action: (ClassVfsItem) -> Unit) {
        classes.values.forEach {
            it[locationId]?.let { action(it) }
        }
    }

    fun dropLocation(locationId: Long) {
        classes.values.forEach {
            it.remove(locationId)
        }
    }
}