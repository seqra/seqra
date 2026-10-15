package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.ApMode
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PremiseKey
import org.opentaint.dataflow.bidi.ap.members
import org.opentaint.dataflow.bidi.engine.RunMethodAnalyzer
import org.opentaint.dataflow.bidi.engine.RunResult
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.engine.SharedObjects
import org.opentaint.dataflow.bidi.store.Link
import org.opentaint.dataflow.bidi.store.confirmableMember
import org.opentaint.dataflow.bidi.store.supplies
import org.opentaint.ir.api.common.cfg.CommonInst

/** Historical raw-history support oracle. The current driver deliberately does not confirm candidates this way. */
class HistoricalSupport(input: HistoricalSupportInput, roots: List<MethodKey>,
              private val mode: ApMode, private val shared: SharedObjects) {
    private val result = input.result
    private data class Site(val callee: MethodKey, val caller: MethodKey, val call: CommonInst)
    private val roots = roots.toHashSet()
    private val ap = shared.ap
    private val atSite = HashMap<Site, MutableList<Link>>()
    private val sitesOf = HashMap<MethodKey, MutableSet<Site>>()
    private val fedBy = HashMap<Pair<MethodKey, PremiseKey>, MutableSet<Site>>()
    private val questions = HashMap<MethodKey, MutableSet<PremiseKey>>()
    private val supported = HashSet<Pair<MethodKey, PremiseKey>>()

    init {
        check(result.status == RunStatus.COMPLETE && result.direction == Direction.FORWARD &&
            mode.direction == Direction.FORWARD) { "Support needs a complete forward run" }
        shared.cancellation.checkpoint()
        for (callee in result.analyzers) {
            shared.cancellation.checkpoint()
            for (link in input.links[callee.key].orEmpty()) {
                shared.cancellation.checkpoint()
                val ref = link.caller
                if (link.linkLayer != Layer.NORMAL || ref.callerLayer != Layer.NORMAL) continue
                val site = Site(callee.key, ref.caller, ref.call)
                atSite.getOrPut(site, ::ArrayList) += link
                sitesOf.getOrPut(callee.key, ::HashSet) += site
                fedBy.getOrPut(ref.caller to ref.premise, ::HashSet) += site
                questions.getOrPut(ref.caller, ::HashSet) += ref.premise
            }
        }
        val work = ArrayDeque<Pair<MethodKey, PremiseKey>>()
        for (root in this.roots) {
            shared.cancellation.checkpoint()
            if (supported.add(root to ap.zero)) work += root to ap.zero
        }
        while (work.isNotEmpty()) {
            shared.cancellation.checkpoint()
            val known = work.removeFirst()
            for (site in fedBy[known].orEmpty()) {
                shared.cancellation.checkpoint()
                for (premise in questions[site.callee].orEmpty()) {
                    shared.cancellation.checkpoint()
                    if ((site.callee to premise) !in supported && suppliedAt(site, premise.members)) {
                        supported += site.callee to premise
                        work += site.callee to premise
                    }
                }
            }
        }
    }

    private fun suppliedAt(site: Site, members: Collection<InitialAp>): Boolean = members.all { member ->
        shared.cancellation.checkpoint()
        confirmableMember(member, mode) && atSite[site].orEmpty().any { link ->
            shared.cancellation.checkpoint()
            supplies(member, link, mode) && (site.caller to link.caller.premise) in supported
        }
    }

    fun isSupported(method: MethodKey, members: Collection<InitialAp>): Boolean {
        require(members.isNotEmpty()) { "A support premise set must be nonempty" }
        shared.cancellation.checkpoint()
        if (method in roots && members.all { shared.cancellation.checkpoint(); it.isZero }) return true
        return sitesOf[method].orEmpty().any { site ->
            shared.cancellation.checkpoint()
            suppliedAt(site, members)
        }
    }

    fun confirm() {
        shared.cancellation.checkpoint()
        for ((_, witness) in result.vulnerabilities.witnessesOf(result.runIndex)) {
            shared.cancellation.checkpoint()
            witness.confirmed = witness.edges.all {
                shared.cancellation.checkpoint()
                it.layer == Layer.NORMAL
            } && isSupported(witness.methodKey, witness.supportPremise(ap).members)
        }
    }
}
