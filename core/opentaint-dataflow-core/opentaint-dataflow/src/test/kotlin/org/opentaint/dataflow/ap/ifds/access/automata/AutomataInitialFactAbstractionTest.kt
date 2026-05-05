package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAbstractionTest

class AutomataInitialFactAbstractionTest : InitialFactAbstractionTest() {
    override fun mkApManager(strategy: AnyAccessorUnrollStrategy): ApManager = AutomataApManager(strategy)

    override fun merge(fact: FinalFactAp, vararg facts: FinalFactAp): FinalFactAp {
        check(fact is AccessGraphFinalFactAp)
        return facts.fold(fact) { acc, f ->
            val graph = f as AccessGraphFinalFactAp
            val access = acc.access.merge(graph.access)
            AccessGraphFinalFactAp(fact.base, access, fact.exclusions)
        }
    }
}
