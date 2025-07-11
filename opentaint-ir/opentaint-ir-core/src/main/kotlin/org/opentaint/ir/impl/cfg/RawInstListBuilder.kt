package org.opentaint.ir.impl.cfg

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.BsmArg
import org.opentaint.ir.api.jvm.cfg.BsmDoubleArg
import org.opentaint.ir.api.jvm.cfg.BsmFloatArg
import org.opentaint.ir.api.jvm.cfg.BsmHandle
import org.opentaint.ir.api.jvm.cfg.BsmIntArg
import org.opentaint.ir.api.jvm.cfg.BsmLongArg
import org.opentaint.ir.api.jvm.cfg.BsmMethodTypeArg
import org.opentaint.ir.api.jvm.cfg.BsmStringArg
import org.opentaint.ir.api.jvm.cfg.BsmTypeArg
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRRawAddExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawAndExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawArgument
import org.opentaint.ir.api.jvm.cfg.JIRRawArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRRawAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRRawCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCallInst
import org.opentaint.ir.api.jvm.cfg.JIRRawCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCatchEntry
import org.opentaint.ir.api.jvm.cfg.JIRRawCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRRawClassConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawCmpExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCmpgExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCmplExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawDivExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawDynamicCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawEnterMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRRawEqExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawExitMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRRawFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRRawGeExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRRawGtExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawIfInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInstanceOfExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawInterfaceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawLabelInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLabelRef
import org.opentaint.ir.api.jvm.cfg.JIRRawLeExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawLengthExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawLineNumberInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLocalVar
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
import org.opentaint.ir.api.jvm.cfg.JIRRawShrExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawSimpleValue
import org.opentaint.ir.api.jvm.cfg.JIRRawSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawSubExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawSwitchInst
import org.opentaint.ir.api.jvm.cfg.JIRRawThis
import org.opentaint.ir.api.jvm.cfg.JIRRawThrowInst
import org.opentaint.ir.api.jvm.cfg.JIRRawUshrExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawValue
import org.opentaint.ir.api.jvm.cfg.JIRRawVirtualCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawXorExpr
import org.opentaint.ir.impl.cfg.util.CLASS_CLASS
import org.opentaint.ir.impl.cfg.util.ExprMapper
import org.opentaint.ir.impl.cfg.util.METHOD_HANDLES_CLASS
import org.opentaint.ir.impl.cfg.util.METHOD_HANDLES_LOOKUP_CLASS
import org.opentaint.ir.impl.cfg.util.METHOD_HANDLE_CLASS
import org.opentaint.ir.impl.cfg.util.METHOD_TYPE_CLASS
import org.opentaint.ir.impl.cfg.util.NULL
import org.opentaint.ir.impl.cfg.util.OBJECT_CLASS
import org.opentaint.ir.impl.cfg.util.STRING_CLASS
import org.opentaint.ir.impl.cfg.util.THROWABLE_CLASS
import org.opentaint.ir.impl.cfg.util.TOP
import org.opentaint.ir.impl.cfg.util.UNINIT_THIS
import org.opentaint.ir.impl.cfg.util.asArray
import org.opentaint.ir.impl.cfg.util.elementType
import org.opentaint.ir.impl.cfg.util.isArray
import org.opentaint.ir.impl.cfg.util.isDWord
import org.opentaint.ir.impl.cfg.util.isPrimitive
import org.opentaint.ir.impl.cfg.util.typeName
import org.opentaint.ir.impl.types.TypeNameImpl
import org.objectweb.asm.*
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.*

const val LOCAL_VAR_START_CHARACTER = '%'

private fun Int.toPrimitiveType(): TypeName = when (this) {
    Opcodes.T_CHAR -> PredefinedPrimitives.Char
    Opcodes.T_BOOLEAN -> PredefinedPrimitives.Boolean
    Opcodes.T_BYTE -> PredefinedPrimitives.Byte
    Opcodes.T_DOUBLE -> PredefinedPrimitives.Double
    Opcodes.T_FLOAT -> PredefinedPrimitives.Float
    Opcodes.T_INT -> PredefinedPrimitives.Int
    Opcodes.T_LONG -> PredefinedPrimitives.Long
    Opcodes.T_SHORT -> PredefinedPrimitives.Short
    else -> error("Unknown primitive type opcode: $this")
}.typeName()

private fun parsePrimitiveType(opcode: Int) = when (opcode) {
    0 -> TOP
    1 -> PredefinedPrimitives.Int.typeName()
    2 -> PredefinedPrimitives.Float.typeName()
    3 -> PredefinedPrimitives.Double.typeName()
    4 -> PredefinedPrimitives.Long.typeName()
    5 -> NULL
    6 -> UNINIT_THIS
    else -> error("Unknown opcode in primitive type parsing: $opcode")
}

private fun parseType(any: Any): TypeName = when (any) {
    is String -> any.typeName()
    is Int -> parsePrimitiveType(any)
    is LabelNode -> {
        val newNode: TypeInsnNode = any.run {
            var cur: AbstractInsnNode = this
            var typeInsnNode: TypeInsnNode?
            do {
                typeInsnNode = cur.next as? TypeInsnNode
                cur = cur.next
            } while (typeInsnNode == null)
            typeInsnNode
        }
        newNode.desc.typeName()
    }

    else -> error("Unexpected local type $any")
}

private fun List<*>?.parseLocals(): Array<TypeName?> {
    if (this == null || isEmpty()) return emptyArray()

    var result = arrayOfNulls<TypeName>(16)
    var realSize = 0

    var index = 0
    for (any in this) {
        val type = parseType(any!!)

        result = result.add(index, type)
        realSize = index + 1

        when {
            type.isDWord -> index += 2
            else -> ++index
        }
    }

    return result.copyOf(realSize)
}

private fun List<*>?.parseStack(): List<TypeName> =
    this?.map { parseType(it!!) } ?: emptyList()

private val primitiveWeights = mapOf(
    PredefinedPrimitives.Boolean to 0,
    PredefinedPrimitives.Byte to 1,
    PredefinedPrimitives.Char to 1,
    PredefinedPrimitives.Short to 2,
    PredefinedPrimitives.Int to 3,
    PredefinedPrimitives.Long to 4,
    PredefinedPrimitives.Float to 5,
    PredefinedPrimitives.Double to 6
)

private fun maxOfPrimitiveTypes(first: String, second: String): String {
    val weight1 = primitiveWeights[first] ?: 0
    val weight2 = primitiveWeights[second] ?: 0
    return when {
        weight1 >= weight2 -> first
        else -> second
    }
}

private fun String.lessThen(anotherPrimitive: String): Boolean {
    val weight1 = primitiveWeights[anotherPrimitive] ?: 0
    val weight2 = primitiveWeights[this] ?: 0
    return weight2 <= weight1
}

private val Type.asTypeName: BsmArg
    get() = when (this.sort) {
        Type.VOID -> BsmTypeArg(PredefinedPrimitives.Void.typeName())
        Type.BOOLEAN -> BsmTypeArg(PredefinedPrimitives.Boolean.typeName())
        Type.CHAR -> BsmTypeArg(PredefinedPrimitives.Char.typeName())
        Type.BYTE -> BsmTypeArg(PredefinedPrimitives.Byte.typeName())
        Type.SHORT -> BsmTypeArg(PredefinedPrimitives.Short.typeName())
        Type.INT -> BsmTypeArg(PredefinedPrimitives.Int.typeName())
        Type.FLOAT -> BsmTypeArg(PredefinedPrimitives.Float.typeName())
        Type.LONG -> BsmTypeArg(PredefinedPrimitives.Long.typeName())
        Type.DOUBLE -> BsmTypeArg(PredefinedPrimitives.Double.typeName())
        Type.ARRAY -> BsmTypeArg((elementType.asTypeName as BsmTypeArg).typeName.asArray())
        Type.OBJECT -> BsmTypeArg(className.typeName())
        Type.METHOD -> BsmMethodTypeArg(
            this.argumentTypes.map { (it.asTypeName as BsmTypeArg).typeName },
            (this.returnType.asTypeName as BsmTypeArg).typeName
        )

        else -> error("Unknown type: $this")
    }

private val AbstractInsnNode.isBranchingInst
    get() = when (this) {
        is JumpInsnNode -> true
        is TableSwitchInsnNode -> true
        is LookupSwitchInsnNode -> true
        is InsnNode -> opcode == Opcodes.ATHROW
        else -> false
    }

