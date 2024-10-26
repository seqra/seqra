package org.opentaint.ir.impl.storage


import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.types.AnnotationInfo
import org.opentaint.ir.impl.types.AnnotationValue
import org.opentaint.ir.impl.types.AnnotationValueList
import org.opentaint.ir.impl.types.ClassInfo
import org.opentaint.ir.impl.types.ClassRef
import org.opentaint.ir.impl.types.EnumRef
import org.opentaint.ir.impl.types.FieldInfo
import org.opentaint.ir.impl.types.PrimitiveValue
import java.util.concurrent.atomic.AtomicLong

class PersistenceService(private val persistence: SQLitePersistenceImpl) {

    private val symbolsCache = HashMap<String, Long>()
    private val classIdGen = AtomicLong()
    private val symbolsIdGen = AtomicLong()
    private val methodIdGen = AtomicLong()
    private val fieldIdGen = AtomicLong()
    private val methodParamIdGen = AtomicLong()
    private val annotationIdGen = AtomicLong()
    private val annotationValueIdGen = AtomicLong()
    private val outerClassIdGen = AtomicLong()

    fun setup() {
        transaction(persistence.db) {
            SymbolEntity.all().forEach {
                symbolsCache[it.name] = it.id.value
            }

            classIdGen.set(Classes.maxId ?: 0)
            symbolsIdGen.set(Symbols.maxId ?: 0)
            methodIdGen.set(Methods.maxId ?: 0)
            fieldIdGen.set(Fields.maxId ?: 0)
            methodParamIdGen.set(MethodParameters.maxId ?: 0)
            annotationIdGen.set(Annotations.maxId ?: 0)
            annotationValueIdGen.set(AnnotationValues.maxId ?: 0)
            outerClassIdGen.set(OuterClasses.maxId ?: 0)
        }
    }

