package org.opentaint.semgrep.pattern

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ConsoleErrorListener
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.opentaint.semgrep.pattern.TypeName.SimpleTypeName
import org.opentaint.semgrep.pattern.antlr.JavaLexer
import org.opentaint.semgrep.pattern.antlr.JavaParser
import org.opentaint.semgrep.pattern.antlr.JavaParser.AltAnnotationQualifiedNameContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.AnnotationContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ArgumentsContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.BinaryOperatorExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.BlockContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.CatchBlockSemgrepPatternContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.CatchClauseContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ClassBodyDeclarationContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ClassCreatorRestContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ClassDeclarationContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ClassOrInterfaceModifierContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ClassOrInterfaceTypeContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ConstructorDeclarationContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.CreatedNameContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.CreatorContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ElementValuePairContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.FormalParameterContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.FormalParameterMetavarContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.FormalParametersContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.IdentifierContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ImportSemgrepPatternContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.LiteralContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.LocalTypeDeclarationContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.LocalVariableDeclarationContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.MemberReferenceExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.MethodCallContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.MethodDeclarationContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ModifierContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.ObjectCreationExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.PatternsContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.PrimitiveTypeContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.QualifiedNameContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.SquareBracketExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeArgumentContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeDeclSemgrepPatternContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeDeclarationContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeIdentifierContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeTypeBodyContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeTypeBodySimpleContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeTypeContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypeTypeOrVoidContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.TypedVariableExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.VariableDeclaratorContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.VariableModifierContext
import org.opentaint.semgrep.pattern.antlr.JavaParserBaseVisitor
import java.util.Collections
import java.util.IdentityHashMap

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

private fun IdentifierContext.parseName(): Name = withRule {
    tryRule(IdentifierContext::METAVAR) { return MetavarName(it.text) }
    tryRule(IdentifierContext::ANONYMOUS_METAVAR) { this@parseName.todo() }
    tryRule(IdentifierContext::IDENTIFIER) { return ConcreteName(it.text) }
    unreachable()
}

private fun TypeIdentifierContext.parseTypeIdentifierName(): Name = withRule {
    tryRule(TypeIdentifierContext::METAVAR) { return MetavarName(it.text) }
    tryRule(TypeIdentifierContext::ANONYMOUS_METAVAR) { this@parseTypeIdentifierName.todo() }
    tryRule(TypeIdentifierContext::IDENTIFIER) { return ConcreteName(it.text) }
    unreachable()
}

private class TypenameParserVisitor : JavaParserBaseVisitor<TypeName>() {
    override fun defaultResult(): TypeName {
        // note: some grammar rules remain uncovered
        TODO("Can't parse typename")
    }

    override fun visitCreatedName(ctx: CreatedNameContext): TypeName = ctx.withRule {
        tryRule(CreatedNameContext::primitiveType) {
            return it.accept(this@TypenameParserVisitor)
        }

        val identifiers = value(CreatedNameContext::identifierWithTypeArgs)

        val dotSeparatedParts = mutableListOf<Name>()
        for ((i, identifier) in identifiers.withIndex()) {
            dotSeparatedParts += identifier.identifier().parseName()
            val typeArgs = identifier.typeArgumentsOrDiamond()?.typeArguments()
                ?: continue

            if (i != identifiers.lastIndex) {
                ctx.todo()
            }

            val parsedTypeArgs = parseTypeArgs(ctx, typeArgs.typeArgument())
            return SimpleTypeName(dotSeparatedParts, parsedTypeArgs)
        }

        return SimpleTypeName(dotSeparatedParts)
    }

    override fun visitTypeTypeOrVoid(ctx: TypeTypeOrVoidContext): TypeName = ctx.withRule {
        tryRule(TypeTypeOrVoidContext::VOID) { return SimpleTypeName(listOf(ConcreteName(it.text))) }
        tryRule(TypeTypeOrVoidContext::typeType) { return it.accept(this@TypenameParserVisitor) }
        unreachable()
    }

    override fun visitTypeType(ctx: TypeTypeContext): TypeName = ctx.withRule {
        value(TypeTypeContext::typeTypeBody).accept(this@TypenameParserVisitor)
    }

