# Type-Aware Pattern Matching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three broken Semgrep pattern language features for Java — generic type arguments, array return types, and concrete return types — by threading type information through the existing pipeline instead of discarding it.

**Architecture:** The fix is a plumbing change across 7 existing files in 3 modules. Type arguments are added as a new field (`typeArgs: List<TypeNamePattern>`) to `TypeNamePattern.ClassName` and `FullyQualified`, then propagated through `SerializedTypeNameMatcher.ClassPattern` to the runtime matchers. Return types are added to `SemgrepPatternAction.MethodSignature` and flow through to `SerializedSignatureMatcher.Partial`. All new fields default to `emptyList()` / `null` for backward compatibility.

**Tech Stack:** Kotlin, JUnit 5 / kotlin.test, Gradle, kotlinx.serialization

**Spec:** `docs/specs/2026-04-12-type-aware-pattern-matching-design.md`

---

## File Structure

| File | Module | Responsibility |
|---|---|---|
| `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/ParamCondition.kt` | querylang | `TypeNamePattern` sealed interface — add `typeArgs` to `ClassName` and `FullyQualified` |
| `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/SemgrepPatternAction.kt` | querylang | `MethodSignature` action — add `returnType: TypeNamePattern?` field |
| `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/PatternToActionListConverter.kt` | querylang | Stop discarding type args (line 229), array returns (line 559), concrete returns (line 565) |
| `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/MethodFormulaSimplifier.kt` | querylang | `unifyTypeName()` — handle `typeArgs` in unification logic |
| `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/TaintEdgesGeneration.kt` | querylang | `typeNameMetaVars()` — recurse into `typeArgs` for metavar extraction |
| `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/automata/Predicate.kt` | querylang | `MethodSignature` predicate — add optional `returnType` |
| `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/AutomataToTaintRuleConversion.kt` | querylang | `typeMatcher()` — propagate `typeArgs`; `evaluateFormulaSignature()` — emit return type to `SerializedSignatureMatcher.Partial` |
| `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/SemgrepRuleLoadErrorMessage.kt` | querylang | Remove 4 now-obsolete warning classes |
| `core/opentaint-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/opentaint/dataflow/configuration/jvm/serialized/SerializedNameMatcher.kt` | config-rules | `ClassPattern` — add `typeArgs` field |
| `core/opentaint-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/opentaint/dataflow/configuration/jvm/TaintCondition.kt` | config-rules | `TypeMatchesPattern` — add `typeArgs` field for deferred generic matching |
| `core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/TaintConfiguration.kt` | jvm-sast | `matchFunctionSignature()` — add `matchType(JIRType)` overload; `resolveIsType()` — defer generic checks via `typeArgs` |
| `core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/taint/JIRBasicAtomEvaluator.kt` | jvm-dataflow | `typeMatchesPattern()` — resolve local var generic types when `typeArgs` present |

### Test Files

| File | Module | Tests |
|---|---|---|
| `core/opentaint-java-querylang/samples/src/main/java/example/RuleWithGenericTypeArgs.java` | querylang/samples | E2E sample: generic type arg matching (positive + negative) |
| `core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithGenericTypeArgs.yaml` | querylang/samples | Semgrep rule for generic type arg test |
| `core/opentaint-java-querylang/samples/src/main/java/example/RuleWithArrayReturnType.java` | querylang/samples | E2E sample: array return type matching |
| `core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithArrayReturnType.yaml` | querylang/samples | Semgrep rule for array return type test |
| `core/opentaint-java-querylang/samples/src/main/java/example/RuleWithConcreteReturnType.java` | querylang/samples | E2E sample: concrete return type matching |
| `core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithConcreteReturnType.yaml` | querylang/samples | Semgrep rule for concrete return type test |
| `core/opentaint-java-querylang/src/test/kotlin/org/opentaint/semgrep/TypeAwarePatternTest.kt` | querylang/test | E2E test class exercising all three scenarios |

---

## Task 1: Add `typeArgs` to `TypeNamePattern.ClassName` and `FullyQualified`

**Files:**
- Modify: `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/ParamCondition.kt:14` (ClassName), `:9` (FullyQualified)

- [ ] **Step 1: Add `typeArgs` field to `ClassName`**

In `ParamCondition.kt`, change `ClassName`:

```kotlin
@Serializable
data class ClassName(val name: String, val typeArgs: List<TypeNamePattern> = emptyList()) : TypeNamePattern {
    override fun toString(): String = if (typeArgs.isEmpty()) "*.$name" else "*.$name<${typeArgs.joinToString(", ")}>"
}
```

- [ ] **Step 2: Add `typeArgs` field to `FullyQualified`**

In `ParamCondition.kt`, change `FullyQualified`:

```kotlin
@Serializable
data class FullyQualified(val name: String, val typeArgs: List<TypeNamePattern> = emptyList()) : TypeNamePattern {
    override fun toString(): String = if (typeArgs.isEmpty()) name else "$name<${typeArgs.joinToString(", ")}>"
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :core:opentaint-java-querylang:compileKotlin`
Expected: BUILD SUCCESSFUL (default empty lists mean all existing call sites remain valid)

- [ ] **Step 4: Commit**

