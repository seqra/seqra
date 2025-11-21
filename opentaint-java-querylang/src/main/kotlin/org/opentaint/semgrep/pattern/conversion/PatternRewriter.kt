package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.AddExpr
import org.opentaint.org.opentaint.semgrep.pattern.Annotation
import org.opentaint.org.opentaint.semgrep.pattern.BoolConstant
import org.opentaint.org.opentaint.semgrep.pattern.ClassDeclaration
import org.opentaint.org.opentaint.semgrep.pattern.Ellipsis
import org.opentaint.org.opentaint.semgrep.pattern.EllipsisArgumentPrefix
import org.opentaint.org.opentaint.semgrep.pattern.EllipsisMethodInvocations
import org.opentaint.org.opentaint.semgrep.pattern.EmptyPatternSequence
import org.opentaint.org.opentaint.semgrep.pattern.FieldAccess
import org.opentaint.org.opentaint.semgrep.pattern.FormalArgument
import org.opentaint.org.opentaint.semgrep.pattern.Identifier
import org.opentaint.org.opentaint.semgrep.pattern.Metavar
import org.opentaint.org.opentaint.semgrep.pattern.MethodArguments
import org.opentaint.org.opentaint.semgrep.pattern.MethodDeclaration
import org.opentaint.org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.org.opentaint.semgrep.pattern.Modifier
import org.opentaint.org.opentaint.semgrep.pattern.Name
import org.opentaint.org.opentaint.semgrep.pattern.NamedValue
import org.opentaint.org.opentaint.semgrep.pattern.NoArgs
import org.opentaint.org.opentaint.semgrep.pattern.ObjectCreation
import org.opentaint.org.opentaint.semgrep.pattern.PatternArgumentPrefix
import org.opentaint.org.opentaint.semgrep.pattern.PatternSequence
import org.opentaint.org.opentaint.semgrep.pattern.ReturnStmt
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.org.opentaint.semgrep.pattern.StringEllipsis
import org.opentaint.org.opentaint.semgrep.pattern.StringLiteral
import org.opentaint.org.opentaint.semgrep.pattern.ThisExpr
import org.opentaint.org.opentaint.semgrep.pattern.TypeName
import org.opentaint.org.opentaint.semgrep.pattern.TypedMetavar
import org.opentaint.org.opentaint.semgrep.pattern.VariableAssignment

interface PatternRewriter {
    fun SemgrepJavaPattern.rewrite(): SemgrepJavaPattern = when (this) {
        is MethodArguments -> rewriteMethodArguments()
        is AddExpr -> rewriteAddExpr()
        is Annotation -> rewriteAnnotation()
        is BoolConstant -> rewriteBoolConstant()
        Ellipsis -> rewriteEllipsis()
        EmptyPatternSequence -> rewriteEmptyPatternSequence()
        is Identifier -> rewriteIdentifier()
        is Metavar -> rewriteMetavar()
        is MethodDeclaration -> rewriteMethodDeclaration()

        is EllipsisMethodInvocations -> rewriteEllipsisMethodInvocations()
        is FieldAccess -> rewriteFieldAccess()
        is FormalArgument -> rewriteFormalArgument()
        is NamedValue -> rewriteNamedValue()

        is MethodInvocation -> rewriteMethodInvocation()
        is ObjectCreation -> rewriteObjectCreation()
        is PatternSequence -> rewritePatternSequence()
        is ReturnStmt -> rewriteReturnStmt()
        StringEllipsis -> rewriteStringEllipsis()
        is StringLiteral -> rewriteStringLiteral()
        ThisExpr -> rewriteThisExpr()
        is TypedMetavar -> rewriteTypedMetavar()
        is VariableAssignment -> rewriteVariableAssignment()
        is ClassDeclaration -> rewriteClassDeclaration()
    }

    fun AddExpr.rewriteAddExpr(): SemgrepJavaPattern = createAddExpr(left.rewrite(), right.rewrite())

