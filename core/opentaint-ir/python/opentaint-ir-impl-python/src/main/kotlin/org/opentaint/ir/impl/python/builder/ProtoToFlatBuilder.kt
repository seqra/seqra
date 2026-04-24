package org.opentaint.ir.impl.python.builder

import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.api.python.PIRDiagnosticSeverity
import org.opentaint.ir.impl.python.proto.*

/**
 * Proto → FlatModuleIR lowering. The Flat IR is proto-independent;
 * anything downstream (FlatToPirConverter, transforms, analyzers)
 * reads only FlatModuleIR.
 */
class ProtoToFlatBuilder(private val astModule: MypyModuleProto) {
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

    fun build(): FlatModuleIR {
        for (error in astModule.errorsList) {
            diagnostics.add(PIRDiagnostic(
                PIRDiagnosticSeverity.ERROR,
                error,
                moduleName,
                "MypyBuildError",
            ))
        }

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
        // Snapshot after buildFlatModuleInit(): module-level lambdas reached
        // through module-init CFG construction must appear in pendingLambdas.
        val allFlatFunctions = topLevelFlatFunctions + pendingLambdas + pendingNested + flatModuleInit

        return FlatModuleIR(
            moduleName = moduleName,
            path = astModule.path,
            functions = allFlatFunctions,
            classes = flatClasses,
            fields = flatFields,
            imports = astModule.importsList,
            diagnostics = diagnostics.toList(),
        )
    }

    // ─── Class building ──────────────────────────────────

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
                PIRDiagnosticSeverity.ERROR,
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
        expr.hasStrExpr() -> "\"${escapeStrLiteral(expr.strExpr.value)}\""
        expr.hasFloatExpr() -> expr.floatExpr.value.toString()
        expr.hasBytesExpr() -> "b\"${escapeStrLiteral(expr.bytesExpr.value.toStringUtf8())}\""
        expr.hasComplexExpr() -> "${expr.complexExpr.real}+${expr.complexExpr.imag}j"
        expr.hasEllipsisExpr() -> "..."
        // True / False / None are NameExprs in mypy's AST; their names render verbatim.
        expr.hasNameExpr() -> expr.nameExpr.name
        expr.hasMemberExpr() -> memberExprDottedPath(expr.memberExpr)
        else -> "<expr>"
    }

    private fun escapeStrLiteral(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

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
                PIRDiagnosticSeverity.ERROR,
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
    fun extractNestedFunction(
        funcDef: MypyFuncDefProto,
        decoratorExprs: List<MypyExprProto>,
        enclosingQualifiedName: String,
    ): Pair<String, String> {
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
                PIRDiagnosticSeverity.ERROR,
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
            decorators = if (decoratorExprs.isNotEmpty()) {
                decoratorExprs.map { flatDecoratorFromExpr(it) }
            } else {
                // Defensive fallback: the serializer does not populate MypyFuncDefProto.decorators
                // today (decorators only reach Kotlin via Decorator.original_decorators), so this
                // branch is unreached. Kept to avoid coupling to serializer internals.
                funcDef.decoratorsList.map { FlatDecorator(it.name, it.qualifiedName, it.argumentsList) }
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
}
