# Source-Matching Enrichment for Semgrep Pattern Language Support

**Date:** 2026-04-11
**Status:** Draft
**Branch:** misonijnik/source-matching

## Problem Statement

OpenTaint translates Semgrep pattern-rules (source-level patterns) into taint configs (bytecode-level configurations). There is a gap in three areas of the Semgrep pattern language for Java:

1. **Type arguments/generics** — Currently ignored (`TypeArgumentsIgnored`). Pattern `Map<String, Object>` becomes just `Map`.
2. **Array return types** — Not supported in method declarations (`MethodDeclarationReturnTypeIsArray`). The return type constraint is skipped.
3. **Concrete return types** — Only metavariable return types supported (`MethodDeclarationReturnTypeIsNotMetaVar`). Pattern `String foo(...)` can't constrain on the `String` return type.

The root cause: JVM bytecode uses type erasure — `Map<String, Object>` and `Map<String, String>` are both `java.util.Map` in bytecode descriptors. The existing pipeline tries to translate source-level patterns directly to bytecode-level matchers, losing generic type information in the process.

## Proposed Solution: Source Pre-Resolution Enrichment

Instead of trying to match generics at the bytecode level, use the project's source code (which has full generic information) to pre-resolve patterns and generate precise bytecode-level taint rules.

### Key Insight

Semgrep patterns always match against **user project source code**, not library source code. The project source is always available during analysis (the tool already resolves source files via `JIRSourceFileResolver`). This means source-level matching is feasible for all patterns.

Example: `(Map<String, Object> $M).get(...)` — this matches a call site in the user's code where a variable declared as `Map<String, Object>` has `.get()` called on it. The library source for `java.util.Map` is never needed.

### Architecture

```
Semgrep YAML Rule
    |
    v
Parse patterns (existing pipeline)
    |
    v
[NEW] Source pre-resolution phase:
    - Scan project .java files using existing ANTLR Java parser
    - Match Semgrep patterns against source ASTs
    - Extract: exact class, method, line, erased types,
      variable bindings with full generic type info
    |
    v
Generate enriched taint rules:
    - Produce precise rules targeting exact matched locations
    - Use extracted erased types for bytecode-level matching
    - Generic type info used to filter matches (not stored in taint rules)
    |
    v
Existing bytecode IFDS analysis (unchanged)
```

### Why Not Bytecode-Only?

Three alternative approaches were considered and rejected:

**Approach A: Extend automata `MethodSignature` with return type.**
- Would add a `returnType` field to the automata predicate, flowing through to `SerializedSignatureMatcher.Partial`.
- Pros: Uses purpose-built `signature.return` field; fast matching at runtime (pre-filters before condition resolution).
- Cons: Larger change surface in automata model; risks breaking determinization/edge merging; doesn't solve generics at all.

**Approach B: Use `SerializedCondition.IsType` on `PositionBase.Result`.**
- Would add return type as a condition using the existing constraint system.
- Pros: Minimal automata change; reuses existing `IsType` → `resolveIsType()` plumbing.
- Cons: Doesn't solve generics; condition-based matching evaluates later than signature filtering.

**Both A and B fail on generics** because `JIRMethod.returnType.typeName` is the erased bytecode type (from `MethodInfo.returnClass` which uses `Type.getReturnType(desc).className`). The `JIRTypedMethod` has full generic info, but the matching infrastructure uses `JIRMethod`.

**Approach C (this design): Source pre-resolution enrichment.**
- Matches patterns against source ASTs where full type information (generics, arrays, concrete types) is available.
- Generates precise taint rules from the matched information.
- Solves all three gaps uniformly.

## Existing Infrastructure

### What Already Exists

