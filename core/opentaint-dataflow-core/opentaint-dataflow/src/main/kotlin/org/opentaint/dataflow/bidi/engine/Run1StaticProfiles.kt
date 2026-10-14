package org.opentaint.dataflow.bidi.engine

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ownedList
import org.opentaint.dataflow.bidi.interp.ownedMap
import org.opentaint.dataflow.bidi.store.RequestMarkCursor
import org.opentaint.dataflow.bidi.store.RequestMarkSelection
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.CommonMethod
import java.util.IdentityHashMap

enum class StaticMarkRole { SOURCE, SINK, INPUT_GATE }

/** Shared persistent membership; no component-by-mark primitive array is materialized. */
class StaticCatalogMarkSet internal constructor(internal val ids: PersistentSet<Int>, private val hash: Int,
                                                private val cancellation: Cancellation? = null) : RequestMarkSelection {
    val size: Int get() = ids.size
    val isEmpty: Boolean get() = ids.isEmpty()
    override fun containsId(id: Int): Boolean = id in ids
    operator fun contains(mark: TaintMark): Boolean = containsId(mark.id)
    override fun cursor(): RequestMarkCursor = object : RequestMarkCursor {
        private val values = ids.iterator()
        override val isComplete: Boolean get() = !values.hasNext()
        override fun step(budget: Int, visit: (TaintMark) -> Unit): Int {
            require(budget > 0)
            cancellation?.checkpoint()
            var used = 0
            while (used < budget && values.hasNext()) {
                cancellation?.checkpoint(); visit(TaintMark(values.next())); used++
            }
            return used
        }
    }
    override fun equals(other: Any?): Boolean = this === other || other is StaticCatalogMarkSet &&
        hash == other.hash && ids == other.ids
    override fun hashCode(): Int = hash
    override fun toString(): String = ids.toString()

    internal companion object {
        fun from(marks: RequestMarkSet): StaticCatalogMarkSet {
            var ids = persistentHashSetOf<Int>()
            var hash = 0
            marks.forEachId { id -> require(id > 0); ids = ids.add(id); hash += id }
            return StaticCatalogMarkSet(ids, hash)
        }
    }
}

internal data class StaticMarkShape(val pattern: Pattern, val role: StaticMarkRole)

/** Grouped interest only. Full query geometry/role is retained; the mark lives in membership.
 *  Original concrete NNF literals remain in their form, not in this catalog query view. */
class StaticMarkInterest private constructor(
    val pattern: Pattern, val role: StaticMarkRole, val marks: StaticCatalogMarkSet,
    val slot: List<AccessorIdx>,
) {
    constructor(pattern: Pattern, role: StaticMarkRole, marks: RequestMarkSet) : this(
        pattern.copy(fact = pattern.fact.copy(path = ownedList(pattern.fact.path), mark = MarkSlot.STAR)),
        role, StaticCatalogMarkSet.from(marks), ownedList(pattern.fact.path.take(2)))
    internal val shape = StaticMarkShape(pattern, role)
    private val hash = 31 * shape.hashCode() + marks.hashCode()

    init {
        require(pattern.fact.base == STATIC) { "A static mark interest must have the static base" }
        require(!marks.isEmpty) { "A declared mark interest must name a mark" }
    }

    override fun equals(other: Any?): Boolean = this === other || other is StaticMarkInterest &&
        hash == other.hash && pattern == other.pattern && role == other.role && marks == other.marks
    override fun hashCode(): Int = hash
    override fun toString(): String = "$role:$pattern:$marks"
    internal fun withMarks(ids: StaticCatalogMarkSet): StaticMarkInterest =
        if (ids === marks) this else StaticMarkInterest(pattern, role, ids, slot)
}

internal typealias StaticQueries = PersistentMap<StaticMarkShape, StaticMarkInterest>
internal typealias StaticDirectory = PersistentMap<List<AccessorIdx>, StaticQueries>

