package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.PathTrie
import java.util.Collections

/** Candidate lookup preserves the full premise and the member that matched its position. */
class PublicationIndex(private val ap: ApManager, private val ops: ApOps) {
    private data class Member(val premise: PremiseKey, val index: Int)
    private val interners = ap.newInterners()
    private val merged = LinkedHashMap<PremiseKey, ConclusionGroup>()
    private val byMember = PathTrie<Member>(ap.cancellation)
    private data class SupportLeaf(val pattern: Pattern, val layer: Layer)
    /** Only guarded premises need cofactor metadata. Keys are semantic leaf
     * memberships, never historical Facts versions or a second summary store. */
    private val supported = HashMap<PremiseKey, LinkedHashMap<SupportLeaf, GroundingSupport>>()

    fun addAll(pubs: List<Publication>): List<Publication> {
        ap.cancellation.checkpoint()
        val out = ArrayList<Publication>()
        for (pub in pubs) {
            ap.cancellation.checkpoint()
            check(pub.conclusion.base !is AccessPathBase.CleanerTemporary) {
                "A field-action temporary cannot enter a publication"
            }
            check((pub.conclusion is FlowTree) == pub.premise.isFlow) {
                "K6: the conclusion kind follows its premise"
            }
            check(pub.premise !is PremiseSet || pub.conclusion is TaintTree) {
                "A multi-premise publication must be TAINT"
            }
            val previous = merged[pub.premise]
            val supportRows = supported[pub.premise] ?: if (pub.grounding != GroundingSupport.TRUE) {
                LinkedHashMap<SupportLeaf, GroundingSupport>().also { rows ->
                    // These earlier rows had no conditional guard.
                    for (facts in previous?.all().orEmpty()) for (leaf in ops.leaves(facts)) {
                        ap.cancellation.checkpoint()
                        rows[SupportLeaf(leaf, facts.layer)] = GroundingSupport.TRUE
                    }
                    supported[pub.premise] = rows
                }
            } else null
            val group = merged.getOrPut(pub.premise) {
                for (member in 0 until pub.premise.size) {
                    ap.cancellation.checkpoint()
                    val initial = pub.premise.member(member)
                    byMember.add(initial.base, initial.pathData, Member(pub.premise, member))
                }
                ConclusionGroup(ap, interners)
            }
            val delta = group.add(pub.conclusion)
            if (supportRows == null) delta?.let { out += Publication(pub.premise, it) }
            else for (leaf in ops.leaves(pub.conclusion)) {
                ap.cancellation.checkpoint()
                val key = SupportLeaf(leaf, pub.layer)
                val before = supportRows[key]
                val after = before?.or(pub.grounding) ?: pub.grounding
                if (before == after) continue
                supportRows[key] = after
                out += Publication(pub.premise, ap.factsOf(leaf.fact, leaf.exclusion, pub.layer), after)
            }
        }
        return Collections.unmodifiableList(out)
    }

    fun candidates(part: Subscription, mode: ApMode): List<Pair<Publication, Int>> {
        ap.cancellation.checkpoint()
        val members = LinkedHashSet<Member>()
        if (part.zeroOnly) {
            for (member in byMember.lookupExtensions(AccessPathBase.Zero, EMPTY_PATH)) {
                ap.cancellation.checkpoint()
                if (member.premise.isZero) members += member
            }
        } else {
            for (leaf in ops.leaves(part.added)) {
                ap.cancellation.checkpoint()
                val path = leaf.fact.path.toIntArray()
                if (mode.run1) {
                    members += byMember.lookupPrefixes(leaf.fact.base, path)
                    if (leaf.fact.tail != Tail.EXACT) {
                        // A nonexact actual ancestor can supply a requested
                        // component. The complete AP predicate checks its E,
                        // marks and tail after this candidate lookup.
                        members += byMember.lookupExtensions(leaf.fact.base, path)
                    }
                }
                else {
                    members += byMember.lookupExtensions(leaf.fact.base, path)
                    for (member in byMember.lookupPrefixes(leaf.fact.base, path)) {
                        ap.cancellation.checkpoint()
                        if (member.premise.member(member.index).mark is MarkSlot.Star) members += member
                    }
                }
            }
        }
        val out = ArrayList<Pair<Publication, Int>>()
        for (member in members) {
            ap.cancellation.checkpoint()
            for (publication in publications(member.premise)) {
                ap.cancellation.checkpoint()
                out += publication to member.index
            }
        }
        return Collections.unmodifiableList(out)
    }

    /** Borrow the existing publication rows. No duplicate static-summary store. */
    fun staticCandidates(): List<Pair<Publication, Int>> {
        ap.cancellation.checkpoint()
        val out = ArrayList<Pair<Publication, Int>>()
        for (member in byMember.lookupExtensions(STATIC, EMPTY_PATH)) {
            ap.cancellation.checkpoint()
            for (publication in publications(member.premise)) {
                ap.cancellation.checkpoint()
                out += publication to member.index
            }
        }
        return Collections.unmodifiableList(out)
    }

    private fun publications(premise: PremiseKey): List<Publication> {
        val rows = supported[premise]
        return if (rows == null) merged.getValue(premise).all().map { Publication(premise, it) }.toList()
        else rows.map { (leaf, support) ->
            ap.cancellation.checkpoint()
            Publication(premise, ap.factsOf(leaf.pattern.fact, leaf.pattern.exclusion, leaf.layer), support)
        }
    }
}
