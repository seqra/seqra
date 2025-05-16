package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

fun interface FlowFunction<Fact> {
    fun compute(fact: Fact): Collection<Fact>
}

interface FlowFunctions<Fact, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    /**
     * Method for obtaining initial domain facts at the method entrypoint.
     * Commonly, it is only `listOf(Zero)`.
     */
    fun obtainPossibleStartFacts(method: Method): Collection<Fact>

    /**
     * Sequent flow function.
     *
     * ```
     *   [ DO() ] :: current
     *     |
     *     | (sequent edge)
     *     |
     *   [ DO() ]
     * ```
     */
    fun obtainSequentFlowFunction(
        current: Statement,
        next: Statement,
    ): FlowFunction<Fact>

    /**
     * Call-to-return-site flow function.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   :
     *   : (call-to-return-site edge)
     *   :
     * [ RETURN FROM p ] :: returnSite
     * ```
     */
    fun obtainCallToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement,
    ): FlowFunction<Fact>

    /**
     * Call-to-start flow function.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   : \
     *   :  \ (call-to-start edge)
     *   :   \
     *   :  [ START p ]
     *   :    |
     *   :  [ EXIT p ]
     *   :   /
     *   :  /
     * [ RETURN FROM p ]
     * ```
     */
    fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        calleeStart: Statement,
    ): FlowFunction<Fact>

    /**
     * Exit-to-return-site flow function.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   :  \
     *   :   \
     *   :  [ START p ]
     *   :    |
     *   :  [ EXIT p ] :: exitStatement
     *   :   /
     *   :  / (exit-to-return-site edge)
     *   : /
     * [ RETURN FROM p ] :: returnSite
     * ```
     */
    fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement,
        exitStatement: Statement,
    ): FlowFunction<Fact>
}
