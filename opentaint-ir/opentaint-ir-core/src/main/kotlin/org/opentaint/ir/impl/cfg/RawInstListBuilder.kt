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
import org.opentaint.ir.api.jvm.cfg.BsmHandleTag
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
import org.opentaint.ir.api.jvm.cfg.JIRRawBranchingInst
import org.opentaint.ir.api.jvm.cfg.JIRRawCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCallInst
import org.opentaint.ir.api.jvm.cfg.JIRRawCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCatchEntry
import org.opentaint.ir.api.jvm.cfg.JIRRawCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRRawClassConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawCmpExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCmpgExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCmplExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawConditionExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawDivExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawDynamicCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawEnterMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRRawEqExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawExitMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRRawExpr
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
import org.opentaint.ir.impl.cfg.util.typeNameFromAsmInternalName
import org.opentaint.ir.impl.cfg.util.typeNameFromJvmName
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

private fun parseBsmHandleTag(tag: Int): BsmHandleTag = when (tag) {
    Opcodes.H_GETFIELD -> BsmHandleTag.FieldHandle.GET_FIELD
    Opcodes.H_GFrontendTATIC -> BsmHandleTag.FieldHandle.GET_STATIC
    Opcodes.H_PUTFIELD -> BsmHandleTag.FieldHandle.PUT_FIELD
    Opcodes.H_PUTSTATIC -> BsmHandleTag.FieldHandle.PUT_STATIC

    Opcodes.H_INVOKEVIRTUAL -> BsmHandleTag.MethodHandle.INVOKE_VIRTUAL
    Opcodes.H_INVOKESTATIC -> BsmHandleTag.MethodHandle.INVOKE_STATIC
    Opcodes.H_INVOKESPECIAL -> BsmHandleTag.MethodHandle.INVOKE_SPECIAL
    Opcodes.H_NEWINVOKESPECIAL -> BsmHandleTag.MethodHandle.NEW_INVOKE_SPECIAL
    Opcodes.H_INVOKEINTERFACE -> BsmHandleTag.MethodHandle.INVOKE_INTERFACE

    else -> error("Unknown tag in BSM handle: $tag")
}

private fun parseType(any: Any): TypeName = when (any) {
    is String -> any.typeNameFromAsmInternalName()
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
        newNode.desc.typeNameFromAsmInternalName()
    }

    else -> error("Unexpected local type $any")
}

private infix fun TypeName.isCompatibleWith(type: TypeName): Boolean {
    val isPrimitiveLeft = isPrimitive
    val isPrimitiveRight = type.isPrimitive
    if (isPrimitiveLeft != isPrimitiveRight) {
        return false
    }
    if (!isPrimitiveLeft) { // both are reference types
        return true
    }
    // both are primitive types
    return isDWord == type.isDWord
}

private val OBJECT_TYPE_NAME = OBJECT_CLASS.typeNameFromJvmName()

private fun typeLub(first: TypeName, second: TypeName): TypeName {
    if (first == second) return first

    if (first == TOP || second == TOP) return TOP

    if (first.isPrimitive) {
        return primitiveTypeLub(first, second)
    }

    if (second.isPrimitive) {
        return primitiveTypeLub(second, first)
    }

    return OBJECT_TYPE_NAME
}

