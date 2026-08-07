package org.opentaint.semgrep.go.pattern

// ----------------------------------------------------------------------------
// Root
// ----------------------------------------------------------------------------

sealed interface SemgrepGoPattern {
    val children: List<SemgrepGoPattern>

    /**
     * Fallback for grammar fragments that the visitor cannot yet translate but
     * shouldn't make the whole parsing fail. Real AST nodes never use this.
     */
    data class Raw(val text: String) : SemgrepGoPattern {
        override val children: List<SemgrepGoPattern> get() = emptyList()
    }
}

// ----------------------------------------------------------------------------
// 5.2 Names
// ----------------------------------------------------------------------------

sealed interface Name
data class ConcreteName(val name: String) : Name
data class MetavarName(val name: String) : Name

// ----------------------------------------------------------------------------
// 5.1 Pattern atoms
// ----------------------------------------------------------------------------

data class Metavar(val name: String, val star: Boolean = false) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class EllipsisMetavar(val name: String) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data object Ellipsis : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class DeepExpr(val nested: SemgrepGoPattern) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(nested)
}

data class TypedMetavar(val name: String, val type: TypeName, val star: Boolean = false) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class Identifier(val name: Name) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class QualifiedIdent(val pkg: Name, val sel: Name) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

// ----------------------------------------------------------------------------
// 5.3 Literals
// ----------------------------------------------------------------------------

data class IntLiteral(val text: String) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class FloatLiteral(val text: String) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class ImaginaryLiteral(val text: String) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class RuneLiteral(val text: String) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class BoolConstant(val value: Boolean) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data object NilLiteral : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class StringLiteral(val content: Name) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data object StringEllipsis : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

// ----------------------------------------------------------------------------
// 5.4 Expressions
// ----------------------------------------------------------------------------

sealed interface CallArgs : SemgrepGoPattern

data object NoArgs : CallArgs {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class EllipsisArgPrefix(val rest: CallArgs) : CallArgs {
    override val children: List<SemgrepGoPattern> get() = listOf(rest)
}

data class ArgPrefix(val arg: SemgrepGoPattern, val rest: CallArgs) : CallArgs {
    override val children: List<SemgrepGoPattern> get() = listOf(arg, rest)
}

data class CallExpr(
    val fn: SemgrepGoPattern,
    val args: CallArgs,
    val hasEllipsis: Boolean,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(fn, args)
}

data class SelectorExpr(val obj: SemgrepGoPattern, val sel: Name) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(obj)
}

data class IndexExpr(val obj: SemgrepGoPattern, val index: SemgrepGoPattern) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(obj, index)
}

data class SliceExpr(
    val obj: SemgrepGoPattern,
    val low: SemgrepGoPattern?,
    val high: SemgrepGoPattern?,
    val max: SemgrepGoPattern?,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern>
        get() = listOfNotNull(obj, low, high, max)
}

data class TypeAssertion(val obj: SemgrepGoPattern, val type: TypeName?) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(obj)
}

data class BinaryExpr(
    val op: String,
    val left: SemgrepGoPattern,
    val right: SemgrepGoPattern,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(left, right)
}

data class UnaryExpr(val op: String, val operand: SemgrepGoPattern) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(operand)
}

data class StarExpr(val operand: SemgrepGoPattern) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(operand)
}

sealed interface CompositeElem : SemgrepGoPattern

data class KeyedElem(val key: SemgrepGoPattern?, val value: SemgrepGoPattern) : CompositeElem {
    override val children: List<SemgrepGoPattern> get() = listOfNotNull(key, value)
}

data object EllipsisElem : CompositeElem {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class CompositeLit(
    val type: TypeName?,
    val elements: List<CompositeElem>,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = elements
}

data class FuncLit(val signature: FuncType, val body: BlockStmt) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(body)
}

data class ConversionExpr(val type: TypeName, val expr: SemgrepGoPattern) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(expr)
}

// ----------------------------------------------------------------------------
// 5.5 Statements
// ----------------------------------------------------------------------------

