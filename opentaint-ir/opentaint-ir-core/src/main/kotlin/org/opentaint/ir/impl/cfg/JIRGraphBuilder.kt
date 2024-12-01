package org.opentaint.opentaint-ir.impl.cfg

import org.opentaint.opentaint-ir.api.JIRClassType
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.JIRType
import org.opentaint.opentaint-ir.api.JIRTypedMethod
import org.opentaint.opentaint-ir.api.TypeName
import org.opentaint.opentaint-ir.api.boolean
import org.opentaint.opentaint-ir.api.byte
import org.opentaint.opentaint-ir.api.cfg.BsmHandle
import org.opentaint.opentaint-ir.api.cfg.JIRAddExpr
import org.opentaint.opentaint-ir.api.cfg.JIRAndExpr
import org.opentaint.opentaint-ir.api.cfg.JIRArgument
import org.opentaint.opentaint-ir.api.cfg.JIRArrayAccess
import org.opentaint.opentaint-ir.api.cfg.JIRAssignInst
import org.opentaint.opentaint-ir.api.cfg.JIRBinaryExpr
import org.opentaint.opentaint-ir.api.cfg.JIRBool
import org.opentaint.opentaint-ir.api.cfg.JIRByte
import org.opentaint.opentaint-ir.api.cfg.JIRCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRCallInst
import org.opentaint.opentaint-ir.api.cfg.JIRCastExpr
import org.opentaint.opentaint-ir.api.cfg.JIRCatchInst
import org.opentaint.opentaint-ir.api.cfg.JIRChar
import org.opentaint.opentaint-ir.api.cfg.JIRClassConstant
import org.opentaint.opentaint-ir.api.cfg.JIRCmpExpr
import org.opentaint.opentaint-ir.api.cfg.JIRCmpgExpr
import org.opentaint.opentaint-ir.api.cfg.JIRCmplExpr
import org.opentaint.opentaint-ir.api.cfg.JIRConditionExpr
import org.opentaint.opentaint-ir.api.cfg.JIRDivExpr
import org.opentaint.opentaint-ir.api.cfg.JIRDouble
import org.opentaint.opentaint-ir.api.cfg.JIRDynamicCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIREnterMonitorInst
import org.opentaint.opentaint-ir.api.cfg.JIREqExpr
import org.opentaint.opentaint-ir.api.cfg.JIRExitMonitorInst
import org.opentaint.opentaint-ir.api.cfg.JIRExpr
import org.opentaint.opentaint-ir.api.cfg.JIRFieldRef
import org.opentaint.opentaint-ir.api.cfg.JIRFloat
import org.opentaint.opentaint-ir.api.cfg.JIRGeExpr
import org.opentaint.opentaint-ir.api.cfg.JIRGotoInst
import org.opentaint.opentaint-ir.api.cfg.JIRGraph
import org.opentaint.opentaint-ir.api.cfg.JIRGtExpr
import org.opentaint.opentaint-ir.api.cfg.JIRIfInst
import org.opentaint.opentaint-ir.api.cfg.JIRInst
import org.opentaint.opentaint-ir.api.cfg.JIRInstRef
import org.opentaint.opentaint-ir.api.cfg.JIRInstanceOfExpr
import org.opentaint.opentaint-ir.api.cfg.JIRInt
import org.opentaint.opentaint-ir.api.cfg.JIRLambdaExpr
import org.opentaint.opentaint-ir.api.cfg.JIRLeExpr
import org.opentaint.opentaint-ir.api.cfg.JIRLengthExpr
import org.opentaint.opentaint-ir.api.cfg.JIRLocal
import org.opentaint.opentaint-ir.api.cfg.JIRLong
import org.opentaint.opentaint-ir.api.cfg.JIRLtExpr
import org.opentaint.opentaint-ir.api.cfg.JIRMethodConstant
import org.opentaint.opentaint-ir.api.cfg.JIRMulExpr
import org.opentaint.opentaint-ir.api.cfg.JIRNegExpr
import org.opentaint.opentaint-ir.api.cfg.JIRNeqExpr
import org.opentaint.opentaint-ir.api.cfg.JIRNewArrayExpr
import org.opentaint.opentaint-ir.api.cfg.JIRNewExpr
import org.opentaint.opentaint-ir.api.cfg.JIRNullConstant
import org.opentaint.opentaint-ir.api.cfg.JIROrExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawAddExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawAndExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawArgument
import org.opentaint.opentaint-ir.api.cfg.JIRRawArrayAccess
import org.opentaint.opentaint-ir.api.cfg.JIRRawAssignInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawBinaryExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawBool
import org.opentaint.opentaint-ir.api.cfg.JIRRawByte
import org.opentaint.opentaint-ir.api.cfg.JIRRawCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawCallInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawCastExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawCatchInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawChar
import org.opentaint.opentaint-ir.api.cfg.JIRRawClassConstant
import org.opentaint.opentaint-ir.api.cfg.JIRRawCmpExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawCmpgExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawCmplExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawDivExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawDouble
import org.opentaint.opentaint-ir.api.cfg.JIRRawDynamicCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawEnterMonitorInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawEqExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawExitMonitorInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawExprVisitor
import org.opentaint.opentaint-ir.api.cfg.JIRRawFieldRef
import org.opentaint.opentaint-ir.api.cfg.JIRRawFloat
import org.opentaint.opentaint-ir.api.cfg.JIRRawGeExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawGotoInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawGtExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawIfInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawInstVisitor
import org.opentaint.opentaint-ir.api.cfg.JIRRawInstanceOfExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawInt
import org.opentaint.opentaint-ir.api.cfg.JIRRawInterfaceCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawLabelInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawLabelRef
import org.opentaint.opentaint-ir.api.cfg.JIRRawLeExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawLengthExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawLocal
import org.opentaint.opentaint-ir.api.cfg.JIRRawLong
import org.opentaint.opentaint-ir.api.cfg.JIRRawLtExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawMethodConstant
import org.opentaint.opentaint-ir.api.cfg.JIRRawMulExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawNegExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawNeqExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawNewArrayExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawNewExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawNullConstant
import org.opentaint.opentaint-ir.api.cfg.JIRRawOrExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawRemExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawReturnInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawShlExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawShort
import org.opentaint.opentaint-ir.api.cfg.JIRRawShrExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawSpecialCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawStaticCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawStringConstant
import org.opentaint.opentaint-ir.api.cfg.JIRRawSubExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawSwitchInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawThis
import org.opentaint.opentaint-ir.api.cfg.JIRRawThrowInst
import org.opentaint.opentaint-ir.api.cfg.JIRRawUshrExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawVirtualCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRawXorExpr
import org.opentaint.opentaint-ir.api.cfg.JIRRemExpr
import org.opentaint.opentaint-ir.api.cfg.JIRReturnInst
import org.opentaint.opentaint-ir.api.cfg.JIRShlExpr
import org.opentaint.opentaint-ir.api.cfg.JIRShort
import org.opentaint.opentaint-ir.api.cfg.JIRShrExpr
import org.opentaint.opentaint-ir.api.cfg.JIRSpecialCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRStaticCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRStringConstant
import org.opentaint.opentaint-ir.api.cfg.JIRSubExpr
import org.opentaint.opentaint-ir.api.cfg.JIRSwitchInst
import org.opentaint.opentaint-ir.api.cfg.JIRThis
import org.opentaint.opentaint-ir.api.cfg.JIRThrowInst
import org.opentaint.opentaint-ir.api.cfg.JIRUshrExpr
import org.opentaint.opentaint-ir.api.cfg.JIRValue
import org.opentaint.opentaint-ir.api.cfg.JIRVirtualCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRXorExpr
import org.opentaint.opentaint-ir.api.char
import org.opentaint.opentaint-ir.api.double
import org.opentaint.opentaint-ir.api.ext.anyType
import org.opentaint.opentaint-ir.api.ext.findTypeOrNull
import org.opentaint.opentaint-ir.api.ext.toType
import org.opentaint.opentaint-ir.api.float
import org.opentaint.opentaint-ir.api.int
import org.opentaint.opentaint-ir.api.long
import org.opentaint.opentaint-ir.api.short

