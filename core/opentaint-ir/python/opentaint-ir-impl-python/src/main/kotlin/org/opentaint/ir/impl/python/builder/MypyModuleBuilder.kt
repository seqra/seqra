package org.opentaint.ir.impl.python.builder

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.*
import org.opentaint.ir.impl.python.converter.InstructionConverter
import org.opentaint.ir.impl.python.converter.TypeConverter
import org.opentaint.ir.impl.python.proto.*

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
    private val pendingLambdas = mutableListOf<FlatFunctionIR>()
    var nestedFuncCounter = 0
    private val pendingNested = mutableListOf<FlatFunctionIR>()
    private val diagnostics = mutableListOf<PIRDiagnostic>()
    private val typeConverter = TypeConverter()
    private val ic = InstructionConverter()

    fun build(): PIRModule {
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

        val moduleInit = buildModuleInit(dummyModule)

        val lambdaFuncList = pendingLambdas.map { convertFlatFunction(it, dummyModule) }
        val nestedFuncList = pendingNested.map { convertFlatFunction(it, dummyModule) }
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

    /**
     * Wires PIRLocation onto every instruction in the function's CFG.
     * Called after PIRFunctionImpl construction, similar to how enclosingClass is wired.
     */
    private fun wireInstructionLocations(function: PIRFunction) {
        function.instList.forEach { inst ->
            val (line, col) = ic.getInstPosition(inst)
            val index = ic.getInstIndex(inst)
            inst.location = PIRLocationImpl(function, index, line, col)
        }
    }

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
    ): PIRFunction = convertFlatFunction(
        buildFlatFunction(
            funcDef,
            buildDecorators(funcDef),
            enclosingClass,
        ),
        module,
    )

    private fun buildDecoratedFunction(
        decorator: MypyDecoratorDefProto,
        enclosingClass: String?,
        module: PIRModule,
    ): PIRFunction = convertFlatFunction(
        buildFlatFunction(
            decorator.func,
            buildDecorators(decorator),
            enclosingClass,
        ),
        module,
    )

    private fun buildDecorators(
        decorator: MypyDecoratorDefProto
    ): List<FlatDecorator> = decorator.originalDecoratorsList.map { flatDecoratorFromExpr(it) }

    private fun buildDecorators(
        funcDef: MypyFuncDefProto
    ): List<FlatDecorator> = funcDef.decoratorsList.map { FlatDecorator(it.name, it.qualifiedName, it.argumentsList) }

    private fun buildFlatFunction(
        funcDef: MypyFuncDefProto,
        decorators: List<FlatDecorator>,
        enclosingClass: String?,
    ): FlatFunctionIR {
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

        val cfgScope = ScopeStack()
        val cfgBuilder = CfgBuilder(cfgScope, this, currentFunctionQualifiedName = qualifiedName)
        val flatCfg = try {
            if (funcDef.hasBody()) {
                cfgBuilder.buildFunctionCfg(funcDef.body)
            } else {
                FlatCFG.EMPTY
            }
        } catch (e: Exception) {
            diagnostics.add(PIRDiagnostic(
                org.opentaint.ir.api.python.PIRDiagnosticSeverity.ERROR,
                "Failed to build CFG for $qualifiedName: ${e.javaClass.simpleName}: ${e.message}",
                funcDef.name,
                e.javaClass.simpleName,
            ))
            FlatCFG.EMPTY
        }

        return FlatFunctionIR(
            name = funcDef.name,
            qualifiedName = qualifiedName,
            parentQualifiedName = null,
            kind = if (enclosingClass != null) FlatFunctionKind.METHOD else FlatFunctionKind.TOP_LEVEL,
            cfg = flatCfg,
            parameters = convertParameters(funcDef.argumentsList),
            returnType = if (funcDef.hasReturnType()) protoTypeToFlat(funcDef.returnType) else FlatAnyType,
            isAsync = funcDef.isAsync,
            isGenerator = funcDef.isGenerator,
            closureVars = funcDef.closureVarsList,
            decorators = decorators,
        )
    }

    private fun flatDecoratorFromExpr(expr: MypyExprProto): FlatDecorator = when {
        expr.hasNameExpr() -> {
            val ne = expr.nameExpr
            FlatDecorator(
                name = ne.name,
                qualifiedName = ne.fullname.ifEmpty { ne.name },
                arguments = emptyList(),
            )
        }
        expr.hasMemberExpr() -> {
            val me = expr.memberExpr
            FlatDecorator(
                name = me.name,
                qualifiedName = me.fullname.ifEmpty { memberExprDottedPath(me) },
                arguments = emptyList(),
            )
        }
        expr.hasCallExpr() -> {
            val ce = expr.callExpr
            val callee = flatDecoratorFromExpr(ce.callee)
            FlatDecorator(
                name = callee.name,
                qualifiedName = callee.qualifiedName,
                arguments = ce.argsList.map { exprRepr(it.expr) },
            )
        }
        else -> FlatDecorator("<unknown>", "<unknown>", emptyList())
    }

    private fun memberExprDottedPath(me: MypyMemberExprProto): String {
        val prefix = when {
            me.expr.hasNameExpr() -> me.expr.nameExpr.fullname.ifEmpty { me.expr.nameExpr.name }
            me.expr.hasMemberExpr() -> memberExprDottedPath(me.expr.memberExpr)
            else -> "<expr>"
        }
        return "$prefix.${me.name}"
    }

    private fun exprRepr(expr: MypyExprProto): String = when {
        expr.hasIntExpr() -> expr.intExpr.value.toString()
        expr.hasStrExpr() -> "\"${expr.strExpr.value}\""
        expr.hasFloatExpr() -> expr.floatExpr.value.toString()
        expr.hasBytesExpr() -> "b\"${expr.bytesExpr.value.toStringUtf8()}\""
        expr.hasComplexExpr() -> "${expr.complexExpr.real}+${expr.complexExpr.imag}j"
        expr.hasEllipsisExpr() -> "..."
        expr.hasNameExpr() -> expr.nameExpr.name
        expr.hasMemberExpr() -> memberExprDottedPath(expr.memberExpr)
        else -> "<expr>"
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

        val flatCfg = try {
            cfgBuilder.buildModuleInitCfg(moduleStmts)
        } catch (e: Exception) {
            diagnostics.add(PIRDiagnostic(
                org.opentaint.ir.api.python.PIRDiagnosticSeverity.ERROR,
                "Failed to build module_init CFG for $moduleName: ${e.javaClass.simpleName}: ${e.message}",
                "__module_init__",
                e.javaClass.simpleName,
            ))
            FlatCFG.EMPTY
        }

        val pirCfg = ic.convertCFG(flatCfg)
        val function = PIRFunctionImpl(
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
        wireInstructionLocations(function)
        return function
    }

    // ─── Nested function / lambda extraction ────────────

    /**
     * Extract a nested FuncDef from within a function body, building it as a
     * module-level function (same pattern as lambdas).
     *
     * Returns pair of (uniqueName, qualifiedName) for the extracted function.
     * The caller can use the qualified name to create a GlobalRef.
     */
    fun extractNestedFunction(funcDef: MypyFuncDefProto, enclosingQualifiedName: String): Pair<String, String> {
        val idx = nestedFuncCounter++
        val uniqueName = "${funcDef.name}\$local$idx"
        val qualifiedName = "$enclosingQualifiedName.${funcDef.name}"

        val nestedScope = ScopeStack()
        val nestedCfgBuilder = CfgBuilder(nestedScope, this, currentFunctionQualifiedName = qualifiedName)
        val flatCfg = try {
            if (funcDef.hasBody()) {
                nestedCfgBuilder.buildFunctionCfg(funcDef.body)
            } else {
                FlatCFG.EMPTY
            }
        } catch (e: Exception) {
            diagnostics.add(PIRDiagnostic(
                org.opentaint.ir.api.python.PIRDiagnosticSeverity.ERROR,
                "Failed to build CFG for nested $qualifiedName: ${e.javaClass.simpleName}: ${e.message}",
                funcDef.name,
                e.javaClass.simpleName,
            ))
            FlatCFG.EMPTY
        }

        val paramNames = funcDef.argumentsList.map { it.name }
        val closureVars = if (funcDef.hasBody()) {
            FreeVarAnalyzer.collectFreeVars(funcDef.body, paramNames)
        } else emptyList()

        pendingNested.add(FlatFunctionIR(
            name = uniqueName,
            qualifiedName = qualifiedName,
            parentQualifiedName = enclosingQualifiedName,
            kind = FlatFunctionKind.NESTED_DEF,
            cfg = flatCfg,
            parameters = convertParameters(funcDef.argumentsList),
            returnType = if (funcDef.hasReturnType()) protoTypeToFlat(funcDef.returnType) else FlatAnyType,
            isAsync = funcDef.isAsync,
            isGenerator = funcDef.isGenerator,
            closureVars = closureVars,
            decorators = funcDef.decoratorsList.map {
                FlatDecorator(it.name, it.qualifiedName, it.argumentsList)
            },
        ))

        return Pair(uniqueName, qualifiedName)
    }

    fun addLambda(
        name: String,
        qualifiedName: String,
        parentQualifiedName: String?,
        cfg: FlatCFG,
        arguments: List<MypyArgumentProto>,
        returnType: PIRTypeProto?,
    ) {
        pendingLambdas.add(FlatFunctionIR(
            name = name,
            qualifiedName = qualifiedName,
            parentQualifiedName = parentQualifiedName,
            kind = FlatFunctionKind.LAMBDA,
            cfg = cfg,
            parameters = convertParameters(arguments),
            returnType = if (returnType != null) protoTypeToFlat(returnType) else FlatAnyType,
            isAsync = false,
            isGenerator = false,
            closureVars = emptyList(),
            decorators = emptyList(),
        ))
    }

    // ─── Helpers ─────────────────────────────────────────

    private fun convertFlatFunction(pending: FlatFunctionIR, module: PIRModule): PIRFunction {
        val params = pending.parameters.mapIndexed { idx, p ->
            PIRParameterImpl(
                p.name,
                ic.convertType(p.type),
                flatParamKindToPir(p.kind),
                p.hasDefault,
                p.defaultValue?.let { ic.convertConstValue(it) },
                idx,
            )
        }
        val returnType = ic.convertType(pending.returnType)
        val cfg = ic.convertCFG(pending.cfg)

        val function = PIRFunctionImpl(
            name = pending.name,
            qualifiedName = pending.qualifiedName,
            parameters = params,
            returnType = returnType,
            cfg = cfg,
            decorators = pending.decorators.map {
                PIRDecoratorImpl(it.name, it.qualifiedName, it.arguments)
            },
            isAsync = pending.isAsync,
            isGenerator = pending.isGenerator,
            isStaticMethod = pending.isStaticMethod,
            isClassMethod = pending.isClassMethod,
            isProperty = pending.isProperty,
            closureVars = pending.closureVars,
            enclosingClass = null,
            module = module,
        )
        wireInstructionLocations(function)
        return function
    }

    // ─── Proto → Flat IR conversion ──────────────────────

    private fun convertParameters(args: List<MypyArgumentProto>): List<FlatParameter> =
        args.map { arg ->
            FlatParameter(
                name = arg.name,
                type = if (arg.hasType()) protoTypeToFlat(arg.type) else FlatAnyType,
                kind = protoParamKindToFlat(arg.kind),
                hasDefault = arg.hasDefault,
                defaultValue = if (arg.hasDefault && arg.hasDefaultValue()) evalFlatConst(arg.defaultValue) else null,
            )
        }

    private fun protoParamKindToFlat(kind: Int): FlatParamKind = when (kind) {
        0, 1 -> FlatParamKind.POSITIONAL_OR_KEYWORD
        2 -> FlatParamKind.VAR_POSITIONAL
        4 -> FlatParamKind.VAR_KEYWORD
        3, 5 -> FlatParamKind.KEYWORD_ONLY
        else -> FlatParamKind.POSITIONAL_OR_KEYWORD
    }

    private fun protoTypeToFlat(proto: PIRTypeProto): FlatType = when (proto.kindCase) {
        PIRTypeProto.KindCase.CLASS_TYPE -> {
            val ct = proto.classType
            FlatClassType(
                qualifiedName = ct.qualifiedName,
                typeArgs = ct.typeArgsList.map { protoTypeToFlat(it) },
                isOptional = ct.isOptional,
            )
        }
        PIRTypeProto.KindCase.FUNCTION_TYPE -> FlatFunctionType(
            paramTypes = proto.functionType.paramTypesList.map { protoTypeToFlat(it) },
            returnType = protoTypeToFlat(proto.functionType.returnType),
        )
        PIRTypeProto.KindCase.UNION_TYPE -> FlatUnionType(
            members = proto.unionType.membersList.map { protoTypeToFlat(it) },
        )
        PIRTypeProto.KindCase.TUPLE_TYPE -> FlatTupleType(
            elementTypes = proto.tupleType.elementTypesList.map { protoTypeToFlat(it) },
            isVarLength = proto.tupleType.isVarLength,
        )
        PIRTypeProto.KindCase.LITERAL_TYPE -> FlatLiteralType(
            value = proto.literalType.value,
            baseType = protoTypeToFlat(proto.literalType.baseType),
        )
        PIRTypeProto.KindCase.ANY_TYPE -> FlatAnyType
        PIRTypeProto.KindCase.NEVER_TYPE -> FlatNeverType
        PIRTypeProto.KindCase.NONE_TYPE -> FlatNoneType
        PIRTypeProto.KindCase.TYPE_VAR_TYPE -> FlatTypeVarType(
            name = proto.typeVarType.name,
            bounds = proto.typeVarType.boundsList.map { protoTypeToFlat(it) },
        )
        PIRTypeProto.KindCase.KIND_NOT_SET, null -> FlatAnyType
    }

    private fun evalFlatConst(expr: MypyExprProto): FlatConst? = when (expr.kindCase) {
        MypyExprProto.KindCase.INT_EXPR -> FlatIntConst(expr.intExpr.value)
        MypyExprProto.KindCase.FLOAT_EXPR -> FlatFloatConst(expr.floatExpr.value)
        MypyExprProto.KindCase.STR_EXPR -> FlatStrConst(expr.strExpr.value)
        MypyExprProto.KindCase.BYTES_EXPR -> FlatBytesConst(expr.bytesExpr.value.toByteArray())
        MypyExprProto.KindCase.COMPLEX_EXPR -> FlatComplexConst(expr.complexExpr.real, expr.complexExpr.imag)
        MypyExprProto.KindCase.ELLIPSIS_EXPR -> FlatEllipsisConst
        MypyExprProto.KindCase.NAME_EXPR -> when (expr.nameExpr.name) {
            "True" -> FlatBoolConst(true)
            "False" -> FlatBoolConst(false)
            "None" -> FlatNoneConst
            else -> null
        }
        else -> null
    }

    // ─── Flat IR → PIR conversion ────────────────────────

    private fun flatParamKindToPir(kind: FlatParamKind): PIRParameterKind = when (kind) {
        FlatParamKind.POSITIONAL_OR_KEYWORD -> PIRParameterKind.POSITIONAL_OR_KEYWORD
        FlatParamKind.VAR_POSITIONAL -> PIRParameterKind.VAR_POSITIONAL
        FlatParamKind.VAR_KEYWORD -> PIRParameterKind.VAR_KEYWORD
        FlatParamKind.KEYWORD_ONLY -> PIRParameterKind.KEYWORD_ONLY
    }
}
