package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.bidi.ap.*

/** A raw normal singleton summary, retained in the direction in which it was derived. */
class Record(
    val method: MethodKey,
    val direction: Direction,
    val premise: InitialAp,
    val conclusion: Facts,
) {
    /** Transient opposite-direction reads retain their actual premise and result layer. */
    fun reversedAt(a: Pattern): Sequence<RecordLeafView> = sequence {
        val m = premise.manager
        m.cancellation.checkpoint()
        check(conclusion.layer == Layer.NORMAL) { "Only a normal native record has reversed views" }
        val nativePremise = premise.toPattern()
        for (leaf in ApOps(m).leaves(conclusion)) {
            m.cancellation.checkpoint()
            if (leaf.fact.base != a.fact.base ||
                !(leaf.fact.path.startsWith(a.fact.path) || a.fact.path.startsWith(leaf.fact.path))) continue
            val view = reverseRecordLeaf(direction, nativePremise, Conclusion(leaf.fact, leaf.exclusion, demand = false))
                ?: continue
            yield(view)
        }
    }
}

/** Persistent native records. A run obtains a view that permits reads only. */
interface RecordStore {
    fun add(record: Record)
    fun byEntry(method: MethodKey, addedFact: Pattern): Sequence<Record>
    fun byExit(method: MethodKey, fact: Pattern): Sequence<Record>
    fun view(): RecordStore
    fun persist(direction: Direction, summaries: Sequence<Pair<MethodKey, RunSummaryStore>>)
}

/** The driver writes only at a barrier; the next run reads the published indexes. */
class PersistentRecordStore(private val m: ApManager) : RecordStore {
    private val entry = HashMap<MethodKey, PathTrie<Record>>()
    private val exit = HashMap<MethodKey, PathTrie<Record>>()
    private val merged = HashMap<Triple<MethodKey, Direction, InitialAp>, ConclusionGroup>()
    private val interners = m.newInterners()

    override fun add(record: Record) {
        m.cancellation.checkpoint()
        checkNativeRecord(record.direction, record.premise, record.conclusion)
        entry.getOrPut(record.method) { PathTrie(m.cancellation) }
            .add(record.premise.base, record.premise.pathArray, record)
        m.forEachLeafPosition(record.conclusion) { base, path ->
            exit.getOrPut(record.method) { PathTrie(m.cancellation) }.add(base, path, record)
        }
    }

    /** R2 candidates; the caller tests applicable or inside on the full native premise. */
    override fun byEntry(method: MethodKey, addedFact: Pattern): Sequence<Record> = sequence {
        m.cancellation.checkpoint()
        val candidates = entry[method]?.around(addedFact.fact.base, addedFact.fact.path.toIntArray()).orEmpty()
        for (record in candidates) {
            m.cancellation.checkpoint()
            yield(record)
        }
    }

    /** A record at several exit leaf positions is returned once. R3 supplies its transient views. */
    override fun byExit(method: MethodKey, fact: Pattern): Sequence<Record> = sequence {
        m.cancellation.checkpoint()
        val candidates = exit[method]?.around(fact.fact.base, fact.fact.path.toIntArray()).orEmpty()
        val seen = HashSet<Record>()
        for (record in candidates) {
            m.cancellation.checkpoint()
            if (seen.add(record)) yield(record)
        }
    }

    override fun view(): RecordStore = object : RecordStore by this {
        override fun add(record: Record) = error("Read-only during a run (analyzer-core.md A4)")
        override fun persist(direction: Direction, summaries: Sequence<Pair<MethodKey, RunSummaryStore>>) =
            error("Read-only during a run (analyzer-core.md A4)")
        override fun view(): RecordStore = this
    }

    /** R1 reads raw summaries, merges eligible native values and indexes their propagation delta. */
    override fun persist(direction: Direction, summaries: Sequence<Pair<MethodKey, RunSummaryStore>>) {
        m.cancellation.checkpoint()
        for ((method, store) in summaries) {
            m.cancellation.checkpoint()
            for ((premise, facts) in store.all()) {
                m.cancellation.checkpoint()
                if (facts.layer == Layer.DEMAND) continue
                val initial = premise as? InitialAp ?: continue
                if (direction == Direction.BACKWARD && initial.isZero) continue
                checkNativeRecord(direction, initial, facts)
                val delta = merged.getOrPut(Triple(method, direction, initial)) { ConclusionGroup(m, interners) }
                    .add(facts) ?: continue
                add(Record(method, direction, initial, delta))
            }
        }
    }
}

private fun checkNativeRecord(direction: Direction, premise: InitialAp, conclusion: Facts) {
    check(conclusion.layer == Layer.NORMAL) { "R1: a native record must be normal" }
    check((conclusion is FlowTree) == premise.isFlow) { "K6: the conclusion kind follows the native premise" }
    check(premise.isZero || when (premise.mark) {
        is MarkSlot.Concrete -> premise.tail != Tail.STAR
        is MarkSlot.Star -> premise.tail == Tail.STAR && premise.exclusion == ExclusionSet.Empty
    }) { "A native record needs a zero, concrete exact/any or empty-exclusion abstract STAR initial" }
    if (direction == Direction.BACKWARD) {
        check(!premise.isZero) { "R1: a backward zero-premise edge is not a native record" }
        check(premise.tail != Tail.ANY_TAINT) { "W8 (d): a backward record cannot have a must premise" }
        check(conclusion !is TaintTree || !conclusion.root.hasAny) { "W6: a backward any leaf is demand" }
    }
}
