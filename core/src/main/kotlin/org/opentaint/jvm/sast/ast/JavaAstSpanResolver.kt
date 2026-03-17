package org.opentaint.jvm.sast.ast

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.antlr.v4.runtime.tree.TerminalNode
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.sast.sarif.IntermediateLocation
import org.opentaint.jvm.sast.sarif.JIRSarifTraits
import org.opentaint.jvm.sast.sarif.LocationSpan
import org.opentaint.jvm.sast.sarif.LocationType
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

class JavaAstSpanResolver(traits: JIRSarifTraits) : AbstractAstSpanResolver(traits) {

    override fun computeSpan(sourceLocation: Path, location: IntermediateLocation): LocationSpan? = runCatching {
        val ast = getJavaAst(sourceLocation) ?: return@runCatching null
        val targetLine = location.info.lineNumber
        // if failed, let's highlight the entire line
        computeSpan(ast, location)
            ?: findBroadestSpan(ast, targetLine)
            ?: findSmallestSpan(ast, targetLine)
    }.onFailure { ex ->
        logger.error(ex) { "Span resolution failure" }
    }.getOrNull()

    override fun getParameterName(sourceLocation: Path, inst: JIRInst, paramIdx: Int): String? = runCatching {
        val ast = getJavaAst(sourceLocation) ?: return@runCatching null

        val method = inst.location.method
        val line = traits.lineNumber(inst)
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

    fun getJavaAst(path: Path): CompilationUnitContext? =
        parsedFiles.computeIfAbsent(path) { Optional.ofNullable(parseJavaFile(path)) }.getOrNull()

    private fun parseJavaFile(path: Path): CompilationUnitContext? = runCatching {
        val lexer = JavaLexer(CharStreams.fromPath(path)).apply { removeErrorListeners() }
        val tokenStream = CommonTokenStream(lexer)
        val parser = JavaParser(tokenStream).apply { removeErrorListeners() }
        parser.compilationUnit()?.apply {
            exception?.let { throw it }
        }
    }.onFailure { ex ->
        logger.error(ex) { "File parsing failure: $path" }
    }.getOrNull()

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
            InstructionKind.ASSIGNMENT -> findAssignmentNode(ast, targetLine, inst)
            InstructionKind.UNKNOWN -> null
        }

        if (node == null) {
            logger.trace { "Instruction ast not identified" }
            return null
        }

        return createLocationSpan(node.start, node.stop)
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
        val call = inst.callExpr ?: return oldFindMethodCallNode(root, line)

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
        if (filtered.isEmpty()) return adjustForAssignment(simpleCall.minByOrNull { spanLen(it) }, inst)

        return adjustForAssignment(filtered.maxByOrNull { spanLen(it) }, inst)
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
            ?: return false
        return ctx.text == typeName || ctx.text.split(".").last() == typeName
    }

    private fun findObjectCreationNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val callExpr = inst.callExpr ?: return null
        val typeName = callExpr.method.method.enclosingClass.simpleName
        val creations = collectContexts(root, line) { checkCreatedType(it, typeName) }
        return adjustForAssignment(creations.maxByOrNull { spanLen(it) }, inst)
    }

    private fun ParserRuleContext?.isAssignment(): Boolean =
        (this is BinaryOperatorExpressionContext && isAssignmentOperator(this))
                || this is JavaParser.LocalVariableDeclarationContext
                || this is JavaParser.ResourceContext

    private fun checkAssigneeName(expr: ParserRuleContext?, assignee: String): Boolean {
        val varName = when (expr) {
            is JavaParser.LocalVariableDeclarationContext ->
                expr.findChildType(JavaParser.VariableDeclaratorsContext::class.java)
                    .findChildType(JavaParser.VariableDeclaratorContext::class.java)
                    .findChildType(JavaParser.VariableDeclaratorIdContext::class.java)
                    .findChildType(JavaParser.IdentifierContext::class.java)
                    ?.text

            is BinaryOperatorExpressionContext ->
                expr.takeIf { isAssignmentOperator(expr) }
                    .findChildType(JavaParser.PrimaryExpressionContext::class.java)
                    .findChildType(JavaParser.PrimarySimpleContext::class.java)
                    .findChildType(JavaParser.IdentifierContext::class.java)
                    ?.text

            is JavaParser.ResourceContext ->
                expr.findChildType(JavaParser.VariableDeclaratorIdContext::class.java)
                    .findChildType(JavaParser.IdentifierContext::class.java)
                    ?.text

            else -> null
        }
        return varName == assignee
    }

    private fun ParserRuleContext?.isOutOfBlock(): Boolean =
        this == null || this is JavaParser.BlockStatementContext

    private fun adjustForAssignment(node: ParserRuleContext?, inst: JIRInst?): ParserRuleContext? {
        if (node == null) return null
        val assignee = inst.getAssignee() ?: return node
        var curParent = node.parent as? ParserRuleContext
        while (!curParent.isAssignment() && !curParent.isOutOfBlock()) {
            curParent = curParent!!.parent as? ParserRuleContext
        }
        if (curParent.isOutOfBlock() || !checkAssigneeName(curParent, assignee)) return node
        return curParent
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

    private fun findAssignmentNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val assignee = inst.getAssignee() ?: return null
        val candidates = collectContexts(root, line) { checkAssigneeName(it, assignee) }
        return candidates.minByOrNull { spanLen(it) }
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
            override fun visitSquareBracketExpression(ctx: SquareBracketExpressionContext) {
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

        return adjustForAssignment(access, inst)
    }

    private fun findReturnNode(root: ParseTree, line: Int): ParserRuleContext? =
        findSmallestOfTypes(
            root, line,
            JavaParser.ReturnExpressionContext::class.java
        )

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

        return adjustForAssignment(memberRefs.maxByOrNull { spanLen(it) }, inst)
    }

    private abstract class LineBasedVisitor(val line: Int) : JavaParserBaseVisitor<Unit>() {
        override fun visitChildren(node: RuleNode) = visitChildrenWithLine(node, line)
    }
}
