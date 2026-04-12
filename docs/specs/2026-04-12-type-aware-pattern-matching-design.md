# Type-Aware Pattern Matching for Semgrep Pattern Language

**Date:** 2026-04-12
**Status:** Approved
**Branch:** misonijnik/source-matching
**Supersedes:** `2026-04-11-source-matching-enrichment-design.md` (the "source pre-resolution" approach is replaced by this simpler plumbing fix)

## Problem Statement

OpenTaint translates Semgrep pattern-rules (source-level patterns) into taint configs (bytecode-level configurations). Three Semgrep pattern language features for Java are currently broken:

1. **Type arguments/generics** — Pattern `Map<String, Object>` becomes just `Map` (`TypeArgumentsIgnored` warning at `PatternToActionListConverter.kt:229`)
2. **Array return types** — Pattern `String[] foo(...)` loses the return type constraint (`MethodDeclarationReturnTypeIsArray` at line 559)
3. **Concrete return types** — Pattern `String foo(...)` loses the return type constraint (`MethodDeclarationReturnTypeIsNotMetaVar` at line 565)

Two matching scenarios are affected:

- **Scenario 1 (method declarations):** `String[] foo(Map<String, Object> $M, ...)` — constraining the declared method's return type and parameter types
- **Scenario 2 (call-site receivers):** `(Map<String, Object> $M).get(...)` — constraining the generic type of the receiver variable at the call site

## Key Insight: Bytecode Already Has the Type Information

The JVM preserves generic type signatures in bytecode through two mechanisms:

1. **Signature attribute** on classes, methods, and fields — preserves full generic signatures (e.g., `Ljava/util/List<Ljava/lang/String;>;`). Already parsed by `JIRTypedMethod` via `MethodSignature.kt` / `FieldSignature.kt`.

2. **LocalVariableTypeTable attribute** — preserves generic signatures for local variables. Already accessible via `JIRTypedMethod.typeOf(LocalVariableNode)` at `JIRTypedMethodImpl.kt:119`.

**No source parsing, no new IR levels, no multi-level architecture needed.** The fix is plumbing: stop discarding type information in the conversion pipeline and use the typed method infrastructure that already exists in the IR.

## Design

### Architecture Overview

The change flows through the existing pipeline without introducing new stages:

```
Semgrep YAML Rule
    │
    ▼
SemgrepJavaPattern (pattern AST — already preserves type args, arrays, concrete types)
    │
    ▼
PatternToActionListConverter ──► SemgrepPatternAction with TypeNamePattern
    │                            NOW PRESERVES: typeArgs, array returns, concrete returns
    ▼
ActionListToAutomata ──► SemgrepRuleAutomata (TypeNamePattern passes through unchanged)
    │
    ▼
AutomataToTaintRuleConversion.typeMatcher() ──► SerializedRule with SerializedTypeNameMatcher
    │                                           NOW CARRIES: typeArgs on ClassPattern
    ▼
TaintConfiguration.matchFunctionSignature() ──► matches against JIRTypedMethod (generic types)
    │                                           INSTEAD OF: JIRMethod (erased types)
    ▼
JIRBasicAtomEvaluator.typeMatchesPattern() ──► resolves receiver local var generic type
                                               via LocalVariableTypeTable
```

### Change 1: Preserve Type Args in TypeNamePattern

**File:** `core/opentaint-java-querylang/.../conversion/SemgrepPatternAction.kt`

Add `typeArgs` field to `ClassName` and `FullyQualified`:

```kotlin
sealed interface TypeNamePattern {
    data class ClassName(
        val name: String,
        val typeArgs: List<TypeNamePattern> = emptyList()   // NEW
    ) : TypeNamePattern

    data class FullyQualified(
        val name: String,
        val typeArgs: List<TypeNamePattern> = emptyList()   // NEW
    ) : TypeNamePattern

    // ArrayType, PrimitiveName, MetaVar, AnyType — unchanged
}
```

**File:** `core/opentaint-java-querylang/.../conversion/PatternToActionListConverter.kt`

Three changes:

1. **`transformSimpleTypeName()` (line 228-231):** Remove the `TypeArgumentsIgnored` warning. Map `typeName.typeArgs` to `TypeNamePattern` recursively:
   ```kotlin
   private fun transformSimpleTypeName(typeName: TypeName.SimpleTypeName): TypeNamePattern {
       val typeArgs = typeName.typeArgs.map { transformTypeName(it) }
       // ... existing name resolution logic ...
       return TypeNamePattern.ClassName(className, typeArgs)
   }
   ```

2. **`transformMethodDeclaration()` (lines 559-571):** Remove the three return-type guards. Flow the return type through:
   ```kotlin
   val retType = pattern.returnType
   if (retType != null) {
       val retTypePattern = transformTypeName(retType)
       // Use retTypePattern in method signature action
   }
   ```

3. **Populate signature on emitted actions:** The `MethodSignature` action already has a `methodName` and `params` — add a `returnType: TypeNamePattern?` field to carry the return type pattern. The downstream `evaluateFormulaSignature()` in `AutomataToTaintRuleConversion.kt` will convert this to `SerializedSignatureMatcher.Partial(return = ...)` using the existing `typeMatcher()` function.

### Change 2: Carry Type Args Through Serialization

**File:** `core/opentaint-configuration-rules/.../serialized/SerializedNameMatcher.kt`

Add `typeArgs` to `ClassPattern`:

```kotlin
sealed interface SerializedTypeNameMatcher {
    data class ClassPattern(
        val `package`: SerializedSimpleNameMatcher,
        val `class`: SerializedSimpleNameMatcher,
        val typeArgs: List<SerializedTypeNameMatcher> = emptyList()   // NEW
    ) : SerializedTypeNameMatcher

    data class Array(val element: SerializedTypeNameMatcher) : SerializedTypeNameMatcher
    // rest unchanged
}
```

**File:** `core/opentaint-java-querylang/.../taint/AutomataToTaintRuleConversion.kt`

In `typeMatcher()` (line 802-892), propagate type args:

```kotlin
is TypeNamePattern.ClassName -> MetaVarConstraintFormula.Constraint(
    SerializedTypeNameMatcher.ClassPattern(
        `package` = anyName(),
        `class` = Simple(typeName.name),
        typeArgs = typeName.typeArgs.mapNotNull { typeMatcher(it, semgrepRuleTrace)?.constraint }
    )
)
```

### Change 3: Match Against JIRTypedMethod at Runtime (Scenario 1)

**File:** `core/opentaint-jvm-sast-dataflow/.../rules/TaintConfiguration.kt`

**`matchFunctionSignature()` (lines 281-308):** Change parameter from `JIRMethod` to `JIRTypedMethod` (or accept both, resolving typed from erased via classpath lookup):

```kotlin
private fun SerializedSignatureMatcher.matchFunctionSignature(typedMethod: JIRTypedMethod): Boolean {
    when (this) {
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
        // Simple matcher handling similar
    }
}
```

New `matchType()` overload on `SerializedTypeNameMatcher`:

```kotlin
private fun SerializedTypeNameMatcher.matchType(type: JIRType): Boolean = when {
    // No type args → fall back to erased name matching (backward compat)
    this is ClassPattern && typeArgs.isEmpty() -> match(type.erasedName)

    // Has type args → structural comparison against JIRClassType
    this is ClassPattern && type is JIRClassType -> {
        match(type.erasedName) &&
        typeArgs.size == type.typeArguments.size &&
        typeArgs.zip(type.typeArguments).all { (matcher, arg) -> matcher.matchType(arg) }
    }

    // Array matching
    this is Array && type is JIRArrayType -> element.matchType(type.elementType)

    // Default: erased matching
    else -> match(type.erasedName)
}
```

**Resolving `JIRTypedMethod` from `JIRMethod`:** The call sites that invoke `matchFunctionSignature()` need to resolve the typed method. `JIRClassType.lookup` or `JIRClassType.declaredMethods` provide `JIRTypedMethod` instances. The classpath is already available in `TaintConfiguration`.

### Change 4: Call-Site Receiver Generic Matching (Scenario 2)

