package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.fs.ClassByteCodeSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ClassTree(private val listeners: List<ClassTreeListener>? = null) {

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
        return findPackage(splitted)?.firstClassOrNull(simpleClassName, codeLocation.version)
    }

    fun firstClassNodeOrNull(fullName: String, predicate: (String) -> Boolean): ClassNode? {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.firstClassOrNull(simpleClassName, predicate)
    }

    fun filterClassNodes(fullName: String, predicate: (ClassNode) -> Boolean = { true } ): List<ClassNode> {
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

    suspend fun notifyOnMetaLoaded(classNodeWithLoadedMeta: ClassNode) {
        listeners?.forEach {
            it.notifyOnMetaLoaded(classNodeWithLoadedMeta, this)
        }
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

    fun firstClassOrNull(className: String, version: String): ClassNode? {
        return classes[className]?.get(version)
    }

    fun filterClassNodes(className: String,  predicate: (ClassNode) -> Boolean): List<ClassNode> {
        return classes[className]?.filterValues(predicate).orEmpty().values.toList()
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

    private val _subTypes = CopyOnWriteArrayList<ClassNode>()

    val subTypes: List<ClassNode> get() = _subTypes.toList()

    fun addSubType(subTypeNode: ClassNode) {
        _subTypes.add(subTypeNode)
    }

    suspend fun info() = source.meta()

}