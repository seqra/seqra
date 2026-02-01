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
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.jvm.sast.sarif.LocationSpan
import org.opentaint.jvm.sast.sarif.TracePathNode
import org.opentaint.jvm.sast.sarif.TracePathNodeKind
import org.opentaint.jvm.sast.sarif.isPureEntryPoint
import org.opentaint.semgrep.pattern.antlr.JavaLexer
import org.opentaint.semgrep.pattern.antlr.JavaParser
import org.opentaint.semgrep.pattern.antlr.JavaParser.BinaryOperatorExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.CompilationUnitContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.MemberReferenceExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParserBaseVisitor
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

class JavaAstSpanResolver(private val traits: JIRSarifTraits) {
    fun computeSpan(sourceLocation: Path, targetLine: Int, inst: JIRInst, trace: TracePathNode?): LocationSpan? = runCatching {
        val ast = getJavaAst(sourceLocation) ?: return@runCatching null
        computeSpan(ast, targetLine, inst, trace)
    }.onFailure { ex ->
        logger.error(ex) { "Span resolution failure" }
    }.getOrNull()

    private val parsedFiles = ConcurrentHashMap<Path, Optional<CompilationUnitContext>>()

    private fun getJavaAst(path: Path): CompilationUnitContext? =
        parsedFiles.computeIfAbsent(path) { Optional.ofNullable(parseJavaFile(path)) }.getOrNull()

    private fun parseJavaFile(path: Path): CompilationUnitContext? = runCatching {
        val lexer = JavaLexer(CharStreams.fromPath(path))
        val tokenStream = CommonTokenStream(lexer)
        val parser = JavaParser(tokenStream).also { it.removeErrorListeners() }
        parser.compilationUnit()
    }.onFailure { ex ->
        logger.error(ex) { "File parsing failure" }
    }.getOrNull()

    private fun TracePathNode?.isMethodEntry(): Boolean {
        if (this == null) return false
        return entry is MethodTraceResolver.TraceEntry.MethodEntry || entry.isPureEntryPoint()
    }

    private fun TracePathNode?.isMethodExit(): Boolean {
        if (this == null) return false
        return kind != TracePathNodeKind.SINK && entry is MethodTraceResolver.TraceEntry.Final
                && statement is JIRReturnInst
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

    private fun computeSpan(ast: CompilationUnitContext, targetLine: Int, inst: JIRInst, trace: TracePathNode?): LocationSpan? {
        if (trace.isMethodEntry()) {
            return findMethodDeclaration(ast, targetLine, inst)
        }
        if (trace.isMethodExit()) {
            return findMethodEnd(ast, targetLine, inst)
        }

        val kind = inferKind(inst)
        val node = when (kind) {
            InstructionKind.METHOD_CALL -> findMethodCallNode(ast, targetLine, inst)
            InstructionKind.OBJECT_CREATION -> findObjectCreationNode(ast, targetLine)
            InstructionKind.FIELD_ACCESS -> findFieldAccessNode(ast, targetLine, inst)
            InstructionKind.ARRAY_ACCESS -> findArrayAccessNode(ast, targetLine)
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

    private fun findMethodDeclarationContext(root: ParseTree, line: Int, inst: JIRInst): JavaParser.MethodDeclarationContext? {
        val methodName = inst.location.method.name

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
        val declaration = findMethodDeclarationContext(root, line, inst) ?: return null
        // expecting modifiers, identifier and arguments for declaration; skipping method body
        if (declaration.children.size < 3) return null
        val childStart = declaration.children[0] as ParserRuleContext
        val childStop = declaration.children[2] as ParserRuleContext
        return createLocationSpan(childStart.start, childStop.stop)
    }

    private fun findMethodEnd(root: ParseTree, line: Int, inst: JIRInst): LocationSpan? {
        val declaration = findMethodDeclarationContext(root, line, inst) ?: return null
        val endToken = declaration.stop
        return createLocationSpan(endToken, endToken)
    }

    private fun findMethodCallNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val call = traits.getCallExpr(inst) ?: return oldFindMethodCallNode(root, line)

        val callee = call.callee.name

        val withInstance = mutableListOf<ParserRuleContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitMemberReferenceExpression(ctx: MemberReferenceExpressionContext) {
                if (checkMethodName(callee, ctx) && coversLine(ctx, line)) {
                    withInstance.add(ctx)
                }
                super.visitMemberReferenceExpression(ctx)
            }

            override fun visitMethodCall(ctx: JavaParser.MethodCallContext) {
                if (checkMethodName(callee, ctx) && coversLine(ctx, line)) {
                    withInstance.add(ctx)
                }
                super.visitMethodCall(ctx)
            }
        }
        root.accept(collector)

        return adjustForAssignment(withInstance.maxByOrNull { spanLen(it) })
    }

    private fun oldFindMethodCallNode(root: ParseTree, line: Int): ParserRuleContext? =
        findSmallestOfTypes(
            root, line,
            JavaParser.MethodCallExpressionContext::class.java,
            JavaParser.MethodCallContext::class.java,
            JavaParser.PrimaryInvocationContext::class.java,
            MemberReferenceExpressionContext::class.java,
        )

    private fun findObjectCreationNode(root: ParseTree, line: Int): ParserRuleContext? =
        findSmallestOfTypes(root, line,
            JavaParser.ObjectCreationExpressionContext::class.java,
            JavaParser.ClassCreatorRestContext::class.java
        )

    private fun adjustForAssignment(node: ParserRuleContext?): ParserRuleContext? {
        if (node == null) return null
        var curParent = node.parent as? ParserRuleContext
        while (
            curParent !is BinaryOperatorExpressionContext && curParent !is JavaParser.LocalVariableDeclarationContext
            && curParent !is JavaParser.BlockStatementContext && curParent != null
        ) {
            curParent = curParent.parent as? ParserRuleContext
        }
        if (curParent == null || curParent is JavaParser.BlockStatementContext) return node
        return curParent
    }

    private fun findArrayAccessNode(root: ParseTree, line: Int): ParserRuleContext? =
        findSmallestOfTypes(root, line,
            JavaParser.SquareBracketExpressionContext::class.java
        )

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

        return adjustForAssignment(memberRefs.maxByOrNull { spanLen(it) })
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
        val candidates = collectContexts(root, line) { ctx -> set.any { it.isInstance(ctx) } && coversLine(ctx, line) }
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

        private fun coversLine(ctx: ParserRuleContext, line: Int): Boolean {
            val stop = ctx.stop ?: return false
            return ctx.start.line <= line && line <= stop.line
        }
    }
}
