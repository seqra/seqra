package org.opentaint.ir.configuration

import org.opentaint.ir.api.JIRClasspath

fun JIRClasspath.taintConfigurationFeature(): TaintConfigurationFeature = features
    ?.singleOrNull { it is TaintConfigurationFeature } as? TaintConfigurationFeature
    ?: error("No taint configuration feature found")

fun ClassMatcher.extractAlternatives(): Set<ClassMatcher> {
    val unprocessedMatchers = mutableListOf(this)
    val results = mutableSetOf<ClassMatcher>()

    while (unprocessedMatchers.isNotEmpty()) {
        var unprocessedMatcher = unprocessedMatchers.removeLast()

        var matchedPackageParts: List<String> = emptyList()
        var unmatchedPackagePart: String? = null

        var isAnyPackageAllowed = false

        when (val pkg = unprocessedMatcher.pkg) {
            AnyNameMatcher -> isAnyPackageAllowed = true
            is NameExactMatcher -> matchedPackageParts = pkg.name.split(DOT_DELIMITER)
            is NamePatternMatcher -> {
                val alternatives = unprocessedMatcher.extractAlternativesToIfRequired(
                    { prevClassMatcher, newMatcher -> prevClassMatcher.copy(pkg = newMatcher) },
                    pkg,
                )

                if (alternatives.size != 1) {
                    unprocessedMatchers += alternatives
                    continue
                }

                unprocessedMatcher = alternatives.single()

                val simplifiedName = unprocessedMatcher.pkg as NamePatternMatcher
                val (matchedParts, unmatchedPart) = simplifiedName.splitRegex()

                matchedPackageParts = matchedParts
                unmatchedPackagePart = unmatchedPart
            }
        }

        if (unmatchedPackagePart == null) {
            val nameExactMatcher = NameExactMatcher(matchedPackageParts.joinToString(DOT_DELIMITER))
            unprocessedMatcher = unprocessedMatcher.copy(pkg = nameExactMatcher)
        }

        when (val classNameMatcher = unprocessedMatcher.classNameMatcher) {
            AnyNameMatcher -> results += unprocessedMatcher
            is NameExactMatcher -> results += unprocessedMatcher
            is NamePatternMatcher -> {
                val alternatives = unprocessedMatcher.extractAlternativesToIfRequired(
                    { prevClassMatcher, newMatcher -> prevClassMatcher.copy(classNameMatcher = newMatcher) },
                    classNameMatcher,
                )

                if (alternatives.size != 1) {
                    unprocessedMatchers += alternatives
                    continue
                }

                val classPattern = alternatives.single().classNameMatcher as NamePatternMatcher

                if (classPattern.pattern == ALL_MATCH) {
                    results += unprocessedMatcher
                    continue
                }

                val classPatternName = classPattern.pattern
                    .replace("\\.", "\$")
                    .replace("\\\$", "\$")

                val containsOnlyLettersOrDigits = classPatternName.all {
                    it.isLetterOrDigit() || it == '_' || it == '$'
                }

                results += if (!containsOnlyLettersOrDigits || isAnyPackageAllowed) {
                    unprocessedMatcher
                } else {
                    val classExactName = classPatternName.trimEnd('$')

                    val cls = unprocessedMatcher.copy(classNameMatcher = NameExactMatcher(classExactName))
                    cls
                }
            }
        }
    }

    return results
}

internal fun NamePatternMatcher.splitRegex(): SplitRegex {
    val possibleParts = pattern.split("\\.")
    val matchedParts = mutableListOf<String>()
    var unmatchedPart: String? = null

    for ((i, part) in possibleParts.withIndex()) {
        if (part.all { char -> char.isLetterOrDigit() }) {
            matchedParts += part
            continue
        }

        unmatchedPart = possibleParts
            .subList(i, possibleParts.size)
            .joinToString("\\.")

        break
    }

    return SplitRegex(matchedParts, unmatchedPart)
}

internal data class SplitRegex(val matchedParts: List<String>, val unmatchedPart: String?)

private inline fun ClassMatcher.extractAlternativesToIfRequired(
    classMatcherModifier: (ClassMatcher, NameMatcher) -> ClassMatcher,
    namePatternMatcher: NamePatternMatcher
): List<ClassMatcher> {
    val alternatives = namePatternMatcher.pattern.extractAlternatives()

    if (alternatives.singleOrNull() == namePatternMatcher.pattern) return listOf(this)

    val alternativeMatchers = alternatives.map { NamePatternMatcher(it) }

    return alternativeMatchers.map { classMatcherModifier(this, it) }
}

fun String.extractAlternatives(): Set<String> {
    val queue = mutableListOf(this)
    val result = hashSetOf<String>()

    while (queue.isNotEmpty()) {
        val last = queue.removeLast()

        if (ALTERNATIVE_MARK !in last) {
            result += last
            continue
        }

        queue += last.splitTheMostNestedAlternatives()
    }

    return result
        .flatMapTo(hashSetOf()) { it.splitOnQuestionMark() }
        .mapTo(hashSetOf()) { it.removeRedundantParentheses() }
}

