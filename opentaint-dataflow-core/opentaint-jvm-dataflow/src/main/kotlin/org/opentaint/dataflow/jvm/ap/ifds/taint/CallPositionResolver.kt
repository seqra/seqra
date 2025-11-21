package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.util.Maybe
import org.opentaint.util.flatFmap
import org.opentaint.util.fmap
import org.opentaint.util.toMaybe

sealed interface PositionAccess {
    data class Simple(val base: AccessPathBase) : PositionAccess
    data class Complex(val base: PositionAccess, val accessor: Accessor) : PositionAccess
}

class CalleePositionToAccessPath(val resultAp: PositionAccess?) : PositionResolver<Maybe<List<PositionAccess>>> {
    override fun resolve(position: Position): Maybe<List<PositionAccess>> = when (position) {
        is Argument -> listOf(PositionAccess.Simple(AccessPathBase.Argument(position.index))).toMaybe()
        This -> listOf(PositionAccess.Simple(AccessPathBase.This)).toMaybe()

        is PositionWithAccess -> resolve(position.base).flatFmap { pos ->
            val accessor = when (val a = position.access) {
                PositionAccessor.ElementAccessor -> ElementAccessor
                is PositionAccessor.FieldAccessor -> FieldAccessor(a.className, a.fieldName, a.fieldType)
                PositionAccessor.AnyFieldAccessor -> {
                    // force loop in access path
                    val loopedPosition = PositionAccess.Complex(
                        PositionAccess.Complex(pos, AnyAccessor),
                        AnyAccessor
                    )
                    return@flatFmap listOf(loopedPosition)
                }
            }
            listOf(PositionAccess.Complex(pos, accessor))
        }

        is ClassStatic -> listOf(PositionAccess.Simple(AccessPathBase.ClassStatic(position.className))).toMaybe()

        Result -> Maybe.from(resultAp).fmap { listOf(it) }
    }
}