package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.fs.ClassByteCodeSource

open class ClassTree(internal val listeners: List<ClassTreeListener>? = null) {

    internal val rootNode = PackageNode(null, null)

    fun addClass(source: ClassByteCodeSource): ClassNode {
        val splitted = source.className.splitted
        val simpleClassName = splitted[splitted.size - 1]
        val version = source.location.id
        if (splitted.size == 1) {
            return rootNode.findClassOrNew(simpleClassName, version, source)
        }
        var node = rootNode
        var index = 0
        while (index < splitted.size - 1) {
            val subfolderName = splitted[index]
            node = node.findPackageOrNew(subfolderName)
            index++
        }
        return node.findClassOrNew(simpleClassName, version, source)
    }

    fun findClassNodeOrNull(codeLocation: ByteCodeLocation, fullName: String): ClassNode? {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.firstClassOrNull(simpleClassName, codeLocation.id)
    }

    fun firstClassNodeOrNull(fullName: String, predicate: (String) -> Boolean = { true }): ClassNode? {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.firstClassOrNull(simpleClassName, predicate)
    }

    fun filterClassNodes(fullName: String, predicate: (ClassNode) -> Boolean = { true }): List<ClassNode> {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.filterClassNodes(simpleClassName, predicate).orEmpty()
    }

    private fun findPackage(splitted: List<String>): PackageNode? {
        var node: PackageNode? = rootNode
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

    fun visit(visitor: NodeVisitor) {
        rootNode.visit(visitor)
    }

    suspend fun notifyOnMetaLoaded(classNodeWithLoadedMeta: ClassNode) {
        listeners?.forEach {
            it.notifyOnMetaLoaded(classNodeWithLoadedMeta, this)
        }
    }

    private val String.splitted get() = this.split(".")
}
