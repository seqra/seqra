package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.bidi.ap.CleanReach
import org.opentaint.dataflow.bidi.ap.Cleaner
import org.opentaint.dataflow.bidi.ap.ConjunctiveEdge
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.ap.TypeFilter

/** Build forward forms in insertion order. Supplied edges and conjunctions retain their identities. */
class MicroEdgeBuilder {
    private val touched = LinkedHashSet<AccessPathBase>()
    private val edges = LinkedHashSet<PathEdge>()
    private val conjunctions = LinkedHashSet<ConjunctiveEdge>()
    private val operand = LinkedHashMap<AccessPathBase, TypeFilter>()
    private val result = LinkedHashMap<AccessPathBase, TypeFilter>()

    fun touch(b: AccessPathBase) { touched += b }
    fun edge(e: PathEdge) { edges += e }
    fun conjunction(c: ConjunctiveEdge) { conjunctions += c }

    fun keep(b: AccessPathBase) {
        touch(b)
        edge(keepEdge(b))
    }

    fun keepZero() {
        touch(AccessPathBase.Zero)
        edge(ZERO_KEEP)
    }

    fun operandFilter(b: AccessPathBase, f: TypeFilter?) {
        if (f != null) operand.merge(b, f) { x, y -> x.and(y) }
    }

    fun resultFilter(b: AccessPathBase, f: TypeFilter?) {
        if (f != null) result.merge(b, f) { x, y -> x.and(y) }
    }

    /** A missing source kills the target. A self move retains one identity edge. */
    fun move(to: AccessPathBase, from: AccessPathBase?, toPath: List<AccessorIdx> = emptyList()) {
        touch(to)
        if (from == null) return
        keep(from)
        if (from != to || toPath.isNotEmpty()) edge(starEdge(from, emptyList(), to, toPath))
    }

    /** A read retains its source, except when the read replaces that same base. It adds no exclusion. */
    fun read(to: AccessPathBase, base: AccessPathBase, path: List<AccessorIdx>) {
        touch(to)
        touch(base)
        if (base != to) keep(base)
        edge(starEdge(base, path, to, emptyList()))
    }

    /** Strong writes keep proper prefixes. Alias targets get value edges and remain untouched by the write. */
    fun write(
        base: AccessPathBase,
        path: List<AccessorIdx>,
        weak: Boolean,
        values: List<AccessPathBase>,
        aliasPaths: List<Pair<AccessPathBase, List<AccessorIdx>>>,
    ) {
        touch(base)
        if (weak) keep(base) else strongKeep(base, path).forEach(::edge)
        for (v in values) {
            if (v != base) keep(v) else touch(v)
            edge(starEdge(v, emptyList(), base, path))
        }
        for ((c, q) in aliasPaths) {
            if (c == base) continue
            for (v in values) edge(starEdge(v, emptyList(), c, q + path))
        }
    }

    /** Statement forms retain touched bases, conjunctions, and both filter maps. */
    fun build(): StatementSummary = StatementSummary(touched, edges.map(MicroEdge::of),
        conjunctions.toList(), operand, result)

    /** Every plain or conjunctive endpoint belongs to the stage's touched set. */
    fun buildStage(): StatementSummary {
        edges.forEach { touch(it.from.base); touch(it.to.base) }
        conjunctions.forEach { c ->
            c.literals.forEach { touch(it.fact.base) }
            touch(c.target.base)
        }
        return build()
    }

    /** GEN uses plain edges only; its caller retains the input. */
    fun buildGen(): StatementSummary = StatementSummary(emptySet(), edges.map(MicroEdge::of), emptyList(), emptyMap())
}

/** A named field cleaner is an ordinary read, one temporary cleaner, and an ordinary strong write. */
fun fieldCleanStep(
    base: AccessPathBase,
    path: List<AccessorIdx>,
    reach: CleanReach,
    mark: TaintMark?,
    baseFilter: TypeFilter? = null,
    valueFilter: TypeFilter? = null,
    aliasPaths: List<Pair<AccessPathBase, List<AccessorIdx>>> = emptyList(),
): CleanStep.Field {
    check(base != AccessPathBase.ClassStatic && path.isNotEmpty() && path.last().isFieldAccessor())
    val tmp = AccessPathBase.CleanerTemporary()
    val read = MicroEdgeBuilder().apply {
        read(tmp, base, path)
        operandFilter(base, baseFilter)
        resultFilter(tmp, valueFilter)
    }.build()
    val write = MicroEdgeBuilder().apply {
        write(base, path, weak = false, values = listOf(tmp), aliasPaths = aliasPaths)
        operandFilter(base, baseFilter)
        operandFilter(tmp, valueFilter)
    }.build()
    return CleanStep.Field(tmp, read, Cleaner(tmp, null, reach, mark), write)
}
