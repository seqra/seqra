package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.impl.python.flat.*
import org.opentaint.ir.impl.python.protoToFlat.cfg.CfgBuild
import org.opentaint.ir.impl.python.proto.MypyAssignmentStmtProto
import org.opentaint.ir.impl.python.proto.MypyClassDefProto
import org.opentaint.ir.impl.python.proto.MypyDefinitionProto
import org.opentaint.ir.impl.python.proto.MypyModuleProto

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

        val classes = mutableListOf<FlatClass>()
        val topLevelFunctions = mutableListOf<FlatFunctionIR>()
        val moduleFields = mutableListOf<FlatModuleField>()
        val moduleInitAssignments = mutableListOf<MypyAssignmentStmtProto>()

        for (def in astModule.defsList) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.CLASS_DEF ->
                    classes.add(lowerClass(context, def.classDef, enclosingQualifier = null))
                MypyDefinitionProto.KindCase.FUNC_DEF,
                MypyDefinitionProto.KindCase.DECORATOR ->
                    topLevelFunctions.add(lowerFuncOrDecorator(context, def, enclosingClassQualifiedName = null))
                MypyDefinitionProto.KindCase.ASSIGNMENT -> {
                    moduleFields.addAll(extractFields(def.assignment) { name, type ->
                        FlatModuleField(name = name, type = type, hasInitializer = true)
                    })
                    moduleInitAssignments.add(def.assignment)
                }
                else -> {}
            }
        }

        val moduleInit = lowerModuleInit(context, moduleInitAssignments)

        // Read after module-init lowering: any lambdas/nested defs registered
        // while building module-init's CFG must be included.
        val syntheticFunctions = context.registeredFunctions

        return FlatModuleIR(
            moduleName = astModule.name,
            path = astModule.path,
            functions = topLevelFunctions + syntheticFunctions,
            moduleInit = moduleInit,
            classes = classes,
            fields = moduleFields,
            imports = astModule.importsList,
            diagnostics = context.diagnostics,
        )
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
                MypyDefinitionProto.KindCase.ASSIGNMENT ->
                    // TODO: pir_server doesn't carry an `is_class_var` flag; once it does,
                    //  thread it through MypyAssignmentStmtProto and read it here.
                    classFields.addAll(extractFields(def.assignment) { name, type ->
                        FlatClassField(name = name, type = type, isClassVar = false, hasInitializer = true)
                    })
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
        assignments: List<MypyAssignmentStmtProto>,
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
            closureVars = emptyList(),
            decorators = emptyList(),
        )
    }
}
