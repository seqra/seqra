package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.reader.ClassMetaInfo
import java.util.concurrent.ConcurrentHashMap

class ClassTree {

    private val rootNode = PackageNode(null, null)

    fun addClass(codeLocation: ByteCodeLocation, fullName: String, info: ClassMetaInfo): ClassNode {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        val version = codeLocation.version
        if (splitted.size == 1) {
            return rootNode.findClassOrNew(simpleClassName, version, codeLocation, info)
        }
        var node = rootNode
        var index = 0
        while (index < splitted.size - 1) {
            val subfolderName = splitted[index]
            node = node.findPackageOrNew(subfolderName)
            index++
        }
        return node.findClassOrNew(simpleClassName, version, codeLocation, info)
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
            node = node.findPackageOrNull(subfolderName)
            index++
        }
        return node?.findClassOrNull(simpleClassName, version)
    }

    fun firstClassNodeOrNull(fullName: String, predicate: (String) -> Boolean): ClassNode? {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
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
        return node?.firstClassOrNull(simpleClassName, predicate)
    }

    private val String.splitted get() = this.split(".")
}

class PackageNode(val folderName: String?, val parent: PackageNode?) {

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

    fun findClassOrNew(simpleClassName: String, version: String, location: ByteCodeLocation, info: ClassMetaInfo): ClassNode {
        val nameIndex = classes.getOrPut(simpleClassName) {
            ConcurrentHashMap()
        }
        return nameIndex.getOrPut(version) { ClassNode(simpleClassName, this, location, info) }
    }

    fun findClassOrNull(className: String, version: String): ClassNode? {
        return classes[className]?.get(version)
    }

    fun firstClassOrNull(className: String, predicate: (String) -> Boolean): ClassNode? {
        val versioned = classes[className] ?: return null
        return versioned.search(1L) { version, node -> node.takeIf { predicate(version) } }
    }
}


data class ClassNode(val simpleName: String, val packageNode: PackageNode, val location: ByteCodeLocation, val info: ClassMetaInfo) {

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
