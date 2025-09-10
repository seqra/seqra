package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionAccessor
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.PositionWithAccess
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.ResultAnyElement
import org.opentaint.ir.taint.configuration.This
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.flatFmap
import org.opentaint.dataflow.ifds.toMaybe

sealed interface PositionAccess {
    data class Simple(val base: AccessPathBase) : PositionAccess
    data class Complex(val base: PositionAccess, val accessor: Accessor) : PositionAccess
}

class CalleePositionToAccessPath : PositionResolver<Maybe<List<PositionAccess>>> {
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

        // Inapplicable callee positions
        Result -> Maybe.none()
        ResultAnyElement -> Maybe.none()
    }
}