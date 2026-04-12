# Symbolic Sequence Alignment: Source-to-Bytecode Linking Without Debug Info

**Date:** 2026-04-11
**Status:** Research Note
**Context:** Multi-level IR design for Semgrep pattern language support

## Problem

Given a Java source file (parsed into an ANTLR AST) and the corresponding `.class` file (parsed into JIR bytecode instructions), establish a reliable mapping between specific source-level constructs (method calls, field accesses, object creations) and their corresponding bytecode instructions — **without relying on debug information** (`LineNumberTable`, `LocalVariableTable`).

Debug info is unreliable because:
- It can be stripped (`-g:none`)
- It provides only line-level granularity (multiple statements per line are ambiguous)
- It's compiler-specific in format details
- It doesn't exist for generated/synthetic code

## Core Insight: JLS-Mandated Evaluation Order

The Java Language Specification mandates **left-to-right evaluation order** for:
- Operands of binary operators (JLS 15.7)
- Arguments in method invocations (JLS 15.12.4.2)
- Array dimensions in array creation (JLS 15.10.1)

This means the sequence of symbolic references (method calls, field accesses) in bytecode is **specification-mandated**, not a compiler implementation detail. Any conforming compiler (javac, ECJ, Kotlin compiler targeting Java interop) must produce them in the same evaluation order.

## Algorithm

### Overview

```
Source AST (ANTLR)                  Bytecode (ASM/JIR)
       |                                   |
  [Walk in evaluation order]        [Walk instruction sequence]
       |                                   |
  [Extract symbolic refs:           [Extract symbolic refs:
   method calls, field              invoke*, getfield,
   accesses, object                 putfield, new +
   creations, constants]            invokespecial <init>,
       |                            ldc constants]
       |                                   |
       |                            [Filter synthetic refs
       |                             using pattern catalog]
       |                                   |
       +------>  SEQUENCE ALIGN  <---------+
                      |
              [Matched pairs:
               AST node <-> bytecode offset]
```

### Step 1: Extract Symbolic Reference Sequence from Bytecode

Walk all instructions in a method body. For each instruction that references a symbolic name, record a `BytecodeRef`:

```kotlin
data class BytecodeRef(
    val offset: Int,            // bytecode offset
    val kind: RefKind,          // INVOKE, FIELD_GET, FIELD_PUT, NEW, CONSTANT
    val owner: String,          // owning class (internal name)
    val name: String,           // method/field name
    val descriptor: String,     // JVM descriptor
    val isSynthetic: Boolean,   // identified as compiler-generated
)

enum class RefKind { INVOKE, FIELD_GET, FIELD_PUT, NEW, CONSTANT }
```

Instructions that produce refs:
- `invokevirtual`, `invokeinterface`, `invokestatic`, `invokespecial` -> `INVOKE`
- `getfield`, `getstatic` -> `FIELD_GET`
- `putfield`, `putstatic` -> `FIELD_PUT`
- `new` (paired with `invokespecial <init>`) -> `NEW`
- `ldc`, `ldc_w`, `ldc2_w` -> `CONSTANT`

### Step 2: Extract Symbolic Reference Sequence from Source AST

Walk the AST in **evaluation order** (left-to-right, depth-first — matching JLS semantics). For each method call, field access, object creation, or constant, record a `SourceRef`:

```kotlin
data class SourceRef(
    val node: ParserRuleContext,  // ANTLR AST node
    val kind: RefKind,
    val name: String,             // method/field name as written in source
    val argCount: Int?,           // for method calls, number of arguments
)
```

The evaluation-order walk must handle:
- Nested expressions: `a.foo(b.bar())` produces `[bar, foo]` (callee args evaluated before the call)
- Chained calls: `a.foo().bar()` produces `[foo, bar]`
- Binary operators: `a.x() + b.y()` produces `[x, y]` (left before right)
- Short-circuit: `a.x() && b.y()` produces `[x, y]` but `y` is conditional (still in order)

### Step 3: Filter Synthetic Bytecode References

Java compilation introduces bytecode instructions with no corresponding source construct. These must be identified and tagged before alignment.

#### Synthetic Pattern Catalog

| Source Pattern | Synthetic Bytecode (pre-Java 9) | Synthetic Bytecode (Java 9+) |
|---|---|---|
| String `+` | `new StringBuilder`, `.append()` chain, `.toString()` | `invokedynamic makeConcatWithConstants` |
| Enhanced for (Iterable) | `.iterator()`, `.hasNext()`, `.next()` | same |
| Enhanced for (array) | `arraylength` | same |
| Autoboxing | `Integer.valueOf()`, `Long.valueOf()`, etc. | same |
| Unboxing | `.intValue()`, `.longValue()`, etc. | same |
| Try-with-resources | `.close()`, `addSuppressed()` | same |
| Assert | `getstatic $assertionsDisabled`, `new AssertionError` | same |
| Enum switch | synthetic `$SwitchMap$...` array access | same |
| Lambda | `invokedynamic` (LambdaMetafactory) | same |
| String switch | `.hashCode()`, `.equals()` on switch expression | same |
| Instanceof pattern (16+) | `checkcast` after `instanceof` | same |
| Record accessors | synthetic accessor methods | same |

Detection heuristics:
- **Bridge methods**: `ACC_BRIDGE` flag in method access flags
- **Synthetic methods**: `ACC_SYNTHETIC` flag
- **Lambda bodies**: Method name matches `lambda$<enclosing>$<n>` pattern
- **String concat**: `makeConcatWithConstants` bootstrap method
- **Boxing/unboxing**: Calls to `<WrapperType>.valueOf()` or `.<primitive>Value()` that don't appear in source
- **Iterator protocol**: Sequence `iterator() -> hasNext() -> next()` within a loop structure