**File:** `core/opentaint-configuration-rules/.../TaintCondition.kt`

Extend `TypeMatchesPattern` to carry type args:

```kotlin
data class TypeMatchesPattern(
    val position: Position,
    val pattern: ConditionNameMatcher,
    val typeArgs: List<SerializedTypeNameMatcher> = emptyList()   // NEW
) : Condition
```

**File:** `core/opentaint-jvm-sast-dataflow/.../rules/TaintConfiguration.kt`

In `resolveIsType()` (lines 674-708), when the `IsType` matcher has `typeArgs` and position is `This`:

```kotlin
is This -> {
    // Erased class check (existing)
    if (!normalizedTypeIs.match(method.enclosingClass.name)) {
        // ... existing super-hierarchy check ...
    }
    // When type args present, defer to instruction-level evaluation
    if (normalizedTypeIs.hasTypeArgs()) {
        return TypeMatchesPattern(This, matcher, normalizedTypeIs.typeArgs)
    }
    // Otherwise: existing eager resolution
}
```

**File:** `core/opentaint-jvm-sast-dataflow/.../JIRBasicAtomEvaluator.kt`

In `typeMatchesPattern()` (lines 328-347), when `typeArgs` is non-empty:

```kotlin
override fun visit(condition: TypeMatchesPattern): Condition {
    // Existing erased type check first
    val value = positionResolver.resolve(condition.position) ?: return condition
    val type = value.type as? JIRRefType ?: return mkFalse()
    if (!condition.pattern.matches(type.typeName)) return mkFalse()

    // NEW: Generic type args check
    if (condition.typeArgs.isNotEmpty()) {
        val genericType = resolveGenericType(value)
        if (genericType is JIRClassType) {
            if (genericType.typeArguments.size != condition.typeArgs.size) return mkFalse()
            val allMatch = condition.typeArgs.zip(genericType.typeArguments).all { (matcher, arg) ->
                matcher.matchType(arg)
            }
            return allMatch.asCondition()
        }
        // Can't resolve generics → fall back to erased match (true, already passed above)
        return mkTrue()
    }
    // ... existing logic
}

private fun resolveGenericType(value: JIRValue): JIRType? {
    // 1. Get the local variable index from the JIRValue
    val localVarIndex = (value as? JIRLocalVar)?.index ?: return null

    // 2. Find the LocalVariableNode for this index at the current instruction
    val localVarNode = findLocalVariable(localVarIndex) ?: return null

    // 3. Resolve generic type via JIRTypedMethod
    return typedMethod.typeOf(localVarNode)
}
```

**Context requirements:** `JIRBasicAtomEvaluator` needs access to:
- The enclosing `JIRTypedMethod` (for `typeOf()`)
- The ASM `MethodNode.localVariables` list (for `LocalVariableNode` lookup)
- The current instruction (for scoping the `LocalVariableNode` to the right range)

These are available through the `analysisContext` and the `statement` already passed to the evaluator.

### Graceful Degradation

| Scenario | Behavior |
|---|---|
| `typeArgs` empty on matcher | Erased matching — identical to current behavior |
| Bytecode has no Signature attribute | `JIRTypedMethod` falls back to erased types → type args comparison skipped |
| `LocalVariableTypeTable` absent | `LocalVariableNode.signature` is null → `typeOf()` returns erased type → type args check skipped |
| Receiver is not a local variable | `resolveGenericType()` returns null → falls back to erased matching |
| Existing rules without generics | All `typeArgs` fields default to empty → zero behavior change |

### Backward Compatibility

All changes are additive:
- `TypeNamePattern.ClassName.typeArgs` defaults to `emptyList()`
- `SerializedTypeNameMatcher.ClassPattern.typeArgs` defaults to `emptyList()`
- `TypeMatchesPattern.typeArgs` defaults to `emptyList()`
- When empty, every code path follows the existing logic exactly
- Serialization format: `typeArgs` is a new optional field — existing serialized configs deserialize with empty list

## Files Changed

### Modified Files