    override fun visitTypeTypeBody(ctx: TypeTypeBodyContext): TypeName = ctx.withRule {
        val simpleType = value(TypeTypeBodyContext::typeTypeBodySimple).accept(this@TypenameParserVisitor)

        val arrayModifier = value(TypeTypeBodyContext::typeTypeBodyArrayModifier)
        val arrayDimensions = arrayModifier.LBRACK().size

        var result: TypeName = simpleType
        repeat(arrayDimensions) { result = TypeName.ArrayTypeName(result) }
        result
    }

    override fun visitTypeTypeBodySimple(ctx: TypeTypeBodySimpleContext): TypeName = ctx.withRule {
        tryRule(TypeTypeBodySimpleContext::primitiveType) { return it.accept(this@TypenameParserVisitor) }
        tryRule(TypeTypeBodySimpleContext::classOrInterfaceType) { return it.accept(this@TypenameParserVisitor) }
        unreachable()
    }

    override fun visitPrimitiveType(ctx: PrimitiveTypeContext): TypeName =
        SimpleTypeName(listOf(ConcreteName(ctx.text)))

    override fun visitClassOrInterfaceType(ctx: ClassOrInterfaceTypeContext): TypeName = ctx.withRule {
        val prefix = value(ClassOrInterfaceTypeContext::identifier).map { it.parseName() }
        val final = value(ClassOrInterfaceTypeContext::typeIdentifier).parseTypeIdentifierName()

        val identifierTypeArgs = value(ClassOrInterfaceTypeContext::identifierTypeArguments)
        if (identifierTypeArgs.isNotEmpty()) {
            ctx.todo()
        }

        val typeTypeArguments = get(ClassOrInterfaceTypeContext::typeIdentifierTypeArguments)
            ?: return SimpleTypeName(prefix + final)

        val typeArgs = parseTypeArgs(ctx, typeTypeArguments.typeArguments().typeArgument())
        return SimpleTypeName(prefix + final, typeArgs)
    }

    private fun parseTypeArgs(ctx: ParserRuleContext, args: List<TypeArgumentContext>): List<TypeName> {
        val parsed = args.map { parseTypeArgument(it) }
        val parsedTypes = parsed.filterNotNull()
        if (parsedTypes.size == parsed.size) return parsedTypes

        // T<?>
        if (parsed.size == 1 && parsedTypes.isEmpty()) return emptyList()

        ctx.todo()
    }

    private fun parseTypeArgument(ctx: TypeArgumentContext): TypeName? = ctx.withRule {
        tryRule(TypeArgumentContext::typeType) { return it.accept(this@TypenameParserVisitor) }

        tryRule(TypeArgumentContext::typeArgumentWildcard) {
            if (it.typeType() != null || it.annotation().isNotEmpty()) {
                it.todo()
            }

            return null
        }

        unreachable()
    }

    override fun visitQualifiedName(ctx: QualifiedNameContext): TypeName = ctx.withRule {
        val parts = value(QualifiedNameContext::identifier).map { it.parseName() }
        return SimpleTypeName(parts)
    }

    override fun visitAltAnnotationQualifiedName(ctx: AltAnnotationQualifiedNameContext): TypeName = ctx.withRule {
        val parts = value(AltAnnotationQualifiedNameContext::identifier).map { it.parseName() }
        return SimpleTypeName(parts)
    }
}

private class SemgrepJavaPatternParserVisitor : JavaParserBaseVisitor<SemgrepJavaPattern?>() {
    private val typenameParser = TypenameParserVisitor()

    override fun visitPatterns(ctx: PatternsContext): SemgrepJavaPattern = ctx.withRule {
        value(PatternsContext::semgrepPatternElement).map { it.parse() }.asPatternSequence()
    }

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

    override fun visitBinaryOperatorExpression(ctx: BinaryOperatorExpressionContext): SemgrepJavaPattern = ctx.withRule {
        val operator = ctx.bop
        val expression = value(BinaryOperatorExpressionContext::expression)
        val lhs = expression[0].parse("Unable to parse lhs")
        val rhs = expression[1].parse("Unable to parse rhs")

        return when (operator.type) {
            JavaParser.ASSIGN -> VariableAssignment(null, lhs, rhs)
            JavaParser.ADD_ASSIGN -> VariableAssignment(null, lhs, AddExpr(lhs, rhs))
            JavaParser.ADD -> AddExpr(lhs, rhs)
            else -> ctx.todo()
        }
    }

