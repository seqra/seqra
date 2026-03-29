package org.opentaint.dataflow.go

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.type.*
import org.opentaint.ir.go.value.*

/**
 * Foundational utility for mapping Go IR values and expressions to the framework's
 * AccessPathBase and Accessor types. Every flow function depends on this.
 */
object GoFlowFunctionUtils {

    // ── Access sealed interface ──────────────────────────────────────

    sealed interface Access {
        val base: AccessPathBase

        data class Simple(override val base: AccessPathBase) : Access
        data class RefAccess(
            override val base: AccessPathBase,
            val accessor: Accessor,
        ) : Access
    }

    // ── Value → AccessPathBase ───────────────────────────────────────

    /**
     * Maps a Go IR value to a framework access path base.
     * Requires the enclosing method for free variable mapping.
     */
    fun accessPathBase(value: GoIRValue, method: GoIRFunction): AccessPathBase? {
        return when (value) {
            is GoIRParameterValue -> AccessPathBase.Argument(value.paramIndex)
            is GoIRRegister -> AccessPathBase.LocalVar(value.index)
            is GoIRConstValue -> AccessPathBase.Constant(value.type.displayName, value.value.toString())
            is GoIRGlobalValue -> AccessPathBase.ClassStatic
            is GoIRFunctionValue -> AccessPathBase.Constant("func", value.function.fullName)
            is GoIRBuiltinValue -> AccessPathBase.Constant("builtin", value.name)
            is GoIRFreeVarValue -> {
                val paramCount = method.params.size
                AccessPathBase.Argument(paramCount + value.freeVarIndex)
            }
            else -> null
        }
    }

    /**
     * Maps a Go IR value to AccessPathBase without method context.
     * Cannot handle GoIRFreeVarValue (returns null).
     */
    fun accessPathBaseFromValue(value: GoIRValue): AccessPathBase? {
        return when (value) {
            is GoIRParameterValue -> AccessPathBase.Argument(value.paramIndex)
            is GoIRRegister -> AccessPathBase.LocalVar(value.index)
            is GoIRConstValue -> AccessPathBase.Constant(value.type.displayName, value.value.toString())
            is GoIRGlobalValue -> AccessPathBase.ClassStatic
            is GoIRFunctionValue -> AccessPathBase.Constant("func", value.function.fullName)
            is GoIRBuiltinValue -> AccessPathBase.Constant("builtin", value.name)
            is GoIRFreeVarValue -> null
            else -> null
        }
    }

    // ── Expression → Access ──────────────────────────────────────────

    /**
     * Maps a Go IR expression to an Access.
     * Returns null for expressions that don't propagate taint (alloc, make, arithmetic).
     */
    fun exprToAccess(expr: GoIRExpr, method: GoIRFunction): Access? {
        return when (expr) {
            // Field access
            is GoIRFieldExpr -> {
                val base = accessPathBase(expr.x, method) ?: return null
                Access.RefAccess(base, fieldAccessor(expr))
            }
            is GoIRFieldAddrExpr -> {
                val base = accessPathBase(expr.x, method) ?: return null
                Access.RefAccess(base, fieldAccessorFromAddr(expr))
            }

            // Index/element access
            is GoIRIndexExpr -> {
                val base = accessPathBase(expr.x, method) ?: return null
                // String indexing (s[i]) reads a byte — treat as simple taint propagation
                if (isStringType(expr.x.type)) {
                    Access.Simple(base)
                } else {
                    Access.RefAccess(base, ElementAccessor)
                }
            }
            is GoIRIndexAddrExpr -> {
                val base = accessPathBase(expr.x, method) ?: return null
                Access.RefAccess(base, ElementAccessor)
            }
            is GoIRLookupExpr -> {
                val base = accessPathBase(expr.x, method) ?: return null
                Access.RefAccess(base, ElementAccessor)
            }

            // Conversions/wrapping (preserve taint)
            is GoIRChangeTypeExpr -> singleOperandAccess(expr.x, method)
            is GoIRConvertExpr -> singleOperandAccess(expr.x, method)
            is GoIRMultiConvertExpr -> singleOperandAccess(expr.x, method)
            is GoIRChangeInterfaceExpr -> singleOperandAccess(expr.x, method)
            is GoIRMakeInterfaceExpr -> singleOperandAccess(expr.x, method)
            is GoIRTypeAssertExpr -> singleOperandAccess(expr.x, method)
            is GoIRSliceToArrayPointerExpr -> singleOperandAccess(expr.x, method)

            // Pointer ops
            is GoIRUnOpExpr -> when (expr.op) {
                GoIRUnaryOp.DEREF -> singleOperandAccess(expr.x, method)
                GoIRUnaryOp.ARROW -> {
                    // Channel receive: <-ch reads element from channel
                    val base = accessPathBase(expr.x, method) ?: return null
                    Access.RefAccess(base, ElementAccessor)
                }
                else -> null // NOT, NEG, XOR — kills taint
            }

            // Slice (sub-view preserves taint)
            is GoIRSliceExpr -> singleOperandAccess(expr.x, method)

            // Range iteration
            is GoIRRangeExpr -> singleOperandAccess(expr.x, method)
            is GoIRNextExpr -> singleOperandAccess(expr.iter, method)

            // Tuple extract (multi-return): index-sensitive
            is GoIRExtractExpr -> {
                val base = accessPathBase(expr.tuple, method) ?: return null
                Access.RefAccess(base, tupleFieldAccessor(expr.extractIndex, expr.type))
            }

            // Binary op: string concat preserves taint, arithmetic doesn't
            is GoIRBinOpExpr -> {
                if (expr.op == GoIRBinaryOp.ADD && isStringType(expr.type)) {
                    singleOperandAccess(expr.x, method)
                } else {
                    null
                }
            }

            // Allocations: no taint source
            is GoIRAllocExpr -> null
            is GoIRMakeSliceExpr -> null
            is GoIRMakeMapExpr -> null
            is GoIRMakeChanExpr -> null

            // Closure: taint if any binding is tainted
            is GoIRMakeClosureExpr -> {
                if (expr.bindings.isNotEmpty()) {
                    singleOperandAccess(expr.bindings.first(), method)
                } else {
                    null
                }
            }

            // Select: complex, treat as opaque
            is GoIRSelectExpr -> null
        }
    }

