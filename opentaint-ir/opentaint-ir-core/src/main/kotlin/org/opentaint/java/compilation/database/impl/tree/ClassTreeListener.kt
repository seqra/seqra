package org.opentaint.java.compilation.database.impl.tree

interface ClassTreeListener {

    /**
     * method called when metaInfo is loaded for node
     * @param classNodeWithLoadedMeta - class node with fully loaded node
     */
    suspend fun notifyOnByteCodeLoaded(classNodeWithLoadedMeta: ClassNode, classTree: ClassTree)

}