package org.opentaint.ir.impl.storage

import org.jooq.TableField
import org.jooq.impl.DSL.max
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.storage.jooq.tables.references.ANNOTATIONS
import org.opentaint.ir.impl.storage.jooq.tables.references.ANNOTATIONVALUES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSINNERCLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.FIELDS
import org.opentaint.ir.impl.storage.jooq.tables.references.METHODPARAMETERS
import org.opentaint.ir.impl.storage.jooq.tables.references.METHODS
import org.opentaint.ir.impl.storage.jooq.tables.references.OUTERCLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.types.AnnotationInfo
import org.opentaint.ir.impl.types.AnnotationValue
import org.opentaint.ir.impl.types.AnnotationValueList
import org.opentaint.ir.impl.types.ClassInfo
import org.opentaint.ir.impl.types.ClassRef
import org.opentaint.ir.impl.types.EnumRef
import org.opentaint.ir.impl.types.FieldInfo
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.types.ParameterInfo
import org.opentaint.ir.impl.types.PrimitiveValue
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class PersistenceService(private val persistence: SQLitePersistenceImpl) {

    private val symbolsCache = ConcurrentHashMap<String, Long>()
    private val classIdGen = AtomicLong()
    private val symbolsIdGen = AtomicLong()
    private val methodIdGen = AtomicLong()
    private val fieldIdGen = AtomicLong()
    private val methodParamIdGen = AtomicLong()
    private val annotationIdGen = AtomicLong()
    private val annotationValueIdGen = AtomicLong()
    private val outerClassIdGen = AtomicLong()

    fun setup() {
        persistence.read {
            it.selectFrom(SYMBOLS).fetch().forEach {
                val (id, name) = it
                if (name != null && id != null)
                    symbolsCache[name] = id
            }

            classIdGen.set(CLASSES.ID.maxId ?: 0)
            symbolsIdGen.set(SYMBOLS.ID.maxId ?: 0)
            methodIdGen.set(METHODS.ID.maxId ?: 0)
            fieldIdGen.set(FIELDS.ID.maxId ?: 0)
            methodParamIdGen.set(METHODPARAMETERS.ID.maxId ?: 0)
            annotationIdGen.set(ANNOTATIONS.ID.maxId ?: 0)
            annotationValueIdGen.set(ANNOTATIONVALUES.ID.maxId ?: 0)
            outerClassIdGen.set(OUTERCLASSES.ID.maxId ?: 0)
        }
    }

    fun persist(location: RegisteredLocation, classes: List<ClassInfo>) {
        val classCollector = ClassCollector(classIdGen)
        val annotationCollector = AnnotationCollector(annotationIdGen, annotationValueIdGen, symbolsCache)
        val fieldCollector = FieldCollector(fieldIdGen, annotationCollector)
        val classRefCollector = ClassRefCollector()
        val paramsCollector = MethodParamsCollector()
        val methodsCollector = MethodsCollector(methodIdGen, annotationCollector, paramsCollector)
        val names = HashSet<String>()
        classes.forEach {
            names.add(it.name.substringBeforeLast('.'))
            names.add(it.name)
            it.superClass?.let {
                names.add(it)
            }
            names.addAll(it.interfaces)
            names.addAll(it.innerClasses)
            names.addAll(listOfNotNull(it.outerClass?.name, it.outerMethod))
            names.addAll(it.methods.map { it.name })
            names.addAll(it.methods.map { it.returnClass })
            names.addAll(it.methods.flatMap { it.parameters })
            names.addAll(it.fields.map { it.name })
            names.addAll(it.fields.map { it.type })
            it.outerClass?.className?.let { names.add(it) }
            it.annotations.extractAllSymbolsTo(names)
            it.methods.forEach {
                it.annotations.extractAllSymbolsTo(names)
                it.parametersInfo.forEach { it.annotations.extractAllSymbolsTo(names) }
            }
            it.fields.forEach { it.annotations.extractAllSymbolsTo(names) }
        }
        val namesToAdd = arrayListOf<Pair<Long, String>>()
        persistence.write {
            names.forEach {
                if (!symbolsCache.containsKey(it)) {
                    val id = symbolsIdGen.incrementAndGet()
                    symbolsCache[it] = id
                    namesToAdd.add(id to it)
                }
            }
        }
        val locationId = location.id
        classes.forEach { classCollector.collect(it) }
        classCollector.classes.entries.forEach { (classInfo, storedClassId) ->
            if (classInfo.interfaces.isNotEmpty()) {
                classInfo.interfaces.forEach {
                    classRefCollector.collectParent(storedClassId, it, isClass = false)
                }
            }
            classRefCollector.collectParent(storedClassId, classInfo.superClass, isClass = true)
            if (classInfo.innerClasses.isNotEmpty()) {
                classInfo.innerClasses.forEach {
                    classRefCollector.collectInnerClass(storedClassId, it)
                }
            }
            classInfo.methods.forEach {
                methodsCollector.collect(storedClassId, it)
            }
            classInfo.fields.forEach {
                fieldCollector.collect(storedClassId, it)
            }
        }

        persistence.write {
            it.connection { conn ->

                conn.insertElements(SYMBOLS, namesToAdd) { pair ->
                    setLong(1, pair.first)
                    setString(2, pair.second)
                    setLong(3, pair.second.longHash)
                }

                conn.insertElements(CLASSES, classCollector.classes.entries) {
                    val (classInfo, id) = it
                    val packageName = classInfo.name.substringBeforeLast('.')
                    val pack = packageName.findCachedSymbol()
                    setLong(1, id)
                    setInt(2, classInfo.access)
                    setLong(3, classInfo.name.findCachedSymbol())
                    setString(4, classInfo.signature)
                    setBytes(5, classInfo.bytecode)
                    setLong(6, locationId)
                    setLong(7, pack)
                    annotationCollector.collect(classInfo.annotations, id, RefKind.CLASS)
                }
                conn.insertElements(METHODS, methodsCollector.methods) {
                    val (classId, methodId, method) = it
                    setLong(1, methodId)
                    setInt(2, method.access)
                    setLong(3, method.name.findCachedSymbol())
                    setString(4, method.signature)
                    setString(5, method.desc)
                    setLong(6, method.returnClass.findCachedSymbol())
                    setLong(7, classId)
                    annotationCollector.collect(method.annotations, methodId, RefKind.METHOD)
                }

                conn.insertElements(METHODPARAMETERS, paramsCollector.params) {
                    val (methodId, param) = it
                    val paramId = methodParamIdGen.incrementAndGet()
                    setLong(1, paramId)
                    setInt(2, param.access)
                    setInt(3, param.index)
                    setString(4, param.name)
                    setLong(5, param.type.findCachedSymbol())
                    setLong(6, methodId)
                    annotationCollector.collect(param.annotations, paramId, RefKind.PARAM)
                }

                conn.insertElements(CLASSINNERCLASSES, classRefCollector.innerClasses.entries) { innerClass ->
                    setNull(1, Types.BIGINT)
                    setLong(2, innerClass.key)
                    setLong(3, innerClass.value.findCachedSymbol())
                }
                conn.insertElements(CLASSHIERARCHIES, classRefCollector.superClasses) { superClass ->
                    setNull(1, Types.BIGINT)
                    setLong(2, superClass.first)
                    setLong(3, superClass.second.findCachedSymbol())
                    setBoolean(4, superClass.third)
                }

                conn.insertElements(FIELDS, fieldCollector.fields.entries) { field ->
                    val (fieldId, fieldInfo) = field.value
                    setLong(1, fieldId)
                    setInt(2, fieldInfo.access)
                    setLong(3, fieldInfo.name.findCachedSymbol())
                    setString(4, fieldInfo.signature)
                    setLong(5, fieldInfo.type.findCachedSymbol())
                    setLong(6, field.key)
                }

                conn.insertElements(ANNOTATIONS, annotationCollector.collected) { annotation ->
                    setLong(1, annotation.id)
                    setLong(2, annotation.info.className.findCachedSymbol())
                    setBoolean(3, annotation.info.visible)
                    setNullableLong(4, annotation.parentId)

                    setNullableLong(5, annotation.refId.takeIf { annotation.refKind == RefKind.CLASS })
                    setNullableLong(6, annotation.refId.takeIf { annotation.refKind == RefKind.METHOD })
                    setNullableLong(7, annotation.refId.takeIf { annotation.refKind == RefKind.FIELD })
                    setNullableLong(8, annotation.refId.takeIf { annotation.refKind == RefKind.PARAM })
                }

                conn.insertElements(ANNOTATIONVALUES, annotationCollector.collectedValues) { value ->
                    setLong(1, value.id)
                    setString(2, value.name)
                    setLong(3, value.annotationId)
                    if (value.primitiveValueType != null) {
                        setInt(4, value.primitiveValueType.ordinal)
                        setString(5, value.primitiveValue)
                    } else {
                        setNull(4, Types.INTEGER)
                        setNull(5, Types.VARCHAR)
                    }
                    setNullableLong(6, value.enumSymbolId)
                }

                conn.insertElements(OUTERCLASSES, classes.filter { it.outerClass != null }) { classInfo ->
                    val outerClass = classInfo.outerClass!!
                    val outerClassId = outerClass.className.findCachedSymbol()
                    setLong(1, outerClassIdGen.incrementAndGet())
                    setLong(2, outerClassId)
                    setString(3, outerClass.name)
                    setString(4, classInfo.outerMethod)
                    setString(5, classInfo.outerMethodDesc)
                }
            }
        }
    }

    fun findSymbolId(symbol: String): Long? {
        return symbolsCache.get(symbol)
    }

    private fun String.findCachedSymbol(): Long {
        return symbolsCache[this]
            ?: throw IllegalStateException("Symbol $this is required in cache. Please setup cache first")
    }

    private val TableField<*, Long?>.maxId: Long?
        get() {
            val create = persistence.jooq
            return create.select(max(this))
                .from(table).fetchAny()?.component1()
        }
}

