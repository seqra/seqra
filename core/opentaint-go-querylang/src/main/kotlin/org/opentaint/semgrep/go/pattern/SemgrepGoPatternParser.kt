package org.opentaint.semgrep.go.pattern

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ConsoleErrorListener
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.TerminalNode
import org.opentaint.semgrep.pattern.PatternParsingAstFailed
import org.opentaint.semgrep.pattern.PatternParsingFailure
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import org.opentaint.semgrep.pattern.go.antlr.GoLexer
import org.opentaint.semgrep.pattern.go.antlr.GoParser
import org.opentaint.semgrep.pattern.go.antlr.GoParserBaseVisitor

sealed interface SemgrepGoPatternParsingResult {
    data class Ok(val pattern: SemgrepGoPattern) : SemgrepGoPatternParsingResult
    data class ParserFailure(val exception: SemgrepGoParsingException) : SemgrepGoPatternParsingResult
    data class OtherFailure(val exception: Throwable) : SemgrepGoPatternParsingResult
    data class FailedASTParsing(val errorMessages: List<String>) : SemgrepGoPatternParsingResult
}

sealed class SemgrepGoParsingException(val element: ParserRuleContext, message: String) : Exception(message)

class SemgrepGoParsingFailedException(ctx: ParserRuleContext, additionalMessage: String) :
    SemgrepGoParsingException(ctx, "Exception during parsing ${ctx.text}: $additionalMessage")

class UnsupportedGoElement(element: ParserRuleContext) :
    SemgrepGoParsingException(element, "Unsupported element: ${element.text}")

class SemgrepGoPatternParser : SemgrepPatternParser<SemgrepGoPattern> {
    private val visitor = SemgrepGoPatternParserVisitor()

    override fun parseOrNull(pattern: String, semgrepTrace: SemgrepRuleLoadStepTrace): SemgrepGoPattern? =
        when (val r = parseSemgrepGoPattern(pattern)) {
            is SemgrepGoPatternParsingResult.Ok -> r.pattern
            is SemgrepGoPatternParsingResult.FailedASTParsing -> {
                semgrepTrace.error(PatternParsingAstFailed(r.errorMessages))
                null
            }
            is SemgrepGoPatternParsingResult.ParserFailure -> {
                semgrepTrace.error(PatternParsingFailure(r.exception.message))
                null
            }
            is SemgrepGoPatternParsingResult.OtherFailure -> {
                semgrepTrace.error(PatternParsingFailure(r.exception.message))
                null
            }
        }

    fun parseSemgrepGoPattern(pattern: String): SemgrepGoPatternParsingResult {
        val errors = mutableListOf<String>()

        val lexer = GoLexer(CharStreams.fromString(pattern)).apply { configureErrorListener(errors) }
        val tokens = CommonTokenStream(lexer)
        val parser = GoParser(tokens).apply { configureErrorListener(errors) }

        val tree = try {
            parser.semgrepPattern()
        } catch (t: Throwable) {
            return SemgrepGoPatternParsingResult.OtherFailure(t)
        }
        if (errors.isNotEmpty()) {
            return SemgrepGoPatternParsingResult.FailedASTParsing(errors)
        }

        return try {
            val ast = visitor.visit(tree)
                ?: return SemgrepGoPatternParsingResult.OtherFailure(IllegalStateException("Null AST"))
            SemgrepGoPatternParsingResult.Ok(ast)
        } catch (e: SemgrepGoParsingException) {
            SemgrepGoPatternParsingResult.ParserFailure(e)
        } catch (t: Throwable) {
            SemgrepGoPatternParsingResult.OtherFailure(t)
        }
    }