```bash
git add core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/ParamCondition.kt
git commit -m "feat: add typeArgs field to TypeNamePattern.ClassName and FullyQualified"
```

---

## Task 2: Add `returnType` to `MethodSignature` action and `MethodSignature` predicate

**Files:**
- Modify: `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/SemgrepPatternAction.kt:103-122`
- Modify: `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/automata/Predicate.kt:19-22`

- [ ] **Step 1: Add `returnType` to `SemgrepPatternAction.MethodSignature`**

In `SemgrepPatternAction.kt`, change the `MethodSignature` data class:

```kotlin
data class MethodSignature(
    val methodName: SignatureName,
    val params: ParamConstraint.Partial,
    val returnType: TypeNamePattern? = null,          // NEW
    val modifiers: List<SignatureModifier>,
    val enclosingClassMetavar: String?,
    val enclosingClassConstraints: List<ClassConstraint>,
): SemgrepPatternAction {
    override val metavars: List<MetavarAtom>
        get() {
            val metavars = mutableSetOf<MetavarAtom>()
            params.conditions.forEach { it.collectMetavarTo(metavars) }
            return metavars.toList()
        }

    override val result: ParamCondition? = null

    override fun setResultCondition(condition: ParamCondition): SemgrepPatternAction {
        error("Unsupported operation?")
    }
}
```

- [ ] **Step 2: Add `returnType` to automata `MethodSignature` predicate**

In `Predicate.kt`, change:

```kotlin
@Serializable
data class MethodSignature(
    val methodName: MethodName,
    val enclosingClassName: MethodEnclosingClassName,
    val returnType: TypeNamePattern? = null,           // NEW
)
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :core:opentaint-java-querylang:compileKotlin`
Expected: BUILD SUCCESSFUL (default `null` keeps existing call sites valid)

- [ ] **Step 4: Commit**

```bash
git add core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/SemgrepPatternAction.kt
git add core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/automata/Predicate.kt
git commit -m "feat: add returnType field to MethodSignature action and predicate"
```

---

## Task 3: Stop discarding type info in `PatternToActionListConverter`

**Files:**
- Modify: `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/PatternToActionListConverter.kt:228-231` (transformSimpleTypeName), `:547-626` (transformMethodDeclaration)
- Modify: `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/SemgrepRuleLoadErrorMessage.kt:149-163` (remove warnings)

- [ ] **Step 1: Preserve type args in `transformSimpleTypeName()`**

In `PatternToActionListConverter.kt`, replace lines 228-258:

```kotlin
private fun transformSimpleTypeName(typeName: TypeName.SimpleTypeName): TypeNamePattern {
    val typeArgs = typeName.typeArgs.map { transformTypeName(it) }

    if (typeName.dotSeparatedParts.size == 1) {
        val name = typeName.dotSeparatedParts.single()
        if (name is MetavarName) return TypeNamePattern.MetaVar(name.metavarName)
    }

    val concreteNames = typeName.dotSeparatedParts.filterIsInstance<ConcreteName>()
    if (concreteNames.size == typeName.dotSeparatedParts.size) {
        if (concreteNames.size == 1) {
            val className = concreteNames.single().name
            if (className.first().isUpperCase()) {
                return TypeNamePattern.ClassName(className, typeArgs)
            }

            if (className in primitiveTypeNames) {
                return TypeNamePattern.PrimitiveName(className)
            }

            transformationFailed("TypeName_concrete_unexpected")
        }

        val fqn = concreteNames.joinToString(".") { it.name }
        return TypeNamePattern.FullyQualified(fqn, typeArgs)
    }

    transformationFailed("TypeName_non_concrete_unsupported")
}
```

Key changes:
- Removed `TypeArgumentsIgnored` warning emission (was line 229-231)
- Added `val typeArgs = typeName.typeArgs.map { transformTypeName(it) }` at the top
- Passed `typeArgs` to `ClassName(className, typeArgs)` and `FullyQualified(fqn, typeArgs)`

- [ ] **Step 2: Preserve return types in `transformMethodDeclaration()`**

In `PatternToActionListConverter.kt`, replace the return type handling block (lines 556-573) and the signature construction (line 614-619):

