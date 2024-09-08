package org.opentaint.java.compilation.database.impl.tree

object SubTypesInstallationListener : ClassTreeListener {

    override suspend fun notifyOnMetaLoaded(classNodeWithLoadedMeta: ClassNode, classTree: ClassTree) {
        val superClass = classNodeWithLoadedMeta.info().superClass?.takeIf {
            it != "java.lang.Object"
        } ?: return
        classTree.addSubtypeOf(classNodeWithLoadedMeta, superClass)

        classNodeWithLoadedMeta.info().interfaces.forEach {
            classTree.addSubtypeOf(classNodeWithLoadedMeta, it)
        }
    }


    private fun ClassTree.addSubtypeOf(classNodeWithLoadedMeta: ClassNode, className: String){
        val allNodes = filterClassNodes(className)
        allNodes.forEach {
            it.addSubType(classNodeWithLoadedMeta)
        }
    }
}