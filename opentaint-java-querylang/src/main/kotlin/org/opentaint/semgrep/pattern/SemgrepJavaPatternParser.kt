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
import org.opentaint.semgrep.pattern.antlr.JavaParser.ClassOrInterfaceModifierContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ClassOrInterfaceTypeContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.CreatedNameContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.IdentifierContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ModifierContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.PrimitiveTypeContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeDeclarationContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeTypeContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeTypeOrVoidContext
import org.opentaint.semgrep.pattern.antlr.JavaParserBaseVisitor

sealed interface SemgrepJavaPatternParsingResult {
    data class Ok(val pattern: SemgrepJavaPattern) : SemgrepJavaPatternParsingResult
    data class ParserFailure(val exception: SemgrepParsingException) : SemgrepJavaPatternParsingResult
    data class OtherFailure(val exception: Throwable) : SemgrepJavaPatternParsingResult
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
        return result.getOrElse {
            if (it is SemgrepParsingException) {
                return@getOrElse SemgrepJavaPatternParsingResult.ParserFailure(it)
            }

            SemgrepJavaPatternParsingResult.OtherFailure(it)
        }
    }
}

private fun IdentifierContext.parseName(): Name {
    tryRule({ METAVAR() }) { return MetavarName(it.text) }
    tryRule({ IDENTIFIER() }) { return ConcreteName(it.text) }
    parsingFailed("Unknown identifier")
}

private fun JavaParser.TypeIdentifierContext.parseTypeIdentifierName(): Name {
    tryRule({ METAVAR() }) { return MetavarName(it.text) }
    tryRule({ IDENTIFIER() }) { return ConcreteName(it.text) }
    parsingFailed("Unknown type identifier")
}

private class TypenameParserVisitor : JavaParserBaseVisitor<TypeName>() {
    override fun defaultResult(): TypeName {
        // note: some grammar rules remain uncovered
        TODO("Can't parse typename")
    }

    override fun visitCreatedName(ctx: CreatedNameContext): TypeName {
        val dotSeparatedParts = ctx.identifier().map { it.parseName() }.toMutableList()
        return TypeName(dotSeparatedParts)
    }

    override fun visitTypeTypeOrVoid(ctx: TypeTypeOrVoidContext): TypeName {
        ctx.tryRule({ VOID() }) { return TypeName(listOf(ConcreteName(it.text))) }
        return ctx.typeType().accept(this)
    }

    override fun visitTypeType(ctx: TypeTypeContext): TypeName {
        ctx.tryRule({ primitiveType() }) { return it.accept(this) }
        return ctx.classOrInterfaceType().accept(this)
    }

    override fun visitPrimitiveType(ctx: PrimitiveTypeContext): TypeName =
        TypeName(listOf(ConcreteName(ctx.text)))

    override fun visitClassOrInterfaceType(ctx: ClassOrInterfaceTypeContext): TypeName {
        val prefix = ctx.identifier().map { it.parseName() }
        val final = ctx.typeIdentifier().parseTypeIdentifierName()
        return TypeName(dotSeparatedParts = prefix + final)
    }

    override fun visitQualifiedName(ctx: JavaParser.QualifiedNameContext): TypeName {
        val parts = ctx.identifier().map { it.parseName() }
        return TypeName(dotSeparatedParts = parts)
    }

    override fun visitAltAnnotationQualifiedName(ctx: JavaParser.AltAnnotationQualifiedNameContext): TypeName {
        val parts = ctx.identifier().map { it.parseName() }
        return TypeName(dotSeparatedParts = parts)
    }
}

private class SemgrepJavaPatternParserVisitor : JavaParserBaseVisitor<SemgrepJavaPattern?>() {
    private val typenameParser = TypenameParserVisitor()

    override fun visitPatterns(ctx: JavaParser.PatternsContext): SemgrepJavaPattern =
        ctx.semgrepPatternElement().map { it.parse() }.asPatternSequence()

    override fun aggregateResult(aggregate: SemgrepJavaPattern?, nextResult: SemgrepJavaPattern?): SemgrepJavaPattern? {
        if (aggregate == null) {
            return nextResult
        } else if (nextResult == null) {
            return aggregate
        }

        // note: some grammar rules remain uncovered
        TODO("Unexpected aggregation of non-null patterns")
    }