```kotlin
private fun transformMethodDeclaration(pattern: MethodDeclaration): SemgrepPatternActionList {
    val bodyPattern = transformPatternToActionList(pattern.body)
    val params = methodArgumentsToPatternList(pattern.args)

    val methodName = when (val name = pattern.name) {
        is ConcreteName -> SignatureName.Concrete(name.name)
        is MetavarName -> SignatureName.MetaVar(name.metavarName)
    }

    val returnTypePattern: TypeNamePattern? = pattern.returnType?.let { transformTypeName(it) }

    val paramConditions = mutableListOf<ParamPattern>()

    var idxIsConcrete = true
    for ((i, param) in params.withIndex()) {
        when (param) {
            is FormalArgument -> {
                val paramName = (param.name as? MetavarName)?.metavarName
                    ?: transformationFailed("MethodDeclaration_param_name_not_metavar")

                val position = if (idxIsConcrete) {
                    ParamPosition.Concrete(i)
                } else {
                    ParamPosition.Any(paramClassifier = paramName)
                }

                val paramModifiers = param.modifiers.map { transformModifier(it) }
                paramModifiers.mapTo(paramConditions) { modifier ->
                    ParamPattern(position, ParamCondition.ParamModifier(modifier))
                }

                paramConditions += ParamPattern(position, IsMetavar(MetavarAtom.create(paramName)))

                val paramType = transformTypeName(param.type)
                paramConditions += ParamPattern(position, ParamCondition.TypeIs(paramType))
            }

            is EllipsisArgumentPrefix -> {
                idxIsConcrete = false
                continue
            }

            else -> {
                transformationFailed("MethodDeclaration_parameters_not_extracted")
            }
        }
    }

    val modifiers = pattern.modifiers.map { transformModifier(it) }

    val signature = SemgrepPatternAction.MethodSignature(
        methodName, ParamConstraint.Partial(paramConditions),
        returnType = returnTypePattern,
        modifiers = modifiers,
        enclosingClassMetavar = null,
        enclosingClassConstraints = emptyList(),
    )

    return SemgrepPatternActionList(
        listOf(signature) + bodyPattern.actions,
        hasEllipsisInTheEnd = bodyPattern.hasEllipsisInTheEnd,
        hasEllipsisInTheBeginning = false
    )
}
```

Key changes:
- Replaced the entire return type guard block (lines 556-573) with a single line: `val returnTypePattern: TypeNamePattern? = pattern.returnType?.let { transformTypeName(it) }`
- Removed `MethodDeclarationReturnTypeIsArray`, `MethodDeclarationReturnTypeIsNotMetaVar`, `MethodDeclarationReturnTypeHasTypeArgs` warning emissions
- Passed `returnType = returnTypePattern` to the `MethodSignature` constructor

- [ ] **Step 3: Remove obsolete warning classes**

In `SemgrepRuleLoadErrorMessage.kt`, delete the four classes (lines 149-163):

