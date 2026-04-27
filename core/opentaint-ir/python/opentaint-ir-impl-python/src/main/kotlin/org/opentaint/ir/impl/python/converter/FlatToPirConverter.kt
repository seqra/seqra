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
        val pirFunctions = flat.functions.map { convertFlatFunction(it) }
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
        val classFields = flat.fields.map {
            PIRFieldImpl(it.name, TypeConverter.convert(it.type), it.isClassVar, it.hasInitializer)
        }
        val nestedClasses = flat.nestedClasses.map { flatClassToPir(it) }
        val properties = synthesizeProperties(flat.methods, methods)
        val decorators = flat.decorators.map { it.toPir() }

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
     *
     * [flatMethods] and [pirMethods] are position-aligned: `pirMethods[i]` is the
     * conversion of `flatMethods[i]`.
     */
    private fun synthesizeProperties(
        flatMethods: List<FlatFunctionIR>,
        pirMethods: List<PIRFunctionImpl>,
    ): List<PIRProperty> {
        require(flatMethods.size == pirMethods.size) {
            "flatMethods and pirMethods must be position-aligned: ${flatMethods.size} vs ${pirMethods.size}"
        }
        // `distinct()` preserves first-occurrence order so `PIRClass.properties`
        // reflects source order of getter definitions.
        val propertyGetterNames = flatMethods.filter { it.isProperty }.map { it.name }.distinct()
        val result = mutableListOf<PIRProperty>()
        for (propName in propertyGetterNames) {
            val groupIndices = flatMethods.indices.filter { flatMethods[it].name == propName }
            val getterIdx = groupIndices.firstOrNull { flatMethods[it].isProperty }
            val getterParamCount = getterIdx?.let { flatMethods[it].parameters.size } ?: 0
            val setterIdx = groupIndices.firstOrNull {
                !flatMethods[it].isProperty && flatMethods[it].parameters.size > getterParamCount
            } ?: groupIndices.firstOrNull {
                it != getterIdx && flatMethods[it].parameters.size > getterParamCount
            }
            val deleterIdx = groupIndices.firstOrNull {
                it != getterIdx && it != setterIdx && flatMethods[it].parameters.size == getterParamCount
            }

            val getterPir = getterIdx?.let { pirMethods[it] }
            val setterPir = setterIdx?.let { pirMethods[it] }
            val deleterPir = deleterIdx?.let { pirMethods[it] }
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
                p.kind.toPir(),
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
            decorators = pending.decorators.map { it.toPir() },
            isAsync = pending.isAsync,
            isGenerator = pending.isGenerator,
            isStaticMethod = pending.isStaticMethod,
            isClassMethod = pending.isClassMethod,
            isProperty = pending.isProperty,
            closureVars = pending.closureVars,
            enclosingClass = null,
        )
        // Each instruction was constructed with its location already populated;
        // only `method` is still pending. Wire it now that the function exists.
        for (loc in cfgResult.locations) loc.method = function
        return function
    }
}
