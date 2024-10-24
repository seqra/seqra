package org.opentaint.ir.impl.storage

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.LocationType

class BytecodeLocationEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<BytecodeLocationEntity>(BytecodeLocations) {

        fun JIRByteCodeLocation.findOrNew(): BytecodeLocationEntity {
            return findOrNull() ?: BytecodeLocationEntity[BytecodeLocations.insertAndGetId {
                it[path] = this@findOrNew.path
                it[hash] = this@findOrNew.hash
                it[runtime] = type == LocationType.RUNTIME
            }]
        }

        fun JIRByteCodeLocation.findOrNull(): BytecodeLocationEntity? {
            return BytecodeLocationEntity.find { BytecodeLocations.path eq path and (BytecodeLocations.hash eq hash) }.firstOrNull()
        }
    }

    var path by BytecodeLocations.path
    var hash by BytecodeLocations.hash
    var runtime by BytecodeLocations.runtime

    var updated by BytecodeLocationEntity optionalReferencedOn BytecodeLocations.updated
    val classes: SizedIterable<ClassEntity> by ClassEntity referrersOn Classes.locationId
}

class SymbolEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SymbolEntity>(Symbols)

    var name by Symbols.name
    var hash by Symbols.hash
}

class OuterClassEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<OuterClassEntity>(OuterClasses)

    var name: String? by OuterClasses.name
    var outerClass by ClassEntity referencedOn OuterClasses.classId
}

class ClassEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ClassEntity>(Classes) {
        val eagerColumns = listOf(ClassEntity::superClass, ClassEntity::packageRef, ClassEntity::name)
    }

    var access by Classes.access
    var name by SymbolEntity referencedOn Classes.name
    var signature by Classes.signature

    var superClass by SymbolEntity optionalReferencedOn Classes.superClass
    var interfaces: SizedIterable<SymbolEntity> by SymbolEntity via ClassInterfaces
    var innerClasses: SizedIterable<SymbolEntity> by SymbolEntity via ClassInnerClasses

    var bytecode by Classes.bytecode
    val annotations: SizedIterable<AnnotationEntity> by AnnotationEntity optionalReferrersOn Annotations.classRef

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
    var name by SymbolEntity referencedOn Fields.name
    var signature by Methods.signature
    var desc by Methods.desc

    var returnClass by SymbolEntity optionalReferencedOn Methods.returnClass
    var declaringClass: ClassEntity by ClassEntity referencedOn Methods.classId

    val annotations: SizedIterable<AnnotationEntity> by AnnotationEntity optionalReferrersOn Annotations.methodRef
}

class FieldEntity(id: EntityID<Long>) : LongEntity(id) {

    companion object : LongEntityClass<FieldEntity>(Fields) {
        val eagerColumns = listOf(FieldEntity::name)
    }

    var access by Fields.access
    var name by SymbolEntity referencedOn Fields.name
    var signature by Fields.signature

    var fieldClass by SymbolEntity referencedOn Fields.fieldClass

    var declaringClass by ClassEntity referencedOn Fields.classId
    val annotations: SizedIterable<AnnotationEntity> by AnnotationEntity optionalReferrersOn Annotations.fieldRef
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
    val annotations: SizedIterable<AnnotationEntity> by AnnotationEntity optionalReferrersOn Annotations.parameterRef
}

class AnnotationEntity(id: EntityID<Long>) : LongEntity(id) {

    companion object : LongEntityClass<AnnotationEntity>(Annotations) {
        val eagerColumns = listOf(FieldEntity::name)
    }

    var annotationClassName by SymbolEntity referencedOn Annotations.name
    var visible by Annotations.visible

    var parentAnnotation by AnnotationEntity optionalReferencedOn Annotations.parentAnnotation

    var classRef by ClassEntity optionalReferencedOn Annotations.classRef
    var fieldRef by FieldEntity optionalReferencedOn Annotations.fieldRef
    var methodRef by ClassEntity optionalReferencedOn Annotations.methodRef
    var methodParameterRef by ClassEntity optionalReferencedOn Annotations.parameterRef

}

class AnnotationValueEntity(id: EntityID<Long>) : LongEntity(id) {

    companion object : LongEntityClass<AnnotationEntity>(AnnotationValues) {
        val eagerColumns = listOf(FieldEntity::name)
    }

    var kind by AnnotationValues.kind
    var valueName by AnnotationValues.name
    var refValue by AnnotationEntity optionalReferencedOn AnnotationValues.refValue

    var annotation by AnnotationEntity referencedOn AnnotationValues.annotation
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