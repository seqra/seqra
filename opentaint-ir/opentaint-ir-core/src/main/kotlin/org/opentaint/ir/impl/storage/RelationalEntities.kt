package org.opentaint.ir.impl.storage

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.insertAndGetId
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.LocationScope

class BytecodeLocationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BytecodeLocationEntity>(BytecodeLocations) {

        fun ByteCodeLocation.findOrNew(): BytecodeLocationEntity {
            return BytecodeLocationEntity.find { BytecodeLocations.path eq path }.firstOrNull()
                ?: BytecodeLocationEntity.get(BytecodeLocations.insertAndGetId {
                    it[path] = this@findOrNew.path
                    it[runtime] = scope == LocationScope.RUNTIME
                })
        }


    }

    var path by BytecodeLocations.path
    var runtime by BytecodeLocations.runtime

    val outdated by BytecodeLocationEntity optionalReferrersOn BytecodeLocations.outdated
    val classes: SizedIterable<ClassEntity> by ClassEntity referrersOn Classes.locationId
}

class SymbolEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SymbolEntity>(Symbols)

    var name by Symbols.name
    var hash by Symbols.hash
}

class OuterClassEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<OuterClassEntity>(OuterClasses)

    var name: String? by OuterClasses.name
    var outerClass by ClassEntity referencedOn OuterClasses.classId
}

class ClassEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClassEntity>(Classes) {
        val eagerColumns = listOf(ClassEntity::superClass, ClassEntity::packageRef, ClassEntity::name)
    }

    var access by Classes.access
    var name by SymbolEntity referencedOn Classes.name
    var signature by Classes.signature

    var superClass by SymbolEntity optionalReferencedOn Classes.superClass
    var interfaces: SizedIterable<SymbolEntity> by SymbolEntity via ClassInterfaces
    var innerClasses: SizedIterable<SymbolEntity> by SymbolEntity via ClassInnerClasses

    var bytecode by Classes.bytecode
    var annotations by Classes.annotations

    val methods by MethodEntity referrersOn Methods.classId

    var locationRef by BytecodeLocationEntity referencedOn Classes.locationId
    var packageRef by SymbolEntity referencedOn Classes.packageId
    var outerClass by OuterClassEntity optionalReferencedOn Classes.outerClass

    var outerMethod by MethodEntity optionalReferencedOn Classes.outerMethod
}

class MethodEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<MethodEntity>(Methods) {
        val eagerColumns = listOf(MethodEntity::name)
    }

    var access by Methods.access
    var name by Methods.name
    var signature by Methods.signature
    var desc by Methods.desc

    var returnClass by SymbolEntity optionalReferencedOn Methods.returnClass
    var declaringClass: ClassEntity by ClassEntity referencedOn Methods.classId

    var annotations by Methods.annotations
}

class FieldEntity(id: EntityID<Long>) : LongEntity(id) {

    companion object : LongEntityClass<FieldEntity>(Fields) {
        val eagerColumns = listOf(FieldEntity::name)
    }

    var access by Fields.access
    var name by Fields.name
    var signature by Fields.signature

    var fieldClass by SymbolEntity referencedOn Fields.fieldClass

    var declaringClass by ClassEntity referencedOn Fields.classId
    var annotations by Fields.annotations
}

class MethodParameterEntity(id: EntityID<Long>) : LongEntity(id) {

    companion object : LongEntityClass<MethodParameterEntity>(MethodParameters) {
        val eagerColumns = listOf(FieldEntity::name)
    }

    var name by MethodParameters.name
    var access by MethodParameters.access
    var index by MethodParameters.index

    var parameterClass by SymbolEntity referencedOn MethodParameters.parameterClass
    var method by MethodEntity referencedOn MethodParameters.methodId
    var annotations by MethodParameters.annotations
}

val String.longHash: Long
    get() {
        var h = 1125899906842597L // prime
        val len = length
        for (i in 0 until len) {
            h = 31 * h + this[i].code.toLong()
        }
        return h
    }