- `TypeArgumentsIgnored`
- `MethodDeclarationReturnTypeIsArray`
- `MethodDeclarationReturnTypeIsNotMetaVar`
- `MethodDeclarationReturnTypeHasTypeArgs`

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :core:opentaint-java-querylang:compileKotlin`
Expected: BUILD SUCCESSFUL. If any code references the deleted warning classes, fix those references (they should only be in the lines we already changed).

- [ ] **Step 5: Commit**

```bash
git add core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/PatternToActionListConverter.kt
git add core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/SemgrepRuleLoadErrorMessage.kt
git commit -m "feat: stop discarding type args, array returns, concrete returns in pattern converter"
```

---

## Task 4: Update `unifyTypeName` in `MethodFormulaSimplifier` for `typeArgs`

**Files:**
- Modify: `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/MethodFormulaSimplifier.kt:769-858`

- [ ] **Step 1: Add `typeArgs` unification logic**

The `unifyTypeName` function (lines 769-858) has a pattern-match on `left` and `right`. We need to handle `typeArgs` when both sides are `ClassName` or `FullyQualified`. Add a helper and update the relevant match arms.

Add a private helper above `unifyTypeName`:

```kotlin
private fun unifyTypeArgs(
    left: List<TypeNamePattern>,
    right: List<TypeNamePattern>,
    metaVarInfo: ResolvedMetaVarInfo
): List<TypeNamePattern>? {
    if (left.isEmpty()) return right
    if (right.isEmpty()) return left
    if (left.size != right.size) return null
    val unified = left.zip(right).map { (l, r) -> unifyTypeName(l, r, metaVarInfo) ?: return null }
    return unified
}
```

Update the `ClassName`-to-`ClassName` case inside `unifyTypeName`. Currently at line 785 the match arm is:

```kotlin
is TypeNamePattern.ClassName -> when (right) {
    TypeNamePattern.AnyType -> return left

    is TypeNamePattern.ArrayType,
    is TypeNamePattern.ClassName,
    is TypeNamePattern.PrimitiveName -> return null
    // ...
```

Change the `is TypeNamePattern.ClassName` sub-case to unify names and typeArgs:

```kotlin
is TypeNamePattern.ClassName -> when (right) {
    TypeNamePattern.AnyType -> return left

    is TypeNamePattern.ArrayType,
    is TypeNamePattern.PrimitiveName -> return null

    is TypeNamePattern.ClassName -> {
        if (left.name != right.name) return null
        val args = unifyTypeArgs(left.typeArgs, right.typeArgs, metaVarInfo) ?: return null
        return TypeNamePattern.ClassName(left.name, args)
    }

    is TypeNamePattern.FullyQualified -> {
        if (right.name.endsWith(left.name)) {
            val args = unifyTypeArgs(left.typeArgs, right.typeArgs, metaVarInfo) ?: return null
            return TypeNamePattern.FullyQualified(right.name, args)
        }
        return null
    }

    is TypeNamePattern.MetaVar -> {
        if (!stringMatches(left.name, metaVarInfo.metaVarConstraints[right.metaVar])) return null
        return left
    }
}
```

Similarly update the `FullyQualified`-to-`FullyQualified` and `FullyQualified`-to-`ClassName` sub-cases:

```kotlin
is TypeNamePattern.FullyQualified -> when (right) {
    TypeNamePattern.AnyType -> return left

    is TypeNamePattern.ArrayType,
    is TypeNamePattern.PrimitiveName -> return null

    is TypeNamePattern.ClassName -> {
        if (left.name.endsWith(right.name)) {
            val args = unifyTypeArgs(left.typeArgs, right.typeArgs, metaVarInfo) ?: return null
            return TypeNamePattern.FullyQualified(left.name, args)
        }
        return null
    }

    is TypeNamePattern.FullyQualified -> {
        if (left.name != right.name) return null
        val args = unifyTypeArgs(left.typeArgs, right.typeArgs, metaVarInfo) ?: return null
        return TypeNamePattern.FullyQualified(left.name, args)
    }

    is TypeNamePattern.MetaVar -> {
        if (left.name == generatedMethodClassName) return null
        if (!stringMatches(left.name, metaVarInfo.metaVarConstraints[right.metaVar])) return null
        return left
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:opentaint-java-querylang:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/MethodFormulaSimplifier.kt
git commit -m "feat: handle typeArgs in unifyTypeName for generic type unification"
```

---

## Task 5: Update `typeNameMetaVars` in `TaintEdgesGeneration` to recurse into `typeArgs`

**Files:**
- Modify: `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/TaintEdgesGeneration.kt:355-372`

- [ ] **Step 1: Recurse into `typeArgs` for metavar extraction**

Replace the `typeNameMetaVars` function:

```kotlin
private fun MetaVarCtx.typeNameMetaVars(typeName: TypeNamePattern, metaVars: BitSet) {
    when (typeName) {
        is TypeNamePattern.MetaVar -> {
            metaVars.set(typeName.metaVar.idx())
        }

        is TypeNamePattern.ArrayType -> {
            typeNameMetaVars(typeName.element, metaVars)
        }

        TypeNamePattern.AnyType,
        is TypeNamePattern.PrimitiveName -> {
            // no metavars
        }

        is TypeNamePattern.ClassName -> {
            typeName.typeArgs.forEach { typeNameMetaVars(it, metaVars) }
        }

        is TypeNamePattern.FullyQualified -> {
            typeName.typeArgs.forEach { typeNameMetaVars(it, metaVars) }
        }
    }
}
```

Key change: `ClassName` and `FullyQualified` now recurse into their `typeArgs` to extract any embedded metavariables.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:opentaint-java-querylang:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/TaintEdgesGeneration.kt
git commit -m "feat: recurse into typeArgs for metavar extraction in typeNameMetaVars"
```

---

## Task 6: Add `typeArgs` to `SerializedTypeNameMatcher.ClassPattern`

**Files:**
- Modify: `core/opentaint-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/opentaint/dataflow/configuration/jvm/serialized/SerializedNameMatcher.kt:19-22`

- [ ] **Step 1: Add `typeArgs` field to `ClassPattern`**

```kotlin
@Serializable
data class ClassPattern(
    val `package`: SerializedSimpleNameMatcher,
    val `class`: SerializedSimpleNameMatcher,
    val typeArgs: List<SerializedTypeNameMatcher> = emptyList()   // NEW
) : SerializedTypeNameMatcher
```

- [ ] **Step 2: Verify compilation across all modules**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (empty default means all existing `ClassPattern(...)` call sites remain valid)

- [ ] **Step 3: Commit**

```bash
git add core/opentaint-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/opentaint/dataflow/configuration/jvm/serialized/SerializedNameMatcher.kt
git commit -m "feat: add typeArgs field to SerializedTypeNameMatcher.ClassPattern"
```

---

## Task 7: Propagate `typeArgs` and `returnType` in `AutomataToTaintRuleConversion`

**Files:**
- Modify: `core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/AutomataToTaintRuleConversion.kt:802-812` (typeMatcher), `:531-613` (evaluateFormulaSignature)

- [ ] **Step 1: Propagate `typeArgs` in `typeMatcher()` for `ClassName`**

In `AutomataToTaintRuleConversion.kt`, change the `ClassName` branch (lines 807-812):

```kotlin
is TypeNamePattern.ClassName -> {
    val serializedTypeArgs = typeName.typeArgs.mapNotNull { typeMatcher(it, semgrepRuleTrace)?.constraint }
    MetaVarConstraintFormula.Constraint(
        SerializedTypeNameMatcher.ClassPattern(
            `package` = anyName(),
            `class` = Simple(typeName.name),
            typeArgs = serializedTypeArgs
        )
    )
}
```

- [ ] **Step 2: Propagate `typeArgs` in `typeMatcher()` for `FullyQualified`**

For `FullyQualified` (lines 814-818), if `typeArgs` is non-empty we need a `ClassPattern` rather than `Simple`:

```kotlin
is TypeNamePattern.FullyQualified -> {
    if (typeName.typeArgs.isEmpty()) {
        MetaVarConstraintFormula.Constraint(
            Simple(typeName.name)
        )
    } else {
        val serializedTypeArgs = typeName.typeArgs.mapNotNull { typeMatcher(it, semgrepRuleTrace)?.constraint }
        val (pkg, cls) = classNamePartsFromConcreteString(typeName.name)
        MetaVarConstraintFormula.Constraint(
            SerializedTypeNameMatcher.ClassPattern(
                `package` = pkg,
                `class` = cls,
                typeArgs = serializedTypeArgs
            )
        )
    }
}
```

- [ ] **Step 3: Propagate `returnType` in `evaluateFormulaSignature()`**

In `evaluateFormulaSignature()` (around lines 531-613), after the method name and class are evaluated, add return type handling. The function returns `Pair<MethodSignature, List<RuleConditionBuilder>>`. The `RuleConditionBuilder` is what eventually gets converted to serialized rules.

Find the `RuleConditionBuilder` class definition and check if it has a `signature` or `returnType` field. If `RuleConditionBuilder` already builds `SerializedSignatureMatcher.Partial`, add the return type there.

Locate where the `MethodSignature` predicate's `returnType` should be converted:

```kotlin
// After line 560 (after buildersWithClass is populated)
// Add return type conversion
val returnTypeFormula = signature.returnType?.let { typeMatcher(it, semgrepRuleTrace) }
if (returnTypeFormula != null) {
    val returnTypeDnf = returnTypeFormula.toDNF()
    for (builder in buildersWithClass) {
        for (cube in returnTypeDnf) {
            if (cube.positive.isNotEmpty()) {
                builder.returnType = cube.positive.first().constraint
            }
        }
    }
}
```

**Note:** The exact integration depends on how `RuleConditionBuilder` manages the signature. Read the `RuleConditionBuilder` class to determine where `returnType` should be set. The builder should populate `SerializedSignatureMatcher.Partial(return = returnTypeMatcher)` when a return type is specified.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :core:opentaint-java-querylang:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/AutomataToTaintRuleConversion.kt
git commit -m "feat: propagate typeArgs and returnType through automata-to-taint conversion"
```

---

## Task 8: Add `typeArgs` to `TypeMatchesPattern` in `TaintCondition`

**Files:**
- Modify: `core/opentaint-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/opentaint/dataflow/configuration/jvm/TaintCondition.kt:119-124`

- [ ] **Step 1: Add `typeArgs` field to `TypeMatchesPattern`**

```kotlin
data class TypeMatchesPattern(
    val position: Position,
    val pattern: ConditionNameMatcher,
    val typeArgs: List<SerializedTypeNameMatcher> = emptyList(),   // NEW
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}
```

This requires adding the import:

```kotlin
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (empty default = backward compatible)

- [ ] **Step 3: Commit**

```bash
git add core/opentaint-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/opentaint/dataflow/configuration/jvm/TaintCondition.kt
git commit -m "feat: add typeArgs to TypeMatchesPattern for deferred generic matching"
```

---

## Task 9: Add `matchType(JIRType)` to `TaintConfiguration` and update `resolveIsType()`

**Files:**
- Modify: `core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/TaintConfiguration.kt:236-255` (matchType), `:281-308` (matchFunctionSignature), `:674-708` (resolveIsType)

- [ ] **Step 1: Add `matchType(JIRType)` extension function**

Add a new private extension function near the existing `match(String)` function (after line 255):

```kotlin
private fun SerializedTypeNameMatcher.matchType(type: JIRType): Boolean = when {
    // No type args on matcher → fall back to erased name matching (backward compat)
    this is ClassPattern && typeArgs.isEmpty() -> match(type.typeName)

    // Has type args → structural comparison against JIRClassType
    this is ClassPattern && type is JIRClassType -> {
        match(type.typeName) &&
        typeArgs.size == type.typeArguments.size &&
        typeArgs.zip(type.typeArguments).all { (matcher, arg) -> matcher.matchType(arg) }
    }

    // Array matching
    this is SerializedTypeNameMatcher.Array && type is JIRArrayType -> element.matchType(type.elementType)

    // Default: erased matching
    else -> match(type.typeName)
}
```

Add necessary imports at the top of the file:

```kotlin
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypedMethod
```

- [ ] **Step 2: Update `matchFunctionSignature()` to use typed matching when `typeArgs` present**

The existing `matchFunctionSignature` (lines 281-308) operates on `JIRMethod` which only has erased type names. We need to add a typed overload that accepts `JIRTypedMethod` and delegates to `matchType(JIRType)` when type args are present.

Add a new overload:

```kotlin
private fun SerializedSignatureMatcher.matchFunctionSignatureTyped(typedMethod: JIRTypedMethod): Boolean {
    when (this) {
        is SerializedSignatureMatcher.Simple -> {
            if (typedMethod.parameters.size != args.size) return false
            if (!`return`.matchType(typedMethod.returnType)) return false
            return args.zip(typedMethod.parameters).all { (matcher, param) ->
                matcher.matchType(param.type)
            }
        }

        is SerializedSignatureMatcher.Partial -> {
            val ret = `return`
            if (ret != null && !ret.matchType(typedMethod.returnType)) return false

            val params = params
            if (params != null) {
                for (param in params) {
                    val methodParam = typedMethod.parameters.getOrNull(param.index) ?: return false
                    if (!param.type.matchType(methodParam.type)) return false
                }
            }

            return true
        }
    }
}
```

Then update the call site that invokes `matchFunctionSignature`. Find where `matchFunctionSignature(method)` is called (line 230) and check if we can resolve `JIRTypedMethod`. The call is:

```kotlin
rules.removeAll { it.signature?.matchFunctionSignature(method) == false }
```

We need to check if a `SerializedSignatureMatcher` has any `ClassPattern` with non-empty `typeArgs`. If so, we need the typed method. Add a helper:

```kotlin
private fun SerializedSignatureMatcher.hasTypeArgs(): Boolean = when (this) {
    is SerializedSignatureMatcher.Simple -> false
    is SerializedSignatureMatcher.Partial -> {
        (`return` as? ClassPattern)?.typeArgs?.isNotEmpty() == true ||
        params?.any { (it.type as? ClassPattern)?.typeArgs?.isNotEmpty() == true } == true
    }
}
```

Update the call site to resolve the typed method when needed:

```kotlin
rules.removeAll { rule ->
    val sig = rule.signature ?: return@removeAll false
    if (sig.hasTypeArgs()) {
        val typedMethod = cp.findTypeOrNull(method.enclosingClass.name)
            ?.let { it as? JIRClassType }
            ?.declaredMethods
            ?.find { it.method == method }
        if (typedMethod != null) {
            !sig.matchFunctionSignatureTyped(typedMethod)
        } else {
            !sig.matchFunctionSignature(method)
        }
    } else {
        !sig.matchFunctionSignature(method)
    }
}
```

**Note:** The exact implementation depends on how `cp` (classpath) is accessed within `TaintConfiguration`. Read the class constructor and fields to find the classpath reference. It's already available — `TaintConfiguration(cp)` takes it as a constructor parameter.

- [ ] **Step 3: Update `resolveIsType()` to pass `typeArgs` through to `TypeMatchesPattern`**

In `resolveIsType()` (line 707), when the `IsType` condition's `typeIs` matcher has non-empty `typeArgs`, pass them to `TypeMatchesPattern`:

```kotlin
// Replace line 707:
// return mkOr(nonFalsePositions.map { TypeMatchesPattern(it, matcher) })
// With:
val typeArgs = when (val typeIs = normalizedTypeIs) {
    is ClassPattern -> typeIs.typeArgs
    else -> emptyList()
}
return mkOr(nonFalsePositions.map { TypeMatchesPattern(it, matcher, typeArgs) })
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :core:opentaint-jvm-sast-dataflow:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/TaintConfiguration.kt
git commit -m "feat: add typed matching with JIRType for generic type args in TaintConfiguration"
```

---

## Task 10: Resolve generic types in `JIRBasicAtomEvaluator.typeMatchesPattern()`

**Files:**
- Modify: `core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/taint/JIRBasicAtomEvaluator.kt:328-347`

- [ ] **Step 1: Extend `typeMatchesPattern()` to check generic type args**

The current method (lines 328-347) checks erased type names. When `condition.typeArgs` is non-empty, we need to resolve the generic type of the value and compare.

First, check what context is available. The constructor takes:
- `negated: Boolean`
- `positionResolver: PositionResolver<CallPositionValue>`
- `typeChecker: JIRFactTypeChecker`
- `aliasAnalysis: JIRLocalAliasAnalysis?`
- `statement: CommonInst`

We need to add a `typedMethod: JIRTypedMethod?` parameter (or access it through the analysis context). Check how `JIRBasicAtomEvaluator` is instantiated to determine the best way to thread this through.

Add `typedMethod: JIRTypedMethod?` as a constructor parameter:

```kotlin
class JIRBasicAtomEvaluator(
    private val negated: Boolean,
    private val positionResolver: PositionResolver<CallPositionValue>,
    private val typeChecker: JIRFactTypeChecker,
    private val aliasAnalysis: JIRLocalAliasAnalysis?,
    private val statement: CommonInst,
    private val typedMethod: JIRTypedMethod? = null,    // NEW
) : ConditionVisitor<Boolean>
```

Then extend `typeMatchesPattern`:

```kotlin
private fun typeMatchesPattern(value: JIRValue, condition: TypeMatchesPattern): Boolean {
    val type = value.type as? JIRRefType ?: return false

    val pattern = condition.pattern
    if (!pattern.match(type.typeName)) {
        if (pattern is ConditionNameMatcher.Concrete) {
            if (!negated && type.typeName != "java.lang.Object") {
                if (!typeChecker.typeMayHaveSubtypeOf(type.typeName, pattern.name)) return false
            } else {
                return false
            }
        } else {
            return false
        }
    }

    // Generic type args check
    if (condition.typeArgs.isNotEmpty()) {
        val genericType = resolveGenericType(value)
        if (genericType is JIRClassType) {
            if (genericType.typeArguments.size != condition.typeArgs.size) return false
            return condition.typeArgs.zip(genericType.typeArguments).all { (matcher, arg) ->
                matcher.matchType(arg)
            }
        }
        // Can't resolve generics → fall back to erased match (already passed above)
        return true
    }

    return true
}
```

Add the `resolveGenericType` helper and `matchType`:

```kotlin
private fun resolveGenericType(value: JIRValue): JIRType? {
    val localVar = value as? JIRLocalVar ?: return null
    val typedMethod = typedMethod ?: return null

    // Find the LocalVariableNode for this local variable at the current instruction
    val methodNode = typedMethod.method.methodNode ?: return null
    val localVarNode = methodNode.localVariables?.find { lvn ->
        lvn.index == localVar.index
    } ?: return null

    return typedMethod.typeOf(localVarNode)
}

private fun SerializedTypeNameMatcher.matchType(type: JIRType): Boolean = when {
    this is ClassPattern && typeArgs.isEmpty() -> matchErasedName(type.typeName)
    this is ClassPattern && type is JIRClassType -> {
        matchErasedName(type.typeName) &&
        typeArgs.size == type.typeArguments.size &&
        typeArgs.zip(type.typeArguments).all { (m, a) -> m.matchType(a) }
    }
    this is SerializedTypeNameMatcher.Array && type is JIRArrayType -> element.matchType(type.elementType)
    else -> matchErasedName(type.typeName)
}

private fun SerializedTypeNameMatcher.matchErasedName(name: String): Boolean = when (this) {
    is SerializedSimpleNameMatcher.Simple -> value == name
    is SerializedSimpleNameMatcher.Pattern -> Regex(pattern).containsMatchIn(name)
    is ClassPattern -> {
        val (pkgName, clsName) = splitClassName(name)
        `package`.matchErasedName(pkgName) && `class`.matchErasedName(clsName)
    }
    is SerializedTypeNameMatcher.Array -> {
        val nameWithout = name.removeSuffix("[]")
        name != nameWithout && element.matchErasedName(nameWithout)
    }
}
```

Add necessary imports:

```kotlin
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypedMethod
```

**Note:** The `matchType` logic here mirrors what was added to `TaintConfiguration`. If the logic is identical, consider extracting to a shared utility. However, `TaintConfiguration` lives in a different module (`jvm-sast-dataflow`) than `JIRBasicAtomEvaluator` (`jvm-dataflow`). Check module dependencies before sharing — it may be simpler to keep the two copies aligned rather than creating a new shared module.

- [ ] **Step 2: Update all call sites that create `JIRBasicAtomEvaluator`**

Search for all instantiation sites of `JIRBasicAtomEvaluator(...)` and add the `typedMethod` parameter. Use `null` where the typed method is unavailable — this preserves backward compatibility (generic checks are skipped).

Run: `grep -r "JIRBasicAtomEvaluator(" --include="*.kt"` to find all sites.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/taint/JIRBasicAtomEvaluator.kt
git add -u  # any files that instantiate JIRBasicAtomEvaluator
git commit -m "feat: resolve generic types in JIRBasicAtomEvaluator for call-site receiver matching"
```

---

## Task 11: Add E2E test samples and rules for generic type args

**Files:**
- Create: `core/opentaint-java-querylang/samples/src/main/java/example/RuleWithGenericTypeArgs.java`
- Create: `core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithGenericTypeArgs.yaml`

- [ ] **Step 1: Write the YAML rule**

Create `core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithGenericTypeArgs.yaml`:

```yaml
rules:
  - id: example-RuleWithGenericTypeArgs
    languages:
      - java
    severity: ERROR
    message: match example/RuleWithGenericTypeArgs
    patterns:
      - pattern: |-
          ...
          sink($A);
          ...
      - pattern-inside: |-
          $RET $METHOD(Map<String, Object> $M, ...) {
              ...
          }
```

- [ ] **Step 2: Write the Java sample**

Create `core/opentaint-java-querylang/samples/src/main/java/example/RuleWithGenericTypeArgs.java`:

```java
package example;

import base.RuleSample;
import base.RuleSet;
import java.util.Map;

@RuleSet("example/RuleWithGenericTypeArgs.yaml")
public abstract class RuleWithGenericTypeArgs implements RuleSample {

    void sink(String data) {}

    void methodWithGenericParam(Map<String, Object> m, String data) {
        sink(data);
    }

    void methodWithDifferentGenericParam(Map<String, String> m, String data) {
        sink(data);
    }

    void methodWithRawMapParam(Map m, String data) {
        sink(data);
    }

    final static class PositiveMatchingGenericParam extends RuleWithGenericTypeArgs {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map<String, Object> m = null;
            methodWithGenericParam(m, data);
        }
    }

    final static class NegativeDifferentGenericParam extends RuleWithGenericTypeArgs {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map<String, String> m = null;
            methodWithDifferentGenericParam(m, data);
        }
    }

    final static class NegativeRawMapParam extends RuleWithGenericTypeArgs {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map m = null;
            methodWithRawMapParam(m, data);
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add core/opentaint-java-querylang/samples/src/main/java/example/RuleWithGenericTypeArgs.java
git add core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithGenericTypeArgs.yaml
git commit -m "test: add E2E samples for generic type arg pattern matching"
```

---

## Task 12: Add E2E test samples for array and concrete return types

**Files:**
- Create: `core/opentaint-java-querylang/samples/src/main/java/example/RuleWithArrayReturnType.java`
- Create: `core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithArrayReturnType.yaml`
- Create: `core/opentaint-java-querylang/samples/src/main/java/example/RuleWithConcreteReturnType.java`
- Create: `core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithConcreteReturnType.yaml`

- [ ] **Step 1: Write the array return type YAML rule**

Create `core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithArrayReturnType.yaml`:

```yaml
rules:
  - id: example-RuleWithArrayReturnType
    languages:
      - java
    severity: ERROR
    message: match example/RuleWithArrayReturnType
    patterns:
      - pattern: |-
          ...
          sink($A);
          ...
      - pattern-inside: |-
          String[] $METHOD(..., String $A, ...) {
              ...
          }
```

- [ ] **Step 2: Write the array return type Java sample**

Create `core/opentaint-java-querylang/samples/src/main/java/example/RuleWithArrayReturnType.java`:

```java
package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithArrayReturnType.yaml")
public abstract class RuleWithArrayReturnType implements RuleSample {

    void sink(String data) {}

    String[] methodReturningStringArray(String data) {
        sink(data);
        return new String[] { data };
    }

    int[] methodReturningIntArray(String data) {
        sink(data);
        return new int[] { 1 };
    }

    String methodReturningString(String data) {
        sink(data);
        return data;
    }

    final static class PositiveStringArrayReturn extends RuleWithArrayReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningStringArray(data);
        }
    }

    final static class NegativeIntArrayReturn extends RuleWithArrayReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningIntArray(data);
        }
    }

    final static class NegativeStringReturn extends RuleWithArrayReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningString(data);
        }
    }
}
```

- [ ] **Step 3: Write the concrete return type YAML rule**

Create `core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithConcreteReturnType.yaml`:

```yaml
rules:
  - id: example-RuleWithConcreteReturnType
    languages:
      - java
    severity: ERROR
    message: match example/RuleWithConcreteReturnType
    patterns:
      - pattern: |-
          ...
          sink($A);
          ...
      - pattern-inside: |-
          String $METHOD(..., String $A, ...) {
              ...
          }
