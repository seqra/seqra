package org.opentaint.ir.taint.configuration

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.opentaint.ir.api.core.TypeName
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspathFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.PredefinedJIRPrimitives
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.ir.api.jvm.ext.boolean
import org.opentaint.ir.api.jvm.ext.byte
import org.opentaint.ir.api.jvm.ext.char
import org.opentaint.ir.api.jvm.ext.double
import org.opentaint.ir.api.jvm.ext.float
import org.opentaint.ir.api.jvm.ext.int
import org.opentaint.ir.api.jvm.ext.long
import org.opentaint.ir.api.jvm.ext.short
import java.nio.file.Path
import kotlin.io.path.readText

class TaintConfigurationFeature private constructor(
    jsonConfig: String,
    additionalSerializersModule: SerializersModule?
) : JIRClasspathFeature {
    private val rulesByClass: MutableMap<JIRClassOrInterface, List<SerializedTaintConfigurationItem>> = hashMapOf()
    private val rulesForMethod: MutableMap<JIRMethod, List<TaintConfigurationItem>> = hashMapOf()
    private val compiledRegex: MutableMap<String, Regex> = hashMapOf()

    private val configurationTrie: ConfigurationTrie by lazy {
        val serializers = additionalSerializersModule?.let {
            SerializersModule {
                include(defaultSerializationModule)
                include(it)
            }
        } ?: defaultSerializationModule

        val json = Json {
            classDiscriminator = CLASS_DISCRIMINATOR
            serializersModule = serializers
            prettyPrint = true
        }

        val configuration = json.decodeFromString<List<SerializedTaintConfigurationItem>>(jsonConfig).map {
            when (it) {
                is SerializedTaintCleaner -> it.copy(condition = it.condition.accept(ConditionSimplifier))
                is SerializedTaintEntryPointSource -> it.copy(condition = it.condition.accept(ConditionSimplifier))
                is SerializedTaintMethodSink -> it.copy(condition = it.condition.accept(ConditionSimplifier))
                is SerializedTaintMethodSource -> it.copy(condition = it.condition.accept(ConditionSimplifier))
                is SerializedTaintPassThrough -> it.copy(condition = it.condition.accept(ConditionSimplifier))
            }
        }

        ConfigurationTrie(configuration, ::matches)
    }

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

        rulesForMethod[method] = destination.distinct()

        return rulesForMethod.getValue(method)
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
            is SerializedTaintCleaner -> TaintCleaner(method, condition.resolve(method), actionsAfter.resolve(method))
            is SerializedTaintEntryPointSource -> TaintEntryPointSource(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintMethodSink -> TaintMethodSink(condition.resolve(method), method)
            is SerializedTaintMethodSource -> TaintMethodSource(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintPassThrough -> TaintPassThrough(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )
        }

    private fun Condition.resolve(method: JIRMethod): Condition = accept(ConditionSpecializer(method))
    private fun List<Action>.resolve(method: JIRMethod): List<Action> = flatMap { it.accept(ActionSpecializer(method)) }

    private fun specializePosition(method: JIRMethod, position: Position): List<Position> {
        if (!inBounds(method, position)) return emptyList()
        if (position !is AnyArgument) return listOf(position)
        return method.parameters.indices.map { Argument(it) }.filter { inBounds(method, it) }
    }

    private fun mkOr(conditions: List<Condition>) = if (conditions.size == 1) conditions.single() else Or(conditions)
    private fun mkAnd(conditions: List<Condition>) = if (conditions.size == 1) conditions.single() else And(conditions)

    private fun inBounds(method: JIRMethod, position: Position): Boolean =
        when (position) {
            AnyArgument -> method.parameters.isNotEmpty()
            is Argument -> position.number in method.parameters.indices
            Result -> method.returnType.typeName != PredefinedJIRPrimitives.Void
            ThisArgument -> !method.isStatic
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
    }

    private inner class ConditionSpecializer(val method: JIRMethod) : ConditionVisitor<Condition> {
        override fun visit(condition: And): Condition = mkAnd(condition.args.map { it.accept(this) })

        override fun visit(condition: Or): Condition = mkOr(condition.args.map { it.accept(this) })

        override fun visit(condition: Not): Condition = Not(condition.condition.accept(this))

        override fun visit(condition: IsConstant): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: IsType): Condition {
            val position = specializePosition(method, condition.position)

            val typeMatcher = condition.typeMatcher
            if (typeMatcher is AnyTypeMatcher) {
                return mkOr(position.map { ConstantTrue })
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

        override fun visit(condition: AnnotationType): Condition = ConstantTrue // TODO("Not yet implemented")

        override fun visit(condition: ConstantEq): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantLt): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantGt): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantMatches): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: SourceFunctionMatches): Condition = ConstantTrue // TODO Not implemented yet

        override fun visit(condition: CallParameterContainsMark): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantTrue): Condition = condition

        override fun visit(condition: TypeMatches): Condition = error("Must not occur here")
    }

    companion object {
        fun fromPath(
            configPath: Path,
            serializersModule: SerializersModule? = null
        ) = TaintConfigurationFeature(configPath.readText(), serializersModule)

        fun fromJson(
            jsonConfig: String,
            serializersModule: SerializersModule? = null
        ) = TaintConfigurationFeature(jsonConfig, serializersModule)

        val defaultSerializationModule: SerializersModule
            get() = SerializersModule {
                polymorphic(Condition::class) {
                    subclass(And::class)
                    subclass(Or::class)
                    subclass(Not::class)
                    subclass(IsConstant::class)
                    subclass(IsType::class)
                    subclass(AnnotationType::class)
                    subclass(ConstantEq::class)
                    subclass(ConstantLt::class)
                    subclass(ConstantGt::class)
                    subclass(ConstantMatches::class)
                    subclass(SourceFunctionMatches::class)
                    subclass(CallParameterContainsMark::class)
                    subclass(ConstantTrue::class)
                    subclass(TypeMatches::class)
                }

                polymorphic(Action::class) {
                    subclass(CopyAllMarks::class)
                    subclass(AssignMark::class)
                    subclass(RemoveAllMarks::class)
                    subclass(RemoveMark::class)
                    subclass(CopyMark::class)
                }
            }

        private const val CLASS_DISCRIMINATOR = "_"
    }
}

