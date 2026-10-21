package org.opentaint.dataflow.python

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.configuration.python.AnyArgument
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.ClassRef
import org.opentaint.dataflow.configuration.python.KwArgument
import org.opentaint.dataflow.configuration.python.Position
import org.opentaint.dataflow.configuration.python.PositionAccessor
import org.opentaint.dataflow.configuration.python.PositionWithAccess
import org.opentaint.dataflow.configuration.python.Result
import org.opentaint.dataflow.configuration.python.This
import org.opentaint.dataflow.python.util.indexOfKeywordArg
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.PositionTypeResolver
import org.opentaint.ir.api.common.CommonType
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRConst
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRGlobalNameRef
import org.opentaint.ir.api.python.PIRLocalVar
import org.opentaint.ir.api.python.PIRModuleNameRef
import org.opentaint.ir.api.python.PIRNameRef
import org.opentaint.ir.api.python.PIRParameterRef
import org.opentaint.ir.api.python.PIRValue
import org.opentaint.ir.api.python.PythonNames

object PIRFlowFunctionUtils {
    fun accessPathBase(value: PIRValue): AccessPathBase? = when (value) {
        is PIRLocalVar -> AccessPathBase.LocalVar(value.index)
        is PIRParameterRef -> AccessPathBase.Argument(value.index)
        is PIRConst -> null
    }

    fun globalAccess(ref: PIRNameRef): Pair<AccessPathBase, Accessor> {
        val name = when (ref) {
            is PIRGlobalNameRef -> ref.qualifiedName
            is PIRModuleNameRef -> ref.module
        }
        return AccessPathBase.ClassStatic to ClassStaticAccessor(name)
    }

    fun implicitParamOffset(callee: PIRFunction): Int {
        if (callee.enclosingClass == null) return 0
        if (callee.isStaticMethod) return 0
        return 1
    }

    fun Position.resolveAp(call: PIRCall? = null): PositionAccess? = resolveBaseAp(call)?.let { resolveAp(it) }

    fun Position.resolveAp(baseAp: AccessPathBase): PositionAccess? = when (this) {
        is KwArgument,
        is Argument,
        Result,
        This -> PositionAccess.Simple(baseAp)

        is ClassRef -> PositionAccess.Complex(
            PositionAccess.Simple(baseAp),
            ClassStaticAccessor(fqn)
        )

        is PositionWithAccess -> {
            val base = base.resolveAp(baseAp) ?: return null
            val accessor = access.resolve()
            PositionAccess.Complex(base, accessor)
        }

        AnyArgument -> anyArgumentUnsupported()
    }

    fun Position.resolveBaseAp(call: PIRCall? = null): AccessPathBase? = when (this) {
        is ClassRef -> AccessPathBase.ClassStatic
        is Argument -> AccessPathBase.Argument(index)
        Result -> AccessPathBase.Return
        This -> AccessPathBase.This
        is PositionWithAccess -> base.resolveBaseAp(call)

        // TODO create separate ap-resolvers?
        is KwArgument -> call?.let { call ->
            call.indexOfKeywordArg(name)?.let { AccessPathBase.Argument(it) }
        }

        AnyArgument -> anyArgumentUnsupported()
    }

    private fun anyArgumentUnsupported(): Nothing =
        error("Unexpected any argument position")

    private fun PositionAccessor.resolve(): Accessor = when (this) {
        PositionAccessor.ElementAccessor -> ElementAccessor
        is PositionAccessor.FieldAccessor -> mkFieldAccessor(name)
    }

    fun FinalFactAp.mayReadAccessor(base: AccessPathBase, accessor: Accessor): Boolean = when {
        this.base != base -> false
        startsWithAccessor(accessor) -> true
        else -> isAbstract() && accessor !in exclusions
    }

    fun mkFieldAccessor(fieldName: String): FieldAccessor = FieldAccessor("", fieldName, "")

    object DummyPositionTypeResolver : PositionTypeResolver {
        override fun resolve(position: PositionAccess): CommonType? = null
    }

    val SELF_ACCESSOR = mkFieldAccessor(PythonNames.BOUND_SELF_ATTR)
}
