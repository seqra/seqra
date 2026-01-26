package org.opentaint.semgrep.pattern

sealed interface SemgrepJavaPattern {
    val children: List<SemgrepJavaPattern>
}

data class Metavar(val name: String) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class EllipsisMetavar(val name: String) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class TypedMetavar(val name: String, val type: TypeName) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data object Ellipsis : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class Identifier(val name: String) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data object ThisExpr : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data object EmptyPatternSequence : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class PatternSequence(val first: SemgrepJavaPattern, val second: SemgrepJavaPattern) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = listOf(first, second)
}

data class ArrayAccess(val obj: SemgrepJavaPattern, val arrayIndex: SemgrepJavaPattern) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> get() = listOf(arrayIndex, obj)
}

data class FieldAccess(val fieldName: Name, val obj: Object) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = when (obj) {
        is ObjectPattern -> listOf(obj.pattern)
        is SuperObject -> emptyList()
    }

    sealed interface Object
    data class ObjectPattern(val pattern: SemgrepJavaPattern) : Object
    data object SuperObject : Object
}

data class StaticFieldAccess(val fieldName: Name, val classTypeName: TypeName) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class MethodInvocation(
    val methodName: Name,
    val obj: SemgrepJavaPattern?,
    val args: MethodArguments,
) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = listOf(args) + (obj?.let { listOf(it) } ?: emptyList())
}

sealed interface MethodArguments : SemgrepJavaPattern

data object NoArgs : MethodArguments {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class EllipsisArgumentPrefix(val rest: MethodArguments) : MethodArguments {
    override val children: List<SemgrepJavaPattern> = listOf(rest)
}

data class PatternArgumentPrefix(
    val argument: SemgrepJavaPattern,
    val rest: MethodArguments
) : MethodArguments {
    override val children: List<SemgrepJavaPattern> = listOf(argument, rest)
}

data class EllipsisMethodInvocations(
    val obj: SemgrepJavaPattern
) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = listOf(obj)
}

data class AddExpr(val left: SemgrepJavaPattern, val right: SemgrepJavaPattern) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = listOf(left, right)
}

data class ReturnStmt(val value: SemgrepJavaPattern?) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = value?.let { listOf(it) } ?: emptyList()
}

data class VariableAssignment(
    val type: TypeName?,
    val variable: SemgrepJavaPattern,
    val value: SemgrepJavaPattern?,
) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = listOfNotNull(variable, value)
}

data class StringLiteral(val content: Name) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class IntLiteral(val value: Int) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data object NullLiteral : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data object StringEllipsis : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class BoolConstant(val value: Boolean) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class ObjectCreation(
    val type: TypeName,
    val args: MethodArguments,
) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = listOf(args)
}

data class MethodDeclaration(
    val name: Name,
    val returnType: TypeName?,
    val args: MethodArguments,
    val body: SemgrepJavaPattern,
    val modifiers: List<Modifier>,
) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> =
        listOf(args, body) + modifiers.mapNotNull { it as? SemgrepJavaPattern }
}

data class FormalArgument(
    val name: Name,
    val type: TypeName,
    val modifiers: List<Modifier>,
) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = emptyList()
}

data class NamedValue(
    val name: Name,
    val value: SemgrepJavaPattern,
) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> = listOf(value)
}

data class ClassDeclaration(
    val name: Name,
    val extends: TypeName?,
    val implements: List<TypeName>,
    val modifiers: List<Modifier>,
    val body: SemgrepJavaPattern,
) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern>
        get() = listOf(body) + modifiers.mapNotNull { it as? SemgrepJavaPattern }
}

data class ImportStatement(val dotSeparatedParts: List<Name>, val isConcrete: Boolean) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> get() = emptyList()
}

data class CatchStatement(
    val exceptionTypes: List<TypeName>,
    val exceptionVariable: Name,
    val handlerBlock: SemgrepJavaPattern
) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> get() = listOf(handlerBlock)
}

data class DeepExpr(val nestedExpr: SemgrepJavaPattern) : SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> get() = listOf(nestedExpr)
}

sealed interface Name

data class ConcreteName(val name: String) : Name
data class MetavarName(val metavarName: String) : Name

sealed interface TypeName {
    data class SimpleTypeName(
        val dotSeparatedParts: List<Name>,
        val typeArgs: List<TypeName> = emptyList()
    ) : TypeName

    data class ArrayTypeName(val elementType: TypeName) : TypeName
}

sealed interface Modifier

data class Annotation(
    val name: TypeName,
    val args: MethodArguments,
) : Modifier, SemgrepJavaPattern {
    override val children: List<SemgrepJavaPattern> get() = listOf(args)
}
