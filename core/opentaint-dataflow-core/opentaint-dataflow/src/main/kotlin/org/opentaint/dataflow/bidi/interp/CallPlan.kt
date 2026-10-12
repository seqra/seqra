package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import java.util.AbstractMap.SimpleImmutableEntry

enum class CallPoint { BEFORE, BOUND, ADDED, RETURNED, REWRITTEN, AFTER }

/** Stage kind selects source places, statement edges and identity-candidate updates. */
enum class StageKind {
    BIND_IN, END_FACTS, SOURCES, UNRESOLVED, CONSTRUCTOR, BIND_BACK, ALIASES, PASS_OVER;

    val statementEdges: Boolean get() = this == SOURCES || this == UNRESOLVED
}

/** Null means no producing stage has classified this result. Other stages preserve that state. */
fun StageKind.identityCandidateOf(me: MicroEdge?, prev: Boolean?): Boolean? = when (this) {
    StageKind.SOURCES, StageKind.END_FACTS -> false
    StageKind.UNRESOLVED -> me?.isIdentity == true
    StageKind.CONSTRUCTOR -> true
    else -> prev
}

/** Forward stage selection. Reversed end facts retain the sink as a trigger, without selecting inputs. */
sealed interface Guard {
    class SinkTriggered(val sink: SinkRule) : Guard

    data object MemoryEffect : Guard {
        fun admits(identityCandidate: Boolean, layer: Layer): Boolean = layer == Layer.DEMAND || !identityCandidate
    }
}

sealed interface CallStage {
    val from: CallPoint
    val to: CallPoint
    fun reversed(): CallStage

    /** A summary is applied in STAGE mode. A backward trigger fires after an end-fact edge gives a result. */
    data class Edges(
        override val from: CallPoint,
        override val to: CallPoint,
        val kind: StageKind,
        val summary: StatementSummary,
        val guard: Guard? = null,
        val trigger: SinkRule? = null,
    ) : CallStage {
        init {
            check(trigger == null || (kind == StageKind.END_FACTS && guard == null)) {
                "A backward sink trigger belongs to an unguarded END_FACTS stage"
            }
        }

        override fun reversed(): Edges = Edges(to, from, kind, summary.reversed(), guard = null,
            trigger = (guard as? Guard.SinkTriggered)?.sink)
    }

    /** Cleaners and the rewriter keep one ordered, immutable action chain. */
    class Actions(override val from: CallPoint, override val to: CallPoint, steps: List<CleanStep>) : CallStage {
        val steps: List<CleanStep> = ownedList(steps)

        override fun reversed(): Actions = Actions(to, from, steps.asReversed().map { it.reversed() })

        override fun equals(other: Any?): Boolean = this === other ||
            (other is Actions && from == other.from && to == other.to && steps == other.steps)

        override fun hashCode(): Int = (from.hashCode() * 31 + to.hashCode()) * 31 + steps.hashCode()
    }

    /** Callee keys remain the same objects when the graph reverses. */
    class Callees(override val from: CallPoint, override val to: CallPoint, callees: List<MethodKey>) : CallStage {
        val callees: List<MethodKey> = ownedList(callees)

        override fun reversed(): Callees = Callees(to, from, callees)

        override fun equals(other: Any?): Boolean = this === other ||
            (other is Callees && from == other.from && to == other.to && callees == other.callees)

        override fun hashCode(): Int = (from.hashCode() * 31 + to.hashCode()) * 31 + callees.hashCode()
    }
}

/** Cached call graph. passReads is observer metadata and does not affect propagation. */
class CallPlan(
    touched: Set<AccessPathBase>,
    stages: List<CallStage>,
    sinks: List<SinkRule>,
    val entry: CallPoint,
    val exit: CallPoint,
    passReads: Set<AccessPathBase> = emptySet(),
) {
    val touched: Set<AccessPathBase> = ownedSet(touched)
    val stages: List<CallStage> = ownedList(stages)
    val sinks: List<SinkRule> = ownedList(sinks)
    val passReads: Set<AccessPathBase> = ownedSet(passReads)

    /** Every stage from a point reads the same input facts. */
    val stagesFrom: Map<CallPoint, List<CallStage>> = pointDirectory(this.stages)

    fun stagesAt(point: CallPoint): List<CallStage> = stagesFrom[point].orEmpty()

    fun reversed(): CallPlan {
        check(entry == CallPoint.BEFORE && exit == CallPoint.AFTER) { "The core reverses only a forward plan" }
        val exitTargets = stages.filter { it.to == exit }.filterIsInstance<CallStage.Edges>()
            .flatMap { it.summary.targets }
        val aliasBases = passOverBases(exitTargets, touched)
        val reversed = stages.mapTo(ArrayList()) { it.reversed() }
        if (aliasBases.isNotEmpty()) {
            reversed += CallStage.Edges(CallPoint.AFTER, CallPoint.BEFORE, StageKind.PASS_OVER,
                StatementSummary.identities(aliasBases))
        }
        return CallPlan(touched + aliasBases, reversed, sinks, entry = exit, exit = entry, passReads = passReads)
    }
}

private fun pointDirectory(stages: List<CallStage>): Map<CallPoint, List<CallStage>> {
    val groups = ownedOrdinalGroups(stages) { it.from }
    return if (groups.size <= 1) groups else CallPointDirectory(groups)
}

/** Fixed six-point lookup; compatibility iteration keeps the first stage's point order. */
private class CallPointDirectory(groups: Map<CallPoint, List<CallStage>>) : AbstractMap<CallPoint, List<CallStage>>() {
    override val size: Int = groups.size
    private val pointOrder = IntArray(size)
    private val byOrdinal = arrayOfNulls<List<CallStage>>(CallPoint.entries.size)
    @Volatile private var entryView: Set<Map.Entry<CallPoint, List<CallStage>>>? = null

    init {
        var index = 0
        for ((point, stages) in groups) {
            pointOrder[index++] = point.ordinal
            byOrdinal[point.ordinal] = stages
        }
    }

    override fun get(key: CallPoint): List<CallStage>? = byOrdinal[key.ordinal]
    override fun containsKey(key: CallPoint): Boolean = byOrdinal[key.ordinal] != null

    override val entries: Set<Map.Entry<CallPoint, List<CallStage>>>
        get() = entryView ?: object : AbstractSet<Map.Entry<CallPoint, List<CallStage>>>() {
            override val size: Int get() = this@CallPointDirectory.size
            override fun iterator(): Iterator<Map.Entry<CallPoint, List<CallStage>>> =
                object : Iterator<Map.Entry<CallPoint, List<CallStage>>> {
                    private var next = 0
                    override fun hasNext(): Boolean = next < size
                    override fun next(): Map.Entry<CallPoint, List<CallStage>> {
                        if (!hasNext()) throw NoSuchElementException()
                        val ordinal = pointOrder[next++]
                        return SimpleImmutableEntry(CallPoint.entries[ordinal], checkNotNull(byOrdinal[ordinal]))
                    }
                }
        }.also { entryView = it }
}