/** AP IDs belong to the new analysis manager. Neutral JVM descriptors must be remapped before this call. */
class Run1StaticLocalProfile(
    accesses: Collection<List<AccessorIdx>> = emptyList(),
    abstractions: Collection<List<AccessorIdx>> = emptyList(),
    marks: Collection<StaticMarkInterest> = emptyList(),
    val cleanerMarks: RequestMarkSet = RequestMarkSet.EMPTY,
) {
    val accesses: List<List<AccessorIdx>> = ownedList(accesses.map(::ownedSlot))
    val abstractions: List<List<AccessorIdx>> = ownedList(abstractions.map(::ownedSlot))
    val marks: List<StaticMarkInterest> = ownedList(marks)

    companion object {
        @JvmField val EMPTY = Run1StaticLocalProfile()
    }
}

private fun ownedSlot(path: List<AccessorIdx>): List<AccessorIdx> {
    require(path.size <= 2) { "A static slot has at most its class and field accessors" }
    require(path.isEmpty() || path.first().isClass()) { "A static slot must start with its class" }
    require(path.size < 2 || path[1].isCounted()) { "A static field slot needs a counted second accessor" }
    return ownedList(path)
}

/** One component's shared immutable closure. No mutable actor policy or supplier lives here. */
class Run1StaticProfile internal constructor(
    val accesses: Set<List<AccessorIdx>>,
    val abstractions: Set<List<AccessorIdx>>,
    private val bySlot: StaticDirectory,
    private val interestCount: Int,
    val declaredMarkCount: Int,
    internal val cleanerIds: PersistentSet<Int>,
    private val cancellation: Cancellation,
) {
    val markInterests: Set<StaticMarkInterest> = object : AbstractSet<StaticMarkInterest>() {
        override val size: Int get() = interestCount
        override fun contains(element: StaticMarkInterest): Boolean = bySlot[element.slot]?.get(element.shape) == element
        override fun iterator(): Iterator<StaticMarkInterest> = bySlot.values.asSequence().flatMap { it.values.asSequence() }.iterator()
    }
    val cleanerMarkCount: Int get() = cleanerIds.size
    val cleanerMarks: RequestMarkSelection = object : RequestMarkSelection {
        override fun containsId(id: Int): Boolean { cancellation.checkpoint(); return id in cleanerIds }
        override fun cursor(): RequestMarkCursor = object : RequestMarkCursor {
            private val input = cleanerIds.iterator()
            override val isComplete: Boolean get() = !input.hasNext()
            override fun step(budget: Int, visit: (TaintMark) -> Unit): Int {
                require(budget > 0)
                cancellation.checkpoint()
                var used = 0
                while (used < budget && input.hasNext()) {
                    cancellation.checkpoint()
                    visit(TaintMark(input.next()))
                    used++
                }
                return used
            }
        }
    }

    fun containsAbstraction(path: List<AccessorIdx>): Boolean {
        cancellation.checkpoint()
        return path in abstractions
    }

    /** Ordinary map lookup; a field supplier does not scan the component's entire catalog. */
    fun markInterestsAt(slot: List<AccessorIdx>): Sequence<StaticMarkInterest> {
        cancellation.checkpoint()
        return bySlot[slot]?.values?.asSequence().orEmpty()
    }

    /** Coverage is stricter than interest membership because an ancestor answer can have another shape. */
    fun covers(initial: InitialAp, mark: TaintMark): Boolean {
        cancellation.checkpoint()
        if (initial.base != STATIC || initial.tail != Tail.STAR || initial.exclusion != ExclusionSet.Empty ||
            initial.mark != MarkSlot.STAR) return false
        val path = initial.path?.toList().orEmpty()
        if (path.isEmpty() && mark.id in cleanerIds) return true
        if (path.size > 2) return false
        return markInterestsAt(path).any { interest ->
            cancellation.checkpoint()
            val fact = interest.pattern.fact
            mark in interest.marks && fact.path == path && interest.pattern.exclusion == ExclusionSet.Empty &&
                (fact.tail == Tail.STAR || fact.tail == Tail.ANY) && when (val declared = fact.mark) {
                    is MarkSlot.Concrete -> declared.mark == mark
                    is MarkSlot.Star -> mark !in declared.excluded
                }
        }
    }

    internal fun directory(): StaticDirectory = bySlot
}

