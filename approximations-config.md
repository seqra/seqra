# Approximations Configuration Design Document

## Overview

Approximations tell the dataflow engine **how taint propagates** through library and framework methods that the engine cannot analyze directly (because their source code is not part of the project). There are two layers:

| Layer | Format | Location | Scope | Override Priority |
|-------|--------|----------|-------|-------------------|
| **YAML config rules** | Declarative passThrough YAML | `core/opentaint-config/config/` | Tens of thousands of library methods | Base layer (lowest priority) |
| **Code-based approximations** | Java stub classes | `core/opentaint-jvm-sast-dataflow/dataflow-approximations/` | Complex functional/async APIs | Overrides YAML config (highest priority) |

When both exist for the same method, **code-based approximations always win** — the engine analyzes the stub body directly instead of applying config rules.

## Layer 1: YAML Config Rules (passThrough)

### File Layout

```
core/opentaint-config/config/config/
├── stdlib.yaml          # ~21,000 lines — java.io, java.lang, java.util, java.net, java.nio
├── config.yaml          # ~18,000 lines — javax.*, org.json
├── jmod.yaml            # ~3,400 lines — javax.naming, javax.script, javax.sql
├── unverified.yaml      # ~1,100 lines — Jackson, Spring utils, Reactor, XML parsers
└── jar-split/           # Per-library configs (29 files)
    ├── spring-web-7.0.2.yaml
    ├── spring-webmvc-7.0.2.yaml
    ├── reactor-core-3.8.2.yaml
    ├── guava-33.5.0-jre.yaml
    ├── jackson-databind-2.20.1.yaml
    ├── netty-buffer-4.2.0.Final.yaml
    └── ...
```

### YAML Schema

All config files share the same top-level `passThrough:` schema:

```yaml
passThrough:
  - function: <function-name-matcher>
    signature: <optional-signature-matcher>
    overrides: <boolean, default true>
    condition: <optional-condition>
    copy:
      - from: <position>
        to: <position>
```

### Function Name Matching

#### Simple Form (String)

```yaml
function: java.lang.String#concat
```

Parsed as: package = `java.lang`, class = `String`, method = `concat`.

#### Complex Form (Map with Patterns)

```yaml
function:
  package: org.apache.axis.types
  class:
    pattern: .*
  name:
    pattern: get.*
```

Each of `package`, `class`, `name` can be either a plain string (exact match) or `pattern: <regex>` (substring regex match).

#### Pattern Matching Semantics

- **Exact name** (`"get"`) — matches only methods named exactly `get`
- **Pattern** (`pattern: "get.*"`) — matches any method whose name contains a substring matching `get.*` (uses `containsMatchIn`, not `fullMatch`)
- **Wildcard** (`pattern: ".*"`) — matches any name

### Signature Matching

```yaml
signature: (java.lang.String) java.lang.String
```

Format: `(<param-types>) <return-type>`. Used to disambiguate overloaded methods.

Alternative structured form:
```yaml
signature:
  params:
    - index: 0
      type: java.lang.String
```

### Taint Flow Positions

Positions describe where taint lives on a method's interface:

| Position                                      | Meaning                                            |
|-----------------------------------------------|----------------------------------------------------|
| `this`                                        | The receiver object (`this` reference)             |
| `arg(0)`, `arg(1)`, ...                       | Method arguments (0-indexed)                       |
| `arg(*)`                                      | All arguments (expanded to individual arg rules)   |
| `result`                                      | The method's return value                          |
| `[*]`                                         | Array element access (appended to a base position) |
| `.<fqn class name>#<field name>$<firld type>` | Field access (appended to a base position)         |

#### Internal State Tracking (Rule Storage)

For modeling taint that persists inside an object across method calls:

```yaml
from:
  - this
  - .java.io.ByteArrayOutputStream#<rule-storage>#java.lang.Object
to: result
```

The `<rule-storage>` is a synthetic field — it doesn't exist in the real class. The engine uses it as a virtual container to track taint flow through an object's internal state. When a method stores taint into the object, the `to` side points to the rule-storage field. When another method retrieves it, the `from` side reads from the same field.

#### Named Field Access

```yaml
from:
  - this
  - .java.lang.Throwable#message#java.lang.Object
to: result
```

