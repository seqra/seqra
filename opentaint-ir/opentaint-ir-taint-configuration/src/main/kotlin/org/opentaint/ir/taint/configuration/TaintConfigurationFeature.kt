@file:Suppress("PublicApiImplicitType")

package org.opentaint.ir.taint.configuration

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspathFeature
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRPrimitiveType
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.ext.allSuperHierarchySequence
import org.opentaint.ir.api.ext.boolean
import org.opentaint.ir.api.ext.byte
import org.opentaint.ir.api.ext.char
import org.opentaint.ir.api.ext.double
import org.opentaint.ir.api.ext.float
import org.opentaint.ir.api.ext.int
import org.opentaint.ir.api.ext.long
import org.opentaint.ir.api.ext.short
import org.opentaint.ir.impl.cfg.util.isArray
import java.nio.file.Path
import kotlin.io.path.readText

class TaintConfigurationFeature private constructor(
    jsonConfig: String,
    additionalSerializersModule: SerializersModule?,
) : JIRClasspathFeature {
    private val rulesByClass: MutableMap<JIRClassOrInterface, List<SerializedTaintConfigurationItem>> = hashMapOf()
    private val rulesForMethod: MutableMap<JIRMethod, List<TaintConfigurationItem>> = hashMapOf()
    private val compiledRegex: MutableMap<String, Regex> = hashMapOf()

    private val configurationTrie: ConfigurationTrie by lazy {
        val serializers = SerializersModule {
            include(defaultSerializationModule)
            additionalSerializersModule?.let { include(it) }
        }

        val json = Json {
            classDiscriminator = CLASS_DISCRIMINATOR
            serializersModule = serializers
            prettyPrint = true
        }

        val configuration = json
            .decodeFromString<List<SerializedTaintConfigurationItem>>(jsonConfig)
            .map {
                when (it) {
                    is SerializedTaintEntryPointSource -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )

                    is SerializedTaintMethodSource -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )

                    is SerializedTaintMethodSink -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )

                    is SerializedTaintPassThrough -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )

                    is SerializedTaintCleaner -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )
                }
            }

        ConfigurationTrie(configuration, ::matches)
    }

    @Synchronized
    fun getConfigForMethod(method: JIRMethod): List<TaintConfigurationItem> =
        resolveConfigForMethod(method)

    private var primitiveTypesSet: Set<JIRPrimitiveType>? = null

    private fun primitiveTypes(method: JIRMethod): Set<JIRPrimitiveType> {
        if (primitiveTypesSet == null) {
            val cp = method.enclosingClass.classpath
            primitiveTypesSet = setOf(
                cp.boolean,
                cp.byte,
                cp.short,
                cp.int,
                cp.long,
                cp.char,
                cp.float,
                cp.double,
            )
        }
        return primitiveTypesSet!!
    }

    private fun resolveConfigForMethod(method: JIRMethod): List<TaintConfigurationItem> {
        val taintConfigurationItems = rulesForMethod[method]
        if (taintConfigurationItems != null) {
            return taintConfigurationItems
        }

        val classRules = getClassRules(method.enclosingClass)
        val destination = mutableListOf<TaintConfigurationItem>()
        classRules.mapNotNullTo(destination) {
            val functionMatcher = it.methodInfo
            if (!functionMatcher.matches(method)) return@mapNotNullTo null
            it.resolveForMethod(method)
        }

        method
            .enclosingClass
            .allSuperHierarchySequence
            .distinct()
            .map { getClassRules(it) }
            .forEach { rules ->
                rules.mapNotNullTo(destination) {
                    val methodInfo = it.methodInfo
                    if (!methodInfo.applyToOverrides || !methodInfo.matches(method)) return@mapNotNullTo null
                    it.resolveForMethod(method)
                }
            }

        val rules = destination.distinct()
        rulesForMethod[method] = rules
        return rules
    }

    private fun getClassRules(clazz: JIRClassOrInterface) = rulesByClass.getOrPut(clazz) {
        configurationTrie.getRulesForClass(clazz)
    }

    private fun FunctionMatcher.matches(method: JIRMethod): Boolean {
        val functionNameMatcher = functionName
        val functionName = if (method.isConstructor) "init^" else method.name
        val functionNameMatches = matches(functionNameMatcher, functionName)
        if (!functionNameMatches) return false

        val parameterMatches = parametersMatchers.all {
            val parameter = method.parameters.getOrNull(it.index) ?: return@all false
            it.typeMatcher.matches(parameter.type)
        }
        if (!parameterMatches) return false

        val returnTypeMatches = returnTypeMatcher.matches(method.returnType)
        if (!returnTypeMatches) return false

        // TODO add function's label processing

        require(modifier == -1) {
            "Unexpected modifier matcher value $modifier"
        }

        val isExcluded = exclude.any { it.matches(method) }
        return !isExcluded
    }

    private fun ClassMatcher.matches(fqn: String) = matches(
        fqn.substringBeforeLast(DOT_DELIMITER, missingDelimiterValue = ""),
        fqn.substringAfterLast(DOT_DELIMITER, missingDelimiterValue = fqn)
    )

    private fun ClassMatcher.matches(pkgName: String, className: String): Boolean {
        val packageMatches = matches(pkg, pkgName)
        if (!packageMatches) return false
        return matches(classNameMatcher, className)
    }

    private fun matches(nameMatcher: NameMatcher, nameToBeMatched: String): Boolean = when (nameMatcher) {
        AnyNameMatcher -> true
        is NameExactMatcher -> nameToBeMatched == nameMatcher.name
        is NamePatternMatcher -> {
            compiledRegex.getOrPut(nameMatcher.pattern) {
                nameMatcher.pattern.toRegex()
            }.matches(nameToBeMatched)
        }
    }

    private fun TypeMatcher.matches(typeName: TypeName): Boolean = matches(typeName.typeName)

    private fun TypeMatcher.matches(typeName: String): Boolean =
        when (this) {
            AnyTypeMatcher -> true
            is ClassMatcher -> matches(typeName)
            is PrimitiveNameMatcher -> name == typeName
        }

    private fun SerializedTaintConfigurationItem.resolveForMethod(method: JIRMethod): TaintConfigurationItem =
        when (this) {
            is SerializedTaintEntryPointSource -> TaintEntryPointSource(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintMethodSource -> TaintMethodSource(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintMethodSink -> TaintMethodSink(
                method,
                ruleNote,
                cwe,
                condition.resolve(method)
            )

            is SerializedTaintPassThrough -> TaintPassThrough(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintCleaner -> TaintCleaner(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )
        }

    private fun Condition.resolve(method: JIRMethod): Condition = this
        .accept(ConditionSpecializer(method))
        .accept(ConditionSimplifier())

    private fun List<Action>.resolve(method: JIRMethod): List<Action> =
        flatMap { it.accept(ActionSpecializer(method)) }

    private fun specializePosition(method: JIRMethod, position: Position): List<Position> = when {
        !inBounds(method, position) -> emptyList()
        position !is AnyArgument -> listOf(position)
        else -> method.parameters.indices.map { Argument(it) }.filter { inBounds(method, it) }
    }

    private fun mkTrue(): Condition = ConstantTrue
    private fun mkFalse(): Condition = Not(ConstantTrue)

    private fun mkOr(conditions: List<Condition>) = when (conditions.size) {
        0 -> mkFalse()
        1 -> conditions.single()
        else -> Or(conditions)
    }

    private fun mkAnd(conditions: List<Condition>) = when (conditions.size) {
        0 -> mkTrue()
        1 -> conditions.single()
        else -> And(conditions)
    }

    private fun inBounds(method: JIRMethod, position: Position): Boolean =
        when (position) {
            AnyArgument -> method.parameters.isNotEmpty()
            is Argument -> position.index in method.parameters.indices
            This -> !method.isStatic
            Result -> method.returnType.typeName != PredefinedPrimitives.Void
            ResultAnyElement -> method.returnType.isArray
        }

    private inner class ActionSpecializer(val method: JIRMethod) : TaintActionVisitor<List<Action>> {
        override fun visit(action: CopyAllMarks): List<Action> {
            val from = specializePosition(method, action.from)
            val to = specializePosition(method, action.to)
            return from.flatMap { fst ->
                to.mapNotNull { snd ->
                    if (fst == snd) return@mapNotNull null
                    CopyAllMarks(fst, snd)
                }
            }
        }

        override fun visit(action: CopyMark): List<Action> {
            val from = specializePosition(method, action.from)
            val to = specializePosition(method, action.to)
            return from.flatMap { fst ->
                to.mapNotNull { snd ->
                    if (fst == snd) return@mapNotNull null
                    action.copy(from = fst, to = snd)
                }
            }
        }

        override fun visit(action: AssignMark): List<Action> =
            specializePosition(method, action.position).map { action.copy(position = it) }

        override fun visit(action: RemoveAllMarks): List<Action> =
            specializePosition(method, action.position).map { action.copy(position = it) }

        override fun visit(action: RemoveMark): List<Action> =
            specializePosition(method, action.position).map { action.copy(position = it) }

        override fun visit(action: Action): List<Action> = error("Unexpected action $action")
    }

    private inner class ConditionSpecializer(val method: JIRMethod) : ConditionVisitor<Condition> {
        override fun visit(condition: And): Condition =
            mkAnd(condition.args.map { it.accept(this) })

        override fun visit(condition: Or): Condition =
            mkOr(condition.args.map { it.accept(this) })

        override fun visit(condition: Not): Condition =
            Not(condition.arg.accept(this))

        override fun visit(condition: IsConstant): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: IsType): Condition {
            val position = specializePosition(method, condition.position)
            val typeMatcher = condition.typeMatcher

            if (typeMatcher is AnyTypeMatcher) {
                return mkTrue()
            }

            if (typeMatcher is PrimitiveNameMatcher) {
                val types = primitiveTypes(method).filter { typeMatcher.matches(it.typeName) }
                return mkOr(types.flatMap { type -> position.map { TypeMatches(it, type) } })
            }

            val typeMatchers = (typeMatcher as ClassMatcher).extractAlternatives()
            val unresolvedMatchers = mutableListOf<ClassMatcher>()
            val disjuncts = mutableListOf<Condition>()

            val cp = method.enclosingClass.classpath

            for (matcher in typeMatchers) {
                val pkgMatcher = matcher.pkg
                val clsMatcher = matcher.classNameMatcher

                if (pkgMatcher !is NameExactMatcher || clsMatcher !is NameExactMatcher) {
                    unresolvedMatchers += matcher
                    continue
                }

                val type = cp.findTypeOrNull("${pkgMatcher.name}$DOT_DELIMITER${clsMatcher.name}")
                    ?: continue

                position.mapTo(disjuncts) { TypeMatches(it, type) }
            }

            if (unresolvedMatchers.isNotEmpty()) {
                val allClassNames = cp.registeredLocations.flatMapTo(hashSetOf()) {
                    val names = it.jIRLocation?.classNames ?: return@flatMapTo emptyList()
                    names.map { name ->
                        val packageName = name.substringBeforeLast(DOT_DELIMITER, missingDelimiterValue = "")
                        val simpleName = name.substringAfterLast(DOT_DELIMITER)
                        packageName to simpleName
                    }
                }

                unresolvedMatchers.forEach { classMatcher ->
                    val matchedClassNames = allClassNames.filter { (packageName, simpleName) ->
                        matches(classMatcher.pkg, packageName) && matches(classMatcher.classNameMatcher, simpleName)
                    }

                    matchedClassNames.flatMapTo(disjuncts) { (packageName, simpleName) ->
                        val type = cp.findTypeOrNull("${packageName}$DOT_DELIMITER${simpleName}")
                            ?: return@flatMapTo emptyList()

                        position.map { TypeMatches(it, type) }
                    }
                }
            }

            return mkOr(disjuncts)
        }

        override fun visit(condition: AnnotationType): Condition {
            val positions = specializePosition(method, condition.position)
            return if (positions.any { methodAnnotationMatches(it, condition.typeMatcher) }) {
                mkTrue()
            } else {
                mkFalse()
            }
        }

        private fun methodAnnotationMatches(position: Position, matcher: TypeMatcher): Boolean {
            when (position) {
                is Argument -> {
                    val annotations = method.parameters.getOrNull(position.index)?.annotations
                    return annotations?.any { matcher.matches(it.name) } ?: false
                }

                Result -> TODO("What does it mean?")
                This -> TODO("What does it mean?")
                AnyArgument -> error("Must not occur here")
                ResultAnyElement -> error("Must not occur here")
            }
        }

        override fun visit(condition: ConstantEq): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantLt): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantGt): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantMatches): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: SourceFunctionMatches): Condition = ConstantTrue // TODO Not implemented yet

        override fun visit(condition: ContainsMark): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantTrue): Condition = condition

        override fun visit(condition: TypeMatches): Condition = error("Must not occur here")

        override fun visit(condition: Condition): Condition = condition
    }

    private inner class ConditionSimplifier : ConditionVisitor<Condition> {
        override fun visit(condition: And): Condition {
            val queue = ArrayDeque(condition.args)
            val args = mutableListOf<Condition>()
            while (queue.isNotEmpty()) {
                val it = queue.removeFirst().accept(this)
                if (it is ConstantTrue) {
                    // skip
                } else if (it is Not && it.arg is ConstantTrue) {
                    return mkFalse()
                } else if (it is And) {
                    queue += it.args
                } else {
                    args += it
                }
            }
            return mkAnd(args)
        }

        override fun visit(condition: Or): Condition {
            val queue = ArrayDeque(condition.args)
            val args = mutableListOf<Condition>()
            while (queue.isNotEmpty()) {
                val it = queue.removeFirst().accept(this)
                if (it is ConstantTrue) {
                    return mkTrue()
                } else if (it is Not && it.arg is ConstantTrue) {
                    // skip
                } else if (it is Or) {
                    queue += it.args
                } else {
                    args += it
                }
            }
            return mkOr(args)
        }

        override fun visit(condition: Not): Condition {
            val arg = condition.arg.accept(this)
            return if (arg is Not) {
                // Eliminate double negation:
                arg.arg
            } else {
                Not(arg)
            }
        }

        override fun visit(condition: IsConstant): Condition = condition
        override fun visit(condition: IsType): Condition = condition
        override fun visit(condition: AnnotationType): Condition = condition
        override fun visit(condition: ConstantEq): Condition = condition
        override fun visit(condition: ConstantLt): Condition = condition
        override fun visit(condition: ConstantGt): Condition = condition
        override fun visit(condition: ConstantMatches): Condition = condition
        override fun visit(condition: SourceFunctionMatches): Condition = condition
        override fun visit(condition: ContainsMark): Condition = condition
        override fun visit(condition: ConstantTrue): Condition = condition
        override fun visit(condition: TypeMatches): Condition = condition

        override fun visit(condition: Condition): Condition = condition
    }

    companion object {
        fun fromJson(
            jsonConfig: String,
            serializersModule: SerializersModule? = null,
        ) = TaintConfigurationFeature(jsonConfig, serializersModule)

        fun fromPath(
            configPath: Path,
            serializersModule: SerializersModule? = null,
        ) = fromJson(configPath.readText(), serializersModule)

        val defaultSerializationModule = SerializersModule {
            include(conditionModule)
            include(actionModule)
        }

        private const val CLASS_DISCRIMINATOR = "_"
    }
}
