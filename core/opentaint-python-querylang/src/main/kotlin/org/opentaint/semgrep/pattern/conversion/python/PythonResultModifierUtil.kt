package org.opentaint.semgrep.pattern.conversion.python

import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.PythonLanguageStrategy
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue

fun ParamCondition.ParamModifier.dropFieldResultModifier(
    handleFieldModifier: (String) -> Unit,
): ParamCondition.ParamModifier? {
    val value = this.modifier.value as? SignatureModifierValue.StringValue ?: return this
    if (value.paramName != PythonLanguageStrategy.FIELD_AUX_MODIFIER) return this
    handleFieldModifier(value.value)
    return null
}