| Component | Location | Relevance |
|---|---|---|
| ANTLR Java grammar | `opentaint-java-querylang` (`JavaLexer.g4`, `JavaParser.g4`) | Parse project source files |
| Java AST parsing | `JavaAstSpanResolver` | Already parses `.java` into ANTLR parse trees |
| Semgrep pattern parser | `SemgrepJavaPatternParser` | Produces `SemgrepJavaPattern` AST from pattern strings |
| Source file resolver | `JIRSourceFileResolver` | Locates `.java` files from bytecode classes |
| Project source root | `Project.sourceRoot`, `Module.moduleSourceRoot` | Root paths for source files |
| Pattern AST types | `SemgrepJavaPattern.kt` | Full pattern representation including `TypeName.SimpleTypeName.typeArgs` and `TypeName.ArrayTypeName` |
| Type name patterns | `TypeNamePattern` in `ParamCondition.kt` | Already has `ArrayType`, `ClassName`, `FullyQualified`, `MetaVar`, `AnyType` |
| Serialized type matchers | `SerializedTypeNameMatcher` | `ClassPattern`, `Array` variants for bytecode matching |
| Serialized signature matchers | `SerializedSignatureMatcher.Partial` | Already has `return: SerializedTypeNameMatcher?` and `params` fields |
| Runtime signature matching | `TaintConfiguration.kt:281-307` | `matchFunctionSignature()` already evaluates `return` on `Partial` matchers |

### What's New (Needs Implementation)

1. **Source pattern matcher** — Matches `SemgrepJavaPattern` nodes against ANTLR `JavaParser` parse tree nodes. Must support:
   - Method invocations with typed receiver (`(Type<Args> $X).method(...)`)
   - Method declarations with return types (concrete, array, generic)
   - Object creation with type arguments (`new Type<Args>(...)`)
   - Variable declarations with full type info
   - Metavariable binding and ellipsis handling
   - `pattern-inside` / `pattern-not-inside` structural constraints

2. **Match-to-taint-rule converter** — Takes source match results and produces `SerializedRule` instances with precise function matchers and signatures.

3. **Integration into analysis pipeline** — A new phase between rule loading and bytecode analysis that scans source files and enriches taint rules.

## Key Data Flow Details

### Pattern Parsing (existing)

The `SemgrepJavaPattern` AST already correctly represents all three gap features:

```kotlin
// TypeName already supports generics and arrays:
sealed interface TypeName {
    data class SimpleTypeName(
        val dotSeparatedParts: List<Name>,
        val typeArgs: List<TypeName> = emptyList()  // <-- generics preserved
    ) : TypeName

    data class ArrayTypeName(val elementType: TypeName) : TypeName  // <-- arrays preserved
}

// MethodDeclaration already carries return type:
data class MethodDeclaration(
    val name: Name,
    val returnType: TypeName?,  // <-- concrete return types preserved
    val args: MethodArguments,
    val body: SemgrepJavaPattern,
    val modifiers: List<Modifier>,
)
```

The information is parsed correctly — it's only discarded during the `PatternToActionListConverter` step (lines 229-231, 559-571).

### Where the Gaps Are Triggered

In `PatternToActionListConverter.transformMethodDeclaration()` (lines 547-573):

```kotlin
// Return type handling — currently discards everything except metavar:
val retType = pattern.returnType
if (retType != null) {
    run {
        if (retType !is TypeName.SimpleTypeName) {
            semgrepTrace?.error(MethodDeclarationReturnTypeIsArray())  // Gap 2: array skipped
            return@run
        }
        val retTypeMetaVar = retType.dotSeparatedParts.singleOrNull() as? MetavarName
        if (retTypeMetaVar == null) {
            semgrepTrace?.error(MethodDeclarationReturnTypeIsNotMetaVar())  // Gap 3: concrete skipped
        }
        if (retType.typeArgs.isNotEmpty()) {
            semgrepTrace?.error(MethodDeclarationReturnTypeHasTypeArgs())  // Gap 1 (return-specific)
        }
    }
}
```

In `PatternToActionListConverter.transformSimpleTypeName()` (lines 228-231):

```kotlin
// Type arguments — currently discarded everywhere:
if (typeName.typeArgs.isNotEmpty()) {
    semgrepTrace?.error(TypeArgumentsIgnored())  // Gap 1: generics dropped
}
```

### Taint Rule Generation (existing, to be leveraged)

Generated rules currently always pass `signature = null`:
```kotlin
SerializedRule.Source(function, signature = null, overrides = true, cond, actions, info)
```

After source matching, we can populate `signature` with precise matchers:
```kotlin
SerializedRule.Source(
    function = SerializedFunctionNameMatcher.Complex(package, class, method),
    signature = SerializedSignatureMatcher.Partial(
        params = listOf(SerializedArgMatcher(0, Simple("java.util.Map"))),
        `return` = Simple("java.util.List")
    ),
    overrides = true,
    condition = cond,
    taint = actions,
    info = info
)
```

