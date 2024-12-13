package org.opentaint.opentaint-ir.impl.cfg

import org.opentaint.opentaint-ir.api.*
import org.opentaint.opentaint-ir.api.cfg.*
import org.opentaint.opentaint-ir.api.ext.anyType
import org.opentaint.opentaint-ir.api.ext.findTypeOrNull
import org.opentaint.opentaint-ir.api.ext.toType

class JIRGraphBuilder(
    val classpath: JIRClasspath,
    val instList: JIRRawInstListImpl,
    val method: JIRMethod
) : JIRRawInstVisitor<JIRInst?>, JIRRawExprVisitor<JIRExpr> {
    private val instMap = mutableMapOf<JIRRawInst, JIRInst>()
    private var currentLineNumber = 0
    private val labels = instList.filterIsInstance<JIRRawLabelInst>().associateBy { it.ref }
    private val inst2Index: Map<JIRRawInst, Int> = run {
        val res = mutableMapOf<JIRRawInst, Int>()
        var index = 0
        for (inst in instList) {
            res[inst] = index
            if (inst !is JIRRawLabelInst && inst !is JIRRawLineNumberInst) ++index
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
        JIRAssignInst(currentLineNumber, lhv, rhv)
    }

    override fun visitJIRRawEnterMonitorInst(inst: JIRRawEnterMonitorInst): JIRInst = handle(inst) {
        JIREnterMonitorInst(currentLineNumber, inst.monitor.accept(this) as JIRValue)
    }

    override fun visitJIRRawExitMonitorInst(inst: JIRRawExitMonitorInst): JIRInst = handle(inst) {
        JIRExitMonitorInst(currentLineNumber, inst.monitor.accept(this) as JIRValue)
    }

    override fun visitJIRRawCallInst(inst: JIRRawCallInst): JIRInst = handle(inst) {
        JIRCallInst(currentLineNumber, inst.callExpr.accept(this) as JIRCallExpr)
    }

    override fun visitJIRRawLabelInst(inst: JIRRawLabelInst): JIRInst? {
        return null
    }

    override fun visitJIRRawLineNumberInst(inst: JIRRawLineNumberInst): JIRInst? {
        currentLineNumber = inst.lineNumber
        return null
    }

    override fun visitJIRRawReturnInst(inst: JIRRawReturnInst): JIRInst {
        return JIRReturnInst(currentLineNumber, inst.returnValue?.accept(this) as? JIRValue)
    }

    override fun visitJIRRawThrowInst(inst: JIRRawThrowInst): JIRInst {
        return JIRThrowInst(currentLineNumber, inst.throwable.accept(this) as JIRValue)
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
            currentLineNumber,
            inst.throwable.accept(this) as JIRValue,
            throwers
        )
    }

    override fun visitJIRRawGotoInst(inst: JIRRawGotoInst): JIRInst = handle(inst) {
        JIRGotoInst(currentLineNumber, label2InstRef(inst.target))
    }

    override fun visitJIRRawIfInst(inst: JIRRawIfInst): JIRInst = handle(inst) {
        JIRIfInst(
            currentLineNumber,
            inst.condition.accept(this) as JIRConditionExpr,
            label2InstRef(inst.trueBranch),
            label2InstRef(inst.falseBranch)
        )
    }

    override fun visitJIRRawSwitchInst(inst: JIRRawSwitchInst): JIRInst = handle(inst) {
        JIRSwitchInst(
            currentLineNumber,
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
        JIRArgument(it.index, value.name, it.type.asType)
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
