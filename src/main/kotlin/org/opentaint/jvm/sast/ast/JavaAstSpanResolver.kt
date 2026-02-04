package org.opentaint.jvm.sast.ast

import mu.KLogging
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.antlr.v4.runtime.tree.TerminalNode
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.dataflow.jvm.util.JIRSarifTraits
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.sast.sarif.IntermediateLocation
import org.opentaint.jvm.sast.sarif.LocationSpan
import org.opentaint.jvm.sast.sarif.LocationType
import org.opentaint.jvm.sast.sarif.TracePathNodeKind
import org.opentaint.jvm.sast.sarif.isPureEntryPoint
import org.opentaint.semgrep.pattern.antlr.JavaLexer
import org.opentaint.semgrep.pattern.antlr.JavaParser
import org.opentaint.semgrep.pattern.antlr.JavaParser.BinaryOperatorExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.CompilationUnitContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.MemberReferenceExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.SquareBracketExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParserBaseVisitor
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

class JavaAstSpanResolver(private val traits: JIRSarifTraits) {
    fun computeSpan(sourceLocation: Path, location: IntermediateLocation): LocationSpan? = runCatching {
        val ast = getJavaAst(sourceLocation) ?: return@runCatching null
        val targetLine = location.info.lineNumber
        // if failed, let's highlight the entire line
        computeSpan(ast, location)
            ?: findBroadestSpan(ast, targetLine)
            ?: findSmallestSpan(ast, targetLine)
    }.onFailure { ex ->
        logger.error(ex) { "Span resolution failure" }
    }.getOrNull()

    fun getParameterName(sourceLocation: Path, inst: JIRInst, paramIdx: Int): String? = runCatching {
        val ast = getJavaAst(sourceLocation) ?: return@runCatching null

        val method = inst.location.method
        val line = inst.lineNumber
        val declaration = findMethodDeclarationContext(ast, line, method) ?: return@runCatching null

        val paramsContexts = declaration.findChildType(JavaParser.FormalParametersContext::class.java)
            .findChildType(JavaParser.FormalParameterListContext::class.java)
            ?.children?.filterIsInstance<JavaParser.FormalParameterContext>() ?: return@runCatching null

        if (paramIdx >= paramsContexts.size) return@runCatching null

        paramsContexts[paramIdx]
            .findChildType(JavaParser.VariableDeclaratorIdContext::class.java)
            .findChildType(JavaParser.IdentifierContext::class.java)
            ?.text
    }.onFailure {
        logger.error { "Argument name resolution failure" }
    }.getOrNull()

    private val parsedFiles = ConcurrentHashMap<Path, Optional<CompilationUnitContext>>()

    private fun getJavaAst(path: Path): CompilationUnitContext? =
        parsedFiles.computeIfAbsent(path) { Optional.ofNullable(parseJavaFile(path)) }.getOrNull()

    private fun parseJavaFile(path: Path): CompilationUnitContext? = runCatching {
        val lexer = JavaLexer(CharStreams.fromPath(path)).apply { removeErrorListeners() }
        val tokenStream = CommonTokenStream(lexer)
        val parser = JavaParser(tokenStream).apply { removeErrorListeners() }
        parser.compilationUnit()
    }.onFailure { ex ->
        logger.error(ex) { "File parsing failure" }
    }.getOrNull()

    private fun IntermediateLocation.isMethodEntry(): Boolean {
        val entry = this.node?.entry
        return entry is MethodTraceResolver.TraceEntry.MethodEntry || entry.isPureEntryPoint() || type == LocationType.RuleMethodEntry
    }

    private fun IntermediateLocation.isMethodExit(): Boolean {
        if (node == null) return false
        return node.kind != TracePathNodeKind.SINK && node.entry is MethodTraceResolver.TraceEntry.Final
                && node.statement is JIRReturnInst
    }

    private fun createLocationSpan(start: Token?, stop: Token?): LocationSpan? {
        if (start == null || stop == null) return null

        val (startLine, startCol) = start.line to (start.charPositionInLine + 1)
        val endLine = stop.line
        val endCol = tokenEndColumn(stop)

        return LocationSpan(
            startLine = startLine,
            startColumn = startCol,
            endLine = endLine,
            endColumn = endCol,
        )
    }