    private fun singleOperandAccess(value: GoIRValue, method: GoIRFunction): Access? {
        val base = accessPathBase(value, method) ?: return null
        return Access.Simple(base)
    }

    // ── Store address resolution ─────────────────────────────────────

    /**
     * Resolves the access for a store destination address.
     * If the address was produced by FieldAddrExpr or IndexAddrExpr,
     * returns a RefAccess with the appropriate accessor.
     */
    fun accessForAddr(addr: GoIRValue, method: GoIRFunction): Access? {
        if (addr !is GoIRRegister) {
            return Access.Simple(accessPathBase(addr, method) ?: return null)
        }
        val defInst = findDefInst(addr, method)
            ?: return Access.Simple(AccessPathBase.LocalVar(addr.index))

        return when (val expr = (defInst as? GoIRAssignInst)?.expr) {
            is GoIRFieldAddrExpr -> {
                val base = accessPathBase(expr.x, method) ?: return null
                Access.RefAccess(base, fieldAccessorFromAddr(expr))
            }
            is GoIRIndexAddrExpr -> {
                val base = accessPathBase(expr.x, method) ?: return null
                Access.RefAccess(base, ElementAccessor)
            }
            else -> Access.Simple(AccessPathBase.LocalVar(addr.index))
        }
    }

    // ── Field accessor helpers ───────────────────────────────────────

    fun tupleFieldAccessor(index: Int, elementType: GoIRType): FieldAccessor {
        return FieldAccessor("tuple", "\$$index", elementType.displayName)
    }

    fun fieldAccessor(expr: GoIRFieldExpr): FieldAccessor {
        val structTypeName = resolveStructTypeName(expr.x.type)
        return FieldAccessor(structTypeName, expr.fieldName, expr.type.displayName)
    }

    fun fieldAccessorFromAddr(expr: GoIRFieldAddrExpr): FieldAccessor {
        val structTypeName = resolveStructTypeName(expr.x.type)
        return FieldAccessor(structTypeName, expr.fieldName, expr.type.displayName)
    }

    private fun resolveStructTypeName(type: GoIRType): String {
        return when (type) {
            is GoIRNamedTypeRef -> type.namedType.fullName
            is GoIRPointerType -> resolveStructTypeName(type.elem)
            is GoIRStructType -> type.namedType?.fullName ?: "anonymous"
            else -> type.displayName
        }
    }

    // ── Defining instruction lookup ──────────────────────────────────

    fun findDefInst(register: GoIRRegister, method: GoIRFunction): GoIRDefInst? {
        val body = method.body ?: return null
        return body.instructions.getOrNull(register.index) as? GoIRDefInst
    }

    /**
     * Traces a register back to a MakeClosureExpr, if it was defined by one.
     */
    fun findMakeClosureExpr(register: GoIRRegister, method: GoIRFunction): GoIRMakeClosureExpr? {
        val defInst = findDefInst(register, method) ?: return null
        return (defInst as? GoIRAssignInst)?.expr as? GoIRMakeClosureExpr
    }

    // ── Call info extraction ─────────────────────────────────────────

    fun extractCallInfo(inst: GoIRInst): GoIRCallInfo? {
        return when (inst) {
            is GoIRCall -> inst.call
            is GoIRGo -> inst.call
            is GoIRDefer -> inst.call
            else -> null
        }
    }

    fun extractResultRegister(inst: GoIRInst): GoIRRegister? {
        return when (inst) {
            is GoIRCall -> inst.register
            else -> null
        }
    }

    // ── Type checks ──────────────────────────────────────────────────

    fun isStringType(type: GoIRType): Boolean {
        return type is GoIRBasicType && type.kind == GoIRBasicTypeKind.STRING
    }

    // ── Position resolution (for taint rules) ────────────────────────

    fun resolvePosition(pos: PositionBase): AccessPathBase {
        return when (pos) {
            is PositionBase.Result -> AccessPathBase.Return
            is PositionBase.Argument -> AccessPathBase.Argument(pos.idx ?: 0)
            is PositionBase.This -> AccessPathBase.This
            is PositionBase.ClassStatic -> AccessPathBase.ClassStatic
            is PositionBase.AnyArgument -> AccessPathBase.Argument(0)
        }
    }

    fun resolvePositionWithModifiers(pos: PositionBaseWithModifiers): Pair<AccessPathBase, List<Accessor>> {
        val base = resolvePosition(pos.base)
        val accessors = when (pos) {
            is PositionBaseWithModifiers.BaseOnly -> emptyList()
            is PositionBaseWithModifiers.WithModifiers -> pos.modifiers.map { mod ->
                when (mod) {
                    is PositionModifier.ArrayElement -> ElementAccessor
                    is PositionModifier.AnyField -> org.opentaint.dataflow.ap.ifds.AnyAccessor
                    is PositionModifier.Field -> FieldAccessor(mod.className, mod.fieldName, mod.fieldType)
                }
            }
        }
        return Pair(base, accessors)
    }
}