/** Direct/transitive interest counts expand (full query, role, mark) declarations per method;
 *  group counts record the shared queries actually visited, including methods sharing one SCC profile.
 *  Root counts use object identity. Distinct path values are not a physical-array census.
 *  No primitive mark arrays are owned here. Persistent-library node bytes require a heap/layout measurement. */
data class Run1StaticProfileStatistics(
    val methods: Int,
    val callEdges: Long,
    val components: Int,
    val directAccesses: Long,
    val directAbstractions: Long,
    val directMarkInterests: Long,
    val directCleanerMarks: Long,
    val transitiveAccesses: Long,
    val transitiveAbstractions: Long,
    val transitiveMarkInterests: Long,
    val transitiveCleanerMarks: Long,
    val uniquePositions: Int,
    val uniqueMarkInterests: Int,
    val distinctPathAccessorValues: Long,
    val ownedMarkPrimitiveCells: Long,
    val accessSetRoots: Int,
    val abstractionSetRoots: Int,
    val markDirectoryRoots: Int,
    val cleanerSetRoots: Int,
    val membershipAdds: Long,
    val unchangedUnions: Long,
    val cancellationCheckpoints: Long,
    val maximumProfileSize: Long,
    val closureNanos: Long,
    val persistentNodeBytes: Long? = null,
    val directMarkGroups: Long = 0,
    val transitiveMarkGroups: Long = 0,
    val uniqueMarkQueries: Int = 0,
)

/** Immutable copied graph plus one profile per SCC. Release this owner after run-1 workers join. */
class Run1StaticProfiles internal constructor(
    private val profiles: Map<CommonMethod, Run1StaticProfile>,
    private val targets: Map<CommonMethod, Set<CommonMethod>>,
    val statistics: Run1StaticProfileStatistics,
) {
    val methods: Set<CommonMethod> get() = profiles.keys
    operator fun get(method: CommonMethod): Run1StaticProfile = checkNotNull(profiles[method]) {
        "Runtime method is absent from the frozen run-1 static catalog: $method"
    }
    fun contains(method: CommonMethod): Boolean = profiles.containsKey(method)
    fun containsTarget(caller: CommonMethod, callee: CommonMethod): Boolean = targets[caller]?.contains(callee) == true

    companion object {
        fun build(
            ap: ApManager,
            adjacency: Map<CommonMethod, Collection<CommonMethod>>,
            locals: Map<CommonMethod, Run1StaticLocalProfile>,
        ): Run1StaticProfiles = StaticProfileBuilder(ap).build(adjacency, locals)
    }
}

private class StaticProfileBuilder(private val ap: ApManager) {
    private val cancellation = ap.cancellation
    private var checkpoints = 0L
    private var adds = 0L
    private var reuses = 0L
    private val positions = HashMap<List<AccessorIdx>, List<AccessorIdx>>()
    private val interests = HashMap<StaticMarkInterest, StaticMarkInterest>()
    private val singletonMarks = HashMap<Int, StaticCatalogMarkSet>()

    private fun check() { checkpoints++; cancellation.checkpoint() }

    private fun position(value: List<AccessorIdx>): List<AccessorIdx> {
        check()
        return positions.getOrPut(value) {
            val result = ownedSlot(value)
            result.forEach { check(); ap.accessors.accessor(it) }
            result
        }
    }

    private fun interest(value: StaticMarkInterest): StaticMarkInterest {
        check()
        return interests.getOrPut(value) {
            val slot = position(value.slot)
            for (accessor in value.pattern.fact.path) { check(); ap.accessors.accessor(accessor) }
            var marks: StaticCatalogMarkSet? = null
            for (id in value.marks.ids) {
                check(); ap.marks.name(TaintMark(id))
                val single = singletonMarks.getOrPut(id) {
                    StaticCatalogMarkSet(persistentHashSetOf(id), id, cancellation)
                }
                marks = marks?.let { unionMarks(it, single) } ?: single
            }
            value.withMarks(checkNotNull(marks)).also { check(it.slot == slot) }
        }
    }

