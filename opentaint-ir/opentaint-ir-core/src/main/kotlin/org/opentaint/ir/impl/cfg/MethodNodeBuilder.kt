
package org.opentaint.ir.impl.cfg

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.ParameterNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.opentaint.ir.api.BsmDoubleArg
import org.opentaint.ir.api.BsmFloatArg
import org.opentaint.ir.api.BsmHandle
import org.opentaint.ir.api.BsmIntArg
import org.opentaint.ir.api.BsmLongArg
import org.opentaint.ir.api.BsmMethodTypeArg
import org.opentaint.ir.api.BsmStringArg
import org.opentaint.ir.api.BsmTypeArg
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRRawAddExpr
import org.opentaint.ir.api.JIRRawAndExpr
import org.opentaint.ir.api.JIRRawArgument
import org.opentaint.ir.api.JIRRawArrayAccess
import org.opentaint.ir.api.JIRRawAssignInst
import org.opentaint.ir.api.JIRRawBool
import org.opentaint.ir.api.JIRRawByte
import org.opentaint.ir.api.JIRRawCallExpr
import org.opentaint.ir.api.JIRRawCallInst
import org.opentaint.ir.api.JIRRawCastExpr
import org.opentaint.ir.api.JIRRawCatchInst
import org.opentaint.ir.api.JIRRawChar
import org.opentaint.ir.api.JIRRawClassConstant
import org.opentaint.ir.api.JIRRawCmpExpr
import org.opentaint.ir.api.JIRRawCmpgExpr
import org.opentaint.ir.api.JIRRawCmplExpr
import org.opentaint.ir.api.JIRRawComplexValue
import org.opentaint.ir.api.JIRRawDivExpr
import org.opentaint.ir.api.JIRRawDouble
import org.opentaint.ir.api.JIRRawDynamicCallExpr
import org.opentaint.ir.api.JIRRawEnterMonitorInst
import org.opentaint.ir.api.JIRRawEqExpr
import org.opentaint.ir.api.JIRRawExitMonitorInst
import org.opentaint.ir.api.JIRRawExpr
import org.opentaint.ir.api.JIRRawFieldRef
import org.opentaint.ir.api.JIRRawFloat
import org.opentaint.ir.api.JIRRawGeExpr
import org.opentaint.ir.api.JIRRawGotoInst
import org.opentaint.ir.api.JIRRawGtExpr
import org.opentaint.ir.api.JIRRawIfInst
import org.opentaint.ir.api.JIRRawInstList
import org.opentaint.ir.api.JIRRawInstanceOfExpr
import org.opentaint.ir.api.JIRRawInt
import org.opentaint.ir.api.JIRRawInterfaceCallExpr
import org.opentaint.ir.api.JIRRawLabelInst
import org.opentaint.ir.api.JIRRawLabelRef
import org.opentaint.ir.api.JIRRawLeExpr
import org.opentaint.ir.api.JIRRawLengthExpr
import org.opentaint.ir.api.JIRRawLocal
import org.opentaint.ir.api.JIRRawLong
import org.opentaint.ir.api.JIRRawLtExpr
import org.opentaint.ir.api.JIRRawMethodConstant
import org.opentaint.ir.api.JIRRawMulExpr
import org.opentaint.ir.api.JIRRawNegExpr
import org.opentaint.ir.api.JIRRawNeqExpr
import org.opentaint.ir.api.JIRRawNewArrayExpr
import org.opentaint.ir.api.JIRRawNewExpr
import org.opentaint.ir.api.JIRRawNullConstant
import org.opentaint.ir.api.JIRRawOrExpr
import org.opentaint.ir.api.JIRRawRemExpr
import org.opentaint.ir.api.JIRRawReturnInst
import org.opentaint.ir.api.JIRRawShlExpr
import org.opentaint.ir.api.JIRRawShort
import org.opentaint.ir.api.JIRRawShrExpr
import org.opentaint.ir.api.JIRRawSpecialCallExpr
import org.opentaint.ir.api.JIRRawStaticCallExpr
import org.opentaint.ir.api.JIRRawStringConstant
import org.opentaint.ir.api.JIRRawSubExpr
import org.opentaint.ir.api.JIRRawSwitchInst
import org.opentaint.ir.api.JIRRawThis
import org.opentaint.ir.api.JIRRawThrowInst
import org.opentaint.ir.api.JIRRawUshrExpr
import org.opentaint.ir.api.JIRRawValue
import org.opentaint.ir.api.JIRRawVirtualCallExpr
import org.opentaint.ir.api.JIRRawXorExpr
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.cfg.JIRRawExprVisitor
import org.opentaint.ir.api.cfg.JIRRawInstVisitor
import org.opentaint.ir.api.isStatic
import org.opentaint.ir.api.jvmName
import org.opentaint.ir.impl.cfg.util.baseElementType
import org.opentaint.ir.impl.cfg.util.internalDesc
import org.opentaint.ir.impl.cfg.util.isDWord
import org.opentaint.ir.impl.cfg.util.isPrimitive
import org.opentaint.ir.impl.cfg.util.jvmClassName
import org.opentaint.ir.impl.cfg.util.jvmTypeName
import org.opentaint.ir.impl.cfg.util.typeName