private fun primitiveTypeLub(primitiveType: TypeName, other: TypeName): TypeName {
    if (primitiveType == NULL) {
        return if (other.isPrimitive) TOP else other
    }

    if (!other.isPrimitive) return TOP

    if (primitiveType.typeName == PredefinedPrimitives.Int) {
        return other
    }

    if (other.typeName == PredefinedPrimitives.Int) {
        return primitiveType
    }

    return TOP
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

private val TryCatchBlockNode.typeOrDefault: TypeName
    get() = this.type?.typeNameFromAsmInternalName()
        ?: THROWABLE_CLASS.typeNameFromJvmName()

private val Collection<TryCatchBlockNode>.commonTypeOrDefault: TypeName
    get() = map { it.type?.typeNameFromAsmInternalName() }
        .distinct()
        .singleOrNull()
        ?: THROWABLE_CLASS.typeNameFromJvmName()

internal fun <K, V> identityMap(): MutableMap<K, V> = IdentityHashMap()

class RawInstListBuilder(
    val method: JIRMethod,
    private val methodNode: MethodNode,
    private val keepLocalVariableNames: Boolean,
) {
    private val ENTRY = InsnNode(ENTRY_NODE_OP)
    private val methodInstList = methodNode.instructions
    private val insnNodeGraph = IntNodeGraph(ENTRY_NODE_ID, insnNodeCount())

    private val frames = arrayOfNulls<Frame>(insnNodeCount())
    private val instructionLists = arrayOfNulls<MutableList<JIRRawInst>>(insnNodeCount())

    private val labels = identityMap<LabelNode, JIRRawLabelInst>()
    private val tryCatchHandlers = identityMap<AbstractInsnNode, MutableList<TryCatchBlockNode>>()

    private val localTypeRefinement = hashMapOf<JIRRawLocalVar, JIRRawLocalVar>()
    private val blackListForTypeRefinement = listOf(TOP, NULL, UNINIT_THIS)

    private val localMergeAssignments = mutableListOf<LaterAssignments>()
    private val stackMergeAssignments = mutableListOf<LaterAssignments>()

    private var argCounter = 0
    private var generatedLocalVarsCounter = 0

    fun build(): JIRInstList<JIRRawInst> {
        buildGraph()

        buildInstructions()
        buildRequiredAssignments()
        buildRequiredGotos()

        val generatedInstructions = mutableListOf<JIRRawInst>()
        instructionLists.forEach { insnList -> insnList?.let { generatedInstructions += it } }
        generatedInstructions.ensureFirstInstIsLineNumber()

        val originalInstructionList = JIRInstListImpl(generatedInstructions)

        // after all the frame info resolution we can refine type info for some local variables,
        // so we replace all the old versions of the variables with the type refined ones
        @Suppress("UNCHECKED_CAST")
        val localTypeRefinementExprMap = localTypeRefinement as Map<JIRRawExpr, JIRRawExpr>
        val localsNormalizedInstructionList = originalInstructionList.map(ExprMapper(localTypeRefinementExprMap))

        return Simplifier().simplify(localsNormalizedInstructionList)
    }

    private fun MutableList<JIRRawInst>.ensureFirstInstIsLineNumber() {
        val firstLineNumber = indexOfFirst { it is JIRRawLineNumberInst }
        if (firstLineNumber == -1 || firstLineNumber == 0) return
        if (firstLineNumber == 1 && this[0] is JIRRawLabelInst) return

        val lineNumberInst = this[firstLineNumber] as JIRRawLineNumberInst
        val label = generateFreshLabel()
        val lineNumberWithLabel = JIRRawLineNumberInst(lineNumberInst.owner, lineNumberInst.lineNumber, label.ref)
        addAll(0, listOf(label, lineNumberWithLabel))
    }

    private fun nodeId(node: AbstractInsnNode): Int = node.index + 1

    private fun nodeById(nodeId: Int): AbstractInsnNode =
        if (nodeId == ENTRY_NODE_ID) ENTRY else methodInstList.get(nodeId - 1)

    private fun insnNodeCount(): Int = methodInstList.size() + 1

    private fun buildInstructions() {
        insnNodeGraph.forEachNodeIdInTopOrderWithoutBackEdges { insnNodeId ->
            if (insnNodeId == ENTRY_NODE_ID) {
                frames[ENTRY_NODE_ID] = createInitialFrame()
                return@forEachNodeIdInTopOrderWithoutBackEdges
            }

            val insn = nodeById(insnNodeId)
            val frame = when (insn) {
                is LabelNode -> buildLabelNode(insn)
                is FrameNode -> buildFrameNode(insn)
                else -> buildSimpleInstruction(insn)
            }
            frames[insnNodeId] = frame
        }
    }

    private fun buildSimpleInstruction(insn: AbstractInsnNode): Frame {
        val predecessorId = insnNodeGraph.singlePredecessor(nodeId(insn))
        val predecessorFrame = frames[predecessorId]
            ?: error("Incorrect frame processing order")

        val frameBuilder = FrameBuilder(predecessorFrame)

        when (insn) {
            is InsnNode -> buildInsnNode(insn, frameBuilder)
            is FieldInsnNode -> buildFieldInsnNode(insn, frameBuilder)
            is IincInsnNode -> buildIincInsnNode(insn, frameBuilder)
            is IntInsnNode -> buildIntInsnNode(insn, frameBuilder)
            is InvokeDynamicInsnNode -> buildInvokeDynamicInsn(insn, frameBuilder)
            is JumpInsnNode -> buildJumpInsnNode(insn, frameBuilder)
            is LineNumberNode -> buildLineNumberNode(insn)
            is LdcInsnNode -> buildLdcInsnNode(insn, frameBuilder)
            is LookupSwitchInsnNode -> buildLookupSwitchInsnNode(insn, frameBuilder)
            is MethodInsnNode -> buildMethodInsnNode(insn, frameBuilder)
            is MultiANewArrayInsnNode -> buildMultiANewArrayInsnNode(insn, frameBuilder)
            is TableSwitchInsnNode -> buildTableSwitchInsnNode(insn, frameBuilder)
            is TypeInsnNode -> buildTypeInsnNode(insn, frameBuilder)
            is VarInsnNode -> buildVarInsnNode(insn, frameBuilder)

            else -> error("Unknown insn node ${insn::class}")
        }

        return frameBuilder.currentFrame
    }

    data class LaterAssignments(val label: LabelNode, val assignments: LinkedHashMap<Int, JIRRawSimpleValue>)

    data class LaterAssignment(
        val insn: AbstractInsnNode,
        val assignTo: JIRRawSimpleValue,
        val currentValue: JIRRawSimpleValue
    )

    // `localMergeAssignments` and `stackMergeAssignments` are maps of variable assignments
    // that we need to add to the instruction list after the construction process to ensure
    // liveness of the variables on every step of the method. We cannot add them during the construction
    // because some of them are unknown at that stage (e.g. because of loops)
    private fun buildRequiredAssignments() {
        for ((mergeInst, localAssignments) in localMergeAssignments) {
            for ((variable, value) in localAssignments) {
                val assignments = mutableListOf<LaterAssignment>()
                insnNodeGraph.forEachPredecessor(mergeInst) { insn ->
                    val frame = instructionFrame(insn)
                    val frameVariable = frame.findLocal(variable)
                    if (frameVariable != null && value != frameVariable) {
                        assignments.add(LaterAssignment(insn, value, frameVariable))
                    }
                }
                insertLaterAssignments(assignments)
            }
        }

        for ((mergeInst, stackAssignments) in stackMergeAssignments) {
            for ((index, value) in stackAssignments) {
                val assignments = mutableListOf<LaterAssignment>()
                insnNodeGraph.forEachPredecessor(mergeInst) { insn ->
                    val frame = instructionFrame(insn)
                    val frameValue = frame.stack[index]
                    if (value != frameValue) {
                        assignments.add(LaterAssignment(insn, value, frameValue))
                    }
                }
                insertLaterAssignments(assignments)
            }
        }
    }

    private fun insertLaterAssignments(assignments: List<LaterAssignment>) {
        for ((currentValue, assignmentGroup) in assignments.groupBy { it.currentValue }) {
            if (assignmentGroup.size == 1) {
                val assignment = assignmentGroup.single()
                val insnList = instructionList(assignment.insn)
                insertValueAssignment(assignment.insn, insnList, assignment.assignTo, currentValue)
                continue
            }

            val groupNodeIds = BitSet(insnNodeCount())
            for (assignment in assignmentGroup) {
                groupNodeIds.set(nodeId(assignment.insn))
            }
            val startNodes = insnNodeGraph.findStartNodes(groupNodeIds)

            for (assignment in assignmentGroup) {
                val insnList = instructionList(assignment.insn)
                val nodeId = nodeId(assignment.insn)

                if (startNodes.get(nodeId) || insnList.containsAssignToValue(currentValue)) {
                    insertValueAssignment(assignment.insn, insnList, assignment.assignTo, currentValue)
                    continue
                }
            }
        }
    }

    private fun List<JIRRawInst>.containsAssignToValue(value: JIRRawSimpleValue): Boolean =
        any { it is JIRRawAssignInst && it.lhv == value }

    private fun insertValueAssignment(
        insn: AbstractInsnNode,
        insnList: MutableList<JIRRawInst>,
        value: JIRRawSimpleValue,
        expr: JIRRawSimpleValue
    ) {
        val assignment = JIRRawAssignInst(method, value, expr)
        if (insn.isTerminateInst) {
            insnList.addInst(assignment, insnList.lastIndex)
        } else if (insn.isBranchingInst) {
            val branchInstIdx = insnList.indexOfFirst { it is JIRRawBranchingInst }
            val branchInst = insnList[branchInstIdx] as JIRRawBranchingInst
            when (branchInst) {
                is JIRRawGotoInst -> {
                    insnList.addInst(assignment, branchInstIdx)
                }

                is JIRRawSwitchInst -> {
                    insnList.addInst(assignment, branchInstIdx)
                    if (branchInst.key.dependsOn(value)) {
                        val freshVar = generateFreshLocalVar(branchInst.key.typeName)
                        insnList.addInst(JIRRawAssignInst(method, freshVar, branchInst.key), index = 0)
                        insnList[branchInstIdx + 2] = JIRRawSwitchInst(
                            owner = branchInst.owner,
                            key = freshVar,
                            branches = branchInst.branches,
                            default = branchInst.default
                        )
                    }
                }

                is JIRRawIfInst -> {
                    insnList.addInst(assignment, branchInstIdx)
                    if (branchInst.condition.dependsOn(value)) {
                        val freshVar = generateFreshLocalVar(value.typeName)
                        insnList.addInst(JIRRawAssignInst(method, freshVar, value), index = 0)

                        val updatedCondition = branchInst.condition.replace(fromValue = value, toValue = freshVar)
                        insnList[branchInstIdx + 2] = JIRRawIfInst(
                            owner = branchInst.owner,
                            condition = updatedCondition,
                            trueBranch = branchInst.trueBranch,
                            falseBranch = branchInst.falseBranch
                        )
                    }
                }
            }
        } else {
            insnList.addInst(assignment)
        }
    }

    private fun JIRRawExpr.dependsOn(value: JIRRawSimpleValue): Boolean =
        this == value || operands.any { it.dependsOn(value) }

    private fun JIRRawConditionExpr.replace(
        fromValue: JIRRawSimpleValue,
        toValue: JIRRawSimpleValue
    ): JIRRawConditionExpr =
        accept(ExprMapper(mapOf(fromValue to toValue))) as JIRRawConditionExpr

    // adds the `goto` instructions to ensure consistency in the instruction list:
    // every jump is show explicitly with some branching instruction
    private fun buildRequiredGotos() {
        for (insnId in instructionLists.indices) {
            // dead instruction
            if (instructionLists[insnId] == null) continue

            val insn = nodeById(insnId)
            if (tryCatchHandlers.contains(insn)) continue

            if (!insnNodeGraph.hasMultiplePredecessors(insnId)) continue
            insnNodeGraph.forEachPredecessor(insn) { predecessor ->
                if (!predecessor.isBranchingInst) {
                    val label = when (insn) {
                        is LabelNode -> labelRef(insn)
                        else -> {
                            val newLabel = generateFreshLabel()
                            addInstruction(insn, newLabel, 0)
                            newLabel.ref
                        }
                    }
                    addInstruction(predecessor, JIRRawGotoInst(method, label))
                }
            }
        }
    }

    /**
     * represents a frame state: information about types of local variables and stack variables
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

    private fun refineWithFrameState(frame: Frame, frameState: FrameState): Frame {
        val localTypes = frameState.localsUnsafe()
        val refinedLocals = Array(localTypes.size) { variable ->
            val type = localTypes[variable]
            if (type == null || type == TOP) return@Array null

            val value = frame.findLocal(variable) ?: return@Array null

            if (value is JIRRawLocalVar && value.typeName != type && type !in blackListForTypeRefinement) {
                JIRRawLocalVar(value.index, value.name, type).also { newLocal ->
                    localTypeRefinement[value] = newLocal
                }
            } else {
                value
            }
        }

        val stackTypes = frameState.stack
        val refinedStack = frame.stack.withIndex()
            .filter { it.index in stackTypes.indices }
            .map { (index, value) ->
                val type = stackTypes[index]
                if (value is JIRRawLocalVar && value.typeName != type && type !in blackListForTypeRefinement) {
                    JIRRawLocalVar(value.index, value.name, type).also { newLocal ->
                        localTypeRefinement[value] = newLocal
                    }
                } else value
            }

        return Frame(refinedLocals.trimEndNulls(), refinedStack.toPersistentList())
    }

    /**
     * represents the bytecode Frame: a set of active local variables and stack variables
     * during the execution of the instruction
     */
    private data class Frame(
        val locals: Array<JIRRawSimpleValue?>,
        val stack: PersistentList<JIRRawSimpleValue>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Frame

            if (!locals.contentEquals(other.locals)) return false
            if (stack != other.stack) return false

            return true
        }

        override fun hashCode(): Int {
            var result = locals.contentHashCode()
            result = 31 * result + stack.hashCode()
            return result
        }

        fun putLocal(variable: Int, value: JIRRawSimpleValue): Frame {
            val newLocals = locals.copyOf(maxOf(locals.size, variable + 1))
            newLocals[variable] = value
            return copy(locals = newLocals, stack = stack)
        }

        fun hasLocal(variable: Int): Boolean = findLocal(variable) != null

        fun maxLocal(): Int = locals.lastIndex

        fun findLocal(variable: Int): JIRRawSimpleValue? = locals.getOrNull(variable)

        fun getLocal(variable: Int): JIRRawSimpleValue = locals.getOrNull(variable)
            ?: error("No local variable $variable")

        fun push(value: JIRRawSimpleValue) = copy(locals = locals, stack = stack.add(value))
        fun peek() = stack.last()
        fun pop(): Pair<Frame, JIRRawSimpleValue> =
            copy(locals = locals, stack = stack.removeAt(stack.lastIndex)) to stack.last()
    }

    private class FrameBuilder(var currentFrame: Frame)

    private fun FrameBuilder.pop(): JIRRawSimpleValue {
        val (frame, value) = currentFrame.pop()
        currentFrame = frame
        return value
    }

    private fun FrameBuilder.push(value: JIRRawSimpleValue) {
        currentFrame = currentFrame.push(value)
    }

    private fun FrameBuilder.peek(): JIRRawSimpleValue = currentFrame.peek()

    private fun FrameBuilder.local(variable: Int): JIRRawSimpleValue {
        return currentFrame.getLocal(variable)
    }

    private fun Frame.valueUsedInStack(value: JIRRawSimpleValue): Boolean =
        stack.any { it == value }

    private fun Frame.valueUsedInLocals(value: JIRRawSimpleValue, variable: Int): Boolean {
        for (i in locals.indices) {
            if (i == variable) continue
            val localValue = locals[i] ?: continue
            if (localValue == value) return true
        }
        return false
    }

    private fun FrameBuilder.local(
        variable: Int,
        expr: JIRRawSimpleValue,
        insn: AbstractInsnNode,
    ): JIRRawAssignInst? {
        val oldVar = currentFrame.findLocal(variable)?.let {
            val infoFromLocalVars = findLocalVariableWithInstruction(variable, insn)
            val isArg =
                variable < argCounter && infoFromLocalVars != null && infoFromLocalVars.start == firstLabelOrNull
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
            if (oldVar is JIRRawArgument) {
                currentFrame = currentFrame.putLocal(variable, expr)
                null
            } else if (oldVar.typeName == expr.typeName || (expr is JIRRawNullConstant && !oldVar.typeName.isPrimitive)) {
                if (!currentFrame.valueUsedInStack(oldVar) && !currentFrame.valueUsedInLocals(oldVar, variable)) {
                    // optimization: old variable has no other usages. So we can overwrite it
                    JIRRawAssignInst(method, oldVar, expr)
                } else {
                    currentFrame = currentFrame.putLocal(variable, expr)
                    null
                }
            } else {
                val assignment = nextRegisterDeclaredVariable(expr.typeName, variable, insn)
                currentFrame = currentFrame.putLocal(variable, assignment)
                JIRRawAssignInst(method, assignment, expr)
            }
        } else {
            // We have to get type if rhv expression is NULL
            val typeOfNewAssigment =
                if (expr.typeName.typeName == PredefinedPrimitives.Null) {
                    findLocalVariableWithInstruction(variable, insn)
                        ?.desc?.typeNameFromJvmName()
                        ?: currentFrame.findLocal(variable)?.typeName
                        ?: OBJECT_TYPE_NAME
                } else {
                    expr.typeName
                }
            val newLocal = nextRegisterDeclaredVariable(typeOfNewAssigment, variable, insn)
            val result = JIRRawAssignInst(method, newLocal, expr)
            currentFrame = currentFrame.putLocal(variable, newLocal)
            result
        }
    }

    private fun label(insnNode: LabelNode): JIRRawLabelInst = labels[insnNode]
        ?: error("No label for: $insnNode")

    private fun labelRef(insnNode: LabelNode): JIRRawLabelRef = label(insnNode).ref

    private var generatedLabelIndex = 0
    private fun generateFreshLabel(): JIRRawLabelInst {
        return JIRRawLabelInst(method, "#${generatedLabelIndex++}")
    }

    private fun generateFreshLocalVar(typeName: TypeName): JIRRawLocalVar {
        val freshVarIdx = generatedLocalVarsCounter++
        return JIRRawLocalVar(freshVarIdx, "$LOCAL_VAR_START_CHARACTER${freshVarIdx}", typeName)
    }

    private fun nextRegister(typeName: TypeName): JIRRawLocalVar =
        generateFreshLocalVar(typeName)

    private fun instructionList(insn: AbstractInsnNode): MutableList<JIRRawInst> {
        val id = nodeId(insn)

        val insnList = instructionLists[id]
        if (insnList != null) return insnList

        return mutableListOf<JIRRawInst>().also { instructionLists[id] = it }
    }

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

    private fun instructionFrame(insn: AbstractInsnNode): Frame =
        frames[nodeId(insn)] ?: error("No frame for inst")

    private fun nextRegisterDeclaredVariable(
        typeName: TypeName,
        variable: Int,
        insn: AbstractInsnNode
    ): JIRRawSimpleValue {
        val nextLabel = generateSequence(insn) { it.next }
            .filterIsInstance<LabelNode>()
            .firstOrNull()

        val lvNode = methodNode.localVariables
            .singleOrNull { it.index == variable && it.start == nextLabel }

        var nextVar = nextRegister(typeName)

        val lvName = lvNode?.name?.takeIf { keepLocalVariableNames }
        if (lvName != null) {
            nextVar = nextVar.copy(name = lvName)
        }

        val declaredTypeName = lvNode?.desc?.typeNameFromJvmName()
        if (declaredTypeName != null && !declaredTypeName.isPrimitive && !typeName.isArray) {
            nextVar = nextVar.copy(typeName = declaredTypeName)
        }

        return nextVar
    }

    private fun buildGraph() {
        val instructions = methodInstList.toArray()
        instructions.firstOrNull()?.let {
            insnNodeGraph.addPredecessor(it, ENTRY)
        }
        for (insn in instructions) {
            if (insn is LabelNode) {
                labels[insn] = generateFreshLabel()
            }

            if (insn is JumpInsnNode) {
                insnNodeGraph.addPredecessor(insn.label, insn)
                if (insn.opcode != Opcodes.GOTO) {
                    insnNodeGraph.addPredecessor(insn.next, insn)
                }
            } else if (insn is TableSwitchInsnNode) {
                insnNodeGraph.addPredecessor(insn.dflt, insn)
                insn.labels.forEach {
                    insnNodeGraph.addPredecessor(it, insn)
                }
            } else if (insn is LookupSwitchInsnNode) {
                insnNodeGraph.addPredecessor(insn.dflt, insn)
                insn.labels.forEach {
                    insnNodeGraph.addPredecessor(it, insn)
                }
            } else if (insn.isTerminateInst) {
                continue
            } else if (insn.next != null) {
                insnNodeGraph.addPredecessor(insn.next, insn)
            }
        }

        for (tryCatchBlock in methodNode.tryCatchBlocks) {
            val handlers = tryCatchHandlers.getOrPut(tryCatchBlock.handler, ::mutableListOf)

            val blockStart = tryCatchBlock.start
            val blockEnd = tryCatchBlock.end
            val handler = tryCatchBlock.handler

            if (blockStart == handler) {
                continue
            }

            handlers.add(tryCatchBlock)

            var current: AbstractInsnNode = blockStart
            while (current != blockEnd) {
                insnNodeGraph.addPredecessor(handler, current)
                current = current.next ?: error("Unexpected instruction")
            }

            insnNodeGraph.forEachPredecessor(blockStart) { startPredecessor ->
                if (startPredecessor.isBetween(blockStart, blockEnd)) return@forEachPredecessor

                insnNodeGraph.addPredecessor(handler, startPredecessor)
            }
        }

        val deadInstructionIds = insnNodeGraph.computeAndRemoveUnreachableNodes()
        deadInstructionIds.forEach { deadInstructionId ->
            val insn = nodeById(deadInstructionId)

            if (insn is LabelNode) {
                labels.remove(insn)
            }
        }
    }

    private fun IntNodeGraph.addPredecessor(insnNode: AbstractInsnNode, predecessor: AbstractInsnNode) {
        addPredecessor(nodeId(insnNode), nodeId(predecessor))
    }

    private inline fun IntNodeGraph.forEachPredecessor(node: AbstractInsnNode, body: (AbstractInsnNode) -> Unit) {
        forEachPredecessor(nodeId(node)) { predecessorId ->
            body(nodeById(predecessorId))
        }
    }

    private fun createInitialFrame(): Frame {
        var locals = arrayOfNulls<JIRRawSimpleValue>(16)
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

    private fun thisRef() = JIRRawThis(TypeNameImpl.fromTypeName(method.enclosingClass.name))

    private fun buildInsnNode(insn: InsnNode, frame: FrameBuilder) = with(frame) {
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

    private fun FrameBuilder.buildConstant(insn: InsnNode) {
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

    private fun FrameBuilder.buildArrayRead(insn: InsnNode) {
        val index = pop()
        val arrayRef = pop()
        val read = JIRRawArrayAccess(arrayRef, index, arrayRef.typeName.elementType())

        val assignment = nextRegister(read.typeName)
        addInstruction(insn, JIRRawAssignInst(method, assignment, read))
        push(assignment)
    }

    private fun FrameBuilder.buildArrayStore(insn: InsnNode) {
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

    private fun FrameBuilder.buildPop(insn: InsnNode) {
        when (val opcode = insn.opcode) {
            Opcodes.POP -> pop()
            Opcodes.POP2 -> {
                val top = pop()
                if (!top.typeName.isDWord) pop()
            }

            else -> error("Unknown pop opcode: $opcode")
        }
    }

    private fun FrameBuilder.buildDup(insn: InsnNode) {
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

    private fun FrameBuilder.buildSwap() {
        val top = pop()
        val bot = pop()
        push(top)
        push(bot)
    }

    private fun FrameBuilder.buildBinary(insn: InsnNode) {
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
                max.lessThen(PredefinedPrimitives.Int) -> TypeNameImpl.fromTypeName(PredefinedPrimitives.Int)
                else -> TypeNameImpl.fromTypeName(max)
            }
        }
        return left
    }

    private fun FrameBuilder.buildUnary(insn: InsnNode) {
        val operand = pop()
        val expr = when (val opcode = insn.opcode) {
            in Opcodes.INEG..Opcodes.DNEG -> {
                val resolvedType = maxOfPrimitiveTypes(operand.typeName.typeName, PredefinedPrimitives.Int)
                JIRRawNegExpr(TypeNameImpl.fromTypeName(resolvedType), operand)
            }

            Opcodes.ARRAYLENGTH -> JIRRawLengthExpr(PredefinedPrimitives.Int.typeName(), operand)
            else -> error("Unknown unary opcode $opcode")
        }
        val assignment = nextRegister(expr.typeName)
        addInstruction(insn, JIRRawAssignInst(method, assignment, expr))
        push(assignment)
    }

    private fun FrameBuilder.buildCast(insn: InsnNode) {
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

    private fun FrameBuilder.buildCmp(insn: InsnNode) {
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

    private fun FrameBuilder.buildReturn(insn: InsnNode) {
        addInstruction(
            insn, when (val opcode = insn.opcode) {
                Opcodes.RETURN -> JIRRawReturnInst(method, null)
                in Opcodes.IRETURN..Opcodes.ARETURN -> JIRRawReturnInst(method, pop())
                else -> error("Unknown return opcode: $opcode")
            }
        )
    }

    private fun FrameBuilder.buildMonitor(insn: InsnNode) {
        val monitor = pop()
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

    private fun FrameBuilder.buildThrow(insn: InsnNode) {
        val throwable = pop()
        addInstruction(insn, JIRRawThrowInst(method, throwable))
    }

    private fun buildFieldInsnNode(insnNode: FieldInsnNode, frame: FrameBuilder) {
        val fieldName = insnNode.name
        val fieldType = insnNode.desc.typeNameFromJvmName()
        val declaringClass = insnNode.owner.typeNameFromAsmInternalName()
        when (insnNode.opcode) {
            Opcodes.GETFIELD -> {
                val assignment = nextRegister(fieldType)
                val field = JIRRawFieldRef(frame.pop(), declaringClass, fieldName, fieldType)
                addInstruction(insnNode, JIRRawAssignInst(method, assignment, field))
                frame.push(assignment)
            }

            Opcodes.PUTFIELD -> {
                val value = frame.pop()
                val instance = frame.pop()
                val fieldRef = JIRRawFieldRef(instance, declaringClass, fieldName, fieldType)
                addInstruction(insnNode, JIRRawAssignInst(method, fieldRef, value))
            }

            Opcodes.GFrontendTATIC -> {
                val assignment = nextRegister(fieldType)
                val field = JIRRawFieldRef(declaringClass, fieldName, fieldType)
                addInstruction(insnNode, JIRRawAssignInst(method, assignment, field))
                frame.push(assignment)
            }

            Opcodes.PUTSTATIC -> {
                val value = frame.pop()
                val fieldRef = JIRRawFieldRef(declaringClass, fieldName, fieldType)
                addInstruction(insnNode, JIRRawAssignInst(method, fieldRef, value))
            }
        }
    }

    private val firstLabelOrNull: AbstractInsnNode? get() = methodInstList.firstOrNull { it is LabelNode }

    private fun buildFrameNode(insnNode: FrameNode): Frame {
        val lastFrameState = when (insnNode.type) {
            Opcodes.F_NEW -> FrameState.parseNew(insnNode)
            Opcodes.F_FULL -> FrameState.parseNew(insnNode)

            // todo: complex frame nodes
            Opcodes.F_APPEND,
            Opcodes.F_CHOP,
            Opcodes.F_SAME,
            Opcodes.F_SAME1 -> null

            else -> error("Unknown frame node type: ${insnNode.type}")
        }

        val predecessorId = insnNodeGraph.singlePredecessor(nodeId(insnNode))

        val predecessorFrame = frames[predecessorId]
            ?: error("Incorrect frame processing order")

        if (lastFrameState == null) {
            return predecessorFrame
        }

        return refineWithFrameState(predecessorFrame, lastFrameState)
    }

    private fun buildIincInsnNode(insnNode: IincInsnNode, frame: FrameBuilder) = with(frame) {
        val variable = insnNode.`var`
        val local = local(variable)
        val rhv = JIRRawInt(insnNode.incr)

        val resolvedType = resolveType(local.typeName, rhv.typeName)
        val expr = JIRRawAddExpr(resolvedType, local, rhv)
        val assignment = nextRegister(resolvedType)
        addInstruction(insnNode, JIRRawAssignInst(method, assignment, expr))

        val localAssign = local(variable, assignment, insnNode)
        if (localAssign != null) {
            addInstruction(insnNode, localAssign)
        }
    }

    private fun buildIntInsnNode(insnNode: IntInsnNode, frame: FrameBuilder) = with(frame) {
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
            parseBsmHandleTag(tag),
            owner.typeNameFromAsmInternalName(),
            name,
            if (desc.contains("(")) {
                Type.getArgumentTypes(desc).map { it.descriptor.typeNameFromJvmName() }
            } else {
                listOf()
            },
            if (desc.contains("(")) {
                Type.getReturnType(desc).descriptor.typeNameFromJvmName()
            } else {
                Type.getReturnType("(;)$desc").descriptor.typeNameFromJvmName()
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

    private fun buildInvokeDynamicInsn(insnNode: InvokeDynamicInsnNode, frame: FrameBuilder) = with(frame) {
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
            Type.getArgumentTypes(desc).map { it.descriptor.typeNameFromJvmName() },
            Type.getReturnType(desc).descriptor.typeNameFromJvmName(),
            args,
        )
        if (Type.getReturnType(desc) == Type.VOID_TYPE) {
            addInstruction(insnNode, JIRRawCallInst(method, expr))
        } else {
            val result = nextRegister(Type.getReturnType(desc).descriptor.typeNameFromJvmName())
            addInstruction(insnNode, JIRRawAssignInst(method, result, expr))
            push(result)
        }
    }

    private fun buildJumpInsnNode(insnNode: JumpInsnNode, frame: FrameBuilder) = with(frame) {
        val target = labelRef(insnNode.label)
        when (val opcode = insnNode.opcode) {
            Opcodes.GOTO -> addInstruction(insnNode, JIRRawGotoInst(method, target))
            else -> {
                val falseTarget = (insnNode.next as? LabelNode)?.let { label(it) } ?: generateFreshLabel()
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

                addInstruction(insnNode, JIRRawIfInst(method, expr, target, falseTarget.ref))
                if (insnNode.next !is LabelNode) {
                    addInstruction(insnNode, falseTarget)
                }
            }
        }
    }

    private fun mergeFrames(frames: List<Pair<AbstractInsnNode, Frame?>>, curInsn: LabelNode): Frame {
        val frameSet = frames.mapNotNull { it.second }
        val maxLocalVar = frameSet.minOf { it.maxLocal() }
        val maxStackIndex = frameSet.minOf { it.stack.lastIndex }

        val localTypes = Array(maxLocalVar + 1) { local ->
            resolveFrameVariableType(frameSet, local, curInsn)
        }

        val stackTypes = List(maxStackIndex + 1) {
            resolveStackVariableType(frameSet, it)
        }

        if (frameSet.size == frames.size) {
            @Suppress("UNCHECKED_CAST")
            return mergeWithPresentFrames(frames as List<Pair<AbstractInsnNode, Frame>>, curInsn, localTypes, stackTypes)
        } else {
            return mergeWithMissedFrames(curInsn, localTypes, stackTypes)
        }
    }

    private fun mergeWithMissedFrames(
        curNode: LabelNode,
        localTypes: Array<TypeName?>,
        stackTypes: List<TypeName>,
    ): Frame {
        val localMergeAssignments = LinkedHashMap<Int, JIRRawSimpleValue>()
        val stackMergeAssignments = LinkedHashMap<Int, JIRRawSimpleValue>()

        val mergedStack = stackTypes.mapIndexed { index, type ->
            nextRegister(type).also { stackMergeAssignments[index] = it }
        }

        val mergedLocals = Array<JIRRawSimpleValue?>(localTypes.size) { variable ->
            val type = localTypes[variable]
            if (type == null || type == TOP) {
                return@Array null
            }

            nextRegister(type).also { localMergeAssignments[variable] = it }
        }

        if (stackMergeAssignments.isNotEmpty()) {
            this.stackMergeAssignments.add(LaterAssignments(curNode, stackMergeAssignments))
        }

        if (localMergeAssignments.isNotEmpty()) {
            this.localMergeAssignments.add(LaterAssignments(curNode, localMergeAssignments))
        }

        return Frame(mergedLocals.trimEndNulls(), mergedStack.toPersistentList())
    }

    private fun mergeWithPresentFrames(
        frames: List<Pair<AbstractInsnNode, Frame>>,
        curNode: LabelNode,
        localTypes: Array<TypeName?>,
        stackTypes: List<TypeName>,
    ): Frame {
        val localMergeAssignments = LinkedHashMap<Int, JIRRawSimpleValue>()
        val stackMergeAssignments = LinkedHashMap<Int, JIRRawSimpleValue>()

        val mergedStack = stackTypes.mapIndexed { index, type ->
            val allFramesSameValue = framesStackSameValue(frames, index)
            if (allFramesSameValue != null) {
                stackMergeAssignments.remove(index)
                return@mapIndexed allFramesSameValue
            }

            nextRegister(type).also { stackMergeAssignments[index] = it }
        }

        val mergedLocals = Array(localTypes.size) { variable ->
            val type = localTypes[variable]
            if (type == null || type == TOP) {
                localMergeAssignments.remove(variable)
                return@Array null
            }

            val allFramesSameValue = framesVariableSameValue(frames, variable)
            if (allFramesSameValue != null) {
                localMergeAssignments.remove(variable)
                return@Array allFramesSameValue
            }

            nextRegister(type).also { localMergeAssignments[variable] = it }
        }

        if (stackMergeAssignments.isNotEmpty()) {
            this.stackMergeAssignments.add(LaterAssignments(curNode, stackMergeAssignments))
        }

        if (localMergeAssignments.isNotEmpty()) {
            this.localMergeAssignments.add(LaterAssignments(curNode, localMergeAssignments))
        }

        return Frame(mergedLocals.trimEndNulls(), mergedStack.toPersistentList())
    }

    private fun resolveStackVariableType(frames: Iterable<Frame>, stackIndex: Int): TypeName {
        var type: TypeName? = null
        for (frame in frames) {
            val frameType = frame.stack[stackIndex].typeName

            if (type == null) {
                type = frameType
                continue
            }

            type = typeLub(type, frameType)
        }

        check(type != null && type != TOP) {
            "Incorrect stack types"
        }

        return type
    }

    private fun resolveFrameVariableType(frames: Iterable<Frame>, variable: Int, curLabel: LabelNode): TypeName? {
        var type: TypeName? = null
        for (frame in frames) {
            if (!frame.hasLocal(variable)) return null

            val frameType = frame.getLocal(variable).typeName
            if (type == null) {
                type = frameType
                continue
            }

            type = typeLub(type, frameType)
        }

        if (type == TOP) return TOP

        // If we have several variables types for one register we have to search right type in debug info otherwise we cannot guarantee anything
        val debugType = findLocalVariableWithInstruction(variable, curLabel)
                ?.let { Type.getType(it.desc) }
                ?.descriptor?.typeNameFromJvmName()

        if (debugType != null) return debugType

        return type ?: error("No type")
    }

    private fun framesVariableSameValue(frames: Iterable<Pair<AbstractInsnNode, Frame>>, variable: Int): JIRRawSimpleValue? =
        frames.sameOrNull { second.getLocal(variable) }

    private fun framesStackSameValue(frames: Iterable<Pair<AbstractInsnNode, Frame>>, index: Int): JIRRawSimpleValue? =
        frames.sameOrNull { second.stack[index] }

    private inline fun <R : Any, T> Iterable<T>.sameOrNull(getter: T.() -> R): R? {
        var result: R? = null
        for (element in this) {
            val elementValue = getter(element)
            if (result == null) {
                result = elementValue
                continue
            }

            if (elementValue != result) return null
        }
        return result
    }

    private fun buildLabelNode(insnNode: LabelNode): Frame {
        val labelInst = label(insnNode)
        addInstruction(insnNode, labelInst)

        val predecessors = mutableListOf<AbstractInsnNode>()
        insnNodeGraph.forEachPredecessor(insnNode) { predecessors.add(it) }

        val predecessorFrames = predecessors.map { frames[nodeId(it)] }

        var throwable: JIRRawLocalVar? = null

        val catchEntries = tryCatchHandlers[insnNode].orEmpty()
        if (catchEntries.isNotEmpty()) {
            throwable = nextRegister(catchEntries.commonTypeOrDefault)

            val entries = catchEntries.mapIndexed { index, node ->
                buildCatchEntry(node)
            }

            val catchInst = JIRRawCatchInst(
                method,
                throwable,
                labelRef(insnNode),
                entries
            )

            addInstruction(insnNode, catchInst)
        }

        val singleFrame = predecessorFrames.singleOrNull()
        var currentFrame = if (singleFrame != null) {
            singleFrame
        } else {
            mergeFrames(predecessors.zip(predecessorFrames), insnNode)
        }

        if (throwable != null) {
            currentFrame = currentFrame.push(throwable)
        }

        return currentFrame
    }

    private fun buildCatchEntry(node: TryCatchBlockNode): JIRRawCatchEntry {
        var startLabel = labels[node.start]
        if (startLabel == null) {
            startLabel = generateFreshLabel()
            instructionList(node.start).add(startLabel)
        }

        var endLabel = labels[node.end]
        if (endLabel == null) {
            endLabel = generateFreshLabel()
            instructionList(node.end).add(endLabel)
        }

        return JIRRawCatchEntry(node.typeOrDefault, startLabel.ref, endLabel.ref)
    }

    private fun buildLineNumberNode(insnNode: LineNumberNode) =
        addInstruction(insnNode, JIRRawLineNumberInst(method, insnNode.line, labelRef(insnNode.start)))

    private fun ldcValue(cst: Any): JIRRawSimpleValue {
        return when (cst) {
            is Int -> JIRRawInt(cst)
            is Float -> JIRRawFloat(cst)
            is Double -> JIRRawDouble(cst)
            is Long -> JIRRawLong(cst)
            is String -> JIRRawStringConstant(cst, STRING_CLASS.typeNameFromJvmName())
            is Type -> JIRRawClassConstant(cst.descriptor.typeNameFromJvmName(), CLASS_CLASS.typeNameFromJvmName())
            is Handle -> {
                JIRRawMethodConstant(
                    cst.owner.typeNameFromAsmInternalName(),
                    cst.name,
                    Type.getArgumentTypes(cst.desc).map { it.descriptor.typeNameFromJvmName() },
                    Type.getReturnType(cst.desc).descriptor.typeNameFromJvmName(),
                    METHOD_HANDLE_CLASS.typeNameFromJvmName()
                )
            }

            else -> error("Can't convert LDC value: $cst of type ${cst::class.java.name}")
        }
    }

    private fun buildLdcInsnNode(insnNode: LdcInsnNode, frame: FrameBuilder) = with(frame) {
        when (val cst = insnNode.cst) {
            is Int -> push(ldcValue(cst))
            is Float -> push(ldcValue(cst))
            is Double -> push(ldcValue(cst))
            is Long -> push(ldcValue(cst))
            is String -> push(JIRRawStringConstant(cst, STRING_CLASS.typeNameFromJvmName()))
            is Type -> {
                val assignment = nextRegister(CLASS_CLASS.typeNameFromJvmName())
                addInstruction(
                    insnNode, JIRRawAssignInst(
                        method,
                        assignment,
                        when (cst.sort) {
                            Type.METHOD -> JIRRawMethodType(
                                cst.argumentTypes.map { it.descriptor.typeNameFromJvmName() },
                                cst.returnType.descriptor.typeNameFromJvmName(),
                                METHOD_TYPE_CLASS.typeNameFromJvmName()
                            )

                            else -> ldcValue(cst)
                        }
                    )
                )
                push(assignment)
            }

            is Handle -> {
                val assignment = nextRegister(CLASS_CLASS.typeNameFromJvmName())
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
                val assignment = nextRegister(CLASS_CLASS.typeNameFromJvmName())
                val exprs = arrayListOf<JIRRawValue>()
                repeat(cst.bootstrapMethodArgumentCount) {
                    exprs.add(
                        ldcValue(cst.getBootstrapMethodArgument(it - 1))
                    )
                }
                val methodCall: JIRRawCallExpr = when (cst.bootstrapMethod.tag) {
                    Opcodes.INVOKESPECIAL -> JIRRawSpecialCallExpr(
                        methodHande.owner.typeNameFromAsmInternalName(),
                        cst.name,
                        Type.getArgumentTypes(methodHande.desc).map { it.descriptor.typeNameFromJvmName() },
                        Type.getReturnType(methodHande.desc).descriptor.typeNameFromJvmName(),
                        thisRef(),
                        exprs
                    )

                    else -> {
                        val lookupAssignment = nextRegister(METHOD_HANDLES_LOOKUP_CLASS.typeNameFromJvmName())
                        addInstruction(
                            insnNode, JIRRawAssignInst(
                                method,
                                lookupAssignment,
                                JIRRawStaticCallExpr(
                                    METHOD_HANDLES_CLASS.typeNameFromJvmName(),
                                    "lookup",
                                    emptyList(),
                                    METHOD_HANDLES_LOOKUP_CLASS.typeNameFromJvmName(),
                                    emptyList()
                                )
                            )
                        )
                        JIRRawStaticCallExpr(
                            methodHande.owner.typeNameFromAsmInternalName(),
                            methodHande.name,
                            Type.getArgumentTypes(methodHande.desc).map { it.descriptor.typeNameFromJvmName() },
                            Type.getReturnType(methodHande.desc).descriptor.typeNameFromJvmName(),
                            listOf(
                                lookupAssignment,
                                JIRRawStringConstant(cst.name, STRING_CLASS.typeNameFromJvmName()),
                                JIRRawClassConstant(cst.descriptor.typeNameFromJvmName(), CLASS_CLASS.typeNameFromJvmName())
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

    private fun buildLookupSwitchInsnNode(insnNode: LookupSwitchInsnNode, frame: FrameBuilder) = with(frame) {
        val key = pop()
        val default = labelRef(insnNode.dflt)
        val branches = insnNode.keys
            .zip(insnNode.labels)
            .associate { (JIRRawInt(it.first) as JIRRawValue) to labelRef(it.second) }
        addInstruction(insnNode, JIRRawSwitchInst(method, key, branches, default))
    }

    private fun buildMethodInsnNode(insnNode: MethodInsnNode, frame: FrameBuilder) = with(frame) {
        val ownerTypeName = insnNode.owner.typeNameFromAsmInternalName()
        val owner = when {
            ownerTypeName.isArray -> OBJECT_TYPE_NAME
            else -> ownerTypeName
        }
        val methodName = insnNode.name
        val argTypes = Type.getArgumentTypes(insnNode.desc).map { it.descriptor.typeNameFromJvmName() }
        val returnType = Type.getReturnType(insnNode.desc).descriptor.typeNameFromJvmName()

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
            val result = nextRegister(Type.getReturnType(insnNode.desc).descriptor.typeNameFromJvmName())
            addInstruction(insnNode, JIRRawAssignInst(method, result, expr))
            push(result)
        }
    }

    private fun buildMultiANewArrayInsnNode(insnNode: MultiANewArrayInsnNode, frame: FrameBuilder) = with(frame) {
        val dimensions = mutableListOf<JIRRawValue>()
        repeat(insnNode.dims) {
            dimensions += pop()
        }
        val expr = JIRRawNewArrayExpr(insnNode.desc.typeNameFromJvmName(), dimensions.reversed())
        val assignment = nextRegister(expr.typeName)
        addInstruction(insnNode, JIRRawAssignInst(method, assignment, expr))
        push(assignment)
    }

    private fun buildTableSwitchInsnNode(insnNode: TableSwitchInsnNode, frame: FrameBuilder) = with(frame) {
        val index = pop()
        val default = labelRef(insnNode.dflt)
        val branches = (insnNode.min..insnNode.max)
            .zip(insnNode.labels)
            .associate { (JIRRawInt(it.first) as JIRRawValue) to labelRef(it.second) }
        addInstruction(insnNode, JIRRawSwitchInst(method, index, branches, default))
    }

    private fun buildTypeInsnNode(insnNode: TypeInsnNode, frame: FrameBuilder) = with(frame) {
        val type = insnNode.desc.typeNameFromAsmInternalName()
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

    private fun buildVarInsnNode(insnNode: VarInsnNode, frame: FrameBuilder) = with(frame) {
        val variable = insnNode.`var`
        when (insnNode.opcode) {
            in Opcodes.ISTORE..Opcodes.ASTORE -> {
                val inst = local(variable, pop(), insnNode)
                if (inst != null) {
                    addInstruction(insnNode, inst)
                }
            }

            in Opcodes.ILOAD..Opcodes.ALOAD -> {
                push(local(variable))
            }

            else -> error("Unknown opcode ${insnNode.opcode} in VarInsnNode")
        }
    }

    private fun findLocalVariableWithInstruction(variable: Int, insn: AbstractInsnNode): LocalVariableNode? =
        methodNode.localVariables.find { it.index == variable && insn.isBetween(it.start, it.end) }

    private fun AbstractInsnNode.isBetween(labelStart: AbstractInsnNode, labelEnd: AbstractInsnNode): Boolean =
        this.index in labelStart.index..labelEnd.index

    private val AbstractInsnNode.index: Int
        get() = methodInstList.indexOf(this)

    companion object {
        private const val ENTRY_NODE_ID = 0
        private const val ENTRY_NODE_OP = -1
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

private inline fun BitSet.forEach(body: (Int) -> Unit) {
    var node = nextSetBit(0)
    while (node >= 0) {
        body(node)
        node = nextSetBit(node + 1)
    }
}

private class IntNodeGraph(val entryNode: Int, val nodeCount: Int) {
    val predecessors = arrayOfNulls<IntSet?>(nodeCount)

    fun addPredecessor(node: Int, predecessor: Int) {
        val nodePredecessors = predecessors[node]

        if (nodePredecessors == null) {
            predecessors[node] = IntSet.Singleton(predecessor)
            return
        }

        predecessors[node] = nodePredecessors.add(predecessor)
    }

    fun hasMultiplePredecessors(node: Int): Boolean =
        predecessors[node].let { it != null && it.size > 1 }

    fun singlePredecessor(node: Int): Int =
        predecessors[node]?.singleOrNull()
            ?: error("No single predecessor for node: $node")

    inline fun forEachPredecessor(node: Int, body: (Int) -> Unit) {
        predecessors[node]?.forEach { nodePredecessor ->
            body(nodePredecessor)
        }
    }

    fun computeAndRemoveUnreachableNodes(): BitSet {
        val deadNodes = BitSet(nodeCount)
        for (node in predecessors.indices) {
            if (node == entryNode) continue

            val preds = predecessors[node]
            if (preds == null || preds.all { deadNodes.get(it) }) {
                deadNodes.set(node)
                predecessors[node] = null
            }
        }

        if (deadNodes.isEmpty) return deadNodes

        for (node in predecessors.indices) {
            val preds = predecessors[node] ?: continue
            predecessors[node] = preds.removeAll(deadNodes)
        }

        return deadNodes
    }

    fun findStartNodes(nodeSet: BitSet): BitSet {
        val result = nodeSet.clone() as BitSet
        nodeSet.forEach { node ->
            val predecessors = predecessors[node]
            if (predecessors != null && predecessors.all { nodeSet.get(it) }) {
                result.clear(node)
            }
        }
        return result
    }

    private fun buildSuccessorsMap(): Array<IntSet?> {
        val successors = arrayOfNulls<IntSet?>(nodeCount)
        for (node in predecessors.indices) {
            val nodePredecessors = predecessors[node] ?: continue
            nodePredecessors.forEach { predecessor ->
                val predecessorSuccessors = successors[predecessor]
                if (predecessorSuccessors == null) {
                    successors[predecessor] = IntSet.Singleton(node)
                    return@forEach
                }

                successors[predecessor] = predecessorSuccessors.add(node)
            }
        }
        return successors
    }

    fun topSortNodesWithoutBackEdges(): IntArray {
        val successors = buildSuccessorsMap()
        val sorter = GraphTopSorter(successors)
        return sorter.graphInTopOrder(entryNode)
    }

    inline fun forEachNodeIdInTopOrderWithoutBackEdges(body: (Int) -> Unit) {
        val sortedNodes = topSortNodesWithoutBackEdges()
        for (node in sortedNodes) {
            if (node == GraphTopSorter.END_NODE) break
            body(node)
        }
    }
}

private sealed class IntSet(val value: Int) {
    abstract val size: Int

    abstract fun add(element: Int): IntSet

    abstract fun remove(element: Int): IntSet?

    abstract fun toSet(): Set<Int>

    override fun toString(): String = toSet().toString()

    class Singleton(value: Int) : IntSet(value) {
        override val size: Int get() = 1

        override fun add(element: Int): IntSet {
            if (element == value) return this
            return Multiple(minOf(value, element), BitSet()).add(value).add(element)
        }

        override fun remove(element: Int): IntSet? {
            if (element == value) return null
            return this
        }

        override fun toSet(): Set<Int> = setOf(value)
    }

    class Multiple(base: Int, val offsets: BitSet) : IntSet(base) {
        override val size: Int get() = offsets.cardinality()

        override fun add(element: Int): IntSet {
            val base = value

            val offset = element - base
            if (offset >= 0) {
                offsets.set(offset)
                return this
            }

            val result = Multiple(element, BitSet())
            result.add(element)
            offsets.forEach { result.add(base + it) }
            return result
        }

        override fun remove(element: Int): IntSet {
            val base = value

            val offset = element - base
            if (offset < 0) return this

            if (!offsets.get(offset)) return this

            offsets.clear(offset)

            if (offsets.cardinality() > 1) return this

            return Singleton(base + offsets.nextSetBit(0))
        }

        override fun toSet(): Set<Int> {
            val set = mutableSetOf<Int>()
            offsets.forEach { set.add(value + it) }
            return set
        }
    }
}

private fun IntSet.singleOrNull(): Int? = (this as? IntSet.Singleton)?.value

private inline fun IntSet.forEach(body: (Int) -> Unit) {
    when (this) {
        is IntSet.Singleton -> body(value)
        is IntSet.Multiple -> {
            val base = value
            offsets.forEach { offset ->
                body(base + offset)
            }
        }
    }
}

private inline fun IntSet.all(predicate: (Int) -> Boolean): Boolean {
    forEach { if (!predicate(it)) return false }
    return true
}

private fun IntSet.removeAll(values: BitSet): IntSet? {
    var result = this
    values.forEach { result = result.remove(it) ?: return null }
    return result
}

private class GraphTopSorter(val successors: Array<IntSet?>) {
    private val startTime = IntArray(successors.size)
    private val endTime = IntArray(successors.size)

    fun graphInTopOrder(startNode: Int): IntArray {
        findBackEdges(startNode)
        return createTopSortFromStart(startNode)
    }

    private data class FinderAction(val node: Int, val isForward: Boolean)

    private fun findBackEdges(startNode: Int) {
        var time = 0
        val visited = BitSet(successors.size)

        val unprocessed = mutableListOf(FinderAction(startNode, isForward = true))

        while (unprocessed.isNotEmpty()) {
            val action = unprocessed.removeLast()

            if (!action.isForward) {
                endTime[action.node] = time++
                continue
            }

            if (visited.get(action.node)) continue
            visited.set(action.node)

            val node = action.node
            startTime[node] = time++
            unprocessed.add(FinderAction(node, isForward = false))

            successors[node]?.forEach { successor ->
                if (!visited.get(successor)) {
                    unprocessed.add(FinderAction(successor, isForward = true))
                }
            }
        }
    }

    private fun isBackEdge(edgeFrom: Int, edgeTo: Int): Boolean {
        if (edgeFrom == edgeTo) return true
        return startTime[edgeFrom] > startTime[edgeTo] && endTime[edgeFrom] < endTime[edgeTo]
    }

    private fun createTopSortFromStart(startNode: Int): IntArray {
        var resultPos = 0
        val result = IntArray(successors.size)

        val currentNodeDegree = IntArray(successors.size)
        for (node in successors.indices) {
            successors[node]?.forEach { successor ->
                if (isBackEdge(node, successor)) return@forEach

                currentNodeDegree[successor]++
            }
        }

        check(currentNodeDegree[startNode] == 0)

        val unprocessed = mutableListOf<Int>()
        unprocessed.add(startNode)

        while (unprocessed.isNotEmpty()) {
            val node = unprocessed.removeLast()
            result[resultPos++] = node

            successors[node]?.forEach { successor ->
                if (isBackEdge(node, successor)) return@forEach

                if (--currentNodeDegree[successor] == 0) {
                    unprocessed.add(successor)
                }
            }
        }

        if (resultPos != result.size) {
            result[resultPos] = END_NODE
        }

        return result
    }

    companion object {
        const val END_NODE = -1
    }
}
