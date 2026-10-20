package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*

/** An immutable AP slice. The selector is stored separately from group membership. */
data class StaticRegion(val pattern: Pattern) {
    init { require(pattern.fact.base == STATIC) }
    constructor(fact: PathFact, exclusion: ExclusionSet = ExclusionSet.Empty) :
        this(Pattern(fact.copy(mark = MarkSlot.STAR), exclusion))
}

data class RegionGroups(val region: Pattern, val groups: GroupSet)

/** Event payloads contain values and IDs, never actor callbacks or mutable stores. */
sealed interface StaticOwnerEffect {
    val owner: MethodKey
    data class ReadReady(override val owner: MethodKey, val id: Long, val region: Pattern) : StaticOwnerEffect
    data class PointReady(override val owner: MethodKey, val id: Long, val supply: RegionGroups) : StaticOwnerEffect
    data class WriterInterest(override val owner: MethodKey, val id: Long,
                              val groups: GroupSet) : StaticOwnerEffect
}

data class StaticReadRegistration(val owner: MethodKey, val id: Long, val region: Pattern)
data class StaticInterest(val owner: MethodKey, val id: Long, val region: Pattern, val wanted: GroupSet)
data class StaticWriterRegistration(val owner: MethodKey, val id: Long, val premise: PremiseKey, val region: Pattern)
data class StaticFlowWriter(val owner: MethodKey, val id: Long, val region: Pattern)

/** Temporary actor-owned deltas. Commit is the only route to cumulative service membership. */
class StaticBatch {
    val reads = ArrayList<StaticReadRegistration>()
    val interests = ArrayList<StaticInterest>()
    val writes = ArrayList<Pattern>()
    val supplies = ArrayList<RegionGroups>()
    val writers = ArrayList<StaticWriterRegistration>()
    val flowWriters = ArrayList<StaticFlowWriter>()
    val isEmpty: Boolean get() = reads.isEmpty() && interests.isEmpty() && writes.isEmpty() &&
        supplies.isEmpty() && writers.isEmpty() && flowWriters.isEmpty()
}

/** Run-1 positive forward closure. Program transfer and resumption always run at the addressed owner. */
class Run1StaticService(private val ap: ApManager) {
    private val lock = Any()
    private val ops = ApOps(ap)
    private data class Key(val owner: MethodKey, val id: Long)

    /** Positional candidates only. Owners perform complete AP matching and transfer. */
    private inner class Regions<V : Any> {
        private var index = org.opentaint.dataflow.bidi.store.PathTrie<V>(ap.cancellation)
        fun add(p: Pattern, value: V) = index.add(p.fact.base, p.fact.path.toIntArray(), value)
        fun around(p: Pattern): List<V> = index.around(p.fact.base, p.fact.path.toIntArray()).distinct()
        fun clear() { index = org.opentaint.dataflow.bidi.store.PathTrie(ap.cancellation) }
    }
    private class ReadRow(val registration: StaticReadRegistration) { val delivered = HashSet<Pattern>() }
    private class InterestRow(val registration: StaticInterest) { var wanted = registration.wanted }
    private class FlowWriterRow(val owner: MethodKey, val id: Long, val region: Pattern,
                                val staticOrigin: Boolean) { var admitted = GroupSet.EMPTY }
    private data class FieldKey(val klass: AccessorIdx, val field: AccessorIdx)
    private class FieldState(val region: Pattern) {
        var req = GroupSet.EMPTY
        var reach = GroupSet.EMPTY
        var processed = GroupSet.EMPTY
        val pending = LinkedHashMap<Key, InterestRow>()
    }
    private val writes = LinkedHashSet<Pattern>()
    private val writeIndex = Regions<Pattern>()
    // Only broad root/class supplies keep spatial history. A normal field has
    // one canonical membership, never a second region-valued group pool.
    private val broadGroups = LinkedHashMap<Pattern, GroupSet>()
    private val fields = LinkedHashMap<FieldKey, FieldState>()
    private val fieldIndex = Regions<FieldKey>()
    private val reads = LinkedHashMap<Key, ReadRow>()
    private val readIndex = Regions<Key>()
    private val classInterests = LinkedHashMap<Key, InterestRow>()
    private val classRequested = LinkedHashMap<Pattern, GroupSet>()
    private val flowWriters = LinkedHashMap<Key, FlowWriterRow>()
    private val flowWriterIndex = Regions<Key>()
    private var candidateVisits = 0L
    private var replies = 0L