    override fun visitThisExpression(ctx: JavaParser.ThisExpressionContext): SemgrepJavaPattern = ThisExpr

    override fun visitBinaryOperatorExpression(ctx: JavaParser.BinaryOperatorExpressionContext): SemgrepJavaPattern {
        val operator = ctx.bop
        val lhs = ctx.expression(0).parse("Unable to parse lhs")
        val rhs = ctx.expression(1).parse("Unable to parse rhs")

        return when (operator.type) {
            JavaParser.ASSIGN -> VariableAssignment(null, lhs, rhs)
            JavaParser.ADD_ASSIGN -> VariableAssignment(null, lhs, AddExpr(lhs, rhs))
            JavaParser.ADD -> AddExpr(lhs, rhs)
            else -> ctx.parsingFailed("Unknown binary operator")
        }
    }

    override fun visitTypedVariableExpression(ctx: JavaParser.TypedVariableExpressionContext): TypedMetavar {
        val type = ctx.typeTypeOrVoid().accept(typenameParser)
        val name = ctx.identifier().parseName() as? MetavarName
            ?: ctx.parsingFailed("Expected variable name to be a metavar name")

        return TypedMetavar(name.metavarName, type)
    }

    override fun visitVariableDeclarator(ctx: JavaParser.VariableDeclaratorContext): VariableAssignment? {
        val initializer = ctx.variableInitializer() ?: return null

        val variable = ctx.variableDeclaratorId().parse("Can't parse variable name")
        val value = initializer.parse("Can't parse initializer")
        return VariableAssignment(type = null, variable, value)
    }

