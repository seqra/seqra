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

    fun firstClassOrNull(className: String, predicate: (Long) -> Boolean): ClassVfsItem? {
        val locationsClasses = classes[className] ?: return null
        return locationsClasses.asSequence().firstOrNull { predicate(it.key) }?.value
    }

    fun findClasses(className: String, predicate: (Long) -> Boolean): List<ClassVfsItem> {
        val locationsClasses = classes[className] ?: return emptyList()
        return locationsClasses.asSequence().filter { predicate(it.key) }.map { it.value }.toList()
    }

    fun visit(visitor: VfsVisitor) {
        visitor.visitPackage(this)
        subpackages.values.forEach {
            it.visit(visitor)
        }
    }

    fun removeClasses(locationId: Long) {
        classes.values.forEach {
            it.remove(locationId)
        }
    }
}