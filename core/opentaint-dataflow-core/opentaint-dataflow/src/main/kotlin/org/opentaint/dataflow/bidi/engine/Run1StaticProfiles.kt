package org.opentaint.dataflow.bidi.engine

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ownedList
import org.opentaint.dataflow.bidi.interp.ownedMap
import org.opentaint.dataflow.bidi.interp.ownedSet
import org.opentaint.dataflow.bidi.store.RequestMarkCursor
import org.opentaint.dataflow.bidi.store.RequestMarkSelection
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.CommonMethod

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
            marks.forEachId { id -> ids = ids.add(id); hash += id }
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
    wideClasses: Set<Int> = emptySet(),
    val allStatic: Boolean = false,
    classPoints: Set<Int> = emptySet(),
    val staticMayWrite: Boolean = false,
) {
    val accesses: List<List<AccessorIdx>> = ownedList(accesses.map(::ownedSlot))
    val abstractions: List<List<AccessorIdx>> = ownedList(abstractions.map(::ownedSlot))
    val marks: List<StaticMarkInterest> = ownedList(marks)
    val wideClasses: Set<Int> = ownedSet(wideClasses)
    val classPoints: Set<Int> = ownedSet(classPoints)

    companion object {
        @JvmField val EMPTY = Run1StaticLocalProfile()
    }
}

private fun ownedSlot(path: List<AccessorIdx>): List<AccessorIdx> {
    require(path.size <= 2) { "A static slot has at most its class and field accessors" }
    require(path.isEmpty() || path.first().isClass()) { "A static slot must start with its class" }
    require(path.size < 2 || path[1].isFieldAccessor()) { "A static field slot needs a field second accessor" }
    return ownedList(path)
}

/** Mark-independent structural metadata shared by an SCC. Actual static marks
 * are admitted directly; no inherited static request payload is retained. */
class Run1StaticProfile internal constructor(
    val footprint: Run1StaticFootprint,
    val staticMayWrite: Boolean,
) {
    // Compatibility observations are explicit empty views, not another plan.
    val precomputedRequests: FrozenRequestBase get() = EmptyFrozenRequestBase
    val classPointClasses: Set<Int> get() = footprint.classPoints
    fun classPointMarks(cls: Int): RequestMarkSet = RequestMarkSet.EMPTY
    fun wideRequests(input: Facts): RequestRows = emptyMap()
    fun covers(initial: InitialAp, mark: TaintMark): Boolean = false
    val markInterests: Set<StaticMarkInterest> get() = emptySet()
    val declaredMarkCount: Int get() = 0
    val cleanerMarkCount: Int get() = 0
    val cleanerMarks: RequestMarkSelection get() = RequestMarkSet.EMPTY
    internal fun directory(): StaticDirectory = persistentHashMapOf()
    internal fun visitOwnedRequestMarks(visit: (RequestMarkSet) -> Unit) = Unit
    fun markInterestsAt(slot: List<AccessorIdx>): Sequence<StaticMarkInterest> = emptySequence()
}

/** Direct counters describe copied local templates. Historical transitive
 * read/mark and B payload metrics remain explicit zero: only class consumers,
 * legal coordinates and the write bit close over calls. Local mark templates
 * are discarded after their structural positions are collected. These numeric
 * source counters do not describe retained marks, live facts or request work. */
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
    val precomputedBaseRoots: Int = 0,
    val physicalPrecomputedRows: Long = 0,
    val logicalMethodRequestRows: Long = 0,
    val logicalMethodRequestMembers: Long = 0,
    val runtimeMarkSetRoots: Int = 0,
    val uniqueBaseLogicalRows: Long = 0,
    val uniqueBaseLogicalMembers: Long = 0,
    val sparseFieldRows: Long = 0,
    val selectedSetRoots: Int = 0,
    val selectedFieldSlots: Long = 0,
    val commonClassMapRoots: Int = 0,
    val commonClassRows: Long = 0,
    val sparseFieldMapRoots: Int = 0,
    val staticMayWriteMethods: Int = 0,
)

