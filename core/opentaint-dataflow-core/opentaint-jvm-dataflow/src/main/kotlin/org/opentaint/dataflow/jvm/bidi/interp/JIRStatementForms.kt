package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.TypeFilter
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.interp.FormKey
import org.opentaint.dataflow.bidi.interp.FormOwner
import org.opentaint.dataflow.bidi.interp.OperationId
import org.opentaint.dataflow.bidi.interp.StaticOperation
import org.opentaint.dataflow.bidi.interp.starEdge
import org.opentaint.dataflow.bidi.interp.starSide
import org.opentaint.dataflow.bidi.interp.MicroEdgeBuilder
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.MemoryAccess
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.RefAccess
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.StaticRefAccess
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mkAccess
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBinaryExpr
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.cfg.callExpr

/** Non-call JVM statements. The complete interpreter supplies aliases and read sources when it wires this adapter. */
internal class JIRStatementForms(
    private val manager: ApManager,
    private val typeFilters: JIRTypeFilters,
    private val aliasPaths: (JIRInst, AccessPathBase) -> List<Pair<AccessPathBase, List<AccessorIdx>>> = { _, _ -> emptyList() },
    private val readSources: (MicroEdgeBuilder, JIRAssignInst) -> Unit = { _, _ -> },
    private val formOwner: FormOwner = FormOwner(),
) {
    private fun filter(type: JIRType?): TypeFilter? = typeFilters.of(type)

    fun build(s: JIRInst): StatementSummary {
        check(s.callExpr == null) { "A call has a call plan (interpreter.md §2.1)" }
        val b = MicroEdgeBuilder()
        when (s) {
            is JIRAssignInst -> {
                b.assign(s, s.lhv, s.rhv)
                readSources(b, s)
            }
            is JIRReturnInst -> b.move(AccessPathBase.Return, s.returnValue?.let(::accessPathBase))
            is JIRThrowInst -> b.move(AccessPathBase.Exception, accessPathBase(s.throwable))
            else -> return StatementSummary.EMPTY
        }
        return b.build()
    }

    /** Input filters read operands; a simple lhs has a result filter after transfer. */
    private fun MicroEdgeBuilder.assign(s: JIRInst, lhv: JIRValue, rhv: JIRExpr) {
        if (rhv is JIRBinaryExpr) {
            assign(s, lhv, rhv.lhv)
            assign(s, lhv, rhv.rhv)
            return
        }
        val from = when (rhv) {
            is JIRCastExpr -> mkAccess(rhv.operand)?.also { operandFilter(it.base, filter(rhv.type)) } ?: return
            is JIRImmediate -> mkAccess(rhv)?.also { operandFilter(it.base, filter(rhv.type)) } ?: return
            is JIRArrayAccess -> mkAccess(rhv)?.also { operandFilter(it.base, filter(rhv.array.type)) } ?: return
            is JIRFieldRef -> mkAccess(rhv)?.also { fieldFilters(it.base, rhv) } ?: return
            else -> null
        }
        val to = when (lhv) {
            is JIRImmediate -> mkAccess(lhv)?.also { resultFilter(it.base, filter(lhv.type)) } ?: return
            is JIRArrayAccess -> mkAccess(lhv)?.also { operandFilter(it.base, filter(lhv.array.type)) } ?: return
            is JIRFieldRef -> mkAccess(lhv)?.also { fieldFilters(it.base, lhv) } ?: return
            else -> error("Assign to complex value: $lhv")
        }
        when {
            from is MemoryAccess -> {
                check(to !is MemoryAccess) { "Complex assignment: $lhv = $rhv" }
                val transfer = starEdge(from.base, path(from), to.base, emptyList())
                read(to.base, from.base, path(from))
                if (from is StaticRefAccess) staticOperation(StaticOperation.Read(operation(s), transfer))
            }
            to is MemoryAccess -> {
                write(to.base, path(to), weak = (to as? RefAccess)?.accessor == ElementAccessor,
                    values = listOfNotNull(from?.base), aliasPaths = aliases(s, to.base))
                if (to is StaticRefAccess) staticOperation(StaticOperation.Write(operation(s),
                    Pattern(starSide(to.base, path(to)), ExclusionSet.Empty),
                    from?.base?.let { starEdge(it, emptyList(), to.base, path(to)) }))
            }
            else -> move(to.base, from?.base)
        }
    }

    private fun operation(s: JIRInst) = OperationId(FormKey(formOwner, s.location.method, null, s))

    /** A field operand uses its instance type and declaring class. Static fields add no filter on S. */
    private fun MicroEdgeBuilder.fieldFilters(base: AccessPathBase, ref: JIRFieldRef) {
        val instance = ref.instance ?: return
        operandFilter(base, filter(instance.type))
        operandFilter(base, filter(ref.field.enclosingType))
    }

    private fun path(access: MemoryAccess): List<AccessorIdx> = when (access) {
        is RefAccess -> listOf(manager.accessors.index(access.accessor))
        is StaticRefAccess -> listOf(manager.accessors.index(access.classStaticAccessor), manager.accessors.index(access.accessor))
    }

    /** A1/A4: only a local write asks for aliases. Reads and statics do not call the provider. */
    private fun aliases(s: JIRInst, base: AccessPathBase): List<Pair<AccessPathBase, List<AccessorIdx>>> =
        if (base is AccessPathBase.LocalVar) aliasPaths(s, base) else emptyList()
}
