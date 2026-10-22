package org.opentaint.ir.go.type

import org.opentaint.ir.api.common.CommonTypeName
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.api.GoIRProgram

/**
 * Base sealed interface for all Go IR types.
 * Enables exhaustive `when` matching.
 */
sealed interface GoIRType: CommonTypeName {
    /** Human-readable type string (e.g., "*int", "map[string]int") */
    val displayName: String

    override val typeName: String get() = displayName
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

data class NamedTypeRef(
    val refTypePkg: String,
    val refTypeName: String,
) {
    lateinit var program: GoIRProgram
    val fullRefName: String get() = "$refTypePkg.$refTypeName"

    val namedType: GoIRNamedType
        get() = program.lookup.namedType(this)
            ?: error("Can't find namedType for $fullRefName")

    override fun toString(): String = "ref($fullRefName)"
}

data class GoIRStructType(
    val fields: List<GoIRStructField>,
    val structTypeRef: NamedTypeRef?,
) : GoIRType {
    val namedType: GoIRNamedType? get() = structTypeRef?.namedType

    override val displayName: String get() =
        structTypeRef?.fullRefName ?: "struct{${fields.joinToString("; ") { "${it.name} ${it.type.displayName}" }}}"
}

data class GoIRStructField(
    val name: String,
    val type: GoIRType,
    val isEmbedded: Boolean,
    val tag: String,
)

sealed interface GoIRInterfaceType: GoIRType

data class GoIRAnonymousInterfaceTypeRef(
    val id: Int,
) : GoIRInterfaceType {
    lateinit var program: GoIRProgram

    val interfaceType: GoIRAnonymousInterfaceType
        get() = program.anonymousInterfaces[id]
            ?: error("Anonymous interface for $id not found")

    override val displayName: String get() = interfaceType.displayName
}

data class GoIRAnonymousInterfaceType(
    val id: Int,
    val methods: List<GoIRInterfaceMethodSig>,
    val embeds: List<GoIRType>,
) : GoIRInterfaceType {
    override val displayName: String get() = "interface{...}"
}

data class GoIRNamedInterfaceType(
    val methods: List<GoIRInterfaceMethodSig>,
    val embeds: List<GoIRType>,
    val interfaceTypeRef: NamedTypeRef,
) : GoIRInterfaceType {
    val namedType: GoIRNamedType get() = interfaceTypeRef.namedType
    override val displayName: String get() = namedType.fullName
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
    val typeRef: NamedTypeRef,
    val typeArgs: List<GoIRType>,
) : GoIRType {
    override val displayName: String get() = toString()

    val namedType: GoIRNamedType get() = typeRef.namedType

    override fun toString(): String = if (typeArgs.isEmpty()) {
        typeRef.fullRefName
    } else {
        "${typeRef.fullRefName}[${typeArgs.joinToString(", ") { it.displayName }}]"
    }
}

data class GoIRTypeParamRef(
    val name: String,
    val paramIndex: Int,
) : GoIRType {
    override val displayName: String get() = name
}

data class GoIRTypeParamType(
    val ref: GoIRTypeParamRef,
    val constraint: GoIRType,
) : GoIRType {
    val name: String get() = ref.name
    val paramIndex: Int get() = ref.paramIndex

    override val displayName: String get() = ref.displayName
}

data class GoIRTupleType(val elements: List<GoIRType>) : GoIRType {
    override val displayName: String get() =
        "(${elements.joinToString(", ") { it.displayName }})"
}

data object GoIRUnsafePointerType : GoIRType {
    override val displayName: String get() = "unsafe.Pointer"
}