This models taint flowing from a specific named field (`message`) of the `this` object to the result.

### Actions

#### `copy` — Propagate Taint

```yaml
copy:
  - from: arg(0)
    to: result
  - from: this
    to: result
```

Copies all taint marks from `from` position to `to` position. The most common action.

### Conditions

Conditions restrict when a rule applies:

```yaml
condition:
  typeIs:
    position: arg(0)
    type: java.lang.String

condition:
  anyOf:
    - typeIs:
        position: arg(0)
        type: java.lang.String
    - typeIs:
        position: arg(0)
        type: java.lang.CharSequence

condition:
  not:
    isConstant:
      position: arg(0)

condition:
  allOf:
    - annotatedWith:
        position: arg(0)
        type: javax.annotation.Nonnull
    - numberOfArgs: 2
```

#### Condition Types

| Condition | YAML Key | Description |
|-----------|----------|-------------|
| Type check | `typeIs` | Position's type matches a name/pattern |
| Annotation | `annotatedWith` | Position has an annotation |
| Constant | `isConstant` | Position is a compile-time constant |
| Null | `isNull` | Position is null |
| Constant regex | `constantMatches` | Constant value matches regex |
| Constant comparison | `constantEq`, `constantGt`, `constantLt` | Compare constant value |
| Taint check | `tainted` | Position already carries a taint mark |
| Arg count | `numberOfArgs` | Method has N parameters |
| Method annotation | `methodAnnotated` | Method has annotation |
| Class annotation | `classAnnotated` | Enclosing class has annotation |
| Method name | `methodNameMatches` | Method name matches pattern |
| Class name | `classNameMatches` | Class name matches pattern |
| Static field | `isStaticField` | Position is a specific static field |
| Combinators | `anyOf`, `allOf`, `not` | Boolean logic |

### The `overrides` Field and Hierarchy

The `overrides` field (default: `true`) controls **class hierarchy inheritance**:

- `overrides: true` — Rule applies to the specified class **and all subclasses**. When looking up rules for a method, the engine walks the class hierarchy upward and includes matching rules from superclasses.
- `overrides: false` — Rule applies **only** to the exact specified class.

#### Hierarchical Matching (Method Name Level)

The `MethodTaintRulesStorage` indexes rules in three tiers:

1. **Concrete name rules** — exact method name match (e.g., `getEntry`)
2. **Pattern method rules** — regex match on method name (e.g., `get.*`)
3. **Any method rules** — wildcard `.*` match

When resolving rules for a specific method:
1. Check concrete name match first
2. Evaluate all pattern matches
3. Include any-method wildcard matches
4. All matching rules are **merged** (not prioritized) — they all apply

**Important**: There is no priority between concrete and pattern rules at this level. If both a `get*` pattern rule and a `getEntry` concrete rule match `getEntry`, **both apply**. To make a specific rule override a general pattern, use conditions to restrict the general rule, or ensure the specific rule's actions make the general rule's actions redundant.

#### Hierarchical Matching (Class Name Level)

The `MethodClassTaintRulesStorage` resolves rules by:

1. **Exact class match** — highest specificity
2. **Pattern class match** — four strategies:
   - Concrete class name, any package
   - Concrete class name, package pattern
   - Concrete package, class pattern
   - Both class and package patterns
3. **Any-class wildcard** — lowest specificity
4. **Hierarchy walk** — for superclasses, only `overrides: true` rules propagate
5. **Subclass push** — rules are pushed to supertypes with added `typeIs: This` conditions

### Complete Example

```yaml
passThrough:
  # String.concat: taint on this or arg flows to result
  - function: java.lang.String#concat
    signature: (java.lang.String) java.lang.String
    copy:
      - from: arg(0)
        to: result
      - from: this
        to: result

  # StringBuilder.append: taint on arg flows to this and result
  - function: java.lang.StringBuilder#append
    copy:
      - from: arg(0)
        to: this
      - from: arg(0)
        to: result
      - from: this
        to: result

  # ByteArrayOutputStream: write stores taint, toString retrieves it
  - function: java.io.ByteArrayOutputStream#write
    copy:
      - from: arg(0)
        to:
          - this
          - .java.io.ByteArrayOutputStream#<rule-storage>#java.lang.Object
  - function: java.io.ByteArrayOutputStream#toString
    copy:
      - from:
          - this
          - .java.io.ByteArrayOutputStream#<rule-storage>#java.lang.Object
        to: result

  # Generic getter pattern for Axis types: any get* method propagates this to result
  - function:
      package: org.apache.axis.types
      class:
        pattern: .*
      name:
        pattern: get.*
    copy:
      - from: this
        to: result
```