    private fun fieldKey(p: Pattern): FieldKey? = p.fact.path
        .takeIf { p.fact.base == STATIC && it.size >= 2 }
        ?.let { FieldKey(it[0], it[1]) }

    private fun field(p: Pattern): FieldState {
        val key = checkNotNull(fieldKey(p))
        return fields.getOrPut(key) {
            val canonical = Pattern(PathFact(STATIC, listOf(key.klass, key.field), Tail.STAR,
                MarkSlot.STAR), ExclusionSet.Empty)
            FieldState(canonical).also { state ->
                fieldIndex.add(canonical, key)
                for ((position, groups) in broadGroups)
                    if (intersect(canonical, position) != null) state.reach += groups
            }
        }
    }

    fun commit(batch: StaticBatch): List<StaticOwnerEffect> = synchronized(lock) {
        ap.cancellation.checkpoint()
        val out = ArrayList<StaticOwnerEffect>()
        for (read in batch.reads) {
            val key = Key(read.owner, read.id)
            if (key in reads) continue
            val row = ReadRow(read)
            reads[key] = row
            readIndex.add(read.region, key)
            for (write in writeIndex.around(read.region)) admitRead(row, write, out)
        }
        for (interest in batch.interests) {
            if (interest.wanted.isEmpty || interest.region.fact.base != STATIC) continue
            if (fieldKey(interest.region) != null) request(field(interest.region), interest, out)
            else requestClass(interest, out)
        }
        // Every FLOW writer uses this route, including static-origin summaries.
        // Complete fixed-mark bindings are pending actor state, not writer rows.
        for (writer in batch.writers) {
            check(writer.premise is InitialAp && writer.premise.mark is MarkSlot.Star)
            check(writer.premise.base == STATIC) { "Only all-static summaries subscribe to pool values" }
            registerWriter(FlowWriterRow(writer.owner, writer.id, writer.region, true), out)
        }
        for (writer in batch.flowWriters)
            registerWriter(FlowWriterRow(writer.owner, writer.id, writer.region, false), out)
        for (write in batch.writes) admitWrite(region(write), out)
        for (supply in batch.supplies) {
            if (supply.groups.isEmpty) continue
            val position = region(supply.region)
            require(position.fact.base == STATIC)
            admitWrite(position, out)
            if (fieldKey(position) != null) groupWrite(field(position), supply.groups, out)
            else {
                val old = broadGroups[position] ?: GroupSet.EMPTY
                val fresh = supply.groups - old
                if (fresh.isEmpty) continue
                broadGroups[position] = old + fresh
                for (key in fieldIndex.around(position)) {
                    val state = fields.getValue(key)
                    if (intersect(state.region, position) != null) groupWrite(state, fresh, out)
                }
            }
            // Exact class points never supply fields. Keep the old broad AP
            // boundary and pending-only class continuations.
            val iterator = classInterests.iterator()
            while (iterator.hasNext()) {
                val row = iterator.next().value
                answerClass(row, position, supply.groups, out)
                if (row.wanted.isEmpty) iterator.remove()
            }
        }
        replies += out.size
        out
    }

    private fun request(state: FieldState, interest: StaticInterest,
                        out: MutableList<StaticOwnerEffect>) {
        val answered = interest.wanted intersect (state.reach + state.processed)
        if (!answered.isEmpty) out += StaticOwnerEffect.PointReady(interest.owner, interest.id,
            RegionGroups(state.region, answered))
        val newlyRequested = interest.wanted - (state.req + state.processed)
        state.reach -= interest.wanted
        state.processed += answered
        val pending = interest.wanted - answered
        state.req += pending
        if (!pending.isEmpty) {
            val key = Key(interest.owner, interest.id)
            val row = state.pending[key]
            if (row == null) state.pending[key] = InterestRow(interest.copy(wanted = pending))
            else row.wanted += pending
        }
        if (!newlyRequested.isEmpty) {
            for (key in flowWriterIndex.around(state.region)) {
                val writer = flowWriters.getValue(key)
                if (requestMatches(writer.region, state.region)) askOnce(writer, newlyRequested, out)
            }
        }
    }

