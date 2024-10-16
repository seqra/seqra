package org.opentaint.ir.impl.storage

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption


object Classpaths : IntIdTable()

object ClasspathLocations : IntIdTable() {

    val classpathId = reference("classpath_id", Classpaths.id, onDelete = ReferenceOption.CASCADE)
    val locationId = reference("location_id", BytecodeLocations.id)

}

object BytecodeLocations : IntIdTable() {
    val path = varchar("path", length = 1024)
    val runtime = bool("runtime").default(false)
    val outdated = reference("outdated_id", BytecodeLocations.id).nullable()
}

object Packages : IntIdTable() {
    val name = varchar("name", length = 256) // full package name
}

object Symbols : IntIdTable() {
    val name = varchar("name", length = 256)

    init {
        uniqueIndex(name)
    }

}

object ClassInterfaces : IntIdTable() {

    val classId = reference("class_id", Classes.id, onDelete = ReferenceOption.CASCADE)
    val interfaceId = reference("interface_id", Symbols.id)

}

object ClassInnerClasses : IntIdTable() {

    val classId = reference("class_id", Classes.id, onDelete = ReferenceOption.CASCADE)
    val innerClassId = reference("inner_class_id", Symbols.id)

}

object OuterClasses : IntIdTable() {

    val classId = reference("class_id", Classes.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 256).nullable()

}

object Classes : IntIdTable() {
    val access = integer("access")
    val name = reference("name", Symbols.id)
    val signature = text("signature").nullable()

    val superClass = reference("super_class", Symbols.id).nullable()

    val bytecode = binary("bytecode").nullable()
    val annotations = binary("annotation_data").nullable()

    val locationId = reference("location_id", BytecodeLocations.id, onDelete = ReferenceOption.CASCADE)
    val packageId = reference("package_id", Packages.id, onDelete = ReferenceOption.CASCADE)
    val outerClass = reference("outer_class", OuterClasses.id, onDelete = ReferenceOption.CASCADE).nullable()
    val outerMethod = reference("outer_method", Methods.id, onDelete = ReferenceOption.CASCADE).nullable()
}

object Methods : LongIdTable() {
    val access = integer("access")
    val name = varchar("name", length = 256)
    val signature = text("signature").nullable()
    val desc = text("desc").nullable()

    val returnClass = reference("return_class", Symbols.id).nullable()

    val classId = reference("class_id", Classes.id, onDelete = ReferenceOption.CASCADE)

    val annotations = binary("annotation_data").nullable()

    init {
        uniqueIndex(classId, name, desc)
    }

}

object Fields : LongIdTable() {
    val access = integer("access")
    val name = varchar("name", length = 256)
    val signature = text("signature").nullable()

    val fieldClass = reference("field_class", Symbols.id)

    val classId = reference("class_id", Classes.id, onDelete = ReferenceOption.CASCADE)
    val annotations = binary("annotation_data").nullable()

    init {
        uniqueIndex(classId, name)
    }

}

object MethodParameters : LongIdTable() {
    val access = integer("access")
    val index = integer("index")
    val name = varchar("name", length = 256).nullable()

    val parameterClass = reference("parameter_class", Symbols.id)

    val methodId = reference("method_id", Methods.id, onDelete = ReferenceOption.CASCADE)
    val annotations = binary("annotation_data").nullable()

}