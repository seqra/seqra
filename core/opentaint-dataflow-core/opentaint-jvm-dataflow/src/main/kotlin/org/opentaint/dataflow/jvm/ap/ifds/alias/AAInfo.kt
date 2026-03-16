package org.opentaint.dataflow.jvm.ap.ifds.alias

import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor

sealed interface AAInfo : Comparable<AAInfo> {
    val infoKind: Int
    val ctx: ContextInfo

    fun compareInfo(other: AAInfo): Int

    override fun compareTo(other: AAInfo): Int {
        val kindCmp = infoKind.compareTo(other.infoKind)
        if (kindCmp != 0) return kindCmp

        val ctxCmp = ctx.compareTo(other.ctx)
        if (ctxCmp != 0) return ctxCmp

        return compareInfo(other)
    }
}

data class Unknown(val stmt: Stmt, override val ctx: ContextInfo) : AAInfo {
    override val infoKind: Int get() = 3

    override fun compareInfo(other: AAInfo): Int = compare(other as Unknown)
    fun compare(other: Unknown): Int = stmt.compareTo(other.stmt)
}

data class CallReturn(val stmt: Stmt.Call, override val ctx: ContextInfo) : AAInfo {
    override val infoKind: Int = 1

    override fun compareInfo(other: AAInfo): Int = compare(other as CallReturn)
    fun compare(other: CallReturn): Int = stmt.compareTo(other.stmt)
}

sealed interface LocalAlias : AAInfo {
    data class SimpleLoc(val loc: RefValue) : LocalAlias {
        override val infoKind: Int get() = 2

        override val ctx get() = if (loc is RefValue.Local) loc.ctx else ContextInfo.rootContext

        override fun compareInfo(other: AAInfo): Int = compare(other as SimpleLoc)
        fun compare(other: SimpleLoc): Int = loc.compareTo(other.loc)
    }

    data class Alloc(val stmt: Stmt, override val ctx: ContextInfo) : LocalAlias {
        override val infoKind: Int get() = 0
        override fun compareInfo(other: AAInfo): Int = compare(other as Alloc)
        fun compare(other: Alloc): Int = stmt.compareTo(other.stmt)
    }
}

sealed interface AAHeapAccessor : Comparable<AAHeapAccessor> {
    val isImmutable: Boolean

    val accessorKind: Int

    fun compareAccessor(accessor: AAHeapAccessor): Int

    override fun compareTo(other: AAHeapAccessor): Int {
        val kindCmp = accessorKind.compareTo(other.accessorKind)
        if (kindCmp != 0) return kindCmp
        return compareAccessor(other)
    }
}

data class FieldAlias(
    val field: AliasAccessor.Field,
    override val isImmutable: Boolean,
) : AAHeapAccessor {
    override val accessorKind: Int get() = 0
    override fun compareAccessor(accessor: AAHeapAccessor): Int = compare(accessor as FieldAlias)
    fun compare(other: FieldAlias): Int = comparator.compare(this, other)

    companion object {
        private val fieldComparator = compareBy<AliasAccessor.Field> { it.fieldName }
            .thenComparing { it.className }
            .thenComparing { it.fieldType }

        private val comparator = compareBy<FieldAlias> { it.isImmutable }.thenComparing({ it.field }, fieldComparator)
    }
}

data object ArrayAlias : AAHeapAccessor {
    override val isImmutable: Boolean get() = false
    override val accessorKind: Int get() = 1
    override fun compareAccessor(accessor: AAHeapAccessor): Int = 0
}

data class HeapAlias(
    val instance: Int,
    val heapAccessor: AAHeapAccessor
) : AAInfo {
    override val infoKind: Int get() = 4
    override val ctx get() = ContextInfo.rootContext

    override fun compareInfo(other: AAInfo): Int = compare(other as HeapAlias)

    fun compare(other: HeapAlias): Int {
        val instanceCmp = instance.compareTo(other.instance)
        if (instanceCmp != 0) return instanceCmp
        return heapAccessor.compareTo(other.heapAccessor)
    }
}
