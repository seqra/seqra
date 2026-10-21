package org.opentaint.semgrep.python.pattern

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ConsoleErrorListener
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.opentaint.semgrep.pattern.PatternParsingAstFailed
import org.opentaint.semgrep.pattern.PatternParsingFailure
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import org.opentaint.semgrep.pattern.python.antlr.PythonLexer
import org.opentaint.semgrep.pattern.python.antlr.PythonParser
import org.opentaint.semgrep.pattern.python.antlr.PythonParserBaseVisitor

sealed interface SemgrepPythonPatternParsingResult {
    data class Ok(val pattern: SemgrepPythonPattern) : SemgrepPythonPatternParsingResult
    data class ParserFailure(val exception: SemgrepPythonParsingException) : SemgrepPythonPatternParsingResult
    data class OtherFailure(val exception: Throwable) : SemgrepPythonPatternParsingResult
    data class FailedASTParsing(val errorMessages: List<String>) : SemgrepPythonPatternParsingResult
}

sealed class SemgrepPythonParsingException(val element: ParserRuleContext, message: String) : Exception(message)

class SemgrepPythonParsingFailedException(ctx: ParserRuleContext, additionalMessage: String) :
    SemgrepPythonParsingException(ctx, "Exception during parsing ${ctx.text}: $additionalMessage")

class UnsupportedPythonElement(element: ParserRuleContext) :
    SemgrepPythonParsingException(element, "Unsupported element: ${element.text}")

class SemgrepPythonPatternParser : SemgrepPatternParser<SemgrepPythonPattern> {
    private val visitor = SemgrepPythonPatternParserVisitor()

    override fun parseOrNull(pattern: String, semgrepTrace: SemgrepRuleLoadStepTrace): SemgrepPythonPattern? =
        when (val r = parseSemgrepPythonPattern(pattern)) {
            is SemgrepPythonPatternParsingResult.Ok -> r.pattern
            is SemgrepPythonPatternParsingResult.FailedASTParsing -> {
                semgrepTrace.error(PatternParsingAstFailed(r.errorMessages))
                null
            }
            is SemgrepPythonPatternParsingResult.ParserFailure -> {
                semgrepTrace.error(PatternParsingFailure(r.exception.message))
                null
            }
            is SemgrepPythonPatternParsingResult.OtherFailure -> {
                semgrepTrace.error(PatternParsingFailure(r.exception.message))
                null
            }
        }

