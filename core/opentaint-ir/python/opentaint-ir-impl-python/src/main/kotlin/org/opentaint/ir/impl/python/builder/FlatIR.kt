package org.opentaint.ir.impl.python.builder

import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.api.python.PIRField

// ─── Values ───────────────────────────────────────────

sealed interface FlatValue

data class FlatLocal(val name: String, val type: FlatType = FlatAnyType) : FlatValue
data class FlatGlobalRef(val name: String, val module: String) : FlatValue
data class FlatParameterRef(val name: String) : FlatValue

sealed interface FlatConst : FlatValue
data class FlatIntConst(val value: Long) : FlatConst
data class FlatFloatConst(val value: Double) : FlatConst
data class FlatStrConst(val value: String) : FlatConst
data class FlatBoolConst(val value: Boolean) : FlatConst
data object FlatNoneConst : FlatConst
data object FlatEllipsisConst : FlatConst
data class FlatBytesConst(val value: ByteArray) : FlatConst {
    override fun equals(other: Any?) = this === other || (other is FlatBytesConst && value.contentEquals(other.value))
    override fun hashCode() = value.contentHashCode()
}
data class FlatComplexConst(val real: Double, val imag: Double) : FlatConst

// ─── Types ────────────────���───────────────────────────

sealed interface FlatType
data object FlatAnyType : FlatType
data object FlatNeverType : FlatType
data object FlatNoneType : FlatType
data class FlatClassType(
    val qualifiedName: String,
    val typeArgs: List<FlatType> = emptyList(),
    val isOptional: Boolean = false,
) : FlatType
data class FlatFunctionType(val paramTypes: List<FlatType>, val returnType: FlatType) : FlatType
data class FlatUnionType(val members: List<FlatType>) : FlatType
data class FlatTupleType(val elementTypes: List<FlatType>, val isVarLength: Boolean) : FlatType
data class FlatLiteralType(val value: String, val baseType: FlatType) : FlatType
data class FlatTypeVarType(val name: String, val bounds: List<FlatType>) : FlatType

// ─── Instructions ─────────────────────────────────────

sealed interface FlatInst {
    val line: Int
}

data class FlatAssign(val target: FlatValue, val source: FlatValue, override val line: Int = -1) : FlatInst
data class FlatLoadAttr(val target: FlatValue, val obj: FlatValue, val attribute: String, val type: FlatType = FlatAnyType, override val line: Int = -1) : FlatInst
data class FlatStoreAttr(val obj: FlatValue, val attribute: String, val value: FlatValue, override val line: Int = -1) : FlatInst
data class FlatLoadSubscript(val target: FlatValue, val obj: FlatValue, val index: FlatValue, val type: FlatType = FlatAnyType, override val line: Int = -1) : FlatInst
data class FlatStoreSubscript(val obj: FlatValue, val index: FlatValue, val value: FlatValue, override val line: Int = -1) : FlatInst
data class FlatLoadGlobal(val target: FlatValue, val name: String, val module: String, override val line: Int = -1) : FlatInst
data class FlatStoreGlobal(val name: String, val module: String, val value: FlatValue, override val line: Int = -1) : FlatInst
data class FlatLoadClosure(val target: FlatValue, val name: String, val depth: Int, override val line: Int = -1) : FlatInst
data class FlatStoreClosure(val name: String, val depth: Int, val value: FlatValue, override val line: Int = -1) : FlatInst

data class FlatBinOp(val target: FlatValue, val left: FlatValue, val right: FlatValue, val op: FlatBinaryOperator, override val line: Int = -1) : FlatInst
data class FlatUnaryOp(val target: FlatValue, val operand: FlatValue, val op: FlatUnaryOperator, override val line: Int = -1) : FlatInst
data class FlatCompare(val target: FlatValue, val left: FlatValue, val right: FlatValue, val op: FlatCompareOperator, override val line: Int = -1) : FlatInst

data class FlatCall(val target: FlatValue?, val callee: FlatValue, val args: List<FlatCallArg> = emptyList(), val resolvedCallee: String = "", override val line: Int = -1) : FlatInst
data class FlatCallArg(val value: FlatValue, val kind: FlatArgKind = FlatArgKind.POSITIONAL, val keyword: String = "")

