package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.Conclusion
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark

data class TestPosition(val base: AccessPathBase, val path: List<Int>)
data class TestLocation(val base: AccessPathBase, val path: List<Int>, val mark: TaintMark) {
    val position: TestPosition get() = TestPosition(base, path)
}
data class TestLocationPair(val entry: TestLocation, val exit: TestLocation)

/** Finite observation of AP §3.1–§3.2. No production selector, trie or index is called here. */
class BoundedDenotation(
    bases: List<AccessPathBase>,
    accessors: List<Int>,
    val marks: List<TaintMark>,
    maximumPathLength: Int,
) {
    val paths: List<List<Int>> = buildList {
        var level: List<List<Int>> = listOf(emptyList())
        addAll(level)
        repeat(maximumPathLength) {
            level = level.flatMap { prefix -> accessors.map { prefix + it } }
            addAll(level)
        }
    }
    val positions: Set<TestPosition> = bases.flatMap { base -> paths.map { TestPosition(base, it) } }.toSet()
    val locations: Set<TestLocation> = positions.flatMap { p -> marks.map { TestLocation(p.base, p.path, it) } }.toSet()

    private fun suffix(full: List<Int>, prefix: List<Int>): List<Int>? {
        if (full.size < prefix.size) return null
        for (index in prefix.indices) if (full[index] != prefix[index]) return null
        return full.drop(prefix.size)
    }

    private fun excludedFields(exclusion: ExclusionSet): Set<Int> = when (exclusion) {
        ExclusionSet.Empty -> emptySet()
        is ExclusionSet.Concrete -> exclusion.ids.toSet()
    }

    fun admitsContinuation(tail: Tail, exclusion: ExclusionSet, continuation: List<Int>): Boolean = when (tail) {
        Tail.EXACT -> continuation.isEmpty()
        Tail.ANY -> true
        Tail.STAR, Tail.ANY_TAINT -> continuation.isEmpty() || continuation.first() !in excludedFields(exclusion)
    }

    fun admitsMark(slot: MarkSlot, mark: TaintMark): Boolean = when (slot) {
        is MarkSlot.Concrete -> slot.mark == mark
        is MarkSlot.Star -> mark.id !in slot.excluded.ids
    }

    fun markedValues(slot: MarkSlot): Set<TaintMark> = marks.filter { admitsMark(slot, it) }.toSet()

    fun spatial(pattern: Pattern): Set<TestPosition> = positions.filterTo(HashSet()) { position ->
        if (position.base != pattern.fact.base) false
        else suffix(position.path, pattern.fact.path)?.let {
            admitsContinuation(pattern.fact.tail, pattern.exclusion, it)
        } == true
    }

    fun marked(pattern: Pattern): Set<TestLocation> {
        val spatial = spatial(pattern)
        return locations.filterTo(HashSet()) { it.position in spatial && admitsMark(pattern.fact.mark, it.mark) }
    }

    fun containsPair(premise: Pattern, conclusion: Conclusion, pair: TestLocationPair): Boolean {
        if (pair.entry.base != premise.fact.base || pair.exit.base != conclusion.fact.base) return false
        val sigma = suffix(pair.entry.path, premise.fact.path) ?: return false
        val tau = suffix(pair.exit.path, conclusion.fact.path) ?: return false
        if (!admitsContinuation(premise.fact.tail, premise.exclusion, sigma)) return false
        if (!admitsMark(premise.fact.mark, pair.entry.mark)) return false
        val target = conclusion.fact.mark
        val outputMark = when (target) {
            is MarkSlot.Concrete -> target.mark
            is MarkSlot.Star -> {
                if (!admitsMark(target, pair.entry.mark)) return false
                pair.entry.mark
            }
        }
        if (pair.exit.mark != outputMark) return false
        return when (conclusion.fact.tail) {
            Tail.EXACT -> tau.isEmpty()
            Tail.STAR -> tau == sigma && admitsContinuation(Tail.STAR, conclusion.exclusion, tau)
            Tail.ANY -> true
            Tail.ANY_TAINT -> admitsContinuation(Tail.ANY_TAINT, conclusion.exclusion, tau)
        }
    }

    fun pairs(premise: Pattern, conclusion: Conclusion): Set<TestLocationPair> {
        val entries = marked(premise)
        val exits = locations.filter { it.base == conclusion.fact.base }
        return buildSet {
            for (entry in entries) for (exit in exits) {
                val pair = TestLocationPair(entry, exit)
                if (containsPair(premise, conclusion, pair)) add(pair)
            }
        }
    }
}
