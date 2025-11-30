package org.opentaint.dataflow.configuration.jvm.serialized

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.allSuperHierarchy
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.Simple
import java.util.LinkedList
import java.util.Queue

class MethodTaintRulesStorage<S : SerializedRule> private constructor(
    private val patternManager: PatternManager,
    private val concreteMethodNameRules: MutableMap<String, MethodClassTaintRulesStorage<S>>,
    private val patternMethodRules: Map<Regex, Array<SerializedRule>>,
    private val anyMethodRules: MethodClassTaintRulesStorage<S>?,
) {
    private val methodNameWithoutConcreteRules = hashSetOf<String>()

    fun findRules(rules: MutableList<S>, method: JIRMethod) {
        anyMethodRules?.findRules(rules, method)

        val concreteRules = concreteMethodNameRules[method.name]
        if (concreteRules != null) {
            concreteRules.findRules(rules, method)
            return
        }

        if (method.name in methodNameWithoutConcreteRules) {
            return
        }

        val builder = MethodClassTaintRulesStorage.Builder<S>(patternManager, method.name)
        resolvePatterns(patternMethodRules, method.name, builder)
        val storage = builder.build()

        if (storage == null) {
            methodNameWithoutConcreteRules.add(method.name)
            return
        }

        concreteMethodNameRules[method.name] = storage
        storage.findRules(rules, method)
    }

    class Builder<S : SerializedRule>(private val patternManager: PatternManager) {
        private val rules = mutableListOf<S>()

        fun addRules(rules: List<S>) {
            this.rules.addAll(rules)
        }

        fun build(): MethodTaintRulesStorage<S> {
            val concreteMethodNameRules = hashMapOf<String, MethodClassTaintRulesStorage.Builder<S>>()
            val anyMethodRules = MethodClassTaintRulesStorage.Builder<S>(patternManager)
            val patternMethodRules = hashMapOf<String, MutableSet<S>>()

            for (rule in rules) {
                when (val fName = rule.function.name.normalizeAnyName()) {
                    is ClassPattern -> error("impossible")
                    is Simple -> {
                        concreteMethodNameRules.getOrPut(fName.value) {
                            MethodClassTaintRulesStorage.Builder(patternManager, concreteMethodName = fName.value)
                        }.addRule(rule)
                    }

                    is Pattern -> {
                        if (fName.isAny()) {
                            anyMethodRules.addRule(rule)
                        } else {
                            patternMethodRules.getOrPut(fName.pattern, ::hashSetOf).add(rule)
                        }
                    }
                }
            }

            val compiledPatternMethodRules = patternMethodRules
                .mapKeys { patternManager.compilePattern(it.key) }
                .mapValuesTo(hashMapOf()) { it.value.toTypedArray<SerializedRule>() }


            val concreteRules = hashMapOf<String, MethodClassTaintRulesStorage<S>>()
            for ((methodName, builder) in concreteMethodNameRules) {
                resolvePatterns(compiledPatternMethodRules, methodName, builder)
                concreteRules[methodName] = builder.build() ?: continue
            }

            return MethodTaintRulesStorage(
                patternManager,
                concreteRules,
                compiledPatternMethodRules,
                anyMethodRules.build()
            )
        }
    }

    companion object {
        private fun <S : SerializedRule> resolvePatterns(
            patterns: Map<Regex, Array<SerializedRule>>,
            methodName: String,
            builder: MethodClassTaintRulesStorage.Builder<S>
        ) {
            for ((pattern, rules) in patterns) {
                if (pattern.containsMatchIn(methodName)) {
                    for (rule in rules) {
                        @Suppress("UNCHECKED_CAST")
                        builder.addRule(rule as S)
                    }
                }
            }
        }
    }
}

