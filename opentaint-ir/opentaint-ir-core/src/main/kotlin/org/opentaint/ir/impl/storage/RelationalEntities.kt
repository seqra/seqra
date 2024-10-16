package org.opentaint.ir.impl.storage

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedIterable

class BytecodeLocationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BytecodeLocationEntity>(BytecodeLocations)

    var path by BytecodeLocations.path
    var runtime by BytecodeLocations.runtime

    val outdated by BytecodeLocationEntity optionalReferrersOn BytecodeLocations.outdated
    val classes: SizedIterable<ClassEntity> by ClassEntity referrersOn Classes.locationId
}


class PackageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PackageEntity>(Packages)

    var name by Packages.name

    val classes by ClassEntity referrersOn Classes.packageId
}

class ClassNameEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClassNameEntity>(ClassNames)

    var name by ClassNames.name
}

class OuterClassEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<OuterClassEntity>(OuterClasses)

    var name: String? by OuterClasses.name
    var outerClass by ClassEntity referencedOn OuterClasses.classId
}

class ClassEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClassEntity>(Classes)

    var access by Classes.access
    var name by ClassNameEntity referencedOn Classes.name
    var signature by Classes.signature

    var superClass by ClassNameEntity optionalReferencedOn Classes.superClass
    var interfaces: SizedIterable<ClassNameEntity> by ClassNameEntity via ClassInterfaces
    var innerClasses: SizedIterable<ClassNameEntity> by ClassNameEntity via ClassInnerClasses

    var bytecode by Classes.bytecode
    var annotations by Classes.annotations

    val methods by MethodEntity referrersOn Methods.classId

    var locationRef by BytecodeLocationEntity referencedOn Classes.locationId
    var packageRef by PackageEntity referencedOn Classes.packageId
    var outerClass by OuterClassEntity optionalReferencedOn Classes.outerClass

    var outerMethod by MethodEntity optionalReferencedOn Classes.outerMethod
}

class MethodEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<MethodEntity>(Methods)

    var access by Methods.access
    var name by Methods.name
    var signature by Methods.signature
    var desc by Methods.desc

    var returnClass by ClassNameEntity optionalReferencedOn Methods.returnClass
    var declaringClass: ClassEntity by ClassEntity referencedOn Methods.classId

    var annotations by Methods.annotations
}

class FieldEntity(id: EntityID<Long>) : LongEntity(id) {

    companion object : LongEntityClass<FieldEntity>(Fields)

    var access by Fields.access
    var name by Fields.name
    var signature by Fields.signature

    var fieldClass by ClassNameEntity referencedOn Fields.fieldClass

    var declaringClass by ClassEntity referencedOn Fields.classId
    var annotations by Fields.annotations
}

class MethodParameterEntity(id: EntityID<Long>) : LongEntity(id) {

    companion object : LongEntityClass<MethodParameterEntity>(MethodParameters)

    var name by MethodParameters.name
    var access by MethodParameters.access
    var index by MethodParameters.index

    var parameterClass by ClassNameEntity referencedOn MethodParameters.parameterClass
    var method by MethodEntity referencedOn MethodParameters.methodId
    var annotations by MethodParameters.annotations
}