### Step 4: Sequence Alignment

Align the filtered bytecode refs with the source refs using a variant of the Longest Common Subsequence (LCS) algorithm with domain-specific scoring:

**Strong match** (high score):
- Same method/field name AND compatible descriptor AND same ref kind
- Example: source `obj.parse(x)` <-> bytecode `invokevirtual Foo.parse:(Ljava/lang/String;)I`

**Partial match** (medium score):
- Same method/field name AND same ref kind, but descriptor can't be verified (no type resolution on source side)
- Example: source `obj.process(x)` <-> bytecode `invokevirtual Foo.process:(I)V` (we know the name matches but can't verify arg types from source alone)

**No match** (skip):
- Unmatched bytecode refs -> synthetic (compiler-generated)
- Unmatched source refs -> inlined constants or optimized away

For most methods, alignment is trivial: after filtering synthetics, the sequences are the same length and in the same order, giving 1:1 correspondence.

## Reliability Assessment

| Construct | Reliability | Notes |
|---|---|---|
| Simple method calls | **Excellent** | Name + descriptor + order = unambiguous |
| Field accesses | **Excellent** | Name + owner class + order |
| Object creation (`new`) | **Excellent** | `new` + `invokespecial <init>` pattern is invariant |
| String concatenation | **Good** | Need version-aware synthetic detection |
| Enhanced for-loops | **Good** | Well-defined iterator/array patterns |
| Lambdas | **Good** | `invokedynamic` is recognizable; body in synthetic method |
| Try-with-resources | **Moderate** | Complex synthetic code, well-defined pattern |
| Inlined constants | **Moderate** | Match by value (`ldc` value = source literal value) |
| Overloaded methods | **Depends** | Without type resolution, arg count disambiguates many cases |
| Compiler independence | **Good** | Symbolic sequence is JLS-mandated; only synthetic catalog varies |

## Edge Cases and Mitigations

### Overloaded Methods

When source has `obj.foo(x)` and the class has multiple `foo` methods, the bytecode descriptor disambiguates but the source ref may not carry type info.

**Mitigation**: Use argument count as a discriminator. If ambiguity remains, the alignment algorithm can use positional context (surrounding matched refs) to resolve.

### Conditional Evaluation (Short-Circuit, Ternary)

`a.x() && b.y()` — both calls appear in bytecode but `y()` is behind a branch. The symbolic sequence still has both refs in source order; they just appear in different basic blocks in bytecode.

**Mitigation**: Flatten the bytecode control flow for alignment purposes — walk all basic blocks in a linearized order that respects source evaluation order.

### Nested Lambdas

Lambda bodies are compiled to separate synthetic methods. The lambda *creation* (invokedynamic) appears in the enclosing method's bytecode.

**Mitigation**: Align the lambda creation point in the enclosing method. Lambda body matching is a separate alignment pass on the synthetic method vs. the lambda expression's AST subtree.

### Compiler-Specific Optimizations

Some compilers may perform limited optimizations (constant folding, dead code elimination).

**Mitigation**: The alignment algorithm tolerates gaps (unmatched refs on either side). Gaps are expected and handled gracefully.

## Complexity Estimate

| Component | Lines (est.) | Complexity |
|---|---|---|
| Bytecode symbolic ref extraction | ~200 | Low (ASM visitor) |
| AST evaluation-order walk | ~500-800 | Medium (handle all expression types) |
| Synthetic pattern catalog | ~300-500 | Medium (version-aware, needs maintenance) |
| Sequence alignment | ~100-200 | Low (LCS variant) |
| **Total** | **~1100-1700** | **Medium** |

## Relationship to Existing Infrastructure

### What Already Exists in OpenTaint

| Component | Used By | Can Reuse? |
|---|---|---|
| `JavaAstSpanResolver` | SARIF reporting | **Yes** — already walks AST + matches by instruction kind + method name. Currently uses line numbers as primary filter; could be extended with symbolic alignment as primary strategy. |
| `JIRSourceFileResolver` | Class-to-file mapping | **Yes** — narrows which source file to parse for a given bytecode class. |
| `JIRTypedMethod` | Type resolution | **Yes** — provides full generic types from Signature attribute. For method-level matching, this may suffice without source alignment. |
| `RawInstListBuilder` | Bytecode loading | **Yes** — already walks all instructions. Symbolic ref extraction can piggyback. |
| `JIRCallExpr` | Instruction metadata | **Yes** — already carries callee name, descriptor, owner class. This IS the bytecode symbolic ref. |

### Key Difference from Current Approach

Current `JavaAstSpanResolver` strategy:
1. Get line number from bytecode instruction
2. Find AST nodes on that line
3. Filter by instruction kind + name

Proposed symbolic alignment strategy:
1. Extract full symbolic ref sequence from bytecode method
2. Extract full symbolic ref sequence from source AST method
3. Align sequences (line numbers used as optional tiebreaker, not primary signal)
4. Each match links an AST node to a specific bytecode offset

The symbolic approach is **more reliable** (works without debug info) and **more precise** (disambiguates multiple calls on the same line).

## Open Questions

1. **Type resolution scope**: Should source-side type resolution be attempted (using classpath) to improve matching precision for overloaded methods? Or is name + arg count + position sufficient?

2. **Incremental alignment**: When source changes but bytecode hasn't been recompiled, the alignment will fail. How should staleness be detected and handled?

3. **Multi-language**: For Kotlin, the compilation model differs (extension functions, coroutines, companion objects). The synthetic pattern catalog needs a Kotlin-specific section. The core alignment algorithm (JLS evaluation order) doesn't directly apply — Kotlin has its own specification.