fun String.splitOnQuestionMark(): Set<String> {
    if (QUESTION_MARK !in this) return setOf(this)

    val queue = mutableListOf(this)
    val result = hashSetOf<String>()

    questionMarkSplitter@ while (queue.isNotEmpty()) {
        val nextElement = queue.removeLast()

        var questionMarkIndex = nextElement.indexOf(QUESTION_MARK)
        while (questionMarkIndex > 0) {
            val prevSymbol = nextElement[questionMarkIndex - 1]
            when {
                prevSymbol.isLetterOrDigit() -> {
                    val prefix = nextElement.substring(0, questionMarkIndex - 1)
                    val suffix = nextElement.substring(questionMarkIndex + 1)
                    queue += "$prefix$suffix"
                    queue += "$prefix$prevSymbol$suffix"
                    continue@questionMarkSplitter
                }

                prevSymbol == ')' -> {
                    var balance = 1
                    var index = questionMarkIndex - 1

                    while (balance > 0 && index > 0) {
                        index--
                        when (nextElement[index]) {
                            ')' -> balance++
                            '(' -> balance--
                            else -> continue
                        }
                    }

                    if (balance != 0) {
                        questionMarkIndex = nextElement.indexOf(QUESTION_MARK, questionMarkIndex + 1)
                        continue
                    }

                    val prefix = nextElement.substring(0, index)
                    val suffix = nextElement.substring(questionMarkIndex + 1)

                    val optionalPart = nextElement.substring(index, questionMarkIndex)

                    queue += "$prefix$suffix"
                    queue += "$prefix$optionalPart$suffix"
                    continue@questionMarkSplitter
                }

                else -> questionMarkIndex = nextElement.indexOf(QUESTION_MARK, questionMarkIndex + 1)
            }
        }

        result += nextElement
    }

    return result
}

private fun String.removeRedundantParentheses(): String {
    val openParentheses = mutableListOf<Pair<Int, Boolean>>()
    val redundantValues = hashSetOf<Int>()

    for ((i, char) in withIndex()) {
        when (char) {
            '(' -> openParentheses += i to false

            ')' -> {
                val nextSymbolIsImportant = if (i == lastIndex) {
                    false
                } else {
                    get(i + 1) in SYMBOLS_FORBIDDING_TO_REMOVE_PARENTHESES
                }

                val (index, contaminated) = openParentheses.removeLast()

                if (!contaminated && !nextSymbolIsImportant) {
                    redundantValues += index
                    redundantValues += i
                }
            }

            else -> {
                val (index, contaminated) = openParentheses.lastOrNull() ?: continue
                if (contaminated) continue

                if (char.isLetterOrDigit() || char == '.' || char == '\\' || char == '$' || char == '_') continue

                openParentheses.removeLast()
                openParentheses += index to true
            }
        }
    }

    return buildString {
        for ((i, char) in this@removeRedundantParentheses.withIndex()) {
            if (i in redundantValues) continue

            append(char)
        }
    }
}

private fun String.splitTheMostNestedAlternatives(): List<String> {
    val openIndices = mutableListOf<Int>()
    val matchingIndicesByLevel: MutableMap<Int, MutableList<Pair<Int, Int>>> = mutableMapOf()
    val currentBracketsContainsAlternativeMark = mutableListOf(false)

    var levels = 0

    for ((i, char) in this.withIndex()) {
        when (char) {
            '(' -> {
                openIndices += i
                currentBracketsContainsAlternativeMark += false
            }

            ')' -> {
                val matchingIndex = openIndices.removeLast()

                if (currentBracketsContainsAlternativeMark.removeLast()) {
                    val level = openIndices.size + 1

                    levels = maxOf(level, levels)

                    matchingIndicesByLevel
                        .getOrPut(level) { mutableListOf() }
                        .add(matchingIndex to i)
                }
            }

            ALTERNATIVE_MARK -> {
                if (!currentBracketsContainsAlternativeMark.last()) {
                    currentBracketsContainsAlternativeMark.removeLast()
                    currentBracketsContainsAlternativeMark += true
                }
            }

            else -> continue
        }
    }

    if (levels == 0) {
        return split(ALTERNATIVE_MARK)
    }

    val brackets = matchingIndicesByLevel.getValue(levels)

    val firstBracketEntry = brackets.first().first
    val prefix = substring(0, firstBracketEntry)

    var prefixesValues = mutableListOf(prefix)

    for ((i, indices) in brackets.withIndex()) {
        val (start, end) = indices
        val alternatives = substring(start + 1, end).split(ALTERNATIVE_MARK)

        val nextIndex = brackets.getOrNull(i + 1)?.first ?: length
        val suffix = substring(end + 1, nextIndex)

        val prefixesCopy = prefixesValues
        prefixesValues = mutableListOf()

        alternatives.flatMapTo(prefixesValues) { a -> prefixesCopy.map { "$it($a)$suffix" } }
    }

    return prefixesValues
}

internal const val DOT_DELIMITER = "."
internal const val ALL_MATCH = ".*"
private const val QUESTION_MARK = '?'
private const val ALTERNATIVE_MARK = '|'
private const val SYMBOLS_FORBIDDING_TO_REMOVE_PARENTHESES = "*?{+"