data class BlockStmt(val stmts: List<SemgrepGoPattern>) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = stmts
}

data class IfStmt(
    val init: SemgrepGoPattern?,
    val cond: SemgrepGoPattern,
    val then: BlockStmt,
    val els: SemgrepGoPattern?,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern>
        get() = listOfNotNull(init, cond, then, els)
}

data class ForStmt(
    val init: SemgrepGoPattern?,
    val cond: SemgrepGoPattern?,
    val post: SemgrepGoPattern?,
    val body: BlockStmt,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOfNotNull(init, cond, post, body)
}

data class ForEllipsisStmt(val body: BlockStmt) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(body)
}

data class RangeStmt(
    val key: SemgrepGoPattern?,
    val value: SemgrepGoPattern?,
    val decl: Boolean,
    val range: SemgrepGoPattern,
    val body: BlockStmt,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern>
        get() = listOfNotNull(key, value, range, body)
}

sealed interface CaseValues
data object DefaultCase : CaseValues
data class ExprCaseValues(val exprs: List<SemgrepGoPattern>) : CaseValues
data class TypeCaseValuesList(val types: List<TypeName>) : CaseValues
data object EllipsisCase : CaseValues

data class CaseClause(
    val values: CaseValues,
    val body: List<SemgrepGoPattern>,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = body
}

data class TypeCaseClause(
    val values: CaseValues,
    val body: List<SemgrepGoPattern>,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = body
}

sealed interface CommCase
data object CommDefault : CommCase
data class CommSend(val stmt: SendStmt) : CommCase
data class CommRecv(val stmt: RecvStmt) : CommCase
data object CommEllipsis : CommCase

data class CommClause(
    val comm: CommCase,
    val body: List<SemgrepGoPattern>,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = body
}

data class SwitchStmt(
    val init: SemgrepGoPattern?,
    val tag: SemgrepGoPattern?,
    val cases: List<CaseClause>,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern>
        get() = listOfNotNull(init, tag) + cases
}

data class TypeSwitchStmt(
    val init: SemgrepGoPattern?,
    val guard: SemgrepGoPattern,
    val cases: List<TypeCaseClause>,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern>
        get() = listOfNotNull(init, guard) + cases
}

data class SelectStmt(val cases: List<CommClause>) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = cases
}

data class ReturnStmt(val values: List<SemgrepGoPattern>) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = values
}

data class DeferStmt(val call: SemgrepGoPattern) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(call)
}

data class GoStmt(val call: SemgrepGoPattern) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(call)
}

data class AssignStmt(
    val op: String,
    val lhs: List<SemgrepGoPattern>,
    val rhs: List<SemgrepGoPattern>,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = lhs + rhs
}

data class ShortVarDecl(
    val lhs: List<SemgrepGoPattern>,
    val rhs: List<SemgrepGoPattern>,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = lhs + rhs
}

data class IncDecStmt(val operand: SemgrepGoPattern, val op: String) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(operand)
}

data class SendStmt(val channel: SemgrepGoPattern, val value: SemgrepGoPattern) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(channel, value)
}

data class RecvStmt(
    val lhs: List<SemgrepGoPattern>,
    val op: String,
    val value: SemgrepGoPattern,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = lhs + value
}

data class LabeledStmt(val label: Name, val stmt: SemgrepGoPattern?) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOfNotNull(stmt)
}

enum class BranchKind { BREAK, CONTINUE, GOTO, FALLTHROUGH }

data class BranchStmt(val kind: BranchKind, val label: Name?) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class ExprStmt(val expr: SemgrepGoPattern) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(expr)
}

data object EllipsisStmt : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

// ----------------------------------------------------------------------------
// 5.6 Declarations
// ----------------------------------------------------------------------------

data class PackageClause(val name: Name) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class ImportSpec(
    val alias: Name?,
    val dotImport: Boolean,
    val path: SemgrepGoPattern,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOf(path)
}

sealed interface ImportDecl: SemgrepGoPattern

