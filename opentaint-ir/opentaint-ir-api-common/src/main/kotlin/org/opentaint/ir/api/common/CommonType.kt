package org.opentaint.ir.api.common

interface CommonType {
    val typeName: String

    /**
     * Nullability of a type:
     * - `true` for `T?` (nullable Kotlin type),
     * - `false` for `T` (non-nullable Kotlin type)
     * - `null` for `T!` (platform type, means `T or T?`)
     */
    val nullable: Boolean?
}

interface CommonTypeName {
    val typeName: String
}

interface CommonRefType : CommonType {
    val jIRClass: CommonClass
}

interface CommonArrayType : CommonType {
    val elementType: CommonType
    val dimensions: Int
}

interface CommonClassType : CommonRefType