private class MethodClassTaintRulesStorage<S : SerializedRule> private constructor(
    private val concreteMethodName: String?,
    private val patterns: ClassNamePattern<S>,
    private val anyRules: Array<S>,
    private val concreteClassRules: MutableMap<String, MutableSet<S>>,
) {
    private val patternResolvedClasses = hashSetOf<String>()
    private val pushDelayRulesQueue: Queue<Pair<String, Iterable<S>>> = LinkedList()

    init {
        for ((className, rules) in concreteClassRules) {
            registerRules(className, rules)
        }
    }

    private fun registerRules(className: String, rules: Iterable<S>) {
        if (concreteMethodName == null) return // todo: push any method name matchers???
        pushDelayRulesQueue.add(className to rules)
    }

    private fun pushDelayedRules(cp: JIRClasspath) {
        if (pushDelayRulesQueue.isEmpty()) return

        val iter = pushDelayRulesQueue.iterator()
        while (iter.hasNext()) {
            val (className, rules) = iter.next()
            iter.remove()

            val cls = cp.findClassOrNull(className) ?: continue
            pushRuleForSuperTypes(cls, rules)
        }
    }

    private fun pushRuleForSuperTypes(cls: JIRClassOrInterface, rules: Iterable<S>) {
        val typeCondition = SerializedCondition.IsType(
            typeIs = Simple(cls.name),
            pos = PositionBase.This
        )

        val conditionedRules = rules.map { rule ->
            rule.modifyCondition { cond ->
                SerializedCondition.and(listOfNotNull(cond, typeCondition))
            }
        }

        cls.allSuperHierarchy.filter { c ->
            c.declaredMethods.any { it.name == concreteMethodName }
        }.forEach { c ->
            concreteClassRules.getOrPut(c.name, ::hashSetOf).addAll(conditionedRules)
        }
    }

    fun findRules(dst: MutableList<S>, method: JIRMethod) {
        pushDelayedRules(method.enclosingClass.classpath)

        dst.addAll(anyRules)

        findRules(dst, method.enclosingClass)
        method.enclosingClass.allSuperHierarchy.forEach { cls ->
            val overrideRules = mutableListOf<S>()
            findRules(overrideRules, cls)
            overrideRules.removeAll { !it.overrides }
            dst.addAll(overrideRules)
        }
    }

    private fun findRules(dst: MutableList<S>, cls: JIRClassOrInterface) {
        val className = cls.name
        val concreteRules = concreteClassRules[className]
        if (concreteRules != null) {
            dst.addAll(concreteRules)
            return
        }

        if (!patternResolvedClasses.add(className)) {
            return
        }

        val newRules = hashSetOf<S>()
        resolveClassNamePattern(patterns, className, newRules)

        if (newRules.isEmpty()) return

        registerRules(className, newRules)
        pushDelayedRules(cls.classpath)

        concreteClassRules.getOrPut(className, ::hashSetOf).addAll(newRules)
        dst.addAll(newRules)

        return
    }

    private class ClassNamePattern<S: SerializedRule>(
        val concreteClassNameAnyPackageRules: Map<String, Array<S>>,
        val concreteClassPackagePatternRules: Map<String, Array<Pair<Regex, Array<S>>>>,
        val concretePackageClassPatternRules: Map<String, Array<Pair<Regex, Array<S>>>>,
        val classPatternPackagePatternRules: Array<Pair<Regex, Array<Pair<Regex, Array<S>>>>>,
    )

    class Builder<S : SerializedRule>(
        private val patternManager: PatternManager,
        private val concreteMethodName: String? = null,
    ) {
        private val rules = mutableListOf<S>()
        fun addRule(rule: S) {
            rules.add(rule)
        }

        private val anyRules = hashSetOf<S>()

        private val concreteClassRules = hashMapOf<String, MutableSet<S>>()

        private val concreteClassNameAnyPackageRules = hashMapOf<String, MutableSet<S>>()
        private val concreteClassPackagePatternRules = hashMapOf<String, MutableMap<Regex, MutableSet<S>>>()
        private val concretePackageClassPatternRules = hashMapOf<String, MutableMap<Regex, MutableSet<S>>>()
        private val classPatternPackagePatternRules = hashMapOf<Regex, MutableMap<Regex, MutableSet<S>>>()

        fun build(): MethodClassTaintRulesStorage<S>? {
            if (rules.isEmpty()) return null

            for (rule in rules) {
                val pkg = rule.function.`package`.normalizeAnyName()
                val cls = rule.function.`class`.normalizeAnyName()

                when (pkg) {
                    is ClassPattern -> error("impossible")
                    is Simple -> when (cls) {
                        is ClassPattern -> error("impossible")
                        is Simple -> {
                            addConcreteClassRule(joinClassName(pkg.value, cls.value), rule)
                        }

                        is Pattern -> {
                            addConcretePackagePatternClassRule(pkg.value, cls, rule)
                        }
                    }

                    is Pattern -> when (cls) {
                        is ClassPattern -> error("impossible")
                        is Simple -> addPatternPackageConcreteClassRule(pkg, cls.value, rule)
                        is Pattern -> addPatternPackagePatternClassRule(pkg, cls, rule)
                    }
                }
            }

            val patterns = ClassNamePattern(
                concreteClassNameAnyPackageRules.mapValuesTo(hashMapOf()) { it.value.toRuleArray() },
                concreteClassPackagePatternRules.mapValuesTo(hashMapOf()) { (_, pkgRules) ->
                    pkgRules.entries.map { it.key to it.value.toRuleArray() }.toTypedArray()
                },
                concretePackageClassPatternRules.mapValuesTo(hashMapOf()) { (_, clsRules) ->
                    clsRules.entries.map { it.key to it.value.toRuleArray() }.toTypedArray()
                },
                classPatternPackagePatternRules.map { (clsPattern, pkgRules) ->
                    clsPattern to pkgRules.entries.map { it.key to it.value.toRuleArray() }.toTypedArray()
                }.toTypedArray()
            )

            val resultConcreteRules = hashMapOf<String, MutableSet<S>>()
            for ((className, classRules) in concreteClassRules) {
                resolveClassNamePattern(patterns, className, classRules)
                resultConcreteRules[className] = classRules
            }

            return MethodClassTaintRulesStorage(
                concreteMethodName, patterns,
                anyRules.toRuleArray(), resultConcreteRules
            )
        }

        private fun addPatternPackagePatternClassRule(pkg: Pattern, cls: Pattern, rule: S) {
            if (pkg.isAny() && cls.isAny()) {
                anyRules.add(rule)
                return
            }

            val clsPattern = patternManager.compilePattern(cls.pattern)
            val pkgPattern = patternManager.compilePattern(pkg.pattern)

            classPatternPackagePatternRules
                .getOrPut(clsPattern, ::hashMapOf)
                .getOrPut(pkgPattern, ::hashSetOf)
                .add(rule)
        }

        private fun addPatternPackageConcreteClassRule(pkg: Pattern, cls: String, rule: S) {
            if (pkg.isAny()) {
                concreteClassNameAnyPackageRules.getOrPut(cls, ::hashSetOf).add(rule)
                return
            }

            val pkgPattern = patternManager.compilePattern(pkg.pattern)
            concreteClassPackagePatternRules
                .getOrPut(cls, ::hashMapOf)
                .getOrPut(pkgPattern, ::hashSetOf)
                .add(rule)
        }

        private fun addConcretePackagePatternClassRule(pkg: String, cls: Pattern, rule: S) {
            val clsPattern = patternManager.compilePattern(cls.pattern)
            concretePackageClassPatternRules
                .getOrPut(pkg, ::hashMapOf)
                .getOrPut(clsPattern, ::hashSetOf)
                .add(rule)
        }

        private fun addConcreteClassRule(className: String, rule: S) {
            concreteClassRules.getOrPut(className, ::hashSetOf).add(rule)
        }
    }

    companion object {
        private fun <S : SerializedRule> Collection<S>.toRuleArray(): Array<S> {
            @Suppress("UNCHECKED_CAST")
            return toTypedArray<SerializedRule>() as Array<S>
        }

        private fun <S : SerializedRule> resolveClassNamePattern(
            patterns: ClassNamePattern<S>,
            fullClassName: String,
            classRules: MutableSet<S>
        ) {
            val (pkgName, simpleName) = splitClassName(fullClassName)
            patterns.concreteClassNameAnyPackageRules[simpleName]?.forEach { classRules.add(it) }

            patterns.concreteClassPackagePatternRules[simpleName]?.forEach { (pkgPattern, rules) ->
                if (pkgPattern.matches(pkgName)) {
                    rules.forEach { classRules.add(it) }
                }
            }

            patterns.concretePackageClassPatternRules[pkgName]?.forEach { (clsPattern, rules) ->
                if (clsPattern.matches(simpleName)) {
                    rules.forEach { classRules.add(it) }
                }
            }

            for ((clsPattern, pkgRules) in patterns.classPatternPackagePatternRules) {
                if (!clsPattern.matches(simpleName)) continue
                for ((pkg, rules) in pkgRules) {
                    if (!pkg.matches(pkgName)) continue
                    rules.forEach { classRules.add(it) }
                }
            }
        }
    }
}
