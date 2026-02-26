package org.opentaint.jvm.sast.ast

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.antlr.v4.runtime.tree.TerminalNode
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.jvm.sast.sarif.JIRSarifTraits
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.sast.sarif.IntermediateLocation
import org.opentaint.jvm.sast.sarif.LocationSpan
import org.opentaint.jvm.sast.sarif.LocationType
import org.opentaint.semgrep.pattern.kotlin.antlr.KotlinLexer
import org.opentaint.semgrep.pattern.kotlin.antlr.KotlinParser
import org.opentaint.semgrep.pattern.kotlin.antlr.KotlinParser.KotlinFileContext
import org.opentaint.semgrep.pattern.kotlin.antlr.KotlinParserBaseVisitor
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

class KotlinAstSpanResolver(traits: JIRSarifTraits) : AbstractAstSpanResolver(traits) {

    override fun computeSpan(sourceLocation: Path, location: IntermediateLocation): LocationSpan? = runCatching {
        val ast = getKotlinAst(sourceLocation) ?: return@runCatching null
        val targetLine = location.info.lineNumber
        // if failed, let's highlight the entire line
        computeSpan(ast, location)
            ?: findBroadestSpan(ast, targetLine)
            ?: findSmallestSpan(ast, targetLine)
    }.onFailure { ex ->
        logger.error(ex) { "Span resolution failure" }
    }.getOrNull()

    override fun getParameterName(sourceLocation: Path, inst: JIRInst, paramIdx: Int): String? = runCatching {
        val ast = getKotlinAst(sourceLocation) ?: return@runCatching null

        val method = inst.location.method
        val line = traits.lineNumber(inst)
        val declaration = findFunctionDeclarationContext(ast, line, method) ?: return@runCatching null

        val paramsContext = declaration.findChildType(KotlinParser.FunctionValueParametersContext::class.java)
            ?: return@runCatching null

        val params = paramsContext.children
            ?.filterIsInstance<KotlinParser.FunctionValueParameterContext>()
            ?: return@runCatching null

        if (paramIdx >= params.size) return@runCatching null

        params[paramIdx]
            .findChildType(KotlinParser.ParameterContext::class.java)
            .findChildType(KotlinParser.SimpleIdentifierContext::class.java)
            ?.text
    }.onFailure {
        logger.error { "Argument name resolution failure" }
    }.getOrNull()

    private val parsedFiles = ConcurrentHashMap<Path, Optional<KotlinFileContext>>()

    private fun getKotlinAst(path: Path): KotlinFileContext? =
        parsedFiles.computeIfAbsent(path) { Optional.ofNullable(parseKotlinFile(path)) }.getOrNull()

    private fun parseKotlinFile(path: Path): KotlinFileContext? = runCatching {
        val lexer = KotlinLexer(CharStreams.fromPath(path)).apply { removeErrorListeners() }
        val tokenStream = CommonTokenStream(lexer)
        val parser = KotlinParser(tokenStream).apply { removeErrorListeners() }
        parser.kotlinFile()
    }.onFailure { ex ->
        logger.error(ex) { "File parsing failure" }
    }.getOrNull()