| File | Module | Change |
|---|---|---|
| `SemgrepPatternAction.kt` | opentaint-java-querylang | Add `typeArgs` to `TypeNamePattern.ClassName`, `FullyQualified` |
| `PatternToActionListConverter.kt` | opentaint-java-querylang | Stop discarding type args (line 229), array returns (line 559), concrete returns (line 565); flow type info through |
| `AutomataToTaintRuleConversion.kt` | opentaint-java-querylang | `typeMatcher()`: propagate `typeArgs` to `ClassPattern` |
| `SerializedNameMatcher.kt` | opentaint-configuration-rules | Add `typeArgs` to `ClassPattern` |
| `TaintCondition.kt` | opentaint-configuration-rules | Add `typeArgs` to `TypeMatchesPattern` |
| `TaintConfiguration.kt` | opentaint-jvm-sast-dataflow | `matchFunctionSignature()`: use `JIRTypedMethod`; `resolveIsType()`: defer generic checks |
| `JIRBasicAtomEvaluator.kt` | opentaint-jvm-sast-dataflow | `typeMatchesPattern()`: resolve local var generic types when `typeArgs` present |

### No New Files

This is a plumbing fix across the existing pipeline — no new modules, classes, or architectural layers.

### Unchanged

| Component | Why Unchanged |
|---|---|
| `SemgrepJavaPattern.kt` | Already preserves type args and array types correctly |
| `ActionListToAutomata.kt` | `TypeNamePattern` passes through untransformed |
| `SerializedSignatureMatcher.kt` | `Partial` already has `return` and `params` fields |
| `JIRTypedMethod` / `JIRTypedMethodImpl` | Already resolves generics from Signature attribute |
| IFDS dataflow engine | Unchanged — condition evaluation is extended, not the engine |
| SARIF reporting | Unchanged |

## Test Strategy

### Unit Tests

- **TypeNamePattern with type args:** Verify `transformSimpleTypeName()` preserves `typeArgs` from pattern AST
- **typeMatcher() propagation:** Verify `TypeNamePattern.ClassName(name, typeArgs)` → `ClassPattern(pkg, cls, typeArgs)`
- **matchType() with generics:** `ClassPattern("Map", typeArgs=[Simple("String"), Simple("Object")])` matches `JIRClassType(Map, typeArgs=[String, Object])` but not `JIRClassType(Map, typeArgs=[String, String])`
- **matchType() without generics:** `ClassPattern("Map", typeArgs=[])` matches any `Map` regardless of type args (backward compat)
- **matchFunctionSignature() with JIRTypedMethod:** Return type and parameter type generic matching
- **resolveGenericType():** Local variable index → `LocalVariableNode` → `typeOf()` → correct generic type
- **Graceful degradation:** Missing Signature attribute, missing LocalVariableTypeTable, non-local-variable receiver

### Integration Tests

- End-to-end: YAML rule with `Map<String, Object>` pattern → correct taint findings
- End-to-end: `String[] foo(...)` pattern → correct return type matching
- End-to-end: `(List<String> $L).get(...)` → matches only `List<String>` receivers, not `List<Integer>`
- Regression: all existing rules produce identical results

### E2E Test Samples

Extend existing `@PositiveRuleSample` / `@NegativeRuleSample` annotations:

- Positive: `Map<String, Object> m = ...; m.get(key)` with pattern `(Map<String, Object> $M).get(...)`
- Negative: `Map<String, String> m = ...; m.get(key)` with same pattern (should NOT match)
- Positive: `String[] foo()` with pattern `String[] foo(...)`
- Positive: `List<String> bar()` with pattern `List<String> bar(...)`
- Negative: `List<Integer> bar()` with same pattern

## Open Questions Resolved

| Question (from previous spec) | Resolution |
|---|---|
| Enrichment granularity? | Method-level for signatures, instruction-level for call-site receivers |
| Fallback when sources unavailable? | Not applicable — all type info comes from bytecode, not source |
| Incremental vs. full scan? | Not applicable — no source scanning |
| Pattern language scope? | Scoped to type args, array returns, concrete returns. Other features (`pattern-regex`, `metavariable-comparison`) are separate work |
| Multi-level IR needed? | No — plumbing fix on existing pipeline |