internal enum class RefKind {
    CLASS, FIELD, METHOD, PARAM,
}

private data class AnnotationItem(
    val id: Long,
    val parentId: Long?,
    val refId: Long,
    val refKind: RefKind,
    val info: AnnotationInfo
)

private data class AnnotationValueItem(
    val id: Long,
    val annotationId: Long,

    val name: String,

    val classSymbolId: Long? = null,
    val enumSymbolId: Long? = null,

    val primitiveValue: String? = null,
    val primitiveValueType: AnnotationValueKind? = null,
)

private class AnnotationCollector(
    val annotationIdGen: AtomicLong,
    val annotationValueIdGen: AtomicLong,
    val symbolsCache: Map<String, Long>
) {
    val collected = ArrayList<AnnotationItem>()
    val collectedValues = ArrayList<AnnotationValueItem>()

    fun collect(annotations: List<AnnotationInfo>, refId: Long, kind: RefKind) {
        annotations.forEach {
            collect(it, refId, kind)
        }
    }

    fun collect(info: AnnotationInfo, refId: Long, kind: RefKind, parentId: Long? = null) {
        val id = annotationIdGen.incrementAndGet()
        val parent = AnnotationItem(id = id, refId = refId, info = info, refKind = kind, parentId = parentId)
        collected.add(parent)
        info.values.forEach {
            collectValue(it, parent)
        }
    }

    fun collectValue(nameValue: Pair<String, AnnotationValue>, parent: AnnotationItem) {
        val (name, value) = nameValue
        val valueId = annotationValueIdGen.incrementAndGet()
        when (value) {
            is AnnotationInfo -> collect(value, parent.refId, parent.refKind, parent.id)
            is ClassRef -> collectedValues.add(
                AnnotationValueItem(
                    id = valueId,
                    name = name,
                    classSymbolId = symbolsCache.get(value.className)!!,
                    annotationId = parent.id,
                    enumSymbolId = null,
                )
            )

            is EnumRef -> collectedValues.add(
                AnnotationValueItem(
                    id = valueId,
                    name = name,
                    classSymbolId = symbolsCache[value.className]!!,
                    enumSymbolId = symbolsCache[value.enumName]!!,
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

private class FieldCollector(private val fieldIdGen: AtomicLong, private val annotationCollector: AnnotationCollector) {

    val fields = HashMap<Long, Pair<Long, FieldInfo>>()

    fun collect(classId: Long, fieldInfo: FieldInfo) {
        val fieldId = fieldIdGen.incrementAndGet()
        fields[classId] = fieldId to fieldInfo
        annotationCollector.collect(fieldInfo.annotations, fieldId, RefKind.FIELD)
    }
}

private class ClassRefCollector {

    val superClasses = ArrayList<Triple<Long, String, Boolean>>()
    val innerClasses = HashMap<Long, String>()

    fun collectParent(classId: Long, superClass: String?, isClass: Boolean = true) {
        if (superClass != null && superClass != "java.lang.Object") {
            superClasses += Triple(classId, superClass, isClass)
        }
    }

    fun collectInnerClass(classId: Long, innerClass: String) {
        innerClasses[classId] = innerClass
    }
}

private class MethodParamsCollector {

    val params = ArrayList<Pair<Long, ParameterInfo>>()

    fun collect(methodId: Long, param: ParameterInfo) {
        params.add(methodId to param)
    }

}

private class ClassCollector(private val classIdGen: AtomicLong) {

    val classes = HashMap<ClassInfo, Long>()

    fun collect(classInfo: ClassInfo) {
        val id = classIdGen.incrementAndGet()
        classes[classInfo] = id
    }

}

private class MethodsCollector(
    private val methodIdGen: AtomicLong,
    private val annotationCollector: AnnotationCollector,
    private val paramsCollector: MethodParamsCollector
) {

    val methods = ArrayList<Triple<Long, Long, MethodInfo>>()

    fun collect(classId: Long, method: MethodInfo) {
        val methodId = methodIdGen.incrementAndGet()
        methods.add(Triple(classId, methodId, method))
        method.parametersInfo.forEach {
            paramsCollector.collect(methodId, it)
        }
        annotationCollector.collect(method.annotations, methodId, RefKind.METHOD)
    }

}
