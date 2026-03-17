package org.opentaint.semgrep.pattern

import dk.brics.automaton.RegExp

// TODO: better implementation or get rid of this
fun checkIfRegexIsSimpleEnumeration(regex: String): List<String>? {
    val re = runCatching {
        RegExp(regex)
    }.getOrNull() ?: return null

    return checkIfRegexIsSimpleEnumeration(re)
}

private fun checkIfRegexIsSimpleEnumeration(re: RegExp): List<String>? {
    val kind = getField<Any>(re, "kind").toString()

    return when (kind) {
        "REGEXP_STRING" -> {
            val s = getField<String>(re, "s")
            listOf(s)
        }
        "REGEXP_UNION" -> {
            val exp1 = getField<RegExp>(re, "exp1").let {
                checkIfRegexIsSimpleEnumeration(it)
                    ?: return null
            }
            val exp2 = getField<RegExp>(re, "exp2").let {
                checkIfRegexIsSimpleEnumeration(it)
                    ?: return null
            }
            exp1 + exp2
        }
        else -> {
            null
        }
    }
}

private inline fun <reified T> getField(
    re: RegExp,
    fieldName: String,
): T {
    val field = re::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(re) as T
}
