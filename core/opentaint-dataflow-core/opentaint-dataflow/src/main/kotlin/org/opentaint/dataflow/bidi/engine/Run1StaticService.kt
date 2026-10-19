package org.opentaint.dataflow.bidi.engine

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
    data class WriterReady(override val owner: MethodKey, val id: Long,
                           val inputs: List<RegionGroups>) : StaticOwnerEffect
    data class WriterInterest(override val owner: MethodKey, val id: Long,
                              val groups: GroupSet) : StaticOwnerEffect
}

data class StaticReadRegistration(val owner: MethodKey, val id: Long, val region: Pattern)
data class StaticInterest(val owner: MethodKey, val id: Long, val region: Pattern, val wanted: GroupSet)
data class StaticWriterRegistration(val owner: MethodKey, val id: Long, val premise: PremiseKey)
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
    private class InterestRow(val registration: StaticInterest) {
        var wanted = registration.wanted
        val delivered = HashMap<Pattern, GroupSet>()
    }
    private class WriterRow(val registration: StaticWriterRegistration) {
        val delivered = HashMap<List<Pattern>, List<GroupSet>>()
    }
    private class FlowWriterRow(val registration: StaticFlowWriter) {
        var delivered = GroupSet.EMPTY
    }
    private val writes = LinkedHashSet<Pattern>()
    private val writeIndex = Regions<Pattern>()
    private val groups = LinkedHashMap<Pattern, GroupSet>()
    private val groupIndex = Regions<Pattern>()
    private val reads = LinkedHashMap<Key, ReadRow>()
    private val readIndex = Regions<Key>()
    private val interests = LinkedHashMap<Key, InterestRow>()
    private val interestIndex = Regions<Key>()
    private val writers = LinkedHashMap<Key, WriterRow>()
    private val flowWriters = LinkedHashMap<Key, FlowWriterRow>()
    private val flowWriterIndex = Regions<Key>()
    private val staticDependents = Regions<Key>()
    private var candidateVisits = 0L
    private var replies = 0L

    fun commit(batch: StaticBatch): List<StaticOwnerEffect> = synchronized(lock) {
        ap.cancellation.checkpoint()
        val out = ArrayList<StaticOwnerEffect>()
        val dirty = LinkedHashSet<Key>()
        // Register first. Each fresh side joins the existing opposite index.
        for (read in batch.reads) {
            val key = Key(read.owner, read.id)
            if (key in reads) continue
            val row = ReadRow(read)
            reads[key] = row
            readIndex.add(read.region, key)
            for (write in writeIndex.around(read.region)) admitRead(row, write, out)
        }
        for (interest in batch.interests) {
            val key = Key(interest.owner, interest.id)
            val row = interests[key] ?: InterestRow(interest).also {
                interests[key] = it
                interestIndex.add(interest.region, key)
            }
            row.wanted = row.wanted + interest.wanted
            for (position in groupIndex.around(interest.region))
                admitInterest(row, position, groups.getValue(position), out)
            for (writer in flowWriterIndex.around(interest.region))
                admitWriterInterest(flowWriters.getValue(writer), row, out)
        }
        for (writer in batch.writers) {
            val key = Key(writer.owner, writer.id)
            if (writers.putIfAbsent(key, WriterRow(writer)) == null) {
                check(!writer.premise.isZero)
                writer.premise.forEachMember { member ->
                    check(member.base == STATIC) { "Only all-static summaries subscribe to pool values" }
                    staticDependents.add(member.toPattern(), key)
                }
                dirty += key
            }
        }
        for (writer in batch.flowWriters) {
            val key = Key(writer.owner, writer.id)
            if (key in flowWriters) continue
            val row = FlowWriterRow(writer)
            flowWriters[key] = row
            flowWriterIndex.add(writer.region, key)
            for (interest in interestIndex.around(writer.region))
                admitWriterInterest(row, interests.getValue(interest), out)
        }
        for (write in batch.writes) admitWrite(region(write), out)
        for (supply in batch.supplies) {
            if (supply.groups.isEmpty) continue
            val position = region(supply.region)
            require(position.fact.base == STATIC)
            admitWrite(position, out)
            val old = groups[position] ?: GroupSet.EMPTY
            val fresh = minus(supply.groups, old)
            if (fresh.isEmpty) continue
            groups[position] = old + fresh
            groupIndex.add(position, position)
            for (key in interestIndex.around(position))
                admitInterest(interests.getValue(key), position, fresh, out)
            dirty += staticDependents.around(position)
        }
        // Only all-static dependencies touched by a new pool membership are revisited.
        for (key in dirty) {
            ap.cancellation.checkpoint()
            val writer = writers.getValue(key)
            for (binding in candidateBindings(writer.registration.premise)) {
                val image = binding.map { it.region }
                val marks = binding.map { it.groups }
                val old = writer.delivered[image]
                if (old == null || hasGroupGrowth(marks, old)) {
                    writer.delivered[image] = unionGroups(marks, old)
                    out += StaticOwnerEffect.WriterReady(writer.registration.owner, writer.registration.id, binding)
                }
            }
        }
        replies += out.size
        out
    }

    private fun hasGroupGrowth(now: List<GroupSet>, old: List<GroupSet>): Boolean =
        now.indices.any { !minus(now[it], old[it]).isEmpty }

    private fun unionGroups(now: List<GroupSet>, old: List<GroupSet>?): List<GroupSet> =
        if (old == null) now else now.indices.map { now[it] + old[it] }

    private fun admitWriterInterest(writer: FlowWriterRow, interest: InterestRow,
                                    out: MutableList<StaticOwnerEffect>) {
        ap.cancellation.checkpoint()
        candidateVisits++
        // This routes a request, not a taint supply. Exclusions do not suppress it.
        if (intersect(writer.registration.region.copy(exclusion = ExclusionSet.Empty),
                interest.registration.region.copy(exclusion = ExclusionSet.Empty)) == null) return
        val fresh = minus(interest.wanted, writer.delivered)
        if (fresh.isEmpty) return
        writer.delivered += fresh
        out += StaticOwnerEffect.WriterInterest(writer.registration.owner, writer.registration.id, fresh)
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

    private fun admitInterest(row: InterestRow, position: Pattern, supplied: GroupSet,
                              out: MutableList<StaticOwnerEffect>) {
        ap.cancellation.checkpoint()
        candidateVisits++
        val overlap = intersect(row.registration.region, position) ?: return
        val selected = supplied intersect row.wanted
        val old = row.delivered[overlap] ?: GroupSet.EMPTY
        val fresh = minus(selected, old)
        if (fresh.isEmpty) return
        row.delivered[overlap] = old + fresh
        out += StaticOwnerEffect.PointReady(row.registration.owner, row.registration.id, RegionGroups(overlap, fresh))
    }

    /** The owner rechecks every member with ordinary AP matching after unlocking. */
    private fun candidateBindings(premise: PremiseKey): List<List<RegionGroups>> {
        var combinations: List<List<RegionGroups>> = listOf(emptyList())
        premise.forEachMember { member ->
            val matching = ArrayList<RegionGroups>()
            for (position in groupIndex.around(member.toPattern())) {
                ap.cancellation.checkpoint()
                candidateVisits++
                val marks = groups.getValue(position)
                val selected = when (val mark = member.mark) {
                    is MarkSlot.Concrete -> {
                        val group = ap.marks.groupOf(mark.mark)
                        if (group in marks) GroupSet.of(group) else GroupSet.EMPTY
                    }
                    is MarkSlot.Star -> marks
                }
                if (!selected.isEmpty) matching += RegionGroups(position, selected)
            }
            if (matching.isEmpty()) return emptyList()
            combinations = combinations.flatMap { prefix -> matching.map { prefix + it } }
        }
        return combinations
    }

    data class Statistics(val writeRegions: Int, val suppliedRegions: Int, val suppliedGroups: Long,
                          val readDescriptors: Int, val pointSubscriptions: Int, val writerRows: Int,
                          val flowWriterRows: Int, val candidateVisits: Long,
                          val ownerReplies: Long)
    fun statistics(): Statistics = synchronized(lock) {
        Statistics(writes.size, groups.size, groups.values.sumOf { it.size.toLong() }, reads.size,
            interests.size, writers.size, flowWriters.size, candidateVisits, replies)
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
        writes.clear(); writeIndex.clear(); groups.clear(); groupIndex.clear()
        reads.clear(); readIndex.clear(); interests.clear(); interestIndex.clear()
        writers.clear(); flowWriters.clear(); flowWriterIndex.clear(); staticDependents.clear()
    }

    companion object {
        fun region(pattern: Pattern): Pattern = pattern.copy(fact = pattern.fact.copy(mark = MarkSlot.STAR))
        fun hasStaticMember(premise: PremiseKey): Boolean {
            var found = false
            premise.forEachMember { if (it.base == STATIC) found = true }
            return found
        }
        private fun minus(a: GroupSet, b: GroupSet): GroupSet = GroupSet.fromIds(a.ids.filter { GroupId(it) !in b }.toIntArray())

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
