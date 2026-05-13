package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.impl.python.flat.*
import org.opentaint.ir.impl.python.protoToFlat.cfg.CfgBuild
import org.opentaint.ir.impl.python.proto.MypyAssignmentStmtProto
import org.opentaint.ir.impl.python.proto.MypyClassDefProto
import org.opentaint.ir.impl.python.proto.MypyDefinitionProto
import org.opentaint.ir.impl.python.proto.MypyModuleProto
import org.opentaint.ir.impl.python.proto.MypyStmtProto

/**
 * Module-level lowering: walks the top-level definitions of a [MypyModuleProto]
 * and assembles the [FlatModuleIR]. All inner-scope work (lambdas, nested
 * defs, diagnostics) flows through a [ModuleContext] sink.
 */
internal object ModuleLowering {

    private val ENUM_BASE_CLASSES = setOf(
        "enum.Enum", "enum.IntEnum", "enum.Flag", "enum.IntFlag",
    )

    fun lower(astModule: MypyModuleProto): FlatModuleIR {
        val context = ModuleContext(moduleName = astModule.name)

        for (error in astModule.errorsList) {
            context.reportError(error, astModule.name, "MypyBuildError")
        }

        // Pass 1: partition the top-level defs.
        //
        // `moduleInitStmts` carries every module-level statement that needs
        // to participate in module-init's CFG, in source order:
        //   - `Import` / `ImportFrom`           — update [ModuleContext.imports]
        //   - `Assignment`                      — emit FlatInst into module-init
        // `defQueue` holds the class and function definitions, lowered AFTER
        // module-init so they see the populated import map.
        //
        // Limitation: imports nested in module-level control flow
        // (`try: import …`, `if cond: import …`) never reach Kotlin —
        // `_serialize_definitions` in ast_serializer.py drops module-level
        // `If` / `Try` / `With` statements entirely today. Such imports still
        // mis-classify as `FlatGlobalRef("scope.x")` when mypy can't resolve
        // them. Pre-existing limitation, called out for future maintainers.
        val moduleFields = mutableListOf<FlatModuleField>()
        val moduleInitStmts = mutableListOf<MypyStmtProto>()
        val defQueue = mutableListOf<MypyDefinitionProto>()

        for (def in astModule.defsList) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.CLASS_DEF,
                MypyDefinitionProto.KindCase.FUNC_DEF,
                MypyDefinitionProto.KindCase.DECORATOR -> defQueue.add(def)
                MypyDefinitionProto.KindCase.ASSIGNMENT -> {
                    val stmt = def.assignment
                    if (stmt.hasAssignment()) {
                        moduleFields.addAll(extractFields(stmt.assignment) { name, type ->
                            FlatModuleField(name = name, type = type, hasInitializer = true)
                        })
                    }
                    moduleInitStmts.add(stmt)
                }
                else -> {}
            }
        }

        // Pass 2: build module-init first. Its two-pass walk (inside
        // [CfgBuild.buildModuleInitCfg]) records all module-level imports
        // BEFORE emitting any FlatInst, so a lambda RHS in a module-level
        // assignment that references a textually-later import still sees
        // the import.
        val moduleInit = lowerModuleInit(context, moduleInitStmts)

        // Pass 3: lower classes and top-level functions. [ModuleContext.imports]
        // is now fully populated, so any function/method body or lambda within
        // these defs reads the complete module-level import map.
        val classes = mutableListOf<FlatClass>()
        val topLevelFunctions = mutableListOf<FlatFunctionIR>()
        for (def in defQueue) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.CLASS_DEF ->
                    classes.add(lowerClass(context, def.classDef, enclosingQualifier = null))
                MypyDefinitionProto.KindCase.FUNC_DEF,
                MypyDefinitionProto.KindCase.DECORATOR ->
                    topLevelFunctions.add(lowerFuncOrDecorator(context, def, enclosingClassQualifiedName = null))
                else -> error("defQueue must contain only class/func/decorator defs; got ${def.kindCase}")
            }
        }

        // Read after all lowering: any lambdas/nested defs registered along
        // the way (in module-init, class methods, function bodies) must be
        // included in the module's function list.
        val syntheticFunctions = context.registeredFunctions

        val rawModule = FlatModuleIR(
            moduleName = astModule.name,
            path = astModule.path,
            functions = topLevelFunctions + syntheticFunctions,
            moduleInit = moduleInit,
            classes = classes,
            fields = moduleFields,
            imports = astModule.importsList,
            diagnostics = context.diagnostics,
        )

        // Mypy emits resolvedCallee in its lexical form (`m.outer.inner`),
        // but the lifter uses `$`-flat encoding for nested-def qualified
        // names (`m.outer$inner`). Normalize so every consumer of
        // resolvedCallee sees the same canonical form as
        // FlatFunctionIR.qualifiedName.
        // TODO maybe use it as a transform?
        return ResolvedCalleeNormalizer.normalize(rawModule)
    }

    // ─── Class building ──────────────────────────────

    /**
     * @param enclosingQualifier full module-qualified name of the immediately
     *   enclosing class (e.g. `"module.Outer"`), or null at top level. Used
     *   to chain qualified names through nested classes.
     */
    private fun lowerClass(
        context: ModuleContext,
        classDef: MypyClassDefProto,
        enclosingQualifier: String?,
    ): FlatClass {
        val qualifiedName = classDef.fullname.ifEmpty {
            "${enclosingQualifier ?: context.moduleName}.${classDef.name}"
        }

        val methods = mutableListOf<FlatFunctionIR>()
        val classFields = mutableListOf<FlatClassField>()
        val nestedClasses = mutableListOf<FlatClass>()

        for (def in classDef.bodyList) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.FUNC_DEF,
                MypyDefinitionProto.KindCase.DECORATOR ->
                    methods.add(lowerFuncOrDecorator(context, def, enclosingClassQualifiedName = qualifiedName))
                MypyDefinitionProto.KindCase.ASSIGNMENT -> {
                    // The `assignment` slot now also carries module-level
                    // `Import` / `ImportFrom` stmts (see comment on
                    // [_serialize_definitions] in ast_serializer.py). Inside a
                    // class body imports bind on the class namespace (which
                    // taint analysis doesn't track today) — skip non-assignment
                    // stmts so `extractFields` only ever sees actual assigns.
                    if (def.assignment.hasAssignment()) {
                        // TODO: pir_server doesn't carry an `is_class_var` flag; once it does,
                        //  thread it through MypyAssignmentStmtProto and read it here.
                        classFields.addAll(extractFields(def.assignment.assignment) { name, type ->
                            FlatClassField(name = name, type = type, isClassVar = false, hasInitializer = true)
                        })
                    }
                }
                MypyDefinitionProto.KindCase.CLASS_DEF ->
                    nestedClasses.add(lowerClass(context, def.classDef, enclosingQualifier = qualifiedName))
                else -> {}
            }
        }

        val decorators = DecoratorLowering.fromClassDef(classDef)
        val isEnum = classDef.baseClassesList.any { it in ENUM_BASE_CLASSES }
        val isDataclass = classDef.isDataclass || decorators.any { it.name == "dataclass" }

        return FlatClass(
            name = classDef.name,
            qualifiedName = qualifiedName,
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

    /**
     * Common shape for the FUNC_DEF / DECORATOR arms in both `lower` and
     * `lowerClass`. The difference is the decorator source.
     *
     * @param enclosingClassQualifiedName fully-qualified name of the enclosing
     *   class (e.g. `"module.Outer.Inner"`), or null for free functions.
     */
    private fun lowerFuncOrDecorator(
        context: ModuleContext,
        def: MypyDefinitionProto,
        enclosingClassQualifiedName: String?,
    ): FlatFunctionIR = when (def.kindCase) {
        MypyDefinitionProto.KindCase.FUNC_DEF -> FunctionLowering.lowerTopLevel(
            module = context,
            funcDef = def.funcDef,
            decorators = DecoratorLowering.fromFuncDef(def.funcDef),
            enclosingClassQualifiedName = enclosingClassQualifiedName,
        )
        MypyDefinitionProto.KindCase.DECORATOR -> FunctionLowering.lowerTopLevel(
            module = context,
            funcDef = def.decorator.func,
            decorators = DecoratorLowering.fromDecoratorDef(def.decorator),
            enclosingClassQualifiedName = enclosingClassQualifiedName,
        )
        else -> error("lowerFuncOrDecorator: unexpected kind ${def.kindCase}")
    }

    // ─── Field extraction ────────────────────────────

    private inline fun <T> extractFields(
        assignment: MypyAssignmentStmtProto,
        factory: (name: String, type: FlatType) -> T,
    ): List<T> = assignment.lvaluesList.mapNotNull { lvalue ->
        if (!lvalue.hasNameExpr()) return@mapNotNull null
        factory(
            lvalue.nameExpr.name,
            if (lvalue.hasExprType()) TypeLowering.convertType(lvalue.exprType) else FlatAnyType,
        )
    }

    // ─── Module init ─────────────────────────────────

    private fun lowerModuleInit(
        context: ModuleContext,
        assignments: List<MypyStmtProto>,
    ): FlatFunctionIR {
        val cfg = CfgBuild.buildModuleInitCfg(context, assignments)
        return FlatFunctionIR(
            name = "__module_init__",
            qualifiedName = "${context.moduleName}.__module_init__",
            parentQualifiedName = null,
            kind = FlatFunctionKind.MODULE_INIT,
            cfg = cfg,
            parameters = emptyList(),
            returnType = FlatAnyType,
            isAsync = false,
            isGenerator = false,
            decorators = emptyList(),
        )
    }
}
