package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.alias.applyAlias
import org.opentaint.dataflow.ap.ifds.analysis.alias.forEachAliasAtStatement
import org.opentaint.dataflow.ap.ifds.analysis.alias.forEachAliasAtStatementAmongBases
import org.opentaint.dataflow.ap.ifds.analysis.alias.forEachHeapAliasBeforeStatement
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasInfo
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.locals

fun JIRLocalAliasAnalysis.forEachAliasAtStatement(statement: JIRInst, fact: InitialFactAp, body: (InitialFactAp) -> Unit) =
    forEachAliasAtStatement(statement, fact, AliasInfo::relevantApInfo, AliasAccessor::apAccessor, body)

fun JIRLocalAliasAnalysis.forEachAliasAfterCallStatement(statement: JIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) {
    val base = fact.base as? AccessPathBase.LocalVar ?: return
    aliasesPersistedThroughCall(base, statement)
        .forEach { alias -> applyAlias(fact, alias, AliasAccessor::apAccessor, body) }
}

/** Callee outputs use only alias paths that hold both before and after their call. */
fun JIRLocalAliasAnalysis.aliasesPersistedThroughCall(base: AccessPathBase.LocalVar, statement: JIRInst): List<AliasApInfo> {
    val before = findAlias(base, statement) ?: return emptyList()
    val after = findAliasAfterStatement(base, statement)?.toSet() ?: return emptyList()
    return before.filter { it in after }.mapNotNull { it.relevantApInfo() }
}

fun JIRLocalAliasAnalysis.forEachHeapAliasBeforeStatement(statement: JIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) =
    forEachHeapAliasBeforeStatement(statement, fact, Accessor::aliasAccessor, AliasInfo::relevantApInfo, AliasAccessor::apAccessor, body)

fun JIRLocalAliasAnalysis.forEachPossibleAliasAtStatement(
    statement: JIRInst,
    fact: InitialFactAp,
    body: (InitialFactAp) -> Unit
) {
    val localVars = statement.locals
        .map(MethodFlowFunctionUtils::accessPathBase)
        .filterIsInstance<AccessPathBase.LocalVar>()

    return forEachAliasAtStatementAmongBases(statement, fact, localVars, body)
}

fun JIRLocalAliasAnalysis.forEachAliasAtStatementAmongBases(
    statement: JIRInst,
    fact: InitialFactAp,
    bases: List<AccessPathBase.LocalVar>,
    body: (InitialFactAp) -> Unit
) = forEachAliasAtStatementAmongBases(
    statement, fact, bases, AliasInfo::relevantApInfo, AliasAccessor::apAccessor, body
)

fun JIRLocalAliasAnalysis.forEachAliasPathAtStatement(
    statement: JIRInst,
    base: AccessPathBase,
    body: (AccessPathBase, List<Accessor>) -> Unit
) {
    val local = base as? AccessPathBase.LocalVar ?: return
    val aliases = findAlias(local, statement) ?: return
    aliases.forEach { alias ->
        val info = alias.relevantApInfo() ?: return@forEach
        body(info.base, info.accessors.map { it.apAccessor() })
    }
}

private fun AliasInfo.relevantApInfo(): AliasApInfo? =
    (this as? AliasApInfo)?.takeIf { it.base !is AccessPathBase.Constant }

fun AliasAccessor.apAccessor(): Accessor = when (this) {
    is AliasAccessor.Array -> ElementAccessor
    is AliasAccessor.Field -> FieldAccessor(className, fieldName, fieldType)
    is AliasAccessor.Static -> ClassStaticAccessor(typeName)
}

fun Accessor.aliasAccessor(): AliasAccessor? = when (this) {
    is ElementAccessor -> AliasAccessor.Array
    is FieldAccessor -> AliasAccessor.Field(className, fieldName, fieldType)
    is ClassStaticAccessor -> AliasAccessor.Static(typeName)
    else -> null
}
