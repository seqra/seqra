package org.opentaint.ir.analysis.util

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.Project
import org.opentaint.ir.api.common.cfg.CommonArgument
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonThis
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.taint.configuration.ConstantValue

/**
 * Extensions for analysis.
 */
interface Traits<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val @UnsafeVariance Method.thisInstance: CommonThis
    val @UnsafeVariance Method.isConstructor: Boolean

    fun CommonExpr.toPathOrNull(): AccessPath?
    fun CommonValue.toPathOrNull(): AccessPath?
    fun CommonValue.toPath(): AccessPath

    val CommonCallExpr.callee: Method

    fun Project.getArgument(param: CommonMethodParameter): CommonArgument?
    fun Project.getArgumentsOf(method: @UnsafeVariance Method): List<CommonArgument>

    fun CommonValue.isConstant(): Boolean
    fun CommonValue.eqConstant(constant: ConstantValue): Boolean
    fun CommonValue.ltConstant(constant: ConstantValue): Boolean
    fun CommonValue.gtConstant(constant: ConstantValue): Boolean
    fun CommonValue.matches(pattern: String): Boolean

    fun CommonExpr.toPaths(): List<AccessPath> = listOfNotNull(toPathOrNull())
}
