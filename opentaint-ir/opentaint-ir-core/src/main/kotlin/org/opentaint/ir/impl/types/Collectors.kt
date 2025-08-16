package org.opentaint.ir.impl.types

import org.opentaint.ir.api.storage.SymbolInterner
import org.opentaint.ir.api.storage.asSymbolId
import org.opentaint.ir.impl.storage.AnnotationValueKind

fun collectSymbols(classes: List<ClassInfo>): Set<String> = hashSetOf<String>().apply {
    classes.forEach {
        add(it.name.substringBeforeLast('.'))
        add(it.name)
        it.superClass?.let {
            add(it)
        }
        addAll(it.interfaces)
        addAll(it.innerClasses)
        addAll(listOfNotNull(it.outerClass?.name, it.outerMethod))
        addAll(it.methods.map { it.name })
        addAll(it.methods.map { it.returnClass })
        addAll(it.methods.flatMap { it.parameters })
        addAll(it.fields.map { it.name })
        addAll(it.fields.map { it.type })
        it.outerClass?.className?.let { add(it) }
        it.annotations.extractAllSymbolsTo(this)
        it.methods.forEach {
            it.annotations.extractAllSymbolsTo(this)
            it.parametersInfo.forEach { it.annotations.extractAllSymbolsTo(this) }
        }
        it.fields.forEach { it.annotations.extractAllSymbolsTo(this) }
    }
}

fun List<AnnotationInfo>.extractAllSymbolsTo(result: HashSet<String>) {
    forEach { it.extractAllSymbolsTo(result) }
}

fun AnnotationInfo.extractAllSymbolsTo(result: HashSet<String>) {
    result.add(className)
    values.forEach {
        it.second.extractSymbolsTo(result)
    }
}

fun AnnotationValue.extractSymbolsTo(result: HashSet<String>) {
    when (this) {
        is AnnotationInfo -> extractAllSymbolsTo(result)
        is ClassRef -> result.add(className)
        is EnumRef -> {
            result.add(enumName)
            result.add(className)
        }

        is AnnotationValueList -> annotations.forEach { it.extractSymbolsTo(result) }
        else -> {}
    }
}

enum class RefKind {
    CLASS, FIELD, METHOD, PARAM,
}

data class AnnotationItem(
    val id: Long,
    val parentId: Long?,
    val refId: Long,
    val refKind: RefKind,
    val info: AnnotationInfo
)

data class AnnotationValueItem(
    val id: Long,
    val annotationId: Long,

    val name: String,

    val classSymbolId: Long? = null,
    val refAnnotationId: Long? = null,
    val enumSymbolId: Long? = null,

    val primitiveValue: String? = null,
    val primitiveValueType: AnnotationValueKind? = null,
)

class AnnotationCollector(
    private val annotationIdGen: LongRef,
    private val annotationValueIdGen: LongRef,
    private val symbolInterner: SymbolInterner
) {
    val collected = ArrayList<AnnotationItem>()
    val collectedValues = ArrayList<AnnotationValueItem>()

    fun collect(annotations: List<AnnotationInfo>, refId: Long, kind: RefKind) {
        annotations.forEach {
            collect(it, refId, kind)
        }
    }

    private fun collect(info: AnnotationInfo, refId: Long, kind: RefKind, parentId: Long? = null): Long {
        val id = annotationIdGen.incrementAndGet()
        val parent = AnnotationItem(id = id, refId = refId, info = info, refKind = kind, parentId = parentId)
        collected.add(parent)
        info.values.forEach {
            collectValue(it, parent)
        }
        return id
    }

    private fun collectValue(nameValue: Pair<String, AnnotationValue>, parent: AnnotationItem) {
        val (name, value) = nameValue
        val valueId = annotationValueIdGen.incrementAndGet()
        when (value) {
            is AnnotationInfo -> {
                val refId = collect(value, parent.refId, parent.refKind, parent.id)
                collectedValues.add(
                    AnnotationValueItem(
                        id = valueId,
                        name = name,
                        refAnnotationId = refId,
                        annotationId = parent.id,
                        enumSymbolId = null,
                    )
                )
            }

            is ClassRef -> collectedValues.add(
                AnnotationValueItem(
                    id = valueId,
                    name = name,
                    classSymbolId = value.className.asSymbolId(symbolInterner),
                    annotationId = parent.id,
                    enumSymbolId = null,
                )
            )

            is EnumRef -> collectedValues.add(
                AnnotationValueItem(
                    id = valueId,
                    name = name,
                    classSymbolId = value.className.asSymbolId(symbolInterner),
                    enumSymbolId = value.enumName.asSymbolId(symbolInterner),
                    annotationId = parent.id,
                )
            )

            is PrimitiveValue -> collectedValues.add(
                AnnotationValueItem(
                    id = valueId,
                    name = name,
                    annotationId = parent.id,
                    primitiveValue = AnnotationValueKind.serialize(value.value),
                    primitiveValueType = value.dataType
                )
            )

            is AnnotationValueList -> {
                value.annotations.forEach {
                    collectValue(name to it, parent)
                }
            }
        }
    }
}

class FieldCollector(private val fieldIdGen: LongRef) {

    val fields = ArrayList<Triple<Long, Long, FieldInfo>>()

    fun collect(classId: Long, fieldInfo: FieldInfo) {
        val fieldId = fieldIdGen.incrementAndGet()
        fields.add(Triple(classId, fieldId, fieldInfo))
    }
}

class ClassRefCollector {

    val superClasses = ArrayList<Triple<Long, String, Boolean>>()
    val innerClasses = ArrayList<Pair<Long, String>>()

    fun collectParent(classId: Long, superClass: String?, isClass: Boolean = true) {
        if (superClass != null && superClass != "java.lang.Object") {
            superClasses += Triple(classId, superClass, isClass)
        }
    }

    fun collectInnerClass(classId: Long, innerClass: String) {
        innerClasses.add(classId to innerClass)
    }
}

class MethodParamCollector {

    val params = ArrayList<Pair<Long, ParameterInfo>>()

    fun collect(methodId: Long, param: ParameterInfo) {
        params.add(methodId to param)
    }
}

class MethodCollector(
    private val methodIdGen: LongRef,
    private val paramsCollector: MethodParamCollector
) {

    val methods = ArrayList<Triple<Long, Long, MethodInfo>>()

    fun collect(classId: Long, method: MethodInfo) {
        val methodId = methodIdGen.incrementAndGet()
        methods.add(Triple(classId, methodId, method))
        method.parametersInfo.forEach {
            paramsCollector.collect(methodId, it)
        }
    }
}

class LongRef(private var l: Long = 0L) {

    fun incrementAndGet(): Long = ++l

    fun set(l: Long) = also { this.l = l }
}