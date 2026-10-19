package org.opentaint.dataflow.bidi.ap

import java.util.Collections

/** Ownership is independent of method specialization and execution mode. */
@JvmInline value class GroupId(val value: Int)
@JvmInline value class FullMarkId(val value: Int)
sealed interface MarkGroupKey {
    data class Rule(val outerRuleId: String) : MarkGroupKey
    data class Model(val declaredId: String) : MarkGroupKey
    data object SharedModel : MarkGroupKey
}

/** Names and claims are collected before method binding. Providers export detached copies. */
data class RawMarkDeclarations(
    val names: Set<String>,
    val explicitOwners: Map<String, MarkGroupKey>,
    val primitiveByOwner: Map<MarkGroupKey, Boolean>,
) {
    companion object { val EMPTY = RawMarkDeclarations(emptySet(), emptyMap(), emptyMap()) }
}

@JvmInline value class GroupName(val encoded: String)
data class FullKey(val group: GroupName, val name: String)

/** Includes owner kind and a final mode, even when an owner ID contains delimiters. */
fun encodeGroup(owner: MarkGroupKey, primitive: Boolean): GroupName {
    val payload = when (owner) {
        is MarkGroupKey.Rule -> "R:${owner.outerRuleId}"
        is MarkGroupKey.Model -> "M:${owner.declaredId}"
        MarkGroupKey.SharedModel -> "S:"
    }
    val mode = if (primitive) "%%primitive%%" else "%%ordinary%%"
    return GroupName("@group:${payload.length}:$payload;$mode")
}

interface MarkDeclarations {
    fun fullKey(name: String): FullKey
}

/** One analysis retains only this immutable index, not the declaration builders. */
class FrozenMarkDeclarations(keys: Map<String, FullKey>) : MarkDeclarations {
    private val byName = Collections.unmodifiableMap(LinkedHashMap(keys))
    init { require(byName.all { (name, key) -> name == key.name }) { "Full-key name mismatch" } }
    override fun fullKey(name: String): FullKey =
        requireNotNull(byName[name]) { "Undeclared executable mark: $name" }
    companion object { val EMPTY = FrozenMarkDeclarations(emptyMap()) }
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

/** All access is under the enclosing MarkTable monitor. */
private class MarkKeyInterner<K> {
    private val byKey = HashMap<K, Int>()
    private val keys = ArrayList<K>()
    fun intern(key: K): Int = byKey.getOrPut(key) { keys.size.also { keys.add(key) } }
    fun key(id: Int): K = keys[id]
    fun existingId(key: K): Int = checkNotNull(byKey[key])
}

/** Canonical immutable keys. Ownership is complete before any handle is published. */
class MarkTable(private val declarations: MarkDeclarations) {
    private val groups = MarkKeyInterner<GroupName>()
    private val fulls = MarkKeyInterner<FullKey>()
    private val atoms = MarkKeyInterner<MarkAtomKey>().also {
        check(it.intern(MarkAtomKey.ControlZero) == TaintMark.ZERO.id)
    }

    @Synchronized fun full(name: String): TaintMark {
        val key = declarations.fullKey(name)
        val group = GroupId(groups.intern(key.group))
        val full = FullMarkId(fulls.intern(key))
        return TaintMark(atoms.intern(MarkAtomKey.Full(ExactMark(group, full))))
    }
    fun mark(name: String): TaintMark = full(name)
    @Synchronized fun group(id: GroupId): TaintMark {
        groups.key(id.value)
        return TaintMark(atoms.intern(MarkAtomKey.Group(id)))
    }
    @Synchronized fun owner(full: FullMarkId): GroupId =
        GroupId(groups.existingId(fulls.key(full.value).group))
    @Synchronized fun key(atom: TaintMark): MarkAtomKey = atoms.key(atom.id)
    @Synchronized fun name(atom: TaintMark): String = when (val key = atoms.key(atom.id)) {
        MarkAtomKey.ControlZero -> "<zero>"
        is MarkAtomKey.Full -> fulls.key(key.mark.full.value).name
        is MarkAtomKey.Group -> groups.key(key.id.value).encoded
    }
    @Synchronized fun isFull(atom: TaintMark): Boolean = atoms.key(atom.id) is MarkAtomKey.Full
    @Synchronized fun isGroup(atom: TaintMark): Boolean = atoms.key(atom.id) is MarkAtomKey.Group
    @Synchronized fun fullId(atom: TaintMark): FullMarkId? = (atoms.key(atom.id) as? MarkAtomKey.Full)?.mark?.full
    @Synchronized fun groupId(atom: TaintMark): GroupId? = (atoms.key(atom.id) as? MarkAtomKey.Group)?.id
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
    fun atomsFor(groups: GroupSet): MarkSet = MarkSet(groups.ids.map { group(GroupId(it)).id }.toIntArray())
}

class RunMarkView(val mode: MarkMode, val catalog: MarkTable) {
    fun literal(atom: TaintMark): TaintMark = catalog.runtimeAtom(atom, mode)
    fun target(atom: TaintMark): TaintMark = catalog.runtimeAtom(atom, mode)
}