    private fun groupWrite(state: FieldState, groups: GroupSet,
                           out: MutableList<StaticOwnerEffect>) {
        val fresh = groups - (state.reach + state.processed)
        if (fresh.isEmpty) return
        val answered = fresh intersect state.req
        state.req -= answered
        state.processed += answered
        state.reach += fresh - answered
        if (answered.isEmpty) return // An unrequested arrival never wakes writers.
        val iterator = state.pending.iterator()
        while (iterator.hasNext()) {
            val row = iterator.next().value
            val selected = row.wanted intersect answered
            if (!selected.isEmpty) {
                row.wanted -= selected
                out += StaticOwnerEffect.PointReady(row.registration.owner, row.registration.id,
                    RegionGroups(state.region, selected))
            }
            if (row.wanted.isEmpty) iterator.remove()
        }
    }

    private fun registerWriter(row: FlowWriterRow, out: MutableList<StaticOwnerEffect>) {
        val key = Key(row.owner, row.id)
        if (flowWriters.putIfAbsent(key, row) != null) return
        flowWriterIndex.add(row.region, key)
        var wanted = GroupSet.EMPTY
        for (field in fieldIndex.around(row.region)) {
            val state = fields.getValue(field)
            if (requestMatches(row.region, state.region)) wanted += state.req + state.processed
        }
        for ((region, groups) in classRequested)
            if (requestMatches(row.region, region)) wanted += groups
        askOnce(row, wanted, out)
    }

    private fun askOnce(row: FlowWriterRow, offered: GroupSet,
                        out: MutableList<StaticOwnerEffect>) {
        val fresh = offered - row.admitted
        if (fresh.isEmpty) return
        row.admitted += fresh
        out += StaticOwnerEffect.WriterInterest(row.owner, row.id, fresh)
    }

    private fun requestMatches(a: Pattern, b: Pattern): Boolean {
        ap.cancellation.checkpoint()
        candidateVisits++
        return intersect(a.copy(exclusion = ExclusionSet.Empty), b.copy(exclusion = ExclusionSet.Empty)) != null
    }

    private fun requestClass(interest: StaticInterest, out: MutableList<StaticOwnerEffect>) {
        check(interest.region.fact.path.size == 1 && interest.region.fact.tail == Tail.EXACT) {
            "A global marker request names an exact class point"
        }
        val key = Key(interest.owner, interest.id)
        val row = classInterests[key] ?: InterestRow(interest).also { classInterests[key] = it }
        row.wanted += interest.wanted
        val requested = region(interest.region).copy(exclusion = ExclusionSet.Empty)
        classRequested[requested] = (classRequested[requested] ?: GroupSet.EMPTY) + interest.wanted
        for ((position, supplied) in broadGroups) answerClass(row, position, supplied, out)
        for (state in fields.values) answerClass(row, state.region, state.reach + state.processed, out)
        if (row.wanted.isEmpty) classInterests.remove(key)
        for (writer in flowWriterIndex.around(interest.region)) {
            val w = flowWriters.getValue(writer)
            if (requestMatches(w.region, interest.region)) askOnce(w, interest.wanted, out)
        }
    }

    private fun answerClass(row: InterestRow, position: Pattern, supplied: GroupSet,
                            out: MutableList<StaticOwnerEffect>) {
        val overlap = intersect(row.registration.region, position) ?: return
        val fresh = supplied intersect row.wanted
        if (fresh.isEmpty) return
        row.wanted -= fresh
        out += StaticOwnerEffect.PointReady(row.registration.owner, row.registration.id,
            RegionGroups(overlap, fresh))
    }

    private fun admitRead(row: ReadRow, write: Pattern, out: MutableList<StaticOwnerEffect>) {
        ap.cancellation.checkpoint()
        candidateVisits++
        val overlap = intersect(row.registration.region, write) ?: return
        if (row.delivered.add(overlap)) out += StaticOwnerEffect.ReadReady(row.registration.owner, row.registration.id, overlap)
    }

    private fun admitWrite(position: Pattern, out: MutableList<StaticOwnerEffect>) {
        if (!writes.add(position)) return
        writeIndex.add(position, position)
        for (key in readIndex.around(position)) admitRead(reads.getValue(key), position, out)
    }

