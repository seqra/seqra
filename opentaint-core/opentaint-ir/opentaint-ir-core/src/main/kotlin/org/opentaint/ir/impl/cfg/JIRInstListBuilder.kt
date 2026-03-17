package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.api.jvm.cfg.*
import org.opentaint.ir.api.jvm.ext.*
import org.opentaint.ir.impl.cfg.util.UNINIT_THIS
import org.opentaint.ir.impl.cfg.util.lambdaMetaFactory
import org.opentaint.ir.impl.cfg.util.lambdaMetaFactoryMethodName

/** This class stores state and is NOT THREAD SAFE. Use it carefully */
class JIRInstListBuilder(val method: JIRMethod,val instList: JIRInstList<JIRRawInst>) : JIRRawInstVisitor<JIRInst?>, JIRRawExprVisitor<JIRExpr> {

    val classpath: JIRClasspath = method.enclosingClass.classpath

    private val instMap = identityMap<JIRRawInst, JIRInst>()
    private var currentLineNumber = 0
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
    }

    fun buildInstList(): JIRInstList<JIRInst> {
        return JIRInstListImpl(instList.mapNotNull { convertRawInst(it) }).also {
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
                        JIRRawLocalVar(unprocessedLhv.index, unprocessedLhv.name, inst.rhv.typeName, unprocessedLhv.kind)
                    }
                } else {
                    unprocessedLhv
                }
            }
        val lhv = preprocessedLhv.accept(this) as JIRValue
        val rhv = inst.rhv.accept(this)
        JIRAssignInst(newLocation(inst), lhv, rhv)
    }

    override fun visitJIRRawEnterMonitorInst(inst: JIRRawEnterMonitorInst): JIRInst = handle(inst) {
        JIREnterMonitorInst(newLocation(inst), inst.monitor.accept(this) as JIRValue)
    }

    override fun visitJIRRawExitMonitorInst(inst: JIRRawExitMonitorInst): JIRInst = handle(inst) {
        JIRExitMonitorInst(newLocation(inst), inst.monitor.accept(this) as JIRValue)
    }

    override fun visitJIRRawCallInst(inst: JIRRawCallInst): JIRInst = handle(inst) {
        JIRCallInst(newLocation(inst), inst.callExpr.accept(this) as JIRCallExpr)
    }

    override fun visitJIRRawLabelInst(inst: JIRRawLabelInst): JIRInst? {
        return null
    }

    override fun visitJIRRawLineNumberInst(inst: JIRRawLineNumberInst): JIRInst? {
        currentLineNumber = inst.lineNumber
        return null
    }

    override fun visitJIRRawReturnInst(inst: JIRRawReturnInst): JIRInst {
        return JIRReturnInst(newLocation(inst), inst.returnValue?.accept(this) as? JIRValue)
    }

    override fun visitJIRRawThrowInst(inst: JIRRawThrowInst): JIRInst {
        return JIRThrowInst(newLocation(inst), inst.throwable.accept(this) as JIRValue)
    }

    override fun visitJIRRawCatchInst(inst: JIRRawCatchInst): JIRInst = handle(inst) {
        val location = newLocation(inst)
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
        JIRGotoInst(newLocation(inst), label2InstRef(inst.target))
    }

    override fun visitJIRRawIfInst(inst: JIRRawIfInst): JIRInst = handle(inst) {
        JIRIfInst(
            newLocation(inst),
            inst.condition.accept(this) as JIRConditionExpr,
            label2InstRef(inst.trueBranch),
            label2InstRef(inst.falseBranch)
        )
    }

    override fun visitJIRRawSwitchInst(inst: JIRRawSwitchInst): JIRInst = handle(inst) {
        JIRSwitchInst(
            newLocation(inst),
            inst.key.accept(this) as JIRValue,
            inst.branches.map { it.key.accept(this) as JIRValue to label2InstRef(it.value) }.toMap(),
            label2InstRef(inst.default)
        )
    }

    private fun newLocation(rawInst: JIRRawInst): JIRInstLocation {
        val index = inst2Index.getValue(rawInst)
        return JIRInstLocationImpl(method, index, currentLineNumber)
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

        val argTypes: List<TypeName>
        val tag = implementation.tag

        check(tag is BsmHandleTag.MethodHandle) {
            "Unexpected tag for invoke dynamic $tag"
        }

        when (tag) {
            BsmHandleTag.MethodHandle.INVOKE_STATIC,
            BsmHandleTag.MethodHandle.NEW_INVOKE_SPECIAL -> {
                // Invoke static or invoke constructor case
                argTypes = implementation.argTypes
            }

            BsmHandleTag.MethodHandle.INVOKE_VIRTUAL,
            BsmHandleTag.MethodHandle.INVOKE_SPECIAL,
            BsmHandleTag.MethodHandle.INVOKE_INTERFACE -> {
                // Invoke non-static case
                argTypes = implementation.argTypes.toMutableList()
                // Adding 'this' type as first argument type
                argTypes.add(0, implementation.declaringClass)
            }
        }

        // Check implementation signature match (starts with) call site arguments
        for ((index, argType) in expr.callSiteArgTypes.withIndex()) {
            if (argType != argTypes.getOrNull(index)) return null
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
            expr.callSiteArgs.map { it.accept(this) as JIRValue },
            tag
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
            JIRLocalVar(replacementForLocalVar.index, replacementForLocalVar.name, replacementForLocalVar.typeName.asType())
        } ?: JIRLocalVar(value.index, value.name, value.typeName.asType())

    override fun visitJIRRawFieldRef(value: JIRRawFieldRef): JIRExpr {
        val type = value.declaringClass.asType() as JIRClassType
        val fieldLookupKind = if (value.instance == null) JIRLookup.FieldKind.STATIC else JIRLookup.FieldKind.INSTANCE
        val field = type.lookup.field(value.fieldName, value.typeName, fieldLookupKind)
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
