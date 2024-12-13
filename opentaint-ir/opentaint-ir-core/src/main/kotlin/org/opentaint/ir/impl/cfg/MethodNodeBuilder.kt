package org.opentaint.opentaint-ir.impl.cfg

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.PredefinedPrimitives
import org.opentaint.opentaint-ir.api.TypeName
import org.opentaint.opentaint-ir.api.cfg.*
import org.opentaint.opentaint-ir.api.ext.isStatic
import org.opentaint.opentaint-ir.api.ext.jvmName
import org.opentaint.opentaint-ir.impl.cfg.util.*

private val PredefinedPrimitives.smallIntegers get() = setOf(Boolean, Byte, Char, Short, Int)

private val TypeName.shortInt
    get() = when (this.typeName) {
        PredefinedPrimitives.Long -> 1
        in PredefinedPrimitives.smallIntegers -> 0
        PredefinedPrimitives.Float -> 2
        PredefinedPrimitives.Double -> 3
        else -> 4
    }
private val TypeName.longInt
    get() = when (this.typeName) {
        PredefinedPrimitives.Boolean -> 5
        PredefinedPrimitives.Byte -> 5
        PredefinedPrimitives.Short -> 7
        PredefinedPrimitives.Char -> 6
        PredefinedPrimitives.Int -> 0
        PredefinedPrimitives.Long -> 1
        PredefinedPrimitives.Float -> 2
        PredefinedPrimitives.Double -> 3
        else -> 4
    }

private val TypeName.typeInt
    get() = when (typeName) {
        PredefinedPrimitives.Char -> Opcodes.T_CHAR
        PredefinedPrimitives.Boolean -> Opcodes.T_BOOLEAN
        PredefinedPrimitives.Byte -> Opcodes.T_BYTE
        PredefinedPrimitives.Double -> Opcodes.T_DOUBLE
        PredefinedPrimitives.Float -> Opcodes.T_FLOAT
        PredefinedPrimitives.Int -> Opcodes.T_INT
        PredefinedPrimitives.Long -> Opcodes.T_LONG
        PredefinedPrimitives.Short -> Opcodes.T_SHORT
        else -> error("$typeName is not primitive type")
    }

