package org.opentaint.java.compilation.database.impl.tree

object SubTypesInstallationListener : ClassTreeListener {

    override suspend fun notifyOnByteCodeLoaded(nodeWithLoadedByteCode: ClassNode, classTree: ClassTree) {
        val superClass = nodeWithLoadedByteCode.info().superClass?.takeIf {
            it != "java.lang.Object"
        } ?: return
        classTree.addSubtypeOf(nodeWithLoadedByteCode, superClass)

        nodeWithLoadedByteCode.info().interfaces.forEach {
            classTree.addSubtypeOf(nodeWithLoadedByteCode, it)
        }
    }


    private fun ClassTree.addSubtypeOf(classNodeWithLoadedMeta: ClassNode, className: String) {
        val allNodes = filterClassNodes(className)
        allNodes.forEach {
            it.addSubType(classNodeWithLoadedMeta)
        }
    }
}