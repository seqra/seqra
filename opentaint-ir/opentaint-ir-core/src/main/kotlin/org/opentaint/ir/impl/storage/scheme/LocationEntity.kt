package org.opentaint.ir.impl.storage.scheme


import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.LocationScope
import org.opentaint.ir.impl.storage.BytecodeLocationEntity
import org.opentaint.ir.impl.storage.BytecodeLocations
import org.opentaint.ir.impl.storage.ClassInnerClasses
import org.opentaint.ir.impl.storage.ClassInterfaces
import org.opentaint.ir.impl.storage.Classes
import org.opentaint.ir.impl.storage.Fields
import org.opentaint.ir.impl.storage.MethodParameters
import org.opentaint.ir.impl.storage.Methods
import org.opentaint.ir.impl.storage.OuterClasses
import org.opentaint.ir.impl.storage.PersistentEnvironment
import org.opentaint.ir.impl.storage.Symbols
import org.opentaint.ir.impl.types.ClassInfo
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private val symbolsCache = HashMap<String, Int>()
val classIdGen = AtomicInteger()
val classNameIdGen = AtomicInteger()
val methodIdGen = AtomicLong()
val fieldIdGen = AtomicLong()
val methodParamIdGen = AtomicLong()

class LocationStore(private val dbStore: PersistentEnvironment) {

    val all: Sequence<BytecodeLocationEntity>
        get() {
            return transaction {
                BytecodeLocationEntity.all()
            }.asSequence()
        }

    fun findOrNew(location: ByteCodeLocation): BytecodeLocationEntity {
        return BytecodeLocationEntity.find { BytecodeLocations.path eq location.path }.firstOrNull()
            ?: BytecodeLocationEntity.get(BytecodeLocations.insertAndGetId {
                it[path] = location.path
                it[runtime] = location.scope == LocationScope.RUNTIME
            })
    }

    fun findOrNewTx(location: ByteCodeLocation): BytecodeLocationEntity {
        return transaction {
            val loc = findOrNew(location)
            loc
        }
    }

    fun saveClasses(location: ByteCodeLocation, classes: List<ClassInfo>) {
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
                names.addAll(it.methods.map { it.name })
                names.addAll(it.methods.flatMap { it.parameters })
                names.addAll(it.fields.map { it.name })
            }
            names.setup()
        }
        val classIds = HashMap<ClassInfo, Int>()
        transaction {
            val locationEntity = findOrNew(location)
            Classes.batchInsert(classes, shouldReturnGeneratedValues = false) { classInfo ->
                val id = classIdGen.incrementAndGet()
                classIds[classInfo] = id
                val packageName = classInfo.name.substringBeforeLast('.')
                val pack = symbolsCache[packageName]!!
                this[Classes.id] = id
                this[Classes.access] = classInfo.access
                this[Classes.locationId] = locationEntity.id
                this[Classes.name] = classInfo.name.findSymbol()
                this[Classes.signature] = classInfo.signature
                this[Classes.superClass] = classInfo.superClass?.findSymbol()
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
                        this[ClassInterfaces.interfaceId] = it.findSymbol()
                    }
                }
                if (classInfo.innerClasses.isNotEmpty()) {
                    ClassInnerClasses.batchInsert(classInfo.innerClasses, shouldReturnGeneratedValues = false) {
                        this[ClassInnerClasses.classId] = storedClassId
                        this[ClassInnerClasses.innerClassId] = it.findSymbol()
                    }
                }
                val methodsResult = Methods.batchInsert(classInfo.methods, shouldReturnGeneratedValues = false) {
                    this[Methods.id] = methodIdGen.incrementAndGet()
                    this[Methods.access] = it.access
                    this[Methods.name] = symbolsCache.get(it.name)!!
                    this[Methods.signature] = it.signature
                    this[Methods.desc] = it.desc
                    this[Methods.classId] = storedClassId
                    this[Methods.returnClass] = it.returnType.findSymbol()
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
                    this[MethodParameters.parameterClass] = param.type.findSymbol()
                    this[MethodParameters.annotations] = param.annotations.takeIf { !it.isNullOrEmpty() }?.let {
                        Cbor.encodeToByteArray(it)
                    }
                }
                Fields.batchInsert(classInfo.fields, shouldReturnGeneratedValues = false) {
                    this[Fields.id] = fieldIdGen.incrementAndGet()
                    this[Fields.classId] = storedClassId
                    this[Fields.access] = it.access
                    this[Fields.name] = symbolsCache.get(it.name)!!
                    this[Fields.signature] = it.signature
                    this[Fields.fieldClass] = it.type.findSymbol()
                    this[Fields.annotations] = it.annotations.takeIf { it.isNotEmpty() }?.let {
                        Cbor.encodeToByteArray(it)
                    }
                }
            }
            classIds
        }
        transaction {
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
                                    (Methods.name eq classInfo.outerMethod.findSymbol()) and
                                    (Methods.desc eq classInfo.outerMethodDesc)
                        }.firstOrNull()?.get(Methods.id)
                    }
                }
            }
        }
    }


    private fun String.findSymbol(): Int {
        return symbolsCache.getOrPut(this) {
            Symbols.select { Symbols.name eq this@findSymbol }.firstOrNull()?.get(Symbols.id)?.value
                ?: kotlin.run {
                    val id = classNameIdGen.incrementAndGet()
                    Symbols.batchInsert(listOf(this), shouldReturnGeneratedValues = false) {
                        this[Symbols.id] = id
                        this[Symbols.name] = it
                    }
                    id
                }
        }
    }

    private fun Collection<String>.setup() {
        val forCreation = filter { !symbolsCache.containsKey(it) }
        Symbols.batchInsert(forCreation, shouldReturnGeneratedValues = false) {
            val id = classNameIdGen.incrementAndGet()
            symbolsCache[it] = id
            this[Symbols.id] = id
            this[Symbols.name] = it
        }
    }
}