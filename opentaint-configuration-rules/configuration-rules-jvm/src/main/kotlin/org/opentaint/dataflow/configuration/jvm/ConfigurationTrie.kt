package org.opentaint.dataflow.configuration.jvm

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.ext.packageName

abstract class ConfigurationTrie<T> {
    private val unprocessedRules = mutableListOf<T>()
    private val rootNode: RootNode<T> = RootNode()

    abstract fun nameMatches(matcher: NameMatcher, name: String): Boolean

    abstract fun ruleClassNameMatcher(rule: T): ClassMatcher

    abstract fun updateRuleClassNameMatcher(rule: T, matcher: ClassMatcher): T

    fun addRules(rules: List<T>) {
        unprocessedRules.addAll(rules)
    }

    private fun initializeIfRequired() {
        if (unprocessedRules.isEmpty()) return

        while (unprocessedRules.isNotEmpty()) {
            var configurationRule = unprocessedRules.removeLast()
            val classMatcher = ruleClassNameMatcher(configurationRule)

            val alternativeClassMatchers = classMatcher.extractAlternatives()
            if (alternativeClassMatchers.size != 1) {
                alternativeClassMatchers.forEach {
                    unprocessedRules += updateRuleClassNameMatcher(configurationRule, it)
                }

                continue
            }

            val simplifiedClassMatcher = alternativeClassMatchers.single()
            configurationRule = updateRuleClassNameMatcher(configurationRule, simplifiedClassMatcher)

            var currentNode: Node<T> = rootNode

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
                currentNode = currentNode.children[part] ?: NodeImpl<T>(part).also { currentNode.children += part to it }
            }

            if (unmatchedPackageNamePart != null && unmatchedPackageNamePart != ALL_MATCH) {
                currentNode.unmatchedRules += configurationRule
                continue
            }

            when (simplifiedClassNameMatcher) {
                AnyNameMatcher -> currentNode.rules += configurationRule

                is NameExactMatcher -> if (unmatchedPackageNamePart == null) {
                    val name = simplifiedClassNameMatcher.name
                    currentNode = currentNode.children[name] ?: Leaf<T>(name).also { currentNode.children += name to it }
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

    fun getRulesForClass(clazz: JIRClassOrInterface): List<T> {
        initializeIfRequired()

        val results = mutableListOf<T>()

        val className = clazz.simpleName
        val packageName = clazz.packageName
        val nameParts = clazz.name.split(DOT_DELIMITER)

        var currentNode: Node<T> = rootNode

        for (i in 0..nameParts.size) {
            results += currentNode.unmatchedRules.filter {
                val classMatcher = ruleClassNameMatcher(it)
                nameMatches(classMatcher.pkg, packageName) && nameMatches(classMatcher.classNameMatcher, className)
            }

            results += currentNode.rules

            // We must process rules containing in the leaf, therefore, we have to spin one more iteration
            currentNode = nameParts.getOrNull(i)?.let { currentNode.children[it] } ?: break
        }

        return results
    }

    private sealed class Node<T> {
        abstract val value: String
        abstract val children: MutableMap<String, Node<T>>
        abstract val rules: MutableList<T>
        abstract val unmatchedRules: MutableList<T>
    }

    private class RootNode<T> : Node<T>() {
        override val children: MutableMap<String, Node<T>> = mutableMapOf()
        override val value: String
            get() = error("Must not be called for the root")
        override val rules: MutableList<T> = mutableListOf()
        override val unmatchedRules: MutableList<T> = mutableListOf()
    }

    private data class NodeImpl<T>(
        override val value: String,
    ) : Node<T>() {
        override val children: MutableMap<String, Node<T>> = mutableMapOf()
        override val rules: MutableList<T> = mutableListOf()
        override val unmatchedRules: MutableList<T> = mutableListOf()
    }

    private data class Leaf<T>(
        override val value: String,
    ) : Node<T>() {
        override val children: MutableMap<String, Node<T>>
            get() = error("Leaf nodes do not have children")
        override val unmatchedRules: MutableList<T>
            get() = mutableListOf()

        override val rules: MutableList<T> = mutableListOf()
    }
}
