package org.opentaint.ir.impl.storage.scheme


import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.jetbrains.exposed.dao.id.EntityID
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

private val symbolsCache = HashMap<String, EntityID<Int>>()

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
            classes.forEach {
                it.name.substringBeforeLast('.').findSymbol(symbolsCache)
                it.name.findSymbol(symbolsCache)
                it.fields.forEach {
                    it.name.findSymbol(symbolsCache)
                }
                it.methods.forEach {
                    it.name.findSymbol(symbolsCache)
                }
            }
        }
        val classInfoToIds = transaction {
            val locationEntity = findOrNew(location)
            val classesResult = Classes.batchInsert(classes) { classInfo ->
                val packageName = classInfo.name.substringBeforeLast('.')
                val pack = symbolsCache[packageName]!!
                this[Classes.access] = classInfo.access
                this[Classes.locationId] = locationEntity.id
                this[Classes.name] = classInfo.name.findSymbol(symbolsCache)
                this[Classes.signature] = classInfo.signature
                this[Classes.superClass] = classInfo.superClass?.findSymbol(symbolsCache)
                this[Classes.packageId] = pack
                this[Classes.bytecode] = classInfo.bytecode
                this[Classes.annotations] = classInfo.annotations.takeIf { it.isNotEmpty() }?.let {
                    Cbor.encodeToByteArray(it)
                }
            }
            val classIds = classesResult.mapIndexed { index, rs -> classes[index] to rs[Classes.id] }.toMap()

            classIds.forEach { (classInfo, storedClassId) ->
                if (classInfo.interfaces.isNotEmpty()) {
                    ClassInterfaces.batchInsert(classInfo.interfaces) {
                        this[ClassInterfaces.classId] = storedClassId
                        this[ClassInterfaces.interfaceId] = it.findSymbol(symbolsCache)
                    }
                }
                if (classInfo.innerClasses.isNotEmpty()) {
                    ClassInnerClasses.batchInsert(classInfo.innerClasses) {
                        this[ClassInnerClasses.classId] = storedClassId
                        this[ClassInnerClasses.innerClassId] = it.findSymbol(symbolsCache)
                    }
                }
                val methodsResult = Methods.batchInsert(classInfo.methods) {
                    this[Methods.access] = it.access
                    this[Methods.name] = symbolsCache.get(it.name)!!
                    this[Methods.signature] = it.signature
                    this[Methods.desc] = it.desc
                    this[Methods.classId] = storedClassId
                    this[Methods.returnClass] = it.returnType.findSymbol(symbolsCache)
                    this[Methods.annotations] = it.annotations.takeIf { it.isNotEmpty() }?.let {
                        Cbor.encodeToByteArray(it)
                    }
                }
                val paramsWithMethodId = methodsResult.flatMapIndexed { index, rs ->
                    val methodId = rs[Methods.id]
                    classInfo.methods[index].parametersInfo.map { it to methodId }
                }
                MethodParameters.batchInsert(paramsWithMethodId) {
                    val (param, methodId) = it
                    this[MethodParameters.access] = param.access
                    this[MethodParameters.name] = param.name
                    this[MethodParameters.index] = param.index
                    this[MethodParameters.methodId] = methodId
                    this[MethodParameters.parameterClass] = param.type.findSymbol(symbolsCache)
                    this[MethodParameters.annotations] = param.annotations.takeIf { !it.isNullOrEmpty() }?.let {
                        Cbor.encodeToByteArray(it)
                    }
                }
                Fields.batchInsert(classInfo.fields) {
                    this[Fields.classId] = storedClassId
                    this[Fields.access] = it.access
                    this[Fields.name] = symbolsCache.get(it.name)!!
                    this[Fields.signature] = it.signature
                    this[Fields.fieldClass] = it.type.findSymbol(symbolsCache)
                    this[Fields.annotations] = it.annotations.takeIf { it.isNotEmpty() }?.let {
                        Cbor.encodeToByteArray(it)
                    }
                }
            }
            classIds
        }
        transaction {
            classes.filter { it.outerClass != null }.forEach { classInfo ->
                val id = classInfoToIds[classInfo]!!
                val outerClazzId = classInfoToIds.filterKeys { it.name == classInfo.outerClass!!.className }
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
                                    (Methods.name eq classInfo.outerMethod.findSymbol(symbolsCache)) and
                                    (Methods.desc eq classInfo.outerMethodDesc)
                        }.firstOrNull()?.get(Methods.id)
                    }
                }
            }
        }
    }


    private fun String.findSymbol(classCache: HashMap<String, EntityID<Int>>): EntityID<Int> {
        return classCache.getOrPut(this) {
            Symbols.select { Symbols.name eq this@findSymbol }.firstOrNull()?.get(Symbols.id)
                ?: Symbols.insertAndGetId {
                    it[name] = this@findSymbol
                }
        }
    }
}