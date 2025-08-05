package org.opentaint.dataflow.jvm.ap.ifds.access.cactus

import org.opentaint.dataflow.jvm.ap.ifds.FinalAccessor

object CactusUtils {

    private fun matchAccessPathWithCactus(
        treeNode: AccessCactus.AccessNode,
        pathElement: AccessPathWithCycles.AccessPathElement,
        visited: MutableSet<Pair<AccessCactus.AccessNode, AccessPathWithCycles.AccessPathElement>>,
        onFinalMatch: (AccessCactus.AccessNode) -> Unit,
        onAbstractMatch: (AccessCactus.AccessNode) -> Unit,
    ) {
        val curState = treeNode to pathElement
        if (!visited.add(curState)) {
            return
        }

        val nextElements = pathElement.next
        if (nextElements.isNotEmpty()) {
            nextElements.forEach { (accessor, nextPathElement) ->
                if (accessor is FinalAccessor) {
                    if (treeNode.isFinal) {
                        onFinalMatch(treeNode)
                    }
                } else {
                    val nextNode = treeNode.getChild(accessor) ?: return@forEach
                    matchAccessPathWithCactus(nextNode, nextPathElement, visited, onFinalMatch, onAbstractMatch)
                }
            }
            return
        }
        onAbstractMatch(treeNode)
    }

    fun matchAccessPathWithCactus(
        treeNode: AccessCactus.AccessNode,
        accessPath: AccessPathWithCycles.AccessNode?,
        onFinalMatch: (AccessCactus.AccessNode) -> Unit,
        onAbstractMatch: (AccessCactus.AccessNode) -> Unit,
    ) = matchAccessPathWithCactus(
        treeNode,
        AccessPathWithCycles.AccessPathElement.fromAccessPath(accessPath),
        mutableSetOf(),
        onFinalMatch,
        onAbstractMatch
    )

}