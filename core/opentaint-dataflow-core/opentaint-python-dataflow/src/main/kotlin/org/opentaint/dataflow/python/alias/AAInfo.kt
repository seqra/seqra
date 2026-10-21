package org.opentaint.dataflow.python.alias

import org.opentaint.dataflow.ap.ifds.analysis.alias.AAHeapAccessor
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.ContextInfo

data class Unknown(val instIdx: Int, override val ctx: ContextInfo) : AAInfo {
    override val infoKind: Int get() = 3

    override fun compareInfo(other: AAInfo): Int = instIdx.compareTo((other as Unknown).instIdx)
}

data class CallReturn(val instIdx: Int, override val ctx: ContextInfo) : AAInfo {
    override val infoKind: Int get() = 1

    override fun compareInfo(other: AAInfo): Int = instIdx.compareTo((other as CallReturn).instIdx)
}

sealed interface LocalAlias : AAInfo {
    data class SimpleLoc(val loc: RefValue) : LocalAlias {
        override val infoKind: Int get() = 2

        override val ctx: ContextInfo
            get() = if (loc is RefValue.Local) loc.ctx else ContextInfo.rootContext

        override fun compareInfo(other: AAInfo): Int = loc.compareTo((other as SimpleLoc).loc)
    }

    data class Alloc(val instIdx: Int, override val ctx: ContextInfo) : LocalAlias {
        override val infoKind: Int get() = 0

        override fun compareInfo(other: AAInfo): Int = instIdx.compareTo((other as Alloc).instIdx)
    }
}

data class FieldAlias(val field: AliasAccessor.Field) : AAHeapAccessor {
    override val isImmutable: Boolean get() = false
    override val accessorKind: Int get() = 0

    override fun compareAccessor(accessor: AAHeapAccessor): Int =
        field.name.compareTo((accessor as FieldAlias).field.name)
}

data object ArrayAlias : AAHeapAccessor {
    override val isImmutable: Boolean get() = false
    override val accessorKind: Int get() = 1

    override fun compareAccessor(accessor: AAHeapAccessor): Int = 0
}
