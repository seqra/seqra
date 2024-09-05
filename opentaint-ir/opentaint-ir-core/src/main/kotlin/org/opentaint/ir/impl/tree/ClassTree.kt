package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import java.util.concurrent.ConcurrentHashMap

class ClassTree {

    private val rootNode = PackageNode(null, null)

    fun addClass(codeLocation: ByteCodeLocation, fullName: String): ClassNode {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        val version = codeLocation.version
        if (splitted.size == 1) {
            return rootNode.findClassOrNew(simpleClassName, version)
        }
        var node = rootNode
        var index = 0
        while (index < splitted.size - 1) {
            val subfolderName = splitted[index]
            node = node.findPackageOrNew(subfolderName, version)
            index++
        }
        return node.findClassOrNew(simpleClassName, version)
    }

    fun findClassNodeOrNull(codeLocation: ByteCodeLocation, fullName: String): ClassNode? {
        val version = codeLocation.version
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        var node: PackageNode? = rootNode
        var index = 0
        while (index < (splitted.size - 1)) {
            if (node == null) {
                return null
            }
            val subfolderName = splitted[index]
            node = node.findPackageOrNull(subfolderName, version)
            index++
        }
        return node?.findClassOrNull(simpleClassName, version)
    }

    private val String.splitted get() = this.split(".")
}

class PackageNode(val folderName: String?, val parent: PackageNode?) {

    private val subpackages = ConcurrentHashMap<NodeKey, PackageNode>()
    private val classes = ConcurrentHashMap<NodeKey, ClassNode>()

    fun findPackageOrNew(subfolderName: String, version: String): PackageNode {
        val key = NodeKey(subfolderName, version)
        return subpackages.getOrPut(key) {
            PackageNode(subfolderName, this)
        }
    }

    fun findPackageOrNull(subfolderName: String, version: String): PackageNode? {
        val key = NodeKey(subfolderName, version)
        return subpackages.get(key)
    }

    fun findClassOrNew(className: String, version: String): ClassNode {
        val key = NodeKey(className, version)
        return classes.getOrPut(key) {
            ClassNode(className, this)
        }
    }

    fun findClassOrNull(className: String, version: String): ClassNode? {
        return classes.get(NodeKey(className, version))
    }
}


data class ClassNode(val simpleName: String, val packageNode: PackageNode) {

    val fullName: String by lazy(LazyThreadSafetyMode.NONE) {
        val reversedNames = arrayListOf(simpleName)
        var node: PackageNode? = packageNode
        while (node != null) {
            node.folderName?.let {
                reversedNames.add(it)
            }
            node = node.parent
        }
        reversedNames.reversed().joinToString(".")
    }
}

data class NodeKey(val name: String, val version: String)