    private fun computeSpan(ast: KotlinFileContext, location: IntermediateLocation): LocationSpan? {
        val targetLine = location.info.lineNumber
        val inst = location.inst as JIRInst

        if (location.type == LocationType.Multiple) {
            return findBroadestSpan(ast, targetLine)
        }
        if (location.isMethodEntry()) {
            return findFunctionDeclaration(ast, targetLine, inst)
        }
        if (location.isMethodExit()) {
            return findFunctionEnd(ast, targetLine, inst)
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

        return createSpanForNode(node)
    }

    private fun createSpanForNode(node: ParserRuleContext): LocationSpan? {
        val startToken = when (node) {
            is KotlinParser.PropertyDeclarationContext -> {
                node.VAL()?.symbol ?: node.VAR()?.symbol ?: node.start
            }
            else -> node.start
        }
        return createLocationSpan(startToken, node.stop)
    }

    private fun checkMethodName(name: String, node: KotlinParser.PostfixUnaryExpressionContext): Boolean {
        // In Kotlin, method calls are typically postfixUnaryExpression with callSuffix
        // We need to find the identifier being called
        val atomic = node.findChildType(KotlinParser.AtomicExpressionContext::class.java) ?: return false
        val identifier = atomic.findChildType(KotlinParser.SimpleIdentifierContext::class.java)
        return identifier?.text == name
    }

    private fun checkMethodNameInCall(name: String, node: KotlinParser.PostfixUnaryExpressionContext): Boolean {
        // Check if this is a member access (obj.method()) or simple call
        val children = node.children ?: return false

        // Look for the method name in postfix operations (member access followed by call)
        for (i in children.indices) {
            val child = children[i]
            if (child is KotlinParser.PostfixUnaryOperationContext) {
                val memberAccess = child.findChildType(KotlinParser.MemberAccessOperatorContext::class.java)
                if (memberAccess != null) {
                    val nextChild = children.getOrNull(i + 1)
                    if (nextChild is KotlinParser.PostfixUnaryOperationContext) {
                        val callSuffix = nextChild.findChildType(KotlinParser.CallSuffixContext::class.java)
                        if (callSuffix != null) {
                            // The method name is in the simpleIdentifier of member access
                            val methodId = child.findChildType(KotlinParser.SimpleIdentifierContext::class.java)
                            if (methodId?.text == name) return true
                        }
                    }
                }
                // Check simple identifier in postfix operation
                val simpleId = child.findChildType(KotlinParser.SimpleIdentifierContext::class.java)
                if (simpleId?.text == name) {
                    // Verify there's a call suffix following
                    val hasCallSuffix = children.any {
                        it is KotlinParser.PostfixUnaryOperationContext &&
                                it.findChildType(KotlinParser.CallSuffixContext::class.java) != null
                    }
                    if (hasCallSuffix) return true
                }
            }
        }

        // Check if the atomic expression itself is the method name (simple call)
        return checkMethodName(name, node)
    }

    private fun ParserRuleContext.checkDeclarationName(name: String): Boolean {
        return when (this) {
            is KotlinParser.FunctionDeclarationContext -> {
                val identifierCtx = findChildType(KotlinParser.IdentifierContext::class.java)
                val simpleId = identifierCtx?.findChildType(KotlinParser.SimpleIdentifierContext::class.java)
                    ?: return false
                simpleId.text == name
            }
            else -> false
        }
    }

    private fun findSecondaryConstructorContext(root: ParseTree, line: Int): ParserRuleContext? {
        val declarations = collectContexts(root, line) {
            it is KotlinParser.SecondaryConstructorContext && coversLine(it, line)
        }
        return declarations.maxByOrNull { spanLen(it) }
    }

    private fun findPrimaryConstructorContext(root: ParseTree, line: Int): ParserRuleContext? {
        val declarations = collectContexts(root, line) {
            it is KotlinParser.PrimaryConstructorContext && coversLine(it, line)
        }
        return declarations.maxByOrNull { spanLen(it) }
    }

    private fun findFunctionDeclarationContext(root: ParseTree, line: Int, method: JIRMethod): ParserRuleContext? {
        if (method.isConstructor) {
            return findSecondaryConstructorContext(root, line)
                ?: findPrimaryConstructorContext(root, line)
        }

        val methodName = method.name

        val declarations = mutableListOf<KotlinParser.FunctionDeclarationContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitFunctionDeclaration(ctx: KotlinParser.FunctionDeclarationContext) {
                if (coversLine(ctx, line) && ctx.checkDeclarationName(methodName)) {
                    declarations.add(ctx)
                }
                super.visitFunctionDeclaration(ctx)
            }
        }
        root.accept(collector)

        return declarations.maxByOrNull { spanLen(it) }
    }

    private fun findFunctionDeclaration(root: ParseTree, line: Int, inst: JIRInst): LocationSpan? {
        val method = inst.location.method
        val declaration = findFunctionDeclarationContext(root, line, method) ?: return null

        // For Kotlin functions: fun name(params): ReturnType
        // We want to highlight from 'fun' to the closing parenthesis
        return when (declaration) {
            is KotlinParser.FunctionDeclarationContext -> {
                val startToken = findFunctionSignatureStart(declaration)
                val endToken = findFunctionSignatureEnd(declaration)
                createLocationSpan(startToken, endToken ?: startToken)
            }
            is KotlinParser.SecondaryConstructorContext -> {
                val startToken = declaration.CONSTRUCTOR()?.symbol ?: declaration.start
                val paramsContext = declaration.findChildType(KotlinParser.FunctionValueParametersContext::class.java)
                if (paramsContext != null) {
                    createLocationSpan(startToken, paramsContext.stop)
                } else {
                    createLocationSpan(startToken, startToken)
                }
            }
            is KotlinParser.PrimaryConstructorContext -> {
                val startToken = declaration.CONSTRUCTOR()?.symbol ?: declaration.start
                val paramsContext = declaration.findChildType(KotlinParser.ClassParametersContext::class.java)
                if (paramsContext != null) {
                    createLocationSpan(startToken, paramsContext.stop)
                } else {
                    createLocationSpan(startToken, declaration.stop)
                }
            }
            else -> createLocationSpan(declaration.start, declaration.stop)
        }
    }

