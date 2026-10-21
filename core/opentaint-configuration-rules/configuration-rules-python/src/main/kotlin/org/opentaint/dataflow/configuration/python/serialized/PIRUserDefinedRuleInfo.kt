package org.opentaint.dataflow.configuration.python.serialized

interface ItemInfo

interface PIRUserDefinedRuleInfo : ItemInfo {
    val relevantTaintMarks: Set<String>
}
