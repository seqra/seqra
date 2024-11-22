
package org.opentaint.ir.impl.vfs

interface ClassTreeListener {

    /**
     * method called when bytecode is read from file
     * @param nodeWithLoadedByteCode - class node with fully loaded node
     */
    suspend fun notifyOnByteCodeLoaded(nodeWithLoadedByteCode: ClassVfsItem, globalClassVFS: GlobalClassesVfs)

}