    override fun visitLocalVariableDeclaration(ctx: JavaParser.LocalVariableDeclarationContext): SemgrepJavaPattern {
        ctx.tryRule({ VAR() }) {
            val variable = ctx.identifier().parse("Can't parse variable")
            val value = ctx.expression().parse("Can't parse variable initialization")
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
        val parsedArguments = argumentList.map { it.parse("Can't parse as an argument") }
        return parsedArguments.asMethodArguments()
    }

    override fun visitFormalParameter(ctx: JavaParser.FormalParameterContext): SemgrepJavaPattern {
        ctx.tryRule({ ellipsisExpression() }) { return Ellipsis }
        ctx.tryRule({ metavar() }) { return Metavar(it.text) }
        ctx.tryRule({ variableDeclaratorId() }) {
            val name = it.identifier().parseName()
            val type = ctx.typeType()?.accept(typenameParser) ?: ctx.parsingFailed()
            return FormalArgument(name, type)
        }

        ctx.parsingFailed("Unexpected formal parameter")
    }

    override fun visitFormalParameters(ctx: JavaParser.FormalParametersContext): MethodArguments {
        val argumentList = ctx.formalParameterList()?.formalParameter().orEmpty()
        val parsedArguments = argumentList.map { it.parse("Can't parse as an argument") }
        return parsedArguments.asMethodArguments()
    }

    override fun visitMethodCallExpression(ctx: JavaParser.MethodCallExpressionContext): SemgrepJavaPattern =
        ctx.methodCall().parse()

    override fun visitMethodCall(ctx: JavaParser.MethodCallContext): SemgrepJavaPattern {
        ctx.tryRule({ ellipsisExpression() }) { return Ellipsis }

        val methodName = ctx.methodIdentifier().identifier().parseName()
        val arguments = visitArguments(ctx.arguments())

        return MethodInvocation(methodName, obj = null, arguments)
    }

    override fun visitMemberReferenceExpression(ctx: JavaParser.MemberReferenceExpressionContext): SemgrepJavaPattern {
        ctx.tryRule({ methodCall() }) {
            val lhs = ctx.expression().parse("Unable to parse lhs of memberReferenceExpression")

            return when (val methodInvocation = visitMethodCall(it)) {
                is MethodInvocation -> MethodInvocation(
                    methodName = methodInvocation.methodName,
                    obj = lhs,
                    args = methodInvocation.args
                )

                is Ellipsis -> EllipsisMethodInvocations(
                    obj = lhs
                )

                else -> ctx.parsingFailed("Unexpected methodCall parsing result $methodInvocation")
            }
        }

        ctx.tryRule({ identifier() }) {
            val fieldName = it.parseName()
            val lhs = ctx.expression().asObject()
            return FieldAccess(fieldName, lhs)
        }

        ctx.parsingFailed("Unsupported member reference expression")
    }

    override fun visitSquareBracketExpression(ctx: JavaParser.SquareBracketExpressionContext): SemgrepJavaPattern =
        ctx.todo()

    override fun visitObjectCreationExpression(ctx: JavaParser.ObjectCreationExpressionContext): ObjectCreation {
        val creator = ctx.creator()
        val parsedType = creator.createdName().accept(typenameParser)

        creator.tryRule({ classCreatorRest() }) {
            val args = visitArguments(it.arguments())
            return ObjectCreation(parsedType, args)
        }

        creator.tryRule({ arrayCreatorRest() }) { arrayCtor ->
            arrayCtor.tryRule({ arrayInitializer() }) {
                it.todo()
            }

            arrayCtor.tryRule({ expression() }) {
                arrayCtor.todo()
            }
        }

        ctx.parsingFailed("Unexpected object creation")
    }

    override fun visitMethodDeclaration(ctx: JavaParser.MethodDeclarationContext): MethodDeclaration {
        val returnType = ctx.typeTypeOrVoid().accept(typenameParser)
        val name = ctx.identifier().parseName()
        val args = visitFormalParameters(ctx.formalParameters())
        val body = ctx.methodBody()?.parse() ?: Ellipsis

        return MethodDeclaration(name, returnType, args, body, modifiers = emptyList())
    }

    override fun visitConstructorDeclaration(ctx: JavaParser.ConstructorDeclarationContext): MethodDeclaration {
        val name = ctx.identifier().parseName()
        val args = visitFormalParameters(ctx.formalParameters())
        val body = ctx.constructorBody.parse("Can't parse body")
        return MethodDeclaration(name, returnType = null, args, body, modifiers = emptyList())
    }

    override fun visitClassDeclaration(ctx: JavaParser.ClassDeclarationContext): ClassDeclaration {
        val name = ctx.identifier().parseName()
        val body = ctx.classBody()?.parse() ?: Ellipsis
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
        ctx.tryRule({ ellipsisExpression() }) { return Ellipsis }

        val name = ctx.identifier().parseName()
        val value = ctx.elementValue().parse()
        return NamedValue(name, value)
    }

    override fun visitAnnotation(ctx: JavaParser.AnnotationContext): Annotation {
        val nameContext = ctx.qualifiedName() ?: ctx.altAnnotationQualifiedName()
        val name = nameContext.accept(typenameParser)

        val elementValue = ctx.elementValue()
        val elementValuePairs = ctx.elementValuePairs()
        val arguments = when {
            elementValue != null -> listOf(elementValue.parse())
            elementValuePairs != null -> elementValuePairs.elementValuePair().map { it.parse() }
            else -> emptyList()
        }

        return Annotation(name, arguments.asMethodArguments())
    }

    override fun visitBlock(ctx: JavaParser.BlockContext): SemgrepJavaPattern {
        val statements = ctx.blockStatement().map { it.parse() }
        return statements.asPatternSequence()
    }

    override fun visitTypeDeclSemgrepPattern(ctx: JavaParser.TypeDeclSemgrepPatternContext): SemgrepJavaPattern {
        val decl = ctx.typeDeclaration()
        val declaration = parseTypeDeclaration(decl)

        val modifiers = decl.classOrInterfaceModifier().map { parseModifier(it) }
        return declaration.withModifiers(ctx, modifiers)
    }

    private fun parseTypeDeclaration(decl: TypeDeclarationContext): SemgrepJavaPattern {
        decl.tryRule({ classDeclaration() }) { return it.parse() }
        decl.tryRule({ interfaceDeclaration() }) { it.todo() }
        decl.parsingFailed("Unexpected local type declaration")
    }

    override fun visitImportSemgrepPattern(ctx: JavaParser.ImportSemgrepPatternContext): SemgrepJavaPattern {
        val importNames = ctx.importDeclaration().qualifiedName().identifier().map { it.parseName() }
        val isConcrete = ctx.importDeclaration().MUL() == null
        return ImportStatement(importNames, isConcrete)
    }

    override fun visitMethodBodySemgrepPattern(ctx: JavaParser.MethodBodySemgrepPatternContext): SemgrepJavaPattern =
        ctx.patternStatement().parse()

    override fun visitClassBodySemgrepPattern(ctx: JavaParser.ClassBodySemgrepPatternContext): SemgrepJavaPattern =
        ctx.classBodyDeclaration().parse()

    override fun visitAnnotationSemgrepPattern(ctx: JavaParser.AnnotationSemgrepPatternContext): SemgrepJavaPattern =
        ctx.annotation().parse()

    override fun visitLocalTypeDeclaration(ctx: JavaParser.LocalTypeDeclarationContext): SemgrepJavaPattern {
        val classDecl = ctx.classDeclaration()
        val declaration = when {
            classDecl != null -> classDecl.parse()
            else -> ctx.parsingFailed("Unexpected local type declaration")
        }

        val modifiers = ctx.classOrInterfaceModifier().map { parseModifier(it) }

        return declaration.withModifiers(ctx, modifiers)
    }

    override fun visitClassBodyDeclaration(ctx: JavaParser.ClassBodyDeclarationContext): SemgrepJavaPattern {
        ctx.tryRule({ block() }) { return it.parse() }

        ctx.tryRule({ ellipsisExpression() }) { return Ellipsis }

        val declaration = ctx.memberDeclaration().parse("Can't parse declaration")

        val modifiers = ctx.modifier().map { parseModifier(it) }

        return declaration.withModifiers(ctx, modifiers)
    }

    private fun parseModifier(ctx: ClassOrInterfaceModifierContext): Modifier {
        val parsed = ctx.parse()
        if (parsed is Modifier) return parsed

        ctx.parsingFailed("Expected modifier, got $parsed")
    }

    private fun parseModifier(ctx: ModifierContext): Modifier {
        val parsed = ctx.parse()
        if (parsed is Modifier) return parsed

        ctx.parsingFailed("Expected modifier, got $parsed")
    }

    override fun visitClassBody(ctx: JavaParser.ClassBodyContext): SemgrepJavaPattern {
        val declarations = ctx.classBodyDeclaration().map { it.parse("Can't parse such declaration") }
        return declarations.asPatternSequence()
    }

    override fun visitReturnExpression(ctx: JavaParser.ReturnExpressionContext): SemgrepJavaPattern {
        val retVal = ctx.expression() ?: return ReturnStmt(value = null)
        return ReturnStmt(retVal.parse())
    }

    override fun visitExpressionDeepEllipsisExpr(ctx: JavaParser.ExpressionDeepEllipsisExprContext): SemgrepJavaPattern {
        val expr = ctx.deepEllipsisExpression().expression().parse()
        return DeepExpr(expr)
    }

    override fun visitExpressionEllipsisMetavar(ctx: JavaParser.ExpressionEllipsisMetavarContext): SemgrepJavaPattern {
        val metaVarName = ctx.ellipsisMetavarExpression().ELLIPSIS_METAVAR().text
        return EllipsisMetavar(metaVarName)
    }

    override fun visitCatchBlockSemgrepPattern(ctx: JavaParser.CatchBlockSemgrepPatternContext): SemgrepJavaPattern {
        val catchClause = ctx.patternCatchBlock().catchClause()
        val block = catchClause.block().parse()
        val exceptionTypes = catchClause.catchType().qualifiedName().map { it.accept(typenameParser) }
        val exceptionVar = catchClause.identifier().parseName()

        return CatchStatement(exceptionTypes, exceptionVar, block)
    }

    override fun visitIdentifier(ctx: IdentifierContext): SemgrepJavaPattern {
        val name = ctx.parseName()
        return when (name) {
            is ConcreteName -> Identifier(name.name)
            is MetavarName -> Metavar(name.metavarName)
            is Name.Pattern -> ctx.parsingFailed("Unknown identifier")
        }
    }

    override fun visitLiteral(ctx: JavaParser.LiteralContext): SemgrepJavaPattern {
        ctx.tryRule({ METAVAR_LITERAL() }) { return StringLiteral(MetavarName(it.text)) }
        ctx.tryRule({ ELLIPSIS_LITERAL() }) { return StringEllipsis }

        ctx.tryRule({ BOOL_LITERAL() }) {
            return when (it.text) {
                "true" -> BoolConstant(true)
                "false" -> BoolConstant(false)
                else -> ctx.parsingFailed("Unknown bool literal")
            }
        }

        ctx.tryRule({ STRING_LITERAL() }) { return StringLiteral(ConcreteName(it.text)) }

        ctx.tryRule({ NULL_LITERAL() }) { return NullLiteral }

        ctx.tryRule({ integerLiteral() }) { return IntLiteral(it.text) }

        ctx.parsingFailed("Unexpected literal")
    }

    override fun visitExpressionEllipsis(ctx: JavaParser.ExpressionEllipsisContext): SemgrepJavaPattern = Ellipsis

    override fun visitEllipsisExpression(ctx: JavaParser.EllipsisExpressionContext): SemgrepJavaPattern = Ellipsis

    override fun visitControlFlowStatement(ctx: JavaParser.ControlFlowStatementContext): SemgrepJavaPattern =
        throw ControlFlowStatementNotSupported(ctx)

    override fun visitPrimaryExpression(ctx: JavaParser.PrimaryExpressionContext): SemgrepJavaPattern =
        ctx.primary().parse()

    override fun visitPrimaryClassLiteral(ctx: JavaParser.PrimaryClassLiteralContext): SemgrepJavaPattern = ctx.todo()

    override fun visitPrimaryInvocation(ctx: JavaParser.PrimaryInvocationContext): SemgrepJavaPattern = ctx.todo()

    override fun visitMethodReferenceExpression(ctx: JavaParser.MethodReferenceExpressionContext): SemgrepJavaPattern =
        ctx.todo()

    override fun visitExpressionSwitch(ctx: JavaParser.ExpressionSwitchContext): SemgrepJavaPattern = ctx.todo()

    override fun visitPostIncrementDecrementOperatorExpression(ctx: JavaParser.PostIncrementDecrementOperatorExpressionContext): SemgrepJavaPattern =
        ctx.todo()

    override fun visitUnaryOperatorExpression(ctx: JavaParser.UnaryOperatorExpressionContext): SemgrepJavaPattern =
        ctx.todo()

    override fun visitCastExpression(ctx: JavaParser.CastExpressionContext): SemgrepJavaPattern = ctx.todo()

    override fun visitInstanceOfOperatorExpression(ctx: JavaParser.InstanceOfOperatorExpressionContext): SemgrepJavaPattern =
        ctx.todo()

    override fun visitTernaryExpression(ctx: JavaParser.TernaryExpressionContext): SemgrepJavaPattern = ctx.todo()

    override fun visitExpressionLambda(ctx: JavaParser.ExpressionLambdaContext): SemgrepJavaPattern = ctx.todo()

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

    private fun SemgrepJavaPattern.withModifiers(
        ctx: ParserRuleContext,
        modifiers: List<Modifier>
    ): SemgrepJavaPattern {
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

            else -> ctx.parsingFailed("Unexpected non-empty list of modifiers")
        }
    }