```

- [ ] **Step 4: Write the concrete return type Java sample**

Create `core/opentaint-java-querylang/samples/src/main/java/example/RuleWithConcreteReturnType.java`:

```java
package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithConcreteReturnType.yaml")
public abstract class RuleWithConcreteReturnType implements RuleSample {

    void sink(String data) {}

    String methodReturningString(String data) {
        sink(data);
        return data;
    }

    int methodReturningInt(String data) {
        sink(data);
        return 0;
    }

    void methodReturningVoid(String data) {
        sink(data);
    }

    final static class PositiveStringReturn extends RuleWithConcreteReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningString(data);
        }
    }

    final static class NegativeIntReturn extends RuleWithConcreteReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningInt(data);
        }
    }

    final static class NegativeVoidReturn extends RuleWithConcreteReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningVoid(data);
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add core/opentaint-java-querylang/samples/src/main/java/example/RuleWithArrayReturnType.java
git add core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithArrayReturnType.yaml
git add core/opentaint-java-querylang/samples/src/main/java/example/RuleWithConcreteReturnType.java
git add core/opentaint-java-querylang/samples/src/main/resources/example/RuleWithConcreteReturnType.yaml
git commit -m "test: add E2E samples for array and concrete return type matching"
```

---

## Task 13: Add E2E test class and run all tests

**Files:**
- Create: `core/opentaint-java-querylang/src/test/kotlin/org/opentaint/semgrep/TypeAwarePatternTest.kt`

- [ ] **Step 1: Write the test class**

Create `core/opentaint-java-querylang/src/test/kotlin/org/opentaint/semgrep/TypeAwarePatternTest.kt`:

```kotlin
package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(PER_CLASS)
class TypeAwarePatternTest : SampleBasedTest() {
    @Test
    fun `test generic type args in method parameter`() = runTest<example.RuleWithGenericTypeArgs>()