    private fun computeSpan(ast: CompilationUnitContext, location: IntermediateLocation): LocationSpan? {
        val targetLine = location.info.lineNumber
        val inst = location.inst as JIRInst

        if (location.type == LocationType.Multiple) {
            return findBroadestSpan(ast, targetLine)
        }
        if (location.isMethodEntry()) {
            return findMethodDeclaration(ast, targetLine, inst)
        }
        if (location.isMethodExit()) {
            return findMethodEnd(ast, targetLine, inst)
        }

        val kind = inferKind(inst)
        val node = when (kind) {
            InstructionKind.METHOD_CALL -> findMethodCallNode(ast, targetLine, inst)
            InstructionKind.OBJECT_CREATION -> findObjectCreationNode(ast, targetLine, inst)
            InstructionKind.FIELD_ACCESS -> findFieldAccessNode(ast, targetLine, inst)
            InstructionKind.ARRAY_ACCESS -> findArrayAccessNode(ast, targetLine, inst)
            InstructionKind.RETURN -> findReturnNode(ast, targetLine)
            InstructionKind.ASSIGNMENT -> findAssignmentNode(ast, targetLine)
            InstructionKind.UNKNOWN -> null
        }

        if (node == null) {
            logger.trace { "Instruction ast not identified" }
            return null
        }

        return createLocationSpan(node.start, node.stop)
    }

    private enum class InstructionKind {
        METHOD_CALL,
        OBJECT_CREATION,
        FIELD_ACCESS,
        ARRAY_ACCESS,
        RETURN,
        ASSIGNMENT,
        UNKNOWN
    }

    private fun inferKind(inst: JIRInst): InstructionKind = when (inst) {
        is JIRReturnInst -> InstructionKind.RETURN
        is JIRCallInst -> if (isConstructorCall(inst.callExpr)) {
            InstructionKind.OBJECT_CREATION
        } else {
            InstructionKind.METHOD_CALL
        }
        is JIRAssignInst -> inferAssignKind(inst)
        else -> InstructionKind.UNKNOWN
    }

    private fun isConstructorCall(call: JIRCallExpr): Boolean =
        call.method.method.isConstructor

    private fun inferAssignKind(assign: JIRAssignInst): InstructionKind {
        val l = assign.lhv
        val r = assign.rhv
        if (r is JIRNewExpr) return InstructionKind.OBJECT_CREATION
        if (l is JIRArrayAccess || r is JIRArrayAccess) return InstructionKind.ARRAY_ACCESS
        if (l is JIRFieldRef || r is JIRFieldRef) return InstructionKind.FIELD_ACCESS
        if (r is JIRCallExpr && isConstructorCall(r)) return InstructionKind.OBJECT_CREATION
        if (r is JIRCallExpr) return InstructionKind.METHOD_CALL
        return InstructionKind.ASSIGNMENT
    }

    private fun ParserRuleContext?.findParentType(type: Class<out ParserRuleContext>): ParserRuleContext? {
        if (this == null || parent == null) return null
        var cur = this
        while (cur != null && !type.isInstance(cur) && cur !is JavaParser.BlockStatementContext) {
            cur = cur.parent as ParserRuleContext?
        }
        if (cur is JavaParser.BlockStatementContext) return null
        return cur
    }

    private fun ParserRuleContext?.findChildType(type: Class<out ParserRuleContext>): ParserRuleContext? {
        if (this == null || children == null) return null
        return children.find { type.isInstance(it) } as ParserRuleContext?
    }

    private fun checkMethodName(name: String, node: JavaParser.MethodCallContext): Boolean {
        val identifier = node.methodIdentifier() ?: return false
        return identifier.text == name
    }

    private fun checkMethodName(name: String, node: MemberReferenceExpressionContext): Boolean {
        val methodCall = node.methodCall() ?: return false
        val identifier = methodCall.methodIdentifier() ?: return false
        return identifier.text == name
    }

    private fun ParserRuleContext.checkDeclarationName(name: String): Boolean {
        return when (this) {
            is JavaParser.MethodDeclarationContext -> {
                val identifier = identifier() ?: return false
                identifier.text == name
            }
            else -> false
        }
    }