data class FlatBuildList(val target: FlatValue, val elements: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst
data class FlatBuildTuple(val target: FlatValue, val elements: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst
data class FlatBuildSet(val target: FlatValue, val elements: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst
data class FlatBuildDict(val target: FlatValue, val keys: List<FlatValue> = emptyList(), val values: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst
data class FlatBuildSlice(val target: FlatValue, val lower: FlatValue?, val upper: FlatValue?, val step: FlatValue?, override val line: Int = -1) : FlatInst
data class FlatBuildString(val target: FlatValue, val parts: List<FlatValue>, override val line: Int = -1) : FlatInst

data class FlatGetIter(val target: FlatValue, val iterable: FlatValue, override val line: Int = -1) : FlatInst
data class FlatNextIter(val target: FlatValue, val iterator: FlatValue, val bodyBlock: Int, val exitBlock: Int, override val line: Int = -1) : FlatInst
data class FlatUnpack(val targets: List<FlatValue>, val source: FlatValue, val starIndex: Int, override val line: Int = -1) : FlatInst

data class FlatGoto(val targetBlock: Int, override val line: Int = -1) : FlatInst
data class FlatBranch(val condition: FlatValue, val trueBlock: Int, val falseBlock: Int, override val line: Int = -1) : FlatInst
data class FlatReturn(val value: FlatValue?, override val line: Int = -1) : FlatInst
data class FlatRaise(val exception: FlatValue?, val cause: FlatValue?, override val line: Int = -1) : FlatInst
data class FlatExceptHandler(val target: FlatValue?, val exceptionTypes: List<FlatType>, override val line: Int = -1) : FlatInst

data class FlatYield(val target: FlatValue?, val value: FlatValue?, override val line: Int = -1) : FlatInst
data class FlatYieldFrom(val target: FlatValue?, val iterable: FlatValue, override val line: Int = -1) : FlatInst
data class FlatAwait(val target: FlatValue?, val awaitable: FlatValue, override val line: Int = -1) : FlatInst

data class FlatDeleteLocal(val local: FlatValue, override val line: Int = -1) : FlatInst
data class FlatDeleteAttr(val obj: FlatValue, val attribute: String, override val line: Int = -1) : FlatInst
data class FlatDeleteSubscript(val obj: FlatValue, val index: FlatValue, override val line: Int = -1) : FlatInst
data class FlatDeleteGlobal(val name: String, val module: String, override val line: Int = -1) : FlatInst

data class FlatTypeCheck(val target: FlatValue, val value: FlatValue, val type: FlatType, override val line: Int = -1) : FlatInst
data object FlatUnreachable : FlatInst {
    override val line: Int = -1
}

// ─── Enums ────────────────────────────────────────────

enum class FlatBinaryOperator { ADD, SUB, MUL, DIV, FLOOR_DIV, MOD, POW, MAT_MUL, BIT_AND, BIT_OR, BIT_XOR, LSHIFT, RSHIFT }
enum class FlatUnaryOperator { NEG, POS, NOT, INVERT }
enum class FlatCompareOperator { EQ, NE, LT, LE, GT, GE, IS, IS_NOT, IN, NOT_IN }
enum class FlatArgKind { POSITIONAL, KEYWORD, STAR, DOUBLE_STAR }

// ─── Block / CFG ──────────────────────────────────────

data class FlatBlock(val label: Int, val instructions: List<FlatInst>, val exceptionHandlers: List<Int>)
data class FlatCFG(val blocks: List<FlatBlock>, val entryBlock: Int, val exitBlocks: List<Int>) {
    companion object {
        val EMPTY = FlatCFG(
            blocks = listOf(FlatBlock(0, listOf(FlatReturn(null)), emptyList())),
            entryBlock = 0,
            exitBlocks = listOf(0),
        )
    }
}

// ─── Functions / Modules ──────────────────────────────

enum class FlatFunctionKind {
    MODULE_INIT,
    TOP_LEVEL,
    METHOD,
    NESTED_DEF,
    LAMBDA,
}

/**
 * A single lexical function-like scope in raw Flat IR: top-level functions,
 * methods, nested defs, lambdas, and the synthetic module initializer.
 *
 * First-pass lowering produces these without any closure semantics. Later
 * transforms (e.g. ClosureLoweringTransform) rewrite them into
 * closure-lowered FlatFunctionIRs.
 *
 * `nonlocalNames` / `globalNames` capture function-wide `nonlocal` / `global`
 * declarations so later passes don't need to re-walk the mypy AST.
 * `closureVars` is legacy metadata produced by the first pass today; it will
 * be replaced by analyzer-computed facts in phases 3–4.
 */
data class FlatFunctionIR(
    val name: String,
    val qualifiedName: String,
    val parentQualifiedName: String?,
    val kind: FlatFunctionKind,
    val cfg: FlatCFG,
    val parameters: List<FlatParameter>,
    val returnType: FlatType,
    val isAsync: Boolean,
    val isGenerator: Boolean,
    val closureVars: List<String>,
    val decorators: List<FlatDecorator>,
    val nonlocalNames: Set<String> = emptySet(),
    val globalNames: Set<String> = emptySet(),
) {
    val isStaticMethod: Boolean get() = decorators.any { it.name == "staticmethod" }
    val isClassMethod: Boolean get() = decorators.any { it.name == "classmethod" }
    val isProperty: Boolean get() = decorators.any { it.name == "property" }
}

/** A function parameter declaration. Mirrors PIRParameter but lives in Flat IR. */
data class FlatParameter(
    val name: String,
    val type: FlatType,
    val kind: FlatParamKind,
    val hasDefault: Boolean,
    val defaultValue: FlatConst?,
)

enum class FlatParamKind {
    POSITIONAL_OR_KEYWORD,
    VAR_POSITIONAL,
    VAR_KEYWORD,
    KEYWORD_ONLY,
}

data class FlatDecorator(
    val name: String,
    val qualifiedName: String,
    val arguments: List<String>,
)

/**
 * Raw module-level Flat IR bundle. Wraps every function-like scope plus
 * module metadata and any diagnostics accumulated during lowering.
 *
 * Not routed through the current builder yet; introduced here so subsequent
 * phases can move to a `FlatModuleIR -> FlatModuleIR` transform shape
 * without re-plumbing types.
 */
data class FlatModuleIR(
    val moduleName: String,
    val path: String,
    val functions: List<FlatFunctionIR>,
    val fields: List<PIRField>,
    val imports: List<String>,
    val diagnostics: List<PIRDiagnostic>,
)
