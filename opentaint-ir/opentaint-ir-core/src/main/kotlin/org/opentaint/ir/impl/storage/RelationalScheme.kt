package org.opentaint.ir.impl.storage

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption


object Classpaths : LongIdTable()

//object ClasspathLocations : LongIdTable() {
//
//    val classpathId = reference("classpath_id", Classpaths.id, onDelete = ReferenceOption.CASCADE)
//    val locationId = reference("location_id", BytecodeLocations.id)
//
//}

object BytecodeLocations : LongIdTable() {
    val path = varchar("path", length = 1024)
    val hash = varchar("hash", length = 1024)
    val runtime = bool("runtime").default(false)
    val state = enumeration<LocationState>("state").default(LocationState.INITIAL)
    val updated = reference("updated_id", BytecodeLocations.id).nullable()
}

object Symbols : LongIdTable() {
    val name = varchar("name", length = 256)
    val hash = long("hash")

    init {
        uniqueIndex(name)
    }

}

object ClassHierarchies : LongIdTable() {

    val classId = reference("class_id", Classes.id, onDelete = ReferenceOption.CASCADE)
    val superRef = reference("super_id", Symbols.id)
    val isClassRef = bool("is_class_ref")

}

object ClassInnerClasses : LongIdTable() {

    val classId = reference("class_id", Classes.id, onDelete = ReferenceOption.CASCADE)
    val innerClassId = reference("inner_class_id", Symbols.id)
}

object OuterClasses : LongIdTable() {
    val outerClassName = reference("outer_class_name_id", Symbols.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 256).nullable()
    val methodName = text("method_name").nullable()
    val methodDesc = text("method_desc").nullable()
}

object Classes : LongIdTable() {
    val access = integer("access")
    val name = reference("name", Symbols.id)
    val signature = text("signature").nullable()

    val bytecode = blob("bytecode")

    val locationId = reference("location_id", BytecodeLocations.id, onDelete = ReferenceOption.CASCADE)
    val packageId = reference("package_id", Symbols.id, onDelete = ReferenceOption.CASCADE)
    val outerClass = reference("outer_class", OuterClasses.id, onDelete = ReferenceOption.CASCADE).nullable()
    val outerMethod = reference("outer_method", Methods.id, onDelete = ReferenceOption.CASCADE).nullable()
}

object Methods : LongIdTable() {
    val access = integer("access")
    val name = reference("name", Symbols.id)
    val signature = text("signature").nullable()
    val desc = text("desc").nullable()

    val returnClass = reference("return_class", Symbols.id).nullable()

    val classId = reference("class_id", Classes.id, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex(classId, name, desc)
    }

}

object Fields : LongIdTable() {
    val access = integer("access")
    val name = reference("name", Symbols.id)
    val signature = text("signature").nullable()

    val fieldClass = reference("field_class", Symbols.id)

    val classId = reference("class_id", Classes.id, onDelete = ReferenceOption.CASCADE)

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
}

object Annotations : LongIdTable() {
    val name = reference("annotation_name", Symbols.id)
    val visible = bool("visible")

    val parentAnnotation = reference("parent_annotation", Annotations.id).nullable()

    val classRef = reference("class_id", Classes.id).nullable()
    val methodRef = reference("method_id", Methods.id).nullable()
    val fieldRef = reference("field_id", Fields.id).nullable()
    val parameterRef = reference("param_id", MethodParameters.id).nullable()
}

object AnnotationValues : LongIdTable() {
    val annotation = reference("annotation_id", Annotations.id)
    val name = varchar("name", 256)
    val refValue = reference("ref_annotation_id", Annotations.id).nullable()
    val kind = enumeration<AnnotationValueKind>("kind").nullable()
    val value = text("value").nullable()

    val classSymbol = reference("class_symbol", Symbols.id).nullable()
    val enumValue = reference("enum_value", Symbols.id).nullable()
}

//private val annotationValues = AnnotationValueKind.values().associateBy { it.value }
enum class AnnotationValueKind {
    BOOLEAN,
    BYTE,
    CHAR,
    SHORT,
    INT,
    FLOAT,
    LONG,
    DOUBLE,
    STRING;

    companion object {
        fun serialize(value: Any): String {
            return when (value) {
                is String -> value
                is Short -> value.toString()
                is Char -> value.toString()
                is Long -> value.toString()
                is Int -> value.toString()
                is Float -> value.toString()
                is Double -> value.toString()
                is Byte -> value.toString()
                is Boolean -> value.toString()
                else -> throw IllegalStateException("Unknown type ${value.javaClass}")
            }
        }
    }

}


enum class LocationState {
    INITIAL,
    PROCESSED,
    OUTDATED
}