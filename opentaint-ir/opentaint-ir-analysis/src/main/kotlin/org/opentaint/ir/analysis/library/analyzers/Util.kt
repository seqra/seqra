package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.analysis.paths.minus
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRThis
import org.opentaint.ir.api.ext.toType

val JIRMethod.thisInstance: JIRThis
    get() = JIRThis(enclosingClass.toType())

fun JIRClasspath.getArgument(param: JIRParameter): JIRArgument? {
    val t = findTypeOrNull(param.type.typeName) ?: return null
    return JIRArgument.of(param.index, param.name, t)
}

fun JIRClasspath.getArgumentsOf(method: JIRMethod): List<JIRArgument> {
    return method.parameters.map { getArgument(it)!! }
}

fun normalFactFlow(
    fact: TaintNode,
    fromPath: AccessPath,
    toPath: AccessPath,
    dropFact: Boolean,
    maxPathLength: Int,
): List<TaintNode> {
    val factPath = fact.variable
    val default = if (dropFact) emptyList() else listOf(fact)

    // Second clause is important here as it saves from false positive aliases, see
    //  #AnalysisTest.`dereferencing copy of value saved before null assignment produce no npe`
    val diff = factPath.minus(fromPath)
    if (diff != null && (fact.activation == null || fromPath != factPath)) {
        val newPath = (toPath / diff).limit(maxPathLength)
        return default
            .plus(fact.moveToOtherPath(newPath))
            .distinct()
    }

    if (factPath.startsWith(toPath)) {
        return emptyList()
    }

    return default
}
