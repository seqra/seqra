package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.RegisteredLocation
import java.util.concurrent.ConcurrentHashMap


/** this class vfs is NOT THREAD SAFE */
class LibraryClassVfs(val location: RegisteredLocation) : AbstractClassVfs<LibraryPackageVfsItem, LibraryClassVfsItem>() {

    override val rootItem = LibraryPackageVfsItem(null, null)

    override fun LibraryPackageVfsItem.findClassOrNew(
        simpleClassName: String,
        source: ClassSource
    ): LibraryClassVfsItem {
        return classes.getOrPut(simpleClassName) {
            LibraryClassVfsItem(simpleClassName, this, source)
        }
    }

    override fun LibraryPackageVfsItem.findPackageOrNew(subfolderName: String): LibraryPackageVfsItem {
        return subpackages.getOrPut(subfolderName) {
            LibraryPackageVfsItem(subfolderName, this)
        }
    }

    fun pushInto(globalClassVFS: GlobalClassesVfs): Map<String, ClassVfsItem> {
        return rootItem.mergeInto(globalClassVFS.rootItem)
    }

}

class LibraryPackageVfsItem(folderName: String?, parent: LibraryPackageVfsItem?) :
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
            classVersions[subNode.locationId] = subNode.asClassNode(global).also {
                loadedClasses[it.fullName] = it
            }
        }
        return loadedClasses
    }
}

class LibraryClassVfsItem(
    simpleName: String,
    packageNode: LibraryPackageVfsItem,
    private val source: ClassSource
) : AbstractVfsItem<LibraryPackageVfsItem>(simpleName, packageNode) {

    override val name: String = simpleName

    val locationId get() = source.location.id

    fun asClassNode(parent: PackageVfsItem) = ClassVfsItem(name, parent, source)
}