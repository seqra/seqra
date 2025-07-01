package org.opentaint.ir.impl.vfs

import jetbrains.exodus.core.dataStructures.hash.LongHashMap
import jetbrains.exodus.core.dataStructures.hash.PackedLongHashSet
import jetbrains.exodus.kotlin.synchronized
import org.opentaint.ir.api.jvm.ClassSource
import java.util.concurrent.ConcurrentHashMap

class PackageVfsItem(folderName: String?, parent: PackageVfsItem?) :
    AbstractVfsItem<PackageVfsItem>(folderName, parent) {

    // folderName -> subpackage
    private val subpackages = ConcurrentHashMap<String, PackageVfsItem>()

    // simpleName -> (locationId -> node)
    private val classes = ConcurrentHashMap<String, LongHashMap<ClassVfsItem>>()

    // all locations
    private val locations = PackedLongHashSet()

    fun findPackageOrNull(subFolderName: String): PackageVfsItem? {
        return subpackages[subFolderName]
    }

    fun findPackageOrNew(subFolderName: String): PackageVfsItem {
        return subpackages.getOrPut(subFolderName) {
            PackageVfsItem(subFolderName, this)
        }
    }

    fun firstClassOrNull(className: String, locationId: Long): ClassVfsItem? {
        return classes[className]?.synchronized { get(locationId) }.also { locations.synchronized { add(locationId) } }
    }

    fun findClassOrNew(simpleClassName: String, source: ClassSource): ClassVfsItem {
        val nameIndex = classes.getOrPut(simpleClassName) {
            LongHashMap()
        }
        val locationId = source.location.id
        return nameIndex.synchronized {
            getOrPut(locationId) {
                ClassVfsItem(simpleClassName, this@PackageVfsItem, source)
            }
        }.also { locations.synchronized { add(locationId) } }
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
        if (locations.synchronized { remove(locationId) }) {
            classes.values.forEach {
                it.remove(locationId)
            }
        }
    }

    fun clear() {
        subpackages.clear()
        classes.clear()
    }
}