private object ConditionSimplifier : ConditionVisitor<Condition> {
    override fun visit(condition: And): Condition {
        val unprocessed = condition.args.toMutableList()
        val conjuncts = mutableListOf<Condition>()
        while (unprocessed.isNotEmpty()) {
            val it = unprocessed.removeLast()
            if (it is And) {
                unprocessed.addAll(it.args)
                continue
            }
            conjuncts += it.accept(this)
        }

        return conjuncts.singleOrNull() ?: And(conjuncts.asReversed())
    }

    override fun visit(condition: Or): Condition {
        val unprocessed = condition.args.toMutableList()
        val conjuncts = mutableListOf<Condition>()
        while (unprocessed.isNotEmpty()) {
            val it = unprocessed.removeLast()
            if (it is Or) {
                unprocessed.addAll(it.args)
                continue
            }
            conjuncts += it.accept(this)
        }

        return conjuncts.singleOrNull() ?: Or(conjuncts.asReversed())
    }

    override fun visit(condition: Not): Condition = Not(condition.condition.accept(this))

    override fun visit(condition: IsConstant): Condition = condition

    override fun visit(condition: IsType): Condition = condition

    override fun visit(condition: AnnotationType): Condition = condition

    override fun visit(condition: ConstantEq): Condition = condition

    override fun visit(condition: ConstantLt): Condition = condition

    override fun visit(condition: ConstantGt): Condition = condition

    override fun visit(condition: ConstantMatches): Condition = condition

    override fun visit(condition: SourceFunctionMatches): Condition = condition

    override fun visit(condition: CallParameterContainsMark): Condition = condition

    override fun visit(condition: ConstantTrue): Condition = condition

    override fun visit(condition: TypeMatches): Condition = condition
}
