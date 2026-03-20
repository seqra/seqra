package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.*
import org.opentaint.ir.impl.python.proto.*

class ModuleConverter(
    private val classpath: PIRClasspath,
) {
    private val typeConverter = TypeConverter()
    private val valueConverter = ValueConverter(typeConverter)
    private val instructionConverter = InstructionConverter(typeConverter, valueConverter)

    fun convert(proto: PIRModuleProto): PIRModule {
        // We create a placeholder module first, then fill in classes/functions
        // because they need back-references to the module.
        val module = PIRModuleImpl(
            name = proto.name,
            path = proto.path,
            classes = emptyList(), // filled below
            functions = emptyList(),
            fields = proto.fieldsList.map { convertField(it) },
            moduleInit = convertFunction(proto.moduleInit, null, null),
            imports = proto.importsList.toList(),
            classpath = classpath,
        )

        // Now convert classes and functions with the module reference
        val classes = proto.classesList.map { convertClass(it, module) }
        val functions = proto.functionsList.map { convertFunction(it, null, module) }
        val moduleInit = convertFunction(proto.moduleInit, null, module)

        return PIRModuleImpl(
            name = proto.name,
            path = proto.path,
            classes = classes,
            functions = functions,
            fields = proto.fieldsList.map { convertField(it) },
            moduleInit = moduleInit,
            imports = proto.importsList.toList(),
            classpath = classpath,
        )
    }

    private fun convertClass(proto: PIRClassProto, module: PIRModule): PIRClass {
        // Placeholder for recursive reference
        val cls = PIRClassImpl(
            name = proto.name,
            qualifiedName = proto.qualifiedName,
            baseClasses = proto.baseClassesList.toList(),
            mro = proto.mroList.toList(),
            methods = proto.methodsList.map { convertFunction(it, null, module) },
            fields = proto.fieldsList.map { convertField(it) },
            nestedClasses = proto.nestedClassesList.map { convertClass(it, module) },
            properties = proto.propertiesList.map { convertProperty(it) },
            decorators = proto.decoratorsList.map { convertDecorator(it) },
            isAbstract = proto.isAbstract,
            isDataclass = proto.isDataclass,
            isEnum = proto.isEnum,
            module = module,
        )

        // Re-create methods with enclosingClass set
        return PIRClassImpl(
            name = cls.name,
            qualifiedName = cls.qualifiedName,
            baseClasses = cls.baseClasses,
            mro = cls.mro,
            methods = proto.methodsList.map { convertFunction(it, cls, module) },
            fields = cls.fields,
            nestedClasses = cls.nestedClasses,
            properties = cls.properties,
            decorators = cls.decorators,
            isAbstract = cls.isAbstract,
            isDataclass = cls.isDataclass,
            isEnum = cls.isEnum,
            module = module,
        )
    }

    private fun convertFunction(
        proto: PIRFunctionProto,
        enclosingClass: PIRClass?,
        module: PIRModule?,
    ): PIRFunction {
        val cfg = if (proto.hasCfg()) instructionConverter.convertCFG(proto.cfg) else emptyCFG()

        return PIRFunctionImpl(
            name = proto.name,
            qualifiedName = proto.qualifiedName,
            parameters = proto.parametersList.mapIndexed { i, p -> convertParameter(p, i) },
            returnType = if (proto.hasReturnType()) typeConverter.convert(proto.returnType) else PIRAnyType,
            cfg = cfg,
            decorators = proto.decoratorsList.map { convertDecorator(it) },
            isAsync = proto.isAsync,
            isGenerator = proto.isGenerator,
            isStaticMethod = proto.isStaticMethod,
            isClassMethod = proto.isClassMethod,
            isProperty = proto.isProperty,
            closureVars = proto.closureVarsList.toList(),
            enclosingClass = enclosingClass,
            module = module ?: createDummyModule(),
        )
    }

    private fun convertField(proto: PIRFieldProto): PIRField {
        return PIRFieldImpl(
            name = proto.name,
            type = if (proto.hasType()) typeConverter.convert(proto.type) else PIRAnyType,
            isClassVar = proto.isClassVar,
            hasInitializer = proto.hasInitializer,
        )
    }

    private fun convertParameter(proto: PIRParameterProto, index: Int): PIRParameter {
        return PIRParameterImpl(
            name = proto.name,
            type = if (proto.hasType()) typeConverter.convert(proto.type) else PIRAnyType,
            kind = convertParameterKind(proto.kind),
            hasDefault = proto.hasDefault,
            index = index,
        )
    }

    private fun convertProperty(proto: PIRPropertyProto): PIRProperty {
        return PIRPropertyImpl(
            name = proto.name,
            type = if (proto.hasType()) typeConverter.convert(proto.type) else PIRAnyType,
            getter = null, // Resolved later by name lookup
            setter = null,
            deleter = null,
        )
    }

    private fun convertDecorator(proto: PIRDecoratorProto): PIRDecorator {
        return PIRDecoratorImpl(
            name = proto.name,
            qualifiedName = proto.qualifiedName,
            arguments = proto.argumentsList.toList(),
        )
    }

    private fun convertParameterKind(kind: ParameterKind): PIRParameterKind = when (kind) {
        ParameterKind.POSITIONAL_ONLY -> PIRParameterKind.POSITIONAL_ONLY
        ParameterKind.POSITIONAL_OR_KEYWORD -> PIRParameterKind.POSITIONAL_OR_KEYWORD
        ParameterKind.VAR_POSITIONAL -> PIRParameterKind.VAR_POSITIONAL
        ParameterKind.KEYWORD_ONLY -> PIRParameterKind.KEYWORD_ONLY
        ParameterKind.VAR_KEYWORD -> PIRParameterKind.VAR_KEYWORD
        ParameterKind.UNRECOGNIZED -> PIRParameterKind.POSITIONAL_OR_KEYWORD
    }

    private fun emptyCFG(): PIRCFG = PIRCFGImpl(
        blocks = listOf(PIRBasicBlock(0, listOf(PIRReturn(null)), emptyList())),
        entryLabel = 0,
        exitLabels = setOf(0),
    )

    private fun createDummyModule(): PIRModule = object : PIRModule {
        override val name = "<unknown>"
        override val path = ""
        override val classes = emptyList<PIRClass>()
        override val functions = emptyList<PIRFunction>()
        override val fields = emptyList<PIRField>()
        override val moduleInit: PIRFunction get() = throw UnsupportedOperationException()
        override val imports = emptyList<String>()
        override val classpath: PIRClasspath get() = throw UnsupportedOperationException()
    }
}
