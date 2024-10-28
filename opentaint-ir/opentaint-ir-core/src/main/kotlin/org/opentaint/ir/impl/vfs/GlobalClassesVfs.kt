package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.fs.ClassSource
import java.util.concurrent.ConcurrentHashMap

open class GlobalClassesVfs : AbstractClassVfs<PackageVfsItem, ClassVfsItem>() {

    public override val rootItem = PackageVfsItem(null, null)

    override fun PackageVfsItem.findClassOrNew(
        simpleClassName: String,
        source: ClassSource
    ): ClassVfsItem {
        val nameIndex = classes.getOrPut(simpleClassName) {
            ConcurrentHashMap<Long, ClassVfsItem>()
        }
        return nameIndex.getOrPut(source.location.id) {
            ClassVfsItem(simpleClassName, this, source)
        }
    }

    override fun PackageVfsItem.findPackageOrNew(subfolderName: String): PackageVfsItem {
        return subpackages.getOrPut(subfolderName) {
            PackageVfsItem(subfolderName, this)
        }
    }

    fun findClassNodeOrNull(codeLocation: RegisteredLocation, fullName: String): ClassVfsItem? {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.firstClassOrNull(simpleClassName, codeLocation.id)
    }

    fun firstClassNodeOrNull(fullName: String, predicate: (Long) -> Boolean = { true }): ClassVfsItem? {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.firstClassOrNull(simpleClassName, predicate)
    }

    private fun findPackage(splitted: List<String>): PackageVfsItem? {
        var node: PackageVfsItem? = rootItem
        var index = 0
        while (index < (splitted.size - 1)) {
            if (node == null) {
                return null
            }
            val subfolderName = splitted[index]
            node = node.findPackageOrNull(subfolderName)
            index++
        }
        return node
    }

    fun visit(visitor: VfsVisitor) {
        rootItem.visit(visitor)
    }

}