private val PredefinedPrimitives.smallIntegers get() = setOf(boolean, byte, char, short, int)

private val TypeName.shortInt
    get() = when (this.typeName) {
        PredefinedPrimitives.long -> 1
        in PredefinedPrimitives.smallIntegers -> 0
        PredefinedPrimitives.float -> 2
        PredefinedPrimitives.double -> 3
        else -> 4
    }
private val TypeName.longInt
    get() = when (this.typeName) {
        PredefinedPrimitives.boolean -> 5
        PredefinedPrimitives.byte -> 5
        PredefinedPrimitives.short -> 7
        PredefinedPrimitives.char -> 6
        PredefinedPrimitives.int -> 0
        PredefinedPrimitives.long -> 1
        PredefinedPrimitives.float -> 2
        PredefinedPrimitives.double -> 3
        else -> 4
    }

private val TypeName.typeInt
    get() = when (typeName) {
        PredefinedPrimitives.char -> Opcodes.T_CHAR
        PredefinedPrimitives.boolean -> Opcodes.T_BOOLEAN
        PredefinedPrimitives.byte -> Opcodes.T_BYTE
        PredefinedPrimitives.double -> Opcodes.T_DOUBLE
        PredefinedPrimitives.float -> Opcodes.T_FLOAT
        PredefinedPrimitives.int -> Opcodes.T_INT
        PredefinedPrimitives.long -> Opcodes.T_LONG
        PredefinedPrimitives.short -> Opcodes.T_SHORT
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
            val argument = JIRRawArgument(parameter.index, null, parameter.type)
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

    private fun local(jirRawValue: JIRRawValue): Int = locals.getOrPut(jirRawValue) {
        val index = localIndex
        localIndex += when {
            jirRawValue.typeName.isDWord -> 2
            else -> 1
        }
        index
    }

    private fun label(jirRawLabelInst: JIRRawLabelInst): LabelNode = labels.getOrPut(jirRawLabelInst) { LabelNode() }
    private fun label(jirRawLabelRef: JIRRawLabelRef): LabelNode = label(labelRefMap.getValue(jirRawLabelRef))

    private fun updateStackInfo(inc: Int) {
        stackSize += inc
        assert(stackSize >= 0)
        if (stackSize > maxStack) maxStack = stackSize
    }

    private fun loadValue(jirRawValue: JIRRawValue): AbstractInsnNode {
        val local = local(jirRawValue)
        val opcode = Opcodes.ILOAD + jirRawValue.typeName.shortInt
        updateStackInfo(1)
        return VarInsnNode(opcode, local)
    }

    private fun storeValue(jirRawValue: JIRRawValue): AbstractInsnNode {
        val local = local(jirRawValue)
        val opcode = Opcodes.ISTORE + jirRawValue.typeName.shortInt
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

    override fun visitJcRawAssignInst(inst: JIRRawAssignInst) {
        when (val lhv = inst.lhv) {
            is JIRRawComplexValue -> complexStore(lhv, inst.rhv)
            else -> {
                inst.rhv.accept(this)
                currentInsnList.add(storeValue(lhv))
            }
        }
    }

    override fun visitJcRawEnterMonitorInst(inst: JIRRawEnterMonitorInst) {
        currentInsnList.add(loadValue(inst.monitor))
        currentInsnList.add(InsnNode(Opcodes.MONITORENTER))
        updateStackInfo(-1)
    }

    override fun visitJcRawExitMonitorInst(inst: JIRRawExitMonitorInst) {
        currentInsnList.add(loadValue(inst.monitor))
        currentInsnList.add(InsnNode(Opcodes.MONITOREXIT))
        updateStackInfo(-1)
    }

    override fun visitJcRawCallInst(inst: JIRRawCallInst) {
        inst.callExpr.accept(this)
    }

    override fun visitJcRawLabelInst(inst: JIRRawLabelInst) {
        currentInsnList.add(label(inst))
    }

    override fun visitJcRawReturnInst(inst: JIRRawReturnInst) {
        inst.returnValue?.accept(this)
        val opcode = when (inst.returnValue) {
            null -> Opcodes.RETURN
            else -> Opcodes.IRETURN + inst.returnValue!!.typeName.shortInt
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-stackSize)
    }

    override fun visitJcRawThrowInst(inst: JIRRawThrowInst) {
        currentInsnList.add(loadValue(inst.throwable))
        currentInsnList.add(InsnNode(Opcodes.ATHROW))
        updateStackInfo(-stackSize)
    }

    override fun visitJcRawCatchInst(inst: JIRRawCatchInst) {
        tryCatchNodeList += TryCatchBlockNode(
            label(inst.startInclusive),
            label(inst.endExclusive),
            label(inst.handler),
            inst.throwable.typeName.internalDesc
        )
        updateStackInfo(1)
        currentInsnList.add(storeValue(inst.throwable))
    }

    override fun visitJcRawGotoInst(inst: JIRRawGotoInst) {
        currentInsnList.add(JumpInsnNode(Opcodes.GOTO, label(inst.target)))
        updateStackInfo(-stackSize)
    }

    override fun visitJcRawIfInst(inst: JIRRawIfInst) {
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

    override fun visitJcRawSwitchInst(inst: JIRRawSwitchInst) {
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

    override fun visitJcRawAddExpr(expr: JIRRawAddExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IADD + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawAndExpr(expr: JIRRawAndExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IAND + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawCmpExpr(expr: JIRRawCmpExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        currentInsnList.add(InsnNode(Opcodes.LCMP))
        updateStackInfo(-1)
    }

    override fun visitJcRawCmpgExpr(expr: JIRRawCmpgExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = when (expr.lhv.typeName.typeName) {
            PredefinedPrimitives.float -> Opcodes.FCMPG
            else -> Opcodes.DCMPG
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawCmplExpr(expr: JIRRawCmplExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = when (expr.lhv.typeName.typeName) {
            PredefinedPrimitives.float -> Opcodes.FCMPL
            else -> Opcodes.DCMPL
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawDivExpr(expr: JIRRawDivExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IDIV + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawMulExpr(expr: JIRRawMulExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IMUL + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawEqExpr(expr: JIRRawEqExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawNeqExpr(expr: JIRRawNeqExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawGeExpr(expr: JIRRawGeExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawGtExpr(expr: JIRRawGtExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawLeExpr(expr: JIRRawLeExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawLtExpr(expr: JIRRawLtExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawOrExpr(expr: JIRRawOrExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IOR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawRemExpr(expr: JIRRawRemExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IREM + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawShlExpr(expr: JIRRawShlExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.ISHL + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawShrExpr(expr: JIRRawShrExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.ISHR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawSubExpr(expr: JIRRawSubExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.ISUB + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawUshrExpr(expr: JIRRawUshrExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IUSHR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawXorExpr(expr: JIRRawXorExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IXOR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawLengthExpr(expr: JIRRawLengthExpr) {
        expr.array.accept(this)
        currentInsnList.add(InsnNode(Opcodes.ARRAYLENGTH))
    }

    override fun visitJcRawNegExpr(expr: JIRRawNegExpr) {
        expr.operand.accept(this)
        currentInsnList.add(InsnNode(Opcodes.INEG + expr.typeName.shortInt))
    }

    override fun visitJcRawCastExpr(expr: JIRRawCastExpr) {
        expr.operand.accept(this)

        val originalType = expr.operand.typeName
        val targetType = expr.typeName

        currentInsnList.add(
            when {
                originalType.isPrimitive && targetType.isPrimitive -> {
                    val opcode = when (originalType.typeName) {
                        PredefinedPrimitives.long -> when (targetType.typeName) {
                            PredefinedPrimitives.int -> Opcodes.L2I
                            PredefinedPrimitives.float -> Opcodes.L2F
                            PredefinedPrimitives.double -> Opcodes.L2D
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        in PredefinedPrimitives.smallIntegers -> when (targetType.typeName) {
                            PredefinedPrimitives.long -> Opcodes.I2L
                            PredefinedPrimitives.float -> Opcodes.I2F
                            PredefinedPrimitives.double -> Opcodes.I2D
                            PredefinedPrimitives.byte -> Opcodes.I2B
                            PredefinedPrimitives.char -> Opcodes.I2C
                            PredefinedPrimitives.short -> Opcodes.I2S
                            PredefinedPrimitives.boolean -> Opcodes.NOP
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        PredefinedPrimitives.float -> when (targetType.typeName) {
                            PredefinedPrimitives.int -> Opcodes.F2I
                            PredefinedPrimitives.long -> Opcodes.F2L
                            PredefinedPrimitives.double -> Opcodes.F2D
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        PredefinedPrimitives.double -> when (targetType.typeName) {
                            PredefinedPrimitives.int -> Opcodes.D2I
                            PredefinedPrimitives.long -> Opcodes.D2L
                            PredefinedPrimitives.float -> Opcodes.D2F
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

    override fun visitJcRawNewExpr(expr: JIRRawNewExpr) {
        currentInsnList.add(TypeInsnNode(Opcodes.NEW, expr.typeName.internalDesc))
        updateStackInfo(1)
    }

    override fun visitJcRawNewArrayExpr(expr: JIRRawNewArrayExpr) {
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

    override fun visitJcRawInstanceOfExpr(expr: JIRRawInstanceOfExpr) {
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

    override fun visitJcRawDynamicCallExpr(expr: JIRRawDynamicCallExpr) {
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

    override fun visitJcRawVirtualCallExpr(expr: JIRRawVirtualCallExpr) {
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
        if (expr.returnType != PredefinedPrimitives.void.typeName())
            updateStackInfo(1)
    }

    override fun visitJcRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr) {
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
        if (expr.returnType != PredefinedPrimitives.void.typeName())
            updateStackInfo(1)
    }

    override fun visitJcRawStaticCallExpr(expr: JIRRawStaticCallExpr) {
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
        if (expr.returnType != PredefinedPrimitives.void.typeName())
            updateStackInfo(1)
    }

    override fun visitJcRawSpecialCallExpr(expr: JIRRawSpecialCallExpr) {
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
        if (expr.returnType != PredefinedPrimitives.void.typeName())
            updateStackInfo(1)
    }

    override fun visitJcRawThis(value: JIRRawThis) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJcRawArgument(value: JIRRawArgument) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJcRawLocal(value: JIRRawLocal) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJcRawFieldRef(value: JIRRawFieldRef) {
        value.instance?.accept(this)
        val opcode = if (value.instance == null) Opcodes.GETSTATIC else Opcodes.GETFIELD
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

    override fun visitJcRawArrayAccess(value: JIRRawArrayAccess) {
        value.array.accept(this)
        value.index.accept(this)
        val opcode = Opcodes.IALOAD + value.typeName.longInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawBool(value: JIRRawBool) {
        currentInsnList.add(InsnNode(if (value.value) Opcodes.ICONST_1 else Opcodes.ICONST_0))
        updateStackInfo(1)
    }

    override fun visitJcRawByte(value: JIRRawByte) {
        currentInsnList.add(IntInsnNode(Opcodes.BIPUSH, value.value.toInt()))
        updateStackInfo(1)
    }

    override fun visitJcRawChar(value: JIRRawChar) {
        currentInsnList.add(LdcInsnNode(value.value.code))
        updateStackInfo(1)
    }

    override fun visitJcRawShort(value: JIRRawShort) {
        currentInsnList.add(IntInsnNode(Opcodes.SIPUSH, value.value.toInt()))
        updateStackInfo(1)
    }

    override fun visitJcRawInt(value: JIRRawInt) {
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

    override fun visitJcRawLong(value: JIRRawLong) {
        currentInsnList.add(
            when (value.value) {
                in 0..1 -> InsnNode(Opcodes.LCONST_0 + value.value.toInt())
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJcRawFloat(value: JIRRawFloat) {
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

    override fun visitJcRawDouble(value: JIRRawDouble) {
        currentInsnList.add(
            when (value.value) {
                0.0 -> InsnNode(Opcodes.DCONST_0)
                1.0 -> InsnNode(Opcodes.DCONST_1)
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJcRawNullConstant(value: JIRRawNullConstant) {
        currentInsnList.add(InsnNode(Opcodes.ACONST_NULL))
        updateStackInfo(1)
    }

    override fun visitJcRawStringConstant(value: JIRRawStringConstant) {
        currentInsnList.add(LdcInsnNode(value.value))
        updateStackInfo(1)
    }

    override fun visitJcRawClassConstant(value: JIRRawClassConstant) {
        currentInsnList.add(LdcInsnNode(value.className.jvmClassName))
        updateStackInfo(1)
    }

    override fun visitJcRawMethodConstant(value: JIRRawMethodConstant) {
        error("Could not load method constant $value")
    }
}
