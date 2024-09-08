package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.impl.fs.ClassByteCodeSource
import java.util.concurrent.ConcurrentHashMap

class PackageNode(folderName: String?, parent: PackageNode?) : AbstractNode(folderName, parent) {

    // folderName -> subpackage
    private val subpackages = ConcurrentHashMap<String, PackageNode>()

    // simpleName -> (version -> node)
    private val classes = ConcurrentHashMap<String, ConcurrentHashMap<String, ClassNode>>()

    fun findPackageOrNew(subfolderName: String): PackageNode {
        return subpackages.getOrPut(subfolderName) {
            PackageNode(subfolderName, this)
        }
    }

    fun findPackageOrNull(subfolderName: String): PackageNode? {
        return subpackages.get(subfolderName)
    }

    fun findClassOrNew(simpleClassName: String, version: String, source: ClassByteCodeSource): ClassNode {
        val nameIndex = classes.getOrPut(simpleClassName) {
            ConcurrentHashMap()
        }
        return nameIndex.getOrPut(version) { ClassNode(simpleClassName, this, source) }
    }

    fun firstClassOrNull(className: String, version: String): ClassNode? {
        return classes[className]?.get(version)
    }

    fun filterClassNodes(className: String, predicate: (ClassNode) -> Boolean): List<ClassNode> {
        return classes[className]?.filterValues(predicate).orEmpty().values.toList()
    }

    fun firstClassOrNull(className: String, predicate: (String) -> Boolean): ClassNode? {
        val versioned = classes[className] ?: return null
        return versioned.search(1L) { version, node -> node.takeIf { predicate(version) } }
    }

    fun visit(visitor: NodeVisitor) {
        visitor.visitPackageNode(this)
        subpackages.values.forEach {
            visitor.visitPackageNode(it)
        }
    }


    fun dropVersion(version: String) {
        subpackages.remove(version)
        classes.forEach { (_, versions) ->
            versions.remove(version)
            versions.forEach { (_, node) ->
                node.removeSubTypesOfVersion(version)
            }
        }
    }
}