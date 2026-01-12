package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.configuration.jvm.serialized.ItemInfo

interface UserDefinedRuleInfo: ItemInfo {
    val relevantTaintMarks: Set<String>
}