### Runtime Matching (existing, already works)

`TaintConfiguration.matchFunctionSignature()` already handles `Partial` signatures:
```kotlin
is SerializedSignatureMatcher.Partial -> {
    val ret = `return`
    if (ret != null && !ret.match(method.returnType.typeName)) return false
    // params matching...
    return true
}
```

This uses `method.returnType.typeName` (erased type), which is correct — the source matching phase already filtered by full generic types and only the erased type needs to be verified at bytecode level.

## Open Design Questions

### 1. Enrichment Granularity

**Method-level:** Source matching identifies which methods match the pattern. Generated taint rules target those methods by class + name + descriptor.

**Call-site-level:** Source matching identifies specific call sites (class + method + bytecode instruction). Can distinguish two `Map` variables with different type args in the same method.

The `(Map<String, Object> $M).get(...)` example motivates call-site-level: two `Map` variables in the same method with different type arguments should be distinguishable. This may require taint rules to reference specific program points, which is an extension to the current rule model.

**Decision needed:** What level of granularity is required?

### 2. Fallback Behavior

When source files are unavailable (e.g., analyzing a JAR without sources), should the system:
- Fall back to current bytecode-only matching (with the existing warnings)?
- Refuse to apply rules that require source matching?
- Apply best-effort bytecode matching (ignoring generics)?

**Decision needed:** Fallback strategy.

### 3. Incremental vs. Full Scan

Should source matching:
- Scan all source files for every rule?
- Build an index of declarations/invocations and query it per-rule?
- Use the existing class index to narrow which files to scan?

**Decision needed:** Performance strategy.

### 4. Pattern Language Scope

This design focuses on three specific gaps. Should the source matching engine also handle other currently-unsupported features?
- `pattern-regex` (matching raw source text) — natural fit for source matching
- `metavariable-comparison` — could extract constant values from source
- Complex `metavariable-pattern` — nested source-level constraints

**Decision needed:** Initial scope vs. extensibility plan.

## Test Strategy

### Unit Tests

- Source pattern matcher: test each pattern construct (invocations, declarations, generics, arrays) against known Java source snippets
- Match-to-rule converter: test that extracted source info produces correct `SerializedRule` instances
- Type resolution: test that generic types are correctly extracted and erased types derived

### Integration Tests

- End-to-end: YAML rule + Java source file -> enriched taint rules -> correct findings
- Regression: existing rules continue to work (no behavioral changes for rules that don't use generics/arrays/concrete return types)

### E2E Tests (Rules Test System)

The existing rules test infrastructure (`@PositiveRuleSample` / `@NegativeRuleSample` annotations, `checkRulesCoverage` task) should be extended with:

- Test samples using generic types (e.g., `Map<String, Object>`, `List<String>`)
- Test samples with array return types
- Test samples with concrete return types
- Negative samples verifying that `Map<String, String>` does NOT match a pattern for `Map<String, Object>`

These tests exercise the full pipeline: YAML rule -> source matching -> taint rule -> bytecode analysis -> SARIF output.

## File Impact Summary

### New Files (Estimated)

| File | Purpose |
|---|---|
| `SourcePatternMatcher.kt` | Matches `SemgrepJavaPattern` against ANTLR parse trees |
| `SourceMatchResult.kt` | Data classes for match results (class, method, types, positions) |
| `SourceMatchToTaintRuleConverter.kt` | Converts match results to `SerializedRule` instances |
| `SourcePreResolutionPhase.kt` | Orchestrates source scanning and rule enrichment |

### Modified Files (Estimated)

| File | Change |
|---|---|
| `PatternToActionListConverter.kt` | Route patterns needing source matching to the new phase instead of emitting warnings |
| `SemgrepRuleAutomataBuilder.kt` | Integrate source pre-resolution before automata build |
| `ProjectAnalyzerRunner.kt` | Add source pre-resolution phase to analysis pipeline |

### Unchanged

- `TaintCondition.kt` — No new condition types needed
- `SerializedSignatureMatcher.kt` — `Partial` already supports `return` and `params`
- `TaintConfiguration.kt` — Runtime matching already handles populated signatures
- IFDS dataflow engine — Unchanged
