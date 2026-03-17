package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.TypeName
import org.opentaint.semgrep.pattern.TypedMetavar
import org.opentaint.semgrep.pattern.conversion.mkGeneratedMethodInvocationObjMetaVar

const val generatedMethodClassName = "__.gen.__"

private var genCnt = 0
private val generatedMethodClassType by lazy { TypeName.SimpleTypeName(generatedMethodClassName.split('.').map { ConcreteName(it) }) }

fun generateMethodInvocation(methodName: String, args: List<SemgrepJavaPattern>): MethodInvocation {
    val argsPattern = createMethodArgs(args)
    val obj = TypedMetavar(mkGeneratedMethodInvocationObjMetaVar(genCnt++), generatedMethodClassType)
    return MethodInvocation(ConcreteName(methodName), obj, argsPattern)
}