    fun persist(location: RegisteredLocation, classes: List<ClassInfo>) {
        val classIds = HashMap<ClassInfo, Long>()
        transaction(persistence.db) {
            val names = HashSet<String>()
            val annotationCollector = AnnotationCollector(annotationIdGen, annotationValueIdGen, symbolsCache)
            val fieldCollector = FieldCollector(fieldIdGen, annotationCollector)
            val classRefCollector = ClassRefCollector()
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
            names.setup()
            val locationId = location.id
            Classes.batchInsert(classes, shouldReturnGeneratedValues = false) { classInfo ->
                val id = classIdGen.incrementAndGet()
                classIds[classInfo] = id
                val packageName = classInfo.name.substringBeforeLast('.')
                val pack = packageName.findCachedSymbol()
                this[Classes.id] = id
                this[Classes.access] = classInfo.access
                this[Classes.locationId] = locationId
                this[Classes.name] = classInfo.name.findCachedSymbol()
                this[Classes.signature] = classInfo.signature
                this[Classes.superClass] = classInfo.superClass?.findCachedSymbol()
                this[Classes.packageId] = pack
                this[Classes.bytecode] = classInfo.bytecode
                annotationCollector.collect(classInfo.annotations, id, RefKind.CLASS)
            }

            classIds.forEach { (classInfo, storedClassId) ->
                if (classInfo.interfaces.isNotEmpty()) {
                    classInfo.interfaces.forEach {
                        classRefCollector.collectInterface(storedClassId, it)
                    }

                    ClassInterfaces.batchInsert(classInfo.interfaces, shouldReturnGeneratedValues = false) {
                        this[ClassInterfaces.classId] = storedClassId
                        this[ClassInterfaces.interfaceId] = it.findCachedSymbol()
                    }
                }
                if (classInfo.innerClasses.isNotEmpty()) {
                    classInfo.innerClasses.forEach {
                        classRefCollector.collectInnerClass(storedClassId, it)
                    }
                }
                val methodsResult = Methods.batchInsert(classInfo.methods, shouldReturnGeneratedValues = false) {
                    val methodId = methodIdGen.incrementAndGet()
                    this[Methods.id] = methodId
                    this[Methods.access] = it.access
                    this[Methods.name] = it.name.findCachedSymbol()
                    this[Methods.signature] = it.signature
                    this[Methods.desc] = it.desc
                    this[Methods.classId] = storedClassId
                    this[Methods.returnClass] = it.returnClass.findCachedSymbol()
                    annotationCollector.collect(it.annotations, methodId, RefKind.METHOD)
                }
                val paramsWithMethodId = methodsResult.flatMapIndexed { index, rs ->
                    val methodId = rs[Methods.id]
                    classInfo.methods[index].parametersInfo.map { it to methodId }
                }
                MethodParameters.batchInsert(paramsWithMethodId, shouldReturnGeneratedValues = false) {
                    val (param, methodId) = it
                    val paramId = methodParamIdGen.incrementAndGet()
                    this[MethodParameters.id] = paramId
                    this[MethodParameters.access] = param.access
                    this[MethodParameters.name] = param.name
                    this[MethodParameters.index] = param.index
                    this[MethodParameters.methodId] = methodId
                    this[MethodParameters.parameterClass] = param.type.findCachedSymbol()
                    annotationCollector.collect(param.annotations, paramId, RefKind.PARAM)
                }
                classInfo.fields.forEach {
                    fieldCollector.collect(storedClassId, it)
                }
            }
            ClassInnerClasses.batchInsert(classRefCollector.innerClasses.entries, shouldReturnGeneratedValues = false) {
                this[ClassInnerClasses.classId] = it.key
                this[ClassInnerClasses.innerClassId] = it.value.findCachedSymbol()
            }
            ClassInterfaces.batchInsert(classRefCollector.interfaces.entries, shouldReturnGeneratedValues = false) {
                this[ClassInterfaces.classId] = it.key
                this[ClassInterfaces.interfaceId] = it.value.findCachedSymbol()
            }
            Fields.batchInsert(fieldCollector.fields.entries, shouldReturnGeneratedValues = false) {
                val (fieldId, fieldInfo) = it.value

                this[Fields.id] = fieldId
                this[Fields.classId] = it.key
                this[Fields.access] = fieldInfo.access
                this[Fields.name] = fieldInfo.name.findCachedSymbol()
                this[Fields.signature] = fieldInfo.signature
                this[Fields.fieldClass] = fieldInfo.type.findCachedSymbol()
            }

            Annotations.batchInsert(annotationCollector.collected, shouldReturnGeneratedValues = false) {
                this[Annotations.id] = it.id
                when (it.refKind) {
                    RefKind.CLASS -> this[Annotations.classRef] = it.refId
                    RefKind.FIELD -> this[Annotations.fieldRef] = it.refId
                    RefKind.METHOD -> this[Annotations.methodRef] = it.refId
                    RefKind.PARAM -> this[Annotations.parameterRef] = it.refId
                }
                this[Annotations.parentAnnotation] = it.parentId
                this[Annotations.visible] = it.info.visible
                this[Annotations.name] = it.info.className.findCachedSymbol()
            }
            AnnotationValues.batchInsert(annotationCollector.collectedValues, shouldReturnGeneratedValues = false) {
                this[AnnotationValues.id] = it.id
                this[AnnotationValues.name] = it.name
                this[AnnotationValues.annotation] = it.annotationId
                if (it.primitiveValueType != null) {
                    this[AnnotationValues.kind] = it.primitiveValueType
                    this[AnnotationValues.value] = it.primitiveValue
                }
                this[AnnotationValues.enumValue] = it.enumSymbolId
            }
            classes.filter { it.outerClass != null }.forEach { classInfo ->
                val outerClass = classInfo.outerClass!!
                val outerClassId = outerClass.className.findCachedSymbol()
                OuterClasses.insert {
                    it[OuterClasses.id] = outerClassIdGen.incrementAndGet()
                    it[name] = outerClass.name
                    it[outerClassName] = outerClassId
                    it[methodName] = classInfo.outerMethod
                    it[methodDesc] = classInfo.outerMethodDesc
                }
            }
        }
    }

    private fun String.findCachedSymbol(): Long {
        return symbolsCache[this]
            ?: throw IllegalStateException("Symbol $this is required in cache. Please setup cache first")
    }

    private fun Collection<String>.setup() {
        val forCreation = filter { !symbolsCache.containsKey(it) }
        Symbols.batchInsert(forCreation, shouldReturnGeneratedValues = false) {
            val id = symbolsIdGen.incrementAndGet()
            symbolsCache[it] = id
            this[Symbols.id] = id
            this[Symbols.name] = it
            this[Symbols.hash] = it.longHash
        }
    }

    private val <T : Comparable<T>> IdTable<T>.maxId: T?
        get() {
            val maxId = slice(id.max()).selectAll().firstOrNull() ?: return null
            return maxId[id.max()]?.value
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

    val interfaces = HashMap<Long, String>()
    val innerClasses = HashMap<Long, String>()

    fun collectInterface(classId: Long, iface: String) {
        interfaces[classId] = iface
    }

    fun collectInnerClass(classId: Long, innerClass: String) {
        innerClasses[classId] = innerClass
    }
}