data class ParsedImportDecl(
    val specs: List<ImportSpec>,
    val hasEllipsis: Boolean = false,
) : ImportDecl {
    override val children: List<SemgrepGoPattern> get() = specs
}

data object RemovedImportDecl : ImportDecl {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class ValueSpec(
    val names: List<Name>,
    val type: TypeName?,
    val values: List<SemgrepGoPattern>,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = values
}

data class ConstDecl(val specs: List<ValueSpec>) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = specs
}

data class VarDecl(val specs: List<ValueSpec>) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = specs
}

data class TypeSpec(
    val name: Name,
    val alias: Boolean,
    val type: TypeName,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class TypeDecl(val specs: List<TypeSpec>) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = specs
}

data class FuncDecl(
    val name: Name,
    val signature: FuncType,
    val body: BlockStmt?,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOfNotNull(body)
}

data class MethodDecl(
    val receiver: ParameterDecl,
    val name: Name,
    val signature: FuncType,
    val body: BlockStmt?,
) : SemgrepGoPattern {
    override val children: List<SemgrepGoPattern> get() = listOfNotNull(body)
}

sealed interface ParameterDecl : SemgrepGoPattern

data class NamedParam(
    val names: List<Name>,
    val type: TypeName,
    val variadic: Boolean,
) : ParameterDecl {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data object EllipsisParam : ParameterDecl {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class EllipsisMetavarParam(val name: String) : ParameterDecl {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class MetavarParam(val name: String) : ParameterDecl {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

sealed interface FieldDecl : SemgrepGoPattern

data class NamedFieldDecl(
    val names: List<Name>,
    val type: TypeName?,
    val embedded: TypeName?,
    val tag: String?,
) : FieldDecl {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data object EllipsisField : FieldDecl {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

// ----------------------------------------------------------------------------
// 5.7 Types
// ----------------------------------------------------------------------------

sealed interface TypeName

data class NamedType(val name: Name, val typeArgs: List<TypeName> = emptyList()) : TypeName
data class QualifiedType(
    val pkg: Name,
    val name: Name,
    val typeArgs: List<TypeName> = emptyList(),
) : TypeName

data class PointerType(val elem: TypeName) : TypeName
data class SliceType(val elem: TypeName) : TypeName
data class ArrayType(val len: SemgrepGoPattern?, val elem: TypeName) : TypeName
data class MapType(val key: TypeName, val value: TypeName) : TypeName

enum class ChanDir { BIDI, SEND, RECV }
data class ChanType(val dir: ChanDir, val elem: TypeName) : TypeName

data class FuncType(
    val params: List<ParameterDecl>,
    val results: List<ParameterDecl>,
) : TypeName

data class StructType(val fields: List<FieldDecl>) : TypeName

sealed interface InterfaceElem
data class MethodSpec(val name: Name, val signature: FuncType) : InterfaceElem
data class EmbeddedType(val type: TypeName) : InterfaceElem
data object EllipsisInterfaceElem : InterfaceElem

data class InterfaceType(val methods: List<InterfaceElem>) : TypeName

data object EllipsisType : TypeName
data class MetavarType(val name: String) : TypeName

// ----------------------------------------------------------------------------
// 5.8 Top level
// ----------------------------------------------------------------------------

sealed interface SemgrepGoPatternTop : SemgrepGoPattern

data class PackageOnlyPattern(val pkg: PackageClause) : SemgrepGoPatternTop {
    override val children: List<SemgrepGoPattern> get() = listOf(pkg)
}

data class TypeOnlyPattern(val type: TypeName) : SemgrepGoPatternTop {
    override val children: List<SemgrepGoPattern> get() = emptyList()
}

data class TopList(val items: List<SemgrepGoPattern>) : SemgrepGoPatternTop {
    override val children: List<SemgrepGoPattern> get() = items
}

data class SourceFile(
    val pkg: PackageClause,
    val imports: List<ImportDecl>,
    val decls: List<SemgrepGoPattern>,
) : SemgrepGoPatternTop {
    override val children: List<SemgrepGoPattern> get() = listOf<SemgrepGoPattern>(pkg) + imports + decls
}
