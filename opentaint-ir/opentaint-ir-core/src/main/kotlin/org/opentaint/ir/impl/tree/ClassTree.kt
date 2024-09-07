package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.fs.ClassByteCodeSource
import java.util.concurrent.ConcurrentHashMap

class ClassTree {

    private val rootNode = PackageNode(null, null)

    fun addClass(source: ClassByteCodeSource): ClassNode {
        val splitted = source.className.splitted
        val simpleClassName = splitted[splitted.size - 1]
        val version = source.location.version
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
        return findPackage(splitted)?.findClassOrNull(simpleClassName, codeLocation.version)
    }

    fun firstClassNodeOrNull(fullName: String, predicate: (String) -> Boolean): ClassNode? {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.firstClassOrNull(simpleClassName, predicate)
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

    private val String.splitted get() = this.split(".")
}


abstract class AbstractNode(open val name: String?, val parent: PackageNode?) {

    val fullName: String by lazy(LazyThreadSafetyMode.NONE) {
        val reversedNames = arrayListOf(name)
        var node: PackageNode? = parent
        while (node != null) {
            node.name?.let {
                reversedNames.add(it)
            }
            node = node.parent
        }
        reversedNames.reversed().joinToString(".")
    }
}

class PackageNode(folderName: String?, parent: PackageNode?) : AbstractNode(folderName, parent) {

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

    fun findClassOrNull(className: String, version: String): ClassNode? {
        return classes[className]?.get(version)
    }

    fun firstClassOrNull(className: String, predicate: (String) -> Boolean): ClassNode? {
        val versioned = classes[className] ?: return null
        return versioned.search(1L) { version, node -> node.takeIf { predicate(version) } }
    }
}


class ClassNode(
    simpleName: String,
    packageNode: PackageNode,
    val source: ClassByteCodeSource
) : AbstractNode(simpleName, packageNode) {

    override val name: String = simpleName

    val location get() = source.location

    suspend fun info() = source.meta()

}