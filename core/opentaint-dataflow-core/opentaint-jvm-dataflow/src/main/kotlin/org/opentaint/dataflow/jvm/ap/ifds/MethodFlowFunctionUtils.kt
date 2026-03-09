package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRConstant
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue

object MethodFlowFunctionUtils {

    sealed interface Access {
        val base: AccessPathBase
    }

    sealed interface MemoryAccess : Access

    data class Simple(override val base: AccessPathBase) : Access

    data class RefAccess(override val base: AccessPathBase, val accessor: Accessor) : MemoryAccess

    data class StaticRefAccess(
        val classStaticAccessor: ClassStaticAccessor,
        val accessor: Accessor
    ) : MemoryAccess {
        override val base get() = AccessPathBase.ClassStatic
    }

    fun mkAccess(value: JIRValue) = when (value) {
        is JIRImmediate -> mkAccess(value)
        is JIRFieldRef -> mkAccess(value)
        is JIRArrayAccess -> mkAccess(value)
        else -> null
    }

    private fun mkAccess(value: JIRImmediate) = accessPathBase(value)?.let(::Simple)
    private fun mkAccess(value: JIRArrayAccess) = mkArrayAccess(value.array)
    private fun mkAccess(value: JIRFieldRef) = mkFieldAccess(value.field.field, value.instance)

    fun mkArrayAccess(array: JIRValue): Access? =
        accessPathBase(array)?.let { RefAccess(it, ElementAccessor) }

    fun mkFieldAccess(field: JIRField, instance: JIRValue?): Access? {
        val accessor = FieldAccessor(field.enclosingClass.name, field.name, field.type.typeName)

        if (instance == null) {
            require(field.isStatic) { "Expected static field" }
            return StaticRefAccess(
                classStaticAccessor = ClassStaticAccessor(field.enclosingClass.name),
                accessor = accessor
            )
        }

        return accessPathBase(instance)?.let { RefAccess(it, accessor) }
    }

    fun accessPathBase(value: JIRValue): AccessPathBase? = (value as? JIRImmediate)?.let { accessPathBase(it) }

    private fun accessPathBase(value: JIRImmediate): AccessPathBase? = when (value) {
        is JIRThis -> AccessPathBase.This
        is JIRArgument -> AccessPathBase.Argument(value.index)
        is JIRLocalVar -> AccessPathBase.LocalVar(value.index)
        is JIRConstant -> AccessPathBase.Constant(value.type.typeName, "$value")
        else -> null
    }

    fun FinalFactAp.mayReadAccessor(base: AccessPathBase, accessor: Accessor): Boolean = when {
        this.base != base -> false
        startsWithAccessor(accessor) -> true
        else -> isAbstract() && accessor !in exclusions
    }

    fun FinalFactAp.mayRemoveAfterWrite(base: AccessPathBase, accessor: Accessor): Boolean = when {
        this.base != base -> false
        startsWithAccessor(accessor) -> true
        else -> isAbstract() && accessor !in exclusions
    }

    fun FinalFactAp.readAccessorTo(newBase: AccessPathBase, accessor: Accessor): FinalFactAp =
        readAccessor(accessor)?.rebase(newBase) ?: error("Can't drop field")

    fun FinalFactAp.writeToAccessor(newBase: AccessPathBase, accessor: Accessor): FinalFactAp =
        prependAccessor(accessor).rebase(newBase)

    fun FinalFactAp.clearField(field: Accessor): FinalFactAp? = clearAccessor(field)

    fun InitialFactAp.excludeField(field: Accessor) = exclude(field)

    fun FinalFactAp.excludeField(field: Accessor) = exclude(field)
}
