package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ZERO_PATTERN

/** Scalar oracle: sort decoded values after a complete binding. No production premise policy,
 * InitialAp factory, union/interner, or executable support-set object supplies the answer. */
internal class RawRun1Representative(private val manager: ApManager) {
    private data class AccessorKey(val kind: Int, val values: List<String>)
    private data class Key(val category: Int, val baseKind: Int, val index: Int,
                           val baseValues: List<String>, val path: List<AccessorKey>,
                           val tail: Int, val markKind: Int, val markName: String)

    fun choose(inputs: Iterable<Set<Pattern>>): Set<Pattern> {
        val bindings = inputs.toList()
        check(bindings.isNotEmpty())
        check(bindings.all { it.size == 1 }) { "An executable run-1 input is atomic" }
        val candidates = bindings.map { it.single() }.filter { it.fact.base != AccessPathBase.Zero }
        if (candidates.isEmpty()) return setOf(ZERO_PATTERN)
        val ranked = candidates.map { it to key(it) }
        val selected = ranked.sortedWith { a, b -> compare(a.second, b.second) }.first().first
        return setOf(selected)
    }

    private fun key(p: Pattern): Key {
        check(p.exclusion == ExclusionSet.Empty)
        val f = p.fact
        val base = f.base
        val category = when (base) {
            is AccessPathBase.Argument -> 0
            AccessPathBase.This -> 1
            AccessPathBase.ClassStatic -> if (f.path.size >= 2) 3 else 4
            else -> 2
        }
        val kind = when (base) {
            is AccessPathBase.Argument -> 0
            AccessPathBase.This -> 1
            is AccessPathBase.LocalVar -> 2
            AccessPathBase.Return -> 3
            AccessPathBase.Exception -> 4
            is AccessPathBase.Constant -> 5
            AccessPathBase.ClassStatic -> 6
            AccessPathBase.Zero -> 7
            is AccessPathBase.CleanerTemporary -> error("No temporary original")
        }
        val index = when (base) {
            is AccessPathBase.Argument -> base.idx
            is AccessPathBase.LocalVar -> base.idx
            else -> 0
        }
        val baseValues = if (base is AccessPathBase.Constant) listOf(base.typeName, base.value) else emptyList()
        val path = f.path.map { id -> when (val a = manager.accessors.accessor(id)) {
            is ClassStaticAccessor -> AccessorKey(0, listOf(a.typeName))
            is FieldAccessor -> AccessorKey(1, listOf(a.className, a.fieldName, a.fieldType))
            ElementAccessor -> AccessorKey(2, emptyList())
            else -> error("No non-path accessor")
        } }
        val tail = when (f.tail) { Tail.EXACT -> 0; Tail.STAR -> 1; Tail.ANY -> 2; Tail.ANY_TAINT -> 3 }
        val markKind = when (val mark = f.mark) {
            is MarkSlot.Star -> 0
            is MarkSlot.Concrete -> when (manager.marks.key(mark.mark)) {
                MarkAtomKey.ControlZero -> 1
                is MarkAtomKey.Group -> 2
                is MarkAtomKey.Full -> 3
            }
        }
        val name = (f.mark as? MarkSlot.Concrete)?.let { manager.marks.name(it.mark) }.orEmpty()
        return Key(category, kind, index, baseValues, path, tail, markKind, name)
    }

    private fun <T> compareList(a: List<T>, b: List<T>, compare: (T, T) -> Int): Int {
        for ((x, y) in a.zip(b)) {
            val result = compare(x, y)
            if (result != 0) return result
        }
        return a.size.compareTo(b.size)
    }

    private fun compare(a: Key, b: Key): Int {
        val base = compareValuesBy(a, b, Key::category, Key::baseKind, Key::index)
        if (base != 0) return base
        val values = compareList(a.baseValues, b.baseValues, String::compareTo)
        if (values != 0) return values
        val path = compareList(a.path, b.path) { x, y ->
            val kind = x.kind.compareTo(y.kind)
            if (kind != 0) kind else compareList(x.values, y.values, String::compareTo)
        }
        if (path != 0) return path
        return compareValuesBy(a, b, Key::tail, Key::markKind, Key::markName)
    }
}
