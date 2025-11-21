package org.opentaint.org.opentaint.semgrep.pattern

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ConsoleErrorListener
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.opentaint.semgrep.pattern.antlr.JavaLexer
import org.opentaint.semgrep.pattern.antlr.JavaParser
import org.opentaint.semgrep.pattern.antlr.JavaParser.ClassOrInterfaceTypeContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.CreatedNameContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.IdentifierContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.PrimitiveTypeContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeTypeContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeTypeOrVoidContext
import org.opentaint.semgrep.pattern.antlr.JavaParserBaseVisitor

sealed interface SemgrepJavaPatternParsingResult {
    data class Ok(val pattern: SemgrepJavaPattern) : SemgrepJavaPatternParsingResult
    data class Fail(val exception: Throwable) : SemgrepJavaPatternParsingResult
    data class FailedASTParsing(val errorMessages: List<String>) : SemgrepJavaPatternParsingResult
}

class SemgrepJavaPatternParser {
    private val visitor = SemgrepJavaPatternParserVisitor()

    fun parseSemgrepJavaPattern(pattern: String): SemgrepJavaPatternParsingResult {
        val lexer = JavaLexer(CharStreams.fromString(pattern))
        val tokens = CommonTokenStream(lexer)

        val errors = mutableListOf<String>()
        val parser = JavaParser(tokens).also {
            // Suppress writing errors to stderr
            it.removeErrorListener(ConsoleErrorListener.INSTANCE)

            // Accumulate errors to report via FailedAstParsing
            it.addErrorListener(object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String,
                    e: RecognitionException?
                ) {
                    errors.add("line $line:$charPositionInLine $msg")
                }
            })
        }

        val tree = parser.semgrepPattern()
        if (errors.isNotEmpty()) {
            return SemgrepJavaPatternParsingResult.FailedASTParsing(errors)
        }

        val result = runCatching {
            val parsed = visitor.visit(tree) as SemgrepJavaPattern
            SemgrepJavaPatternParsingResult.Ok(parsed)
        }
        return result.getOrElse { SemgrepJavaPatternParsingResult.Fail(it) }
    }
}


private class TypenameParserVisitor : JavaParserBaseVisitor<TypeName>() {
    override fun defaultResult(): TypeName {
        throw SemgrepParsingException("Can't parse typename")
    }

    override fun visitCreatedName(ctx: CreatedNameContext): TypeName {
        val dotSeparatedParts = ctx.identifier().map { it.asName() }.toMutableList()
        return TypeName(dotSeparatedParts)
    }

    override fun visitTypeTypeOrVoid(ctx: TypeTypeOrVoidContext): TypeName {
        if (ctx.VOID() != null) {
            return TypeName(listOf(ctx.asName()))
        }

        return ctx.typeType().accept(this)
    }

    override fun visitTypeType(ctx: TypeTypeContext): TypeName {
        if (ctx.primitiveType() != null) {
            return ctx.primitiveType().accept(this)
        }
        return ctx.classOrInterfaceType().accept(this)
    }

    override fun visitPrimitiveType(ctx: PrimitiveTypeContext): TypeName {
        return TypeName(listOf(ctx.asName()))
    }

    override fun visitClassOrInterfaceType(ctx: ClassOrInterfaceTypeContext): TypeName {
        val prefix = ctx.identifier().map { it.asName() }

        val final = ctx.typeIdentifier().asName()
        return TypeName(dotSeparatedParts = prefix + final)
    }

    override fun visitQualifiedName(ctx: JavaParser.QualifiedNameContext): TypeName {
        val parts = ctx.identifier().map { it.asName() }
        return TypeName(dotSeparatedParts = parts)
    }

    override fun visitAltAnnotationQualifiedName(ctx: JavaParser.AltAnnotationQualifiedNameContext): TypeName {
        val parts = ctx.identifier().map { it.asName() }
        return TypeName(dotSeparatedParts = parts)
    }
}

private class SemgrepJavaPatternParserVisitor : JavaParserBaseVisitor<SemgrepJavaPattern>() {
    private val typenameParser = TypenameParserVisitor()

    override fun aggregateResult(aggregate: SemgrepJavaPattern?, nextResult: SemgrepJavaPattern?): SemgrepJavaPattern? {
        if (aggregate == null) {
            return nextResult
        } else if (nextResult == null) {
            return aggregate
        }
        throw SemgrepParsingException("Unexpected aggregation of non-null patterns")
    }

    override fun visitThisExpression(ctx: JavaParser.ThisExpressionContext?): SemgrepJavaPattern {
        return ThisExpr
    }