    fun MethodDeclaration.rewriteMethodDeclaration(): SemgrepJavaPattern = createMethodDeclaration(
        name.rewriteName(),
        returnType?.rewriteTypeName(),
        args.rewriteMethodArguments(),
        body.rewrite(),
        modifiers.map { it.rewriteModifier() }
    )

    fun ClassDeclaration.rewriteClassDeclaration(): SemgrepJavaPattern = createClassDeclaration(
        name.rewriteName(),
        extends?.rewriteTypeName(),
        implements.map { it.rewriteTypeName() },
        modifiers.map { it.rewriteModifier() },
        body.rewrite(),
    )

    fun NamedValue.rewriteNamedValue(): SemgrepJavaPattern = createNamedValue(name.rewriteName(), value.rewrite())

    fun createNamedValue(name: Name, value: SemgrepJavaPattern) = NamedValue(name, value)

    fun createAddExpr(left: SemgrepJavaPattern, right: SemgrepJavaPattern): SemgrepJavaPattern = AddExpr(left, right)

    fun createAnnotation(name: TypeName, args: MethodArguments) = Annotation(name, args)

    fun MethodArguments.rewriteMethodArguments(): MethodArguments = when (this) {
        is EllipsisArgumentPrefix -> createEllipsisArgumentPrefix(rest.rewriteMethodArguments())
        NoArgs -> createNoArgs()
        is PatternArgumentPrefix -> createPatternArgumentPrefix(
            argument.rewrite(),
            rest.rewriteMethodArguments()
        )
    }

    fun EllipsisMethodInvocations.rewriteEllipsisMethodInvocations(): SemgrepJavaPattern =
        createEllipsisMethodInvocations(obj.rewrite())

    fun FieldAccess.rewriteFieldAccess(): SemgrepJavaPattern =
        createFieldAccess(fieldName.rewriteName(), obj.rewriteObject())

    fun FormalArgument.rewriteFormalArgument(): SemgrepJavaPattern = createFormalArgument(
        name.rewriteName(),
        type.rewriteTypeName()
    )

    fun MethodInvocation.rewriteMethodInvocation(): SemgrepJavaPattern = createMethodInvocation(
        methodName.rewriteName(), obj?.rewrite(), args.rewriteMethodArguments()
    )

    fun ObjectCreation.rewriteObjectCreation(): SemgrepJavaPattern =
        createObjectCreation(type.rewriteTypeName(), args.rewriteMethodArguments())

    fun PatternSequence.rewritePatternSequence(): SemgrepJavaPattern =
        createPatternSequence(first.rewrite(), second.rewrite())

    fun ReturnStmt.rewriteReturnStmt(): SemgrepJavaPattern = createReturnStmt(value?.rewrite())
    fun StringLiteral.rewriteStringLiteral(): SemgrepJavaPattern = createStringLiteral(content.rewriteName())
    fun TypedMetavar.rewriteTypedMetavar(): SemgrepJavaPattern = createTypedMetavar(name, type.rewriteTypeName())
    fun VariableAssignment.rewriteVariableAssignment(): SemgrepJavaPattern =
        createVariableAssignment(type?.rewriteTypeName(), variable.rewrite(), value.rewrite())

    fun BoolConstant.rewriteBoolConstant(): SemgrepJavaPattern = this
    fun Identifier.rewriteIdentifier(): SemgrepJavaPattern = this
    fun Metavar.rewriteMetavar(): SemgrepJavaPattern = this
    fun rewriteEllipsis(): SemgrepJavaPattern = Ellipsis
    fun rewriteStringEllipsis(): SemgrepJavaPattern = StringEllipsis
    fun rewriteThisExpr(): SemgrepJavaPattern = ThisExpr
    fun rewriteEmptyPatternSequence(): SemgrepJavaPattern = EmptyPatternSequence

