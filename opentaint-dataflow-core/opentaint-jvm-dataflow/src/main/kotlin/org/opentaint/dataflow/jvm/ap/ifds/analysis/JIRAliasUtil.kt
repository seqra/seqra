package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasInfo
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.locals

fun JIRLocalAliasAnalysis.forEachAliasAtStatement(statement: JIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) {
    val base = fact.base as? AccessPathBase.LocalVar ?: return
    val aliases = findAlias(base, statement) ?: return
    aliases.filterNot { alias -> alias.base is AccessPathBase.Constant }
        .forEach { alias -> applyAlias(fact, alias, body) }
}

fun JIRLocalAliasAnalysis.forEachAliasAfterStatement(statement: JIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) {
    val base = fact.base as? AccessPathBase.LocalVar ?: return
    val aliases = findAliasAfterStatement(base, statement) ?: return
    aliases.filterNot { alias -> alias.base is AccessPathBase.Constant }
        .forEach { alias -> applyAlias(fact, alias, body) }
}

fun JIRLocalAliasAnalysis.forEachAliasAfterCallStatement(statement: JIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) {
    val base = fact.base as? AccessPathBase.LocalVar ?: return
    val aliasesBefore = findAlias(base, statement) ?: return
    val aliasesAfter = findAliasAfterStatement(base, statement)?.toSet() ?: return
    val aliasesPersistedThroughCall = aliasesBefore.filter { it in aliasesAfter }

    aliasesPersistedThroughCall.filterNot { alias -> alias.base is AccessPathBase.Constant }
        .forEach { alias -> applyAlias(fact, alias, body) }
}

private fun applyAlias(fact: FinalFactAp, alias: AliasInfo, body: (FinalFactAp) -> Unit) {
    val result = alias.accessors.foldRight(fact.rebase(alias.base)) { accessor, f ->
        val apAccessor = accessor.apAccessor()
        f.prependAccessor(apAccessor)
    }

    body(result)
}

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
) {
    bases.forEach { base ->
        val aliases = findAlias(base, statement) ?: return@forEach
        aliases.filterNot { alias -> alias.base is AccessPathBase.Constant }
            .forEach { alias -> applyAlias(fact, base, alias, body) }
    }
}

private fun applyAlias(fact: InitialFactAp, newBase: AccessPathBase.LocalVar, alias: AliasInfo, body: (InitialFactAp) -> Unit) {
    if (alias.base != fact.base) {
        return
    }

    val result = alias.accessors.fold(fact.rebase(newBase)) { f, accessor ->
        val apAccessor = accessor.apAccessor()
        f.readAccessor(apAccessor) ?: return
    }

    body(result)
}

private fun AliasAccessor.apAccessor(): Accessor = when (this) {
    is AliasAccessor.Array -> ElementAccessor
    is AliasAccessor.Field -> FieldAccessor(className, fieldName, fieldType)
}
