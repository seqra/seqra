package org.opentaint.dataflow.graph

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.LanguageManager
import java.util.BitSet

inline fun statementFilteredTraverse(
    languageManager: LanguageManager,
    initialStatement: CommonInst,
    next: (CommonInst) -> Sequence<CommonInst>,
    predicate: (CommonInst) -> Boolean,
    body: (CommonInst) -> Unit,
    onSkippedStatement: (CommonInst) -> Unit = {}
) {
    val visitedStatements = BitSet()

    val unprocessed = mutableListOf<CommonInst>()
    unprocessed.addAll(next(initialStatement))

    while (unprocessed.isNotEmpty()) {
        val stmt = unprocessed.removeLast()

        val stmtIdx = languageManager.getInstIndex(stmt)
        if (visitedStatements.get(stmtIdx)) continue
        visitedStatements.set(stmtIdx)

        if (predicate(stmt)) {
            body(stmt)
            continue
        } else {
            onSkippedStatement(stmt)
            unprocessed.addAll(next(stmt))
        }
    }
}
