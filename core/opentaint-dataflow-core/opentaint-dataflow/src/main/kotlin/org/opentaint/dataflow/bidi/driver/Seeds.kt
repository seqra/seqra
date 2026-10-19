package org.opentaint.dataflow.bidi.driver

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.store.RuleId
import org.opentaint.dataflow.bidi.interp.RuleOccurrenceId
import org.opentaint.dataflow.bidi.interp.ActionId
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.Collections

/** Persistent hand-off seeds retain their context and original statement. */
sealed interface Seed {
    val method: MethodKey
    val statement: CommonInst

    /** One full-condition handle; it holds no compiled predicate or leaf inventory. */
    data class PredicateSink(
        val occurrence: RuleOccurrenceId,
        override val method: MethodKey,
        override val statement: CommonInst,
    ) : Seed

    class Sink(
        val rule: RuleId,
        override val method: MethodKey,
        override val statement: CommonInst,
        requirement: Pattern,
    ) : Seed {
        init {
            check(requirement.fact.base !is AccessPathBase.CleanerTemporary) {
                "A field-action temporary cannot enter a sink seed"
            }
        }
        val requirement: Pattern = requirement.copy(fact = requirement.fact.copy(
            path = Collections.unmodifiableList(ArrayList(requirement.fact.path))))

        override fun equals(other: Any?): Boolean = this === other ||
            (other is Sink && rule == other.rule && method == other.method && statement == other.statement &&
                requirement == other.requirement)

        override fun hashCode(): Int =
            ((rule.hashCode() * 31 + method.hashCode()) * 31 + statement.hashCode()) * 31 + requirement.hashCode()
    }

    /** The producer owns edge paths; preserve the supplied forward edge for later source selection. */
    data class Source(override val method: MethodKey, override val statement: CommonInst,
                      val edge: PathEdge, val action: ActionId? = null) : Seed {
        init {
            check(edge.from.base !is AccessPathBase.CleanerTemporary && edge.to.base !is AccessPathBase.CleanerTemporary) {
                "A field-action temporary cannot enter a source seed"
            }
        }
    }
}

/** Seed entries keep order and multiplicity; source admission compares the complete forward edge. */
class SeedIndex(byPlace: Map<Pair<MethodKey, CommonInst>, List<Seed>>) {
    private val byPlace: Map<Pair<MethodKey, CommonInst>, List<Seed>> = Collections.unmodifiableMap(
        LinkedHashMap<Pair<MethodKey, CommonInst>, List<Seed>>().apply {
            for ((place, seeds) in byPlace) put(place, Collections.unmodifiableList(ArrayList(seeds)))
        })
    private val sources: Set<Seed.Source> =
        this.byPlace.values.asSequence().flatten().filterIsInstance<Seed.Source>()
            .toSet()

    fun at(method: MethodKey, statement: CommonInst): List<Seed> = byPlace[method to statement].orEmpty()
    fun allowsSource(method: MethodKey, statement: CommonInst, forward: PathEdge, action: ActionId? = null): Boolean =
        Seed.Source(method, statement, forward, action) in sources
    val size: Int get() = byPlace.values.sumOf { it.size }

    fun sinkMethods(): Set<MethodKey> = byPlace.values.asSequence().flatten()
        .filter { it is Seed.Sink || it is Seed.PredicateSink }
        .mapTo(HashSet()) { it.method }

    companion object {
        val EMPTY = SeedIndex(emptyMap())
        fun of(seeds: List<Seed>): SeedIndex = SeedIndex(seeds.groupBy { it.method to it.statement })
    }
}
