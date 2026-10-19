package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import java.util.Collections

/** Immutable demand snapshot. Tails, marks, exclusions and exits remain candidate payloads. */
class DemandStore private constructor(
    private val byMethod: Map<MethodKey, PathTrie<DemandPattern>>,
    private val zeroDemand: DemandPattern,
    private val sharedStatics: SharedStaticGroupDemand?,
) {
    /** Emission tests prefixes and extensions, with the implicit zero demand on the zero base. */
    fun near(method: MethodKey, base: AccessPathBase, path: PathNode?): List<DemandPattern> {
        val stored = byMethod[method]?.around(base, path?.toIntArray() ?: EMPTY_PATH).orEmpty()
        return when (base) {
            AccessPathBase.Zero -> stored + zeroDemand
            STATIC -> stored + sharedStatics?.patterns().orEmpty()
            else -> stored
        }
    }

    /** Whole-premise restriction needs entry prefixes only. The implicit zero has no exit. */
    fun covering(method: MethodKey, base: AccessPathBase, path: PathNode?): List<DemandPattern> {
        val stored = byMethod[method]?.lookupPrefixes(base, path?.toIntArray() ?: EMPTY_PATH).orEmpty()
        return if (base == STATIC) stored + sharedStatics?.patterns().orEmpty() else stored
    }

    class Builder(private val m: ApManager) {
        private val seen = LinkedHashSet<Pair<MethodKey, DemandPattern>>()
        private val zeroDemand = ownDemand(m, DemandPattern(m.zero.toPattern(), null))
        private var sharedStatics: SharedStaticGroupDemand? = null

        /** One symbolic demand family. It stores no method-by-field product. */
        fun includeStaticGroups() {
            sharedStatics = SharedStaticGroupDemand(m)
        }

        /** Count only a new exact normalized pattern. Validate the typed hand-off boundary. */
        fun add(method: MethodKey, d: DemandPattern): Boolean {
            m.cancellation.checkpoint()
            val entry = d.entry
            val exit = d.exit
            check(entry.fact.base !is AccessPathBase.CleanerTemporary && exit?.fact?.base !is AccessPathBase.CleanerTemporary) {
                "A field-action temporary cannot enter a demand hand-off"
            }
            check(entry.fact.tail != Tail.ANY_TAINT && exit?.fact?.tail != Tail.ANY_TAINT) {
                "W8 (d): a demand pattern cannot have a must tail"
            }
            check(normalizedMarks(entry) && (exit == null || normalizedMarks(exit))) {
                "Normalize mark exclusions at hand-off"
            }
            check(entry.fact.tail != Tail.STAR) { "A canonical demand entry has a non-STAR tail" }
            check(entry.fact.mark !is MarkSlot.Star || entry.fact.tail == Tail.ANY) {
                "A canonical abstract demand entry has an ANY tail"
            }
            check(exit?.fact?.tail != Tail.EXACT || entry.fact.mark is MarkSlot.Concrete) {
                "A canonical exact exit demand has a concrete entry mark"
            }
            check(validFieldExclusion(entry) && (exit == null || validFieldExclusion(exit))) {
                "Only a STAR demand exit can exclude fields"
            }
            val owned = ownDemand(m, d)
            return owned != zeroDemand && seen.add(method to owned)
        }

        /** The new index has no mutable alias in this builder or in earlier snapshots. */
        fun build(): DemandStore {
            m.cancellation.checkpoint()
            val byMethod = LinkedHashMap<MethodKey, PathTrie<DemandPattern>>()
            for ((method, demand) in seen) {
                m.cancellation.checkpoint()
                byMethod.getOrPut(method) { PathTrie(m.cancellation) }
                    .add(demand.entry.fact.base, demand.entry.fact.path.toIntArray(), demand)
            }
            return DemandStore(Collections.unmodifiableMap(byMethod), zeroDemand, sharedStatics)
        }
    }
}

/** Guidance only. This view is never published as an executable summary or initial. */
private class SharedStaticGroupDemand(private val m: ApManager) {
    private val patternsByGroup = LinkedHashMap<TaintMark, DemandPattern>()

    @Synchronized fun patterns(): List<DemandPattern> {
        m.cancellation.checkpoint()
        for (atom in m.marks.groupAtoms()) patternsByGroup.getOrPut(atom) {
            val mark = MarkSlot.Concrete(atom)
            DemandPattern(
                Pattern(PathFact(STATIC, emptyList(), Tail.ANY, mark), ExclusionSet.Empty),
                Pattern(PathFact(STATIC, emptyList(), Tail.STAR, mark), ExclusionSet.Empty),
            )
        }
        return patternsByGroup.values.toList()
    }
}

private fun normalizedMarks(p: Pattern): Boolean =
    (p.fact.mark as? MarkSlot.Star)?.excluded?.isEmpty != false

private fun validFieldExclusion(p: Pattern): Boolean =
    p.exclusion == ExclusionSet.Empty || p.fact.tail == Tail.STAR

private fun ownDemand(m: ApManager, d: DemandPattern): DemandPattern =
    DemandPattern(ownPattern(m, d.entry), d.exit?.let { ownPattern(m, it) })

private fun ownPattern(m: ApManager, p: Pattern): Pattern {
    m.cancellation.checkpoint()
    val path = ArrayList<Int>(p.fact.path.size)
    for (accessor in p.fact.path) {
        m.cancellation.checkpoint()
        path += accessor
    }
    return p.copy(fact = p.fact.copy(path = Collections.unmodifiableList(path)))
}
