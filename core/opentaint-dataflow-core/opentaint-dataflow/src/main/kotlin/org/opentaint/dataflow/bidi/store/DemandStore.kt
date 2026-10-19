package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import java.util.Collections

/** Immutable demand snapshot. Tails, marks, exclusions and exits remain candidate payloads. */
class DemandStore private constructor(
    private val byMethod: Map<MethodKey, PathTrie<DemandPattern>>,
    private val zeroDemand: DemandPattern,
    private val ap: ApManager,
    private val staticGroupsEnabled: Boolean,
) {
    /** Emission tests prefixes and extensions, with the implicit zero demand on the zero base. */
    fun near(method: MethodKey, actual: Pattern): List<DemandPattern> {
        ap.cancellation.checkpoint()
        val stored = byMethod[method]?.around(actual.fact.base, actual.fact.path.toIntArray()).orEmpty()
        return when (actual.fact.base) {
            AccessPathBase.Zero -> stored + zeroDemand
            STATIC -> implicitStaticPair(actual)?.let { stored + it } ?: stored
            else -> stored
        }
    }

    /** Whole-premise restriction needs entry prefixes only. The implicit zero has no exit. */
    fun covering(method: MethodKey, initial: InitialAp): List<DemandPattern> {
        ap.cancellation.checkpoint()
        val stored = byMethod[method]?.lookupPrefixes(initial.base, initial.path?.toIntArray() ?: EMPTY_PATH).orEmpty()
        return implicitStaticPair(initial.toPattern())?.let { stored + it } ?: stored
    }

    /** Guidance only: select the pair from this actual FULL input or entry premise. */
    private fun implicitStaticPair(actual: Pattern): DemandPattern? {
        ap.cancellation.checkpoint()
        if (!staticGroupsEnabled || actual.fact.base != STATIC) return null
        val atom = (actual.fact.mark as? MarkSlot.Concrete)?.mark ?: return null
        if (ap.marks.fullId(atom) == null) return null
        val group = MarkSlot.Concrete(ap.marks.group(ap.marks.groupOf(atom)))
        return DemandPattern(
            Pattern(PathFact(STATIC, emptyList(), Tail.ANY, group), ExclusionSet.Empty),
            Pattern(PathFact(STATIC, emptyList(), Tail.STAR, group), ExclusionSet.Empty),
        )
    }

    class Builder(private val m: ApManager) {
        private val seen = LinkedHashSet<Pair<MethodKey, DemandPattern>>()
        private val zeroDemand = ownDemand(m, DemandPattern(m.zero.toPattern(), null))
        private var staticGroupsEnabled = false

        /** One symbolic demand family. It stores no method-by-field product. */
        fun includeStaticGroups() {
            staticGroupsEnabled = true
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
            return DemandStore(Collections.unmodifiableMap(byMethod), zeroDemand, m, staticGroupsEnabled)
        }
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
