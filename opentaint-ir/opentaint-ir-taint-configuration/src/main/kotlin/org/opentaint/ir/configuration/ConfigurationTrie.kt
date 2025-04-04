package org.opentaint.ir.configuration

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.ext.packageName

class ConfigurationTrie(
    configuration: List<SerializedTaintConfigurationItem>,
    private val nameMatcher: (NameMatcher, String) -> Boolean
) {
    private val unprocessedRules: MutableList<SerializedTaintConfigurationItem> = configuration.toMutableList()
    private val rootNode: RootNode = RootNode()

    private fun initializeIfRequired() {
        if (unprocessedRules.isEmpty()) return

        while (unprocessedRules.isNotEmpty()) {
            var configurationRule = unprocessedRules.removeLast()
            val classMatcher = configurationRule.methodInfo.cls

            val alternativeClassMatchers = classMatcher.extractAlternatives()
            if (alternativeClassMatchers.size != 1) {
                alternativeClassMatchers.forEach {
                    val updatedMethodInfo = configurationRule.methodInfo.copy(cls = it)
                    unprocessedRules += configurationRule.updateMethodInfo(updatedMethodInfo)
                }

                continue
            }

            val simplifiedClassMatcher = alternativeClassMatchers.single()
            val updatedMethodInfo = configurationRule.methodInfo.copy(cls = simplifiedClassMatcher)
            configurationRule = configurationRule.updateMethodInfo(updatedMethodInfo)

            var currentNode: Node = rootNode

            val (simplifiedPkgMatcher, simplifiedClassNameMatcher) = simplifiedClassMatcher

            var matchedPackageNameParts = emptyList<String>()
            var unmatchedPackageNamePart: String? = null

            when (simplifiedPkgMatcher) {
                AnyNameMatcher -> {
                    currentNode.unmatchedRules += configurationRule
                    continue
                }

                is NameExactMatcher -> matchedPackageNameParts = simplifiedPkgMatcher.name.split(DOT_DELIMITER)
                is NamePatternMatcher -> {
                    val (matchedParts, unmatchedParts) = simplifiedPkgMatcher.splitRegex()
                    matchedPackageNameParts = matchedParts
                    unmatchedPackageNamePart = unmatchedParts
                }
            }

            for (part in matchedPackageNameParts) {
                currentNode = currentNode.children[part] ?: NodeImpl(part).also { currentNode.children += part to it }
            }

            if (unmatchedPackageNamePart != null && unmatchedPackageNamePart != ALL_MATCH) {
                currentNode.unmatchedRules += configurationRule
                continue
            }

            when (simplifiedClassNameMatcher) {
                AnyNameMatcher -> currentNode.rules += configurationRule

                is NameExactMatcher -> if (unmatchedPackageNamePart == null) {
                    val name = simplifiedClassNameMatcher.name
                    currentNode = currentNode.children[name] ?: Leaf(name).also { currentNode.children += name to it }
                    currentNode.rules += configurationRule
                } else {
                    // case for patterns like ".*\.Request"
                    currentNode.unmatchedRules += configurationRule
                }

                is NamePatternMatcher -> {
                    val classPattern = simplifiedClassNameMatcher.pattern

                    if (classPattern == ALL_MATCH) {
                        currentNode.rules += configurationRule
                        continue
                    }

                    currentNode.unmatchedRules += configurationRule
                }
            }
        }
    }

    fun getRulesForClass(clazz: JIRClassOrInterface): List<SerializedTaintConfigurationItem> {
        initializeIfRequired()

        val results = mutableListOf<SerializedTaintConfigurationItem>()

        val className = clazz.simpleName
        val packageName = clazz.packageName
        val nameParts = clazz.name.split(DOT_DELIMITER)

        var currentNode: Node = rootNode

        for (i in 0..nameParts.size) {
            results += currentNode.unmatchedRules.filter {
                val classMatcher = it.methodInfo.cls
                nameMatcher(classMatcher.pkg, packageName) && nameMatcher(classMatcher.classNameMatcher, className)
            }

            results += currentNode.rules

            // We must process rules containing in the leaf, therefore, we have to spin one more iteration
            currentNode = nameParts.getOrNull(i)?.let { currentNode.children[it] } ?: break
        }

        return results
    }

    private sealed class Node {
        abstract val value: String
        abstract val children: MutableMap<String, Node>
        abstract val rules: MutableList<SerializedTaintConfigurationItem>
        abstract val unmatchedRules: MutableList<SerializedTaintConfigurationItem>
    }

    private class RootNode : Node() {
        override val children: MutableMap<String, Node> = mutableMapOf()
        override val value: String
            get() = error("Must not be called for the root")
        override val rules: MutableList<SerializedTaintConfigurationItem> = mutableListOf()
        override val unmatchedRules: MutableList<SerializedTaintConfigurationItem> = mutableListOf()
    }

    private data class NodeImpl(
        override val value: String,
    ) : Node() {
        override val children: MutableMap<String, Node> = mutableMapOf()
        override val rules: MutableList<SerializedTaintConfigurationItem> = mutableListOf()
        override val unmatchedRules: MutableList<SerializedTaintConfigurationItem> = mutableListOf()
    }

    private data class Leaf(
        override val value: String
    ) : Node() {
        override val children: MutableMap<String, Node>
            get() = error("Leaf nodes do not have children")
        override val unmatchedRules: MutableList<SerializedTaintConfigurationItem>
            get() = mutableListOf()

        override val rules: MutableList<SerializedTaintConfigurationItem> = mutableListOf()
    }
}
