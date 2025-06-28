package org.opentaint.ir.api.common

interface CommonClass {
    val name: String
}

interface CommonField {
    val enclosingClass: CommonClass?
    val name: String
    val type: CommonTypeName
}
