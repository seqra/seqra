package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.*
import org.opentaint.ir.impl.python.builder.*

/**
 * Converts a [FlatModuleIR] into a [PIRModule]. Pure Flat-side; no proto reads.
 */
class FlatToPirConverter(
    private val flat: FlatModuleIR,
    private val classpath: PIRClasspath,
) {
    // ─── Module conversion ───

    fun convert(): PIRModule {
        val pirFunctions = flat.functions
            .filter { it.kind != FlatFunctionKind.MODULE_INIT }
            .map { convertFlatFunction(it) }
        val pirModuleInit = convertFlatFunction(flat.moduleInit)
        val pirClasses = flat.classes.map { flatClassToPir(it) }
        val pirFields = flat.fields.map {
            PIRFieldImpl(it.name, TypeConverter.convert(it.type), isClassVar = false, hasInitializer = it.hasInitializer)
        }

        return PIRModuleImpl(
            name = flat.moduleName,
            path = flat.path,
            classes = pirClasses,
            functions = pirFunctions,
            fields = pirFields,
            moduleInit = pirModuleInit,
            imports = flat.imports,
            classpath = classpath,
            diagnostics = flat.diagnostics,
        ).also { wireModuleBackRefs(it) }
    }

    /**
     * Wires `.module` on every function and class reachable from [module].
     * Covers: top-level functions (incl. extracted lambdas/nested defs),
     * moduleInit, top-level classes and — recursively — their methods and
     * nested classes. Class methods are only reachable via [PIRClass.methods],
     * not [PIRModule.functions].
     */
    private fun wireModuleBackRefs(module: PIRModuleImpl) {
        for (fn in module.functions) wireFunctionModule(fn, module)
        wireFunctionModule(module.moduleInit, module)
        for (cls in module.classes) wireClassModule(cls as PIRClassImpl, module)
    }

    private fun wireFunctionModule(fn: PIRFunction, module: PIRModule) {
        (fn as PIRFunctionImpl).module = module
    }

    private fun wireClassModule(cls: PIRClassImpl, module: PIRModule) {
        cls.module = module
        for (method in cls.methods) wireFunctionModule(method, module)
        for (nested in cls.nestedClasses) wireClassModule(nested as PIRClassImpl, module)
    }

    // ─── Class conversion ───

    private fun flatClassToPir(flat: FlatClass): PIRClass {
        val methods = flat.methods.map { convertFlatFunction(it) }
        // Identity-keyed so that two `FlatFunctionIR`s which happen to be structurally
        // equal (same name / qualifiedName / CFG / etc.) still map to distinct entries.
        val methodMap = java.util.IdentityHashMap<FlatFunctionIR, PIRFunctionImpl>()
        for (i in flat.methods.indices) {
            methodMap[flat.methods[i]] = methods[i]
        }
        val classFields = flat.fields.map {
            PIRFieldImpl(it.name, TypeConverter.convert(it.type), it.isClassVar, it.hasInitializer)
        }
        val nestedClasses = flat.nestedClasses.map { flatClassToPir(it) }
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
        )

        for (method in methods) {
            method.enclosingClass = cls
        }

        return cls
    }

    // ─── Property synthesis ───

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

    // ─── Function conversion ───

    private fun convertFlatFunction(pending: FlatFunctionIR): PIRFunctionImpl {
        val params = pending.parameters.mapIndexed { idx, p ->
            PIRParameterImpl(
                p.name,
                TypeConverter.convert(p.type),
                flatParamKindToPir(p.kind),
                p.hasDefault,
                p.defaultValue?.let { ConstConverter.convert(it) },
                idx,
            )
        }
        val returnType = TypeConverter.convert(pending.returnType)
        val cfgResult = CfgConverter.convert(pending.cfg)

        val function = PIRFunctionImpl(
            name = pending.name,
            qualifiedName = pending.qualifiedName,
            parameters = params,
            returnType = returnType,
            cfg = cfgResult.cfg,
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
        )
        wireInstructionLocations(function, cfgResult.lines)
        return function
    }

    private fun wireInstructionLocations(function: PIRFunctionImpl, lines: List<Int>) {
        function.instList.forEachIndexed { index, inst ->
            inst.location = PIRLocationImpl(function, index, lines[index], 0)
        }
    }

    private fun flatParamKindToPir(kind: FlatParamKind): PIRParameterKind = when (kind) {
        FlatParamKind.POSITIONAL_OR_KEYWORD -> PIRParameterKind.POSITIONAL_OR_KEYWORD
        FlatParamKind.VAR_POSITIONAL -> PIRParameterKind.VAR_POSITIONAL
        FlatParamKind.VAR_KEYWORD -> PIRParameterKind.VAR_KEYWORD
        FlatParamKind.KEYWORD_ONLY -> PIRParameterKind.KEYWORD_ONLY
    }
}
