package org.opentaint.dataflow.bidi.ap

/** Ownership is independent of method specialization and execution mode. */
@JvmInline value class GroupId(val value: Int)
@JvmInline value class FullMarkId(val value: Int)
sealed interface MarkGroupKey {
    data class Rule(val outerRuleId: String) : MarkGroupKey
    data class Model(val declaredId: String) : MarkGroupKey
    data object SharedModel : MarkGroupKey
}
data class ExactMark(val group: GroupId, val full: FullMarkId)
sealed interface MarkAtomKey {
    data object ControlZero : MarkAtomKey
    data class Group(val id: GroupId) : MarkAtomKey
    data class Full(val mark: ExactMark) : MarkAtomKey
}
enum class MarkMode { GROUP, FULL }

/** A group ID set is never a set of opaque runtime atom IDs. */
class GroupSet(ids: IntArray) {
    private val data = ids.sortedDistinct()
    val ids: IntArray get() = data.copyOf()
    val size: Int get() = data.size
    val isEmpty: Boolean get() = data.isEmpty()
    operator fun contains(group: GroupId): Boolean = data.binarySearch(group.value) >= 0
    operator fun plus(other: GroupSet): GroupSet = GroupSet(sortedUnion(data, other.data))
    operator fun plus(group: GroupId): GroupSet = GroupSet(sortedUnion(data, intArrayOf(group.value)))
    infix fun intersect(other: GroupSet): GroupSet = GroupSet(data.filter { other.data.binarySearch(it) >= 0 }.toIntArray())
    fun forEach(visit: (GroupId) -> Unit) { data.forEach { visit(GroupId(it)) } }
    fun toList(): List<GroupId> = data.map(::GroupId)
    fun isSubsetOf(other: GroupSet): Boolean = sortedSubset(data, other.data)
    operator fun minus(other: GroupSet): GroupSet = GroupSet(data.filter { other.data.binarySearch(it) < 0 }.toIntArray())
    override fun equals(other: Any?): Boolean = other is GroupSet && data.contentEquals(other.data)
    override fun hashCode(): Int = data.contentHashCode()
    override fun toString(): String = data.contentToString()
    companion object {
        val EMPTY = GroupSet(IntArray(0))
        fun of(group: GroupId): GroupSet = GroupSet(intArrayOf(group.value))
        fun of(groups: Iterable<GroupId>): GroupSet = GroupSet(groups.map { it.value }.toIntArray())
        fun fromIds(ids: IntArray): GroupSet = GroupSet(ids)
    }
}

interface RuleMarkCatalog {
    fun reserveFull(name: String): TaintMark
    fun register(name: String, owner: MarkGroupKey)
    fun finishOwnershipCensus()
    fun group(id: GroupId): TaintMark
    fun key(atom: TaintMark): MarkAtomKey
    fun owner(full: FullMarkId): GroupId
    fun eligibleMembers(group: GroupId): List<TaintMark>
}

/** Full handles can be cached before ownership is known. Census completion
 * assigns only undeclared model names to SharedModel; it never guesses a rule ID. */
class MarkTable : RuleMarkCatalog {
    private val fullNames = ArrayList<String>()
    private val fullByName = LinkedHashMap<String, FullMarkId>()
    private val groupKeys = ArrayList<MarkGroupKey>()
    private val groupByKey = LinkedHashMap<MarkGroupKey, GroupId>()
    private val owners = HashMap<FullMarkId, GroupId>()
    private val eligible = LinkedHashSet<FullMarkId>()
    private val fullAtoms = HashMap<FullMarkId, TaintMark>()
    private val groupAtoms = HashMap<GroupId, TaintMark>()
    private sealed interface Handle {
        data object Zero : Handle
        data class Full(val id: FullMarkId) : Handle
        data class Group(val id: GroupId) : Handle
    }
    private val handles = ArrayList<Handle>().apply { add(Handle.Zero) }
    private var fixtureOwners = false
    @Volatile var isOwnershipCensusComplete: Boolean = false
        private set

    @Synchronized override fun reserveFull(name: String): TaintMark {
        val id = fullByName.getOrPut(name) { FullMarkId(fullNames.size).also { fullNames += name } }
        val atom = fullAtoms.getOrPut(id) { TaintMark(handles.size).also { handles += Handle.Full(id) } }
        if (fixtureOwners && id !in owners) {
            val key = MarkGroupKey.Model(name)
            owners[id] = groupByKey.getOrPut(key) { GroupId(groupKeys.size).also { groupKeys += key } }
            eligible += id
        }
        return atom
    }
    fun mark(name: String): TaintMark = reserveFull(name)
    fun full(name: String): TaintMark = reserveFull(name)

