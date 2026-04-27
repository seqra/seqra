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

        for (def in astModule.defsList) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.CLASS_DEF ->
                    classes.add(lowerClass(context, def.classDef))
                MypyDefinitionProto.KindCase.FUNC_DEF ->
                    topLevelFunctions.add(FunctionLowering.lowerTopLevel(
                        module = context,
                        funcDef = def.funcDef,
                        decorators = DecoratorLowering.fromFuncDef(def.funcDef),
                        enclosingClass = null,
                    ))
                MypyDefinitionProto.KindCase.DECORATOR ->
                    topLevelFunctions.add(FunctionLowering.lowerTopLevel(
                        module = context,
                        funcDef = def.decorator.func,
                        decorators = DecoratorLowering.fromDecoratorDef(def.decorator),
                        enclosingClass = null,
                    ))
                MypyDefinitionProto.KindCase.ASSIGNMENT ->
                    moduleFields.addAll(extractModuleFields(def.assignment))
                else -> {}
            }
        }

        val moduleInit = lowerModuleInit(context, astModule)

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

    private fun lowerClass(context: ModuleContext, classDef: MypyClassDefProto): FlatClass {
        val methods = mutableListOf<FlatFunctionIR>()
        val classFields = mutableListOf<FlatClassField>()
        val nestedClasses = mutableListOf<FlatClass>()

        for (def in classDef.bodyList) {
            when (def.kindCase) {
                MypyDefinitionProto.KindCase.FUNC_DEF ->
                    methods.add(FunctionLowering.lowerTopLevel(
                        module = context,
                        funcDef = def.funcDef,
                        decorators = DecoratorLowering.fromFuncDef(def.funcDef),
                        enclosingClass = classDef.name,
                    ))
                MypyDefinitionProto.KindCase.DECORATOR ->
                    methods.add(FunctionLowering.lowerTopLevel(
                        module = context,
                        funcDef = def.decorator.func,
                        decorators = DecoratorLowering.fromDecoratorDef(def.decorator),
                        enclosingClass = classDef.name,
                    ))
                MypyDefinitionProto.KindCase.ASSIGNMENT ->
                    classFields.addAll(extractClassFields(def.assignment))
                MypyDefinitionProto.KindCase.CLASS_DEF ->
                    nestedClasses.add(lowerClass(context, def.classDef))
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
            qualifiedName = classDef.fullname.ifEmpty { "${context.moduleName}.${classDef.name}" },
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

    // ─── Field extraction ────────────────────────────

    private fun extractModuleFields(assignment: MypyAssignmentStmtProto): List<FlatModuleField> =
        assignment.lvaluesList.mapNotNull { lvalue ->
            if (!lvalue.hasNameExpr()) return@mapNotNull null
            FlatModuleField(
                name = lvalue.nameExpr.name,
                type = if (lvalue.hasExprType()) TypeLowering.convertType(lvalue.exprType) else FlatAnyType,
                hasInitializer = true,
            )
        }

    private fun extractClassFields(assignment: MypyAssignmentStmtProto): List<FlatClassField> =
        assignment.lvaluesList.mapNotNull { lvalue ->
            if (!lvalue.hasNameExpr()) return@mapNotNull null
            FlatClassField(
                name = lvalue.nameExpr.name,
                type = if (lvalue.hasExprType()) TypeLowering.convertType(lvalue.exprType) else FlatAnyType,
                isClassVar = false,
                hasInitializer = true,
            )
        }

    // ─── Module init ─────────────────────────────────

    private fun lowerModuleInit(context: ModuleContext, astModule: MypyModuleProto): FlatFunctionIR {
        val assignments = astModule.defsList
            .filter { it.kindCase == MypyDefinitionProto.KindCase.ASSIGNMENT }
            .map { it.assignment }
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
