package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*

/** A supplier needs only root/class/field lookups. It never expands a leaf's concrete marks. */
internal class StaticSupplierCursor(
    facts: Facts,
    private val profile: Run1StaticProfile,
    private val ap: ApManager,
    private val positions: Boolean,
) {
    private val root: TrieNode<*> = when (facts) {
        is FlowTree -> facts.root
        is TaintTree -> facts.root
        is Reach -> error("A static supplier cannot be zero")
    }
    private var classOffset = 0
    private var fieldOffset = 0
    private var atClass: TrieNode<*>? = null
    private var classAccessor = 0
    private var selected: Iterator<StaticMarkInterest>? = if (positions) null else
        if (wide(root)) profile.markInterests.iterator() else profile.markInterestsAt(emptyList()).iterator()
    private var selectedClass: Int? = null
    private var allSelected = !positions && wide(root)
    var isComplete: Boolean = false
        private set

    private fun wide(node: TrieNode<*>): Boolean = when (val leaf = node.leaf) {
        is FlowLeaf -> !leaf.isEmpty
        is TaintLeaves -> !leaf.any.isEmpty
        else -> error("Unexpected static supplier leaf")
    }

    fun step(budget: Int, position: (List<Int>) -> Unit, interest: (StaticMarkInterest) -> Unit): Int {
        require(budget > 0)
        var used = 0
        while (used < budget && !isComplete) {
            ap.cancellation.checkpoint()
            val rows = selected
            if (rows != null) {
                if (rows.hasNext()) {
                    val row = rows.next()
                    if (selectedClass == null || row.slot.firstOrNull() == selectedClass) interest(row)
                    used++
                    continue
                }
                selected = null
                if (allSelected) { isComplete = true; continue }
                if (selectedClass != null) { selectedClass = null; atClass = null }
            }
            val cls = atClass
            if (cls != null) {
                val fields = cls.accessorData
                if (fields != null && fieldOffset < fields.size) {
                    val slot = listOf(classAccessor, fields[fieldOffset++])
                    if (positions) {
                        if (profile.containsAbstraction(slot)) position(slot)
                    } else selected = profile.markInterestsAt(slot).iterator()
                    used++
                    continue
                }
                atClass = null
            }
            val classes = root.accessorData
            if (classes == null || classOffset == classes.size) { isComplete = true; continue }
            classAccessor = classes[classOffset]
            atClass = root.childData!![classOffset++]
            fieldOffset = 0
            val slot = listOf(classAccessor)
            if (positions) {
                if (profile.containsAbstraction(slot)) position(slot)
            } else if (wide(checkNotNull(atClass))) {
                selectedClass = classAccessor
                selected = profile.markInterests.iterator()
            } else selected = profile.markInterestsAt(slot).iterator()
            used++
        }
        return used
    }
}