    private fun findConstructorDeclarationContext(root: ParseTree, line: Int): ParserRuleContext? {
        val declarations = collectContexts(root, line) {
            it is JavaParser.ConstructorDeclarationContext && coversLine(it, line)
        }
        return declarations.maxByOrNull { spanLen(it) }
    }

    private fun findMethodDeclarationContext(root: ParseTree, line: Int, method: JIRMethod): ParserRuleContext? {
        if (method.isConstructor) {
            return findConstructorDeclarationContext(root, line)
        }

        val methodName = method.name

        val declarations = mutableListOf<JavaParser.MethodDeclarationContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitMethodDeclaration(ctx: JavaParser.MethodDeclarationContext) {
                if (coversLine(ctx, line) && ctx.checkDeclarationName(methodName)) {
                    declarations.add(ctx)
                }
                super.visitMethodDeclaration(ctx)
            }
        }
        root.accept(collector)

        return declarations.maxByOrNull { spanLen(it) }
    }

    private fun findMethodDeclaration(root: ParseTree, line: Int, inst: JIRInst): LocationSpan? {
        val method = inst.location.method
        val declaration = findMethodDeclarationContext(root, line, method) ?: return null
        // expecting return type, identifier and arguments for declaration; skipping method body
        // no return type for constructors
        val paramsIndex = if (method.isConstructor) 1 else 2
        if (declaration.children == null || declaration.children.size <= paramsIndex) return null
        val childStart = declaration.children[0] as ParserRuleContext
        val childStop = declaration.children[paramsIndex] as ParserRuleContext
        return createLocationSpan(childStart.start, childStop.stop)
    }

    private fun findMethodEnd(root: ParseTree, line: Int, inst: JIRInst): LocationSpan? {
        val declaration = findMethodDeclarationContext(root, line, inst.location.method) ?: return null
        val endToken = declaration.stop ?: return null
        return createLocationSpan(endToken, endToken)
    }

    private fun findMethodCallNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val call = traits.getCallExpr(inst) ?: return oldFindMethodCallNode(root, line)

        val callee = call.callee.name

        val withInstance = mutableListOf<ParserRuleContext>()
        val simpleCall = mutableListOf<ParserRuleContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitMemberReferenceExpression(ctx: MemberReferenceExpressionContext) {
                if (checkMethodName(callee, ctx) && coversLine(ctx, line)) {
                    withInstance.add(ctx)
                }
                super.visitMemberReferenceExpression(ctx)
            }

            override fun visitMethodCall(ctx: JavaParser.MethodCallContext) {
                if (checkMethodName(callee, ctx) && coversLine(ctx, line)) {
                    simpleCall.add(ctx)
                }
                super.visitMethodCall(ctx)
            }
        }
        root.accept(collector)
        val filtered = withInstance.filter { exactLine(it, line) }
        if (filtered.isEmpty()) return adjustForAssignment(simpleCall.minByOrNull { spanLen(it) }, line)

        return adjustForAssignment(filtered.maxByOrNull { spanLen(it) }, line)
    }

    private fun oldFindMethodCallNode(root: ParseTree, line: Int): ParserRuleContext? =
        findSmallestOfTypes(
            root, line,
            JavaParser.MethodCallExpressionContext::class.java,
            JavaParser.MethodCallContext::class.java,
            JavaParser.PrimaryInvocationContext::class.java,
            MemberReferenceExpressionContext::class.java,
        )

    private fun checkCreatedType(expr: ParserRuleContext, typeName: String): Boolean {
        if (expr is JavaParser.MethodCallContext && checkMethodName("super", expr)) return true
        if (expr !is JavaParser.ObjectCreationExpressionContext) return false
        val ctx = expr.findChildType(JavaParser.CreatorContext::class.java)
            .findChildType(JavaParser.CreatedNameContext::class.java)
            .findChildType(JavaParser.IdentifierWithTypeArgsContext::class.java)
            .findChildType(JavaParser.IdentifierContext::class.java)
            ?: return false
        return ctx.text == typeName
    }

    private fun findObjectCreationNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val callExpr = inst.callExpr ?: return null
        val typeName = callExpr.method.method.enclosingClass.simpleName
        val creations = collectContexts(root, line) { checkCreatedType(it, typeName) }
        return creations.maxByOrNull { spanLen(it) }
    }

    private fun adjustForAssignment(node: ParserRuleContext?, line: Int): ParserRuleContext? {
        if (node == null) return null
        var curParent = node.parent as? ParserRuleContext
        while (
            curParent !is BinaryOperatorExpressionContext && curParent !is JavaParser.LocalVariableDeclarationContext
            && curParent !is JavaParser.BlockStatementContext && curParent != null && exactLine(curParent, line)
        ) {
            curParent = curParent.parent as? ParserRuleContext
        }
        if (curParent == null || curParent is JavaParser.BlockStatementContext || !exactLine(curParent, line)) return node
        return curParent
    }

    private fun checkArrayInitializerName(ctx: JavaParser.ArrayInitializerContext, name: String?): Boolean {
        val arrayName = ctx.findParentType(JavaParser.VariableDeclaratorContext::class.java)
            .findChildType(JavaParser.VariableDeclaratorIdContext::class.java)
            .findChildType(JavaParser.IdentifierContext::class.java)
            ?: return false
        return name == arrayName.text
    }

    private fun checkArrayName(ctx: SquareBracketExpressionContext, name: String?): Boolean {
        if (ctx.childCount < 1 || name == null) return false
        return ctx.children[0].text == name
    }

    private fun findArrayAccessNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val arrayName = inst.getArrayName()
        val arrayAccess = mutableListOf<ParserRuleContext>()
        val arrayInit = mutableListOf<ParserRuleContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitSquareBracketExpression(ctx: SquareBracketExpressionContext)  {
                if (coversLine(ctx, line) && checkArrayName(ctx, arrayName)) {
                    arrayAccess.add(ctx)
                }
                super.visitSquareBracketExpression(ctx)
            }

            override fun visitArrayInitializer(ctx: JavaParser.ArrayInitializerContext) {
                if (coversLine(ctx, line) && checkArrayInitializerName(ctx, arrayName)) {
                    arrayInit.add(ctx)
                }
                super.visitArrayInitializer(ctx)
            }
        }
        root.accept(collector)

        val access =
            if (arrayAccess.isNotEmpty()) arrayAccess.minBy { spanLen(it) }
            else if (arrayInit.isNotEmpty()) arrayInit.minBy { spanLen(it) }
            else findSmallestOfTypes(
                root, line,
                SquareBracketExpressionContext::class.java,
                JavaParser.ArrayInitializerContext::class.java,
            )

        return adjustForAssignment(access, line)
    }

    private fun findReturnNode(root: ParseTree, line: Int): ParserRuleContext? =
        findSmallestOfTypes(root, line,
            JavaParser.ReturnExpressionContext::class.java
        )

    private fun findAssignmentNode(root: ParseTree, line: Int): ParserRuleContext? {
        val candidates = mutableListOf<BinaryOperatorExpressionContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitBinaryOperatorExpression(ctx: BinaryOperatorExpressionContext) {
                if (coversLine(ctx, line) && isAssignmentOperator(ctx)) {
                    candidates.add(ctx)
                }
                super.visitBinaryOperatorExpression(ctx)
            }
        }
        root.accept(collector)

        return candidates.minByOrNull { spanLen(it) }
    }

    private fun JIRInst.getArrayValue(): JIRValue? {
        if (this !is JIRAssignInst) return null
        if (lhv is JIRArrayAccess) return (lhv as JIRArrayAccess).array
        if (rhv is JIRArrayAccess) return (rhv as JIRArrayAccess).array
        return null
    }

    private fun JIRInst?.getArrayName(): String? {
        val value = this?.getArrayValue() ?: return null
        return traits.getReadableValue(this, value)?.replace("\"", "")
    }

    private fun JIRInst?.getFieldName(): String? {
        if (this !is JIRAssignInst) return null
        if (lhv is JIRFieldRef) return (lhv as JIRFieldRef).field.name
        if (rhv is JIRFieldRef) return (rhv as JIRFieldRef).field.name
        return null
    }

    private fun MemberReferenceExpressionContext.checkFieldName(name: String?): Boolean {
        val field = identifier() ?: return false
        if (name == null) return true
        return field.text == name
    }

    private fun findFieldAccessNode(root: ParseTree, line: Int, inst: JIRInst?): ParserRuleContext? {
        val field = inst.getFieldName()

        val memberRefs = mutableListOf<MemberReferenceExpressionContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitMemberReferenceExpression(ctx: MemberReferenceExpressionContext) {
                if (coversLine(ctx, line) && ctx.checkFieldName(field)) {
                    memberRefs.add(ctx)
                }
                super.visitMemberReferenceExpression(ctx)
            }
        }
        root.accept(collector)

        return adjustForAssignment(memberRefs.maxByOrNull { spanLen(it) }, line)
    }

    private fun findSmallestSpan(root: ParseTree, line: Int): LocationSpan? {
        // coversLine is already called inside `collectContexts`
        val lineContexts = collectContexts(root, line) { true }
        val broadest = lineContexts.minByOrNull { spanLen(it) } ?: return null
        return createLocationSpan(broadest.start, broadest.stop)
    }

    private fun findBroadestSpan(root: ParseTree, line: Int): LocationSpan? {
        val lineContexts = collectContexts(root, line) { exactLine(it, line) }
        val broadest = lineContexts.maxByOrNull { spanLen(it) } ?: return null
        return createLocationSpan(broadest.start, broadest.stop)
    }

    private fun spanLen(ctx: ParserRuleContext): Int {
        val stop = ctx.stop ?: return Int.MAX_VALUE
        return stop.tokenIndex - ctx.start.tokenIndex
    }

    private abstract class LineBasedVisitor(val line: Int) : JavaParserBaseVisitor<Unit>() {
        override fun visitChildren(node: RuleNode) {
            val n = node.childCount
            for (i in 0..<n) {
                val c = node.getChild(i)

                if (c is ParserRuleContext) {
                    if (!coversLine(c, line)) continue
                }

                c.accept(this)
            }
            return
        }
    }

    @SafeVarargs
    private fun findSmallestOfTypes(
        root: ParseTree,
        line: Int,
        vararg types: Class<out ParserRuleContext>
    ): ParserRuleContext? {
        val set = types.toSet()
        val candidates = collectContexts(root, line) { ctx -> set.any { it.isInstance(ctx) } }
        return candidates.minByOrNull { spanLen(it) }
    }

    private inline fun collectContexts(
        root: ParseTree,
        line: Int,
        crossinline predicate: (ParserRuleContext) -> Boolean
    ): List<ParserRuleContext> {
        val res = ArrayList<ParserRuleContext>()
        val stack = ArrayDeque<ParseTree>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeFirst()
            if (n is ParserRuleContext) {
                if (!coversLine(n, line)) continue

                if (predicate(n)) {
                    res.add(n)
                }
            }
            val cnt = n.childCount
            for (i in 0 until cnt) stack.addLast(n.getChild(i))
        }
        return res
    }

    private fun isAssignmentOperator(ctx: BinaryOperatorExpressionContext): Boolean {
        // Look for terminal operator tokens among children
        val ops = setOf("=", "+=", "-=", "*=", "/=", "&=", "|=", "^=", ">>=", ">>>=", "<<=", "%=")
        val childCount = ctx.childCount
        for (i in 0 until childCount) {
            val ch = ctx.getChild(i)
            if (ch is TerminalNode) {
                val text = ch.text
                if (text in ops) return true
            }
        }
        return false
    }

    private fun tokenEndColumn(t: Token): Int {
        val textLen = t.text?.length
        if (textLen != null) return t.charPositionInLine + textLen
        val length = if (t.stopIndex >= t.startIndex) (t.stopIndex - t.startIndex + 1) else 1
        return t.charPositionInLine + length
    }

    companion object {
        private val logger = object : KLogging() {}.logger

        private fun exactLine(ctx: ParserRuleContext, line: Int): Boolean {
            val stop = ctx.stop ?: return false
            return ctx.start.line == line && line == stop.line
        }

        private fun coversLine(ctx: ParserRuleContext, line: Int): Boolean {
            val stop = ctx.stop ?: return false
            return ctx.start.line <= line && line <= stop.line
        }
    }
}
