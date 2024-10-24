package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.impl.fs.ClassByteCodeSource
import java.util.concurrent.ConcurrentHashMap


/** this class vfs is NOT THREAD SAFE */
class LibraryClassVfs(val location: JIRByteCodeLocation) : AbstractClassVfs<LibraryPackageVfsItem, LibraryClassVfsItem>() {

    override val rootItem = LibraryPackageVfsItem(null, null, location.hash)

    override fun LibraryPackageVfsItem.findClassOrNew(
        simpleClassName: String,
        source: ClassByteCodeSource
    ): LibraryClassVfsItem {
        return classes.getOrPut(simpleClassName) {
            LibraryClassVfsItem(simpleClassName, this, source)
        }
    }

    override fun LibraryPackageVfsItem.findPackageOrNew(subfolderName: String): LibraryPackageVfsItem {
        return subpackages.getOrPut(subfolderName) {
            LibraryPackageVfsItem(subfolderName, this, location.hash)
        }
    }

    fun pushInto(globalClassVFS: GlobalClassesVfs): Map<String, ClassVfsItem> {
        return rootItem.mergeInto(globalClassVFS.rootItem)
    }

}

class LibraryPackageVfsItem(folderName: String?, parent: LibraryPackageVfsItem?, private val version: String) :
    AbstractVfsItem<LibraryPackageVfsItem>(folderName, parent) {

    // folderName -> subpackage
    internal val subpackages = HashMap<String, LibraryPackageVfsItem>()

    // simpleName -> node
    internal val classes = HashMap<String, LibraryClassVfsItem>()

    fun mergeInto(
        global: PackageVfsItem,
        loadedClasses: MutableMap<String, ClassVfsItem> = hashMapOf()
    ): Map<String, ClassVfsItem> {
        val library = this
        library.subpackages.forEach { (name, subNode) ->
            val globalSubPackage = global.subpackages.getOrPut(name) {
                PackageVfsItem(name, global)
            }
            subNode.mergeInto(globalSubPackage, loadedClasses)
        }
        library.classes.forEach { (name, subNode) ->
            val classVersions = global.classes.getOrPut(name) {
                ConcurrentHashMap()
            }
            classVersions[subNode.hash] = subNode.asClassNode(global).also {
                loadedClasses[it.fullName] = it
            }
        }
        return loadedClasses
    }
}

class LibraryClassVfsItem(
    simpleName: String,
    packageNode: LibraryPackageVfsItem,
    val source: ClassByteCodeSource
) : AbstractVfsItem<LibraryPackageVfsItem>(simpleName, packageNode) {

    override val name: String = simpleName

    val hash get() = source.location.hash

    fun asClassNode(parent: PackageVfsItem) = ClassVfsItem(name, parent, source)
}