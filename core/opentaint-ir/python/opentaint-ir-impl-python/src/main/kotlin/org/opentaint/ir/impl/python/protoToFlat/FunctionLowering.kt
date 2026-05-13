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

    /**
     * Top-level function or method.
     *
     * @param enclosingClassQualifiedName fully-qualified name of the enclosing
     *   class (e.g. `"module.Outer.Inner"`), or null for free functions.
     */
    fun lowerTopLevel(
        module: ModuleContext,
        funcDef: MypyFuncDefProto,
        decorators: List<FlatDecorator>,
        enclosingClassQualifiedName: String?,
    ): FlatFunctionIR {
        val qualifiedName = qualifyTopLevel(module.moduleName, enclosingClassQualifiedName, funcDef)
        val parameters = TypeLowering.convertParameters(funcDef.argumentsList)
        val cfgResult = if (funcDef.hasBody()) {
            CfgBuild.buildFunctionCfg(
                module = module,
                qualifiedName = qualifiedName,
                functionName = funcDef.name,
                body = funcDef.body,
                parameters = parameters,
                sourceLabel = funcDef.name,
                imports = module.imports.nestedChild(),
            )
        } else CfgBuild.CfgBuildResult.EMPTY

        return FlatFunctionIR(
            name = funcDef.name,
            qualifiedName = qualifiedName,
            parentQualifiedName = null,
            kind = if (enclosingClassQualifiedName != null) FlatFunctionKind.METHOD else FlatFunctionKind.TOP_LEVEL,
            cfg = cfgResult.cfg,
            parameters = parameters,
            returnType = if (funcDef.hasReturnType()) TypeLowering.convertType(funcDef.returnType) else FlatAnyType,
            isAsync = funcDef.isAsync,
            isGenerator = funcDef.isGenerator,
            decorators = decorators,
            nonlocalNames = cfgResult.nonlocalNames,
            globalNames = cfgResult.globalNames,
        )
    }

    /**
     * Nested function defined inside another function body. Built as a
     * synthetic module-level function whose [FlatFunctionIR.name] is the
     * suffix of [FlatFunctionIR.qualifiedName] after the module name.
     *
     * The lexical scope is encoded in the name itself with `$` separators
     * (so `qualifiedName == "$moduleName.$name"`). [enclosingName] is the
     * enclosing function's `name` field; combining it with the nested
     * def's source-level identifier gives a module-flat shape like
     * `outer$inner`. Shadowing siblings get a `$N` collision suffix.
     */
    fun lowerNestedFunction(
        module: ModuleContext,
        funcDef: MypyFuncDefProto,
        decorators: List<FlatDecorator>,
        enclosingQualifiedName: String,
        enclosingName: String,
        enclosingImports: ImportManager,
    ): FlatFunctionIR {
        val uniqueName = module.freshNestedName(enclosingName, funcDef.name)
        val qualifiedName = "${module.moduleName}.$uniqueName"
        val parameters = TypeLowering.convertParameters(funcDef.argumentsList)

        val cfgResult = if (funcDef.hasBody()) {
            CfgBuild.buildFunctionCfg(
                module = module,
                qualifiedName = qualifiedName,
                functionName = uniqueName,
                body = funcDef.body,
                parameters = parameters,
                sourceLabel = funcDef.name,
                errorPrefix = "Failed to build CFG for nested $qualifiedName",
                imports = enclosingImports.nestedChild(),
            )
        } else CfgBuild.CfgBuildResult.EMPTY

        return FlatFunctionIR(
            name = uniqueName,
            qualifiedName = qualifiedName,
            parentQualifiedName = enclosingQualifiedName,
            kind = FlatFunctionKind.NESTED_DEF,
            cfg = cfgResult.cfg,
            parameters = parameters,
            returnType = if (funcDef.hasReturnType()) TypeLowering.convertType(funcDef.returnType) else FlatAnyType,
            isAsync = funcDef.isAsync,
            isGenerator = funcDef.isGenerator,
            decorators = decorators,
            nonlocalNames = cfgResult.nonlocalNames,
            globalNames = cfgResult.globalNames,
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
        enclosingImports: ImportManager,
    ): FlatFunctionIR {
        val name = module.freshLambdaName()
        val qualifiedName = "${module.moduleName}.$name"
        val parameters = TypeLowering.convertParameters(expr.argumentsList)

        val cfgResult = CfgBuild.buildFunctionCfg(
            module = module,
            qualifiedName = qualifiedName,
            functionName = name,
            body = expr.body,
            parameters = parameters,
            sourceLabel = name,
            errorPrefix = "Failed to build CFG for lambda $qualifiedName",
            imports = enclosingImports.nestedChild(),
        )

        return FlatFunctionIR(
            name = name,
            qualifiedName = qualifiedName,
            parentQualifiedName = parentQualifiedName,
            kind = FlatFunctionKind.LAMBDA,
            cfg = cfgResult.cfg,
            parameters = parameters,
            returnType = if (expr.hasReturnType()) TypeLowering.convertType(expr.returnType) else FlatAnyType,
            isAsync = false,
            isGenerator = false,
            decorators = emptyList(),
            nonlocalNames = cfgResult.nonlocalNames,
            globalNames = cfgResult.globalNames,
        )
    }

    /**
     * Compose the qualified name for a top-level function or method. For
     * methods we always derive from `enclosingClassQualifiedName.funcName`:
     * mypy's `funcDef.fullname` is unreliable when the method is decorated
     * (the Python serializer doesn't always pass `enclosing_class` to the
     * Decorator path).
     */
    private fun qualifyTopLevel(
        moduleName: String,
        enclosingClassQualifiedName: String?,
        funcDef: MypyFuncDefProto,
    ): String = when {
        enclosingClassQualifiedName != null -> "$enclosingClassQualifiedName.${funcDef.name}"
        funcDef.fullname.isNotEmpty() -> funcDef.fullname
        else -> "$moduleName.${funcDef.name}"
    }
}