    @Test
    fun `test array return type matching`() = runTest<example.RuleWithArrayReturnType>()

    @Test
    fun `test concrete return type matching`() = runTest<example.RuleWithConcreteReturnType>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}
```

- [ ] **Step 2: Build the samples JAR**

Run the appropriate Gradle task to compile and package the samples JAR (needed by `SamplesDb`):

Run: `./gradlew :core:opentaint-java-querylang:samples:jar`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the new tests**

Run: `./gradlew :core:opentaint-java-querylang:test --tests "org.opentaint.semgrep.TypeAwarePatternTest"`
Expected: All 3 tests PASS

- [ ] **Step 4: Run the full test suite to verify no regressions**

Run: `./gradlew :core:opentaint-java-querylang:test`
Expected: All existing tests still PASS (backward compatibility via empty defaults)

- [ ] **Step 5: Commit**

```bash
git add core/opentaint-java-querylang/src/test/kotlin/org/opentaint/semgrep/TypeAwarePatternTest.kt
git commit -m "test: add E2E tests for type-aware pattern matching"
```

---

## Task 14: Run full project build and verify

- [ ] **Step 1: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL with no test failures

- [ ] **Step 2: Check for any remaining references to deleted warning classes**

Run: `grep -r "TypeArgumentsIgnored\|MethodDeclarationReturnTypeIsArray\|MethodDeclarationReturnTypeIsNotMetaVar\|MethodDeclarationReturnTypeHasTypeArgs" --include="*.kt"`
Expected: No matches (all references removed)

- [ ] **Step 3: Final commit if any fixes were needed**

If any fixes were required during the full build, commit them:

```bash
git add -u
git commit -m "fix: address build issues from type-aware pattern matching"
```
