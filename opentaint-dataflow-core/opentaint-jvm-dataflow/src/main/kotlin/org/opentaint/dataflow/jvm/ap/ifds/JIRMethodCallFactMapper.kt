package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

object JIRMethodCallFactMapper : MethodCallFactMapper {
    // TODO: maybe receive FactTypeChecker at instantiation and remove it from method's parameters?
    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        methodExit: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker
    ): List<FinalFactAp> {
        jirDowncast<JIRInst>(callStatement)
        jirDowncast<JIRInst>(methodExit)
        return mapMethodExitToReturnFlowFact(callStatement, methodExit, factAp, checker)
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        methodExit: CommonInst,
        factAp: InitialFactAp
    ): List<InitialFactAp> {
        jirDowncast<JIRInst>(callStatement)
        jirDowncast<JIRInst>(methodExit)
        return mapMethodExitToReturnFlowFact(callStatement, methodExit, factAp)
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit
    ) {
        jirDowncast<JIRMethod>(callee)
        jirDowncast<JIRCallExpr>(callExpr)
        return mapMethodCallToStartFlowFact(callee, callExpr, factAp, checker, onMappedFact)
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit
    ) {
        jirDowncast<JIRMethod>(callee)
        jirDowncast<JIRCallExpr>(callExpr)
        return mapMethodCallToStartFlowFact(callee, callExpr, fact, onMappedFact)
    }

    override fun factCanBeModifiedByMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp
    ): Boolean {
        jirDowncast<JIRImmediate?>(returnValue)
        jirDowncast<JIRCallExpr>(callExpr)
        return factCanBeModifiedByMethodCall(returnValue, callExpr, factAp)
    }

    override fun isValidMethodExitFact(methodExit: CommonInst, factAp: FactAp): Boolean {
        jirDowncast<JIRInst>(methodExit)
        return isValidMethodExitFact(methodExit, factAp)
    }

    private fun mapMethodExitToReturnFlowFact(
        callStatement: JIRInst,
        methodExit: JIRInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker
    ): List<FinalFactAp> = mapMethodExitToReturnFlowFact(
        callStatement = callStatement,
        methodExit = methodExit,
        factAp = factAp,
        checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
        rebaseFact = { f, base -> f.rebase(base) }
    )

    private fun mapMethodExitToReturnFlowFact(
        callStatement: JIRInst,
        methodExit: JIRInst,
        factAp: InitialFactAp
    ): List<InitialFactAp> = mapMethodExitToReturnFlowFact(
        callStatement = callStatement,
        methodExit = methodExit,
        factAp = factAp,
        checkFactType = { _, f -> f },
        rebaseFact = { f, base -> f.rebase(base) }
    )

    private fun mapMethodCallToStartFlowFact(
        callee: JIRMethod,
        callExpr: JIRCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit
    ) = mapMethodCallToStartFlowFact(
        callee = callee,
        callExpr = callExpr,
        factAp = factAp,
        checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
        onMappedFact = onMappedFact
    )

    private fun mapMethodCallToStartFlowFact(
        callee: JIRMethod,
        callExpr: JIRCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit
    ) = mapMethodCallToStartFlowFact(
        callee = callee,
        callExpr = callExpr,
        factAp = fact,
        checkFactType = { _, f -> f },
        onMappedFact = onMappedFact
    )

    private inline fun <F: FactAp> mapMethodExitToReturnFlowFact(
        callStatement: JIRInst,
        methodExit: JIRInst,
        factAp: F,
        checkFactType: (JIRType, F) -> F?,
        rebaseFact: (F, AccessPathBase) -> F,
    ): List<F> = buildList {
        val base = factAp.base

        val callExpr = callStatement.callExpr ?: error("Non call statement")
        val returnValue: JIRImmediate? = (callStatement as? JIRAssignInst)?.lhv?.let {
            it as? JIRImmediate ?: error("Non simple return value: $callStatement")
        }

        if (returnValue != null) {
            mapExitToReturnValue(methodExit, base, returnValue, checkFactType, factAp, rebaseFact)
        }

        when (base) {
            is AccessPathBase.ClassStatic,
            is AccessPathBase.Constant -> {
                this += factAp
            }

            is AccessPathBase.Argument -> {
                val argExpr = callExpr.args.getOrNull(base.idx) ?: error("Call $callExpr has no arg $factAp")
                val newBase = MethodFlowFunctionUtils.accessPathBase(argExpr) ?: return@buildList
                if (newBase is AccessPathBase.Constant) return@buildList

                val checkedFact = checkFactType(argExpr.type, factAp) ?: return@buildList

                this += rebaseFact(checkedFact, newBase)
            }

            AccessPathBase.This -> {
                check(callExpr is JIRInstanceCallExpr) { "Non instance call with <this> argument" }

                val newBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance) ?: return@buildList
                if (newBase is AccessPathBase.Constant) return@buildList

                val checkedFact = checkFactType(callExpr.instance.type, factAp) ?: return@buildList

                this += rebaseFact(checkedFact, newBase)
            }

            is AccessPathBase.LocalVar -> {
                // Already mapped by mapExitToReturnValue
            }
        }
    }

    private inline fun <F : FactAp> MutableList<F>.mapExitToReturnValue(
        methodExit: JIRInst,
        base: AccessPathBase,
        returnValue: JIRImmediate,
        checkFactType: (JIRType, F) -> F?,
        factAp: F,
        rebaseFact: (F, AccessPathBase) -> F
    ) {
        when (methodExit) {
            is JIRReturnInst -> {
                val exitValue = methodExit.returnValue
                    ?.let { MethodFlowFunctionUtils.accessPathBase(it) }
                    ?: return

                if (base != exitValue) return

                val newBase = MethodFlowFunctionUtils.accessPathBase(returnValue) ?: return
                if (newBase is AccessPathBase.Constant) return

                val checkedFact = checkFactType(returnValue.type, factAp) ?: return

                this += rebaseFact(checkedFact, newBase)
            }

            is JIRThrowInst -> {
                // Trow can't be propagated to method return site
                return
            }

            else -> return
        }
    }

    override fun methodExitFactBases(methodExits: List<CommonInst>): List<AccessPathBase> {
        return methodExits.mapNotNull { methodExit ->
            when (methodExit) {
                is JIRReturnInst -> {
                    methodExit.returnValue?.let { MethodFlowFunctionUtils.accessPathBase(it) }
                }

                is JIRThrowInst -> {
                    // Trow can't be propagated to method return site
                    null
                }

                else -> null
            }
        }
    }


    private fun isValidMethodExitFact(methodExit: JIRInst, factAp: FactAp): Boolean =
        isValidMethodExitFact(methodExit, factAp.base)

    private fun isValidMethodExitFact(methodExit: JIRInst, factBase: AccessPathBase): Boolean {
        if (factBase !is AccessPathBase.LocalVar) return true

        when (methodExit) {
            is JIRReturnInst -> {
                val exitValue = methodExit.returnValue
                    ?.let { MethodFlowFunctionUtils.accessPathBase(it) }
                    ?: return false

                return factBase == exitValue
            }

            is JIRThrowInst -> {
                val throwValue = methodExit.throwable
                    .let { MethodFlowFunctionUtils.accessPathBase(it) }

                return factBase == throwValue
            }

            else -> return false
        }
    }

    private inline fun <F : FactAp> mapMethodCallToStartFlowFact(
        callee: JIRMethod,
        callExpr: JIRCallExpr,
        factAp: F,
        checkFactType: (JIRType, F) -> F?,
        onMappedFact: (F, AccessPathBase) -> Unit,
    ) {
        val factBase = factAp.base

        if (factBase is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, factBase)
        }

        if (callExpr is JIRInstanceCallExpr) {
            val instanceBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance)
            if (instanceBase == factBase) {
                val checkedFact = checkFactType(callee.enclosingClass.toType(), factAp)
                if (checkedFact != null) {
                    onMappedFact(checkedFact, AccessPathBase.This)
                }
            }
        }

        for ((i, arg) in callExpr.args.withIndex()) {
            val argBase = MethodFlowFunctionUtils.accessPathBase(arg)
            if (argBase == factBase) {
                val checkedFact = checkFactType(arg.type, factAp)
                if (checkedFact != null) {
                    onMappedFact(checkedFact, AccessPathBase.Argument(i))
                }
            }
        }
    }

    private fun factCanBeModifiedByMethodCall(returnValue: JIRImmediate?, callExpr: JIRCallExpr, factAp: FactAp): Boolean =
        factCanBeModifiedByMethodCall(returnValue, callExpr, factAp.base)

    private fun factCanBeModifiedByMethodCall(
        returnValue: JIRImmediate?,
        callExpr: JIRCallExpr,
        factBase: AccessPathBase
    ): Boolean {
        if (factBase is AccessPathBase.ClassStatic) {
            return true
        }

        if (callExpr is JIRInstanceCallExpr) {
            val instanceBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance)
            // todo: fields only?
            if (instanceBase == factBase) {
                return true
            }
        }

        for (arg in callExpr.args) {
            val argBase = MethodFlowFunctionUtils.accessPathBase(arg)
            // todo: fields only?
            if (argBase == factBase) {
                return true
            }
        }

        if (returnValue != null) {
            val retValBase = MethodFlowFunctionUtils.accessPathBase(returnValue)
            if (retValBase == factBase) {
                return true
            }
        }

        return false
    }
}