    data class Statistics(val writeRegions: Int, val suppliedRegions: Int, val suppliedGroups: Long,
                          val readDescriptors: Int, val pointSubscriptions: Int, val writerRows: Int,
                          val flowWriterRows: Int, val candidateVisits: Long, val ownerReplies: Long)
    fun statistics(): Statistics = synchronized(lock) {
        val supplied = fields.values.filter { !(it.reach + it.processed).isEmpty }
        Statistics(writes.size, supplied.size + broadGroups.size,
            supplied.sumOf { (it.reach + it.processed).size.toLong() } + broadGroups.values.sumOf { it.size.toLong() },
            reads.size, fields.values.sumOf { it.pending.size } + classInterests.size,
            flowWriters.values.count { it.staticOrigin }, flowWriters.values.count { !it.staticOrigin },
            candidateVisits, replies)
    }

    data class RequestStatistics(val requested: Long, val reached: Long, val processed: Long,
                                 val pendingMemberships: Long, val writerAdmissions: Long)
    fun requestStatistics(): RequestStatistics = synchronized(lock) {
        RequestStatistics(fields.values.sumOf { it.req.size.toLong() },
            fields.values.sumOf { it.reach.size.toLong() }, fields.values.sumOf { it.processed.size.toLong() },
            fields.values.sumOf { f -> f.pending.values.sumOf { it.wanted.size.toLong() } } +
                classInterests.values.sumOf { it.wanted.size.toLong() },
            flowWriters.values.sumOf { it.admitted.size.toLong() })
    }

    fun slices(value: Facts): List<RegionGroups> {
        if (value !is TaintTree) return emptyList()
        val out = ArrayList<RegionGroups>()
        for (leaf in ops.leaves(value)) {
            val mark = (leaf.fact.mark as? MarkSlot.Concrete)?.mark ?: continue
            val group = ap.marks.groupOf(mark)
            out += RegionGroups(region(leaf), GroupSet.of(group))
        }
        return out
    }

    fun facts(region: Pattern, groups: GroupSet): Facts {
        require(!groups.isEmpty)
        check(region.fact.path.size != 1 || region.fact.tail == Tail.EXACT) {
            "Global marker materialization preserves the exact class point"
        }
        val atoms = ap.marks.atomsFor(groups)
        var result: TaintTree? = null
        for (atom in atoms.ids) {
            val one = ap.factsOf(region.fact.copy(mark = MarkSlot.Concrete(TaintMark(atom))), region.exclusion, Layer.DEMAND) as TaintTree
            result = if (result == null) one else ap.taintTree(one.base, Layer.DEMAND,
                ap.taintAlg.mergeAdd(result.root, one.root, ap.cancellation), one.exclusion, one.anyTail)
        }
        return checkNotNull(result)
    }

    fun clear() = synchronized(lock) {
        writes.clear(); writeIndex.clear(); broadGroups.clear(); fields.clear(); fieldIndex.clear()
        reads.clear(); readIndex.clear(); classInterests.clear(); classRequested.clear()
        flowWriters.clear(); flowWriterIndex.clear()
    }

    companion object {
        fun region(pattern: Pattern): Pattern = pattern.copy(fact = pattern.fact.copy(mark = MarkSlot.STAR))
        fun hasStaticMember(premise: PremiseKey): Boolean {
            var found = false
            premise.forEachMember { if (it.base == STATIC) found = true }
            return found
        }

        /** Exact ancestor points do not supply descendants. Exclusions apply before consuming a prefix. */
        fun intersect(a: Pattern, b: Pattern): Pattern? {
            if (a.fact.base != b.fact.base) return null
            val p = a.fact.path
            val q = b.fact.path
            val common = minOf(p.size, q.size)
            if ((0 until common).any { p[it] != q[it] }) return null
            if (p.size < q.size) {
                if (a.fact.tail == Tail.EXACT || !a.exclusion.admits(q[p.size])) return null
                return region(b)
            }
            if (q.size < p.size) {
                if (b.fact.tail == Tail.EXACT || !b.exclusion.admits(p[q.size])) return null
                return region(a)
            }
            val tail = when {
                a.fact.tail == Tail.EXACT || b.fact.tail == Tail.EXACT -> Tail.EXACT
                a.fact.tail == Tail.ANY || b.fact.tail == Tail.ANY -> Tail.ANY
                a.fact.tail == Tail.ANY_TAINT || b.fact.tail == Tail.ANY_TAINT -> Tail.ANY_TAINT
                else -> Tail.STAR
            }
            return Pattern(a.fact.copy(tail = tail, mark = MarkSlot.STAR), a.exclusion.union(b.exclusion))
        }
    }
}
