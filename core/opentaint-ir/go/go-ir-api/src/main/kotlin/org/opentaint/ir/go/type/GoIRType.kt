package org.opentaint.ir.go.type

import org.opentaint.ir.go.api.GoIRNamedType

/**
 * Base sealed interface for all Go IR types.
 * Enables exhaustive `when` matching.
 */
sealed interface GoIRType {
    /** Human-readable type string (e.g., "*int", "map[string]int") */
    val displayName: String
}

data class GoIRBasicType(val kind: GoIRBasicTypeKind) : GoIRType {
    override val displayName: String get() = kind.goName
}

enum class GoIRBasicTypeKind(val goName: String) {
    BOOL("bool"),
    INT("int"), INT8("int8"), INT16("int16"), INT32("int32"), INT64("int64"),
    UINT("uint"), UINT8("uint8"), UINT16("uint16"), UINT32("uint32"), UINT64("uint64"),
    FLOAT32("float32"), FLOAT64("float64"),
    COMPLEX64("complex64"), COMPLEX128("complex128"),
    STRING("string"), UINTPTR("uintptr"),
    UNTYPED_BOOL("untyped bool"), UNTYPED_INT("untyped int"),
    UNTYPED_RUNE("untyped rune"), UNTYPED_FLOAT("untyped float"),
    UNTYPED_COMPLEX("untyped complex"), UNTYPED_STRING("untyped string"),
    UNTYPED_NIL("untyped nil"),
}

data class GoIRPointerType(val elem: GoIRType) : GoIRType {
    override val displayName: String get() = "*${elem.displayName}"
}

data class GoIRArrayType(val elem: GoIRType, val length: Long) : GoIRType {
    override val displayName: String get() = "[${length}]${elem.displayName}"
}

data class GoIRSliceType(val elem: GoIRType) : GoIRType {
    override val displayName: String get() = "[]${elem.displayName}"
}

data class GoIRMapType(val key: GoIRType, val value: GoIRType) : GoIRType {
    override val displayName: String get() = "map[${key.displayName}]${value.displayName}"
}

data class GoIRChanType(val elem: GoIRType, val direction: GoIRChanDirection) : GoIRType {
    override val displayName: String get() = when (direction) {
        GoIRChanDirection.SEND_RECV -> "chan ${elem.displayName}"
        GoIRChanDirection.SEND_ONLY -> "chan<- ${elem.displayName}"
        GoIRChanDirection.RECV_ONLY -> "<-chan ${elem.displayName}"
    }
}

data class GoIRStructType(
    val fields: List<GoIRStructField>,
    var namedType: GoIRNamedType?,
) : GoIRType {
    override val displayName: String get() =
        namedType?.fullName ?: "struct{${fields.joinToString("; ") { "${it.name} ${it.type.displayName}" }}}"
}

data class GoIRStructField(
    val name: String,
    val type: GoIRType,
    val isEmbedded: Boolean,
    val tag: String,
)

data class GoIRInterfaceType(
    val methods: List<GoIRInterfaceMethodSig>,
    val embeds: List<GoIRType>,
    var namedType: GoIRNamedType?,
) : GoIRType {
    override val displayName: String get() =
        namedType?.fullName ?: "interface{...}"
}

data class GoIRInterfaceMethodSig(
    val name: String,
    val signature: GoIRFuncType,
)

data class GoIRFuncType(
    val params: List<GoIRType>,
    val results: List<GoIRType>,
    val isVariadic: Boolean,
    val recv: GoIRType?,
) : GoIRType {
    override val displayName: String get() {
        val p = params.joinToString(", ") { it.displayName }
        val r = when (results.size) {
            0 -> ""
            1 -> " ${results[0].displayName}"
            else -> " (${results.joinToString(", ") { it.displayName }})"
        }
        return "func($p)$r"
    }
}

data class GoIRNamedTypeRef(
    var namedType: GoIRNamedType,
    var typeArgs: List<GoIRType>,
) : GoIRType {
    override val displayName: String get() =
        if (typeArgs.isEmpty()) namedType.fullName
        else "${namedType.fullName}[${typeArgs.joinToString(", ") { it.displayName }}]"
}

data class GoIRTypeParamType(
    val name: String,
    val paramIndex: Int,
    val constraint: GoIRType,
) : GoIRType {
    override val displayName: String get() = name
}

data class GoIRTupleType(val elements: List<GoIRType>) : GoIRType {
    override val displayName: String get() =
        "(${elements.joinToString(", ") { it.displayName }})"
}

data object GoIRUnsafePointerType : GoIRType {
    override val displayName: String get() = "unsafe.Pointer"
}
