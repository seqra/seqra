package org.opentaint.dataflow.bidi.ap

private enum class FullAtoms { UNKNOWN, ABSENT, PRESENT }

/** FLOW restrictions denote either an entire owner group or an exact full
 * mark. The atom IDs are private representation, never raw set semantics. */
class MarkExclusions private constructor(
    private val atoms: MarkSet,
    private val catalog: MarkTable?,
    private val fullAtoms: FullAtoms,
) {
    val isEmpty: Boolean get() = atoms.isEmpty
    val ids: IntArray get() = atoms.ids
    internal val data: IntArray get() = atoms.data
    fun withCatalog(table: MarkTable): MarkExclusions {
        require(catalog == null || catalog === table) { "Mixed mark catalogs in FLOW exclusions" }
        if (catalog === table) return this
        return normalized(atoms, table)
    }
    operator fun contains(mark: TaintMark): Boolean = excludes(mark, catalog)
    private fun excludes(mark: TaintMark, table: MarkTable?): Boolean {
        if (mark in atoms) return true
        if (mark == TaintMark.ZERO || table == null || table.isGroup(mark)) return false
        return atoms.data.any { id ->
            val atom = TaintMark(id)
            table.isGroup(atom) && table.groupOf(atom) == table.groupOf(mark)
        }
    }
    operator fun plus(mark: TaintMark): MarkExclusions {
        val combined = atoms + mark
        if (combined === atoms) return this
        return normalized(combined, catalog)
    }
    operator fun plus(other: MarkExclusions): MarkExclusions = union(other)
    operator fun plus(other: MarkSet): MarkExclusions = union(fromMarks(other))
    fun union(other: MarkExclusions): MarkExclusions {
        val table = tableWith(other)
        val combined = atoms + other.atoms
        if (combined === atoms && catalog === table) return this
        return normalized(combined, table)
    }
    fun intersect(other: MarkExclusions): MarkExclusions {
        val table = tableWith(other)
        val left = atoms.data.filter { other.excludes(TaintMark(it), table) }
        val right = other.atoms.data.filter { excludes(TaintMark(it), table) }
        return normalized(MarkSet((left + right).toIntArray()), table)
    }
    fun isSubsetOf(other: MarkExclusions): Boolean {
        val table = tableWith(other)
        return atoms.data.all { id ->
            val atom = TaintMark(id)
            // A finite list of full members does not cover a group, even if
            // it happens to contain the current catalog's complete population.
            if (table?.isGroup(atom) == true) atom in other.atoms else other.excludes(atom, table)
        }
    }
    fun filterMarks(marks: MarkSet): MarkSet = MarkSet(marks.data.filter { TaintMark(it) !in this }.toIntArray())
    fun projected(table: MarkTable, mode: MarkMode): MarkExclusions {
        if (mode == MarkMode.FULL) return withCatalog(table)
        if (isEmpty) return EMPTY
        if (catalog === table && fullAtoms == FullAtoms.ABSENT) return this
        return normalized(MarkSet(atoms.data.map { table.runtimeAtom(TaintMark(it), mode).id }.toIntArray()), table)
    }
    fun forEach(visit: (TaintMark) -> Unit) = atoms.data.forEach { visit(TaintMark(it)) }
    private fun tableWith(other: MarkExclusions): MarkTable? {
        require(catalog == null || other.catalog == null || catalog === other.catalog) { "Mixed mark catalogs" }
        return catalog ?: other.catalog
    }
    override fun equals(other: Any?): Boolean = other is MarkExclusions && atoms == other.atoms
    override fun hashCode(): Int = atoms.hashCode()
    override fun toString(): String = atoms.toString()
    companion object {
        val EMPTY = MarkExclusions(MarkSet.EMPTY, null, FullAtoms.ABSENT)
        fun fromMarks(marks: MarkSet, catalog: MarkTable? = null): MarkExclusions = normalized(marks, catalog)
        fun of(catalog: MarkTable, atom: TaintMark): MarkExclusions = fromMarks(MarkSet(intArrayOf(atom.id)), catalog)
        fun of(catalog: MarkTable, atoms: Iterable<TaintMark>): MarkExclusions =
            normalized(MarkSet(atoms.map { it.id }.toIntArray()), catalog)
        private fun normalized(atoms: MarkSet, catalog: MarkTable?): MarkExclusions {
            if (atoms.isEmpty) return EMPTY
            if (catalog == null) return MarkExclusions(atoms, null, FullAtoms.UNKNOWN)
            var groups: MutableSet<GroupId>? = null
            var fullAtoms = FullAtoms.ABSENT
            for (id in atoms.data) when (val key = catalog.key(TaintMark(id))) {
                MarkAtomKey.ControlZero -> Unit
                is MarkAtomKey.Group -> {
                    val present = groups ?: HashSet<GroupId>().also { groups = it }
                    present.add(key.id)
                }
                is MarkAtomKey.Full -> fullAtoms = FullAtoms.PRESENT
            }
            val presentGroups = groups ?: return MarkExclusions(atoms, catalog, fullAtoms)
            // Metadata describes retained atoms, not the input before GROUP shadowing.
            var retainedFull = FullAtoms.ABSENT
            val kept = atoms.data.filter { id ->
                when (val key = catalog.key(TaintMark(id))) {
                    MarkAtomKey.ControlZero, is MarkAtomKey.Group -> true
                    is MarkAtomKey.Full -> (key.mark.group !in presentGroups).also {
                        if (it) retainedFull = FullAtoms.PRESENT
                    }
                }
            }
            return MarkExclusions(MarkSet(kept.toIntArray()), catalog, retainedFull)
        }
    }
}

operator fun MarkSet.minus(excluded: MarkExclusions): MarkSet = excluded.filterMarks(this)