    private fun ParserRuleContext.asObject(): FieldAccess.Object {
        if (text == "super") {
            return FieldAccess.SuperObject
        }

        val obj = accept(this@SemgrepJavaPatternParserVisitor)
            ?: parsingFailed("Unable to parse as object pattern")

        return FieldAccess.ObjectPattern(obj)
    }

    private fun ParserRuleContext.parse(message: String? = null): SemgrepJavaPattern =
        accept(this@SemgrepJavaPatternParserVisitor)
            ?: (if (message == null) parsingFailed() else parsingFailed(message))
}

sealed class SemgrepParsingException(val element: ParserRuleContext, message: String) : Exception(message)

class SemgrepParsingFailedException(ctx: ParserRuleContext, additionalMessage: String) :
    SemgrepParsingException(ctx, "Exception during parsing ${ctx.text}: $additionalMessage")

class ControlFlowStatementNotSupported(element: ParserRuleContext) :
    SemgrepParsingException(element, "Control flow statements are not supported: ${element.text}")

class UnsupportedElement(element: ParserRuleContext) :
    SemgrepParsingException(element, "Unsupported element: ${element.text}")

private fun ParserRuleContext.parsingFailed(message: String = "Can't parse such statement"): Nothing =
    throw SemgrepParsingFailedException(this, message)

private fun ParserRuleContext.todo(): Nothing = throw UnsupportedElement(this)

private inline fun <reified T : ParserRuleContext, R> T.tryRule(rule: T.() -> R?, block: (R) -> Unit) {
    val element = this.rule() ?: return
    block(element)
}
