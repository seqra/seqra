package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRConstant
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp

object MethodFlowFunctionUtils {
    data class Access(val base: AccessPathBase, val accessor: Accessor?)

    fun mkAccess(value: JIRValue) = when (value) {
        is JIRImmediate -> mkAccess(value)
        is JIRFieldRef -> mkAccess(value)
        is JIRArrayAccess -> mkAccess(value)
        else -> null
    }

    fun mkAccess(value: JIRImmediate) = accessPathBase(value)?.let { Access(it, accessor = null) }
    fun mkAccess(value: JIRArrayAccess) = mkArrayAccess(value.array)
    fun mkAccess(value: JIRFieldRef) = mkFieldAccess(value.field.field, value.instance)

    fun mkArrayAccess(array: JIRValue): Access? =
        accessPathBase(array)?.let { Access(it, ElementAccessor) }

    fun mkFieldAccess(field: JIRField, instance: JIRValue?): Access? {
        val accessor = field.let { f ->
            FieldAccessor(f.enclosingClass.name, f.name, f.type.typeName)
        }

        if (instance == null) {
            require(field.isStatic) { "Expected static field" }
            return Access(AccessPathBase.ClassStatic(field.enclosingClass.name), accessor)
        }

        return accessPathBase(instance)?.let { Access(it, accessor) }
    }

    fun accessPathBase(value: JIRValue): AccessPathBase? = (value as? JIRImmediate)?.let { accessPathBase(it) }

    fun accessPathBase(value: JIRImmediate): AccessPathBase? = when (value) {
        is JIRThis -> AccessPathBase.This
        is JIRArgument -> AccessPathBase.Argument(value.index)
        is JIRLocalVar -> AccessPathBase.LocalVar(value.index)
        is JIRConstant -> AccessPathBase.Constant(value.type.typeName, "$value")
        else -> null
    }

    fun Fact.FinalFact.rebase(newBase: AccessPathBase) = changeAP(ap.rebase(newBase))

    fun FinalFactAp.mayReadField(base: AccessPathBase, field: Accessor): Boolean = when {
        this.base != base -> false
        startsWithAccessor(field) -> true
        else -> isAbstract() && field !in exclusions
    }

    fun FinalFactAp.mayRemoveAfterWrite(base: AccessPathBase, field: Accessor): Boolean = when {
        this.base != base -> false
        startsWithAccessor(field) -> true
        else -> isAbstract() && field !in exclusions
    }

    fun FinalFactAp.readFieldTo(newBase: AccessPathBase, field: Accessor): FinalFactAp =
        readAccessor(field)?.rebase(newBase) ?: error("Can't drop field")

    fun FinalFactAp.writeToField(newBase: AccessPathBase, field: Accessor): FinalFactAp =
        prependAccessor(field).rebase(newBase)

    fun FinalFactAp.clearField(field: Accessor): FinalFactAp? = clearAccessor(field)

    fun InitialFactAp.excludeField(field: Accessor) = exclude(field)

    fun FinalFactAp.excludeField(field: Accessor) = exclude(field)
}