private val AbstractInsnNode.isTerminateInst
    get() = this is InsnNode && (this.opcode == Opcodes.ATHROW || this.opcode in Opcodes.IRETURN..Opcodes.RETURN)

private fun AbstractInsnNode.isBetween(labelStart: AbstractInsnNode, labelEnd: AbstractInsnNode): Boolean {
    var curNode: AbstractInsnNode? = this
    var left = false
    var right = false
    while (curNode != null) {
        if (curNode == labelStart) {
            left = true
            break
        }
        if (curNode == labelEnd && curNode != this) {
            return false
        }
        curNode = curNode.previous

    }
    if (!left) return false
    curNode = this
    while (curNode != null) {
        if (curNode == labelEnd) {
            right = true
            break
        }
        curNode = curNode.next
    }
    return right
}

private val TryCatchBlockNode.typeOrDefault get() = this.type ?: THROWABLE_CLASS

private val Collection<TryCatchBlockNode>.commonTypeOrDefault
    get() = map { it.type }
        .distinct()
        .singleOrNull()
        ?: THROWABLE_CLASS

internal fun <K, V> identityMap(): MutableMap<K, V> = IdentityHashMap()

internal fun <K, V> Map<out K, V>.toIdentityMap(): Map<K, V> = toMap()

class RawInstListBuilder(
    val method: JIRMethod,
    private val methodNode: MethodNode,
    private val keepLocalVariableNames: Boolean,
) {
    private val frames = identityMap<AbstractInsnNode, Frame>()
    private val labels = identityMap<LabelNode, JIRRawLabelInst>()
    private lateinit var lastFrameState: FrameState
    private lateinit var currentFrame: Frame
    private val ENTRY = InsnNode(-1)

    private val deadInstructions = hashSetOf<AbstractInsnNode>()
    private val predecessors = identityMap<AbstractInsnNode, MutableList<AbstractInsnNode>>()
    private val instructions = identityMap<AbstractInsnNode, MutableList<JIRRawInst>>()
    private val laterAssignments = identityMap<AbstractInsnNode, MutableMap<Int, JIRRawValue>>()
    private val laterStackAssignments = identityMap<AbstractInsnNode, MutableMap<Int, JIRRawValue>>()
    private val localTypeRefinement = identityMap<JIRRawLocalVar, JIRRawLocalVar>()
    private val blackListForTypeRefinement = listOf(TOP, NULL, UNINIT_THIS)
    private val additionalSections = hashMapOf<AbstractInsnNode, JIRRawInst>()

    private var labelCounter = 0
    private var localCounter = 0
    private var argCounter = 0

    fun build(): JIRInstList<JIRRawInst> {
        buildGraph()

        buildInstructions()
        buildRequiredAssignments()
        buildRequiredGotos()

        val originalInstructionList = JIRInstListImpl(methodNode.instructions.flatMap { instructionList(it) })

        // after all the frame info resolution we can refine type info for some local variables,
        // so we replace all the old versions of the variables with the type refined ones
        val localsNormalizedInstructionList =
            originalInstructionList.map(ExprMapper(localTypeRefinement.toIdentityMap()))
        return Simplifier().simplify(method.enclosingClass.classpath, localsNormalizedInstructionList)
    }

    private fun buildInstructions() {
        currentFrame = createInitialFrame()
        frames[ENTRY] = currentFrame
        val nodes = methodNode.instructions.toList()
        nodes.forEachIndexed { index, insn ->
            when (insn) {
                is InsnNode -> buildInsnNode(insn)
                is FieldInsnNode -> buildFieldInsnNode(insn)
                is FrameNode -> buildFrameNode(insn)
                is IincInsnNode -> buildIincInsnNode(insn)
                is IntInsnNode -> buildIntInsnNode(insn)
                is InvokeDynamicInsnNode -> buildInvokeDynamicInsn(insn)
                is JumpInsnNode -> buildJumpInsnNode(insn)
                is LabelNode -> buildLabelNode(insn)
                is LdcInsnNode -> buildLdcInsnNode(insn)
                is LineNumberNode -> buildLineNumberNode(insn)
                is LookupSwitchInsnNode -> buildLookupSwitchInsnNode(insn)
                is MethodInsnNode -> buildMethodInsnNode(insn)
                is MultiANewArrayInsnNode -> buildMultiANewArrayInsnNode(insn)
                is TableSwitchInsnNode -> buildTableSwitchInsnNode(insn)
                is TypeInsnNode -> buildTypeInsnNode(insn)
                is VarInsnNode -> buildVarInsnNode(insn)
                else -> error("Unknown insn node ${insn::class}")
            }
            val preds = predecessors[insn]
            if (index != 1 && (preds.isNullOrEmpty() || preds.all { deadInstructions.contains(it) })) {
                deadInstructions.add(insn)
            }
            frames[insn] = currentFrame
        }
    }

    // `laterAssignments` and `laterStackAssignments` are maps of variable assignments
    // that we need to add to the instruction list after the construction process to ensure
    // liveness of the variables on every step of the method. We cannot add them during the construction
    // because some of them are unknown at that stage (e.g. because of loops)
    private fun buildRequiredAssignments() {
        for ((insn, assignments) in laterAssignments) {
            val insnList = instructionList(insn)
            val frame = frames[insn]!!
            for ((variable, value) in assignments) {
                val frameVariable = frame.findLocal(variable)
                if (frameVariable != null && value != frameVariable) {
                    if (insn.isBranchingInst) {
                        val index = insnList.indexOf(additionalSections[insn]) // -1 will be converted to 0 next line
                        insnList.addInst(JIRRawAssignInst(method, value, frameVariable), index + 1)
                    } else if (insn.isTerminateInst) {
                        insnList.addInst(JIRRawAssignInst(method, value, frameVariable), insnList.lastIndex)
                    } else {
                        insnList.addInst(JIRRawAssignInst(method, value, frameVariable))
                    }
                }
            }
        }
        for ((insn, assignments) in laterStackAssignments) {
            val insnList = instructionList(insn)
            val frame = frames[insn]!!
            for ((variable, value) in assignments) {
                if (value != frame.stack[variable]) {
                    if (insn.isBranchingInst || insn.isTerminateInst) {
                        insnList.addInst(JIRRawAssignInst(method, value, frame.stack[variable]), insnList.lastIndex)
                    } else {
                        insnList.addInst(JIRRawAssignInst(method, value, frame.stack[variable]))
                    }
                }
            }
        }
    }

    // adds the `goto` instructions to ensure consistency in the instruction list:
    // every jump is show explicitly with some branching instruction
    private fun buildRequiredGotos() {
        for (insn in methodNode.instructions) {
            if (methodNode.tryCatchBlocks.any { it.handler == insn }) continue

            val predecessors = predecessors.getOrDefault(insn, emptyList())
            if (predecessors.size > 1) {
                for (predecessor in predecessors) {
                    if (!predecessor.isBranchingInst) {
                        val label = when (insn) {
                            is LabelNode -> labelRef(insn)
                            else -> {
                                val newLabel = nextLabel()
                                addInstruction(insn, newLabel, 0)
                                newLabel.ref
                            }
                        }
                        addInstruction(predecessor, JIRRawGotoInst(method, label))
                    }
                }
            }
        }
    }

    /**
     * represets a frame state: information about types of local variables and stack variables
     * needed to handle ASM FrameNode instructions
     */
    private data class FrameState(
        private val locals: Array<TypeName?>,
        val stack: List<TypeName>,
    ) {
        companion object {
            fun parseNew(insn: FrameNode): FrameState {
                return FrameState(insn.local.parseLocals(), insn.stack.parseStack())
            }
        }

        fun appendFrame(insn: FrameNode): FrameState {
            val lastType = locals.lastOrNull()
            val insertKey = when {
                lastType == null -> 0
                lastType.isDWord -> locals.lastIndex + 2
                else -> locals.lastIndex + 1
            }

            val appendedLocals = insn.local.parseLocals()

            val newLocals = locals.copyOf(insertKey + appendedLocals.size)
            for (index in appendedLocals.indices) {
                newLocals[insertKey + index] = appendedLocals[index]
            }

            return copy(locals = newLocals, stack = emptyList())
        }

        fun dropFrame(inst: FrameNode): FrameState {
            val newLocals = locals.copyOf(locals.size - inst.local.size).trimEndNulls()
            return copy(locals = newLocals, stack = emptyList())
        }

        fun copy0(): FrameState = this.copy(stack = emptyList())

        fun copy1(insn: FrameNode): FrameState {
            val newStack = insn.stack.parseStack()
            return this.copy(stack = newStack)
        }

        fun localsUnsafe(): Array<TypeName?> = locals
    }

    private fun FrameState.copyToFrame(
        predFrames: Map<AbstractInsnNode, Frame?>,
        curLabel: LabelNode,
        copyStack: Boolean,
    ): Frame {
        val locals = localsUnsafe().copyLocals(predFrames, curLabel)

        val stack = if (copyStack) {
            stack.copyStack(predFrames).toPersistentList()
        } else {
            persistentListOf()
        }

        return Frame(locals, stack)
    }

    /**
     * represents the bytecode Frame: a set of active local variables and stack variables
     * during the execution of the instruction
     */
    private data class Frame(
        private val locals: Array<JIRRawValue?>,
        val stack: PersistentList<JIRRawValue>,
    ) {
        fun putLocal(variable: Int, value: JIRRawValue): Frame {
            val newLocals = locals.copyOf(maxOf(locals.size, variable + 1))
            newLocals[variable] = value
            return copy(locals = newLocals, stack = stack)
        }

        fun hasLocal(variable: Int): Boolean = findLocal(variable) != null

        fun maxLocal(): Int = locals.lastIndex

        fun findLocal(variable: Int): JIRRawValue? = locals.getOrNull(variable)

        fun getLocal(variable: Int): JIRRawValue = locals.getOrNull(variable)
            ?: error("No local variable $variable")

        fun push(value: JIRRawValue) = copy(locals = locals, stack = stack.add(value))
        fun peek() = stack.last()
        fun pop(): Pair<Frame, JIRRawValue> =
            copy(locals = locals, stack = stack.removeAt(stack.lastIndex)) to stack.last()
    }

    private fun pop(): JIRRawValue {
        val (frame, value) = currentFrame.pop()
        currentFrame = frame
        return value
    }

    private fun push(value: JIRRawValue) {
        currentFrame = currentFrame.push(value)
    }

    private fun peek(): JIRRawValue = currentFrame.peek()

    private fun local(variable: Int): JIRRawValue {
        return currentFrame.getLocal(variable)
    }

    private fun local(
        variable: Int,
        expr: JIRRawValue,
        insn: AbstractInsnNode,
        override: Boolean = false,
    ): JIRRawAssignInst {
        val oldVar = currentFrame.findLocal(variable)?.let {
            val infoFromLocalVars =
                methodNode.localVariables.find { it.index == variable && insn.isBetween(it.start, it.end) }
            val isArg =
                variable < argCounter && infoFromLocalVars != null && infoFromLocalVars.start == methodNode.instructions.firstOrNull { it is LabelNode }
            if (expr.typeName.isPrimitive.xor(it.typeName.isPrimitive)
                && it.typeName.typeName != PredefinedPrimitives.Null
                && !isArg
            ) {
                null
            } else {
                it
            }
        }
        return if (oldVar != null) {
            if (oldVar.typeName == expr.typeName || (expr is JIRRawNullConstant && !oldVar.typeName.isPrimitive)) {
                if (override) {
                    currentFrame = currentFrame.putLocal(variable, expr)
                    JIRRawAssignInst(method, expr, expr)
                } else {
                    JIRRawAssignInst(method, oldVar, expr)
                }
            } else if (oldVar is JIRRawArgument) {
                currentFrame = currentFrame.putLocal(variable, expr)
                JIRRawAssignInst(method, oldVar, expr)
            } else {
                val assignment = nextRegisterDeclaredVariable(expr.typeName, variable, insn)
                currentFrame = currentFrame.putLocal(variable, assignment)
                JIRRawAssignInst(method, assignment, expr)
            }
        } else {
            // We have to get type if rhv expression is NULL
            val typeOfNewAssigment =
                if (expr.typeName.typeName == PredefinedPrimitives.Null) {
                    methodNode.localVariables
                        .find { it.index == variable && insn.isBetween(it.start, it.end) }
                        ?.desc?.typeName()
                        ?: currentFrame.findLocal(variable)?.typeName
                        ?: "java.lang.Object".typeName()
                } else {
                    expr.typeName
                }
            val newLocal = nextRegisterDeclaredVariable(typeOfNewAssigment, variable, insn)
            val result = JIRRawAssignInst(method, newLocal, expr)
            currentFrame = currentFrame.putLocal(variable, newLocal)
            result
        }
    }

    private fun label(insnNode: LabelNode): JIRRawLabelInst = labels.getOrPut(insnNode, ::nextLabel)

    private fun labelRef(insnNode: LabelNode): JIRRawLabelRef = label(insnNode).ref

    private fun instructionList(insn: AbstractInsnNode) = instructions.getOrPut(insn, ::mutableListOf)

    private fun addInstruction(insn: AbstractInsnNode, inst: JIRRawInst, index: Int? = null) {
        instructionList(insn).addInst(inst, index)
    }

    private fun MutableList<JIRRawInst>.addInst(inst: JIRRawInst, index: Int? = null) {
        if (index != null) {
            add(index, inst)
        } else {
            add(inst)
        }
    }

    private fun nextRegister(typeName: TypeName): JIRRawValue {
        return JIRRawLocalVar(localCounter, "$LOCAL_VAR_START_CHARACTER${localCounter++}", typeName)
    }

    private fun nextRegisterDeclaredVariable(typeName: TypeName, variable: Int, insn: AbstractInsnNode): JIRRawValue {
        val nextLabel = generateSequence(insn) { it.next }
            .filterIsInstance<LabelNode>()
            .firstOrNull()

        val lvNode = methodNode.localVariables
            .singleOrNull { it.index == variable && it.start == nextLabel }

        val declaredTypeName = lvNode?.desc?.typeName()
        val idx = localCounter++
        val lvName = lvNode?.name?.takeIf { keepLocalVariableNames } ?: "$LOCAL_VAR_START_CHARACTER$idx"

        return if (declaredTypeName != null && !declaredTypeName.isPrimitive && !typeName.isArray) {
            JIRRawLocalVar(idx, lvName, declaredTypeName)
        } else {
            JIRRawLocalVar(idx, lvName, typeName)
        }
    }

    private fun nextLabel(): JIRRawLabelInst = JIRRawLabelInst(method, "#${labelCounter++}")

    private fun buildGraph() {
        methodNode.instructions.first?.let {
            predecessors.getOrPut(it, ::mutableListOf).add(ENTRY)
        }
        for (insn in methodNode.instructions) {
            if (insn is JumpInsnNode) {
                predecessors.getOrPut(insn.label, ::mutableListOf).add(insn)
                if (insn.opcode != Opcodes.GOTO) {
                    predecessors.getOrPut(insn.next, ::mutableListOf).add(insn)
                }
            } else if (insn is TableSwitchInsnNode) {
                predecessors.getOrPut(insn.dflt, ::mutableListOf).add(insn)
                insn.labels.forEach {
                    predecessors.getOrPut(it, ::mutableListOf).add(insn)
                }
            } else if (insn is LookupSwitchInsnNode) {
                predecessors.getOrPut(insn.dflt, ::mutableListOf).add(insn)
                insn.labels.forEach {
                    predecessors.getOrPut(it, ::mutableListOf).add(insn)
                }
            } else if (insn.isTerminateInst) {
                continue
            } else if (insn.next != null) {
                predecessors.getOrPut(insn.next, ::mutableListOf).add(insn)
            }
        }
        for (tryCatchBlock in methodNode.tryCatchBlocks) {
            val preStart = predecessors.getOrDefault(tryCatchBlock.start, setOf(ENTRY))
            predecessors.getOrPut(tryCatchBlock.handler, ::mutableListOf).addAll(preStart)

            var current: AbstractInsnNode = tryCatchBlock.start
            while (current != tryCatchBlock.end) {
                predecessors.getOrPut(tryCatchBlock.handler, ::mutableListOf).add(current)
                current = current.next ?: error("Unexpected instruction")
            }
        }
    }

    private fun createInitialFrame(): Frame {
        var locals = arrayOfNulls<JIRRawValue>(16)
        var localsRealSize = 0

        argCounter = 0
        var staticInc = 0
        if (!method.isStatic) {
            locals = locals.add(argCounter, thisRef())
            localsRealSize = argCounter + 1

            argCounter++
            staticInc = 1
        }
        val variables = methodNode.localVariables.orEmpty().sortedBy(LocalVariableNode::index)

        fun getName(parameter: JIRParameter): String? {
            val idx = parameter.index + staticInc
            return if (idx < variables.size) {
                variables[idx].name
            } else {
                parameter.name
            }
        }

        for (parameter in method.parameters) {
            val argument = JIRRawArgument.of(parameter.index, getName(parameter), parameter.type)

            locals = locals.add(argCounter, argument)
            localsRealSize = argCounter + 1

            if (argument.typeName.isDWord) argCounter += 2
            else argCounter++
        }

        return Frame(locals.copyOf(localsRealSize), persistentListOf())
    }

    private fun thisRef() = JIRRawThis(method.enclosingClass.name.typeName())

    private fun buildInsnNode(insn: InsnNode) {
        when (insn.opcode) {
            Opcodes.NOP -> Unit
            in Opcodes.ACONST_NULL..Opcodes.DCONST_1 -> buildConstant(insn)
            in Opcodes.IALOAD..Opcodes.SALOAD -> buildArrayRead(insn)
            in Opcodes.IASTORE..Opcodes.SASTORE -> buildArrayStore(insn)
            in Opcodes.POP..Opcodes.POP2 -> buildPop(insn)
            in Opcodes.DUP..Opcodes.DUP2_X2 -> buildDup(insn)
            Opcodes.SWAP -> buildSwap()
            in Opcodes.IADD..Opcodes.DREM -> buildBinary(insn)
            in Opcodes.INEG..Opcodes.DNEG -> buildUnary(insn)
            in Opcodes.ISHL..Opcodes.LXOR -> buildBinary(insn)
            in Opcodes.I2L..Opcodes.I2S -> buildCast(insn)
            in Opcodes.LCMP..Opcodes.DCMPG -> buildCmp(insn)
            in Opcodes.IRETURN..Opcodes.RETURN -> buildReturn(insn)
            Opcodes.ARRAYLENGTH -> buildUnary(insn)
            Opcodes.ATHROW -> buildThrow(insn)
            in Opcodes.MONITORENTER..Opcodes.MONITOREXIT -> buildMonitor(insn)
            else -> error("Unknown insn opcode: ${insn.opcode}")
        }
    }

    private fun buildConstant(insn: InsnNode) {
        val constant = when (val opcode = insn.opcode) {
            Opcodes.ACONST_NULL -> JIRRawNull()
            Opcodes.ICONST_M1 -> JIRRawInt(-1)
            in Opcodes.ICONST_0..Opcodes.ICONST_5 -> JIRRawInt(opcode - Opcodes.ICONST_0)
            in Opcodes.LCONST_0..Opcodes.LCONST_1 -> JIRRawLong((opcode - Opcodes.LCONST_0).toLong())
            in Opcodes.FCONST_0..Opcodes.FCONST_2 -> JIRRawFloat((opcode - Opcodes.FCONST_0).toFloat())
            in Opcodes.DCONST_0..Opcodes.DCONST_1 -> JIRRawDouble((opcode - Opcodes.DCONST_0).toDouble())
            else -> error("Unknown constant opcode: $opcode")
        }
        push(constant)
    }

    private fun buildArrayRead(insn: InsnNode) {
        val index = pop()
        val arrayRef = pop()
        val read = JIRRawArrayAccess(arrayRef, index, arrayRef.typeName.elementType())

        val assignment = nextRegister(read.typeName)
        addInstruction(insn, JIRRawAssignInst(method, assignment, read))
        push(assignment)
    }

    private fun buildArrayStore(insn: InsnNode) {
        val value = pop()
        val index = pop()
        val arrayRef = pop()
        addInstruction(
            insn, JIRRawAssignInst(
                method,
                JIRRawArrayAccess(arrayRef, index, arrayRef.typeName.elementType()),
                value
            )
        )
    }

    private fun buildPop(insn: InsnNode) {
        when (val opcode = insn.opcode) {
            Opcodes.POP -> pop()
            Opcodes.POP2 -> {
                val top = pop()
                if (!top.typeName.isDWord) pop()
            }

            else -> error("Unknown pop opcode: $opcode")
        }
    }

    private fun buildDup(insn: InsnNode) {
        when (val opcode = insn.opcode) {
            Opcodes.DUP -> push(peek())
            Opcodes.DUP_X1 -> {
                val top = pop()
                val prev = pop()
                push(top)
                push(prev)
                push(top)
            }

            Opcodes.DUP_X2 -> {
                val val1 = pop()
                val val2 = pop()
                if (val2.typeName.isDWord) {
                    push(val1)
                    push(val2)
                    push(val1)
                } else {
                    val val3 = pop()
                    push(val1)
                    push(val3)
                    push(val2)
                    push(val1)
                }
            }

            Opcodes.DUP2 -> {
                val top = pop()
                if (top.typeName.isDWord) {
                    push(top)
                    push(top)
                } else {
                    val bot = pop()
                    push(bot)
                    push(top)
                    push(bot)
                    push(top)
                }
            }

            Opcodes.DUP2_X1 -> {
                val val1 = pop()
                if (val1.typeName.isDWord) {
                    val val2 = pop()
                    push(val1)
                    push(val2)
                    push(val1)
                } else {
                    val val2 = pop()
                    val val3 = pop()
                    push(val2)
                    push(val1)
                    push(val3)
                    push(val2)
                    push(val1)
                }
            }

            Opcodes.DUP2_X2 -> {
                val val1 = pop()
                if (val1.typeName.isDWord) {
                    val val2 = pop()
                    if (val2.typeName.isDWord) {
                        push(val1)
                        push(val2)
                        push(val1)
                    } else {
                        val val3 = pop()
                        push(val1)
                        push(val3)
                        push(val2)
                        push(val1)
                    }
                } else {
                    val val2 = pop()
                    val val3 = pop()
                    if (val3.typeName.isDWord) {
                        push(val2)
                        push(val1)
                        push(val3)
                        push(val2)
                        push(val1)
                    } else {
                        val val4 = pop()
                        push(val2)
                        push(val1)
                        push(val4)
                        push(val3)
                        push(val2)
                        push(val1)
                    }
                }
            }

            else -> error("Unknown dup opcode: $opcode")
        }
    }

    private fun buildSwap() {
        val top = pop()
        val bot = pop()
        push(top)
        push(bot)
    }

    private fun buildBinary(insn: InsnNode) {
        val rhv = pop()
        val lhv = pop()
        val resolvedType = resolveType(lhv.typeName, rhv.typeName)
        val expr = when (val opcode = insn.opcode) {
            in Opcodes.IADD..Opcodes.DADD -> JIRRawAddExpr(resolvedType, lhv, rhv)
            in Opcodes.ISUB..Opcodes.DSUB -> JIRRawSubExpr(resolvedType, lhv, rhv)
            in Opcodes.IMUL..Opcodes.DMUL -> JIRRawMulExpr(resolvedType, lhv, rhv)
            in Opcodes.IDIV..Opcodes.DDIV -> JIRRawDivExpr(resolvedType, lhv, rhv)
            in Opcodes.IREM..Opcodes.DREM -> JIRRawRemExpr(resolvedType, lhv, rhv)
            in Opcodes.ISHL..Opcodes.LSHL -> JIRRawShlExpr(resolvedType, lhv, rhv)
            in Opcodes.ISHR..Opcodes.LSHR -> JIRRawShrExpr(resolvedType, lhv, rhv)
            in Opcodes.IUSHR..Opcodes.LUSHR -> JIRRawUshrExpr(resolvedType, lhv, rhv)
            in Opcodes.IAND..Opcodes.LAND -> JIRRawAndExpr(resolvedType, lhv, rhv)
            in Opcodes.IOR..Opcodes.LOR -> JIRRawOrExpr(resolvedType, lhv, rhv)
            in Opcodes.IXOR..Opcodes.LXOR -> JIRRawXorExpr(resolvedType, lhv, rhv)
            else -> error("Unknown binary opcode: $opcode")
        }
        val assignment = nextRegister(resolvedType)
        addInstruction(insn, JIRRawAssignInst(method, assignment, expr))
        push(assignment)
    }

    private fun resolveType(left: TypeName, right: TypeName): TypeName {
        val leftName = left.typeName
        val leftIsPrimitive = PredefinedPrimitives.matches(leftName)
        if (leftIsPrimitive) {
            val rightName = right.typeName
            val max = maxOfPrimitiveTypes(leftName, rightName)
            return when {
                max.lessThen(PredefinedPrimitives.Int) -> TypeNameImpl(PredefinedPrimitives.Int)
                else -> TypeNameImpl(max)
            }
        }
        return left
    }

    private fun buildUnary(insn: InsnNode) {
        val operand = pop()
        val expr = when (val opcode = insn.opcode) {
            in Opcodes.INEG..Opcodes.DNEG -> {
                val resolvedType = maxOfPrimitiveTypes(operand.typeName.typeName, PredefinedPrimitives.Int)
                JIRRawNegExpr(TypeNameImpl(resolvedType), operand)
            }

            Opcodes.ARRAYLENGTH -> JIRRawLengthExpr(PredefinedPrimitives.Int.typeName(), operand)
            else -> error("Unknown unary opcode $opcode")
        }
        val assignment = nextRegister(expr.typeName)
        addInstruction(insn, JIRRawAssignInst(method, assignment, expr))
        push(assignment)
    }

    private fun buildCast(insn: InsnNode) {
        val operand = pop()
        val targetType = when (val opcode = insn.opcode) {
            Opcodes.I2L, Opcodes.F2L, Opcodes.D2L -> PredefinedPrimitives.Long.typeName()
            Opcodes.I2F, Opcodes.L2F, Opcodes.D2F -> PredefinedPrimitives.Float.typeName()
            Opcodes.I2D, Opcodes.L2D, Opcodes.F2D -> PredefinedPrimitives.Double.typeName()
            Opcodes.L2I, Opcodes.F2I, Opcodes.D2I -> PredefinedPrimitives.Int.typeName()
            Opcodes.I2B -> PredefinedPrimitives.Byte.typeName()
            Opcodes.I2C -> PredefinedPrimitives.Char.typeName()
            Opcodes.I2S -> PredefinedPrimitives.Short.typeName()
            else -> error("Unknown cast opcode $opcode")
        }
        val assignment = nextRegister(targetType)
        addInstruction(insn, JIRRawAssignInst(method, assignment, JIRRawCastExpr(targetType, operand)))
        push(assignment)
    }

    private fun buildCmp(insn: InsnNode) {
        val rhv = pop()
        val lhv = pop()
        val expr = when (val opcode = insn.opcode) {
            Opcodes.LCMP -> JIRRawCmpExpr(PredefinedPrimitives.Int.typeName(), lhv, rhv)
            Opcodes.FCMPL, Opcodes.DCMPL -> JIRRawCmplExpr(PredefinedPrimitives.Int.typeName(), lhv, rhv)
            Opcodes.FCMPG, Opcodes.DCMPG -> JIRRawCmpgExpr(PredefinedPrimitives.Int.typeName(), lhv, rhv)
            else -> error("Unknown cmp opcode $opcode")
        }
        val assignment = nextRegister(PredefinedPrimitives.Int.typeName())
        addInstruction(insn, JIRRawAssignInst(method, assignment, expr))
        push(assignment)
    }

    private fun buildReturn(insn: InsnNode) {
        addInstruction(
            insn, when (val opcode = insn.opcode) {
                Opcodes.RETURN -> JIRRawReturnInst(method, null)
                in Opcodes.IRETURN..Opcodes.ARETURN -> JIRRawReturnInst(method, pop())
                else -> error("Unknown return opcode: $opcode")
            }
        )
    }

    private fun buildMonitor(insn: InsnNode) {
        val monitor = pop() as JIRRawSimpleValue
        addInstruction(
            insn, when (val opcode = insn.opcode) {
                Opcodes.MONITORENTER -> {
                    JIRRawEnterMonitorInst(method, monitor)
                }

                Opcodes.MONITOREXIT -> JIRRawExitMonitorInst(method, monitor)
                else -> error("Unknown monitor opcode $opcode")
            }
        )
    }

    private fun buildThrow(insn: InsnNode) {
        val throwable = pop()
        addInstruction(insn, JIRRawThrowInst(method, throwable))
    }

    private fun buildFieldInsnNode(insnNode: FieldInsnNode) {
        val fieldName = insnNode.name
        val fieldType = insnNode.desc.typeName()
        val declaringClass = insnNode.owner.typeName()
        when (insnNode.opcode) {
            Opcodes.GETFIELD -> {
                val assignment = nextRegister(fieldType)
                val field = JIRRawFieldRef(pop(), declaringClass, fieldName, fieldType)
                addInstruction(insnNode, JIRRawAssignInst(method, assignment, field))
                push(assignment)
            }

            Opcodes.PUTFIELD -> {
                val value = pop()
                val instance = pop()
                val fieldRef = JIRRawFieldRef(instance, declaringClass, fieldName, fieldType)
                addInstruction(insnNode, JIRRawAssignInst(method, fieldRef, value))
            }

            Opcodes.GFrontendTATIC -> {
                val assignment = nextRegister(fieldType)
                val field = JIRRawFieldRef(declaringClass, fieldName, fieldType)
                addInstruction(insnNode, JIRRawAssignInst(method, assignment, field))
                push(assignment)
            }

            Opcodes.PUTSTATIC -> {
                val value = pop()
                val fieldRef = JIRRawFieldRef(declaringClass, fieldName, fieldType)
                addInstruction(insnNode, JIRRawAssignInst(method, fieldRef, value))
            }
        }
    }

    /**
     * a helper function that helps to merge local variables from several predecessor frames into one map
     * if all the predecessor frames are known (meaning we already visited all the corresponding instructions
     * in the bytecode) --- merge process is trivial
     * if some predecessor frames are unknown, we remember them and add required assignment instructions after
     * the full construction process is complete, see #buildRequiredAssignments function
     */
    private fun Array<TypeName?>.copyLocals(
        predFrames: Map<AbstractInsnNode, Frame?>,
        curLabel: LabelNode,
    ): Array<JIRRawValue?> =
        when {
            // should not happen usually, but sometimes there are some "handing" blocks in the bytecode that are
            // not connected to any other part of the code
            predFrames.isEmpty() -> Array(size) { idx -> this[idx]?.let { nextRegister(it) } }

            // simple case --- current block has only one predecessor, we can simply copy all the local variables from
            // predecessor to new frame; however we sometimes can refine the information about types of local variables
            // from the frame descriptor. In that case we create a new local variable with correct type and remember to
            // normalize them afterwards
            predFrames.size == 1 -> {
                val (node, frame) = predFrames.toList().first()
                when (frame) {
                    null -> Array(size) { variable ->
                        when (val type = this[variable]) {
                            null, TOP -> null
                            else -> {
                                nextRegister(type).also {
                                    laterAssignments.getOrPut(node, ::mutableMapOf)[variable] = it
                                }
                            }
                        }
                    }.trimEndNulls()

                    else -> Array(size) { variable ->
                        val type = this[variable] ?: return@Array null
                        val value = frame.findLocal(variable) ?: return@Array null

                        if (value is JIRRawLocalVar && value.typeName != type && type !in blackListForTypeRefinement) {
                            JIRRawLocalVar(value.index, value.name, type).also { newLocal ->
                                localTypeRefinement[value] = newLocal
                            }
                        } else {
                            value
                        }
                    }.trimEndNulls()
                }
            }

            // complex case --- we have a multiple predecessor frames and some of them may be unknown
            else -> {
                val predFramesValues = predFrames.values.toList()
                val hasNullFrames = predFramesValues.any { it == null }
                Array(size) { variable ->
                    val type = this[variable] ?: return@Array null
                    if (type == TOP) return@Array null

                    if (!hasNullFrames) {
                        var allFramesSameValue: JIRRawValue? = null
                        var valueInitialized = false
                        var allFramesHaveSameValue = true

                        for (frame in predFramesValues) {
                            val frameValue = frame!!.findLocal(variable)

                            if (!valueInitialized) {
                                valueInitialized = true
                                allFramesSameValue = frameValue
                                continue
                            }

                            if (allFramesSameValue != frameValue) {
                                allFramesHaveSameValue = false
                                break
                            }
                        }

                        if (allFramesHaveSameValue) {
                            return@Array allFramesSameValue
                        }
                    }

                    val actualLocalFromDebugInfo = methodNode.localVariables
                        .firstOrNull { it.index == variable && curLabel.isBetween(it.start, it.end) }

                    val isArg = if (actualLocalFromDebugInfo == null) {
                        variable < argCounter
                    } else {
                        actualLocalFromDebugInfo.start == methodNode.instructions.firstOrNull { it is LabelNode }
                    }

                    if (variable < argCounter && isArg) {
                        val value = frames.values.firstOrNull {
                            val value = it.findLocal(variable)
                            value != null && (value is JIRRawArgument || value is JIRRawThis)
                        }?.getLocal(variable)

                        return@Array value
                    }

                    val assignment = nextRegister(type)
                    for ((node, frame) in predFrames) {
                        // TODO! Make anything with that (we should take into account subtyping)
                        // assigment.isSubtypeOf(frame[variable]!!.typeName)
                        if (frame != null) {
                            val inst = JIRRawAssignInst(method, assignment, frame.getLocal(variable))
                            if (node.isBranchingInst) {
                                addInstruction(node, inst, 0)
                            } else {
                                addInstruction(node, inst)
                            }
                        } else {
                            laterAssignments.getOrPut(node, ::mutableMapOf)[variable] = assignment
                        }
                    }

                    assignment
                }.trimEndNulls()
            }
        }

    /**
     * a helper function that helps to merge stack variables from several predecessor frames into one map
     * if all the predecessor frames are known (meaning we already visited all the corresponding instructions
     * in the bytecode) --- merge process is trivial
     * if some predecessor frames are unknown, we remebmer them and add requried assignment instructions after
     * the full construction process is complete, see #buildRequiredAssignments function
     */
    private fun List<TypeName>.copyStack(predFrames: Map<AbstractInsnNode, Frame?>): List<JIRRawValue> = when {
        // should not happen usually, but sometimes there are some "handing" blocks in the bytecode that are
        // not connected to any other part of the code
        predFrames.isEmpty() -> this.map { nextRegister(it) }

        // simple case --- current block has only one predecessor, we can simply copy all the local variables from
        // predecessor to new frame; however we sometimes can refine the information about types of local variables
        // from the frame descriptor. In that case we create a new local variable with correct type and remember to
        // normalize them afterwards
        predFrames.size == 1 -> {
            val (node, frame) = predFrames.toList().first()
            when (frame) {
                null -> this.mapIndexedNotNull { variable, type ->
                    when (type) {
                        TOP -> null
                        else -> nextRegister(type).also {
                            laterStackAssignments.getOrPut(node, ::mutableMapOf)[variable] = it
                        }
                    }
                }

                else -> frame.stack.withIndex().filter { it.index in this.indices }.map {
                    val value = it.value
                    when {
                        value is JIRRawLocalVar && value.typeName != this[it.index] && this[it.index] !in blackListForTypeRefinement -> JIRRawLocalVar(
                            value.index, value.name, this[it.index]
                        ).also { newLocal ->
                            localTypeRefinement[value] = newLocal
                        }

                        else -> value
                    }
                }
            }
        }

        // complex case --- we have a multiple predecessor frames and some of them may be unknown
        else -> this.mapIndexedNotNull { variable, type ->
            val options = predFrames.values.map { it?.stack?.get(variable) }.toSet()
            when (options.size) {
                1 -> options.singleOrNull()
                else -> {
                    val assignment = nextRegister(type)
                    for ((node, frame) in predFrames) {
                        if (frame != null) {
                            if (node.isBranchingInst) {
                                addInstruction(node, JIRRawAssignInst(method, assignment, frame.stack[variable]), 0)
                            } else {
                                addInstruction(node, JIRRawAssignInst(method, assignment, frame.stack[variable]))
                            }
                        } else {
                            laterStackAssignments.getOrPut(node, ::mutableMapOf)[variable] = assignment
                        }
                    }
                    assignment
                }
            }
        }
    }

    private fun buildFrameNode(insnNode: FrameNode) {
        val (currentEntry, blockPredecessors) = run {
            var current: AbstractInsnNode = insnNode
            while (current !is LabelNode) current = current.previous
            current to predecessors[current]!!
        }
        val predecessorFrames = blockPredecessors
            .associateWith { frames[it] }
            .filter { predecessors[it.key] == null || !predecessors[it.key]!!.contains(currentEntry) }
        assert(predecessorFrames.isNotEmpty())
        lastFrameState = when (insnNode.type) {
            Opcodes.F_NEW -> FrameState.parseNew(insnNode)
            Opcodes.F_FULL -> FrameState.parseNew(insnNode)
            Opcodes.F_APPEND -> lastFrameState.appendFrame(insnNode)
            Opcodes.F_CHOP -> lastFrameState.dropFrame(insnNode)
            Opcodes.F_SAME -> lastFrameState.copy0()
            Opcodes.F_SAME1 -> lastFrameState.copy1(insnNode)
            else -> error("Unknown frame node type: ${insnNode.type}")
        }

        val catchEntries = methodNode.tryCatchBlocks.filter { it.handler == currentEntry }

        if (catchEntries.isEmpty()) {
            currentFrame = lastFrameState.copyToFrame(predecessorFrames, currentEntry, copyStack = true)
        } else {
            currentFrame = lastFrameState.copyToFrame(predecessorFrames, currentEntry, copyStack = false)

            val throwable = nextRegister(catchEntries.commonTypeOrDefault.typeName())
            val entries = catchEntries.map {
                JIRRawCatchEntry(
                    it.typeOrDefault.typeName(),
                    labelRef(it.start),
                    labelRef(it.end)
                )
            }

            val catchInst = JIRRawCatchInst(
                method,
                throwable,
                labelRef(currentEntry),
                entries
            )

            addInstruction(currentEntry, catchInst, index = 1)
            var curInst = currentEntry as AbstractInsnNode
            while (curInst != insnNode) {
                frames[curInst] = currentFrame
                curInst = curInst.next
            }

            push(throwable)
        }
        var curNode: AbstractInsnNode = insnNode
        while (curNode !is LabelNode) {
            curNode = curNode.previous
            frames[curNode] = currentFrame
        }
    }

    private fun buildIincInsnNode(insnNode: IincInsnNode) {
        val variable = insnNode.`var`
        val local = local(variable)
        val nextInst = insnNode.next
        val prevInst = insnNode.previous
        val incrementedVariable = when {
            nextInst != null && nextInst.isBranchingInst -> local

            nextInst != null && nextInst is VarInsnNode && nextInst.`var` == variable -> local

            // Workaround for if (x++) if x is function argument
            prevInst != null && local is JIRRawArgument && prevInst is VarInsnNode && prevInst.`var` == variable ->
                nextRegister(local.typeName)

            local is JIRRawArgument -> local

            else -> nextRegister(local.typeName)
        }
        val add = JIRRawAddExpr(local.typeName, local, JIRRawInt(insnNode.incr))
        instructionList(insnNode) += JIRRawAssignInst(method, incrementedVariable, add)
        local(variable, incrementedVariable, insnNode, override = incrementedVariable != local)
    }

    private fun buildIntInsnNode(insnNode: IntInsnNode) {
        val operand = insnNode.operand
        when (val opcode = insnNode.opcode) {
            Opcodes.BIPUSH -> push(JIRRawInt(operand))
            Opcodes.SIPUSH -> push(JIRRawInt(operand))
            Opcodes.NEWARRAY -> {
                val expr = JIRRawNewArrayExpr(operand.toPrimitiveType().asArray(), pop())
                val assignment = nextRegister(expr.typeName)
                addInstruction(insnNode, JIRRawAssignInst(method, assignment, expr))
                push(assignment)
            }

            else -> error("Unknown int insn opcode: $opcode")
        }
    }

    private val Handle.bsmHandleArg
        get() = BsmHandle(
            tag,
            owner.typeName(),
            name,
            if (desc.contains("(")) {
                Type.getArgumentTypes(desc).map { it.descriptor.typeName() }
            } else {
                listOf()
            },
            if (desc.contains("(")) {
                Type.getReturnType(desc).descriptor.typeName()
            } else {
                Type.getReturnType("(;)$desc").descriptor.typeName()
            },
            isInterface
        )

    private fun bsmNumberArg(number: Number) = when (number) {
        is Int -> BsmIntArg(number)
        is Float -> BsmFloatArg(number)
        is Long -> BsmLongArg(number)
        is Double -> BsmDoubleArg(number)
        else -> error("Unknown number: $number")
    }

    private fun buildInvokeDynamicInsn(insnNode: InvokeDynamicInsnNode) {
        val desc = insnNode.desc
        val bsmArgs = insnNode.bsmArgs.map {
            when (it) {
                is Number -> bsmNumberArg(it)
                is String -> BsmStringArg(it)
                is Type -> it.asTypeName
                is Handle -> it.bsmHandleArg
                else -> error("Unknown arg of bsm: $it")
            }
        }.reversed()
        val args = Type.getArgumentTypes(desc).map { pop() }.reversed()
        val bsmMethod = insnNode.bsm.bsmHandleArg
        val expr = JIRRawDynamicCallExpr(
            bsmMethod,
            bsmArgs,
            insnNode.name,
            Type.getArgumentTypes(desc).map { it.descriptor.typeName() },
            Type.getReturnType(desc).descriptor.typeName(),
            args,
        )
        if (Type.getReturnType(desc) == Type.VOID_TYPE) {
            addInstruction(insnNode, JIRRawCallInst(method, expr))
        } else {
            val result = nextRegister(Type.getReturnType(desc).descriptor.typeName())
            addInstruction(insnNode, JIRRawAssignInst(method, result, expr))
            push(result)
        }
    }

    private fun buildJumpInsnNode(insnNode: JumpInsnNode) {
        val target = labelRef(insnNode.label)
        when (val opcode = insnNode.opcode) {
            Opcodes.GOTO -> addInstruction(insnNode, JIRRawGotoInst(method, target))
            else -> {
                val falseTarget = (insnNode.next as? LabelNode)?.let { label(it) } ?: nextLabel()
                val rhv = pop()
                val boolTypeName = PredefinedPrimitives.Boolean.typeName()
                val expr = when (opcode) {
                    Opcodes.IFNULL -> JIRRawEqExpr(boolTypeName, rhv, JIRRawNull())
                    Opcodes.IFNONNULL -> JIRRawNeqExpr(boolTypeName, rhv, JIRRawNull())
                    Opcodes.IFEQ -> JIRRawEqExpr(boolTypeName, rhv, JIRRawZero(rhv.typeName))
                    Opcodes.IFNE -> JIRRawNeqExpr(boolTypeName, rhv, JIRRawZero(rhv.typeName))
                    Opcodes.IFLT -> JIRRawLtExpr(boolTypeName, rhv, JIRRawZero(rhv.typeName))
                    Opcodes.IFGE -> JIRRawGeExpr(boolTypeName, rhv, JIRRawZero(rhv.typeName))
                    Opcodes.IFGT -> JIRRawGtExpr(boolTypeName, rhv, JIRRawZero(rhv.typeName))
                    Opcodes.IFLE -> JIRRawLeExpr(boolTypeName, rhv, JIRRawZero(rhv.typeName))
                    Opcodes.IF_ICMPEQ -> JIRRawEqExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPNE -> JIRRawNeqExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPLT -> JIRRawLtExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPGE -> JIRRawGeExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPGT -> JIRRawGtExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPLE -> JIRRawLeExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ACMPEQ -> JIRRawEqExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ACMPNE -> JIRRawNeqExpr(boolTypeName, pop(), rhv)
                    else -> error("Unknown jump opcode $opcode")
                }
                if (labels.any { it.value.name == target.name }) { // jump will be up in instruction list
                    val nextLabel = nextLabel()
                    addInstruction(insnNode, JIRRawIfInst(method, expr, nextLabel.ref, falseTarget.ref))
                    addInstruction(insnNode, nextLabel)
                    additionalSections[insnNode] = nextLabel
                    addInstruction(insnNode, JIRRawGotoInst(method, target))
                } else {
                    addInstruction(insnNode, JIRRawIfInst(method, expr, target, falseTarget.ref))
                }
                if (insnNode.next !is LabelNode) {
                    addInstruction(insnNode, falseTarget)
                }
            }
        }
    }

    private fun mergeFrames(frames: Map<AbstractInsnNode, Frame>, curLabel: LabelNode): Frame {
        val frameSet = frames.values
        if (frames.isEmpty()) return currentFrame
        if (frames.size == 1) return frameSet.first()

        val maxLocalVar = frameSet.minOf { it.maxLocal() }
        val localTypes = Array(maxLocalVar + 1) { local ->
            if (!frameSet.all { it.hasLocal(local) }) return@Array null

            var type: TypeName? = null
            for (frame in frameSet) {
                val frameType = frame.getLocal(local).typeName
                if (type == null || type == frameType) {
                    type = frameType
                    continue
                }

                // If we have several variables types for one register we have to search right type in debug info otherwise we cannot guarantee anything
                val debugType = methodNode.localVariables
                    .firstOrNull { curLabel.isBetween(it.start, it.end) && it.index == local }
                    ?.desc
                    ?.let { TypeNameImpl(it) }

                type = when {
                    debugType != null -> debugType
                    frameType != NULL -> frameType
                    else -> type
                }

                break
            }

            type ?: NULL
        }
        val newLocals = localTypes.copyLocals(frames, curLabel)

        val maxStackIndex = frameSet.minOf { it.stack.lastIndex }
        val stackRanges = mutableListOf<TypeName>()
        for (i in 0..maxStackIndex) {
            var type = NULL
            for (frame in frameSet) {
                val frameType = frame.stack[i].typeName
                if (frameType != NULL) {
                    type = frameType
                    break
                }
            }
            stackRanges.add(type)
        }

        val newStack = stackRanges.copyStack(frames).toPersistentList()

        return Frame(newLocals, newStack)
    }

    private fun buildLabelNode(insnNode: LabelNode) {
        val labelInst = label(insnNode)
        addInstruction(insnNode, labelInst)
        val predecessors = predecessors.getOrDefault(insnNode, emptySet()).filter { !deadInstructions.contains(it) }
        val predecessorFrames = predecessors.mapNotNull { frames[it] }
        if (predecessorFrames.size == 1) {
            currentFrame = predecessorFrames.first()
        } else {
            currentFrame = mergeFrames(predecessors.zip(predecessorFrames).toMap(), insnNode)
        }
        val catchEntries = methodNode.tryCatchBlocks.filter { it.handler == insnNode }

        if (catchEntries.isNotEmpty()) {
            push(nextRegister(catchEntries.commonTypeOrDefault.typeName()))
        }
    }

    private fun buildLineNumberNode(insnNode: LineNumberNode) {
        addInstruction(insnNode, JIRRawLineNumberInst(method, insnNode.line, labelRef(insnNode.start)))
    }

    private fun ldcValue(cst: Any): JIRRawValue {
        return when (cst) {
            is Int -> JIRRawInt(cst)
            is Float -> JIRRawFloat(cst)
            is Double -> JIRRawDouble(cst)
            is Long -> JIRRawLong(cst)
            is String -> JIRRawStringConstant(cst, STRING_CLASS.typeName())
            is Type -> JIRRawClassConstant(cst.descriptor.typeName(), CLASS_CLASS.typeName())
            is Handle -> {
                JIRRawMethodConstant(
                    cst.owner.typeName(),
                    cst.name,
                    Type.getArgumentTypes(cst.desc).map { it.descriptor.typeName() },
                    Type.getReturnType(cst.desc).descriptor.typeName(),
                    METHOD_HANDLE_CLASS.typeName()
                )
            }

            else -> error("Can't convert LDC value: $cst of type ${cst::class.java.name}")
        }
    }

    private fun buildLdcInsnNode(insnNode: LdcInsnNode) {
        when (val cst = insnNode.cst) {
            is Int -> push(ldcValue(cst))
            is Float -> push(ldcValue(cst))
            is Double -> push(ldcValue(cst))
            is Long -> push(ldcValue(cst))
            is String -> push(JIRRawStringConstant(cst, STRING_CLASS.typeName()))
            is Type -> {
                val assignment = nextRegister(CLASS_CLASS.typeName())
                addInstruction(
                    insnNode, JIRRawAssignInst(
                        method,
                        assignment,
                        when (cst.sort) {
                            Type.METHOD -> JIRRawMethodType(
                                cst.argumentTypes.map { it.descriptor.typeName() },
                                cst.returnType.descriptor.typeName(),
                                METHOD_TYPE_CLASS.typeName()
                            )

                            else -> ldcValue(cst)
                        }
                    )
                )
                push(assignment)
            }

            is Handle -> {
                val assignment = nextRegister(CLASS_CLASS.typeName())
                addInstruction(
                    insnNode, JIRRawAssignInst(
                        method,
                        assignment,
                        ldcValue(cst)
                    )
                )
                push(assignment)
            }

            is ConstantDynamic -> {
                val methodHande = cst.bootstrapMethod
                val assignment = nextRegister(CLASS_CLASS.typeName())
                val exprs = arrayListOf<JIRRawValue>()
                repeat(cst.bootstrapMethodArgumentCount) {
                    exprs.add(
                        ldcValue(cst.getBootstrapMethodArgument(it - 1))
                    )
                }
                val methodCall: JIRRawCallExpr = when (cst.bootstrapMethod.tag) {
                    Opcodes.INVOKESPECIAL -> JIRRawSpecialCallExpr(
                        methodHande.owner.typeName(),
                        cst.name,
                        Type.getArgumentTypes(methodHande.desc).map { it.descriptor.typeName() },
                        Type.getReturnType(methodHande.desc).descriptor.typeName(),
                        thisRef(),
                        exprs
                    )

                    else -> {
                        val lookupAssignment = nextRegister(METHOD_HANDLES_LOOKUP_CLASS.typeName())
                        addInstruction(
                            insnNode, JIRRawAssignInst(
                                method,
                                lookupAssignment,
                                JIRRawStaticCallExpr(
                                    METHOD_HANDLES_CLASS.typeName(),
                                    "lookup",
                                    emptyList(),
                                    METHOD_HANDLES_LOOKUP_CLASS.typeName(),
                                    emptyList()
                                )
                            )
                        )
                        JIRRawStaticCallExpr(
                            methodHande.owner.typeName(),
                            methodHande.name,
                            Type.getArgumentTypes(methodHande.desc).map { it.descriptor.typeName() },
                            Type.getReturnType(methodHande.desc).descriptor.typeName(),
                            listOf(
                                lookupAssignment,
                                JIRRawStringConstant(cst.name, STRING_CLASS.typeName()),
                                JIRRawClassConstant(cst.descriptor.typeName(), CLASS_CLASS.typeName())
                            ) + exprs,
                            methodHande.isInterface
                        )
                    }
                }
                addInstruction(insnNode, JIRRawAssignInst(method, assignment, methodCall))
                push(assignment)
            }

            else -> error("Unknown LDC constant: $cst and type ${cst::class.java.name}")
        }
    }

    private fun buildLookupSwitchInsnNode(insnNode: LookupSwitchInsnNode) {
        val key = pop()
        val default = labelRef(insnNode.dflt)
        val branches = insnNode.keys
            .zip(insnNode.labels)
            .associate { (JIRRawInt(it.first) as JIRRawValue) to labelRef(it.second) }
        addInstruction(insnNode, JIRRawSwitchInst(method, key, branches, default))
    }

    private fun buildMethodInsnNode(insnNode: MethodInsnNode) {
        val owner = when {
            insnNode.owner.typeName().isArray -> OBJECT_CLASS.typeName()
            else -> insnNode.owner.typeName()
        }
        val methodName = insnNode.name
        val argTypes = Type.getArgumentTypes(insnNode.desc).map { it.descriptor.typeName() }
        val returnType = Type.getReturnType(insnNode.desc).descriptor.typeName()

        val args = Type.getArgumentTypes(insnNode.desc).map { pop() }.reversed()

        val expr = when (val opcode = insnNode.opcode) {
            Opcodes.INVOKESTATIC -> JIRRawStaticCallExpr(
                owner,
                methodName,
                argTypes,
                returnType,
                args,
                insnNode.itf
            )

            else -> {
                val instance = pop()
                when (opcode) {
                    Opcodes.INVOKEVIRTUAL -> JIRRawVirtualCallExpr(
                        owner,
                        methodName,
                        argTypes,
                        returnType,
                        instance,
                        args
                    )

                    Opcodes.INVOKESPECIAL -> JIRRawSpecialCallExpr(
                        owner,
                        methodName,
                        argTypes,
                        returnType,
                        instance,
                        args
                    )

                    Opcodes.INVOKEINTERFACE -> JIRRawInterfaceCallExpr(
                        owner,
                        methodName,
                        argTypes,
                        returnType,
                        instance,
                        args
                    )

                    else -> error("Unknown method insn opcode: ${insnNode.opcode}")
                }
            }
        }
        if (Type.getReturnType(insnNode.desc) == Type.VOID_TYPE) {
            addInstruction(insnNode, JIRRawCallInst(method, expr))
        } else {
            val result = nextRegister(Type.getReturnType(insnNode.desc).descriptor.typeName())
            addInstruction(insnNode, JIRRawAssignInst(method, result, expr))
            push(result)
        }
    }

    private fun buildMultiANewArrayInsnNode(insnNode: MultiANewArrayInsnNode) {
        val dimensions = mutableListOf<JIRRawValue>()
        repeat(insnNode.dims) {
            dimensions += pop()
        }
        val expr = JIRRawNewArrayExpr(insnNode.desc.typeName(), dimensions.reversed())
        val assignment = nextRegister(expr.typeName)
        addInstruction(insnNode, JIRRawAssignInst(method, assignment, expr))
        push(assignment)
    }

    private fun buildTableSwitchInsnNode(insnNode: TableSwitchInsnNode) {
        val index = pop()
        val default = labelRef(insnNode.dflt)
        val branches = (insnNode.min..insnNode.max)
            .zip(insnNode.labels)
            .associate { (JIRRawInt(it.first) as JIRRawValue) to labelRef(it.second) }
        addInstruction(insnNode, JIRRawSwitchInst(method, index, branches, default))
    }

    private fun buildTypeInsnNode(insnNode: TypeInsnNode) {
        val type = insnNode.desc.typeName()
        when (insnNode.opcode) {
            Opcodes.NEW -> {
                val assignment = nextRegister(type)
                addInstruction(insnNode, JIRRawAssignInst(method, assignment, JIRRawNewExpr(type)))
                push(assignment)
            }

            Opcodes.ANEWARRAY -> {
                val length = pop()
                val assignment = nextRegister(type.asArray())
                addInstruction(
                    insnNode, JIRRawAssignInst(
                        method,
                        assignment,
                        JIRRawNewArrayExpr(type.asArray(), length)
                    )
                )
                push(assignment)
            }

            Opcodes.CHECKCAST -> {
                val assignment = nextRegister(type)
                addInstruction(insnNode, JIRRawAssignInst(method, assignment, JIRRawCastExpr(type, pop())))
                push(assignment)
            }

            Opcodes.INSTANCEOF -> {
                val assignment = nextRegister(PredefinedPrimitives.Boolean.typeName())
                addInstruction(
                    insnNode, JIRRawAssignInst(
                        method,
                        assignment,
                        JIRRawInstanceOfExpr(PredefinedPrimitives.Boolean.typeName(), pop(), type)
                    )
                )
                push(assignment)
            }

            else -> error("Unknown opcode ${insnNode.opcode} in TypeInsnNode")
        }
    }

    private fun buildVarInsnNode(insnNode: VarInsnNode) {
        val variable = insnNode.`var`
        when (insnNode.opcode) {
            in Opcodes.ISTORE..Opcodes.ASTORE -> {
                val inst = local(variable, pop(), insnNode)
                addInstruction(insnNode, inst)
            }

            in Opcodes.ILOAD..Opcodes.ALOAD -> {
                push(local(variable))
            }

            else -> error("Unknown opcode ${insnNode.opcode} in VarInsnNode")
        }
    }
}

private fun <T> Array<T?>.trimEndNulls(): Array<T?> {
    var realSize = size

    while (realSize > 0 && this[realSize - 1] == null) {
        realSize--
    }

    if (realSize == size) {
        return this
    }

    return this.copyOf(realSize)
}

private fun <T> Array<T?>.add(index: Int, value: T): Array<T?> {
    if (index < size) {
        this[index] = value
        return this
    }

    val result = copyOf(size * 2)
    result[index] = value
    return result
}
