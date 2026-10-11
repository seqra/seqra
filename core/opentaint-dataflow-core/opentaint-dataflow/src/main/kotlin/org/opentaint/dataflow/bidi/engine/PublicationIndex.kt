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
            val group = merged.getOrPut(pub.premise) {
                for (member in 0 until pub.premise.size) {
                    ap.cancellation.checkpoint()
                    val initial = pub.premise.member(member)
                    byMember.add(initial.base, initial.pathData, Member(pub.premise, member))
                }
                ConclusionGroup(ap, interners)
            }
            group.add(pub.conclusion)?.let { out += Publication(pub.premise, it) }
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
                if (mode.run1) members += byMember.lookupPrefixes(leaf.fact.base, path)
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
            for (facts in merged.getValue(member.premise).all()) {
                ap.cancellation.checkpoint()
                out += Publication(member.premise, facts) to member.index
            }
        }
        return Collections.unmodifiableList(out)
    }
}
