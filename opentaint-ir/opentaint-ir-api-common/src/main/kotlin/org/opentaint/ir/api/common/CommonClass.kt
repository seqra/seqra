package org.opentaint.ir.api.common

interface CommonClass {
    val project: CommonProject
    val name: String
}

interface CommonField {
    val enclosingClass: CommonClass?
    val name: String
    val type: CommonTypeName
}