    @Synchronized override fun register(name: String, owner: MarkGroupKey) {
        val full = fullByName.getOrPut(name) { FullMarkId(fullNames.size).also { fullNames += name } }
        fullAtoms.getOrPut(full) { TaintMark(handles.size).also { handles += Handle.Full(full) } }
        val g = groupByKey.getOrPut(owner) { GroupId(groupKeys.size).also { groupKeys += owner } }
        val old = owners.putIfAbsent(full, g)
        require(old == null || old == g) { "Conflicting owners for full mark $name" }
    }
    fun declareOwner(name: String, owner: MarkGroupKey) = register(name, owner)
    fun declareModel(name: String, owner: String) = register(name, MarkGroupKey.Model(owner))
    @Synchronized fun observeModelReference(name: String): TaintMark = reserveFull(name).also {
        val id = (handles[it.id] as Handle.Full).id
        if (isOwnershipCensusComplete && id !in owners) register(name, MarkGroupKey.SharedModel)
        eligible += id
    }
    @Synchronized fun markEligible(atom: TaintMark) {
        val handle = handles[atom.id]
        require(handle is Handle.Full) { "Only a full mark can be eligible" }
        if (isOwnershipCensusComplete && handle.id !in owners) register(fullNames[handle.id.value], MarkGroupKey.SharedModel)
        eligible += handle.id
    }
    @Synchronized override fun finishOwnershipCensus() {
        for (id in fullNames.indices.map(::FullMarkId)) if (id !in owners)
            register(fullNames[id.value], MarkGroupKey.SharedModel)
        isOwnershipCensusComplete = true
    }
    /** Synthetic methods explicitly declare fixture model owners, then seal. */
    @Synchronized fun finishFixtureOwnershipCensus() {
        fixtureOwners = true
        for (id in fullNames.indices.map(::FullMarkId)) if (id !in owners)
            register(fullNames[id.value], MarkGroupKey.Model(fullNames[id.value]))
        eligible += fullByName.values
        finishOwnershipCensus()
    }
    @Synchronized override fun group(id: GroupId): TaintMark {
        require(id.value in groupKeys.indices) { "Unknown group $id" }
        return groupAtoms.getOrPut(id) { TaintMark(handles.size).also { handles += Handle.Group(id) } }
    }
    @Synchronized override fun owner(full: FullMarkId): GroupId {
        check(isOwnershipCensusComplete) { "Finish the mark ownership census before group execution" }
        return checkNotNull(owners[full]) { "Full mark has no declared owner: $full" }
    }
    @Synchronized override fun key(atom: TaintMark): MarkAtomKey = when (val h = handles[atom.id]) {
        Handle.Zero -> MarkAtomKey.ControlZero
        is Handle.Group -> MarkAtomKey.Group(h.id)
        is Handle.Full -> MarkAtomKey.Full(ExactMark(owner(h.id), h.id))
    }
    @Synchronized fun name(atom: TaintMark): String = when (val h = handles[atom.id]) {
        Handle.Zero -> "<zero>"
        is Handle.Full -> fullNames[h.id.value]
        is Handle.Group -> "<group:${groupKeys[h.id.value]}>"
    }
    @Synchronized fun isGroup(atom: TaintMark): Boolean = handles[atom.id] is Handle.Group
    @Synchronized fun fullId(atom: TaintMark): FullMarkId? = (handles[atom.id] as? Handle.Full)?.id
    @Synchronized fun groupId(atom: TaintMark): GroupId? = (handles[atom.id] as? Handle.Group)?.id
    fun groupOf(atom: TaintMark): GroupId = when (val k = key(atom)) {
        is MarkAtomKey.Group -> k.id
        is MarkAtomKey.Full -> k.mark.group
        MarkAtomKey.ControlZero -> error("Control ZERO has no group")
    }
    fun runtimeAtom(atom: TaintMark, mode: MarkMode): TaintMark {
        if (atom == TaintMark.ZERO) return atom
        if (mode == MarkMode.FULL) {
            require(!isGroup(atom)) { "An executable full fact cannot contain a group selector" }
            return atom
        }
        return group(groupOf(atom))
    }
    fun coversAtom(guide: TaintMark, actual: TaintMark): Boolean {
        if (guide == actual) return true
        if (guide == TaintMark.ZERO || actual == TaintMark.ZERO || !isGroup(guide)) return false
        return groupOf(guide) == groupOf(actual)
    }
    @Synchronized override fun eligibleMembers(group: GroupId): List<TaintMark> =
        eligible.filter { owner(it) == group }.map { checkNotNull(fullAtoms[it]) }
    @Synchronized fun groupAtoms(): List<TaintMark> =
        eligible.map { owner(it) }.distinct().map(::group)
    fun groupAtoms(groups: GroupSet): MarkSet = MarkSet(groups.ids.map { group(GroupId(it)).id }.toIntArray())
}

class RunMarkView(val mode: MarkMode, val catalog: MarkTable) {
    fun literal(atom: TaintMark): TaintMark = catalog.runtimeAtom(atom, mode)
    fun target(atom: TaintMark): TaintMark = catalog.runtimeAtom(atom, mode)
}