class JIRGraphBuilder(
    val classpath: JIRClasspath,
    val instList: JIRRawInstListImpl,
    val method: JIRMethod
) : JIRRawInstVisitor<JIRInst?>, JIRRawExprVisitor<JIRExpr> {
    private val instMap = mutableMapOf<JIRRawInst, JIRInst>()
    private val labels = instList.filterIsInstance<JIRRawLabelInst>().associateBy { it.ref }
    private val inst2Index: Map<JIRRawInst, Int> = run {
        val res = mutableMapOf<JIRRawInst, Int>()
        var index = 0
        for (inst in instList) {
            res[inst] = index
            if (inst !is JIRRawLabelInst) ++index
        }
        res
    }

    fun build(): JIRGraph = JIRGraphImpl(classpath, instList.mapNotNull { convertRawInst(it) })

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

    private val TypeName.asType
        get() = classpath.findTypeOrNull(this)
            ?: error("Could not find type $this")

    private fun label2InstRef(labelRef: JIRRawLabelRef) =
        JIRInstRef(inst2Index[labels.getValue(labelRef)]!!)

    override fun visitJIRRawAssignInst(inst: JIRRawAssignInst): JIRInst = handle(inst) {
        val lhv = inst.lhv.accept(this) as JIRValue
        val rhv = inst.rhv.accept(this)
        JIRAssignInst(lhv, rhv)
    }

    override fun visitJIRRawEnterMonitorInst(inst: JIRRawEnterMonitorInst): JIRInst = handle(inst) {
        JIREnterMonitorInst(inst.monitor.accept(this) as JIRValue)
    }

    override fun visitJIRRawExitMonitorInst(inst: JIRRawExitMonitorInst): JIRInst = handle(inst) {
        JIRExitMonitorInst(inst.monitor.accept(this) as JIRValue)
    }

    override fun visitJIRRawCallInst(inst: JIRRawCallInst): JIRInst = handle(inst) {
        JIRCallInst(inst.callExpr.accept(this) as JIRCallExpr)
    }

    override fun visitJIRRawLabelInst(inst: JIRRawLabelInst): JIRInst? {
        return null
    }

    override fun visitJIRRawReturnInst(inst: JIRRawReturnInst): JIRInst {
        return JIRReturnInst(inst.returnValue?.accept(this) as? JIRValue)
    }

    override fun visitJIRRawThrowInst(inst: JIRRawThrowInst): JIRInst {
        return JIRThrowInst(inst.throwable.accept(this) as JIRValue)
    }

    override fun visitJIRRawCatchInst(inst: JIRRawCatchInst): JIRInst = handle(inst) {
        val throwers = run {
            val result = mutableListOf<JIRInstRef>()
            var current = instList.indexOf(labels.getValue(inst.startInclusive))
            val end = instList.indexOf(labels.getValue(inst.endExclusive))
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
        }
        return JIRCatchInst(
            inst.throwable.accept(this) as JIRValue,
            throwers
        )
    }

    override fun visitJIRRawGotoInst(inst: JIRRawGotoInst): JIRInst = handle(inst) {
        JIRGotoInst(label2InstRef(inst.target))
    }

    override fun visitJIRRawIfInst(inst: JIRRawIfInst): JIRInst = handle(inst) {
        JIRIfInst(
            inst.condition.accept(this) as JIRConditionExpr,
            label2InstRef(inst.trueBranch),
            label2InstRef(inst.falseBranch)
        )
    }

    override fun visitJIRRawSwitchInst(inst: JIRRawSwitchInst): JIRInst = handle(inst) {
        JIRSwitchInst(
            inst.key.accept(this) as JIRValue,
            inst.branches.map { it.key.accept(this) as JIRValue to label2InstRef(it.value) }.toMap(),
            label2InstRef(inst.default)
        )
    }

    private fun convertBinary(
        expr: JIRRawBinaryExpr,
        handler: (JIRType, JIRValue, JIRValue) -> JIRBinaryExpr
    ): JIRBinaryExpr {
        val type = expr.typeName.asType
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
        JIRNegExpr(expr.typeName.asType, expr.operand.accept(this) as JIRValue)

    override fun visitJIRRawCastExpr(expr: JIRRawCastExpr): JIRExpr =
        JIRCastExpr(expr.typeName.asType, expr.operand.accept(this) as JIRValue)

    override fun visitJIRRawNewExpr(expr: JIRRawNewExpr): JIRExpr = JIRNewExpr(expr.typeName.asType)

    override fun visitJIRRawNewArrayExpr(expr: JIRRawNewArrayExpr): JIRExpr =
        JIRNewArrayExpr(expr.typeName.asType, expr.dimensions.map { it.accept(this) as JIRValue })

    override fun visitJIRRawInstanceOfExpr(expr: JIRRawInstanceOfExpr): JIRExpr =
        JIRInstanceOfExpr(classpath.boolean, expr.operand.accept(this) as JIRValue, expr.targetType.asType)

    private fun JIRClassType.getMethod(name: String, argTypes: List<TypeName>, returnType: TypeName): JIRTypedMethod {
        return methods.firstOrNull { typedMethod ->
            val jIRMethod = typedMethod.method
            jIRMethod.name == name &&
                    jIRMethod.returnType.typeName == returnType.typeName &&
                    jIRMethod.parameters.map { param -> param.type.typeName } == argTypes.map { it.typeName }
        }
            ?: error("Could not find a method with correct signature")
    }

    private val JIRRawCallExpr.typedMethod: JIRTypedMethod
        get() {
            val klass = declaringClass.asType as JIRClassType
            return klass.getMethod(methodName, argumentTypes, returnType)
        }

    override fun visitJIRRawDynamicCallExpr(expr: JIRRawDynamicCallExpr): JIRExpr {
        val lambdaBases = expr.bsmArgs.filterIsInstance<BsmHandle>()
        when (lambdaBases.size) {
            1 -> {
                val base = lambdaBases.first()
                val klass = base.declaringClass.asType as JIRClassType
                val typedBase = klass.getMethod(base.name, base.argTypes, base.returnType)

                return JIRLambdaExpr(typedBase, expr.args.map { it.accept(this) as JIRValue })
            }

            else -> {
                val bsm = expr.typedMethod
                return JIRDynamicCallExpr(
                    bsm,
                    expr.bsmArgs,
                    expr.callCiteMethodName,
                    expr.callCiteArgTypes.map { it.asType },
                    expr.callCiteReturnType.asType,
                    expr.args.map { it.accept(this) as JIRValue }
                )
            }
        }
    }

    override fun visitJIRRawVirtualCallExpr(expr: JIRRawVirtualCallExpr): JIRExpr {
        val instance = expr.instance.accept(this) as JIRValue
        val klass = instance.type as? JIRClassType ?: classpath.anyType()
        val method = klass.getMethod(expr.methodName, expr.argumentTypes, expr.returnType)
        val args = expr.args.map { it.accept(this) as JIRValue }
        return JIRVirtualCallExpr(
            method, instance, args
        )
    }

    override fun visitJIRRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr): JIRExpr {
        val instance = expr.instance.accept(this) as JIRValue
        val klass = instance.type as? JIRClassType ?: classpath.anyType()
        val method = klass.getMethod(expr.methodName, expr.argumentTypes, expr.returnType)
        val args = expr.args.map { it.accept(this) as JIRValue }
        return JIRVirtualCallExpr(
            method, instance, args
        )
    }

    override fun visitJIRRawStaticCallExpr(expr: JIRRawStaticCallExpr): JIRExpr {
        val method = expr.typedMethod
        val args = expr.args.map { it.accept(this) as JIRValue }
        return JIRStaticCallExpr(method, args)
    }

    override fun visitJIRRawSpecialCallExpr(expr: JIRRawSpecialCallExpr): JIRExpr {
        val method = expr.typedMethod
        val instance = expr.instance.accept(this) as JIRValue
        val args = expr.args.map { it.accept(this) as JIRValue }
        return JIRSpecialCallExpr(
            method, instance, args,
        )
    }

    override fun visitJIRRawThis(value: JIRRawThis): JIRExpr =
        JIRThis(method.enclosingClass.toType())

    override fun visitJIRRawArgument(value: JIRRawArgument): JIRExpr = method.parameters[value.index].let {
        JIRArgument(it.index, null, it.type.asType)
    }

    override fun visitJIRRawLocal(value: JIRRawLocal): JIRExpr =
        JIRLocal(value.name, value.typeName.asType)

    override fun visitJIRRawFieldRef(value: JIRRawFieldRef): JIRExpr {
        val instance = value.instance?.accept(this) as? JIRValue
        val klass = (instance?.type ?: value.declaringClass.asType) as JIRClassType
        val field =
            klass.fields.first { it.name == value.fieldName && it.field.type.typeName == value.typeName.typeName }
        return JIRFieldRef(value.instance?.accept(this) as? JIRValue, field)
    }

    override fun visitJIRRawArrayAccess(value: JIRRawArrayAccess): JIRExpr =
        JIRArrayAccess(
            value.array.accept(this) as JIRValue,
            value.index.accept(this) as JIRValue,
            value.typeName.asType
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
        JIRNullConstant(classpath.anyType())

    override fun visitJIRRawStringConstant(value: JIRRawStringConstant): JIRExpr =
        JIRStringConstant(value.value, value.typeName.asType)

    override fun visitJIRRawClassConstant(value: JIRRawClassConstant): JIRExpr =
        JIRClassConstant(value.className.asType, value.typeName.asType)

    override fun visitJIRRawMethodConstant(value: JIRRawMethodConstant): JIRExpr {
        val klass = value.declaringClass.asType as JIRClassType
        val argumentTypes = value.argumentTypes.map { it.asType }
        val returnType = value.returnType.asType
        val constant = klass.declaredMethods.first {
            it.name == value.name && it.returnType == returnType && it.parameters.map { param -> param.type } == argumentTypes
        }
        return JIRMethodConstant(constant, value.typeName.asType)
    }
}