    fun parseSemgrepPythonPattern(pattern: String): SemgrepPythonPatternParsingResult {
        val errors = mutableListOf<String>()

        val lexer = PythonLexer(CharStreams.fromString(pattern)).apply { configureErrorListener(errors) }
        val tokens = CommonTokenStream(lexer)
        val parser = PythonParser(tokens).apply { configureErrorListener(errors) }

        val tree = try {
            parser.semgrepPattern()
        } catch (t: Throwable) {
            return SemgrepPythonPatternParsingResult.OtherFailure(t)
        }
        if (errors.isNotEmpty()) {
            return SemgrepPythonPatternParsingResult.FailedASTParsing(errors)
        }

        return try {
            val ast = visitor.visit(tree)
                ?: return SemgrepPythonPatternParsingResult.OtherFailure(IllegalStateException("Null AST"))
            SemgrepPythonPatternParsingResult.Ok(ast)
        } catch (e: SemgrepPythonParsingException) {
            SemgrepPythonPatternParsingResult.ParserFailure(e)
        } catch (t: Throwable) {
            SemgrepPythonPatternParsingResult.OtherFailure(t)
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

private class SemgrepPythonPatternParserVisitor : PythonParserBaseVisitor<SemgrepPythonPattern?>() {

    override fun visitSemgrepPattern(ctx: PythonParser.SemgrepPatternContext): SemgrepPythonPattern {
        ctx.eval()?.let { return parseExpressions(it.expressions()) }
        ctx.file_input()?.let {
            val stmts = it.statements()?.let { s -> parseStatements(s) }.orEmpty()
            return stmts.singleOrNull() ?: TopList(stmts)
        }
        throw UnsupportedPythonElement(ctx)
    }

    private fun parseStatements(ctx: PythonParser.StatementsContext): List<SemgrepPythonPattern> =
        ctx.statement().flatMap { parseStatement(it) }

    private fun parseStatement(ctx: PythonParser.StatementContext): List<SemgrepPythonPattern> {
        ctx.simple_stmts()?.let { return parseSimpleStmts(it) }
        ctx.compound_stmt()?.let { return listOf(parseCompoundStmt(it)) }
        throw UnsupportedPythonElement(ctx)
    }

    private fun parseSimpleStmts(ctx: PythonParser.Simple_stmtsContext): List<SemgrepPythonPattern> =
        ctx.simple_stmt().map { parseSimpleStmt(it) }

    private fun parseSimpleStmt(ctx: PythonParser.Simple_stmtContext): SemgrepPythonPattern {
        ctx.assignment()?.let { return parseAssignment(it) }
        ctx.star_expressions()?.let {
            val expr = parseStarExpressions(it)
            return if (expr is Ellipsis) EllipsisStmt else ExprStmt(expr)
        }
        ctx.return_stmt()?.let { return ReturnStmt(it.star_expressions()?.let { e -> parseStarExpressions(e) }) }
        ctx.pass_stmt()?.let { return PassStmt }
        ctx.break_stmt()?.let { return BreakStmt }
        ctx.continue_stmt()?.let { return ContinueStmt }
        ctx.import_stmt()?.let { return parseImport(it) }
        return raw(ctx)
    }

    private fun parseAssignment(ctx: PythonParser.AssignmentContext): SemgrepPythonPattern {
        ctx.augassign()?.let {
            val value = parseAnnotatedRhs(ctx.annotated_rhs())
            return Assign(it.text, listOf(parseSingleTarget(ctx.single_target())), value, annotation = null)
        }
        ctx.expression()?.let { annotation ->
            val target = ctx.name()?.let { atomFromName(parseName(it)) }
                ?: ctx.single_target()?.let { parseSingleTarget(it) }
                ?: ctx.single_subscript_attribute_target()?.let { parseTrailer(parsePrimary(it.t_primary()), it) }
                ?: throw UnsupportedPythonElement(ctx)
            val value = ctx.annotated_rhs()?.let { parseAnnotatedRhs(it) }
            return Assign("=", listOf(target), value, parseExpression(annotation))
        }
        val targets = ctx.star_targets().map { parseStarTargets(it) }
        return Assign("=", targets, parseAnnotatedRhs(ctx.annotated_rhs()), annotation = null)
    }

    private fun parseAnnotatedRhs(ctx: PythonParser.Annotated_rhsContext): SemgrepPythonPattern =
        ctx.star_expressions()?.let { parseStarExpressions(it) } ?: raw(ctx)

    private fun parseImport(ctx: PythonParser.Import_stmtContext): SemgrepPythonPattern {
        ctx.import_name()?.let { imp ->
            val aliases = imp.dotted_as_names().dotted_as_name().map { dan ->
                ImportAlias(parseDottedName(dan.dotted_name()), dan.name()?.let { parseName(it) })
            }
            return ImportStmt(aliases)
        }
        val from = ctx.import_from()
        val module = from.dotted_name()?.let { parseDottedName(it) }.orEmpty()
        val targets = from.import_from_targets()
        val aliases = targets.import_from_as_names()?.import_from_as_name()?.map { ian ->
            val names = ian.name()
            ImportAlias(listOf(parseName(names[0])), names.getOrNull(1)?.let { parseName(it) })
        }.orEmpty()
        return FromImportStmt(module, aliases, importStar = targets.STAR() != null)
    }

    private fun parseCompoundStmt(ctx: PythonParser.Compound_stmtContext): SemgrepPythonPattern {
        ctx.if_stmt()?.let { return parseIf(it.named_expression(), it.block(), it.elif_stmt(), it.else_block()) }
        ctx.while_stmt()?.let {
            return WhileStmt(parseNamedExpression(it.named_expression()), parseBlock(it.block()), parseElse(it.else_block()))
        }
        ctx.for_stmt()?.let {
            val target = parseStarTargets(it.star_targets())
            val iter = parseStarExpressions(it.star_expressions())
            return ForStmt(target, iter, parseBlock(it.block()), parseElse(it.else_block()))
        }
        ctx.with_stmt()?.let { return parseWith(it) }
        ctx.function_def()?.let { return parseFunctionDef(it) }
        ctx.class_def()?.let { return parseClassDef(it) }
        return raw(ctx)
    }

    private fun parseIf(
        cond: PythonParser.Named_expressionContext,
        block: PythonParser.BlockContext,
        elif: PythonParser.Elif_stmtContext?,
        elseBlock: PythonParser.Else_blockContext?,
    ): SemgrepPythonPattern {
        val orelse = elif?.let { parseIf(it.named_expression(), it.block(), it.elif_stmt(), it.else_block()) }
            ?: parseElse(elseBlock)
        return IfStmt(parseNamedExpression(cond), parseBlock(block), orelse)
    }

    private fun parseElse(ctx: PythonParser.Else_blockContext?): Block? = ctx?.let { parseBlock(it.block()) }

    private fun parseWith(ctx: PythonParser.With_stmtContext): SemgrepPythonPattern {
        val items = ctx.with_item().map { wi ->
            WithItem(parseExpression(wi.expression()), wi.star_target()?.let { parseStarTarget(it) })
        }
        return WithStmt(items, parseBlock(ctx.block()))
    }

    private fun parseDecorators(ctx: PythonParser.DecoratorsContext?): List<SemgrepPythonPattern> =
        ctx?.named_expression()?.map { parseNamedExpression(it) }.orEmpty()

    private fun parseFunctionDef(ctx: PythonParser.Function_defContext): SemgrepPythonPattern {
        val raw = ctx.function_def_raw()
        val params = raw.params()?.let { parseParams(it.parameters()) }.orEmpty()
        val returns = raw.expression()?.let { parseExpression(it) }
        return FunctionDef(
            parseName(raw.name()),
            params,
            parseBlock(raw.block()),
            parseDecorators(ctx.decorators()),
            returns,
            isAsync = raw.ASYNC() != null,
        )
    }

    private fun parseClassDef(ctx: PythonParser.Class_defContext): SemgrepPythonPattern {
        val raw = ctx.class_def_raw()
        val bases = if (raw.LPAR() != null) parseArguments(raw.arguments()) else NoArgs
        return ClassDef(parseName(raw.name()), bases, parseBlock(raw.block()), parseDecorators(ctx.decorators()))
    }

    private fun parseParams(ctx: PythonParser.ParametersContext): List<Param> =
        ctx.children.orEmpty().flatMap { c ->
            when (c) {
                is PythonParser.Slash_no_defaultContext -> c.param_no_default().map { parseParam(it.param(), null) }
                is PythonParser.Slash_with_defaultContext ->
                    c.param_no_default().map { parseParam(it.param(), null) } + c.param_with_default().map { parseParamWithDefault(it) }
                is PythonParser.Param_no_defaultContext -> listOf(parseParam(c.param(), null))
                is PythonParser.Param_with_defaultContext -> listOf(parseParamWithDefault(c))
                is PythonParser.Star_etcContext -> parseStarEtc(c)
                else -> emptyList()
            }
        }

    private fun parseParamWithDefault(ctx: PythonParser.Param_with_defaultContext): Param {
        val param = ctx.param() ?: return EllipsisParam
        return parseParam(param, ctx.default_assignment())
    }

    private fun parseParam(ctx: PythonParser.ParamContext, default: PythonParser.Default_assignmentContext?): Param {
        val name = ctx.name() ?: return EllipsisParam
        val annotation = ctx.annotation()?.let { parseExpression(it.expression()) }
        return NamedParam(parseName(name), annotation, default?.let { parseExpression(it.expression()) })
    }

    private fun parseStarEtc(ctx: PythonParser.Star_etcContext): List<Param> {
        val star = ctx.STAR()?.let {
            val starName = ctx.param_no_default()?.param()?.name()
                ?: ctx.param_no_default_star_annotation()?.param_star_annotation()?.name()
            StarParam(starName?.let { parseName(it) })
        }
        val named = ctx.param_maybe_default().map { parseParam(it.param(), it.default_assignment()) }
        val kwds = ctx.kwds()?.let { DoubleStarParam(it.param_no_default().param().name()?.let { n -> parseName(n) }) }
        return listOfNotNull(star) + named + listOfNotNull(kwds)
    }

    private fun parseBlock(ctx: PythonParser.BlockContext): Block {
        ctx.simple_stmts()?.let { return Block(parseSimpleStmts(it)) }
        return Block(parseStatements(ctx.statements()))
    }

    private fun parseExpressions(ctx: PythonParser.ExpressionsContext): SemgrepPythonPattern {
        return tupleOrSingle(ctx.expression().map { parseExpression(it) }, ctx)
    }

    private fun parseStarExpressions(ctx: PythonParser.Star_expressionsContext): SemgrepPythonPattern {
        return tupleOrSingle(ctx.star_expression().map { parseStarExpression(it) }, ctx)
    }

    private fun parseStarExpression(ctx: PythonParser.Star_expressionContext): SemgrepPythonPattern {
        ctx.bitwise_or()?.let { return StarArgument(parseBinary(it)) }
        return parseExpression(ctx.expression())
    }

    private fun parseStarNamedExpression(ctx: PythonParser.Star_named_expressionContext): SemgrepPythonPattern {
        ctx.bitwise_or()?.let { return StarArgument(parseBinary(it)) }
        return parseNamedExpression(ctx.named_expression())
    }

    private fun parseStarNamedExpressions(ctx: PythonParser.Star_named_expressionsContext?): List<SemgrepPythonPattern> =
        ctx?.star_named_expression()?.map { parseStarNamedExpression(it) }.orEmpty()

    private fun parseNamedExpression(ctx: PythonParser.Named_expressionContext): SemgrepPythonPattern {
        ctx.assignment_expression()?.let { return raw(it) }
        return parseExpression(ctx.expression())
    }

    private fun parseExpression(ctx: PythonParser.ExpressionContext): SemgrepPythonPattern {
        if (ctx.IF() != null || ctx.lambdef() != null) return raw(ctx)
        return parseDisjunction(ctx.disjunction(0))
    }

    private fun parseDisjunction(ctx: PythonParser.DisjunctionContext): SemgrepPythonPattern =
        ctx.conjunction().map { parseConjunction(it) }.reduce { l, r -> BinaryExpr("or", l, r) }

    private fun parseConjunction(ctx: PythonParser.ConjunctionContext): SemgrepPythonPattern =
        ctx.inversion().map { parseInversion(it) }.reduce { l, r -> BinaryExpr("and", l, r) }

    private fun parseInversion(ctx: PythonParser.InversionContext): SemgrepPythonPattern {
        ctx.inversion()?.let { return UnaryExpr("not", parseInversion(it)) }
        return parseComparison(ctx.comparison())
    }

    private fun parseComparison(ctx: PythonParser.ComparisonContext): SemgrepPythonPattern {
        var result = parseBinary(ctx.bitwise_or())
        for (pair in ctx.compare_op_bitwise_or_pair()) {
            val opCtx = pair.getChild(0) as ParserRuleContext
            val op = (0 until opCtx.childCount - 1).joinToString(" ") { opCtx.getChild(it).text }
            val right = opCtx.getChild(opCtx.childCount - 1) as PythonParser.Bitwise_orContext
            result = BinaryExpr(op, result, parseBinary(right))
        }
        return result
    }

    private fun parseBinary(ctx: ParserRuleContext): SemgrepPythonPattern {
        if (ctx is PythonParser.FactorContext) return parseFactor(ctx)
        if (ctx.childCount == 1) return parseBinary(ctx.getChild(0) as ParserRuleContext)
        val left = parseBinary(ctx.getChild(0) as ParserRuleContext)
        val right = parseBinary(ctx.getChild(2) as ParserRuleContext)
        return BinaryExpr(ctx.getChild(1).text, left, right)
    }

    private fun parseFactor(ctx: PythonParser.FactorContext): SemgrepPythonPattern {
        ctx.factor()?.let { operand ->
            val op = ctx.getChild(0).text
            val number = operand.power()?.takeIf { it.factor() == null }?.await_primary()
                ?.takeIf { it.AWAIT() == null }?.primary()?.atom()?.NUMBER()
            if (op == "-" && number != null) return NumberLiteral("-${number.text}")
            return UnaryExpr(op, parseFactor(operand))
        }
        return parsePower(ctx.power())
    }

    private fun parsePower(ctx: PythonParser.PowerContext): SemgrepPythonPattern {
        val base = parsePrimary(ctx.await_primary().primary())
        val exponent = ctx.factor() ?: return base
        return BinaryExpr("**", base, parseFactor(exponent))
    }

    private fun parsePrimary(ctx: ParserRuleContext): SemgrepPythonPattern {
        val head = ctx.getChild(0)
        if (head is PythonParser.AtomContext) return parseAtom(head)
        return parseTrailer(parsePrimary(head as ParserRuleContext), ctx)
    }

    private fun parseTrailer(base: SemgrepPythonPattern, ctx: ParserRuleContext): SemgrepPythonPattern {
        ctx.getRuleContext(PythonParser.NameContext::class.java, 0)?.let { return Attribute(base, parseName(it)) }
        ctx.getRuleContext(PythonParser.GenexpContext::class.java, 0)?.let { return Call(base, ArgPrefix(raw(it), NoArgs), hasEllipsis = false) }
        ctx.getRuleContext(PythonParser.SlicesContext::class.java, 0)?.let { return Subscript(base, parseSlices(it)) }
        val args = parseArguments(ctx.getRuleContext(PythonParser.ArgumentsContext::class.java, 0))
        return Call(base, args, hasEllipsis = args.containsEllipsis())
    }

    private fun parseSlices(ctx: PythonParser.SlicesContext): SemgrepPythonPattern {
        if (ctx.starred_expression().isNotEmpty()) return raw(ctx)
        return tupleOrSingle(ctx.slice().map { parseSlice(it) }, ctx)
    }

    private fun parseSlice(ctx: PythonParser.SliceContext): SemgrepPythonPattern {
        ctx.named_expression()?.let { return parseNamedExpression(it) }
        return raw(ctx)
    }

    private fun parseArguments(ctx: PythonParser.ArgumentsContext?): CallArgs {
        if (ctx == null) return NoArgs
        return collectArgs(ctx.args()).foldRight<SemgrepPythonPattern, CallArgs>(NoArgs) { arg, rest ->
            when (arg) {
                is Ellipsis -> if (rest is EllipsisArgPrefix) rest else EllipsisArgPrefix(rest)
                else -> ArgPrefix(arg, rest)
            }
        }
    }

    private fun collectArgs(ctx: ParserRuleContext): List<SemgrepPythonPattern> =
        ctx.children.orEmpty().flatMap { c ->
            when (c) {
                is PythonParser.ExpressionContext -> listOf(parseExpression(c))
                is PythonParser.Assignment_expressionContext -> listOf(raw(c))
                is PythonParser.Starred_expressionContext -> listOf(StarArgument(parseExpression(c.expression())))
                is PythonParser.KwargsContext -> collectArgs(c)
                is PythonParser.Kwarg_or_starredContext -> {
                    val kw = c.name()?.let { KeywordArgument(parseName(it), parseExpression(c.expression())) }
                    listOf(kw ?: c.ELLIPSIS()?.let { Ellipsis } ?: StarArgument(parseExpression(c.starred_expression().expression())))
                }
                is PythonParser.Kwarg_or_double_starredContext -> {
                    val kw = c.name()?.let { KeywordArgument(parseName(it), parseExpression(c.expression())) }
                    listOf(kw ?: c.ELLIPSIS()?.let { Ellipsis } ?: DoubleStarArgument(parseExpression(c.expression())))
                }
                else -> emptyList()
            }
        }

    private fun parseAtom(ctx: PythonParser.AtomContext): SemgrepPythonPattern {
        ctx.ELLIPSIS()?.let { return Ellipsis }
        ctx.LDOTS()?.let { return DeepExpr(parseExpression(ctx.expression())) }
        ctx.name()?.let { return atomFromName(parseName(it)) }
        ctx.NONE()?.let { return NoneLiteral }
        ctx.TRUE()?.let { return Identifier(ConcreteName("True")) }
        ctx.FALSE()?.let { return Identifier(ConcreteName("False")) }
        ctx.NUMBER()?.let { return NumberLiteral(it.text) }
        ctx.strings()?.let { return parseStrings(it) }
        ctx.group()?.let { g ->
            g.named_expression()?.let { return parseNamedExpression(it) }
            return raw(ctx)
        }
        ctx.tuple()?.let { t ->
            val first = t.star_named_expression() ?: return raw(ctx)
            return TupleExpr(listOf(parseStarNamedExpression(first)) + parseStarNamedExpressions(t.star_named_expressions()))
        }
        ctx.list()?.let { return ListExpr(parseStarNamedExpressions(it.star_named_expressions())) }
        return raw(ctx)
    }

    private fun parseStrings(ctx: PythonParser.StringsContext): SemgrepPythonPattern {
        if (ctx.tstring().isNotEmpty()) return raw(ctx)
        if (ctx.fstring().isEmpty()) return parseStringAtom(ctx.string().map { it.text })
        val parts = ctx.children.orEmpty().flatMap { c ->
            when (c) {
                is PythonParser.StringContext -> listOf(unquoteString(c.text))
                is PythonParser.FstringContext -> c.fstring_middle().map {
                    it.FSTRING_MIDDLE()?.text ?: throw UnsupportedPythonElement(ctx)
                }
                else -> emptyList()
            }
        }
        return classifyStringContent(parts.joinToString(""))
    }

    private fun parseStarTargets(ctx: PythonParser.Star_targetsContext): SemgrepPythonPattern {
        return tupleOrSingle(ctx.star_target().map { parseStarTarget(it) }, ctx)
    }

    private fun parseStarTarget(ctx: PythonParser.Star_targetContext): SemgrepPythonPattern {
        ctx.star_target()?.let { return StarArgument(parseStarTarget(it)) }
        return parseTargetWithStarAtom(ctx.target_with_star_atom())
    }

    private fun parseTargetWithStarAtom(ctx: PythonParser.Target_with_star_atomContext): SemgrepPythonPattern {
        ctx.t_primary()?.let { return parseTrailer(parsePrimary(it), ctx) }
        val atom = ctx.star_atom()
        atom.name()?.let { return atomFromName(parseName(it)) }
        atom.ELLIPSIS()?.let { return Ellipsis }
        atom.target_with_star_atom()?.let { return parseTargetWithStarAtom(it) }
        val seq = atom.star_targets_tuple_seq()?.star_target() ?: atom.star_targets_list_seq()?.star_target()
        val elems = seq.orEmpty().map { parseStarTarget(it) }
        return if (atom.LSQB() != null) ListExpr(elems) else TupleExpr(elems)
    }

    private fun parseSingleTarget(ctx: PythonParser.Single_targetContext): SemgrepPythonPattern {
        ctx.single_subscript_attribute_target()?.let { return parseTrailer(parsePrimary(it.t_primary()), it) }
        ctx.name()?.let { return atomFromName(parseName(it)) }
        return parseSingleTarget(ctx.single_target())
    }

    private fun tupleOrSingle(elems: List<SemgrepPythonPattern>, ctx: ParserRuleContext): SemgrepPythonPattern {
        val hasComma = ctx.getTokens(PythonLexer.COMMA).isNotEmpty()
        return if (elems.size == 1 && !hasComma) elems[0] else TupleExpr(elems)
    }

    private fun raw(ctx: ParseTree): SemgrepPythonPattern.Raw {
        val tokens = mutableListOf<String>()
        fun walk(t: ParseTree) {
            if (t is TerminalNode) {
                if (t.symbol.type !in synthesizedTokens) tokens.add(t.text)
                return
            }
            for (i in 0 until t.childCount) walk(t.getChild(i))
        }
        walk(ctx)
        return SemgrepPythonPattern.Raw(tokens.joinToString(""))
    }

    private fun atomFromName(name: Name): SemgrepPythonPattern =
        when (name) {
            is MetavarName -> if (name.name.startsWith("...")) EllipsisMetavar(name.name.removePrefix("...")) else Metavar(name.name)
            is ConcreteName -> Identifier(name)
        }

    private fun parseStringAtom(parts: List<String>): SemgrepPythonPattern {
        if (parts.size == 1) return classifyStringContent(unquoteString(parts[0]))
        return StringLiteral(ConcreteName(parts.joinToString("") { unquoteString(it) }))
    }

    private fun classifyStringContent(inner: String): SemgrepPythonPattern {
        if (inner == "...") return StringEllipsis
        if (isMetavarText(inner)) return StringLiteral(MetavarName(inner))
        return StringLiteral(ConcreteName(inner))
    }

    private fun parseName(ctx: PythonParser.NameContext): Name {
        ctx.METAVAR()?.let { return MetavarName(it.text) }
        ctx.METAVAR_ELLIPSIS()?.let { return MetavarName(it.text.removePrefix("$")) }
        ctx.ANONYMOUS_METAVAR()?.let { return MetavarName("_") }
        return ConcreteName(ctx.text)
    }

    private fun parseDottedName(ctx: PythonParser.Dotted_nameContext): List<Name> {
        val parts = mutableListOf<Name>()
        fun walk(c: PythonParser.Dotted_nameContext) {
            c.dotted_name()?.let { walk(it) }
            parts.add(parseName(c.name()))
        }
        walk(ctx)
        return parts
    }
}

private val synthesizedTokens = setOf(PythonLexer.NEWLINE, PythonLexer.INDENT, PythonLexer.DEDENT, Token.EOF)

private tailrec fun CallArgs.containsEllipsis(): Boolean =
    when (this) {
        is NoArgs -> false
        is EllipsisArgPrefix -> true
        is ArgPrefix -> rest.containsEllipsis()
    }

private fun isMetavarText(s: String): Boolean =
    s.length >= 2 && s[0] == '$' && (s[1].isUpperCase() || s[1] == '_') &&
        s.drop(1).all { it.isUpperCase() || it.isDigit() || it == '_' }

private fun unquoteString(raw: String): String {
    var s = raw
    val quoteIdx = s.indexOfFirst { it == '"' || it == '\'' }
    if (quoteIdx > 0) s = s.substring(quoteIdx)
    for (q in listOf("\"\"\"", "'''")) {
        if (s.length >= 6 && s.startsWith(q) && s.endsWith(q)) return s.substring(3, s.length - 3)
    }
    if (s.length >= 2 && (s.first() == '"' || s.first() == '\'') && s.last() == s.first()) {
        return s.substring(1, s.length - 1)
    }
    return s
}