    override fun visitTypedVariableExpression(ctx: TypedVariableExpressionContext): TypedMetavar = ctx.withRule {
        val type = value(TypedVariableExpressionContext::typeTypeOrVoid).accept(typenameParser)
        val name = value(TypedVariableExpressionContext::identifier).parseName() as? MetavarName
            ?: ctx.parsingFailed("Expected variable name to be a metavar name")

        return TypedMetavar(name.metavarName, type)
    }

    override fun visitVariableDeclarator(ctx: VariableDeclaratorContext): VariableAssignment = ctx.withRule {
        val variable = value(VariableDeclaratorContext::variableDeclaratorId).parse("Can't parse variable name")
        val initializer = get(VariableDeclaratorContext::variableInitializer)
        val value = initializer?.parse("Can't parse initializer")
        return VariableAssignment(type = null, variable, value)
    }

    override fun visitLocalVariableDeclaration(ctx: LocalVariableDeclarationContext): SemgrepJavaPattern = ctx.withRule {
        tryRule(LocalVariableDeclarationContext::VAR) {
            val variable = value(LocalVariableDeclarationContext::identifier).parse("Can't parse variable")
            val value = value(LocalVariableDeclarationContext::expression).parse("Can't parse variable initialization")
            return VariableAssignment(type = null, variable, value)
        }

        val type = value(LocalVariableDeclarationContext::typeType).accept(typenameParser)
        val declarators = value(LocalVariableDeclarationContext::variableDeclarators)

        val modifiers = value(LocalVariableDeclarationContext::variableModifier)

        val annotationModifier = mutableListOf<AnnotationContext>()
        for (modifier in modifiers) {
            if (modifier.annotation() != null) {
                annotationModifier.add(modifier.annotation())
            }
        }

        if (annotationModifier.isNotEmpty()) {
            ctx.todo()
        }

        val assignments = declarators.variableDeclarator().map { declarator ->
            val declaration = visitVariableDeclarator(declarator)
            declaration.let {
                VariableAssignment(type, it.variable, it.value)
            }
        }

        return assignments.asPatternSequence()
    }

    override fun visitArguments(ctx: ArgumentsContext): MethodArguments = ctx.withRule {
        val argumentList = get(ArgumentsContext::expressionList)?.expression().orEmpty()
        val parsedArguments = argumentList.map { it.parse("Can't parse as an argument") }
        return parsedArguments.asMethodArguments()
    }

    override fun visitFormalParameter(ctx: FormalParameterContext): SemgrepJavaPattern = ctx.withRule {
        tryRule(FormalParameterContext::ellipsisExpression) { return Ellipsis }
        tryRule(FormalParameterContext::formalParameterMetavar) { return parseFormalParameterMetavar(it) }
        tryRule(FormalParameterContext::variableDeclaratorId) {
            val name = it.identifier().parseName()
            val type = value(FormalParameterContext::typeType).accept(typenameParser) ?: ctx.parsingFailed()
            val modifiers = value(FormalParameterContext::variableModifier).mapNotNull { parseModifier(it) }
            return FormalArgument(name, type, modifiers)
        }
        unreachable()
    }

    private fun parseFormalParameterMetavar(ctx: FormalParameterMetavarContext): SemgrepJavaPattern = ctx.withRule {
        tryRule(FormalParameterMetavarContext::METAVAR) { return Metavar(it.text) }
        tryRule(FormalParameterMetavarContext::ANONYMOUS_METAVAR) { ctx.todo() }
        unreachable()
    }

    override fun visitFormalParameters(ctx: FormalParametersContext): MethodArguments = ctx.withRule {
        val argumentList = get(FormalParametersContext::formalParameterList)?.formalParameter().orEmpty()
        val parsedArguments = argumentList.map { it.parse("Can't parse as an argument") }
        return parsedArguments.asMethodArguments()
    }

    override fun visitMethodCallExpression(ctx: JavaParser.MethodCallExpressionContext): SemgrepJavaPattern =
        ctx.methodCall().parse()

