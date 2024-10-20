package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.fs.ClassByteCodeSource
import java.util.concurrent.ConcurrentHashMap

open class ClassTree : AbstractClassTree<PackageNode, ClassNode>() {

    public override val rootNode = PackageNode(null, null)

    override fun PackageNode.findClassOrNew(
        simpleClassName: String,
        source: ClassByteCodeSource
    ): ClassNode {
        val nameIndex = classes.getOrPut(simpleClassName) {
            ConcurrentHashMap<String, ClassNode>()
        }
        return nameIndex.getOrPut(source.location.id) {
            ClassNode(simpleClassName, this, source)
        }
    }

    override fun PackageNode.findPackageOrNew(subfolderName: String): PackageNode {
        return subpackages.getOrPut(subfolderName) {
            PackageNode(subfolderName, this)
        }
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

}
