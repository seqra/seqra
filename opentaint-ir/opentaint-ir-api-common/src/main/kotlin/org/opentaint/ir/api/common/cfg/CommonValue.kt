package org.opentaint.ir.api.common.cfg

interface CommonValue : CommonExpr

interface CommonThis : CommonValue

interface CommonArgument : CommonValue

interface CommonFieldRef : CommonValue {
    val instance: CommonValue? // null for static fields
    // val classField: CommonField
}

interface CommonArrayAccess : CommonValue {
    val array: CommonValue
    val index: CommonValue
}