    private fun unionMarks(a: StaticCatalogMarkSet, b: StaticCatalogMarkSet): StaticCatalogMarkSet {
        check()
        if (a.ids === b.ids) { reuses++; return a }
        val start = if (a.size >= b.size) a else b
        val other = if (start === a) b else a
        var ids = start.ids
        var hash = start.hashCode()
        for (id in other.ids) {
            check()
            val next = ids.add(id)
            if (next !== ids) { adds++; hash += id }
            ids = next
        }
        if (ids === start.ids) { reuses++; return start }
        return StaticCatalogMarkSet(ids, hash, cancellation)
    }

    private fun mergeInterest(a: StaticMarkInterest?, b: StaticMarkInterest): StaticMarkInterest {
        if (a == null) return b
        val marks = unionMarks(a.marks, b.marks)
        return when {
            marks === a.marks -> a
            marks === b.marks -> b
            else -> a.withMarks(marks)
        }
    }

    private fun <T> union(a: PersistentSet<T>, b: PersistentSet<T>): PersistentSet<T> {
        check()
        if (a === b || b.isEmpty()) { reuses++; return a }
        var result = if (a.size >= b.size) a else b
        val other = if (result === a) b else a
        val start = result
        for (value in other) {
            check()
            val next = result.add(value)
            if (next !== result) adds++
            result = next
        }
        if (result === start) reuses++
        return result
    }

    private class JoinedDirectory(val values: StaticDirectory, val groups: Int, val declarations: Int)

    private fun union(
        a: StaticDirectory,
        aCount: Int,
        aDeclarations: Int,
        b: StaticDirectory,
        bCount: Int,
        bDeclarations: Int,
    ): JoinedDirectory {
        check()
        if (a === b || b.isEmpty()) { reuses++; return JoinedDirectory(a, aCount, aDeclarations) }
        var result = if (a.size >= b.size) a else b
        var count = if (result === a) aCount else bCount
        var declarations = if (result === a) aDeclarations else bDeclarations
        val other = if (result === a) b else a
        val start = result
        for ((slot, values) in other) {
            check()
            val current = result[slot] ?: persistentHashMapOf()
            var next = current
            for ((shape, interest) in values) {
                check()
                val old = next[shape]
                val joined = mergeInterest(old, interest)
                if (joined !== old) {
                    if (old == null) count = Math.addExact(count, 1)
                    declarations = Math.addExact(declarations, joined.marks.size - (old?.marks?.size ?: 0))
                    next = next.put(shape, joined)
                }
            }
            if (next !== current) result = result.put(slot, next)
        }
        if (result === start) reuses++
        return JoinedDirectory(result, count, declarations)
    }

    private class Part {
        var accesses = persistentHashSetOf<List<AccessorIdx>>()
        var abstractions = persistentHashSetOf<List<AccessorIdx>>()
        var marks: StaticDirectory = persistentHashMapOf()
        var markCount = 0
        var declarations = 0
        var cleaners = persistentHashSetOf<Int>()
    }

    private fun addInterest(part: Part, value: StaticMarkInterest) {
        val values = part.marks[value.slot] ?: persistentHashMapOf()
        val old = values[value.shape]
        val joined = mergeInterest(old, value)
        if (joined === old) return
        if (old == null) part.markCount = Math.addExact(part.markCount, 1)
        part.declarations = Math.addExact(part.declarations, joined.marks.size - (old?.marks?.size ?: 0))
        part.marks = part.marks.put(value.slot, values.put(value.shape, joined))
    }

