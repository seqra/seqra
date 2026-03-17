package org.opentaint.dataflow.jvm.ap.ifds.alias

interface ImmutableIntDSU {
    fun mutableCopy(): IntDisjointSets
}
