package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.bidi.ap.InitialAp

/** Initial keys are manager-interned and have identity equality. */
class InitialFactStore {
    private val initials = LinkedHashSet<InitialAp>()
    fun add(i: InitialAp): Boolean = initials.add(i)
    fun all(): Collection<InitialAp> = initials.toList()
}
