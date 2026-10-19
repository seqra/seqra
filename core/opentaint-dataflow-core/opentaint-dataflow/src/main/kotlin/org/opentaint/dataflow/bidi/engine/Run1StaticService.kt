package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.CallerRef

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
    data class InputReady(override val owner: MethodKey, val row: N2InputRef,
                          val inputs: List<RegionGroups>) : StaticOwnerEffect
}

data class StaticReadRegistration(val owner: MethodKey, val id: Long, val region: Pattern)
data class StaticInterest(val owner: MethodKey, val id: Long, val region: Pattern, val wanted: GroupSet)
data class StaticWriterRegistration(val owner: MethodKey, val id: Long, val premise: PremiseKey)
data class GroundedInputPort(val caller: MethodKey, val call: org.opentaint.ir.api.common.cfg.CommonInst,
                             val stageOrdinal: Int = 0)
data class N2InputRef(val owner: MethodKey, val callee: MethodKey, val handle: Int, val ref: CallerRef,
                      val stageOrdinal: Int = ref.stageOrdinal)
data class StaticInputRegistration(val row: N2InputRef, val slices: List<RegionGroups>)
data class GroundedInputGrowth(val row: N2InputRef, val slices: List<RegionGroups>)

/** Temporary actor-owned deltas. Commit is the only route to cumulative service membership. */
class StaticBatch {
    val reads = ArrayList<StaticReadRegistration>()
    val interests = ArrayList<StaticInterest>()
    val writes = ArrayList<Pattern>()
    val supplies = ArrayList<RegionGroups>()
    val writers = ArrayList<StaticWriterRegistration>()
    val inputs = ArrayList<StaticInputRegistration>()
    val groundedInputs = ArrayList<GroundedInputGrowth>()
    val isEmpty: Boolean get() = reads.isEmpty() && interests.isEmpty() && writes.isEmpty() &&
        supplies.isEmpty() && writers.isEmpty() && inputs.isEmpty() && groundedInputs.isEmpty()
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
    private data class InputDelivery(val marks: List<GroupSet>, val revision: Long)
    private class InputRow {
        var revision = 0L
        val delivered = HashMap<List<Pattern>, InputDelivery>()
    }
    private sealed interface Dependent {
        data class Writer(val key: Key) : Dependent
        data class Input(val row: N2InputRef) : Dependent
    }
    private inner class GroundedRegions {
        val groups = LinkedHashMap<Pattern, GroupSet>()
        val index = Regions<Pattern>()
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
    private val inputs = LinkedHashMap<N2InputRef, InputRow>()
    private val grounded = LinkedHashMap<MethodKey, LinkedHashMap<GroundedInputPort, GroundedRegions>>()
    private val staticDependents = Regions<Dependent>()
    private val methodDependents = LinkedHashMap<MethodKey, Regions<Dependent>>()
    private var candidateVisits = 0L
    private var replies = 0L

    fun commit(batch: StaticBatch): List<StaticOwnerEffect> = synchronized(lock) {
        ap.cancellation.checkpoint()
        val out = ArrayList<StaticOwnerEffect>()
        val dirty = LinkedHashSet<Dependent>()
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
        }
        for (writer in batch.writers) {
            val key = Key(writer.owner, writer.id)
            if (writers.putIfAbsent(key, WriterRow(writer)) == null)
                watch(writer.owner, writer.premise, Dependent.Writer(key))
            dirty += Dependent.Writer(key)
        }
        for (input in batch.inputs) {
            if (!isRootedPortOrigin(input.row.ref.premise)) continue
            val row = inputs[input.row] ?: InputRow().also {
                inputs[input.row] = it
                watch(input.row.owner, input.row.ref.premise, Dependent.Input(input.row))
            }
            // Apply old support again only when its existing N2 row grows.
            row.revision++
            dirty += Dependent.Input(input.row)
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
        for ((input, slices) in batch.groundedInputs) {
            check(isRootedPortOrigin(input.ref.premise)) {
                "Ordinary conditional inputs must discharge their binding through caller summary joins"
            }
            val port = GroundedInputPort(input.ref.caller, input.ref.call, input.stageOrdinal)
            val row = grounded.getOrPut(input.callee) { LinkedHashMap() }.getOrPut(port) { GroundedRegions() }
            for (slice in slices) {
                val position = region(slice.region)
                val old = row.groups[position] ?: GroupSet.EMPTY
                val fresh = minus(slice.groups, old)
                if (fresh.isEmpty) continue
                row.groups[position] = old + fresh
                row.index.add(position, position)
                dirty += methodDependents[input.callee]?.around(position).orEmpty()
            }
        }
        // Only dependencies touched by new region/group memberships are visited.
        for (dependent in dirty) {
            ap.cancellation.checkpoint()
            when (dependent) {
                is Dependent.Writer -> {
                    val writer = writers.getValue(dependent.key)
                    for (binding in candidateBindings(writer.registration.owner, writer.registration.premise)) {
                        val image = binding.map { it.region }
                        val marks = binding.map { it.groups }
                        val old = writer.delivered[image]
                        if (old == null || hasGroupGrowth(marks, old)) {
                            writer.delivered[image] = unionGroups(marks, old)
                            out += StaticOwnerEffect.WriterReady(writer.registration.owner, writer.registration.id, binding)
                        }
                    }
                }
                is Dependent.Input -> {
                    val input = dependent.row
                    val row = inputs.getValue(input)
                    for (binding in candidateBindings(input.owner, input.ref.premise)) {
                        val image = binding.map { it.region }
                        val marks = binding.map { it.groups }
                        val old = row.delivered[image]
                        if (old == null || old.revision != row.revision || hasGroupGrowth(marks, old.marks)) {
                            row.delivered[image] = InputDelivery(unionGroups(marks, old?.marks), row.revision)
                            out += StaticOwnerEffect.InputReady(input.owner, input, binding)
                        }
                    }
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

    private fun watch(owner: MethodKey, premise: PremiseKey, dependent: Dependent) {
        premise.forEachMember { member ->
            if (member.isZero) return@forEachMember
            val index = if (member.base == STATIC) staticDependents
                else methodDependents.getOrPut(owner) { Regions() }
            index.add(member.toPattern(), dependent)
        }
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

    /** Current Facts remain solely in the caller's existing N2 row. */
    fun registerInput(row: N2InputRef, added: Facts): List<StaticOwnerEffect> {
        if (added is Reach || !isRootedPortOrigin(row.ref.premise)) return emptyList()
        val batch = StaticBatch()
        batch.inputs += StaticInputRegistration(row, emptyList())
        return commit(batch)
    }

    /** Candidate bindings use one caller/context/stage port. Complete AP checks run
     * at owners, after unlocking. No program transfer or foreign store read runs here. */
    private fun candidateBindings(owner: MethodKey, premise: PremiseKey): List<List<RegionGroups>> {
        if (premise.isZero) return listOf(emptyList())
        val members = ArrayList<InitialAp>()
        premise.forEachMember { members += it }
        val ports: List<GroundedRegions?> = if (members.any { it.base != STATIC })
            grounded[owner]?.values.orEmpty().toList() else listOf(null)
        val results = LinkedHashSet<List<RegionGroups>>()
        for (port in ports) {
            var combinations: List<List<RegionGroups>> = listOf(emptyList())
            for (member in members) {
                val matching = ArrayList<RegionGroups>()
                val pool = if (member.base == STATIC) groups else port?.groups.orEmpty()
                val positions = if (member.base == STATIC) groupIndex.around(member.toPattern())
                    else port?.index?.around(member.toPattern()).orEmpty()
                for (position in positions) {
                    ap.cancellation.checkpoint()
                    candidateVisits++
                    val marks = pool.getValue(position)
                    val selected = when (val mark = member.mark) {
                        is MarkSlot.Concrete -> {
                            val g = ap.marks.groupOf(mark.mark)
                            if (g in marks) GroupSet.of(g) else GroupSet.EMPTY
                        }
                        is MarkSlot.Star -> marks
                    }
                    if (!selected.isEmpty) matching += RegionGroups(position, selected)
                }
                if (matching.isEmpty()) { combinations = emptyList(); break }
                combinations = combinations.flatMap { prefix -> matching.map { prefix + it } }
            }
            results += combinations
        }
        return results.toList()
    }

    data class Statistics(val writeRegions: Int, val suppliedRegions: Int, val suppliedGroups: Long,
                          val readDescriptors: Int, val pointSubscriptions: Int, val writerRows: Int,
                          val inputRows: Int, val groundedRegions: Long, val candidateVisits: Long,
                          val ownerReplies: Long)
    fun statistics(): Statistics = synchronized(lock) {
        Statistics(writes.size, groups.size, groups.values.sumOf { it.size.toLong() }, reads.size,
            interests.size, writers.size, inputs.size,
            grounded.values.sumOf { ports -> ports.values.sumOf { it.groups.size.toLong() } }, candidateVisits, replies)
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
        val atoms = ap.marks.groupAtoms(groups)
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
        writers.clear(); inputs.clear(); grounded.clear(); staticDependents.clear(); methodDependents.clear()
    }

    companion object {
        /** Keep ordinary-origin effects conditional until normal summary composition
         * reaches a complete rooted call binding. Forwarding scalar slots through
         * nested ordinary callers would merge incompatible upstream invocations. */
        fun isRootedPortOrigin(premise: PremiseKey): Boolean = premise.isZero ||
            (0 until premise.size).all { premise.member(it).base == STATIC }

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
