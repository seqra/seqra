package org.opentaint.ir.impl.tree

import java.util.concurrent.ConcurrentHashMap

class PackageNode(folderName: String?, parent: PackageNode?) : AbstractNode<PackageNode>(folderName, parent) {

    // folderName -> subpackage
    internal var subpackages = ConcurrentHashMap<String, PackageNode>()

    // simpleName -> (locationId -> node)
    internal var classes = ConcurrentHashMap<String, ConcurrentHashMap<String, ClassNode>>()

    fun findPackageOrNull(subfolderName: String): PackageNode? {
        return subpackages[subfolderName]
    }

    fun firstClassOrNull(className: String, locationId: String): ClassNode? {
        return classes[className]?.get(locationId)
    }

    fun filterClassNodes(className: String, predicate: (ClassNode) -> Boolean): List<ClassNode> {
        return classes[className].orEmpty().asSequence().filter { predicate(it.value) }.map { it.value }.toList()
    }

    fun firstClassOrNull(className: String, predicate: (String) -> Boolean): ClassNode? {
        val locationsClasses = classes.get(className) ?: return null
        return locationsClasses.asSequence().firstOrNull { predicate(it.key) }?.value
    }

    fun visit(visitor: NodeVisitor) {
        visitor.visitPackageNode(this)
        subpackages.values.forEach {
            visitor.visitPackageNode(it)
        }
    }

    fun dropLocation(locationId: String) {
        classes.values.forEach {
            it.remove(locationId)
        }
    }
}