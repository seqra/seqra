package org.opentaint.jvm.sast.ast

import mu.KLogging
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.opentaint.dataflow.jvm.util.JIRSarifTraits
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.jvm.sast.sarif.LocationSpan
import org.opentaint.jvm.sast.sarif.TracePathNode
import org.opentaint.semgrep.pattern.antlr.JavaLexer
import org.opentaint.semgrep.pattern.antlr.JavaParser
import org.opentaint.semgrep.pattern.antlr.JavaParser.BinaryOperatorExpressionContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.CompilationUnitContext
import org.opentaint.semgrep.pattern.antlr.JavaParser.MemberReferenceExpressionContext
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

class JavaAstSpanResolver(
    val traits: JIRSarifTraits
) {
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

    private fun computeSpan(ast: CompilationUnitContext, targetLine: Int, inst: JIRInst, trace: TracePathNode?): LocationSpan? {
        val kind = inferKind(inst)
        val node = when (kind) {
            InstructionKind.METHOD_CALL -> findMethodCallNode(ast, targetLine, trace)
            InstructionKind.OBJECT_CREATION -> findObjectCreationNode(ast, targetLine)
            InstructionKind.FIELD_ACCESS -> findFieldAccessNode(ast, targetLine)
            InstructionKind.ARRAY_ACCESS -> findArrayAccessNode(ast, targetLine)
            InstructionKind.RETURN -> findReturnNode(ast, targetLine)
            InstructionKind.ASSIGNMENT -> findAssignmentNode(ast, targetLine)
            InstructionKind.UNKNOWN -> null
        }

        if (node == null) {
            logger.trace { "Instruction ast not identified" }
            return null
        }

        val start = node.start ?: return null
        val stop = node.stop ?: return null

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

    private fun checkMethodName(name: String, node: ParserRuleContext): Boolean {
        return when (node) {
            is JavaParser.MethodCallContext -> {
                val identifier = node.children.find { it is JavaParser.MethodIdentifierContext } ?: return false
                identifier.text == name
            }
            is MemberReferenceExpressionContext -> {
                val methodCall = node.children.find { it is JavaParser.MethodCallContext } ?: return false
                val identifier = (methodCall as? ParserRuleContext)?.children?.find { it is JavaParser.MethodIdentifierContext } ?: return false
                identifier.text == name
            }
            else -> false
        }
    }

    private fun findMethodCallNode(root: ParseTree, line: Int, trace: TracePathNode?): ParserRuleContext? {
        if (trace == null) return oldFindMethodCallNode(root, line)
        val call = traits.getCallExpr(trace.statement as JIRInst) ?: return oldFindMethodCallNode(root, line)
        val callee = call.callee.name
        val withInstance = collectContexts(root) {
            (it is MemberReferenceExpressionContext || it is JavaParser.MethodCallContext)
                    && checkMethodName(callee, it) && coversLine(it, line)
        }
        return withInstance.maxByOrNull { spanLen(it) }
    }

    private fun oldFindMethodCallNode(root: ParseTree, line: Int): ParserRuleContext? =
        findSmallestOfTypes(root, line,
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

    private fun findArrayAccessNode(root: ParseTree, line: Int): ParserRuleContext? =
        findSmallestOfTypes(root, line,
            JavaParser.SquareBracketExpressionContext::class.java
        )

    private fun findReturnNode(root: ParseTree, line: Int): ParserRuleContext? =
        findSmallestOfTypes(root, line,
            JavaParser.ReturnExpressionContext::class.java
        )

    private fun findAssignmentNode(root: ParseTree, line: Int): ParserRuleContext? {
        val candidates = collectContexts(root) { ctx ->
            ctx is BinaryOperatorExpressionContext && coversLine(ctx, line) && isAssignmentOperator(ctx)
        }
        return candidates.minByOrNull { spanLen(it) }
    }

    private fun findFieldAccessNode(root: ParseTree, line: Int): ParserRuleContext? {
        val memberRefs = collectContexts(root) {
            it is MemberReferenceExpressionContext && coversLine(it, line)
        }
        var best: ParserRuleContext? = null
        for (m in memberRefs) {
            val id = findFirstChildOfType(m, JavaParser.IdentifierContext::class.java) ?: continue

            if (best == null || spanLen(id) < spanLen(best)) best = id
        }
        if (best != null) return best
        return memberRefs.minByOrNull { spanLen(it) }
    }

    private fun coversLine(ctx: ParserRuleContext, line: Int): Boolean {
        val stop = ctx.stop ?: return false
        return ctx.start.line <= line && line <= stop.line
    }

    private fun spanLen(ctx: ParserRuleContext): Int {
        val stop = ctx.stop ?: return Int.MAX_VALUE
        return stop.tokenIndex - ctx.start.tokenIndex
    }

    private fun <T : ParserRuleContext> findFirstChildOfType(root: ParseTree, cls: Class<T>): T? {
        val stack = ArrayDeque<ParseTree>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeFirst()
            if (n is ParserRuleContext && cls.isInstance(n)) return cls.cast(n)
            val cnt = n.childCount
            for (i in 0 until cnt) stack.addLast(n.getChild(i))
        }
        return null
    }

    @SafeVarargs
    private fun findSmallestOfTypes(
        root: ParseTree,
        line: Int,
        vararg types: Class<out ParserRuleContext>
    ): ParserRuleContext? {
        val set = types.toSet()
        val candidates = collectContexts(root) { ctx -> set.any { it.isInstance(ctx) } && coversLine(ctx, line) }
        return candidates.minByOrNull { spanLen(it) }
    }

    private inline fun collectContexts(
        root: ParseTree,
        crossinline predicate: (ParserRuleContext) -> Boolean
    ): List<ParserRuleContext> {
        val res = ArrayList<ParserRuleContext>()
        val stack = ArrayDeque<ParseTree>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeFirst()
            if (n is ParserRuleContext && predicate(n)) res.add(n)
            if (n is JavaParser.PrimaryInvocationContext) {
                n.text
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
    }
}
