package org.opentaint.ir.impl.storage

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
    AWAITING_INDEXING,
    PROCESSED,
    OUTDATED
}