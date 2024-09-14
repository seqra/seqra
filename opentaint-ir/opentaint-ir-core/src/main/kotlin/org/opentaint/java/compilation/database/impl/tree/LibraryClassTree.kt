package org.opentaint.java.compilation.database.impl.tree

import jetbrains.exodus.core.dataStructures.persistent.Persistent23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.writeFinally
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.fs.ClassByteCodeSource


/**
 * this class tree is NOT THREAD SAFE
 */
class LibraryClassTree(val location: ByteCodeLocation) :
    AbstractClassTree<LibraryPackageNode, LibraryClassNode>() {

    override val rootNode = LibraryPackageNode(null, null, location.id)

    override fun LibraryPackageNode.findClassOrNew(
        simpleClassName: String,
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

    fun pushInto(classTree: ClassTree): Map<String, ClassNode> {
        return rootNode.mergeInto(classTree.rootNode)
    }

}

class LibraryPackageNode(folderName: String?, parent: LibraryPackageNode?, private val version: String) :
    AbstractNode<LibraryPackageNode>(folderName, parent) {

    // folderName -> subpackage
    internal val subpackages = HashMap<String, LibraryPackageNode>()

    // simpleName -> node
    internal val classes = HashMap<String, LibraryClassNode>()

    fun asPackageNode(parent: PackageNode, loadedClasses: MutableMap<String, ClassNode>): PackageNode {
        return PackageNode(name, parent).also { packageNode ->
            packageNode.subpackages.writeFinally {
                subpackages.forEach { (key, node) ->
                    put(key, node.asPackageNode(packageNode, loadedClasses))
                }
            }
            packageNode.classes.writeFinally {
                classes.forEach { (key, node) ->
                    put(key, newClassMap(node, packageNode, loadedClasses))
                }
            }
        }
    }

    private fun newClassMap(node: LibraryClassNode, packageNode: PackageNode, loadedClasses: MutableMap<String, ClassNode>): Persistent23TreeMap<String, ClassNode> {
        return Persistent23TreeMap<String, ClassNode>().also {
            it.writeFinally {
                node.asClassNode(packageNode).also {
                    put(version, it)
                    loadedClasses[it.fullName] = it
                }
            }
        }
    }

    fun mergeInto(global: PackageNode, loadedClasses: MutableMap<String, ClassNode> = hashMapOf()): Map<String, ClassNode> {
        val library = this
        global.subpackages.writeFinally {
            library.subpackages.forEach { (name, subNode) ->
                val globalSubPackage = get(name)
                if (globalSubPackage == null) {
                    put(name, subNode.asPackageNode(global, loadedClasses))
                } else {
                    subNode.mergeInto(globalSubPackage, loadedClasses)
                }
            }
        }
        global.classes.writeFinally {
            library.classes.forEach { (name, subNode) ->
                val classNode = get(name)
                if (classNode == null) {
                    put(name, newClassMap(subNode, global, loadedClasses))
                } else {
                    val asClassNode = subNode.asClassNode(global).also {
                        loadedClasses[it.fullName] = it
                    }
                    classNode.writeFinally {
                        put(subNode.version, asClassNode)
                    }
                }
            }
        }
        return loadedClasses
    }
}

class LibraryClassNode(
    simpleName: String,
    packageNode: LibraryPackageNode,
    val source: ClassByteCodeSource
) : AbstractNode<LibraryPackageNode>(simpleName, packageNode) {

    override val name: String = simpleName

    val version get() = source.location.id

    fun asClassNode(parent: PackageNode) = ClassNode(name, parent, source)
}