    private fun Recognizer<*, *>.configureErrorListener(errors: MutableList<String>) {
        removeErrorListener(ConsoleErrorListener.INSTANCE)
        addErrorListener(object : BaseErrorListener() {
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
}

// ----------------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------------

private fun GoParser.IdentifierContext.parseName(): Name {
    METAVAR_IDENT()?.let { return MetavarName(it.text) }
    METAVAR_STAR_IDENT()?.let { throw SemgrepGoParsingFailedException(this, "Star is not expected here") }
    METAVAR_ELLIPSIS()?.let { return MetavarName(it.text.removePrefix("$...")) }
    ANONYMOUS_METAVAR()?.let { return MetavarName("_") }
    return ConcreteName(text)
}

private fun unquoteString(raw: String): String {
    if (raw.length >= 2 && (raw.first() == '"' || raw.first() == '`') && raw.last() == raw.first()) {
        return raw.substring(1, raw.length - 1)
    }
    return raw
}

// ----------------------------------------------------------------------------
// Visitor
// ----------------------------------------------------------------------------

private class SemgrepGoPatternParserVisitor : GoParserBaseVisitor<SemgrepGoPattern?>() {

    // --- top level ---

    override fun visitSemgrepPattern(ctx: GoParser.SemgrepPatternContext): SemgrepGoPattern {
        ctx.packageClause()?.let { pkgCtx ->
            val name = pkgCtx.packageName().identifier().parseName()
            return PackageOnlyPattern(PackageClause(name))
        }
        ctx.semgrepTopList()?.let { return visitSemgrepTopList(it) }
        throw UnsupportedGoElement(ctx)
    }

    override fun visitSemgrepTopList(ctx: GoParser.SemgrepTopListContext): SemgrepGoPattern {
        val items = ctx.semgrepTopItem().map { parseTopItem(it) }
        return TopList(items)
    }

    private fun parseTopItem(ctx: GoParser.SemgrepTopItemContext): SemgrepGoPattern {
        ctx.ELLIPSIS()?.let { return EllipsisStmt }
        ctx.packageClause()?.let { return PackageClause(it.packageName().identifier().parseName()) }
        ctx.importDecl()?.let { return parseImportDecl(it) }
        ctx.functionDecl()?.let { return parseFunctionDecl(it) }
        ctx.methodDecl()?.let { return parseMethodDecl(it) }
        ctx.declaration()?.let { return parseDeclaration(it) }
        ctx.statement()?.let { return parseStatement(it) }
        ctx.compositeLit()?.let { return parseCompositeLit(it) }
        throw UnsupportedGoElement(ctx)
    }

    // --- declarations ---

    private fun parseImportDecl(ctx: GoParser.ImportDeclContext): ImportDecl {
        val specs = ctx.importSpec().map { parseImportSpec(it) }
        return ParsedImportDecl(specs, hasEllipsis = ctx.ELLIPSIS().isNotEmpty())
    }

    private fun parseImportSpec(ctx: GoParser.ImportSpecContext): ImportSpec {
        val dotImport = ctx.DOT() != null
        val alias = ctx.packageName()?.identifier()?.parseName()
        val path = parseString(ctx.importPath().string_())
        return ImportSpec(alias, dotImport, path)
    }

    private fun parseDeclaration(ctx: GoParser.DeclarationContext): SemgrepGoPattern {
        ctx.varDecl()?.let { return parseVarDecl(it) }
        ctx.constDecl()?.let { return parseConstDecl(it) }
        ctx.typeDecl()?.let { return parseTypeDecl(it) }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseVarDecl(ctx: GoParser.VarDeclContext): VarDecl =
        VarDecl(ctx.varSpec().map { parseVarSpec(it) })

    private fun parseVarSpec(ctx: GoParser.VarSpecContext): ValueSpec {
        val names = ctx.identifierList().identifier().map { it.parseName() }
        val type = ctx.type_()?.let { parseType(it) }
        val values = ctx.expressionList()?.expression()?.map { parseExpression(it) }.orEmpty()
        return ValueSpec(names, type, values)
    }

    private fun parseConstDecl(ctx: GoParser.ConstDeclContext): ConstDecl =
        ConstDecl(ctx.constSpec().map { parseConstSpec(it) })

    private fun parseConstSpec(ctx: GoParser.ConstSpecContext): ValueSpec {
        val names = ctx.identifierList().identifier().map { it.parseName() }
        val type = ctx.type_()?.let { parseType(it) }
        val values = ctx.expressionList()?.expression()?.map { parseExpression(it) }.orEmpty()
        return ValueSpec(names, type, values)
    }

    private fun parseTypeDecl(ctx: GoParser.TypeDeclContext): TypeDecl =
        TypeDecl(ctx.typeSpec().map { parseTypeSpec(it) })

    private fun parseTypeSpec(ctx: GoParser.TypeSpecContext): TypeSpec {
        ctx.aliasDecl()?.let { a ->
            return TypeSpec(a.identifier().parseName(), alias = true, type = parseType(a.type_()))
        }
        ctx.typeDef()?.let { d ->
            return TypeSpec(d.identifier().parseName(), alias = false, type = parseType(d.type_()))
        }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseFunctionDecl(ctx: GoParser.FunctionDeclContext): FuncDecl {
        val name = ctx.identifier().parseName()
        val sig = parseSignature(ctx.signature())
        val body = ctx.block()?.let { parseBlock(it) }
        return FuncDecl(name, sig, body)
    }

    private fun parseMethodDecl(ctx: GoParser.MethodDeclContext): MethodDecl {
        val receivers = parseParameters(ctx.receiver().parameters())
        val receiver = receivers.firstOrNull()
            ?: NamedParam(emptyList(), NamedType(ConcreteName("_")), variadic = false)
        val name = ctx.identifier().parseName()
        val sig = parseSignature(ctx.signature())
        val body = ctx.block()?.let { parseBlock(it) }
        return MethodDecl(receiver, name, sig, body)
    }

    private fun parseSignature(ctx: GoParser.SignatureContext): FuncType {
        val params = parseParameters(ctx.parameters())
        val results = ctx.result()?.let { parseResult(it) }.orEmpty()
        return FuncType(params, results)
    }

    private fun parseResult(ctx: GoParser.ResultContext): List<ParameterDecl> {
        ctx.parameters()?.let { return parseParameters(it) }
        ctx.type_()?.let {
            return listOf(NamedParam(emptyList(), parseType(it), variadic = false))
        }
        return emptyList()
    }

    private fun parseParameters(ctx: GoParser.ParametersContext): List<ParameterDecl> =
        ctx.parameterDecl().map { parseParameterDecl(it) }

    private fun parseParameterDecl(ctx: GoParser.ParameterDeclContext): ParameterDecl {
        ctx.METAVAR_ELLIPSIS()?.let { return EllipsisMetavarParam(it.text.removePrefix("$...")) }
        ctx.METAVAR_IDENT()?.let { return MetavarParam(it.text) }
        ctx.METAVAR_STAR_IDENT()?.let { TODO("star") }
        if (ctx.type_() == null && ctx.ELLIPSIS() != null) return EllipsisParam
        val type = ctx.type_()?.let { parseType(it) } ?: return EllipsisParam
        val names = ctx.identifierList()?.identifier()?.map { it.parseName() }.orEmpty()
        val variadic = ctx.ELLIPSIS() != null
        return NamedParam(names, type, variadic)
    }

    // --- types ---

    private fun parseType(ctx: GoParser.Type_Context): TypeName {
        ctx.typeName()?.let { tn ->
            val typeArgs = ctx.typeArgs()?.let { parseTypeArgs(it) }.orEmpty()
            return parseTypeName(tn, typeArgs)
        }
        ctx.typeLit()?.let { return parseTypeLit(it) }
        ctx.type_()?.let { return parseType(it) }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseTypeName(ctx: GoParser.TypeNameContext, typeArgs: List<TypeName>): TypeName {
        ctx.qualifiedIdent()?.let { q ->
            val (pkg, sel) = parseQualifiedIdentParts(q)
            return QualifiedType(pkg, sel, typeArgs)
        }
        ctx.METAVAR_IDENT()?.let { return MetavarType(it.text) }
        ctx.IDENTIFIER()?.let { return NamedType(ConcreteName(it.text), typeArgs) }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseTypeArgs(ctx: GoParser.TypeArgsContext): List<TypeName> {
        val list = ctx.typeList() ?: return emptyList()
        return list.type_().map { parseType(it) }
    }

    private fun parseTypeLit(ctx: GoParser.TypeLitContext): TypeName {
        ctx.arrayType()?.let { return parseArrayType(it) }
        ctx.structType()?.let { return parseStructType(it) }
        ctx.pointerType()?.let { return PointerType(parseType(it.type_())) }
        ctx.functionType()?.let { return parseSignature(it.signature()) }
        ctx.interfaceType()?.let { return parseInterfaceType(it) }
        ctx.sliceType()?.let { return SliceType(parseType(it.elementType().type_())) }
        ctx.mapType()?.let {
            return MapType(parseType(it.type_()), parseType(it.elementType().type_()))
        }
        ctx.channelType()?.let { return parseChanType(it) }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseArrayType(ctx: GoParser.ArrayTypeContext): ArrayType {
        val len = parseExpression(ctx.arrayLength().expression())
        val elem = parseType(ctx.elementType().type_())
        return ArrayType(len, elem)
    }

    private fun parseStructType(ctx: GoParser.StructTypeContext): StructType {
        val fields = ctx.fieldDecl().map { parseFieldDecl(it) }
        return StructType(fields)
    }

    private fun parseFieldDecl(ctx: GoParser.FieldDeclContext): FieldDecl {
        if (ctx.ELLIPSIS() != null && ctx.identifierList() == null && ctx.embeddedField() == null) {
            return EllipsisField
        }
        val tag = ctx.string_()?.text
        ctx.embeddedField()?.let { ef ->
            val tn = ef.typeName()
            val embedded = parseTypeName(tn, emptyList())
            val withPtr = if (ef.STAR() != null) PointerType(embedded) else embedded
            return NamedFieldDecl(emptyList(), null, withPtr, tag)
        }
        val names = ctx.identifierList()?.identifier()?.map { it.parseName() }.orEmpty()
        val type = ctx.type_()?.let { parseType(it) }
        return NamedFieldDecl(names, type, null, tag)
    }

    private fun parseInterfaceType(ctx: GoParser.InterfaceTypeContext): InterfaceType {
        val elems = mutableListOf<InterfaceElem>()
        // Iterate children in order so we keep the semgrep ellipsis position.
        for (i in 0 until ctx.childCount) {
            val c = ctx.getChild(i)
            when (c) {
                is GoParser.MethodSpecContext -> {
                    val name = c.identifier().parseName()
                    val sig = FuncType(
                        params = parseParameters(c.parameters()),
                        results = c.result()?.let { parseResult(it) }.orEmpty()
                    )
                    elems.add(MethodSpec(name, sig))
                }
                is GoParser.TypeElementContext -> {
                    val ts = c.typeTerm().map { parseType(it.type_()) }
                    ts.forEach { elems.add(EmbeddedType(it)) }
                }
                is TerminalNode -> {
                    if (c.symbol.type == GoParser.ELLIPSIS) elems.add(EllipsisInterfaceElem)
                }
            }
        }
        return InterfaceType(elems)
    }

    private fun parseChanType(ctx: GoParser.ChannelTypeContext): ChanType {
        val elem = parseType(ctx.elementType().type_())
        // CHAN, RECEIVE CHAN, CHAN RECEIVE
        val first = ctx.getChild(0)?.text
        val second = if (ctx.childCount >= 2) ctx.getChild(1)?.text else null
        val dir = when {
            first == "<-" -> ChanDir.RECV
            first == "chan" && second == "<-" -> ChanDir.SEND
            else -> ChanDir.BIDI
        }
        return ChanType(dir, elem)
    }

    // --- statements ---

    private fun parseBlock(ctx: GoParser.BlockContext): BlockStmt {
        val stmts = ctx.statementList()?.statement()?.map { parseStatement(it) }.orEmpty()
        return BlockStmt(stmts)
    }

    private fun parseStatement(ctx: GoParser.StatementContext): SemgrepGoPattern {
        ctx.declaration()?.let { return parseDeclaration(it) }
        ctx.labeledStmt()?.let { l ->
            val name = l.identifier().parseName()
            val inner = l.statement()?.let { parseStatement(it) }
            return LabeledStmt(name, inner)
        }
        ctx.simpleStmt()?.let { return parseSimpleStmt(it) }
        ctx.goStmt()?.let { return GoStmt(parseExpression(it.expression())) }
        ctx.returnStmt()?.let { r ->
            val vals = r.expressionList()?.expression()?.map { parseExpression(it) }.orEmpty()
            return ReturnStmt(vals)
        }
        ctx.breakStmt()?.let { return BranchStmt(BranchKind.BREAK, it.identifier()?.parseName()) }
        ctx.continueStmt()?.let { return BranchStmt(BranchKind.CONTINUE, it.identifier()?.parseName()) }
        ctx.gotoStmt()?.let { return BranchStmt(BranchKind.GOTO, it.identifier()?.parseName()) }
        ctx.fallthroughStmt()?.let { return BranchStmt(BranchKind.FALLTHROUGH, null) }
        ctx.block()?.let { return parseBlock(it) }
        ctx.ifStmt()?.let { return parseIfStmt(it) }
        ctx.switchStmt()?.let { return parseSwitchStmt(it) }
        ctx.selectStmt()?.let { return parseSelectStmt(it) }
        ctx.forStmt()?.let { return parseForStmt(it) }
        ctx.deferStmt()?.let { return DeferStmt(parseExpression(it.expression())) }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseSimpleStmt(ctx: GoParser.SimpleStmtContext): SemgrepGoPattern {
        ctx.sendStmt()?.let { s ->
            val exprs = s.expression()
            return SendStmt(parseExpression(exprs[0]), parseExpression(exprs[1]))
        }
        ctx.incDecStmt()?.let { s ->
            val op = if (s.PLUS_PLUS() != null) "++" else "--"
            return IncDecStmt(parseExpression(s.expression()), op)
        }
        ctx.assignment()?.let { a ->
            val lists = a.expressionList()
            val lhs = lists[0].expression().map { parseExpression(it) }
            val rhs = lists[1].expression().map { parseExpression(it) }
            return AssignStmt(a.assign_op().text, lhs, rhs)
        }
        ctx.expressionStmt()?.let { return ExprStmt(parseExpression(it.expression())) }
        ctx.shortVarDecl()?.let { s ->
            val lhs = s.identifierList().identifier().map { Identifier(it.parseName()) }
            val rhs = s.expressionList().expression().map { parseExpression(it) }
            return ShortVarDecl(lhs, rhs)
        }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseIfStmt(ctx: GoParser.IfStmtContext): IfStmt {
        val init = ctx.simpleStmt()?.let { parseSimpleStmt(it) }
        val cond = parseExpression(ctx.expression())
        val blocks = ctx.block()
        val then = parseBlock(blocks[0])
        val els: SemgrepGoPattern? = when {
            ctx.ifStmt() != null -> parseIfStmt(ctx.ifStmt())
            blocks.size > 1 -> parseBlock(blocks[1])
            else -> null
        }
        return IfStmt(init, cond, then, els)
    }

    private fun parseSwitchStmt(ctx: GoParser.SwitchStmtContext): SemgrepGoPattern {
        ctx.exprSwitchStmt()?.let { e ->
            val init = e.simpleStmt()?.let { parseSimpleStmt(it) }
            val tag = e.expression()?.let { parseExpression(it) }
            val cases = e.exprCaseClause().map { parseExprCaseClause(it) }
            return SwitchStmt(init, tag, cases)
        }
        ctx.typeSwitchStmt()?.let { t ->
            val init = t.simpleStmt()?.let { parseSimpleStmt(it) }
            val guard = parseTypeSwitchGuard(t.typeSwitchGuard())
            val cases = t.typeCaseClause().map { parseTypeCaseClause(it) }
            return TypeSwitchStmt(init, guard, cases)
        }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseExprCaseClause(ctx: GoParser.ExprCaseClauseContext): CaseClause {
        val ec = ctx.exprSwitchCase()
        val values: CaseValues = when {
            ec.DEFAULT() != null -> DefaultCase
            ec.ELLIPSIS() != null -> EllipsisCase
            else -> ExprCaseValues(ec.expressionList().expression().map { parseExpression(it) })
        }
        val body = ctx.statementList()?.statement()?.map { parseStatement(it) }.orEmpty()
        return CaseClause(values, body)
    }

    private fun parseTypeCaseClause(ctx: GoParser.TypeCaseClauseContext): TypeCaseClause {
        val tc = ctx.typeSwitchCase()
        val values: CaseValues = when {
            tc.DEFAULT() != null -> DefaultCase
            tc.ELLIPSIS() != null -> EllipsisCase
            else -> TypeCaseValuesList(tc.typeList().type_().map { parseType(it) })
        }
        val body = ctx.statementList()?.statement()?.map { parseStatement(it) }.orEmpty()
        return TypeCaseClause(values, body)
    }

    private fun parseTypeSwitchGuard(ctx: GoParser.TypeSwitchGuardContext): SemgrepGoPattern {
        val primary = parsePrimaryExpr(ctx.primaryExpr())
        val assertion = TypeAssertion(primary, type = null) // x.(type)
        val name = ctx.identifier()?.parseName() ?: return assertion
        return ShortVarDecl(listOf(Identifier(name)), listOf(assertion))
    }

    private fun parseSelectStmt(ctx: GoParser.SelectStmtContext): SelectStmt {
        val cases = ctx.commClause().map { parseCommClause(it) }
        return SelectStmt(cases)
    }

    private fun parseCommClause(ctx: GoParser.CommClauseContext): CommClause {
        val cc = ctx.commCase()
        val comm: CommCase = when {
            cc.DEFAULT() != null -> CommDefault
            cc.ELLIPSIS() != null -> CommEllipsis
            cc.sendStmt() != null -> {
                val s = cc.sendStmt()
                val exprs = s.expression()
                CommSend(SendStmt(parseExpression(exprs[0]), parseExpression(exprs[1])))
            }
            cc.recvStmt() != null -> {
                val r = cc.recvStmt()
                val lhs = r.expressionList()?.expression()?.map { parseExpression(it) }
                    ?: r.identifierList()?.identifier()?.map { Identifier(it.parseName()) }.orEmpty()
                val op = when {
                    r.ASSIGN() != null -> "="
                    r.DECLARE_ASSIGN() != null -> ":="
                    else -> ""
                }
                CommRecv(RecvStmt(lhs, op, parseExpression(r.expression())))
            }
            else -> CommDefault
        }
        val body = ctx.statementList()?.statement()?.map { parseStatement(it) }.orEmpty()
        return CommClause(comm, body)
    }

    private fun parseForStmt(ctx: GoParser.ForStmtContext): SemgrepGoPattern {
        if (ctx.ELLIPSIS() != null) return ForEllipsisStmt(parseBlock(ctx.block()))
        val body = parseBlock(ctx.block())
        ctx.condition()?.let { c ->
            return ForStmt(init = null, cond = parseExpression(c.expression()), post = null, body = body)
        }
        ctx.forClause()?.let { fc ->
            val simpleStmts = fc.simpleStmt()
            val init = simpleStmts.getOrNull(0)?.let { parseSimpleStmt(it) }
            val cond = fc.expression()?.let { parseExpression(it) }
            val post = simpleStmts.getOrNull(1)?.let { parseSimpleStmt(it) }
            return ForStmt(init, cond, post, body)
        }
        ctx.rangeClause()?.let { rc ->
            val decl = rc.DECLARE_ASSIGN() != null
            val lhs: List<SemgrepGoPattern> = rc.expressionList()?.expression()?.map { parseExpression(it) }
                ?: rc.identifierList()?.identifier()?.map { Identifier(it.parseName()) }
                ?: emptyList()
            val key = lhs.getOrNull(0)
            val value = lhs.getOrNull(1)
            return RangeStmt(key, value, decl, parseExpression(rc.expression()), body)
        }
        return ForStmt(init = null, cond = null, post = null, body = body)
    }

    // --- expressions ---

    private fun parseExpression(ctx: GoParser.ExpressionContext): SemgrepGoPattern {
        if (ctx.ELLIPSIS() != null && ctx.LDOTS() == null && ctx.primaryExpr() == null && ctx.expression().isEmpty()) {
            return Ellipsis
        }
        if (ctx.LDOTS() != null && ctx.RDOTS() != null) {
            return DeepExpr(parseExpression(ctx.expression(0)))
        }
        ctx.primaryExpr()?.let { return parsePrimaryExpr(it) }
        val exprs = ctx.expression()
        if (exprs.size == 1) {
            // unary
            val op = ctx.getChild(0).text
            return UnaryExpr(op, parseExpression(exprs[0]))
        }
        if (exprs.size == 2) {
            // binary - find op token between them
            val op = (1 until ctx.childCount).firstNotNullOfOrNull { i ->
                val c = ctx.getChild(i)
                if (c is TerminalNode && c.symbol.type != GoParser.LDOTS && c.symbol.type != GoParser.RDOTS) c.text else null
            } ?: "?"
            return BinaryExpr(op, parseExpression(exprs[0]), parseExpression(exprs[1]))
        }
        throw UnsupportedGoElement(ctx)
    }

    private fun parsePrimaryExpr(ctx: GoParser.PrimaryExprContext): SemgrepGoPattern {
        // Build base
        var base: SemgrepGoPattern = when {
            ctx.operand() != null -> parseOperand(ctx.operand())
            ctx.conversion() != null -> parseConversion(ctx.conversion())
            ctx.methodExpr() != null -> parseMethodExpr(ctx.methodExpr())
            else -> throw UnsupportedGoElement(ctx)
        }

        // Tail: iterate child nodes after base
        var i = 1
        val total = ctx.childCount
        // Skip the first base child
        while (i < total) {
            val c = ctx.getChild(i)
            when {
                c is TerminalNode && c.symbol.type == GoParser.DOT -> {
                    val next = ctx.getChild(i + 1)
                    if (next is TerminalNode) {
                        val sel: Name = when (next.symbol.type) {
                            GoParser.IDENTIFIER -> ConcreteName(next.text)
                            GoParser.METAVAR_IDENT -> MetavarName(next.text)
                            GoParser.ELLIPSIS -> MetavarName("...")
                            else -> ConcreteName(next.text)
                        }
                        base = SelectorExpr(base, sel)
                        i += 2
                        continue
                    }
                    i++
                }
                c is GoParser.IndexContext -> {
                    base = IndexExpr(base, parseExpression(c.expression()))
                    i++
                }
                c is GoParser.Slice_Context -> {
                    val exprs = c.expression()
                    val colons = c.COLON().size
                    val low: SemgrepGoPattern?
                    val high: SemgrepGoPattern?
                    val max: SemgrepGoPattern?
                    // Walk children, treat colons as separators
                    val parts = mutableListOf<SemgrepGoPattern?>()
                    var sawColon = false
                    var idx = 1
                    parts.add(null)
                    while (idx < c.childCount - 1) {
                        val cc = c.getChild(idx)
                        if (cc is TerminalNode && cc.symbol.type == GoParser.COLON) {
                            parts.add(null)
                            sawColon = true
                        } else if (cc is GoParser.ExpressionContext) {
                            parts[parts.size - 1] = parseExpression(cc)
                        }
                        idx++
                    }
                    low = parts.getOrNull(0)
                    high = parts.getOrNull(1)
                    max = parts.getOrNull(2)
                    base = SliceExpr(base, low, high, max)
                    i++
                    if (!sawColon) {
                        // safety
                    }
                    if (colons < 1) { /* unreachable */ }
                    if (exprs.isEmpty()) { /* unreachable */ }
                }
                c is GoParser.TypeAssertionContext -> {
                    base = TypeAssertion(base, parseType(c.type_()))
                    i++
                }
                c is GoParser.ArgumentsContext -> {
                    base = parseArgumentsAsCall(base, c)
                    i++
                }
                else -> i++
            }
        }
        return base
    }

    private fun parseConversion(ctx: GoParser.ConversionContext): ConversionExpr =
        ConversionExpr(parseType(ctx.type_()), parseExpression(ctx.expression()))

    private fun parseMethodExpr(ctx: GoParser.MethodExprContext): SelectorExpr {
        val type = parseType(ctx.type_())
        val sel: Name = when {
            ctx.METAVAR_IDENT() != null -> MetavarName(ctx.METAVAR_IDENT().text)
            else -> ConcreteName(ctx.IDENTIFIER().text)
        }
        // We model type-as-receiver as Raw (since SelectorExpr.obj expects a pattern). Use Identifier with concrete-name fallback.
        val obj: SemgrepGoPattern = (type as? NamedType)?.let { Identifier(it.name) }
            ?: (type as? MetavarType)?.let { Metavar(it.name) }
            ?: SemgrepGoPattern.Raw(ctx.type_().text)
        return SelectorExpr(obj, sel)
    }

    private fun parseArgumentsAsCall(fn: SemgrepGoPattern, ctx: GoParser.ArgumentsContext): CallExpr {
        val args = mutableListOf<SemgrepGoPattern>()
        ctx.type_()?.let { args.add(TypeOnlyPattern(parseType(it))) }
        ctx.expressionList()?.expression()?.forEach { args.add(parseExpression(it)) }
        val callArgs = args.foldRight<SemgrepGoPattern, CallArgs>(NoArgs) { a, rest ->
            when (a) {
                is Ellipsis -> if (rest is EllipsisArgPrefix) rest else EllipsisArgPrefix(rest)
                else -> ArgPrefix(a, rest)
            }
        }
        return CallExpr(fn, callArgs, hasEllipsis = ctx.ELLIPSIS() != null)
    }

    private fun parseOperand(ctx: GoParser.OperandContext): SemgrepGoPattern {
        // Starred metavar `$*X`: the `METAVAR_STAR_IDENT` token (`$*` prefix) is an unambiguous
        // discriminator, so `$X * y` (multiplication) / `*p` (deref) never reach here. A starred
        // TYPED metavar `($*X : T)` also carries METAVAR_STAR_IDENT but has a type_, so exclude it
        // here and let the typed branch below produce a starred TypedMetavar.
        if (ctx.METAVAR_STAR_IDENT() != null && ctx.type_() == null) {
            return Metavar(ctx.METAVAR_STAR_IDENT().text.stripStar(), star = true)
        }
        ctx.literal()?.let { return parseLiteral(it) }
        ctx.operandName()?.let {
            val base = parseOperandName(it)
            ctx.typeArgs()?.let { /* ignore type args on operand */ }
            return base
        }
        // typed metavar `($X : T)` (METAVAR_IDENT), or starred typed metavar `($*X : T)`
        // (METAVAR_STAR_IDENT, star prefix stripped) when the type_ is present.
        if (ctx.type_() != null) {
            ctx.METAVAR_STAR_IDENT()?.let {
                return TypedMetavar(it.text.stripStar(), parseType(ctx.type_()), star = true)
            }
            ctx.METAVAR_IDENT()?.let {
                return TypedMetavar(it.text, parseType(ctx.type_()), star = false)
            }
        }
        ctx.expression()?.let { return parseExpression(it) }
        throw UnsupportedGoElement(ctx)
    }

    // `$*X` -> `$X`: drop the `*` that follows the leading `$`, yielding the plain metavar name.
    private fun String.stripStar(): String = "$" + substring(2)

    private fun parseOperandName(ctx: GoParser.OperandNameContext): SemgrepGoPattern {
        ctx.qualifiedIdent()?.let {
            val (pkg, sel) = parseQualifiedIdentParts(it)
            return SelectorExpr(Identifier(pkg), sel)
        }
        ctx.METAVAR_IDENT()?.let { return Metavar(it.text) }
        ctx.METAVAR_STAR_IDENT()?.let { return Metavar(it.text.stripStar(), star = true) }
        ctx.ANONYMOUS_METAVAR()?.let { return Metavar("_") }
        ctx.METAVAR_ELLIPSIS()?.let { return EllipsisMetavar(it.text.removePrefix("$...")) }
        ctx.IDENTIFIER()?.let { return Identifier(ConcreteName(it.text)) }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseQualifiedIdentParts(ctx: GoParser.QualifiedIdentContext): Pair<Name, Name> {
        val children = (0 until ctx.childCount).map { ctx.getChild(it) }
        val left = children.first() as TerminalNode
        val right = children.last() as TerminalNode
        val pkg: Name = if (left.symbol.type == GoParser.METAVAR_IDENT) {
            MetavarName(left.text)
        } else ConcreteName(left.text)
        val sel: Name = if (right.symbol.type == GoParser.METAVAR_IDENT) {
            MetavarName(right.text)
        } else ConcreteName(right.text)
        return pkg to sel
    }

    // --- literals ---

    private fun parseLiteral(ctx: GoParser.LiteralContext): SemgrepGoPattern {
        ctx.basicLit()?.let { return parseBasicLit(it) }
        ctx.compositeLit()?.let { return parseCompositeLit(it) }
        ctx.functionLit()?.let { f ->
            val sig = parseSignature(f.signature())
            val body = parseBlock(f.block())
            return FuncLit(sig, body)
        }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseBasicLit(ctx: GoParser.BasicLitContext): SemgrepGoPattern {
        ctx.NIL_LIT()?.let { return NilLiteral }
        ctx.integer()?.let {
            val text = it.text
            if (it.IMAGINARY_LIT() != null) return ImaginaryLiteral(text)
            if (it.RUNE_LIT() != null) return RuneLiteral(text)
            return IntLiteral(text)
        }
        ctx.string_()?.let { return parseString(it) }
        ctx.FLOAT_LIT()?.let { return FloatLiteral(it.text) }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseString(ctx: GoParser.String_Context): SemgrepGoPattern {
        ctx.METAVAR_LITERAL()?.let {
            val inner = it.text.removeSurrounding("\"")
            return StringLiteral(MetavarName(inner))
        }
        ctx.ELLIPSIS_LITERAL()?.let { return StringEllipsis }
        ctx.RAW_STRING_LIT()?.let { return StringLiteral(ConcreteName(unquoteString(it.text))) }
        ctx.INTERPRETED_STRING_LIT()?.let {
            return StringLiteral(ConcreteName(unquoteString(it.text)))
        }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseCompositeLit(ctx: GoParser.CompositeLitContext): CompositeLit {
        val type = parseLiteralType(ctx.literalType())
        val elems = parseLiteralValue(ctx.literalValue())
        return CompositeLit(type, elems)
    }

    private fun parseLiteralType(ctx: GoParser.LiteralTypeContext): TypeName {
        ctx.structType()?.let { return parseStructType(it) }
        ctx.arrayType()?.let { return parseArrayType(it) }
        ctx.elementType()?.let {
            // `[...]T`
            if (ctx.ELLIPSIS() != null) return ArrayType(Ellipsis, parseType(it.type_()))
            // sliceType handled
        }
        ctx.sliceType()?.let { return SliceType(parseType(it.elementType().type_())) }
        ctx.mapType()?.let {
            return MapType(parseType(it.type_()), parseType(it.elementType().type_()))
        }
        ctx.typeName()?.let {
            val typeArgs = ctx.typeArgs()?.let { ta -> parseTypeArgs(ta) }.orEmpty()
            return parseTypeName(it, typeArgs)
        }
        throw UnsupportedGoElement(ctx)
    }

    private fun parseLiteralValue(ctx: GoParser.LiteralValueContext): List<CompositeElem> {
        val list = ctx.elementList() ?: return emptyList()
        return list.keyedElement().map { ke ->
            val key: SemgrepGoPattern? = ke.key()?.let { k ->
                k.expression()?.let { parseExpression(it) }
                    ?: k.literalValue()?.let { CompositeLit(null, parseLiteralValue(it)) }
            }
            val element = ke.element()
            val value: SemgrepGoPattern = when {
                element.ELLIPSIS() != null -> return@map EllipsisElem
                element.expression() != null -> parseExpression(element.expression())
                element.literalValue() != null -> CompositeLit(null, parseLiteralValue(element.literalValue()))
                else -> throw UnsupportedGoElement(element)
            }
            KeyedElem(key, value) as CompositeElem
        }
    }
}