## Layer 2: Code-Based Approximations

### Purpose

Code-based approximations replace complex library method bodies with simplified Java implementations that the IFDS taint analyzer can reason about. They are essential for:

- **Functional APIs** (Stream, Optional) — make lambda data flow explicit
- **Async APIs** (CompletableFuture, CompletionStage) — linearize async composition
- **Threading** (Thread, Executor) — make cross-thread data flow visible
- **Coroutines** (Kotlin builders) — linearize coroutine control flow

### File Layout

```
core/opentaint-jvm-sast-dataflow/dataflow-approximations/
└── src/main/java/org/opentaint/jvm/dataflow/approximations/
    ├── OpentaintNdUtil.java          # Non-deterministic boolean utility
    ├── ArgumentTypeContext.java      # Annotation for type context parameters
    └── stdlib/
        ├── Stream.java               # java.util.stream.Stream
        ├── Optional.java             # java.util.Optional
        ├── CompletableFuture.java    # java.util.concurrent.CompletableFuture
        ├── CompletionStage.java      # java.util.concurrent.CompletionStage
        ├── Executor.java             # java.util.concurrent.Executor
        ├── ExecutorService.java      # java.util.concurrent.ExecutorService
        └── Thread.java               # java.lang.Thread
    └── kotlin/
        ├── Builders.java             # kotlinx.coroutines builders
        ├── BuildersBuilders.java
        └── BuildersBuildersCommon.java
```

### How Approximations Work

#### Annotation-Based Registration

```java
@Approximate(java.util.stream.Stream.class)
public class Stream {
    // Methods here replace the real Stream methods during analysis
}
```

The `@Approximate` annotation binds this stub class to the real `java.util.stream.Stream`. The analyzer loads approximation bytecode from a JAR resource and installs them as `JIRClasspathFeature`. When the analyzer encounters a call to a method that has an approximation, it analyzes the stub body instead of:
- Looking up YAML config rules
- Treating the method as opaque external

#### Key Infrastructure

**`OpentaintNdUtil.nextBool()`** — Non-deterministic choice. The analyzer considers **both** branches, enabling modeling of success + failure paths:

```java
if (OpentaintNdUtil.nextBool()) {
    // success path
} else {
    // failure path (or return null/empty)
}
```

**`@ArgumentTypeContext`** — Marks parameters that carry generic type context (e.g., lambda types). The analyzer uses this to resolve lambda parameter/return types for dataflow through higher-order functions.

#### Common Patterns

**Functional transformation** (explicit lambda data flow):

```java
// Approximation for Stream.map(Function)
public java.util.stream.Stream map(@ArgumentTypeContext Function mapper) {
    java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
    Iterator it = t.iterator();
    if (it.hasNext()) {
        Object result = mapper.apply(it.next());
        return java.util.stream.Stream.of(result);
    }
    return java.util.stream.Stream.empty();
}
```

This makes explicit: element extracted from stream → passed to lambda → result wrapped in new stream.

**Async linearization** (flatten future composition):

```java
// Approximation for CompletableFuture.thenApply(Function)
public CompletableFuture thenApply(@ArgumentTypeContext Function fn) throws Throwable {
    CompletableFuture t = (CompletableFuture) (Object) this;
    if (OpentaintNdUtil.nextBool()) return null;
    Object result = fn.apply(t.get());
    return CompletableFuture.completedFuture(result);
}
```

This linearizes: future value extracted via `.get()` → passed to function → wrapped in completed future.

**Threading** (potential direct invocation):

```java
// Approximation for Thread.start()
public void start() {
    Thread t = (Thread) (Object) this;
    if (OpentaintNdUtil.nextBool()) {
        t.run();
    }
}
```