/** Immutable copied graph plus one profile per SCC. Release this owner after run-1 workers join. */
class Run1StaticProfiles internal constructor(
    private val profiles: Map<CommonMethod, Run1StaticProfile>,
    private val targets: Map<CommonMethod, Set<CommonMethod>>,
    private val readers: Map<CommonMethod, Set<Run1Slot.StaticField>>,
    val statistics: Run1StaticProfileStatistics,
    val reachability: FixedPrescanReachability,
) {
    val methods: Set<CommonMethod> get() = profiles.keys
    operator fun get(method: CommonMethod): Run1StaticProfile = checkNotNull(profiles[method]) {
        "Runtime method is absent from the frozen run-1 static catalog: $method"
    }
    fun contains(method: CommonMethod): Boolean = profiles.containsKey(method)
    fun containsTarget(caller: CommonMethod, callee: CommonMethod): Boolean = targets[caller]?.contains(callee) == true
    /** Local reader locations only. They neither inherit across calls nor gate supplied entry bindings. */
    fun localReads(method: CommonMethod): Set<Run1Slot.StaticField> = readers[method].orEmpty()
    fun footprints(): Map<CommonMethod, Run1StaticFootprint> = ownedMap(profiles.mapValues { it.value.footprint })

    companion object {
        fun build(
            ap: ApManager,
            adjacency: Map<CommonMethod, Collection<CommonMethod>>,
            locals: Map<CommonMethod, Run1StaticLocalProfile>,
            declaredFieldUniverse: Set<Run1Slot.StaticField> = emptySet(),
            declaredClassUniverse: Set<Int> = emptySet(),
        ): Run1StaticProfiles = StaticProfileBuilder(ap).build(adjacency, locals,
            declaredFieldUniverse, declaredClassUniverse)
    }
}