    override fun visitBinaryOperatorExpression(ctx: JavaParser.BinaryOperatorExpressionContext): SemgrepJavaPattern {
        val operator = ctx.bop
        val lhs = ctx.expression(0).accept(this)
            ?: throw SemgrepParsingException(ctx, "Unable to parse lhs")
        val rhs = ctx.expression(1).accept(this)
            ?: throw SemgrepParsingException(ctx, "Unable to parse rhs")

        return when (operator.type) {
            JavaParser.ASSIGN -> VariableAssignment(null, lhs, rhs)
            JavaParser.ADD_ASSIGN -> VariableAssignment(null, lhs, AddExpr(lhs, rhs))
            JavaParser.ADD -> AddExpr(lhs, rhs)
            else -> throw SemgrepParsingException(ctx, "Unknown binary operator: ${operator.text}")
        }
    }

    override fun visitTypedVariableExpression(ctx: JavaParser.TypedVariableExpressionContext): TypedMetavar {
        val type = ctx.typeTypeOrVoid().accept(typenameParser)
        val name = ctx.identifier().asName() as? MetavarName
            ?: throw SemgrepParsingException(ctx, "Expected variable name to be a metavar name")

        return TypedMetavar(name.metavarName, type)
    }

    override fun visitVariableDeclarator(ctx: JavaParser.VariableDeclaratorContext): VariableAssignment? {
        val variable = ctx.variableDeclaratorId().accept(this)
            ?: throw SemgrepParsingException(ctx, "Can't parse variable name")

        val initializer = ctx.variableInitializer() ?: return null
        val value = initializer.accept(this)
            ?: throw SemgrepParsingException(ctx, "Can't parse initializer")

        return VariableAssignment(type = null, variable, value)
    }

    override fun visitLocalVariableDeclaration(ctx: JavaParser.LocalVariableDeclarationContext): SemgrepJavaPattern {
        if (ctx.VAR() != null) {
            val variable = ctx.identifier().accept(this)
                ?: throw SemgrepParsingException(ctx, "Can't parse variable")
            val value = ctx.expression().accept(this)
                ?: throw SemgrepParsingException(ctx, "Can't parse variable initialization")

            return VariableAssignment(type = null, variable, value)
        }

        val type = ctx.typeType().accept(typenameParser)

        val assignments = ctx.variableDeclarators().variableDeclarator().mapNotNull { declarator ->
            val declaration = visitVariableDeclarator(declarator)
            declaration?.let {
                VariableAssignment(type, it.variable, it.value)
            }
        }

        return assignments.asPatternSequence()
    }

    override fun visitArguments(ctx: JavaParser.ArgumentsContext): MethodArguments {
        val argumentList = ctx.expressionList()?.expression().orEmpty()

        val parsedArguments = argumentList.map {
            it.accept(this) ?: throw SemgrepParsingException(it, "Can't parse as an argument")
        }

        return parsedArguments.asMethodArguments()
    }

    override fun visitFormalParameter(ctx: JavaParser.FormalParameterContext): SemgrepJavaPattern {
        return when {
            ctx.ellipsisExpression() != null -> Ellipsis
            else -> {
                val name = ctx.variableDeclaratorId().identifier().asName()
                val type = ctx.typeType().accept(typenameParser)
                return FormalArgument(name, type)
            }
        }
    }

    override fun visitFormalParameters(ctx: JavaParser.FormalParametersContext): MethodArguments {
        val argumentList = ctx.formalParameterList().formalParameter().orEmpty()

        val parsedArguments = argumentList.map {
            it.accept(this) ?: throw SemgrepParsingException(it, "Can't parse as an argument")
        }

        return parsedArguments.asMethodArguments()
    }

    override fun visitMethodCall(ctx: JavaParser.MethodCallContext): SemgrepJavaPattern {
        if (ctx.ellipsisExpression() != null) {
            return Ellipsis
        }

        val methodName = ctx.methodIdentifier().asName()
        val arguments = visitArguments(ctx.arguments())

        return MethodInvocation(methodName, obj = null, arguments)
    }

    override fun visitMemberReferenceExpression(ctx: JavaParser.MemberReferenceExpressionContext): SemgrepJavaPattern {
        when {
            ctx.methodCall() != null -> {
                val lhs = ctx.expression().accept(this)
                    ?: throw SemgrepParsingException(ctx, "Unable to parse lhs of memberReferenceExpression")

                return when (val methodInvocation = visitMethodCall(ctx.methodCall())) {
                    is MethodInvocation -> MethodInvocation(
                        methodName = methodInvocation.methodName,
                        obj = lhs,
                        args = methodInvocation.args
                    )
                    is Ellipsis -> EllipsisMethodInvocations(
                        obj = lhs
                    )
                    else -> throw SemgrepParsingException(ctx, "Unexpected methodCall parsing result $methodInvocation")
                }
            }
            ctx.identifier() != null -> {
                val lhs = ctx.expression().asObject()
                val fieldName = ctx.identifier().asName()
                return FieldAccess(fieldName, lhs)
            }
            else -> {
                throw SemgrepParsingException(ctx, "Unsupported member reference expression")
            }
        }
    }