    fun build(
        adjacency: Map<CommonMethod, Collection<CommonMethod>>,
        locals: Map<CommonMethod, Run1StaticLocalProfile>,
    ): Run1StaticProfiles {
        val started = System.nanoTime()
        check()
        val indices = LinkedHashMap<CommonMethod, Int>()
        fun method(m: CommonMethod): Int { check(); return indices.getOrPut(m) { indices.size } }
        locals.keys.forEach { method(it) }
        for ((caller, callees) in adjacency) { method(caller); callees.forEach { method(it) } }
        val methods = indices.keys.toList()
        val graph = Array(methods.size) { index ->
            check()
            val edges = LinkedHashSet<Int>()
            adjacency[methods[index]].orEmpty().forEach { check(); edges += indices.getValue(it) }
            edges.toIntArray()
        }
        val reverse = Array(methods.size) { ArrayList<Int>() }
        var edgeCount = 0L
        graph.forEachIndexed { caller, callees ->
            for (callee in callees) { check(); reverse[callee] += caller; edgeCount++ }
        }

        // Kosaraju with explicit frames; a 32768-method chain never uses the JVM call stack.
        val visited = BooleanArray(methods.size)
        val finish = IntArray(methods.size)
        var finished = 0
        val stack = IntArray(methods.size)
        val offsets = IntArray(methods.size)
        for (start in methods.indices) {
            check()
            if (visited[start]) continue
            var depth = 0
            stack[depth] = start; offsets[depth] = 0; depth++; visited[start] = true
            while (depth > 0) {
                check()
                val at = depth - 1
                val node = stack[at]
                if (offsets[at] == graph[node].size) {
                    finish[finished++] = node; depth--
                } else {
                    val child = graph[node][offsets[at]++]
                    if (!visited[child]) {
                        visited[child] = true; stack[depth] = child; offsets[depth] = 0; depth++
                    }
                }
            }
        }
        val component = IntArray(methods.size) { -1 }
        var componentCount = 0
        for (index in finished - 1 downTo 0) {
            check()
            val start = finish[index]
            if (component[start] >= 0) continue
            var depth = 0
            stack[depth++] = start; component[start] = componentCount
            while (depth > 0) {
                check()
                val node = stack[--depth]
                for (caller in reverse[node]) {
                    check()
                    if (component[caller] < 0) { component[caller] = componentCount; stack[depth++] = caller }
                }
            }
            componentCount++
        }

        val direct = Array(componentCount) { Part() }
        val uniqueDeclarations = Part()
        var directAccesses = 0L; var directAbstractions = 0L
        var directInterests = 0L; var directCleaners = 0L; var directGroups = 0L
        for (index in methods.indices) {
            check()
            val local = locals[methods[index]] ?: continue
            val part = direct[component[index]]
            var localAccesses = persistentHashSetOf<List<AccessorIdx>>()
            for (path in local.accesses) { check(); localAccesses = localAccesses.add(position(path)) }
            var localAbstractions = persistentHashSetOf<List<AccessorIdx>>()
            for (path in local.abstractions) { check(); localAbstractions = localAbstractions.add(position(path)) }
            val localInterests = Part()
            for (value in local.marks) {
                check()
                val compiled = interest(value)
                addInterest(localInterests, compiled)
                addInterest(uniqueDeclarations, compiled)
            }
            directAccesses += localAccesses.size; directAbstractions += localAbstractions.size
            directInterests += localInterests.declarations; directCleaners += local.cleanerMarks.size
            directGroups += localInterests.markCount
            part.accesses = union(part.accesses, localAccesses)
            part.abstractions = union(part.abstractions, localAbstractions)
            val joined = union(part.marks, part.markCount, part.declarations,
                localInterests.marks, localInterests.markCount, localInterests.declarations)
            part.marks = joined.values; part.markCount = joined.groups; part.declarations = joined.declarations
            local.cleanerMarks.forEachId { id ->
                check(); require(id > 0); ap.marks.name(TaintMark(id)); part.cleaners = part.cleaners.add(id)
            }
        }

        val successors = Array(componentCount) { LinkedHashSet<Int>() }
        val parents = Array(componentCount) { ArrayList<Int>() }
        graph.forEachIndexed { caller, callees ->
            for (callee in callees) {
                check()
                val from = component[caller]; val to = component[callee]
                if (from != to && successors[from].add(to)) parents[to] += from
            }
        }
        val remaining = IntArray(componentCount) { successors[it].size }
        val ready = ArrayDeque<Int>()
        remaining.forEachIndexed { index, count -> if (count == 0) ready.addLast(index) }
        val closed = arrayOfNulls<Run1StaticProfile>(componentCount)
        while (ready.isNotEmpty()) {
            check()
            val index = ready.removeFirst()
            val part = direct[index]
            for (callee in successors[index]) {
                check()
                val child = checkNotNull(closed[callee])
                @Suppress("UNCHECKED_CAST")
                part.accesses = union(part.accesses, child.accesses as PersistentSet<List<AccessorIdx>>)
                @Suppress("UNCHECKED_CAST")
                part.abstractions = union(part.abstractions, child.abstractions as PersistentSet<List<AccessorIdx>>)
                val joined = union(part.marks, part.markCount, part.declarations,
                    child.directory(), child.markInterests.size, child.declaredMarkCount)
                part.marks = joined.values
                part.markCount = joined.groups
                part.declarations = joined.declarations
                part.cleaners = union(part.cleaners, child.cleanerIds)
            }
            closed[index] = Run1StaticProfile(part.accesses, part.abstractions, part.marks, part.markCount,
                part.declarations, part.cleaners, cancellation)
            for (parent in parents[index]) { check(); if (--remaining[parent] == 0) ready.addLast(parent) }
        }

        val accessRoots = IdentityHashMap<Any, Boolean>(); val abstractionRoots = IdentityHashMap<Any, Boolean>()
        val markRoots = IdentityHashMap<Any, Boolean>(); val cleanerRoots = IdentityHashMap<Any, Boolean>()
        var transitiveAccesses = 0L; var transitiveAbstractions = 0L
        var transitiveInterests = 0L; var transitiveCleaners = 0L; var maximum = 0L
        for (profile in closed) {
            check()
            val value = checkNotNull(profile)
            accessRoots[value.accesses] = true; abstractionRoots[value.abstractions] = true
            markRoots[value.directory()] = true; cleanerRoots[value.cleanerIds] = true
            transitiveAccesses += value.accesses.size; transitiveAbstractions += value.abstractions.size
            maximum = maxOf(maximum, value.accesses.size.toLong() + value.abstractions.size +
                value.declaredMarkCount + value.cleanerMarkCount)
        }
        // These are metadata payload cells, not a claim about persistent-trie node headers or retained bytes.
        val paths = HashSet<List<AccessorIdx>>()
        positions.values.forEach { check(); paths += it }
        interests.values.forEach { check(); paths += it.pattern.fact.path }
        var pathCells = 0L
        paths.forEach { check(); pathCells += it.size }
        val byMethod = LinkedHashMap<CommonMethod, Run1StaticProfile>()
        val frozenEdges = LinkedHashMap<CommonMethod, Set<CommonMethod>>()
        transitiveAccesses = 0L; transitiveAbstractions = 0L
        transitiveInterests = 0L; transitiveCleaners = 0L
        var transitiveGroups = 0L
        for (index in methods.indices) {
            check()
            val profile = checkNotNull(closed[component[index]])
            byMethod[methods[index]] = profile
            transitiveAccesses += profile.accesses.size; transitiveAbstractions += profile.abstractions.size
            transitiveInterests += profile.declaredMarkCount; transitiveCleaners += profile.cleanerMarkCount
            transitiveGroups += profile.markInterests.size
            var edges = persistentHashSetOf<CommonMethod>()
            for (callee in graph[index]) { check(); edges = edges.add(methods[callee]) }
            frozenEdges[methods[index]] = edges
        }
        check()
        val statistics = Run1StaticProfileStatistics(methods.size, edgeCount, componentCount,
            directAccesses, directAbstractions, directInterests, directCleaners,
            transitiveAccesses, transitiveAbstractions, transitiveInterests, transitiveCleaners,
            positions.size, uniqueDeclarations.declarations, pathCells, 0,
            accessRoots.size, abstractionRoots.size, markRoots.size, cleanerRoots.size,
            adds, reuses, checkpoints, maximum, System.nanoTime() - started,
            directMarkGroups = directGroups, transitiveMarkGroups = transitiveGroups,
            uniqueMarkQueries = uniqueDeclarations.markCount)
        return Run1StaticProfiles(ownedMap(byMethod), ownedMap(frozenEdges), statistics)
    }
}
