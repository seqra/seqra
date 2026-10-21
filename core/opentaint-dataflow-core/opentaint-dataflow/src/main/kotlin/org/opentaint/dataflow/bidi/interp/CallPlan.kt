package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.GroupId
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MarkTable
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

private data class ReturnGroupKey(val base: AccessPathBase, val group: GroupId)

private fun returnGroupKey(step: CleanStep, marks: MarkTable): ReturnGroupKey? {
    val cleaner = (step as? CleanStep.Clean)?.cleaner ?: return null
    when (cleaner.base) {
        AccessPathBase.Zero, AccessPathBase.ClassStatic -> return null
        is AccessPathBase.CleanerTemporary -> return null
        else -> Unit
    }
    val atom = cleaner.mark ?: return null
    val group = marks.groupId(atom) ?: return null
    return ReturnGroupKey(cleaner.base, group)
}

/** Raw indices stay stable; each invocation selects the first equal key in its own suffix. */
internal class ReturnGroupSchedule private constructor(private val previous: IntArray) {
    val size: Int get() = previous.size
    fun execute(step: Int, firstStep: Int): Boolean = previous[step] < firstStep
    fun storage(counts: FormStorageCounts) { counts.index(previous) }

    companion object {
        fun compile(raw: List<CleanStep>, marks: MarkTable): ReturnGroupSchedule? {
            val previous = IntArray(raw.size) { -1 }
            val last = HashMap<ReturnGroupKey, Int>()
            var hasDuplicate = false
            raw.forEachIndexed { index, step ->
                val key = returnGroupKey(step, marks)
                if (key == null) last.clear()
                else {
                    previous[index] = last.put(key, index) ?: -1
                    hasDuplicate = hasDuplicate || previous[index] >= 0
                }
            }
            return if (hasDuplicate) ReturnGroupSchedule(previous) else null
        }
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
    class Actions private constructor(
        override val from: CallPoint,
        override val to: CallPoint,
        val steps: List<CleanStep>,
        internal val returnGroupSchedule: ReturnGroupSchedule?,
    ) : CallStage {
        constructor(from: CallPoint, to: CallPoint, steps: List<CleanStep>) :
            this(from, to, ownedList(steps), null)

        companion object {
            /** Only forward GROUP return rewriters use this factory, after ordinary lowering. */
            fun run1GroupReturnRewriter(steps: List<CleanStep>, marks: MarkTable): Actions {
                val raw = ownedList(steps)
                return Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, raw,
                    ReturnGroupSchedule.compile(raw, marks))
            }
        }

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

    internal fun storage(counts: FormStorageCounts) {
        counts.callPlans++
        counts.include(touched)
        counts.include(stages)
        counts.include(sinks)
        counts.include(passReads)
        counts.callPointDirectoryEntries += stagesFrom.size.toLong()
        if (stagesFrom is CallPointDirectory) stagesFrom.storage(counts)
        else ownedDirectoryStorage(stagesFrom, counts)
        for (stage in stages) when (stage) {
            is CallStage.Edges -> {
                counts.include(stage.summary)
                (stage.guard as? Guard.SinkTriggered)?.let { counts.include(it.sink) }
                stage.trigger?.let(counts::include)
            }
            is CallStage.Actions -> {
                counts.include(stage.steps)
                stage.returnGroupSchedule?.storage(counts)
                for (step in stage.steps) when (step) {
                    is CleanStep.Clean -> Unit
                    is CleanStep.Kill -> counts.include(step.keepEdges)
                    is CleanStep.Field -> { counts.include(step.read); counts.include(step.write) }
                }
            }
            is CallStage.Callees -> counts.include(stage.callees)
        }
        for (sink in sinks) counts.include(sink)
    }

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

    fun storage(counts: FormStorageCounts) {
        counts.ownedContainerEntries += byOrdinal.size.toLong()
        counts.index(pointOrder)
        for (group in byOrdinal) if (group != null) counts.include(group)
    }

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
