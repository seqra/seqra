package org.opentaint.ir.go.codegen

import org.opentaint.ir.go.type.*

/**
 * Formats GoIR types as valid Go type expressions.
 * Unlike [GoIRType.displayName], this produces fully qualified, compilable type strings
 * suitable for code generation.
 */
object TypeFormatter {

    fun format(type: GoIRType): String = when (type) {
        is GoIRBasicType -> formatBasic(type)
        is GoIRPointerType -> "*${format(type.elem)}"
        is GoIRArrayType -> "[${type.length}]${format(type.elem)}"
        is GoIRSliceType -> "[]${format(type.elem)}"
        is GoIRMapType -> "map[${format(type.key)}]${format(type.value)}"
        is GoIRChanType -> formatChan(type)
        is GoIRStructType -> formatStruct(type)
        is GoIRInterfaceType -> formatInterface(type)
        is GoIRFuncType -> formatFunc(type)
        is GoIRNamedTypeRef -> formatNamedRef(type)
        is GoIRTypeParamType -> type.name
        is GoIRTupleType -> "(${type.elements.joinToString(", ") { format(it) }})"
        is GoIRUnsafePointerType -> "unsafe.Pointer"
    }

    private fun formatBasic(type: GoIRBasicType): String {
        // Untyped types map to their default typed counterparts in codegen
        return when (type.kind) {
            GoIRBasicTypeKind.UNTYPED_BOOL -> "bool"
            GoIRBasicTypeKind.UNTYPED_INT -> "int"
            GoIRBasicTypeKind.UNTYPED_RUNE -> "rune"
            GoIRBasicTypeKind.UNTYPED_FLOAT -> "float64"
            GoIRBasicTypeKind.UNTYPED_COMPLEX -> "complex128"
            GoIRBasicTypeKind.UNTYPED_STRING -> "string"
            GoIRBasicTypeKind.UNTYPED_NIL -> "interface{}"
            else -> type.kind.goName
        }
    }

    private fun formatChan(type: GoIRChanType): String = when (type.direction) {
        GoIRChanDirection.SEND_RECV -> "chan ${format(type.elem)}"
        GoIRChanDirection.SEND_ONLY -> "chan<- ${format(type.elem)}"
        GoIRChanDirection.RECV_ONLY -> "<-chan ${format(type.elem)}"
    }

    private fun formatStruct(type: GoIRStructType): String {
        // If the struct has a named type, use its name
        val named = type.namedType
        if (named != null) return formatNamedTypeName(named.name, named.pkg?.importPath)

        // Anonymous struct
        if (type.fields.isEmpty()) return "struct{}"
        val fields = type.fields.joinToString("; ") { f ->
            if (f.isEmbedded) format(f.type)
            else "${f.name} ${format(f.type)}"
        }
        return "struct{ $fields }"
    }

    private fun formatInterface(type: GoIRInterfaceType): String {
        val named = type.namedType
        if (named != null) return formatNamedTypeName(named.name, named.pkg?.importPath)

        // Empty interface
        if (type.methods.isEmpty() && type.embeds.isEmpty()) return "interface{}"

        // Anonymous interface
        val parts = mutableListOf<String>()
        type.embeds.forEach { parts.add(format(it)) }
        type.methods.forEach { m ->
            parts.add("${m.name}${formatFuncSignature(m.signature)}")
        }
        return "interface{ ${parts.joinToString("; ")} }"
    }

    private fun formatFunc(type: GoIRFuncType): String {
        return "func${formatFuncSignature(type)}"
    }

    /**
     * Formats just the (params) results part of a function signature.
     */
    fun formatFuncSignature(type: GoIRFuncType): String {
        val params = type.params.mapIndexed { i, p ->
            if (type.isVariadic && i == type.params.lastIndex) {
                // Last param of variadic is a slice type; emit as ...ElemType
                val sliceType = p as? GoIRSliceType
                if (sliceType != null) "...${format(sliceType.elem)}"
                else format(p)
            } else {
                format(p)
            }
        }.joinToString(", ")

        val results = when (type.results.size) {
            0 -> ""
            1 -> " ${format(type.results[0])}"
            else -> " (${type.results.joinToString(", ") { format(it) }})"
        }
        return "($params)$results"
    }

    private fun formatNamedRef(type: GoIRNamedTypeRef): String {
        val base = formatNamedTypeName(type.namedType.name, type.namedType.pkg?.importPath)
        return if (type.typeArgs.isEmpty()) base
        else "$base[${type.typeArgs.joinToString(", ") { format(it) }}]"
    }

    /**
     * For codegen, we use just the short name of the type since we generate
     * single-package programs. If the type comes from an imported package,
     * we use pkg.Name format.
     */
    private fun formatNamedTypeName(name: String, importPath: String?): String {
        // For now, just return the short name.
        // Cross-package codegen would need import management.
        return name
    }

    /**
     * Returns the zero value expression for the given type, used for variable declarations.
     */
    fun zeroValue(type: GoIRType): String = when (type) {
        is GoIRBasicType -> when {
            type.kind == GoIRBasicTypeKind.BOOL || type.kind == GoIRBasicTypeKind.UNTYPED_BOOL -> "false"
            type.kind == GoIRBasicTypeKind.STRING || type.kind == GoIRBasicTypeKind.UNTYPED_STRING -> "\"\""
            type.kind.goName.startsWith("float") || type.kind == GoIRBasicTypeKind.UNTYPED_FLOAT -> "0.0"
            type.kind.goName.startsWith("complex") || type.kind == GoIRBasicTypeKind.UNTYPED_COMPLEX -> "0"
            else -> "0"
        }
        is GoIRPointerType, is GoIRSliceType, is GoIRMapType, is GoIRChanType,
        is GoIRFuncType, is GoIRInterfaceType, is GoIRUnsafePointerType -> "nil"
        is GoIRStructType -> "${format(type)}{}"
        is GoIRArrayType -> "${format(type)}{}"
        is GoIRNamedTypeRef -> {
            // Check if underlying is a primitive
            val underlying = type.namedType.underlying
            if (underlying != null) zeroValue(underlying)
            else "${format(type)}{}"
        }
        is GoIRTupleType -> "" // tuples don't have zero values in Go
        is GoIRTypeParamType -> "*new(${type.name})" // zero value for type params
    }
}
