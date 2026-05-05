package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAbstractionTest

class TreeInitialFactAbstractionTest : InitialFactAbstractionTest() {
    override fun mkApManager(strategy: AnyAccessorUnrollStrategy): ApManager = TreeApManager(strategy)

    override fun merge(fact: FinalFactAp, vararg facts: FinalFactAp): FinalFactAp {
        check(fact is AccessTree)
        return facts.fold(fact) { acc, f ->
            val tree = f as AccessTree
            val access = acc.access.mergeAdd(tree.access)
            AccessTree(fact.apManager, fact.base, access, fact.exclusions)
        }
    }
}