    override fun visitObjectCreationExpression(ctx: JavaParser.ObjectCreationExpressionContext): ObjectCreation {
        val creator = ctx.creator()
        val arguments = creator.classCreatorRest()?.arguments()
            ?: throw SemgrepParsingException(creator, "Can't parse argument list")

        val parsedType = creator.createdName().accept(typenameParser)
        val parsedArguments = visitArguments(arguments)

        return ObjectCreation(parsedType, parsedArguments)
    }

    override fun visitMethodDeclaration(ctx: JavaParser.MethodDeclarationContext): MethodDeclaration {
        val returnType = ctx.typeTypeOrVoid().accept(typenameParser)
        val name = ctx.identifier().asName()
        val args = visitFormalParameters(ctx.formalParameters())
        val body = ctx.methodBody()?.accept(this) ?: Ellipsis

        return MethodDeclaration(name, returnType, args, body, modifiers = emptyList())
    }

    override fun visitConstructorDeclaration(ctx: JavaParser.ConstructorDeclarationContext): MethodDeclaration {
        val name = ctx.identifier().asName()
        val args = visitFormalParameters(ctx.formalParameters())
        val body = ctx.constructorBody.accept(this)
            ?: throw SemgrepParsingException(ctx, "Can't parse body")

        return MethodDeclaration(name, returnType = null, args, body, modifiers = emptyList())
    }

    override fun visitClassDeclaration(ctx: JavaParser.ClassDeclarationContext): ClassDeclaration {
        val name = ctx.identifier().asName()
        val body = ctx.classBody()?.accept(this) ?: Ellipsis
        val extends = ctx.classExtends?.accept(typenameParser)
        val implements = ctx.classImplements?.typeType().orEmpty().map { it.accept(typenameParser) }

        return ClassDeclaration(
            name = name,
            extends = extends,
            implements = implements,
            modifiers = emptyList(),
            body = body
        )
    }

    override fun visitElementValuePair(ctx: JavaParser.ElementValuePairContext): SemgrepJavaPattern {
        if (ctx.ellipsisExpression() != null) {
            return Ellipsis
        }

        val name = ctx.identifier().asName()
        val value = ctx.elementValue().accept(this)
        return NamedValue(name, value)
    }

    override fun visitAnnotation(ctx: JavaParser.AnnotationContext): Annotation {
        val nameContext = ctx.qualifiedName() ?: ctx.altAnnotationQualifiedName()
        val name = nameContext.accept(typenameParser)

        val arguments = when {
            ctx.elementValue() != null -> listOf(ctx.elementValue().accept(this))
            ctx.elementValuePairs() != null -> ctx.elementValuePairs().elementValuePair().map { it.accept(this) }
            else -> emptyList()
        }

        return Annotation(name, arguments.asMethodArguments())
    }

    override fun visitBlock(ctx: JavaParser.BlockContext): SemgrepJavaPattern {
        val statements = ctx.blockStatement().map {
            it.accept(this) ?: throw SemgrepParsingException(it, "Can't parse such statement")
        }

        return statements.asPatternSequence()
    }

    override fun visitMethodBodySemgrepPattern(ctx: JavaParser.MethodBodySemgrepPatternContext): SemgrepJavaPattern {
        val statements = ctx.patternStatement().map {
            it.accept(this) ?: throw SemgrepParsingException(it, "Can't parse such statement")
        }
        return statements.asPatternSequence()
    }

    override fun visitClassBodySemgrepPattern(ctx: JavaParser.ClassBodySemgrepPatternContext): SemgrepJavaPattern {
        val declarations = ctx.classBodyDeclaration().map {
            it.accept(this) ?: throw SemgrepParsingException(it, "Can't parse such declaration")
        }

        return declarations.asPatternSequence()
    }

    override fun visitAnnotationSemgrepPattern(ctx: JavaParser.AnnotationSemgrepPatternContext): SemgrepJavaPattern {
        val annotations = ctx.annotation().map {
            it.accept(this) ?: throw SemgrepParsingException(it, "Can't parse such annotation")
        }

        return annotations.asPatternSequence()
    }

    override fun visitLocalTypeDeclaration(ctx: JavaParser.LocalTypeDeclarationContext): SemgrepJavaPattern {
        val modifiers = ctx.classOrInterfaceModifier()
            .mapNotNull { it.accept(this) }
            .map { it as? Modifier ?: throw SemgrepParsingException(ctx, "Expected modifier, got $it") }

        val declaration = when {
            ctx.classDeclaration() != null -> ctx.classDeclaration().accept(this)
            else -> throw SemgrepParsingException(ctx, "Unexpected local type declaration")
        }

        return declaration.withModifiers(modifiers)
    }

