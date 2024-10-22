package org.opentaint.ir.impl.storage


import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.storage.BytecodeLocationEntity.Companion.findOrNew
import org.opentaint.ir.impl.types.ClassInfo
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PersistenceService(private val persistence: SQLitePersistenceImpl) {

    private val symbolsCache = HashMap<String, Long>()
    private val classIdGen = AtomicInteger()
    private val symbolsIdGen = AtomicLong()
    private val methodIdGen = AtomicLong()
    private val fieldIdGen = AtomicLong()
    private val methodParamIdGen = AtomicLong()


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
        }
    }

    fun saveClasses(location: ByteCodeLocation, classes: List<ClassInfo>) {
        val classIds = HashMap<ClassInfo, Int>()
        transaction {
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
                names.addAll(it.methods.map { it.returnType })
                names.addAll(it.methods.flatMap { it.parameters })
                names.addAll(it.fields.map { it.name })
                names.addAll(it.fields.map { it.type })
            }
            names.setup()
            val locationEntity = location.findOrNew()
            Classes.batchInsert(classes, shouldReturnGeneratedValues = false) { classInfo ->
                val id = classIdGen.incrementAndGet()
                classIds[classInfo] = id
                val packageName = classInfo.name.substringBeforeLast('.')
                val pack = packageName.findCachedSymbol()
                this[Classes.id] = id
                this[Classes.access] = classInfo.access
                this[Classes.locationId] = locationEntity.id
                this[Classes.name] = classInfo.name.findCachedSymbol()
                this[Classes.signature] = classInfo.signature
                this[Classes.superClass] = classInfo.superClass?.findCachedSymbol()
                this[Classes.packageId] = pack
                this[Classes.bytecode] = classInfo.bytecode
                this[Classes.annotations] = classInfo.annotations.takeIf { it.isNotEmpty() }?.let {
                    Cbor.encodeToByteArray(it)
                }
            }

            classIds.forEach { (classInfo, storedClassId) ->
                if (classInfo.interfaces.isNotEmpty()) {
                    ClassInterfaces.batchInsert(classInfo.interfaces, shouldReturnGeneratedValues = false) {
                        this[ClassInterfaces.classId] = storedClassId
                        this[ClassInterfaces.interfaceId] = it.findCachedSymbol()
                    }
                }
                if (classInfo.innerClasses.isNotEmpty()) {
                    ClassInnerClasses.batchInsert(classInfo.innerClasses, shouldReturnGeneratedValues = false) {
                        this[ClassInnerClasses.classId] = storedClassId
                        this[ClassInnerClasses.innerClassId] = it.findCachedSymbol()
                    }
                }
                val methodsResult = Methods.batchInsert(classInfo.methods, shouldReturnGeneratedValues = false) {
                    this[Methods.id] = methodIdGen.incrementAndGet()
                    this[Methods.access] = it.access
                    this[Methods.name] = it.name.findCachedSymbol()
                    this[Methods.signature] = it.signature
                    this[Methods.desc] = it.desc
                    this[Methods.classId] = storedClassId
                    this[Methods.returnClass] = it.returnType.findCachedSymbol()
                    this[Methods.annotations] = it.annotations.takeIf { it.isNotEmpty() }?.let {
                        Cbor.encodeToByteArray(it)
                    }
                }
                val paramsWithMethodId = methodsResult.flatMapIndexed { index, rs ->
                    val methodId = rs[Methods.id]
                    classInfo.methods[index].parametersInfo.map { it to methodId }
                }
                MethodParameters.batchInsert(paramsWithMethodId, shouldReturnGeneratedValues = false) {
                    val (param, methodId) = it
                    this[MethodParameters.id] = methodParamIdGen.incrementAndGet()
                    this[MethodParameters.access] = param.access
                    this[MethodParameters.name] = param.name
                    this[MethodParameters.index] = param.index
                    this[MethodParameters.methodId] = methodId
                    this[MethodParameters.parameterClass] = param.type.findCachedSymbol()
                    this[MethodParameters.annotations] = param.annotations.takeIf { !it.isNullOrEmpty() }?.let {
                        Cbor.encodeToByteArray(it)
                    }
                }
                Fields.batchInsert(classInfo.fields, shouldReturnGeneratedValues = false) {
                    this[Fields.id] = fieldIdGen.incrementAndGet()
                    this[Fields.classId] = storedClassId
                    this[Fields.access] = it.access
                    this[Fields.name] = it.name.findCachedSymbol()
                    this[Fields.signature] = it.signature
                    this[Fields.fieldClass] = it.type.findCachedSymbol()
                    this[Fields.annotations] = it.annotations.takeIf { it.isNotEmpty() }?.let {
                        Cbor.encodeToByteArray(it)
                    }
                }
            }
            classes.filter { it.outerClass != null }.forEach { classInfo ->
                val id = classIds[classInfo]!!
                val outerClazzId = classIds.filterKeys { it.name == classInfo.outerClass!!.className }
                    .values.first()
                val refId = OuterClasses.insertAndGetId {
                    it[name] = classInfo.outerClass!!.name
                    it[classId] = outerClazzId
                }
                Classes.update(where = { Classes.id eq id }) {
                    it[outerClass] = refId
                    if (classInfo.outerMethod != null) {
                        it[outerMethod] = Methods.select {
                            (Methods.classId eq outerClazzId) and
                                    (Methods.name eq classInfo.outerMethod.findCachedSymbol()) and
                                    (Methods.desc eq classInfo.outerMethodDesc)
                        }.firstOrNull()?.get(Methods.id)
                    }
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