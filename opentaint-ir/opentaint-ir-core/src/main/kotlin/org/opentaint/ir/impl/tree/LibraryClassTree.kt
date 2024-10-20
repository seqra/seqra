package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.fs.ClassByteCodeSource
import java.util.concurrent.ConcurrentHashMap


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

    fun mergeInto(
        global: PackageNode,
        loadedClasses: MutableMap<String, ClassNode> = hashMapOf()
    ): Map<String, ClassNode> {
        val library = this
        library.subpackages.forEach { (name, subNode) ->
            val globalSubPackage = global.subpackages.getOrPut(name) {
                PackageNode(name, global)
            }
            subNode.mergeInto(globalSubPackage, loadedClasses)
        }
        library.classes.forEach { (name, subNode) ->
            val classVersions = global.classes.getOrPut(name) {
                ConcurrentHashMap()
            }
            classVersions[subNode.version] = subNode.asClassNode(global).also {
                loadedClasses[it.fullName] = it
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