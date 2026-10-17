package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ownedSet
import org.opentaint.dataflow.bidi.interp.ownedMap

/** Structural metadata survives request-plan release. It retains no mark memberships or forms. */
class Run1StaticFootprint(
    fieldSlots: Set<Run1Slot.StaticField>,
    val allStatic: Boolean = false,
    wideClasses: Set<Int> = emptySet(),
    fieldUniverse: Set<Run1Slot.StaticField> = fieldSlots,
    classPoints: Set<Int> = emptySet(),
    classUniverse: Set<Int> = fieldUniverse.mapTo(LinkedHashSet()) { it.cls } + classPoints,
) {
    val fieldSlots: Set<Run1Slot.StaticField> = ownedSet(fieldSlots)
    val fieldUniverse: Set<Run1Slot.StaticField> = ownedSet(fieldUniverse)
    val wideClasses: Set<Int> = ownedSet(wideClasses)
    val classPoints: Set<Int> = ownedSet(classPoints)
    val classUniverse: Set<Int> = ownedSet(classUniverse)
    val fieldsByClass: Map<Int, Set<Int>> = ownedMap(fieldSlots.groupBy { it.cls }
        .mapValues { (_, slots) -> ownedSet(slots.map { it.field }) })
    val affectedClasses: Set<Int> = ownedSet(fieldsByClass.keys + wideClasses + classPoints)
    fun owns(cls: Int, field: Int): Boolean = allStatic || cls in wideClasses || Run1Slot.StaticField(cls, field) in fieldSlots
    fun ownsPoint(cls: Int): Boolean = allStatic || cls in wideClasses || cls in classPoints

    /** Request-side structural supply ignores E. Ordinary summary projection must retain it. */
    fun selectedFields(input: Facts, checkpoint: () -> Unit = {}): Set<Run1Slot.StaticField> =
        suppliedFields(input, checkpoint, if (allStatic || wideClasses.isNotEmpty()) fieldUniverse else fieldSlots, ::owns)

    /** Actual geometric supply, independently selected for F or a symbolic wide mark scope. */
    internal fun suppliedFields(input: Facts, checkpoint: () -> Unit,
                                candidates: Set<Run1Slot.StaticField> = fieldUniverse,
                                select: (Int, Int) -> Boolean): Set<Run1Slot.StaticField> = buildSet {
        if (input.base != STATIC) return@buildSet
        val root: TrieNode<*> = when (input) {
            is FlowTree -> input.root
            is TaintTree -> input.root
            is Reach -> return@buildSet
        }
        fun fromUniverse(cls: Int? = null) {
            for (slot in candidates) {
                checkpoint()
                if ((cls == null || cls == slot.cls) && select(slot.cls, slot.field)) add(slot)
            }
        }
        if (root.hasRequestBroadLeaf()) { fromUniverse(); return@buildSet }
        root.requestChildren(checkpoint) { cls, node ->
            if (node.hasRequestBroadLeaf()) fromUniverse(cls)
            else node.requestChildren(checkpoint) { field, child ->
                if (!child.isEmpty) {
                    val slot = Run1Slot.StaticField(cls, field)
                    check(slot in fieldUniverse) { "Static input field is absent from the frozen prescan universe: $slot" }
                    if (select(cls, field)) add(slot)
                }
            }
        }
    }
}
