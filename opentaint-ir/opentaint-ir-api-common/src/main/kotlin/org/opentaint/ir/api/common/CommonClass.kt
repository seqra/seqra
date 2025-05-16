package org.opentaint.ir.api.common

interface CommonClass {
    val project: Project
    val name: String
    val simpleName: String
}

interface CommonClassField {
    val name: String
    val type: CommonTypeName
    val signature: String?
    val enclosingClass: CommonClass
}
