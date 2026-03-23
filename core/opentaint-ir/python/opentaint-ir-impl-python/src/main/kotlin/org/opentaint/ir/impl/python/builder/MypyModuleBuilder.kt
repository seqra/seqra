package org.opentaint.ir.impl.python.builder

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.*
import org.opentaint.ir.impl.python.converter.InstructionConverter
import org.opentaint.ir.impl.python.converter.TypeConverter
import org.opentaint.ir.impl.python.converter.ValueConverter
import org.opentaint.ir.impl.python.proto.*

/**
 * Builds PIRModule from a raw MypyModuleProto (AST).
 *
 * This replaces ModuleConverter for the new AST path.
 * Instead of converting already-lowered PIR protos, it:
 * 1. Walks the raw AST definitions
 * 2. Extracts class/function/field metadata
 * 3. Uses CfgBuilder to lower function bodies to PIR CFGs
 * 4. Produces the same PIRModule output as the old path
 */
class MypyModuleBuilder(
    private val astModule: MypyModuleProto,
    private val classpath: PIRClasspath? = null,
) {
    companion object {
        private val ENUM_BASE_CLASSES = setOf(
            "enum.Enum", "enum.IntEnum", "enum.Flag", "enum.IntFlag",
        )
    }

    val moduleName: String = astModule.name
    var lambdaCounter = 0
    val lambdaFunctions = mutableListOf<PIRFunctionProto>()
    var nestedFuncCounter = 0
    val nestedFunctions = mutableListOf<PIRFunctionProto>()
    private val diagnostics = mutableListOf<PIRDiagnostic>()
    private val typeConverter = TypeConverter()

    private val vc = ValueConverter(typeConverter)
    private val ic = InstructionConverter(typeConverter, vc)

    fun build(): PIRModule {
        // Propagate mypy build errors as diagnostics
        for (error in astModule.errorsList) {
            diagnostics.add(PIRDiagnostic(
                org.opentaint.ir.api.python.PIRDiagnosticSeverity.ERROR,
                error,
                moduleName,
                "MypyBuildError",
            ))
        }

        // Dummy module for back-references during construction
        val dummyModule = createDummyModule()

        val classes = mutableListOf<PIRClass>()
        val functions = mutableListOf<PIRFunction>()
        val fields = mutableListOf<PIRField>()

        for (def in astModule.defsList) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.CLASS_DEF -> {
                    classes.add(buildClass(def.classDef, dummyModule))
                }
                MypyDefinitionProto.KindCase.FUNC_DEF -> {
                    functions.add(buildFunction(def.funcDef, null, dummyModule))
                }
                MypyDefinitionProto.KindCase.DECORATOR -> {
                    functions.add(buildDecoratedFunction(def.decorator, null, dummyModule))
                }
                MypyDefinitionProto.KindCase.ASSIGNMENT -> {
                    for (lvalue in def.assignment.lvaluesList) {
                        if (lvalue.hasNameExpr()) {
                            val type = if (lvalue.hasExprType()) {
                                typeConverter.convert(lvalue.exprType)
                            } else PIRAnyType
                            fields.add(PIRFieldImpl(lvalue.nameExpr.name, type, false, true))
                        }
                    }
                }
                else -> {}
            }
        }

        // Build module_init CFG
        val moduleInit = buildModuleInit(dummyModule)

        // Add lambda and nested functions
        val lambdaFuncList = lambdaFunctions.map { convertLambdaFunction(it, dummyModule) }
        val nestedFuncList = nestedFunctions.map { convertLambdaFunction(it, dummyModule) }
        val allFunctions = functions + lambdaFuncList + nestedFuncList

        return PIRModuleImpl(
            name = moduleName,
            path = astModule.path,
            classes = classes,
            functions = allFunctions,
            fields = fields,
            moduleInit = moduleInit,
            imports = astModule.importsList,
            classpath = classpath ?: dummyModule.classpath,
            diagnostics = diagnostics,
        )
    }

    private fun createDummyModule(): PIRModule = object : PIRModule {
        override val name = moduleName
        override val path = astModule.path
        override val classes = emptyList<PIRClass>()
        override val functions = emptyList<PIRFunction>()
        override val fields = emptyList<PIRField>()
        override val moduleInit: PIRFunction get() = throw UnsupportedOperationException()
        override val imports = emptyList<String>()
        override val classpath: PIRClasspath
            get() = this@MypyModuleBuilder.classpath ?: object : PIRClasspath {
                override val modules = emptyList<PIRModule>()
                override fun findModuleOrNull(name: String): PIRModule? = null
                override fun findClassOrNull(qualifiedName: String): PIRClass? = null
                override fun findFunctionOrNull(qualifiedName: String): PIRFunction? = null
                override val pythonVersion = ""
                override val mypyVersion = ""
                override fun close() {}
            }
        override val diagnostics = emptyList<PIRDiagnostic>()
    }

    // ─── Class building ──────────────────────────────────

    private fun buildClass(classDef: MypyClassDefProto, module: PIRModule): PIRClass {
        val methods = mutableListOf<PIRFunction>()
        val classFields = mutableListOf<PIRField>()
        val nestedClasses = mutableListOf<PIRClass>()
        val properties = mutableListOf<PIRProperty>()
        val decorators = classDef.decoratorsList.map {
            PIRDecoratorImpl(it.name, it.qualifiedName, it.argumentsList)
        }

        for (def in classDef.bodyList) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.FUNC_DEF -> {
                    methods.add(buildFunction(def.funcDef, classDef.name, module))
                }
                MypyDefinitionProto.KindCase.DECORATOR -> {
                    methods.add(buildDecoratedFunction(def.decorator, classDef.name, module))
                }
                MypyDefinitionProto.KindCase.ASSIGNMENT -> {
                    for (lvalue in def.assignment.lvaluesList) {
                        if (lvalue.hasNameExpr()) {
                            val type = if (lvalue.hasExprType()) {
                                typeConverter.convert(lvalue.exprType)
                            } else PIRAnyType
                            classFields.add(PIRFieldImpl(lvalue.nameExpr.name, type, false, true))
                        }
                    }
                }
                MypyDefinitionProto.KindCase.CLASS_DEF -> {
                    nestedClasses.add(buildClass(def.classDef, module))
                }
                else -> {}
            }
        }

        // Group property methods into PIRProperty objects.
        // OverloadedFuncDef serializes all items: getter, setter, deleter in order.
        // The getter is the one with isProperty=true. Setter/deleter have the same name
        // but may NOT have isProperty=true (mypy only sets it on the @property getter).
        // We detect properties by finding methods with isProperty=true, then group all
        // methods sharing that name.
        val propertyGetterNames = methods.filter { it.isProperty }.map { it.name }.toSet()
        for (propName in propertyGetterNames) {
            val group = methods.filter { it.name == propName }
            // First occurrence is the getter (has isProperty=true)
            val getter = group.firstOrNull { it.isProperty }
            // Setter: method with more params than getter (has value parameter)
            val getterParamCount = getter?.parameters?.size ?: 0
            val setter = group.firstOrNull { !it.isProperty && it.parameters.size > getterParamCount }
                ?: group.firstOrNull { it !== getter && it.parameters.size > getterParamCount }
            // Deleter: method with same param count as getter, after getter, not setter
            val deleter = group.firstOrNull { it !== getter && it !== setter
                && it.parameters.size == getterParamCount }

            val propType = getter?.returnType ?: PIRAnyType
            properties.add(PIRPropertyImpl(propName, propType, getter, setter, deleter))
        }

        // Compute flags from raw data (migrated from Python serializer)
        val isEnum = classDef.baseClassesList.any { it in ENUM_BASE_CLASSES }
        val isDataclass = classDef.isDataclass || decorators.any { it.name == "dataclass" }

        val cls = PIRClassImpl(
            name = classDef.name,
            qualifiedName = classDef.fullname.ifEmpty { "$moduleName.${classDef.name}" },
            baseClasses = classDef.baseClassesList,
            mro = classDef.mroList,
            methods = methods,
            fields = classFields,
            nestedClasses = nestedClasses,
            properties = properties,
            decorators = decorators,
            isAbstract = classDef.isAbstract,
            isDataclass = isDataclass,
            isEnum = isEnum,
            module = module,
        )

        // Wire up enclosingClass back-reference on all methods
        for (method in methods) {
            if (method is PIRFunctionImpl) {
                method.enclosingClass = cls
            }
        }

        return cls
    }

    // ─── Function building ───────────────────────────────

    private fun buildFunction(
        funcDef: MypyFuncDefProto,
        enclosingClass: String?,
        module: PIRModule,
    ): PIRFunction {
        val qualifiedName = if (enclosingClass != null) {
            // Always use enclosingClass when present — the proto's fullname may be wrong
            // for decorated methods (Python serializer doesn't pass enclosing_class to
            // _serialize_func_def for Decorator nodes).
            "$moduleName.$enclosingClass.${funcDef.name}"
        } else if (funcDef.fullname.isNotEmpty()) {
            funcDef.fullname
        } else {
            "$moduleName.${funcDef.name}"
        }

        // Build CFG from raw AST body
        val cfgScope = ScopeStack()
        val cfgBuilder = CfgBuilder(cfgScope, this, currentFunctionQualifiedName = qualifiedName)
        val cfgProto = try {
            if (funcDef.hasBody()) {
                cfgBuilder.buildFunctionCfg(funcDef.body)
            } else {
                emptyCfgProto()
            }
        } catch (e: Exception) {
            diagnostics.add(PIRDiagnostic(
                org.opentaint.ir.api.python.PIRDiagnosticSeverity.ERROR,
                "Failed to build CFG for $qualifiedName: ${e.javaClass.simpleName}: ${e.message}",
                funcDef.name,
                e.javaClass.simpleName,
            ))
            emptyCfgProto()
        }

        val pirCfg = ic.convertCFG(cfgProto)

        val params = funcDef.argumentsList.mapIndexed { idx, arg ->
            val kind = when (arg.kind) {
                0, 1 -> PIRParameterKind.POSITIONAL_OR_KEYWORD  // ARG_POS, ARG_OPT
                2 -> PIRParameterKind.VAR_POSITIONAL            // ARG_STAR
                4 -> PIRParameterKind.VAR_KEYWORD               // ARG_STAR2
                3, 5 -> PIRParameterKind.KEYWORD_ONLY           // ARG_NAMED=3, ARG_NAMED_OPT=5
                else -> PIRParameterKind.POSITIONAL_OR_KEYWORD
            }
            val type = if (arg.hasType()) typeConverter.convert(arg.type) else PIRAnyType
            PIRParameterImpl(arg.name, type, kind, arg.hasDefault, null, idx)
        }

        val returnType = if (funcDef.hasReturnType()) {
            typeConverter.convert(funcDef.returnType)
        } else PIRAnyType

        return PIRFunctionImpl(
            name = funcDef.name,
            qualifiedName = qualifiedName,
            parameters = params,
            returnType = returnType,
            cfg = pirCfg,
            decorators = funcDef.decoratorsList.map {
                PIRDecoratorImpl(it.name, it.qualifiedName, it.argumentsList)
            },
            isAsync = funcDef.isAsync,
            isGenerator = funcDef.isGenerator,
            isStaticMethod = funcDef.isStatic,
            isClassMethod = funcDef.isClass,
            isProperty = funcDef.isProperty,
            closureVars = funcDef.closureVarsList,
            enclosingClass = null,
            module = module,
        )
    }

    private fun buildDecoratedFunction(
        decorator: MypyDecoratorDefProto,
        enclosingClass: String?,
        module: PIRModule,
    ): PIRFunction {
        val func = buildFunction(decorator.func, enclosingClass, module)

        // Extract decorator info and flags
        var isStatic = func.isStaticMethod
        var isClass = func.isClassMethod
        var isProp = func.isProperty
        val decList = mutableListOf<PIRDecorator>()

        for (decExpr in decorator.originalDecoratorsList) {
            if (decExpr.hasNameExpr()) {
                when (decExpr.nameExpr.name) {
                    "staticmethod" -> isStatic = true
                    "classmethod" -> isClass = true
                    "property" -> isProp = true
                }
                decList.add(PIRDecoratorImpl(
                    decExpr.nameExpr.name,
                    decExpr.nameExpr.fullname,
                    emptyList()
                ))
            }
        }

        return PIRFunctionImpl(
            func.name, func.qualifiedName, func.parameters, func.returnType,
            func.cfg, decList, func.isAsync, func.isGenerator,
            isStatic, isClass, isProp,
            func.closureVars, func.enclosingClass, module
        )
    }

    // ─── Module init ─────────────────────────────────────

    private fun buildModuleInit(module: PIRModule): PIRFunction {
        val cfgScope = ScopeStack()
        val cfgBuilder = CfgBuilder(cfgScope, this)

        val moduleStmts = mutableListOf<MypyStmtProto>()
        for (def in astModule.defsList) {
            if (def.kindCase == MypyDefinitionProto.KindCase.ASSIGNMENT) {
                moduleStmts.add(
                    MypyStmtProto.newBuilder()
                        .setAssignment(def.assignment)
                        .build()
                )
            }
        }

        val cfgProto = try {
            cfgBuilder.buildModuleInitCfg(moduleStmts)
        } catch (e: Exception) {
            diagnostics.add(PIRDiagnostic(
                org.opentaint.ir.api.python.PIRDiagnosticSeverity.ERROR,
                "Failed to build module_init CFG for $moduleName: ${e.javaClass.simpleName}: ${e.message}",
                "__module_init__",
                e.javaClass.simpleName,
            ))
            emptyCfgProto()
        }

        val pirCfg = ic.convertCFG(cfgProto)
        return PIRFunctionImpl(
            name = "__module_init__",
            qualifiedName = "$moduleName.__module_init__",
            parameters = emptyList(),
            returnType = PIRAnyType,
            cfg = pirCfg,
            decorators = emptyList(),
            isAsync = false,
            isGenerator = false,
            isStaticMethod = false,
            isClassMethod = false,
            isProperty = false,
            closureVars = emptyList(),
            enclosingClass = null,
            module = module,
        )
    }

    // ─── Nested function extraction ─────────────────────

    /**
     * Extract a nested FuncDef from within a function body, building it as a
     * module-level function (same pattern as lambdas).
     *
     * Returns pair of (uniqueName, qualifiedName) for the extracted function.
     * The caller can use the qualified name to create a GlobalRef.
     */
    fun extractNestedFunction(funcDef: MypyFuncDefProto, enclosingQualifiedName: String): Pair<String, String> {
        val idx = nestedFuncCounter++
        // Unique name avoids collisions between nested functions with same name in different outers
        val uniqueName = "${funcDef.name}\$local$idx"
        val qualifiedName = "$enclosingQualifiedName.${funcDef.name}"

        // Build the nested function's CFG using a fresh CfgBuilder
        val nestedScope = ScopeStack()
        val nestedCfgBuilder = CfgBuilder(nestedScope, this, currentFunctionQualifiedName = qualifiedName)
        val cfgProto = try {
            if (funcDef.hasBody()) {
                nestedCfgBuilder.buildFunctionCfg(funcDef.body)
            } else {
                emptyCfgProto()
            }
        } catch (e: Exception) {
            diagnostics.add(PIRDiagnostic(
                org.opentaint.ir.api.python.PIRDiagnosticSeverity.ERROR,
                "Failed to build CFG for nested $qualifiedName: ${e.javaClass.simpleName}: ${e.message}",
                funcDef.name,
                e.javaClass.simpleName,
            ))
            emptyCfgProto()
        }

        val funcBuilder = PIRFunctionProto.newBuilder()
            .setName(uniqueName)
            .setQualifiedName(qualifiedName)
            .setCfg(cfgProto)
            .setIsAsync(funcDef.isAsync)
            .setIsGenerator(funcDef.isGenerator)

        // Parameters
        for (arg in funcDef.argumentsList) {
            val kind = when (arg.kind) {
                2 -> ParameterKind.VAR_POSITIONAL
                4 -> ParameterKind.VAR_KEYWORD
                3, 5 -> ParameterKind.KEYWORD_ONLY
                else -> ParameterKind.POSITIONAL_OR_KEYWORD
            }
            val paramBuilder = PIRParameterProto.newBuilder()
                .setName(arg.name)
                .setKind(kind)
                .setHasDefault(arg.hasDefault)
            if (arg.hasType()) paramBuilder.setType(arg.type)
            // Carry default value through for nested functions
            if (arg.hasDefault && arg.hasDefaultValue()) {
                val defaultVal = evalConstExpr(arg.defaultValue)
                if (defaultVal != null) paramBuilder.setDefaultValue(defaultVal)
            }
            funcBuilder.addParameters(paramBuilder)
        }

        // Return type
        if (funcDef.hasReturnType()) {
            funcBuilder.setReturnType(funcDef.returnType)
        }

        // Compute closure vars from body analysis (migrated from Python serializer)
        val paramNames = funcDef.argumentsList.map { it.name }
        val closureVars = if (funcDef.hasBody()) {
            FreeVarAnalyzer.collectFreeVars(funcDef.body, paramNames)
        } else emptyList()
        for (cv in closureVars) {
            funcBuilder.addClosureVars(cv)
        }

        nestedFunctions.add(funcBuilder.build())
        return Pair(uniqueName, qualifiedName)
    }

    // ─── Helpers ─────────────────────────────────────────

    private fun convertLambdaFunction(proto: PIRFunctionProto, module: PIRModule): PIRFunction {
        val params = proto.parametersList.mapIndexed { idx, p ->
            val kind = when (p.kind) {
                ParameterKind.POSITIONAL_ONLY -> PIRParameterKind.POSITIONAL_ONLY
                ParameterKind.POSITIONAL_OR_KEYWORD -> PIRParameterKind.POSITIONAL_OR_KEYWORD
                ParameterKind.VAR_POSITIONAL -> PIRParameterKind.VAR_POSITIONAL
                ParameterKind.KEYWORD_ONLY -> PIRParameterKind.KEYWORD_ONLY
                ParameterKind.VAR_KEYWORD -> PIRParameterKind.VAR_KEYWORD
                else -> PIRParameterKind.POSITIONAL_OR_KEYWORD
            }
            val type = if (p.hasType()) typeConverter.convert(p.type) else PIRAnyType
            val defVal = if (p.hasDefaultValue()) vc.convert(p.defaultValue) else null
            PIRParameterImpl(p.name, type, kind, p.hasDefault, defVal, idx)
        }
        val returnType = if (proto.hasReturnType()) typeConverter.convert(proto.returnType) else PIRAnyType
        val cfg = ic.convertCFG(proto.cfg)

        return PIRFunctionImpl(
            name = proto.name,
            qualifiedName = proto.qualifiedName,
            parameters = params,
            returnType = returnType,
            cfg = cfg,
            decorators = emptyList(),
            isAsync = proto.isAsync,
            isGenerator = proto.isGenerator,
            isStaticMethod = false,
            isClassMethod = false,
            isProperty = false,
            closureVars = proto.closureVarsList,
            enclosingClass = null,
            module = module,
        )
    }

    /** Try to evaluate a MypyExprProto to a constant PIRValueProto. Returns null for complex expressions. */
    private fun evalConstExpr(expr: MypyExprProto): PIRValueProto? {
        fun constVal(builder: PIRConstProto.Builder): PIRValueProto =
            PIRValueProto.newBuilder().setConstVal(builder).build()
        return when (expr.kindCase) {
            MypyExprProto.KindCase.INT_EXPR ->
                constVal(PIRConstProto.newBuilder().setIntValue(expr.intExpr.value))
            MypyExprProto.KindCase.FLOAT_EXPR ->
                constVal(PIRConstProto.newBuilder().setFloatValue(expr.floatExpr.value))
            MypyExprProto.KindCase.STR_EXPR ->
                constVal(PIRConstProto.newBuilder().setStringValue(expr.strExpr.value))
            MypyExprProto.KindCase.BYTES_EXPR ->
                constVal(PIRConstProto.newBuilder().setStringValue(expr.bytesExpr.value.toStringUtf8()))
            MypyExprProto.KindCase.NAME_EXPR -> {
                val name = expr.nameExpr.name
                when (name) {
                    "True" -> constVal(PIRConstProto.newBuilder().setBoolValue(true))
                    "False" -> constVal(PIRConstProto.newBuilder().setBoolValue(false))
                    "None" -> constVal(PIRConstProto.newBuilder().setNoneValue(true))
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun emptyCfgProto(): PIRCFGProto {
        return PIRCFGProto.newBuilder()
            .addBlocks(
                PIRBasicBlockProto.newBuilder()
                    .setLabel(0)
                    .addInstructions(
                        PIRInstructionProto.newBuilder()
                            .setReturnInst(PIRReturnProto.getDefaultInstance())
                    )
            )
            .setEntryBlock(0)
            .addExitBlocks(0)
            .build()
    }
}