Models `Thread.start()` as a potential direct call to `run()` so the analyzer can trace data through threads.

### Covered API Surface

| API | Methods Approximated |
|-----|---------------------|
| `Stream` | filter, map, flatMap, mapToInt/Long/Double, peek, sorted, forEach, reduce, collect, min, max, anyMatch/allMatch/noneMatch, takeWhile/dropWhile, toArray, mapMulti |
| `Optional` | ifPresent, ifPresentOrElse, filter, map, flatMap, or, orElseGet, orElseThrow |
| `CompletableFuture` | supplyAsync, thenApply/Accept/Run, thenCombine/AcceptBoth, thenCompose, handle, whenComplete, exceptionally, all `*Async` variants |
| `CompletionStage` | All corresponding CompletionStage methods |
| `Executor` | execute |
| `ExecutorService` | submit, invokeAll, invokeAny |
| `Thread` | start, constructors with Runnable |
| Kotlin Coroutines | runBlocking, launch, async, withContext |

## Override Hierarchy

The engine resolves taint propagation rules in this priority order:

```
1. Code-based approximations  (HIGHEST — analyzed as actual code)
      ↓ if no approximation exists
2. YAML passThrough config    (applied at call sites as summary edges)
      ↓ if no config rule exists
3. Auto-generated defaults    (JIRMethodGetDefaultProvider: get* methods → copy this to result)
      ↓ if none of the above
4. Intra-procedural analysis  (analyze the actual callee body if available)
      ↓ if callee is external/unknown
5. Call-to-return passthrough  (taint preserved, method treated as no-op)
```

### Provider Chain (Runtime)

```
JIRTaintRulesProvider           ← loads from TaintConfiguration (YAML)
  └── StringConcatRuleProvider  ← adds synthetic rules for string concat
    └── JIRMethodGetDefaultProvider  ← auto-generates get* passthrough
      └── JIRCombinedTaintRulesProvider  ← merges base + custom config
        └── JIRFilteredTaintRulesProvider  ← applies TaintRuleFilter
```

### JIRCombinedTaintRulesProvider

When a custom config is provided alongside the default config, `JIRCombinedTaintRulesProvider` merges them with configurable per-category modes:

| Category | Default Mode | Behavior |
|----------|-------------|----------|
| Entry points | OVERRIDE | Custom replaces base |
| Sources | OVERRIDE | Custom replaces base |
| Sinks | OVERRIDE | Custom replaces base |
| PassThrough | EXTEND | Custom + base merged |
| Cleaners | EXTEND | Custom + base merged |

Modes: `EXTEND` (union), `OVERRIDE` (only custom), `IGNORE` (only base).

## Agent Interaction Points

### Generating YAML Config Rules

An agent can create new passThrough rules to fix false negatives where taint is lost through library method calls:

```yaml
# Agent-generated rule for a missed library method
passThrough:
  - function: com.example.lib.DataProcessor#transform
    copy:
      - from: arg(0)
        to: result
```

### Generating Code-Based Approximations

For complex APIs with lambdas/callbacks, the agent can write Java stub classes:

```java
@Approximate(com.example.lib.AsyncProcessor.class)
public class AsyncProcessor {
    public CompletableFuture<Object> processAsync(@ArgumentTypeContext Function<Object, Object> fn) {
        AsyncProcessor self = (AsyncProcessor) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(self.getData());
        return CompletableFuture.completedFuture(result);
    }
}
```

### Overriding Existing Rules

An agent can override existing rules by:

1. **For YAML config**: Add rules with more specific function/class matchers. Since all matching rules are merged, add a `cleaner` rule to cancel out an incorrect passthrough, or provide a corrected passthrough with more specific conditions.
2. **For code-based approximations**: Create a new approximation class for the same target class. Code-based approximations always override YAML config for the same methods.

### Important Constraints

1. YAML config rules follow a **merge** (not replace) model — all matching rules contribute
2. Code-based approximations require compilation to bytecode and inclusion in the approximations JAR
3. The `<rule-storage>` pattern must be used consistently for object state tracking
4. Conditions are resolved at rule load time for structural checks (annotations, class names) and at runtime for value checks (constants, types, taint marks)
5. Framework support (Spring) is provided as-is and is **not** configurable through config rules
