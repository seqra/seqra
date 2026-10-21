package org.opentaint.dataflow.python.alias

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.alias.applyAlias
import org.opentaint.dataflow.ap.ifds.analysis.alias.forEachAliasAtStatement
import org.opentaint.dataflow.ap.ifds.analysis.alias.forEachAliasAtStatementAmongBases
import org.opentaint.dataflow.ap.ifds.analysis.alias.forEachHeapAliasBeforeStatement
import org.opentaint.dataflow.python.PIRFlowFunctionUtils
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.mkFieldAccessor
import org.opentaint.ir.api.python.locals
import org.opentaint.ir.api.python.PIRInstruction

fun PIRLocalAliasAnalysis.forEachAliasBeforeStatement(
    statement: PIRInstruction,
    fact: FinalFactAp,
    body: (FinalFactAp) -> Unit,
) {
    val base = fact.base as? AccessPathBase.LocalVar ?: return
    val aliases = findAlias(base, statement) ?: return
    aliases.mapNotNull { it.relevantApInfo() }
        .forEach { applyAlias(fact, it, AliasAccessor::apAccessor, body) }
}

fun PIRLocalAliasAnalysis.forEachAliasBeforeStatement(
    statement: PIRInstruction,
    fact: InitialFactAp,
    body: (InitialFactAp) -> Unit,
) = forEachAliasAtStatement(statement, fact, AliasApInfo::relevantApInfo, AliasAccessor::apAccessor, body)

fun PIRLocalAliasAnalysis.forEachAliasBeforeCallStatement(
    statement: PIRInstruction,
    fact: FinalFactAp,
    body: (FinalFactAp) -> Unit,
) {
    forEachAliasBeforeStatement(statement, fact, body)
    forEachHeapAliasBeforeStatement(
        statement, fact, Accessor::aliasAccessor, AliasApInfo::relevantApInfo, AliasAccessor::apAccessor, body
    )
}

fun PIRLocalAliasAnalysis.forEachAliasAfterStatement(
    statement: PIRInstruction,
    fact: FinalFactAp,
    body: (FinalFactAp) -> Unit,
) {
    val base = fact.base as? AccessPathBase.LocalVar ?: return
    val aliases = findAliasAfterStatement(base, statement) ?: return
    aliases.mapNotNull { it.relevantApInfo() }
        .forEach { applyAlias(fact, it, AliasAccessor::apAccessor, body) }
}

fun PIRLocalAliasAnalysis.forEachPossibleAliasBeforeStatement(
    statement: PIRInstruction,
    fact: InitialFactAp,
    body: (InitialFactAp) -> Unit,
) {
    val localVars = statement.locals
        .map(PIRFlowFunctionUtils::accessPathBase)
        .filterIsInstance<AccessPathBase.LocalVar>()

    forEachAliasAtStatementAmongBases(statement, fact, localVars, body)
}

private fun PIRLocalAliasAnalysis.forEachAliasAtStatementAmongBases(
    statement: PIRInstruction,
    fact: InitialFactAp,
    bases: List<AccessPathBase.LocalVar>,
    body: (InitialFactAp) -> Unit,
) = forEachAliasAtStatementAmongBases(
    statement, fact, bases, AliasApInfo::relevantApInfo, AliasAccessor::apAccessor, body
)

private fun AliasApInfo.relevantApInfo(): AliasApInfo? =
    takeIf { it.base !is AccessPathBase.Constant }

fun AliasAccessor.apAccessor(): Accessor = when (this) {
    is AliasAccessor.Array -> ElementAccessor
    is AliasAccessor.Field -> mkFieldAccessor(name)
}

private fun Accessor.aliasAccessor(): AliasAccessor? = when (this) {
    is ElementAccessor -> AliasAccessor.Array
    is FieldAccessor -> AliasAccessor.Field(fieldName)
    else -> null
}