    override fun visitClassBodyDeclaration(ctx: JavaParser.ClassBodyDeclarationContext): SemgrepJavaPattern {
        if (ctx.block() != null) {
            return ctx.block().accept(this)
        }

        if (ctx.ellipsisExpression() != null) {
            return Ellipsis
        }

        val modifiers = ctx.modifier()
            .mapNotNull { it.accept(this) }
            .map { it as? Modifier ?: throw SemgrepParsingException(ctx, "Expected modifier, got $it") }

        val declaration = ctx.memberDeclaration().accept(this)
            ?: throw SemgrepParsingException(ctx, "Can't parse declaration")

        return declaration.withModifiers(modifiers)
    }

    override fun visitClassBody(ctx: JavaParser.ClassBodyContext): SemgrepJavaPattern {
        val declarations = ctx.classBodyDeclaration().map {
            it.accept(this) ?: throw SemgrepParsingException(it, "Can't parse such declaration")
        }

        return declarations.asPatternSequence()
    }

    override fun visitReturnExpression(ctx: JavaParser.ReturnExpressionContext): SemgrepJavaPattern {
        val value = ctx.expression()?.accept(this)
        return ReturnStmt(value)
    }

    override fun visitIdentifier(ctx: IdentifierContext): SemgrepJavaPattern {
        val name = ctx.IDENTIFIER()?.text
            ?: throw SemgrepParsingException(ctx, "Unknown identifier: ${ctx.text}")

        return if (name.startsWith("$")) {
            Metavar(name)
        } else {
            Identifier(name)
        }
    }

    override fun visitLiteral(ctx: JavaParser.LiteralContext): SemgrepJavaPattern {
        if (ctx.BOOL_LITERAL() != null) {
            return when (val literal = ctx.BOOL_LITERAL().text) {
                "true" -> BoolConstant(true)
                "false" -> BoolConstant(false)
                else -> throw SemgrepParsingException(ctx, "Unknown bool literal: $literal")
            }
        }
        if (ctx.STRING_LITERAL() != null) {
            return when (val stringLiteral = ctx.STRING_LITERAL().text.trim('"')) {
                "..." -> StringEllipsis
                else -> StringLiteral(stringLiteral.asName())
            }
        }

        throw SemgrepParsingException(ctx, "Unexpected literal")
    }

    override fun visitEllipsisExpression(ctx: JavaParser.EllipsisExpressionContext): SemgrepJavaPattern {
        return Ellipsis
    }

    private fun List<SemgrepJavaPattern>.asPatternSequence(): SemgrepJavaPattern {
        return when (size) {
            0 -> EmptyPatternSequence
            1 -> single()
            else -> reduce { acc, newPattern ->
                PatternSequence(acc, newPattern)
            }
        }
    }

    private fun List<SemgrepJavaPattern>.asMethodArguments(): MethodArguments {
        return foldRight<_, MethodArguments>(NoArgs) { newArgument, rest ->
            if (newArgument is Ellipsis) {
                // two ellipsis in row are redundant (though sometimes for some reason used - probably by mistake)
                if (rest is EllipsisArgumentPrefix) {
                    rest
                } else {
                    EllipsisArgumentPrefix(rest)
                }
            } else {
                PatternArgumentPrefix(newArgument, rest)
            }
        }
    }

    private fun SemgrepJavaPattern.withModifiers(modifiers: List<Modifier>): SemgrepJavaPattern {
        if (modifiers.isEmpty()) {
            return this
        }

        return when (this) {
            is MethodDeclaration -> MethodDeclaration(
                name = name,
                returnType = returnType,
                args = args,
                body = body,
                modifiers = modifiers
            )
            is ClassDeclaration -> ClassDeclaration(
                name = name,
                extends = extends,
                implements = implements,
                modifiers = modifiers,
                body = body
            )
            else -> throw SemgrepParsingException("Unexpected non-empty list of modifiers for $this")
        }
    }

    private fun ParserRuleContext.asObject(): FieldAccess.Object {
        if (text == "super") {
            return FieldAccess.SuperObject
        }

        val obj = accept(this@SemgrepJavaPatternParserVisitor)
            ?: throw SemgrepParsingException(this, "Unable to parse as object pattern")
        return FieldAccess.ObjectPattern(obj)
    }
}

class SemgrepParsingException(message: String) : RuntimeException(message) {
    constructor(ctx: ParserRuleContext, additionalMessage: String):
            this("Exception during parsing ${ctx.text}: $additionalMessage")
}


private fun ParserRuleContext.asName(): Name {
    return text.asName()
}

private fun String.asName(): Name {
    return if (startsWith("$")) {
        MetavarName(this)
    } else {
        ConcreteName(this)
    }
}