package org.opentaint.semgrep.pattern

import java.io.File
import kotlin.io.path.Path

object SemgrepRuleUtils {
    fun getRuleId(ruleSetName: String, id: String): String {
        return Path(ruleSetName)
            .parent.toString().replace(
                File.separatorChar, '.'
            ) + "." + id
    }
}
