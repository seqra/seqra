package org.opentaint.dataflow.bidi.interp

import java.util.IdentityHashMap

/** Scratch for one optional shape sample. Nothing in this collector survives the sample.
 *  Count owned element/key/value reference cells and retained primitive index cells, not object
 *  headers, view objects, AP/IR payloads, compiled predicate topology or heap bytes. */
internal class FormStorageCounts {
    var statementSummaries = 0L
    var callPlans = 0L
    var ruleStatements = 0L
    var exitRules = 0L
    var ownedContainerEntries = 0L
    var primitiveIndexCells = 0L
    var summaryBaseDirectoryEntries = 0L
    var callPointDirectoryEntries = 0L
    private val seen = IdentityHashMap<Any, Boolean>()
    private val work = ArrayDeque<Any>()

    fun include(value: Any) {
        if (seen.put(value, true) == null) work.addLast(value)
    }

    fun index(value: IntArray?) {
        if (value != null && seen.put(value, true) == null) primitiveIndexCells += value.size.toLong()
    }

    fun measure(form: Any) {
        include(form)
        while (work.isNotEmpty()) {
            val value = work.removeLast()
            if (ownedStorage(value, this)) continue
            when (value) {
                is StatementSummary -> value.storage(this)
                is CallPlan -> value.storage(this)
                is RuleStatement -> {
                    ruleStatements++
                    include(value.summary)
                    include(value.endFacts)
                    include(value.sinks)
                    for (sink in value.sinks) include(sink)
                }
                is ExitRules -> {
                    exitRules++
                    include(value.rules)
                    include(value.entryMarks)
                }
                is SinkRule -> {
                    include(value.patterns)
                    include(value.endFacts)
                }
            }
        }
    }
}
