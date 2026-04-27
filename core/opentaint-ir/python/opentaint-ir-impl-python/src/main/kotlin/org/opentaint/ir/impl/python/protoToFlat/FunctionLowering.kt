package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.impl.python.flat.*
import org.opentaint.ir.impl.python.protoToFlat.cfg.CfgBuild
import org.opentaint.ir.impl.python.proto.MypyFuncDefProto
import org.opentaint.ir.impl.python.proto.MypyLambdaExprProto

/**
 * Lower a single function-like scope into a [FlatFunctionIR]. Covers four
 * shapes uniformly: top-level, methods, nested defs, lambdas. Each call is
 * independent — there is no per-function state hanging off any builder; every
 * non-trivial helper threads a [ModuleContext] explicitly.
 */
internal object FunctionLowering {

    /** Top-level function or method. `enclosingClass` is non-null iff this is a method. */
    fun lowerTopLevel(
        module: ModuleContext,
        funcDef: MypyFuncDefProto,
        decorators: List<FlatDecorator>,
        enclosingClass: String?,
    ): FlatFunctionIR {
        val qualifiedName = qualifyTopLevel(module.moduleName, enclosingClass, funcDef)
        val cfg = if (funcDef.hasBody()) {
            CfgBuild.buildFunctionCfg(module, qualifiedName, funcDef.body, sourceLabel = funcDef.name)
        } else FlatCFG.EMPTY

        return FlatFunctionIR(
            name = funcDef.name,
            qualifiedName = qualifiedName,
            parentQualifiedName = null,
            kind = if (enclosingClass != null) FlatFunctionKind.METHOD else FlatFunctionKind.TOP_LEVEL,
            cfg = cfg,
            parameters = TypeLowering.convertParameters(funcDef.argumentsList),
            returnType = if (funcDef.hasReturnType()) TypeLowering.convertType(funcDef.returnType) else FlatAnyType,
            isAsync = funcDef.isAsync,
            isGenerator = funcDef.isGenerator,
            closureVars = funcDef.closureVarsList,
            decorators = decorators,
        )
    }

    /**
     * Nested function defined inside another function body. Built as a
     * synthetic module-level function whose name is uniqued per module to
     * avoid collisions between same-named inner functions.
     */
    fun lowerNestedFunction(
        module: ModuleContext,
        funcDef: MypyFuncDefProto,
        decorators: List<FlatDecorator>,
        enclosingQualifiedName: String,
    ): FlatFunctionIR {
        val uniqueName = module.freshNestedName(funcDef.name)
        val qualifiedName = "$enclosingQualifiedName.${funcDef.name}"

        val cfg = if (funcDef.hasBody()) {
            CfgBuild.buildFunctionCfg(
                module = module,
                qualifiedName = qualifiedName,
                body = funcDef.body,
                sourceLabel = funcDef.name,
                errorPrefix = "Failed to build CFG for nested $qualifiedName",
            )
        } else FlatCFG.EMPTY

        val closureVars = if (funcDef.hasBody()) {
            FreeVarAnalyzer.collectFreeVars(funcDef.body, funcDef.argumentsList.map { it.name })
        } else emptyList()

        return FlatFunctionIR(
            name = uniqueName,
            qualifiedName = qualifiedName,
            parentQualifiedName = enclosingQualifiedName,
            kind = FlatFunctionKind.NESTED_DEF,
            cfg = cfg,
            parameters = TypeLowering.convertParameters(funcDef.argumentsList),
            returnType = if (funcDef.hasReturnType()) TypeLowering.convertType(funcDef.returnType) else FlatAnyType,
            isAsync = funcDef.isAsync,
            isGenerator = funcDef.isGenerator,
            closureVars = closureVars,
            decorators = decorators,
        )
    }

    /**
     * Lambda expression. Lifted to a synthetic module-level function. The
     * enclosing function's qualified name is recorded for downstream passes
     * that want to associate the lambda with its lexical parent.
     */
    fun lowerLambda(
        module: ModuleContext,
        expr: MypyLambdaExprProto,
        parentQualifiedName: String?,
    ): FlatFunctionIR {
        val name = module.freshLambdaName()
        val qualifiedName = "${module.moduleName}.$name"

        val cfg = CfgBuild.buildFunctionCfg(
            module = module,
            qualifiedName = qualifiedName,
            body = expr.body,
            sourceLabel = name,
            errorPrefix = "Failed to build CFG for lambda $qualifiedName",
        )

        return FlatFunctionIR(
            name = name,
            qualifiedName = qualifiedName,
            parentQualifiedName = parentQualifiedName,
            kind = FlatFunctionKind.LAMBDA,
            cfg = cfg,
            parameters = TypeLowering.convertParameters(expr.argumentsList),
            returnType = if (expr.hasReturnType()) TypeLowering.convertType(expr.returnType) else FlatAnyType,
            isAsync = false,
            isGenerator = false,
            closureVars = emptyList(),
            decorators = emptyList(),
        )
    }

    /**
     * Compose the qualified name for a top-level function or method. For
     * methods we always derive from `moduleName.className.funcName`: mypy's
     * `funcDef.fullname` is unreliable when the method is decorated (the
     * Python serializer doesn't pass `enclosing_class` to the Decorator path).
     */
    private fun qualifyTopLevel(
        moduleName: String,
        enclosingClass: String?,
        funcDef: MypyFuncDefProto,
    ): String = when {
        enclosingClass != null -> "$moduleName.$enclosingClass.${funcDef.name}"
        funcDef.fullname.isNotEmpty() -> funcDef.fullname
        else -> "$moduleName.${funcDef.name}"
    }
}
