package org.opentaint.ir.impl.vfs

import org.opentaint.ir.impl.fs.ClassByteCodeSource

abstract class AbstractClassVfs<PACKAGE_ITEM : AbstractVfsItem<PACKAGE_ITEM>, CLASS_ITEM : AbstractVfsItem<PACKAGE_ITEM>> {

    protected abstract val rootItem: PACKAGE_ITEM

    fun addClass(source: ClassByteCodeSource): CLASS_ITEM {
        val splitted = source.className.splitted
        val simpleClassName = splitted[splitted.size - 1]
        if (splitted.size == 1) {
            return rootItem.findClassOrNew(simpleClassName, source)
        }
        var node = rootItem
        var index = 0
        while (index < splitted.size - 1) {
            val subfolderName = splitted[index]
            node = node.findPackageOrNew(subfolderName)
            index++
        }
        return node.findClassOrNew(simpleClassName, source)
    }

    abstract fun PACKAGE_ITEM.findClassOrNew(simpleClassName: String, source: ClassByteCodeSource): CLASS_ITEM
    abstract fun PACKAGE_ITEM.findPackageOrNew(subfolderName: String): PACKAGE_ITEM

    protected val String.splitted get() = this.split(".")
}