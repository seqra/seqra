package org.opentaint.java.compilation.database.impl.tree

import jetbrains.exodus.core.dataStructures.persistent.Persistent23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.writeFinally
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.fs.ClassByteCodeSource


/**
 * this class tree is not THREAD SAFE
 */
class LibraryClassTree(private val location: ByteCodeLocation, listeners: List<ClassTreeListener>? = null) :
    AbstractClassTree<LibraryPackageNode, LibraryClassNode>() {

    override val rootNode = LibraryPackageNode(null, null, location.id)

    override fun LibraryPackageNode.findClassOrNew(
        simpleClassName: String,
        version: String,
        source: ClassByteCodeSource
    ): LibraryClassNode {
        return classes.getOrPut(simpleClassName) {
            LibraryClassNode(simpleClassName, this, source)
        }
    }

    override fun LibraryPackageNode.findPackageOrNew(subfolderName: String): LibraryPackageNode {
        return subpackages.getOrPut(subfolderName) {
            LibraryPackageNode(subfolderName, this, location.id)
        }
    }

    fun pushInto(classTree: ClassTree) {
        rootNode.mergeInto(classTree.rootNode)
    }

}

class LibraryPackageNode(folderName: String?, parent: LibraryPackageNode?, private val version: String) :
    AbstractNode<LibraryPackageNode>(folderName, parent) {

    // folderName -> subpackage
    internal val subpackages = HashMap<String, LibraryPackageNode>()

    // simpleName -> (version -> node)
    internal val classes = HashMap<String, LibraryClassNode>()

    fun asPackageNode(parent: PackageNode): PackageNode {
        return PackageNode(name, parent).also { packageNode ->
            packageNode.subpackages.writeFinally {
                subpackages.forEach { (key, node) ->
                    put(key, node.asPackageNode(packageNode))
                }
            }
            packageNode.classes.writeFinally {
                classes.forEach { (key, node) ->
                    put(key, newClassMap(node, packageNode))
                }
            }
        }
    }

    private fun newClassMap(node: LibraryClassNode, packageNode: PackageNode): Persistent23TreeMap<String, ClassNode> {
        return Persistent23TreeMap<String, ClassNode>().also {
            it.writeFinally {
                put(version, node.asClassNode(packageNode))
            }
        }
    }

    fun mergeInto(global: PackageNode) {
        val library = this
        global.subpackages.writeFinally {
            library.subpackages.forEach { (name, subNode) ->
                val globalSubPackage = get(name)
                if (globalSubPackage == null) {
                    put(name, subNode.asPackageNode(global))
                } else {
                    subNode.mergeInto(globalSubPackage)
                }
            }
        }
        global.classes.writeFinally {
            library.classes.forEach { (name, subNode) ->
                val classNode = get(name)
                if (classNode == null) {
                    put(name, newClassMap(subNode, global))
                } else {
                    classNode.writeFinally {
                        put(subNode.version, subNode.asClassNode(global))
                    }
                }
            }
        }
    }
}

class LibraryClassNode(
    simpleName: String,
    packageNode: LibraryPackageNode,
    private val source: ClassByteCodeSource
) : AbstractNode<LibraryPackageNode>(simpleName, packageNode) {

    override val name: String = simpleName

    val version get() = source.location.id

    fun asClassNode(parent: PackageNode) = ClassNode(name, parent, source)
}