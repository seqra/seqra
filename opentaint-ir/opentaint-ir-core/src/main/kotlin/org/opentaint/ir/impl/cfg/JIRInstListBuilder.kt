package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.core.cfg.InstList
import org.opentaint.ir.api.jvm.cfg.BsmHandle
import org.opentaint.ir.api.jvm.cfg.BsmMethodTypeArg
import org.opentaint.ir.api.jvm.cfg.JIRRawAddExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawAndExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawArgument
import org.opentaint.ir.api.jvm.cfg.JIRRawArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRRawAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRRawBinaryExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawBool
import org.opentaint.ir.api.jvm.cfg.JIRRawByte
import org.opentaint.ir.api.jvm.cfg.JIRRawCallInst
import org.opentaint.ir.api.jvm.cfg.JIRRawCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRRawChar
import org.opentaint.ir.api.jvm.cfg.JIRRawClassConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawCmpExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCmpgExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCmplExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawDivExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawDouble
import org.opentaint.ir.api.jvm.cfg.JIRRawDynamicCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawEnterMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRRawEqExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawExitMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRRawFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRRawFloat
import org.opentaint.ir.api.jvm.cfg.JIRRawGeExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRRawGtExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawIfInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInstanceOfExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawInt
import org.opentaint.ir.api.jvm.cfg.JIRRawInterfaceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawLabelInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLabelRef
import org.opentaint.ir.api.jvm.cfg.JIRRawLeExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawLengthExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawLineNumberInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRRawLong
import org.opentaint.ir.api.jvm.cfg.JIRRawLtExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawMethodConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawMethodType
import org.opentaint.ir.api.jvm.cfg.JIRRawMulExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNegExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNeqExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNewArrayExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawOrExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawRemExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRRawShlExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawShort
import org.opentaint.ir.api.jvm.cfg.JIRRawShrExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawSubExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawSwitchInst
import org.opentaint.ir.api.jvm.cfg.JIRRawThis
import org.opentaint.ir.api.jvm.cfg.JIRRawThrowInst
import org.opentaint.ir.api.jvm.cfg.JIRRawUshrExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawVirtualCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawXorExpr
import org.opentaint.ir.api.jvm.ext.boolean
import org.opentaint.ir.api.jvm.ext.byte
import org.opentaint.ir.api.jvm.ext.char
import org.opentaint.ir.api.jvm.ext.double
import org.opentaint.ir.api.jvm.ext.float
import org.opentaint.ir.api.jvm.ext.int
import org.opentaint.ir.api.jvm.ext.long
import org.opentaint.ir.api.jvm.ext.short
import org.opentaint.ir.impl.cfg.util.UNINIT_THIS
import org.opentaint.ir.impl.cfg.util.lambdaMetaFactory
import org.opentaint.ir.impl.cfg.util.lambdaMetaFactoryMethodName
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.core.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRAddExpr
import org.opentaint.ir.api.jvm.cfg.JIRAndExpr
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBinaryExpr
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRByte
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRChar
import org.opentaint.ir.api.jvm.cfg.JIRClassConstant
import org.opentaint.ir.api.jvm.cfg.JIRCmpExpr
import org.opentaint.ir.api.jvm.cfg.JIRCmpgExpr
import org.opentaint.ir.api.jvm.cfg.JIRCmplExpr
import org.opentaint.ir.api.jvm.cfg.JIRConditionExpr
import org.opentaint.ir.api.jvm.cfg.JIRDivExpr
import org.opentaint.ir.api.jvm.cfg.JIRDouble
import org.opentaint.ir.api.jvm.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.jvm.cfg.JIREnterMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIREqExpr
import org.opentaint.ir.api.jvm.cfg.JIRExitMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRFloat
import org.opentaint.ir.api.jvm.cfg.JIRGeExpr
import org.opentaint.ir.api.jvm.cfg.JIRGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRGtExpr
import org.opentaint.ir.api.jvm.cfg.JIRIfInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRInstanceOfExpr
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRLambdaExpr
import org.opentaint.ir.api.jvm.cfg.JIRLeExpr
import org.opentaint.ir.api.jvm.cfg.JIRLengthExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRLong
import org.opentaint.ir.api.jvm.cfg.JIRLtExpr
import org.opentaint.ir.api.jvm.cfg.JIRMethodConstant
import org.opentaint.ir.api.jvm.cfg.JIRMethodType
import org.opentaint.ir.api.jvm.cfg.JIRMulExpr
import org.opentaint.ir.api.jvm.cfg.JIRNegExpr
import org.opentaint.ir.api.jvm.cfg.JIRNeqExpr
import org.opentaint.ir.api.jvm.cfg.JIRNewArrayExpr
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRNullConstant
import org.opentaint.ir.api.jvm.cfg.JIROrExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawExprVisitor
import org.opentaint.ir.api.jvm.cfg.JIRRawInstVisitor
import org.opentaint.ir.api.jvm.cfg.JIRRemExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRShlExpr
import org.opentaint.ir.api.jvm.cfg.JIRShort
import org.opentaint.ir.api.jvm.cfg.JIRShrExpr
import org.opentaint.ir.api.jvm.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRSubExpr
import org.opentaint.ir.api.jvm.cfg.JIRSwitchInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.cfg.JIRUshrExpr
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRXorExpr
import org.opentaint.ir.api.jvm.ext.findTypeOrNull
import org.opentaint.ir.api.jvm.ext.objectType
import org.opentaint.ir.api.jvm.ext.toType

