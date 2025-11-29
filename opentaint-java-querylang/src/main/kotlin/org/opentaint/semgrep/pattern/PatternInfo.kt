package org.opentaint.semgrep.pattern

class PatternInfo(
    val calledMethods: Set<String>,
    val constructorsFor: Set<String>,
    val stringLiterals: Set<String>,
    val formalArgumentTypes: Set<String>,
    val matchesOnlyMethodDeclaration: Boolean,
    val mayMatchInstList: Boolean,
) {
    companion object {
        private val ignoreMethods = setOf("<init>")

        fun construct(pattern: SemgrepJavaPattern): PatternInfo {
            val curMethods = ((pattern as? MethodInvocation)?.methodName as? ConcreteName)?.name
                ?.let { listOf(it) }
                ?.filter { it !in ignoreMethods }
                ?: emptyList()

            val curConstructor =
                ((pattern as? ObjectCreation)?.type?.dotSeparatedParts?.lastOrNull() as? ConcreteName)?.name
                    ?.let { listOf(it) }
                    ?: emptyList()

            val curStringLiteral = ((pattern as? StringLiteral)?.content as? ConcreteName)?.name
                ?.let { listOf(it) }
                ?: emptyList()

            val curFormalArgument =
                ((pattern as? FormalArgument)?.type?.dotSeparatedParts?.lastOrNull() as? ConcreteName)?.name
                    ?.let { listOf(it) }
                    ?: emptyList()

            val childrenInfo = pattern.children.map { construct(it) }

            val methods = curMethods + childrenInfo.flatMap { it.calledMethods }
            val constructors = curConstructor + childrenInfo.flatMap { it.constructorsFor }
            val stringLiterals = curStringLiteral + childrenInfo.flatMap { it.stringLiterals }
            val formalArgumentTypes = curFormalArgument + childrenInfo.flatMap { it.formalArgumentTypes }

            val matchesOnlyMethodDeclaration = pattern is MethodDeclaration
            val mayMatchInstList = pattern is PatternSequence || pattern is Ellipsis || pattern is EmptyPatternSequence

            return PatternInfo(
                calledMethods = methods.toSet(),
                constructorsFor = constructors.toSet(),
                stringLiterals = stringLiterals.toSet(),
                formalArgumentTypes = formalArgumentTypes.toSet(),
                matchesOnlyMethodDeclaration = matchesOnlyMethodDeclaration,
                mayMatchInstList = mayMatchInstList,
            )
        }
    }
}