    override fun visitMethodCall(ctx: MethodCallContext): SemgrepJavaPattern = ctx.withRule {
        tryRule(MethodCallContext::ellipsisExpression) { return Ellipsis }

        val methodName = value(MethodCallContext::methodIdentifier).identifier().parseName()
        val arguments = visitArguments(value(MethodCallContext::arguments))

        return MethodInvocation(methodName, obj = null, arguments)
    }

    override fun visitMemberReferenceExpression(ctx: MemberReferenceExpressionContext): SemgrepJavaPattern = ctx.withRule {
        tryRule(MemberReferenceExpressionContext::methodCall) {
            val lhs = value(MemberReferenceExpressionContext::expression)
                .parse("Unable to parse lhs of memberReferenceExpression")

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

        tryRule(MemberReferenceExpressionContext::identifier) {
            val fieldName = it.parseName()
            val lhs = value(MemberReferenceExpressionContext::expression).asObject()
            return FieldAccess(fieldName, lhs)
        }

        unreachable()
    }

    override fun visitSquareBracketExpression(ctx: SquareBracketExpressionContext): SemgrepJavaPattern = ctx.withRule {
        val expressions = value(SquareBracketExpressionContext::expression).map { it.parse() }
        if (expressions.size != 2) {
            ctx.todo()
        }

        val (arrayRef, arrayIndex) = expressions

        ArrayAccess(arrayRef, arrayIndex)
    }

    override fun visitObjectCreationExpression(ctx: ObjectCreationExpressionContext): ObjectCreation {
        val creator = ctx.creator()
        creator.withRule {
            val parsedType = value(CreatorContext::createdName).accept(typenameParser)

            tryRule(CreatorContext::classCreatorRest) { classCtor ->
                classCtor.withRule {
                    val args = visitArguments(value(ClassCreatorRestContext::arguments))
                    val body = get(ClassCreatorRestContext::classBody)

                    if (body != null) classCtor.todo()

                    return ObjectCreation(parsedType, args)
                }
            }

            tryRule(CreatorContext::arrayCreatorRest) { arrayCtor ->
                arrayCtor.todo()
            }

            unreachable()
        }
    }

    override fun visitArrayInitializer(ctx: JavaParser.ArrayInitializerContext): SemgrepJavaPattern? {
        ctx.todo()
    }

    override fun visitMethodDeclaration(ctx: MethodDeclarationContext): MethodDeclaration = ctx.withRule {
        val returnType = value(MethodDeclarationContext::typeTypeOrVoid).accept(typenameParser)
        val name = value(MethodDeclarationContext::identifier).parseName()
        val args = visitFormalParameters(value(MethodDeclarationContext::formalParameters))
        val body = get(MethodDeclarationContext::methodBody)?.parse() ?: Ellipsis

        return MethodDeclaration(name, returnType, args, body, modifiers = emptyList())
    }

    override fun visitConstructorDeclaration(ctx: ConstructorDeclarationContext): MethodDeclaration = ctx.withRule {
        val name = value(ConstructorDeclarationContext::identifier).parseName()
        val args = visitFormalParameters(value(ConstructorDeclarationContext::formalParameters))
        val body = value(ConstructorDeclarationContext::constructorBody).parse("Can't parse body")
        return MethodDeclaration(name, returnType = null, args, body, modifiers = emptyList())
    }

    override fun visitClassDeclaration(ctx: ClassDeclarationContext): ClassDeclaration = ctx.withRule {
        val name = value(ClassDeclarationContext::identifier).parseName()
        val body = get(ClassDeclarationContext::classBody)?.parse() ?: Ellipsis
        val extends = get(ClassDeclarationContext::classExtends)?.accept(typenameParser)
        val implements = get(ClassDeclarationContext::classImplements)
            ?.typeType().orEmpty().map { it.accept(typenameParser) }

        return ClassDeclaration(
            name = name,
            extends = extends,
            implements = implements,
            modifiers = emptyList(),
            body = body
        )
    }

    override fun visitElementValuePair(ctx: ElementValuePairContext): SemgrepJavaPattern = ctx.withRule {
        tryRule(ElementValuePairContext::ellipsisExpression) { return Ellipsis }

        val name = value(ElementValuePairContext::identifier).parseName()
        val value = value(ElementValuePairContext::elementValue).parse()
        return NamedValue(name, value)
    }

    override fun visitAnnotation(ctx: AnnotationContext): Annotation = ctx.withRule {
        val nameContext = get(AnnotationContext::qualifiedName)
            ?: value(AnnotationContext::altAnnotationQualifiedName)

        val name = nameContext.accept(typenameParser)

        val elementValue = get(AnnotationContext::elementValue)
        val elementValuePairs = get(AnnotationContext::elementValuePairs)
        val arguments = when {
            elementValue != null -> listOf(elementValue.parse())
            elementValuePairs != null -> elementValuePairs.elementValuePair().map { it.parse() }
            else -> emptyList()
        }

        return Annotation(name, arguments.asMethodArguments())
    }

    override fun visitBlock(ctx: BlockContext): SemgrepJavaPattern = ctx.withRule {
        val statements = value(BlockContext::blockStatement).mapNotNull { it.parse() }
        return statements.asPatternSequence()
    }

    override fun visitTypeDeclSemgrepPattern(ctx: TypeDeclSemgrepPatternContext): SemgrepJavaPattern = ctx.withRule {
        val decl = value(TypeDeclSemgrepPatternContext::typeDeclaration)
        decl.withRule {
            val declaration = parseTypeDeclaration()
            val modifiers = value(TypeDeclarationContext::classOrInterfaceModifier).mapNotNull { parseModifier(it) }
            declaration.withModifiers(ctx, modifiers)
        }
    }

    private fun RuleCtx<TypeDeclarationContext>.parseTypeDeclaration(): SemgrepJavaPattern {
        tryRule(TypeDeclarationContext::classDeclaration) { return it.parse() }
        tryRule(TypeDeclarationContext::interfaceDeclaration) { it.todo() }
        unreachable()
    }

    override fun visitImportSemgrepPattern(ctx: ImportSemgrepPatternContext): SemgrepJavaPattern = ctx.withRule {
        val importDecl = value(ImportSemgrepPatternContext::importDeclaration)
        val importNames = importDecl.qualifiedName().identifier().map { it.parseName() }
        val isConcrete = importDecl.MUL() == null
        return ImportStatement(importNames, isConcrete)
    }

    override fun visitMethodBodySemgrepPattern(ctx: JavaParser.MethodBodySemgrepPatternContext): SemgrepJavaPattern =
        ctx.patternStatement().parse()

    override fun visitClassBodySemgrepPattern(ctx: JavaParser.ClassBodySemgrepPatternContext): SemgrepJavaPattern =
        ctx.classBodyDeclaration().parse()

    override fun visitAnnotationSemgrepPattern(ctx: JavaParser.AnnotationSemgrepPatternContext): SemgrepJavaPattern =
        ctx.annotation().parse()

    override fun visitLocalTypeDeclaration(ctx: LocalTypeDeclarationContext): SemgrepJavaPattern = ctx.withRule {
        val classDecl = get(LocalTypeDeclarationContext::classDeclaration)
        val declaration = when {
            classDecl != null -> classDecl.parse()
            else -> ctx.parsingFailed("Unexpected local type declaration")
        }

        val modifiers = value(LocalTypeDeclarationContext::classOrInterfaceModifier)
            .mapNotNull { parseModifier(it) }

        return declaration.withModifiers(ctx, modifiers)
    }

    override fun visitClassBodyDeclaration(ctx: ClassBodyDeclarationContext): SemgrepJavaPattern = ctx.withRule {
        tryRule(ClassBodyDeclarationContext::block) { return it.parse() }
        tryRule(ClassBodyDeclarationContext::ellipsisExpression) { return Ellipsis }

        val declaration = value(ClassBodyDeclarationContext::memberDeclaration).parse("Can't parse declaration")
        val modifiers = value(ClassBodyDeclarationContext::modifier).mapNotNull { parseModifier(it) }
        return declaration.withModifiers(ctx, modifiers)
    }

    private fun parseModifier(ctx: ClassOrInterfaceModifierContext): Modifier? {
        val annotation = ctx.annotation() ?: return null
        return parseAnnotation(annotation)
    }

    private fun parseModifier(ctx: ModifierContext): Modifier? =
        parseModifier(ctx.classOrInterfaceModifier())

    private fun parseModifier(ctx: VariableModifierContext): Modifier? {
        val annotation = ctx.annotation() ?: return null
        return parseAnnotation(annotation)
    }

    private fun parseAnnotation(annotation: AnnotationContext): Modifier {
        val parsed = annotation.parse()
        if (parsed is Modifier) return parsed

        annotation.parsingFailed("Expected modifier, got $parsed")
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

    override fun visitCatchBlockSemgrepPattern(ctx: CatchBlockSemgrepPatternContext): SemgrepJavaPattern {
        val catchClause = ctx.patternCatchBlock().catchClause()
        catchClause.withRule {
            val block = value(CatchClauseContext::block).parse()
            val exceptionTypes = value(CatchClauseContext::catchType).qualifiedName().map { it.accept(typenameParser) }
            val exceptionVar = value(CatchClauseContext::identifier).parseName()

            return CatchStatement(exceptionTypes, exceptionVar, block)
        }
    }

    override fun visitIdentifier(ctx: IdentifierContext): SemgrepJavaPattern {
        val name = ctx.parseName()
        return when (name) {
            is ConcreteName -> Identifier(name.name)
            is MetavarName -> Metavar(name.metavarName)
        }
    }

    override fun visitLiteral(ctx: LiteralContext): SemgrepJavaPattern = ctx.withRule {
        tryRule(LiteralContext::METAVAR_LITERAL) { return StringLiteral(MetavarName(it.text.stringLiteralValue())) }
        tryRule(LiteralContext::ELLIPSIS_LITERAL) { return StringEllipsis }
        tryRule(LiteralContext::STRING_LITERAL) { return StringLiteral(ConcreteName(it.text.stringLiteralValue())) }
        tryRule(LiteralContext::NULL_LITERAL) { return NullLiteral }
        tryRule(LiteralContext::integerLiteral) { return IntLiteral(it.text) }
        tryRule(LiteralContext::BOOL_LITERAL) {
            return when (it.text) {
                "true" -> BoolConstant(true)
                "false" -> BoolConstant(false)
                else -> ctx.parsingFailed("Unknown bool literal")
            }
        }

        unreachable()
    }

    private fun String.stringLiteralValue(): String {
        check(length >= 2 && first() == '"' && last() == '"') { "Quote expected" }
        return substring(1, length - 1)
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

private class RuleCtx<T: ParserRuleContext>(val rule: T) {
    val accessedRules = Collections.newSetFromMap<ParseTree>(IdentityHashMap())

    inline fun <reified T : ParserRuleContext, R : ParseTree> RuleCtx<T>.get(body: T.() -> R?): R? {
        val r = rule.body() ?: return null
        accessedRules.add(r)
        return r
    }

    @JvmName("getList")
    inline fun <reified T : ParserRuleContext, R : ParseTree> RuleCtx<T>.get(body: T.() -> List<R>?): List<R>? {
        val r = rule.body() ?: return null
        accessedRules.addAll(r)
        return r
    }

    inline fun <reified T : ParserRuleContext, R : ParseTree> RuleCtx<T>.value(body: T.() -> R?): R {
        val r = rule.body() ?: error("Required property is null")
        accessedRules.add(r)
        return r
    }

    @JvmName("valueList")
    inline fun <reified T : ParserRuleContext, R : ParseTree> RuleCtx<T>.value(body: T.() -> List<R>?): List<R> {
        val r = rule.body() ?: error("Required property is null")
        accessedRules.addAll(r)
        return r
    }

    inline fun <reified T : ParserRuleContext, R : ParseTree> RuleCtx<T>.tryRule(getter: T.() -> R?, body: (R) -> Unit) {
        val r = get(getter) ?: return
        body(r)
    }

    fun unreachable(): Nothing = TODO("Unreachable")
}

private inline fun <reified T : ParserRuleContext, R> T.withRule(body: RuleCtx<T>.() -> R): R {
    val ctx = RuleCtx(this)
    var exceptional = false
    try {
        return ctx.body()
    } catch (ex: Throwable) {
        exceptional = true
        throw ex
    } finally {
        if (!exceptional) {
            children.filterIsInstance<RuleNode>().forEach {
                if (it !in ctx.accessedRules) {
                    TODO("Missed rule: ${it.text}")
                }
            }
        }
    }
}
