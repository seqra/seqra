package org.opentaint.ir.api.common.cfg

import org.opentaint.ir.api.common.CommonField

interface CommonValue : CommonExpr

interface CommonThis : CommonValue

interface CommonArgument : CommonValue {
    val index: Int
    val name: String
}

interface CommonFieldRef : CommonValue {
    val instance: CommonValue?
    val classField: CommonField
}

interface CommonArrayAccess : CommonValue {
    val array: CommonValue
    val index: CommonValue
}
