package org.opentaint.ir.impl.python.builder

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.*
import org.opentaint.ir.impl.python.converter.InstructionConverter
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

        val flatClasses = mutableListOf<FlatClass>()
        val topLevelFlatFunctions = mutableListOf<FlatFunctionIR>()
        val flatFields = mutableListOf<FlatModuleField>()

        for (def in astModule.defsList) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.CLASS_DEF -> {
                    flatClasses.add(buildFlatClass(def.classDef))
                }
                MypyDefinitionProto.KindCase.FUNC_DEF -> {
                    topLevelFlatFunctions.add(buildFlatFunction(def.funcDef, buildDecorators(def.funcDef), null))
                }
                MypyDefinitionProto.KindCase.DECORATOR -> {
                    topLevelFlatFunctions.add(buildFlatFunction(def.decorator.func, buildDecorators(def.decorator), null))
                }
                MypyDefinitionProto.KindCase.ASSIGNMENT -> {
                    for (lvalue in def.assignment.lvaluesList) {
                        if (lvalue.hasNameExpr()) {
                            val type = if (lvalue.hasExprType()) {
                                protoTypeToFlat(lvalue.exprType)
                            } else FlatAnyType
                            flatFields.add(FlatModuleField(lvalue.nameExpr.name, type, hasInitializer = true))
                        }
                    }
                }
                else -> {}
            }
        }

        val flatModuleInit = buildFlatModuleInit()
        val allFlatFunctions = topLevelFlatFunctions + pendingLambdas + pendingNested + flatModuleInit

        val flatModule = FlatModuleIR(
            moduleName = moduleName,
            path = astModule.path,
            functions = allFlatFunctions,
            classes = flatClasses,
            fields = flatFields,
            imports = astModule.importsList,
            diagnostics = diagnostics,
        )

        val pirFunctions = flatModule.functions
            .filter { it.kind != FlatFunctionKind.MODULE_INIT }
            .map { convertFlatFunction(it, dummyModule) }
        val pirModuleInit = convertFlatFunction(flatModule.moduleInit, dummyModule)
        val pirClasses = flatModule.classes.map { flatClassToPir(it, dummyModule) }
        val pirFields = flatModule.fields.map {
            PIRFieldImpl(it.name, ic.convertType(it.type), isClassVar = false, hasInitializer = it.hasInitializer)
        }

        return PIRModuleImpl(
            name = moduleName,
            path = astModule.path,
            classes = pirClasses,
            functions = pirFunctions,
            fields = pirFields,
            moduleInit = pirModuleInit,
            imports = flatModule.imports,
            classpath = classpath ?: dummyModule.classpath,
            diagnostics = flatModule.diagnostics,
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

    private fun buildFlatClass(classDef: MypyClassDefProto): FlatClass {
        val methods = mutableListOf<FlatFunctionIR>()
        val classFields = mutableListOf<FlatClassField>()
        val nestedClasses = mutableListOf<FlatClass>()

        for (def in classDef.bodyList) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.FUNC_DEF -> {
                    methods.add(buildFlatFunction(def.funcDef, buildDecorators(def.funcDef), classDef.name))
                }
                MypyDefinitionProto.KindCase.DECORATOR -> {
                    methods.add(buildFlatFunction(def.decorator.func, buildDecorators(def.decorator), classDef.name))
                }
                MypyDefinitionProto.KindCase.ASSIGNMENT -> {
                    for (lvalue in def.assignment.lvaluesList) {
                        if (lvalue.hasNameExpr()) {
                            val type = if (lvalue.hasExprType()) {
                                protoTypeToFlat(lvalue.exprType)
                            } else FlatAnyType
                            classFields.add(
                                FlatClassField(
                                    name = lvalue.nameExpr.name,
                                    type = type,
                                    isClassVar = false,
                                    hasInitializer = true,
                                )
                            )
                        }
                    }
                }
                MypyDefinitionProto.KindCase.CLASS_DEF -> {
                    nestedClasses.add(buildFlatClass(def.classDef))
                }
                else -> {}
            }
        }

        val decorators = classDef.decoratorsList.map {
            FlatDecorator(it.name, it.qualifiedName, it.argumentsList)
        }
        val isEnum = classDef.baseClassesList.any { it in ENUM_BASE_CLASSES }
        val isDataclass = classDef.isDataclass || decorators.any { it.name == "dataclass" }

        return FlatClass(
            name = classDef.name,
            qualifiedName = classDef.fullname.ifEmpty { "$moduleName.${classDef.name}" },
            baseClasses = classDef.baseClassesList,
            mro = classDef.mroList,
            methods = methods,
            fields = classFields,
            nestedClasses = nestedClasses,
            decorators = decorators,
            isAbstract = classDef.isAbstract,
            isDataclass = isDataclass,
            isEnum = isEnum,
        )
    }

    private fun flatClassToPir(flat: FlatClass, module: PIRModule): PIRClass {
        val methods = flat.methods.map { convertFlatFunction(it, module) as PIRFunctionImpl }
        // Identity-keyed so that two `FlatFunctionIR`s which happen to be structurally
        // equal (same name / qualifiedName / CFG / etc.) still map to distinct entries.
        val methodMap = java.util.IdentityHashMap<FlatFunctionIR, PIRFunctionImpl>()
        for (i in flat.methods.indices) {
            methodMap[flat.methods[i]] = methods[i]
        }
        val classFields = flat.fields.map {
            PIRFieldImpl(it.name, ic.convertType(it.type), it.isClassVar, it.hasInitializer)
        }
        val nestedClasses = flat.nestedClasses.map { flatClassToPir(it, module) }
        val properties = synthesizeProperties(flat.methods, methodMap)
        val decorators = flat.decorators.map {
            PIRDecoratorImpl(it.name, it.qualifiedName, it.arguments)
        }

        val cls = PIRClassImpl(
            name = flat.name,
            qualifiedName = flat.qualifiedName,
            baseClasses = flat.baseClasses,
            mro = flat.mro,
            methods = methods,
            fields = classFields,
            nestedClasses = nestedClasses,
            properties = properties,
            decorators = decorators,
            isAbstract = flat.isAbstract,
            isDataclass = flat.isDataclass,
            isEnum = flat.isEnum,
            module = module,
        )

        for (method in methods) {
            method.enclosingClass = cls
        }

        return cls
    }

    /**
     * Group property methods into PIRPropertyImpl objects.
     * OverloadedFuncDef serializes all items: getter, setter, deleter in order.
     * The getter is the one with isProperty=true. Setter/deleter have the same name
     * but may NOT have isProperty=true (mypy only sets it on the @property getter).
     * We detect properties by finding methods with  isProperty=true, then group all
     * methods sharing that name.
     */
    private fun synthesizeProperties(
        methods: List<FlatFunctionIR>,
        methodMap: Map<FlatFunctionIR, PIRFunctionImpl>,
    ): List<PIRProperty> {
        // `distinct()` preserves first-occurrence order so `PIRClass.properties`
        // reflects source order of getter definitions.
        val propertyGetterNames = methods.filter { it.isProperty }.map { it.name }.distinct()
        val result = mutableListOf<PIRProperty>()
        for (propName in propertyGetterNames) {
            val group = methods.filter { it.name == propName }
            val getter = group.firstOrNull { it.isProperty }
            val getterParamCount = getter?.parameters?.size ?: 0
            val setter = group.firstOrNull { !it.isProperty && it.parameters.size > getterParamCount }
                ?: group.firstOrNull { it !== getter && it.parameters.size > getterParamCount }
            val deleter = group.firstOrNull {
                it !== getter && it !== setter && it.parameters.size == getterParamCount
            }

            val getterPir = getter?.let { methodMap.getValue(it) }
            val setterPir = setter?.let { methodMap.getValue(it) }
            val deleterPir = deleter?.let { methodMap.getValue(it) }
            val propType = getterPir?.returnType ?: PIRAnyType
            result.add(PIRPropertyImpl(propName, propType, getterPir, setterPir, deleterPir))
        }
        return result
    }

    // ─── Function building ───────────────────────────────

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

    private fun buildFlatModuleInit(): FlatFunctionIR {
        val qualifiedName = "$moduleName.__module_init__"
        val cfgScope = ScopeStack()
        val cfgBuilder = CfgBuilder(cfgScope, this)

        val moduleStmts = astModule.defsList
            .filter { it.kindCase == MypyDefinitionProto.KindCase.ASSIGNMENT }
            .map { MypyStmtProto.newBuilder().setAssignment(it.assignment).build() }

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

        return FlatFunctionIR(
            name = "__module_init__",
            qualifiedName = qualifiedName,
            parentQualifiedName = null,
            kind = FlatFunctionKind.MODULE_INIT,
            cfg = flatCfg,
            parameters = emptyList(),
            returnType = FlatAnyType,
            isAsync = false,
            isGenerator = false,
            closureVars = emptyList(),
            decorators = emptyList(),
        )
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