class MethodNodeBuilder(
    val method: JIRMethod,
    val instList: JIRRawInstList
) : JIRRawInstVisitor<Unit>, JIRRawExprVisitor<Unit> {
    private var localIndex = 0
    private var stackSize = 0
    private var maxStack = 0
    private val locals = mutableMapOf<JIRRawValue, Int>()
    private val labelRefMap = instList.instructions.filterIsInstance<JIRRawLabelInst>().associateBy { it.ref }
    private val labels = mutableMapOf<JIRRawLabelInst, LabelNode>()
    private val currentInsnList = InsnList()
    private val tryCatchNodeList = mutableListOf<TryCatchBlockNode>()

    fun build(): MethodNode {
        initializeFrame(method)
        buildInstructionList()
        val mn = MethodNode()
        mn.name = method.name
        mn.desc = method.description
        mn.access = method.access
        mn.parameters = method.parameters.map {
            ParameterNode(
                if (it.name == it.type.typeName) null else it.name,
                if (it.access == Opcodes.ACC_PUBLIC) 0 else it.access
            )
        }
        mn.exceptions = method.exceptions.map { it.simpleName.jvmName() }
        mn.instructions = currentInsnList
        mn.tryCatchBlocks = tryCatchNodeList
        mn.maxLocals = localIndex
        mn.maxStack = maxStack + 1
        return mn
    }

    private fun initializeFrame(method: JIRMethod) {
        if (!method.isStatic) {
            val thisRef = JIRRawThis(method.enclosingClass.name.typeName())
            locals[thisRef] = localIndex++
        }
        for (parameter in method.parameters) {
            val argument = JIRRawArgument(parameter.index, parameter.name, parameter.type)
            locals[argument] = localIndex
            if (argument.typeName.isDWord) localIndex += 2
            else localIndex++
        }
    }

    private fun buildInstructionList() {
        for (inst in instList.instructions) {
            inst.accept(this)
        }
    }

    private fun local(jIRRawValue: JIRRawValue): Int = locals.getOrPut(jIRRawValue) {
        val index = localIndex
        localIndex += when {
            jIRRawValue.typeName.isDWord -> 2
            else -> 1
        }
        index
    }

    private fun label(jIRRawLabelInst: JIRRawLabelInst): LabelNode = labels.getOrPut(jIRRawLabelInst) { LabelNode() }
    private fun label(jIRRawLabelRef: JIRRawLabelRef): LabelNode = label(labelRefMap.getValue(jIRRawLabelRef))

    private fun updateStackInfo(inc: Int) {
        stackSize += inc
        assert(stackSize >= 0)
        if (stackSize > maxStack) maxStack = stackSize
    }

    private fun loadValue(jIRRawValue: JIRRawValue): AbstractInsnNode {
        val local = local(jIRRawValue)
        val opcode = Opcodes.ILOAD + jIRRawValue.typeName.shortInt
        updateStackInfo(1)
        return VarInsnNode(opcode, local)
    }

    private fun storeValue(jIRRawValue: JIRRawValue): AbstractInsnNode {
        val local = local(jIRRawValue)
        val opcode = Opcodes.ISTORE + jIRRawValue.typeName.shortInt
        updateStackInfo(-1)
        return VarInsnNode(opcode, local)
    }

    private fun complexStore(lhv: JIRRawComplexValue, value: JIRRawExpr) = when (lhv) {
        is JIRRawFieldRef -> {
            lhv.instance?.accept(this)
            value.accept(this)
            val opcode = if (lhv.instance == null) Opcodes.PUTSTATIC else Opcodes.PUTFIELD
            currentInsnList.add(
                FieldInsnNode(
                    opcode,
                    lhv.declaringClass.jvmClassName,
                    lhv.fieldName,
                    lhv.typeName.jvmTypeName
                )
            )
            val stackChange = 1 + if (lhv.instance == null) 0 else 1
            updateStackInfo(-stackChange)
        }

        is JIRRawArrayAccess -> {
            lhv.array.accept(this)
            lhv.index.accept(this)
            value.accept(this)
            val opcode = Opcodes.IASTORE + lhv.typeName.longInt
            currentInsnList.add(InsnNode(opcode))
            updateStackInfo(-2)
        }

        else -> error("Unexpected complex value: ${lhv::class}")
    }

    override fun visitJIRRawAssignInst(inst: JIRRawAssignInst) {
        when (val lhv = inst.lhv) {
            is JIRRawComplexValue -> complexStore(lhv, inst.rhv)
            else -> {
                inst.rhv.accept(this)
                currentInsnList.add(storeValue(lhv))
            }
        }
    }

    override fun visitJIRRawEnterMonitorInst(inst: JIRRawEnterMonitorInst) {
        currentInsnList.add(loadValue(inst.monitor))
        currentInsnList.add(InsnNode(Opcodes.MONITORENTER))
        updateStackInfo(-1)
    }

    override fun visitJIRRawExitMonitorInst(inst: JIRRawExitMonitorInst) {
        currentInsnList.add(loadValue(inst.monitor))
        currentInsnList.add(InsnNode(Opcodes.MONITOREXIT))
        updateStackInfo(-1)
    }

    override fun visitJIRRawCallInst(inst: JIRRawCallInst) {
        inst.callExpr.accept(this)
    }

    override fun visitJIRRawLabelInst(inst: JIRRawLabelInst) {
        currentInsnList.add(label(inst))
    }

    override fun visitJIRRawLineNumberInst(inst: JIRRawLineNumberInst) {
        currentInsnList.add(LineNumberNode(inst.lineNumber, label(inst.start)))
    }

    override fun visitJIRRawReturnInst(inst: JIRRawReturnInst) {
        inst.returnValue?.accept(this)
        val opcode = when (inst.returnValue) {
            null -> Opcodes.RETURN
            else -> Opcodes.IRETURN + inst.returnValue!!.typeName.shortInt
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-stackSize)
    }

    override fun visitJIRRawThrowInst(inst: JIRRawThrowInst) {
        currentInsnList.add(loadValue(inst.throwable))
        currentInsnList.add(InsnNode(Opcodes.ATHROW))
        updateStackInfo(-stackSize)
    }

    override fun visitJIRRawCatchInst(inst: JIRRawCatchInst) {
        tryCatchNodeList += TryCatchBlockNode(
            label(inst.startInclusive),
            label(inst.endExclusive),
            label(inst.handler),
            inst.throwable.typeName.internalDesc
        )
        updateStackInfo(1)
        currentInsnList.add(storeValue(inst.throwable))
    }

    override fun visitJIRRawGotoInst(inst: JIRRawGotoInst) {
        currentInsnList.add(JumpInsnNode(Opcodes.GOTO, label(inst.target)))
        updateStackInfo(-stackSize)
    }

    override fun visitJIRRawIfInst(inst: JIRRawIfInst) {
        val trueTarget = label(inst.trueBranch)
        val falseTarget = label(inst.falseBranch)
        val cond = inst.condition
        val (zeroValue, zeroCmpOpcode, defaultOpcode) = when (cond) {
            is JIRRawEqExpr -> when {
                cond.lhv.typeName.isPrimitive -> Triple(JIRRawInt(0), Opcodes.IFEQ, Opcodes.IF_ICMPEQ)
                else -> Triple(JIRRawNull(), Opcodes.IFNULL, Opcodes.IF_ACMPEQ)
            }

            is JIRRawNeqExpr -> when {
                cond.lhv.typeName.isPrimitive -> Triple(JIRRawInt(0), Opcodes.IFNE, Opcodes.IF_ICMPNE)
                else -> Triple(JIRRawNull(), Opcodes.IFNONNULL, Opcodes.IF_ACMPNE)
            }

            is JIRRawGeExpr -> Triple(JIRRawInt(0), Opcodes.IFGE, Opcodes.IF_ICMPGE)
            is JIRRawGtExpr -> Triple(JIRRawInt(0), Opcodes.IFGT, Opcodes.IF_ICMPGT)
            is JIRRawLeExpr -> Triple(JIRRawInt(0), Opcodes.IFLE, Opcodes.IF_ICMPLE)
            is JIRRawLtExpr -> Triple(JIRRawInt(0), Opcodes.IFLT, Opcodes.IF_ICMPLT)
            else -> error("Unknown condition expr: $cond")
        }
        currentInsnList.add(
            when {
                cond.lhv == zeroValue -> {
                    cond.rhv.accept(this)
                    JumpInsnNode(zeroCmpOpcode, trueTarget)
                }

                cond.rhv == zeroValue -> {
                    cond.lhv.accept(this)
                    JumpInsnNode(zeroCmpOpcode, trueTarget)
                }

                else -> {
                    cond.lhv.accept(this)
                    cond.rhv.accept(this)
                    JumpInsnNode(defaultOpcode, trueTarget)
                }
            }
        )
        currentInsnList.add(JumpInsnNode(Opcodes.GOTO, falseTarget))
        updateStackInfo(-stackSize)
    }

    override fun visitJIRRawSwitchInst(inst: JIRRawSwitchInst) {
        currentInsnList.add(loadValue(inst.key))

        val branches = inst.branches
        val keys = inst.branches.keys.map { (it as JIRRawInt).value }.sorted().toIntArray()
        val default = label(inst.default)
        val labels = keys.map { label(branches[JIRRawInt(it)]!!) }.toTypedArray()

        val isConsecutive = keys.withIndex().all { (index, value) ->
            if (index > 0) value == keys[index - 1] + 1
            else true
        } && keys.size > 1

        currentInsnList.add(
            when {
                isConsecutive -> TableSwitchInsnNode(keys.first(), keys.last(), default, *labels)
                else -> LookupSwitchInsnNode(default, keys, labels)
            }
        )
        updateStackInfo(-stackSize)
    }

    override fun visitJIRRawAddExpr(expr: JIRRawAddExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IADD + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawAndExpr(expr: JIRRawAndExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IAND + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawCmpExpr(expr: JIRRawCmpExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        currentInsnList.add(InsnNode(Opcodes.LCMP))
        updateStackInfo(-1)
    }

    override fun visitJIRRawCmpgExpr(expr: JIRRawCmpgExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = when (expr.lhv.typeName.typeName) {
            PredefinedPrimitives.Float -> Opcodes.FCMPG
            else -> Opcodes.DCMPG
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawCmplExpr(expr: JIRRawCmplExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = when (expr.lhv.typeName.typeName) {
            PredefinedPrimitives.Float -> Opcodes.FCMPL
            else -> Opcodes.DCMPL
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawDivExpr(expr: JIRRawDivExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IDIV + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawMulExpr(expr: JIRRawMulExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IMUL + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawEqExpr(expr: JIRRawEqExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJIRRawNeqExpr(expr: JIRRawNeqExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJIRRawGeExpr(expr: JIRRawGeExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJIRRawGtExpr(expr: JIRRawGtExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJIRRawLeExpr(expr: JIRRawLeExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJIRRawLtExpr(expr: JIRRawLtExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJIRRawOrExpr(expr: JIRRawOrExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IOR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawRemExpr(expr: JIRRawRemExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IREM + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawShlExpr(expr: JIRRawShlExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.ISHL + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawShrExpr(expr: JIRRawShrExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.ISHR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawSubExpr(expr: JIRRawSubExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.ISUB + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawUshrExpr(expr: JIRRawUshrExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IUSHR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawXorExpr(expr: JIRRawXorExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IXOR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawLengthExpr(expr: JIRRawLengthExpr) {
        expr.array.accept(this)
        currentInsnList.add(InsnNode(Opcodes.ARRAYLENGTH))
    }

    override fun visitJIRRawNegExpr(expr: JIRRawNegExpr) {
        expr.operand.accept(this)
        currentInsnList.add(InsnNode(Opcodes.INEG + expr.typeName.shortInt))
    }

    override fun visitJIRRawCastExpr(expr: JIRRawCastExpr) {
        expr.operand.accept(this)

        val originalType = expr.operand.typeName
        val targetType = expr.typeName

        currentInsnList.add(
            when {
                originalType.isPrimitive && targetType.isPrimitive -> {
                    val opcode = when (originalType.typeName) {
                        PredefinedPrimitives.Long -> when (targetType.typeName) {
                            PredefinedPrimitives.Int -> Opcodes.L2I
                            PredefinedPrimitives.Float -> Opcodes.L2F
                            PredefinedPrimitives.Double -> Opcodes.L2D
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        in PredefinedPrimitives.smallIntegers -> when (targetType.typeName) {
                            PredefinedPrimitives.Long -> Opcodes.I2L
                            PredefinedPrimitives.Float -> Opcodes.I2F
                            PredefinedPrimitives.Double -> Opcodes.I2D
                            PredefinedPrimitives.Byte -> Opcodes.I2B
                            PredefinedPrimitives.Char -> Opcodes.I2C
                            PredefinedPrimitives.Short -> Opcodes.I2S
                            PredefinedPrimitives.Boolean -> Opcodes.NOP
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        PredefinedPrimitives.Float -> when (targetType.typeName) {
                            PredefinedPrimitives.Int -> Opcodes.F2I
                            PredefinedPrimitives.Long -> Opcodes.F2L
                            PredefinedPrimitives.Double -> Opcodes.F2D
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        PredefinedPrimitives.Double -> when (targetType.typeName) {
                            PredefinedPrimitives.Int -> Opcodes.D2I
                            PredefinedPrimitives.Long -> Opcodes.D2L
                            PredefinedPrimitives.Float -> Opcodes.D2F
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        else -> error("Impossible cast from $originalType to $targetType")
                    }
                    InsnNode(opcode)
                }

                else -> TypeInsnNode(Opcodes.CHECKCAST, targetType.internalDesc)
            }
        )
    }

    override fun visitJIRRawNewExpr(expr: JIRRawNewExpr) {
        currentInsnList.add(TypeInsnNode(Opcodes.NEW, expr.typeName.internalDesc))
        updateStackInfo(1)
    }

    override fun visitJIRRawNewArrayExpr(expr: JIRRawNewArrayExpr) {
        val component = expr.typeName.baseElementType()
        expr.dimensions.map { it.accept(this) }
        currentInsnList.add(
            when {
                expr.dimensions.size > 1 -> MultiANewArrayInsnNode(expr.typeName.jvmTypeName, expr.dimensions.size)
                component.isPrimitive -> IntInsnNode(Opcodes.NEWARRAY, component.typeInt)
                else -> TypeInsnNode(Opcodes.ANEWARRAY, component.internalDesc)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJIRRawInstanceOfExpr(expr: JIRRawInstanceOfExpr) {
        expr.operand.accept(this)
        currentInsnList.add(TypeInsnNode(Opcodes.INSTANCEOF, expr.typeName.internalDesc))
    }

    private val BsmHandle.asAsmHandle: Handle
        get() = Handle(
            tag,
            declaringClass.jvmClassName,
            name,
            "(${argTypes.joinToString("") { it.jvmTypeName }})${returnType.jvmTypeName}",
            isInterface
        )
    private val JIRRawMethodConstant.asAsmType: Type
        get() = Type.getType(
            argumentTypes.joinToString(
                prefix = "(",
                postfix = ")${returnType.jvmTypeName}",
                separator = ""
            ) { it.jvmTypeName }
        )

    private val TypeName.asAsmType: Type get() = Type.getType(this.jvmTypeName)

    override fun visitJIRRawDynamicCallExpr(expr: JIRRawDynamicCallExpr) {
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            InvokeDynamicInsnNode(
                expr.callCiteMethodName,
                "(${expr.callCiteArgTypes.joinToString("") { it.jvmTypeName }})${expr.callCiteReturnType.jvmTypeName}",
                expr.bsm.asAsmHandle,
                *expr.bsmArgs.map {
                    when (it) {
                        is BsmIntArg -> it.value
                        is BsmFloatArg -> it.value
                        is BsmLongArg -> it.value
                        is BsmDoubleArg -> it.value
                        is BsmStringArg -> it.value
                        is BsmMethodTypeArg -> Type.getMethodType(
                            it.returnType.asAsmType,
                            *it.argumentTypes.map { arg -> arg.asAsmType }.toTypedArray()
                        )

                        is BsmTypeArg -> it.typeName.asAsmType
                        is BsmHandle -> it.asAsmHandle
                        else -> error("Unknown arg of bsm: $it")
                    }
                }.toTypedArray()
            )
        )
        updateStackInfo(-expr.args.size + 1)
    }

    private val JIRRawCallExpr.methodDesc get() = "(${argumentTypes.joinToString("") { it.jvmTypeName }})${returnType.jvmTypeName}"

    override fun visitJIRRawVirtualCallExpr(expr: JIRRawVirtualCallExpr) {
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                expr.declaringClass.jvmClassName,
                expr.methodName,
                expr.methodDesc
            )
        )
        updateStackInfo(-(expr.args.size + 1))
        if (expr.returnType != PredefinedPrimitives.Void.typeName())
            updateStackInfo(1)
    }

    override fun visitJIRRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr) {
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                expr.declaringClass.jvmClassName,
                expr.methodName,
                expr.methodDesc
            )
        )
        updateStackInfo(-(expr.args.size + 1))
        if (expr.returnType != PredefinedPrimitives.Void.typeName())
            updateStackInfo(1)
    }

    override fun visitJIRRawStaticCallExpr(expr: JIRRawStaticCallExpr) {
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            MethodInsnNode(
                Opcodes.INVOKESTATIC,
                expr.declaringClass.jvmClassName,
                expr.methodName,
                expr.methodDesc
            )
        )
        updateStackInfo(-expr.args.size)
        if (expr.returnType != PredefinedPrimitives.Void.typeName())
            updateStackInfo(1)
    }

    override fun visitJIRRawSpecialCallExpr(expr: JIRRawSpecialCallExpr) {
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                expr.declaringClass.jvmClassName,
                expr.methodName,
                expr.methodDesc
            )
        )
        updateStackInfo(-(expr.args.size + 1))
        if (expr.returnType != PredefinedPrimitives.Void.typeName())
            updateStackInfo(1)
    }

    override fun visitJIRRawThis(value: JIRRawThis) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJIRRawArgument(value: JIRRawArgument) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJIRRawLocal(value: JIRRawLocal) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJIRRawFieldRef(value: JIRRawFieldRef) {
        value.instance?.accept(this)
        val opcode = if (value.instance == null) Opcodes.GFrontendTATIC else Opcodes.GETFIELD
        currentInsnList.add(
            FieldInsnNode(
                opcode,
                value.declaringClass.jvmClassName,
                value.fieldName,
                value.typeName.jvmTypeName
            )
        )
        val stackChange = if (value.instance == null) 1 else 0
        updateStackInfo(stackChange)
    }

    override fun visitJIRRawArrayAccess(value: JIRRawArrayAccess) {
        value.array.accept(this)
        value.index.accept(this)
        val opcode = Opcodes.IALOAD + value.typeName.longInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJIRRawBool(value: JIRRawBool) {
        currentInsnList.add(InsnNode(if (value.value) Opcodes.ICONST_1 else Opcodes.ICONST_0))
        updateStackInfo(1)
    }

    override fun visitJIRRawByte(value: JIRRawByte) {
        currentInsnList.add(IntInsnNode(Opcodes.BIPUSH, value.value.toInt()))
        updateStackInfo(1)
    }

    override fun visitJIRRawChar(value: JIRRawChar) {
        currentInsnList.add(LdcInsnNode(value.value.code))
        updateStackInfo(1)
    }

    override fun visitJIRRawShort(value: JIRRawShort) {
        currentInsnList.add(IntInsnNode(Opcodes.SIPUSH, value.value.toInt()))
        updateStackInfo(1)
    }

    override fun visitJIRRawInt(value: JIRRawInt) {
        currentInsnList.add(
            when (value.value) {
                in -1..5 -> InsnNode(Opcodes.ICONST_0 + value.value)
                in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, value.value)
                in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, value.value)
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJIRRawLong(value: JIRRawLong) {
        currentInsnList.add(
            when (value.value) {
                in 0..1 -> InsnNode(Opcodes.LCONST_0 + value.value.toInt())
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJIRRawFloat(value: JIRRawFloat) {
        currentInsnList.add(
            when (value.value) {
                0.0F -> InsnNode(Opcodes.FCONST_0)
                1.0F -> InsnNode(Opcodes.FCONST_1)
                2.0F -> InsnNode(Opcodes.FCONST_2)
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJIRRawDouble(value: JIRRawDouble) {
        currentInsnList.add(
            when (value.value) {
                0.0 -> InsnNode(Opcodes.DCONST_0)
                1.0 -> InsnNode(Opcodes.DCONST_1)
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJIRRawNullConstant(value: JIRRawNullConstant) {
        currentInsnList.add(InsnNode(Opcodes.ACONST_NULL))
        updateStackInfo(1)
    }

    override fun visitJIRRawStringConstant(value: JIRRawStringConstant) {
        currentInsnList.add(LdcInsnNode(value.value))
        updateStackInfo(1)
    }

    override fun visitJIRRawClassConstant(value: JIRRawClassConstant) {
        currentInsnList.add(LdcInsnNode(value.className.jvmClassName))
        updateStackInfo(1)
    }

    override fun visitJIRRawMethodConstant(value: JIRRawMethodConstant) {
        error("Could not load method constant $value")
    }
}