    private fun findFunctionSignatureStart(declaration: KotlinParser.FunctionDeclarationContext): Token {
        val modifiers = declaration.modifierList()
        val funcModifiers = modifiers?.modifier()?.mapNotNull { it.functionModifier() } ?: emptyList()
        val hasSuspend = funcModifiers.any { it.SUSPEND() != null }
        val hasOperator = funcModifiers.any { it.OPERATOR() != null }
        val hasTypeParams = declaration.typeParameters() != null
        val receiverType = declaration.receiverType()
        val hasReceiverType = receiverType != null
        val hasDot = declaration.DOT()?.isNotEmpty() == true
        if (hasTypeParams || hasReceiverType || hasSuspend || hasDot) {
            if (receiverType != null) {
                return receiverType.start
            }
            if (hasDot && declaration.type().isNotEmpty()) {
                return declaration.type(0).start
            }
            val identifierCtx = declaration.findChildType(KotlinParser.IdentifierContext::class.java)
            if (identifierCtx != null) {
                return identifierCtx.start
            }
        }
        if (hasOperator) {
            val operatorModifier = funcModifiers.first { it.OPERATOR() != null }
            return operatorModifier.start
        }
        return declaration.FUN()?.symbol ?: declaration.start
    }

    private fun findFunctionSignatureEnd(declaration: KotlinParser.FunctionDeclarationContext): Token? {
        val functionBody = declaration.findChildType(KotlinParser.FunctionBodyContext::class.java)
        if (functionBody != null) {
            val bodyStartIndex = declaration.children?.indexOf(functionBody) ?: -1
            if (bodyStartIndex > 0) {
                for (i in (bodyStartIndex - 1) downTo 0) {
                    val child = declaration.children[i]
                    if (child is ParserRuleContext && child !is KotlinParser.FunctionBodyContext) {
                        return child.stop
                    }
                }
            }
        }
        val typeContexts = declaration.children?.filterIsInstance<KotlinParser.TypeContext>()
        if (!typeContexts.isNullOrEmpty()) {
            return typeContexts.last().stop
        }
        val paramsContext = declaration.findChildType(KotlinParser.FunctionValueParametersContext::class.java)
        if (paramsContext != null) {
            return paramsContext.stop
        }
        val identifierCtx = declaration.findChildType(KotlinParser.IdentifierContext::class.java)
        return identifierCtx?.stop
    }

    private fun findFunctionEnd(root: ParseTree, line: Int, inst: JIRInst): LocationSpan? {
        val declaration = findFunctionDeclarationContext(root, line, inst.location.method) ?: return null
        
        if (declaration is KotlinParser.FunctionDeclarationContext) {
            val functionBody = declaration.functionBody()
            if (functionBody != null) {
                val assignmentToken = functionBody.ASSIGNMENT()
                if (assignmentToken != null) {
                    return createLocationSpan(assignmentToken.symbol, functionBody.stop)
                }
                val block = functionBody.block()
                if (block != null) {
                    val endToken = block.stop
                    if (endToken != null) {
                        return createLocationSpan(endToken, endToken)
                    }
                }
            }
        }
        
        val endToken = declaration.stop ?: return null
        return createLocationSpan(endToken, endToken)
    }

    private fun findMethodCallNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val call = inst.callExpr ?: return oldFindMethodCallNode(root, line)

        val callee = call.callee.name