    fun TypeName.rewriteTypeName(): TypeName = TypeName(dotSeparatedParts.map { it.rewriteName() })

    fun Name.rewriteName(): Name = this

    fun createEllipsisArgumentPrefix(rest: MethodArguments): MethodArguments = EllipsisArgumentPrefix(rest)

    fun createNoArgs(): MethodArguments = NoArgs

    fun createPatternArgumentPrefix(argument: SemgrepJavaPattern, rest: MethodArguments): MethodArguments =
        PatternArgumentPrefix(argument, rest)

    fun createEllipsisMethodInvocations(obj: SemgrepJavaPattern): SemgrepJavaPattern = EllipsisMethodInvocations(obj)
    fun createFieldAccess(fieldName: Name, obj: FieldAccess.Object): SemgrepJavaPattern = FieldAccess(fieldName, obj)
    fun createFormalArgument(name: Name, type: TypeName): SemgrepJavaPattern = FormalArgument(name, type)

    fun createMethodDeclaration(
        name: Name,
        returnType: TypeName?,
        args: MethodArguments,
        body: SemgrepJavaPattern,
        modifiers: List<Modifier>
    ): SemgrepJavaPattern = MethodDeclaration(name, returnType, args, body, modifiers)

    fun createClassDeclaration(
        name: Name,
        extends: TypeName?,
        implements: List<TypeName>,
        modifiers: List<Modifier>,
        body: SemgrepJavaPattern
    ): SemgrepJavaPattern = ClassDeclaration(name, extends, implements, modifiers, body)

    fun createMethodInvocation(methodName: Name, obj: SemgrepJavaPattern?, args: MethodArguments): SemgrepJavaPattern =
        MethodInvocation(methodName, obj, args)

    fun createObjectCreation(type: TypeName, args: MethodArguments): SemgrepJavaPattern = ObjectCreation(type, args)

    fun createPatternSequence(first: SemgrepJavaPattern, second: SemgrepJavaPattern): SemgrepJavaPattern =
        PatternSequence(first, second)

    fun createReturnStmt(value: SemgrepJavaPattern?): SemgrepJavaPattern = ReturnStmt(value)
    fun createStringLiteral(content: Name): SemgrepJavaPattern = StringLiteral(content)
    fun createTypedMetavar(name: String, type: TypeName): SemgrepJavaPattern = TypedMetavar(name, type)

    fun createVariableAssignment(
        type: TypeName?,
        variable: SemgrepJavaPattern,
        value: SemgrepJavaPattern
    ): SemgrepJavaPattern = VariableAssignment(type, variable, value)

    fun FieldAccess.Object.rewriteObject(): FieldAccess.Object = when (this) {
        is FieldAccess.ObjectPattern -> rewriteObjectPattern()
        FieldAccess.SuperObject -> rewriteSuperObject()
    }

    fun rewriteSuperObject(): FieldAccess.Object = FieldAccess.SuperObject

    fun FieldAccess.ObjectPattern.rewriteObjectPattern(): FieldAccess.Object =
        createObjectPattern(pattern.rewrite())

    fun createObjectPattern(pattern: SemgrepJavaPattern): FieldAccess.Object = FieldAccess.ObjectPattern(pattern)

    fun Modifier.rewriteModifier(): Modifier = when (this) {
        is Annotation -> rewriteAnnotation()
    }

    fun Annotation.rewriteAnnotation(): Annotation = createAnnotation(
        name.rewriteTypeName(),
        args.rewriteMethodArguments()
    )
}

open class RewriteException(message: String) : Exception(message) {
    override fun fillInStackTrace(): Throwable = this
}

fun rewriteFailure(message: String): Nothing = throw RewriteException(message)

inline fun PatternRewriter.safeRewrite(
    pattern: SemgrepJavaPattern,
    onException: (RewriteException) -> Nothing,
): SemgrepJavaPattern = try {
    pattern.rewrite()
} catch (ex: RewriteException) {
    onException(ex)
}