/** This class stores state and is NOT THREAD SAFE. Use it carefully */
class JIRInstListBuilder(val method: JIRMethod, val instList: InstList<JIRRawInst>) : JIRRawInstVisitor<JIRInst?>,
    JIRRawExprVisitor<JIRExpr> {

    val classpath: JIRProject = method.enclosingClass.classpath

    private val instMap = identityMap<JIRRawInst, JIRInst>()
    private var currentLineNumber = 0
    private var index = 0
    private val labels = instList.filterIsInstance<JIRRawLabelInst>().associateBy { it.ref }
    private val convertedLocalVars = mutableMapOf<JIRRawLocalVar, JIRRawLocalVar>()
    private val inst2Index: Map<JIRRawInst, Int> = identityMap<JIRRawInst, Int>().also {
        var index = 0
        for (inst in instList) {
            it[inst] = index
            if (inst !is JIRRawLabelInst && inst !is JIRRawLineNumberInst) ++index
        }
    }

    private fun reset() {
        currentLineNumber = 0
        index = 0
    }

    fun buildInstList(): InstList<JIRInst> {
        return InstListImpl(instList.mapNotNull { convertRawInst(it) }).also {
            reset()
        }
    }

    private inline fun <reified T : JIRRawInst> handle(inst: T, handler: () -> JIRInst) =
        instMap.getOrPut(inst) { handler() }

    private fun convertRawInst(rawInst: JIRRawInst): JIRInst? = when (rawInst) {
        in instMap -> instMap[rawInst]!!
        else -> {
            val jIRInst = rawInst.accept(this)
            if (jIRInst != null) {
                instMap[rawInst] = jIRInst
            }
            jIRInst
        }
    }

    private fun TypeName.asType() = classpath.findTypeOrNull(this)
        ?: error("Could not find type $this")

    private fun label2InstRef(labelRef: JIRRawLabelRef) =
        JIRInstRef(inst2Index[labels.getValue(labelRef)]!!)

    override fun visitJIRRawAssignInst(inst: JIRRawAssignInst): JIRInst = handle(inst) {
        val preprocessedLhv =
            inst.lhv.let { unprocessedLhv ->
                if (unprocessedLhv is JIRRawLocalVar && unprocessedLhv.typeName == UNINIT_THIS) {
                    convertedLocalVars.getOrPut(unprocessedLhv) {
                        JIRRawLocalVar(unprocessedLhv.name, inst.rhv.typeName)
                    }
                } else {
                    unprocessedLhv
                }
            }
        val lhv = preprocessedLhv.accept(this) as JIRValue
        val rhv = inst.rhv.accept(this)
        JIRAssignInst(newLocation(), lhv, rhv)
    }

    override fun visitJIRRawEnterMonitorInst(inst: JIRRawEnterMonitorInst): JIRInst = handle(inst) {
        JIREnterMonitorInst(newLocation(), inst.monitor.accept(this) as JIRValue)
    }

    override fun visitJIRRawExitMonitorInst(inst: JIRRawExitMonitorInst): JIRInst = handle(inst) {
        JIRExitMonitorInst(newLocation(), inst.monitor.accept(this) as JIRValue)
    }

    override fun visitJIRRawCallInst(inst: JIRRawCallInst): JIRInst = handle(inst) {
        JIRCallInst(newLocation(), inst.callExpr.accept(this) as JIRCallExpr)
    }

    override fun visitJIRRawLabelInst(inst: JIRRawLabelInst): JIRInst? {
        return null
    }

    override fun visitJIRRawLineNumberInst(inst: JIRRawLineNumberInst): JIRInst? {
        currentLineNumber = inst.lineNumber
        return null
    }

    override fun visitJIRRawReturnInst(inst: JIRRawReturnInst): JIRInst {
        return JIRReturnInst(newLocation(), inst.returnValue?.accept(this) as? JIRValue)
    }

    override fun visitJIRRawThrowInst(inst: JIRRawThrowInst): JIRInst {
        return JIRThrowInst(newLocation(), inst.throwable.accept(this) as JIRValue)
    }

    override fun visitJIRRawCatchInst(inst: JIRRawCatchInst): JIRInst = handle(inst) {
        val location = newLocation()
        val throwableTypes = inst.entries.map { it.acceptedThrowable.asType() }
        val throwers = inst.entries.flatMap {
            val result = mutableListOf<JIRInstRef>()
            var current = instList.indexOf(labels.getValue(it.startInclusive))
            val end = instList.indexOf(labels.getValue(it.endExclusive))
            while (current != end) {
                val rawInst = instList[current]
                if (rawInst != inst) {
                    val jIRInst = convertRawInst(rawInst)
                    jIRInst?.let {
                        result += JIRInstRef(inst2Index[rawInst]!!)
                    }
                }
                ++current
            }
            result
        }.distinct()

        return JIRCatchInst(
            location,
            inst.throwable.accept(this) as JIRValue,
            throwableTypes,
            throwers
        )
    }

    override fun visitJIRRawGotoInst(inst: JIRRawGotoInst): JIRInst = handle(inst) {
        JIRGotoInst(newLocation(), label2InstRef(inst.target))
    }

    override fun visitJIRRawIfInst(inst: JIRRawIfInst): JIRInst = handle(inst) {
        JIRIfInst(
            newLocation(),
            inst.condition.accept(this) as JIRConditionExpr,
            label2InstRef(inst.trueBranch),
            label2InstRef(inst.falseBranch)
        )
    }

    override fun visitJIRRawSwitchInst(inst: JIRRawSwitchInst): JIRInst = handle(inst) {
        JIRSwitchInst(
            newLocation(),
            inst.key.accept(this) as JIRValue,
            inst.branches.map { it.key.accept(this) as JIRValue to label2InstRef(it.value) }.toMap(),
            label2InstRef(inst.default)
        )
    }

    private fun newLocation(): JIRInstLocation {
        return JIRInstLocationImpl(method, index, currentLineNumber).also {
            index++
        }
    }

    private fun convertBinary(
        expr: JIRRawBinaryExpr,
        handler: (JIRType, JIRValue, JIRValue) -> JIRBinaryExpr
    ): JIRBinaryExpr {
        val type = expr.typeName.asType()
        val lhv = expr.lhv.accept(this) as JIRValue
        val rhv = expr.rhv.accept(this) as JIRValue
        return handler(type, lhv, rhv)
    }

    override fun visitJIRRawAddExpr(expr: JIRRawAddExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRAddExpr(type, lhv, rhv) }

    override fun visitJIRRawAndExpr(expr: JIRRawAndExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRAndExpr(type, lhv, rhv) }

    override fun visitJIRRawCmpExpr(expr: JIRRawCmpExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRCmpExpr(type, lhv, rhv) }

    override fun visitJIRRawCmpgExpr(expr: JIRRawCmpgExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRCmpgExpr(type, lhv, rhv) }

    override fun visitJIRRawCmplExpr(expr: JIRRawCmplExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRCmplExpr(type, lhv, rhv) }

    override fun visitJIRRawDivExpr(expr: JIRRawDivExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRDivExpr(type, lhv, rhv) }

    override fun visitJIRRawMulExpr(expr: JIRRawMulExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRMulExpr(type, lhv, rhv) }

    override fun visitJIRRawEqExpr(expr: JIRRawEqExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIREqExpr(type, lhv, rhv) }

    override fun visitJIRRawNeqExpr(expr: JIRRawNeqExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRNeqExpr(type, lhv, rhv) }

    override fun visitJIRRawGeExpr(expr: JIRRawGeExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRGeExpr(type, lhv, rhv) }

    override fun visitJIRRawGtExpr(expr: JIRRawGtExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRGtExpr(type, lhv, rhv) }

    override fun visitJIRRawLeExpr(expr: JIRRawLeExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRLeExpr(type, lhv, rhv) }

    override fun visitJIRRawLtExpr(expr: JIRRawLtExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRLtExpr(type, lhv, rhv) }

    override fun visitJIRRawOrExpr(expr: JIRRawOrExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIROrExpr(type, lhv, rhv) }

    override fun visitJIRRawRemExpr(expr: JIRRawRemExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRRemExpr(type, lhv, rhv) }

    override fun visitJIRRawShlExpr(expr: JIRRawShlExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRShlExpr(type, lhv, rhv) }

    override fun visitJIRRawShrExpr(expr: JIRRawShrExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRShrExpr(type, lhv, rhv) }

    override fun visitJIRRawSubExpr(expr: JIRRawSubExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRSubExpr(type, lhv, rhv) }

    override fun visitJIRRawUshrExpr(expr: JIRRawUshrExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRUshrExpr(type, lhv, rhv) }

    override fun visitJIRRawXorExpr(expr: JIRRawXorExpr): JIRExpr =
        convertBinary(expr) { type, lhv, rhv -> JIRXorExpr(type, lhv, rhv) }

    override fun visitJIRRawLengthExpr(expr: JIRRawLengthExpr): JIRExpr {
        return JIRLengthExpr(classpath.int, expr.array.accept(this) as JIRValue)
    }

    override fun visitJIRRawNegExpr(expr: JIRRawNegExpr): JIRExpr =
        JIRNegExpr(expr.typeName.asType(), expr.operand.accept(this) as JIRValue)

    override fun visitJIRRawCastExpr(expr: JIRRawCastExpr): JIRExpr =
        JIRCastExpr(expr.typeName.asType(), expr.operand.accept(this) as JIRValue)

    override fun visitJIRRawNewExpr(expr: JIRRawNewExpr): JIRExpr = JIRNewExpr(expr.typeName.asType())

    override fun visitJIRRawNewArrayExpr(expr: JIRRawNewArrayExpr): JIRExpr =
        JIRNewArrayExpr(expr.typeName.asType(), expr.dimensions.map { it.accept(this) as JIRValue })

    override fun visitJIRRawInstanceOfExpr(expr: JIRRawInstanceOfExpr): JIRExpr =
        JIRInstanceOfExpr(classpath.boolean, expr.operand.accept(this) as JIRValue, expr.targetType.asType())

    override fun visitJIRRawDynamicCallExpr(expr: JIRRawDynamicCallExpr): JIRExpr {
        if (expr.bsm.declaringClass == lambdaMetaFactory && expr.bsm.name == lambdaMetaFactoryMethodName) {
            val lambdaExpr = tryResolveJIRLambdaExpr(expr)
            if (lambdaExpr != null) return lambdaExpr
        }

        return JIRDynamicCallExpr(
            classpath.methodRef(expr),
            expr.bsmArgs,
            expr.callSiteMethodName,
            expr.callSiteArgTypes.map { it.asType() },
            expr.callSiteReturnType.asType(),
            expr.callSiteArgs.map { it.accept(this) as JIRValue }
        )
    }

    private fun tryResolveJIRLambdaExpr(expr: JIRRawDynamicCallExpr): JIRLambdaExpr? {
        if (expr.bsmArgs.size != 3) return null
        val (interfaceMethodType, implementation, dynamicMethodType) = expr.bsmArgs

        if (interfaceMethodType !is BsmMethodTypeArg) return null
        if (dynamicMethodType !is BsmMethodTypeArg) return null
        if (implementation !is BsmHandle) return null

        // Check implementation signature match (starts with) call site arguments
        for ((index, argType) in expr.callSiteArgTypes.withIndex()) {
            if (argType != implementation.argTypes.getOrNull(index)) return null
        }

        val klass = implementation.declaringClass.asType() as JIRClassType
        val actualMethod = TypedMethodRefImpl(
            klass, implementation.name, implementation.argTypes, implementation.returnType
        )

        return JIRLambdaExpr(
            classpath.methodRef(expr),
            actualMethod,
            interfaceMethodType,
            dynamicMethodType,
            expr.callSiteMethodName,
            expr.callSiteArgTypes.map { it.asType() },
            expr.callSiteReturnType.asType(),
            expr.callSiteArgs.map { it.accept(this) as JIRValue }
        )
    }

    override fun visitJIRRawVirtualCallExpr(expr: JIRRawVirtualCallExpr): JIRExpr {
        val instance = expr.instance.accept(this) as JIRValue
        val args = expr.args.map { it.accept(this) as JIRValue }
        return JIRVirtualCallExpr(VirtualMethodRefImpl.of(classpath, expr), instance, args)
    }

    override fun visitJIRRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr): JIRExpr {
        val instance = expr.instance.accept(this) as JIRValue
        val args = expr.args.map { it.accept(this) as JIRValue }
        return JIRVirtualCallExpr(VirtualMethodRefImpl.of(classpath, expr), instance, args)
    }

    override fun visitJIRRawStaticCallExpr(expr: JIRRawStaticCallExpr): JIRExpr {
        val args = expr.args.map { it.accept(this) as JIRValue }
        return JIRStaticCallExpr(classpath.methodRef(expr), args)
    }

    override fun visitJIRRawSpecialCallExpr(expr: JIRRawSpecialCallExpr): JIRExpr {
        val instance = expr.instance.accept(this) as JIRValue
        val args = expr.args.map { it.accept(this) as JIRValue }
        return JIRSpecialCallExpr(classpath.methodRef(expr), instance, args)
    }

    override fun visitJIRRawThis(value: JIRRawThis): JIRExpr = JIRThis(method.enclosingClass.toType())

    override fun visitJIRRawArgument(value: JIRRawArgument): JIRExpr = method.parameters[value.index].let {
        JIRArgument.of(it.index, value.name, it.type.asType())
    }

    override fun visitJIRRawLocalVar(value: JIRRawLocalVar): JIRExpr =
        convertedLocalVars[value]?.let { replacementForLocalVar ->
            JIRLocalVar(replacementForLocalVar.name, replacementForLocalVar.typeName.asType())
        } ?: JIRLocalVar(value.name, value.typeName.asType())

    override fun visitJIRRawFieldRef(value: JIRRawFieldRef): JIRExpr {
        val type = value.declaringClass.asType() as JIRClassType
        val field = type.lookup.field(value.fieldName, value.typeName)
            ?: throw IllegalStateException("${type.typeName}#${value.fieldName} not found")
        return JIRFieldRef(value.instance?.accept(this) as? JIRValue, field)
    }

    override fun visitJIRRawArrayAccess(value: JIRRawArrayAccess): JIRExpr =
        JIRArrayAccess(
            value.array.accept(this) as JIRValue,
            value.index.accept(this) as JIRValue,
            value.typeName.asType()
        )

    override fun visitJIRRawBool(value: JIRRawBool): JIRExpr = JIRBool(value.value, classpath.boolean)

    override fun visitJIRRawByte(value: JIRRawByte): JIRExpr = JIRByte(value.value, classpath.byte)

    override fun visitJIRRawChar(value: JIRRawChar): JIRExpr = JIRChar(value.value, classpath.char)

    override fun visitJIRRawShort(value: JIRRawShort): JIRExpr = JIRShort(value.value, classpath.short)

    override fun visitJIRRawInt(value: JIRRawInt): JIRExpr = JIRInt(value.value, classpath.int)

    override fun visitJIRRawLong(value: JIRRawLong): JIRExpr = JIRLong(value.value, classpath.long)

    override fun visitJIRRawFloat(value: JIRRawFloat): JIRExpr = JIRFloat(value.value, classpath.float)

    override fun visitJIRRawDouble(value: JIRRawDouble): JIRExpr = JIRDouble(value.value, classpath.double)

    override fun visitJIRRawNullConstant(value: JIRRawNullConstant): JIRExpr =
        JIRNullConstant(classpath.objectType)

    override fun visitJIRRawStringConstant(value: JIRRawStringConstant): JIRExpr =
        JIRStringConstant(value.value, value.typeName.asType())

    override fun visitJIRRawClassConstant(value: JIRRawClassConstant): JIRExpr =
        JIRClassConstant(value.className.asType(), value.typeName.asType())

    override fun visitJIRRawMethodConstant(value: JIRRawMethodConstant): JIRExpr {
        val klass = value.declaringClass.asType() as JIRClassType
        val argumentTypes = value.argumentTypes.map { it.asType() }
        val returnType = value.returnType.asType()
        val constant = klass.declaredMethods.first {
            it.name == value.name && it.returnType == returnType && it.parameters.map { param -> param.type } == argumentTypes
        }
        return JIRMethodConstant(constant, value.typeName.asType())
    }

    override fun visitJIRRawMethodType(value: JIRRawMethodType): JIRExpr {
        return JIRMethodType(
            value.argumentTypes.map { it.asType() },
            value.returnType.asType(),
            value.typeName.asType()
        )
    }
}