        val methodCalls = mutableListOf<ParserRuleContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitPostfixUnaryExpression(ctx: KotlinParser.PostfixUnaryExpressionContext) {
                // Check if this has a call suffix (method call)
                val hasCallSuffix = ctx.children?.any {
                    it is KotlinParser.PostfixUnaryOperationContext &&
                            it.findChildType(KotlinParser.CallSuffixContext::class.java) != null
                } == true

                if (hasCallSuffix && coversLine(ctx, line) && checkMethodNameInCall(callee, ctx)) {
                    methodCalls.add(ctx)
                }
                super.visitPostfixUnaryExpression(ctx)
            }
        }
        root.accept(collector)

        val filtered = methodCalls.filter { exactLine(it, line) }
        return if (filtered.isEmpty()) {
            adjustForAssignment(methodCalls.minByOrNull { spanLen(it) }, inst)
        } else {
            adjustForAssignment(filtered.maxByOrNull { spanLen(it) }, inst)
        }
    }

    private fun oldFindMethodCallNode(root: ParseTree, line: Int): ParserRuleContext? {
        val methodCalls = collectContexts(root, line) { ctx ->
            ctx is KotlinParser.PostfixUnaryExpressionContext &&
                    ctx.children?.any {
                        it is KotlinParser.PostfixUnaryOperationContext &&
                                it.findChildType(KotlinParser.CallSuffixContext::class.java) != null
                    } == true
        }
        return methodCalls.minByOrNull { spanLen(it) }
    }

    private fun checkCreatedType(expr: ParserRuleContext, typeName: String): Boolean {
        // In Kotlin, object creation is ClassName(args) - looks like a function call
        if (expr !is KotlinParser.PostfixUnaryExpressionContext) return false

        val atomic = expr.findChildType(KotlinParser.AtomicExpressionContext::class.java)
        val identifier = atomic?.findChildType(KotlinParser.SimpleIdentifierContext::class.java)
        val text = identifier?.text ?: return false

        val hasCallSuffix = expr.children?.any {
            it is KotlinParser.PostfixUnaryOperationContext &&
                    it.findChildType(KotlinParser.CallSuffixContext::class.java) != null
        } == true

        return hasCallSuffix && (text == typeName || text == typeName.split(".").last())
    }

    private fun findObjectCreationNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val callExpr = inst.callExpr ?: return null
        val typeName = callExpr.method.method.enclosingClass.simpleName
        val creations = collectContexts(root, line) { checkCreatedType(it, typeName) }
        return adjustForAssignment(creations.maxByOrNull { spanLen(it) }, inst)
    }

    private fun ParserRuleContext?.isAssignment(): Boolean {
        if (this == null) return false
        // In Kotlin: val/var declarations or assignment expressions
        return this is KotlinParser.PropertyDeclarationContext ||
                this is KotlinParser.VariableDeclarationContext ||
                (this is KotlinParser.PostfixUnaryExpressionContext && hasAssignmentOperator(this))
    }

    private fun hasAssignmentOperator(ctx: KotlinParser.PostfixUnaryExpressionContext): Boolean {
        val parent = ctx.parent
        if (parent !is KotlinParser.DisjunctionContext && parent !is KotlinParser.ExpressionContext) return false
        // Check sibling for assignment operator
        val grandParent = parent.parent as? ParserRuleContext ?: return false
        return grandParent.children?.any {
            it is KotlinParser.AssignmentOperatorContext
        } == true
    }

    private fun checkAssigneeName(expr: ParserRuleContext?, assignee: String): Boolean {
        val varName = when (expr) {
            is KotlinParser.PropertyDeclarationContext -> {
                expr.findChildType(KotlinParser.VariableDeclarationContext::class.java)
                    .findChildType(KotlinParser.SimpleIdentifierContext::class.java)
                    ?.text
            }
            is KotlinParser.VariableDeclarationContext -> {
                expr.findChildType(KotlinParser.SimpleIdentifierContext::class.java)?.text
            }
            else -> null
        }
        return varName == assignee
    }

    private fun ParserRuleContext?.isOutOfBlock(): Boolean =
        this == null || this is KotlinParser.StatementContext || this is KotlinParser.BlockContext

    private fun adjustForAssignment(node: ParserRuleContext?, inst: JIRInst?): ParserRuleContext? {
        if (node == null) return null
        var curParent = node.parent as? ParserRuleContext
        while (!curParent.isAssignment() && !curParent.isOutOfBlock()) {
            curParent = curParent!!.parent as? ParserRuleContext
        }
        if (curParent.isOutOfBlock()) return node
        if (curParent is KotlinParser.VariableDeclarationContext) {
            val propertyParent = curParent.parent as? KotlinParser.PropertyDeclarationContext
            if (propertyParent != null) return propertyParent
        }
        return curParent
    }

    private fun findAssignmentNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val candidates = collectContexts(root, line) {
            it is KotlinParser.PropertyDeclarationContext || it is KotlinParser.VariableDeclarationContext
        }
        val propertyDecl = candidates.filterIsInstance<KotlinParser.PropertyDeclarationContext>().maxByOrNull { spanLen(it) }
        if (propertyDecl != null) return propertyDecl
        return candidates.filterIsInstance<KotlinParser.VariableDeclarationContext>().maxByOrNull { spanLen(it) }
    }

    private fun checkArrayName(ctx: KotlinParser.PostfixUnaryExpressionContext, name: String?): Boolean {
        if (name == null) return false
        val atomic = ctx.findChildType(KotlinParser.AtomicExpressionContext::class.java)
        val identifier = atomic?.findChildType(KotlinParser.SimpleIdentifierContext::class.java)
        return identifier?.text == name
    }

    private fun hasArrayAccess(ctx: KotlinParser.PostfixUnaryExpressionContext): Boolean {
        return ctx.children?.any {
            it is KotlinParser.PostfixUnaryOperationContext &&
                    it.findChildType(KotlinParser.ArrayAccessContext::class.java) != null
        } == true
    }

    private fun findArrayAccessNode(root: ParseTree, line: Int, inst: JIRInst): ParserRuleContext? {
        val arrayName = inst.getArrayName()

        val arrayAccess = mutableListOf<ParserRuleContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitPostfixUnaryExpression(ctx: KotlinParser.PostfixUnaryExpressionContext) {
                if (coversLine(ctx, line) && hasArrayAccess(ctx) && checkArrayName(ctx, arrayName)) {
                    arrayAccess.add(ctx)
                }
                super.visitPostfixUnaryExpression(ctx)
            }
        }
        root.accept(collector)

        val access = arrayAccess.minByOrNull { spanLen(it) }
            ?: collectContexts(root, line) { it is KotlinParser.ArrayAccessContext }.minByOrNull { spanLen(it) }

        return adjustForAssignment(access, inst)
    }

    private fun findReturnNode(root: ParseTree, line: Int): ParserRuleContext? {
        val returns = collectContexts(root, line) {
            it is KotlinParser.JumpExpressionContext &&
                    it.children?.any { child ->
                        child is TerminalNode && child.text == "return"
                    } == true
        }
        return returns.minByOrNull { spanLen(it) }
    }

    private fun checkFieldName(ctx: KotlinParser.PostfixUnaryExpressionContext, name: String?): Boolean {
        // Check if this is a field access: obj.field or this.field
        val children = ctx.children ?: return false
        for (child in children) {
            if (child is KotlinParser.PostfixUnaryOperationContext) {
                val memberAccess = child.findChildType(KotlinParser.MemberAccessOperatorContext::class.java)
                if (memberAccess != null) {
                    val fieldId = child.findChildType(KotlinParser.SimpleIdentifierContext::class.java)
                    if (name == null || fieldId?.text == name) {
                        // Make sure this is not a method call (no call suffix after)
                        val idx = children.indexOf(child)
                        val nextChild = children.getOrNull(idx + 1)
                        if (nextChild !is KotlinParser.PostfixUnaryOperationContext ||
                            nextChild.findChildType(KotlinParser.CallSuffixContext::class.java) == null
                        ) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun findFieldAccessNode(root: ParseTree, line: Int, inst: JIRInst?): ParserRuleContext? {
        val field = inst.getFieldName()

        val memberRefs = mutableListOf<KotlinParser.PostfixUnaryExpressionContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitPostfixUnaryExpression(ctx: KotlinParser.PostfixUnaryExpressionContext) {
                if (coversLine(ctx, line) && checkFieldName(ctx, field)) {
                    memberRefs.add(ctx)
                }
                super.visitPostfixUnaryExpression(ctx)
            }
        }
        root.accept(collector)

        val result = memberRefs.maxByOrNull { spanLen(it) }
        if (result != null) {
            return adjustForAssignment(result, inst)
        }

        return findClassParameterForField(root, line, field)
    }

    private fun findClassParameterForField(root: ParseTree, line: Int, fieldName: String?): ParserRuleContext? {
        if (fieldName == null) return null

        val params = mutableListOf<KotlinParser.ClassParameterContext>()
        val collector = object : LineBasedVisitor(line) {
            override fun visitClassParameter(ctx: KotlinParser.ClassParameterContext) {
                if (coversLine(ctx, line)) {
                    val paramName = ctx.simpleIdentifier()?.text
                    if (paramName == fieldName) {
                        params.add(ctx)
                    }
                }
                super.visitClassParameter(ctx)
            }
        }
        root.accept(collector)

        return params.maxByOrNull { spanLen(it) }
    }

    private abstract class LineBasedVisitor(val line: Int) : KotlinParserBaseVisitor<Unit>() {
        override fun visitChildren(node: RuleNode) = visitChildrenWithLine(node, line)
    }
}

