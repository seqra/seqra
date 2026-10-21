package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.ReadableAccessorList
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.python.PIRFlowFunctionUtils
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.SELF_ACCESSOR
import org.opentaint.dataflow.python.adapter.PIRCallExprAdapter
import org.opentaint.dataflow.python.pIRDowncast
import org.opentaint.dataflow.python.util.indexOfKeywordArg
import org.opentaint.dataflow.python.util.indexOfKeywordParam
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRCallArgKind
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRLoadAttr
import org.opentaint.ir.api.python.PIRLocalVar
import org.opentaint.ir.api.python.PIRValue

object PIRMethodCallFactMapper : MethodCallFactMapper {

    private fun valueToBase(value: PIRValue): AccessPathBase? =
        PIRFlowFunctionUtils.accessPathBase(value)

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
    ): List<FinalFactAp> {
        pIRDowncast<PIRCall>(callStatement)
        return listOfNotNull(
            mapMethodExitToReturnFlowFact(callStatement, factAp, FinalFactAp::rebase, FinalFactAp::prependAccessor)
        )
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: InitialFactAp,
    ): List<InitialFactAp> {
        pIRDowncast<PIRCall>(callStatement)
        return listOfNotNull(
            mapMethodExitToReturnFlowFact(callStatement, factAp, InitialFactAp::rebase, InitialFactAp::prependAccessor)
        )
    }

    override fun mapMethodCallToStartFlowFact(
        callStatement: CommonInst,
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        returnValue: CommonValue?,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
    ) {
        pIRDowncast<PIRCall>(callStatement)
        mapMethodCallToStartFlowFact(callStatement, factAp, onMappedFact)
    }

    override fun mapMethodCallToStartFlowFact(
        callStatement: CommonInst,
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        returnValue: CommonValue?,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit,
    ) {
        pIRDowncast<PIRCall>(callStatement)
        mapMethodCallToStartFlowFact(callStatement, fact, onMappedFact)
    }

    fun mapLoadAttributeFactToStart(
        statement: PIRLoadAttr,
        fact: FinalFactAp,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
    ) = mapLoadAttributeFactToStart<FinalFactAp>(statement, fact, onMappedFact)

    fun mapLoadAttributeFactToStart(
        statement: PIRLoadAttr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit,
    ) = mapLoadAttributeFactToStart<InitialFactAp>(statement, fact, onMappedFact)

    private inline fun <F : FactAp> mapLoadAttributeFactToStart(
        statement: PIRLoadAttr,
        fact: F,
        onMappedFact: (F, AccessPathBase) -> Unit,
    ) {
        val objBase = valueToBase(statement.obj) ?: return

        if (objBase == fact.base) {
            onMappedFact(fact, AccessPathBase.This)
        }

        if (fact.base is AccessPathBase.ClassStatic) {
            onMappedFact(fact, fact.base)
        }
    }

    fun mapLoadAttributeFactToReturn(statement: PIRLoadAttr, fact: FinalFactAp): FinalFactAp? =
        mapLoadAttributeFactToReturn(statement, fact, FinalFactAp::rebase)

    fun mapLoadAttributeFactToReturn(statement: PIRLoadAttr, fact: InitialFactAp): InitialFactAp? =
        mapLoadAttributeFactToReturn(statement, fact, InitialFactAp::rebase)

    private inline fun <F : FactAp> mapLoadAttributeFactToReturn(
        statement: PIRLoadAttr,
        fact: F,
        rebase: F.(AccessPathBase) -> F,
    ): F? = when (fact.base) {
        is AccessPathBase.Return -> valueToBase(statement.target)?.let { fact.rebase(it) }
        is AccessPathBase.This -> valueToBase(statement.obj)?.let { fact.rebase(it) }
        is AccessPathBase.ClassStatic -> fact
        else -> null
    }

    private fun <F : FactAp> mapMethodExitToReturnFlowFact(
        call: PIRCall,
        factAp: F,
        rebase: F.(AccessPathBase) -> F,
        prepend: F.(Accessor) -> F,
    ): F? {
        return when (val base = factAp.base) {
            is AccessPathBase.Argument -> {
                val argValue = call.args.getOrNull(base.idx)?.value ?: return null
                val callerBase = valueToBase(argValue) ?: return null
                factAp.rebase(callerBase)
            }
            is AccessPathBase.Return -> {
                val target = call.target ?: return null
                val targetBase = valueToBase(target) ?: return null
                factAp.rebase(targetBase)
            }
            is AccessPathBase.This -> {
                val calleeBase = valueToBase(call.callee) ?: return null
                factAp.prepend(SELF_ACCESSOR).rebase(calleeBase)
            }
            is AccessPathBase.LocalVar -> null
            is AccessPathBase.ClassStatic -> factAp
            is AccessPathBase.Constant -> factAp
            else -> null
        }
    }

    private fun <F> mapMethodCallToStartFlowFact(
        call: PIRCall,
        factAp: F,
        onMappedFact: (F, AccessPathBase) -> Unit,
    ) where F : FactAp, F : ReadableAccessorList<F> {
        val base = factAp.base

        for ((i, arg) in call.args.withIndex()) {
            val argBase = valueToBase(arg.value) ?: continue
            if (base == argBase) {
                val startBase = AccessPathBase.Argument(i)
                onMappedFact(factAp, startBase)
            }
        }

        if (valueToBase(call.callee) == base) {
            val selfFact = factAp.readAccessor(SELF_ACCESSOR)
            selfFact?.let { onMappedFact(it, AccessPathBase.This) }
        }

        if (base is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, base)
        }
    }

    override fun factIsRelevantToMethodCall(
        callStatement: CommonInst,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp,
    ): Boolean {
        pIRDowncast<PIRCall>(callStatement)
        pIRDowncast<PIRLocalVar?>(returnValue)
        pIRDowncast<PIRCallExprAdapter>(callExpr)

        return factIsRelevantToMethodCall(callStatement, returnValue, callExpr, factAp)
    }

    private fun factIsRelevantToMethodCall(
        callStatement: PIRCall,
        returnValue: PIRValue?,
        callExpr: PIRCallExprAdapter,
        factAp: FactAp,
    ): Boolean {
        val base = factAp.base
        if (base is AccessPathBase.ClassStatic) return true
        if (valueToBase(callStatement.callee) == base && factAp.startsWithAccessor(SELF_ACCESSOR)) return true

        for (arg in callStatement.args) {
            if (base == valueToBase(arg.value)) return true
        }

        if (returnValue != null) {
            if (base == valueToBase(returnValue)) return true
        }

        return false
    }

    override fun isValidMethodExitFact(factAp: FactAp): Boolean =
        factAp.base !is AccessPathBase.LocalVar

    fun toCalleeFrame(callSite: PIRCall, callee: PIRFunction, base: AccessPathBase): AccessPathBase? {
        val offset = PIRFlowFunctionUtils.implicitParamOffset(callee)
        return when (base) {
            is AccessPathBase.This -> if (offset > 0) AccessPathBase.Argument(0) else null
            is AccessPathBase.Argument -> {
                val arg = callSite.args.getOrNull(base.idx) ?: return null
                when (arg.kind) {
                    PIRCallArgKind.KEYWORD -> arg.keyword?.let { name ->
                        callee.indexOfKeywordParam(name)?.let { AccessPathBase.Argument(it) }
                    }

                    PIRCallArgKind.POSITIONAL -> {
                        val newIdx = base.idx + offset
                        if (newIdx >= callee.parameters.size) null else AccessPathBase.Argument(newIdx)
                    }
                    PIRCallArgKind.STAR, PIRCallArgKind.DOUBLE_STAR -> null
                }
            }
            else -> base
        }
    }

    fun toCallerFrame(callSite: PIRCall, callee: PIRFunction, base: AccessPathBase): AccessPathBase? {
        if (base !is AccessPathBase.Argument) return base
        val offset = PIRFlowFunctionUtils.implicitParamOffset(callee)
        val p = base.idx
        if (offset > 0 && p == 0) return AccessPathBase.This

        val paramName = callee.parameters.getOrNull(p)?.name
        val kwIdx = paramName?.let { callSite.indexOfKeywordArg(it) }
        if (kwIdx != null) return AccessPathBase.Argument(kwIdx)

        val newIdx = p - offset
        if (newIdx < 0) return null
        if (callSite.args.getOrNull(newIdx)?.kind != PIRCallArgKind.POSITIONAL) return null
        return AccessPathBase.Argument(newIdx)
    }
}