private class StaticProfileBuilder(private val ap: ApManager) {
    private var checkpoints = 0L
    private var adds = 0L
    private var reuses = 0L
    private val positions = HashMap<List<AccessorIdx>, List<AccessorIdx>>()
    private fun check() { checkpoints++; ap.cancellation.checkpoint() }
    private fun position(value: List<AccessorIdx>): List<AccessorIdx> {
        check()
        return positions.getOrPut(value) {
            ownedSlot(value).also { path -> path.forEach { check(); ap.accessors.accessor(it) } }
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
    private class Part {
        var wideClasses: PersistentSet<Int> = persistentHashSetOf()
        var classPoints: PersistentSet<Int> = persistentHashSetOf()
        var allStatic = false
        var staticMayWrite = false
    }
    fun build(
        adjacency: Map<CommonMethod, Collection<CommonMethod>>,
        locals: Map<CommonMethod, Run1StaticLocalProfile>,
        declaredFieldUniverse: Set<Run1Slot.StaticField>,
        declaredClassUniverse: Set<Int>,
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


        val localReaders = LinkedHashMap<CommonMethod, Set<Run1Slot.StaticField>>()
        val direct = Array(componentCount) { Part() }
        val uniqueQueries = HashSet<StaticMarkShape>()
        var directAccesses = 0L; var directAbstractions = 0L
        var directMarks = 0L; var directCleaners = 0L; var directGroups = 0L
        for (index in methods.indices) {
            check()
            val local = locals[methods[index]]
            val part = direct[component[index]]
            if (local == null) { part.staticMayWrite = true; continue }
            val accesses = local.accesses.map { position(it) }.toSet()
            val abstractions = local.abstractions.map { position(it) }.toSet()
            directAccesses += accesses.size; directAbstractions += abstractions.size
            val reads = abstractions.filter { it.size == 2 }.map { Run1Slot.StaticField(it[0], it[1]) }.toSet()
            if (reads.isNotEmpty()) localReaders[methods[index]] = ownedSet(reads)
            // Local templates certify coordinates/class consumers. They do not
            // pre-admit marks or propagate mark sets along the prescan graph.
            for (interest in local.marks) {
                check(); position(interest.slot)
                interest.pattern.fact.path.forEach { check(); ap.accessors.accessor(it) }
                for (id in interest.marks.ids) { check(); ap.marks.name(TaintMark(id)) }
                uniqueQueries += interest.shape
                directMarks += interest.marks.size; directGroups++
                val path = interest.pattern.fact.path
                when {
                    path.size == 1 -> {
                        part.classPoints = part.classPoints.add(path[0])
                        if (interest.pattern.fact.tail != Tail.EXACT) part.wideClasses = part.wideClasses.add(path[0])
                    }
                    path.isEmpty() && interest.pattern.fact.tail != Tail.EXACT -> part.allStatic = true
                }
            }
            local.cleanerMarks.forEachId { check(); ap.marks.name(TaintMark(it)) }
            directCleaners += local.cleanerMarks.size
            part.wideClasses = union(part.wideClasses, persistentHashSetOf<Int>().addAll(local.wideClasses))
            part.classPoints = union(part.classPoints, persistentHashSetOf<Int>().addAll(local.classPoints))
            part.allStatic = part.allStatic || local.allStatic
            part.staticMayWrite = part.staticMayWrite || local.staticMayWrite
        }
        val universe = ownedSet(positions.values.asSequence().filter { it.size == 2 }
            .map { Run1Slot.StaticField(it[0], it[1]) }.toSet() + declaredFieldUniverse.onEach {
                check(); ap.accessors.accessor(it.cls); ap.accessors.accessor(it.field)
            })
        val classes = ownedSet(positions.values.mapNotNull { it.firstOrNull() }.toSet() +
            direct.flatMap { it.classPoints + it.wideClasses } + universe.map { it.cls } +
            declaredClassUniverse.onEach { cls -> check(); require(cls.isClass()); ap.accessors.accessor(cls) })
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
        val closedParts = arrayOfNulls<Part>(componentCount)
        while (ready.isNotEmpty()) {
            check(); val index = ready.removeFirst(); val part = direct[index]
            for (callee in successors[index]) {
                check(); val child = checkNotNull(closedParts[callee])
                part.wideClasses = union(part.wideClasses, child.wideClasses)
                part.classPoints = union(part.classPoints, child.classPoints)
                part.allStatic = part.allStatic || child.allStatic
                part.staticMayWrite = part.staticMayWrite || child.staticMayWrite
            }
            closedParts[index] = part
            for (parent in parents[index]) { check(); if (--remaining[parent] == 0) ready.addLast(parent) }
        }
        val profiles = Array(componentCount) { index ->
            check(); val part = checkNotNull(closedParts[index])
            val points = if (part.allStatic) classes else ownedSet(part.classPoints + part.wideClasses)
            Run1StaticProfile(Run1StaticFootprint(emptySet(), fieldUniverse = universe,
                classPoints = points, classUniverse = classes), part.staticMayWrite)
        }
        val byMethod = LinkedHashMap<CommonMethod, Run1StaticProfile>()
        val frozenEdges = LinkedHashMap<CommonMethod, Set<CommonMethod>>()
        for (index in methods.indices) {
            check(); byMethod[methods[index]] = profiles[component[index]]
            frozenEdges[methods[index]] = ownedSet(graph[index].map { methods[it] })
        }
        val statistics = Run1StaticProfileStatistics(methods.size, edgeCount, componentCount,
            directAccesses, directAbstractions, directMarks, directCleaners,
            0, 0, 0, 0, positions.size, uniqueQueries.size, positions.values.sumOf { it.size.toLong() }, 0,
            0, 0, 0, 0, adds, reuses, checkpoints, 0, System.nanoTime() - started,
            directMarkGroups = directGroups, transitiveMarkGroups = 0, uniqueMarkQueries = uniqueQueries.size,
            staticMayWriteMethods = byMethod.values.count { it.staticMayWrite })
        val targets = ownedMap(frozenEdges)
        val methodComponents = ownedMap(methods.indices.associate { methods[it] to component[it] })
        val reachability = FixedPrescanReachability.fromComponents(methodComponents,
            Array(componentCount) { successors[it].toIntArray() }, targets, ap.cancellation::checkpoint)
        return Run1StaticProfiles(ownedMap(byMethod), targets, ownedMap(localReaders), statistics, reachability)
    }
}
