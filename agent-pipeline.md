# Agent Pipeline Design Document

## Overview

This document describes the end-to-end pipeline for an LLM agent to work with OpenTaint's pattern rules and approximation configs. The agent can generate new rules, debug existing rules, fix false positives (FP) and false negatives (FN), and generate missing approximations.

## Agent Capabilities Summary

| Capability | Artifact Type | Reference |
|-----------|---------------|-----------|
| Generate vulnerability detection rules | Pattern rules (YAML) | `pattern-rules.md` |
| Debug/fix rules (FP/FN) | Pattern rules (YAML) | `pattern-rules.md` |
| Generate taint propagation rules | YAML config rules | `approximations-config.md` |
| Generate complex propagators | Code-based approximations (Java) | `approximations-config.md` |
| Override existing propagation | Either YAML or Java stubs | `approximations-config.md` |
| Framework support | Not configurable | Provided as-is |

## Pipeline Architecture

```
                    ┌─────────────────────┐
                    │     LLM Agent       │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
     ┌────────────────┐ ┌───────────┐ ┌──────────────────┐
     │ Pattern Rules  │ │ YAML      │ │ Code-Based       │
     │ (YAML)         │ │ Config    │ │ Approximations   │
     │                │ │ (YAML)    │ │ (Java stubs)     │
     └───────┬────────┘ └─────┬─────┘ └────────┬─────────┘
             │                │                 │
             ▼                ▼                 ▼
     ┌─────────────────────────────────────────────────┐
     │              OpenTaint Analyzer                  │
     │                                                  │
     │  Pattern Matcher ◄── Pattern Rules               │
     │  IFDS Engine     ◄── Config + Approximations     │
     │  Spring Support  ◄── Built-in (not configurable) │
     └──────────────────────┬──────────────────────────┘
                            │
                            ▼
     ┌─────────────────────────────────────────────────┐
     │              Analysis Results (SARIF)            │
     │  - Vulnerability findings with traces            │
     │  - External methods list (where taint was lost)  │
     └──────────────────────┬──────────────────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │  Agent Review  │
                    │  (FP/FN loop)  │
                    └───────────────┘
```

## Scenario 1: Generating New Vulnerability Rules

### When

The agent needs to detect a new vulnerability class or cover a new framework/API.

### Pipeline

```
1. Agent receives vulnerability specification
   (CWE, description, examples of vulnerable/safe code)
       │
2. Agent identifies source-sink pattern
   ├── What are the untrusted data entry points? (sources)
   ├── What operations are dangerous with untrusted data? (sinks)
   └── What operations make data safe? (sanitizers)
       │
3. Agent generates artifacts
   ├── Source library rule (if new source type needed)
   ├── Sink library rule (if new sink type needed)
   └── Join-mode security rule (composing sources and sinks)
       │
4. Agent generates test cases
   ├── @PositiveRuleSample — vulnerable code that should trigger
   └── @NegativeRuleSample — safe code that should NOT trigger
       │
5. Run analyzer and validate
   └── Execute rule tests, verify findings match expectations
```

### Agent Workflow

**Step 1: Define the source (if not already covered)**

Check existing source library rules in `rules/ruleset/java/lib/`. Most common sources are already defined:
- `servlet-untrusted-data-source.yaml` — Servlet handlers
- `spring/untrusted-data-source.yaml` — Spring controllers

If a new source is needed:

```yaml
# rules/ruleset/java/lib/generic/new-framework-source.yaml
rules:
  - id: new-framework-untrusted-source
    severity: NOTE
    message: Untrusted data from NewFramework
    options:
      lib: true
    languages: [java]
    patterns:
      - pattern-either:
          - pattern: |
              $RETURNTYPE $METHOD(@NewFramework.Input $TYPE $UNTRUSTED, ...) { ... }
```

**Step 2: Define the sink**

```yaml
# rules/ruleset/java/lib/generic/new-vuln-sinks.yaml
rules:
  - id: new-vuln-sinks
    severity: NOTE
    message: Potential new vulnerability sink
    options:
      lib: true
    languages: [java]
    patterns:
      - pattern-either:
          - pattern: DangerousApi.execute($UNTRUSTED, ...)
          - pattern: new DangerousApi($UNTRUSTED)
```

**Step 3: Compose into a security rule**

```yaml
# rules/ruleset/java/security/new-vuln.yaml
rules:
  - id: new-vulnerability
    severity: ERROR
    message: >-
      New vulnerability detected: untrusted data flows to dangerous API
    metadata:
      cwe: CWE-xxx
      short-description: New vulnerability type
      full-description: |-
        ## Vulnerable code
        ```java
        void handler(Request req) {
            String input = req.getParam("data");
            DangerousApi.execute(input);  // vulnerable!
        }
        ```
        ## Safe code
        ```java
        void handler(Request req) {
            String input = req.getParam("data");
            String safe = Sanitizer.clean(input);
            DangerousApi.execute(safe);  // safe
        }
        ```
    languages: [java]
    mode: join
    join:
      refs:
        - rule: java/lib/generic/servlet-untrusted-data-source.yaml#java-servlet-untrusted-data-source
          as: servlet-source
        - rule: java/lib/spring/untrusted-data-source.yaml#spring-untrusted-data-source
          as: spring-source
        - rule: java/lib/generic/new-vuln-sinks.yaml#new-vuln-sinks
          as: sink
      on:
        - 'servlet-source.$UNTRUSTED -> sink.$UNTRUSTED'
        - 'spring-source.$UNTRUSTED -> sink.$UNTRUSTED'
```

## Scenario 2: Fixing False Positives (FP)

### When

The analyzer reports a vulnerability that is not actually exploitable.

### Common FP Causes and Fixes

| Cause | Fix | Example |
|-------|-----|---------|
| Sanitization not recognized | Add `pattern-sanitizers` or `pattern-not-inside` | Encoding, validation, escaping functions |
| Safe type not excluded | Add `metavariable-regex` with negative lookahead | Primitive types, enum constants |
| Context makes it safe | Add `pattern-not-inside` for the safe context | Already inside a try-with-resources, already validated |
| Wrong method matched | Narrow `metavariable-regex` or add conditions | Too broad `pattern: $OBJ.$METHOD(...)` |

### Pipeline

```
1. Agent receives FP report
   (SARIF finding + source code context + trace)
       │
2. Agent analyzes the trace
   ├── Is there a sanitizer in the path? → Add sanitizer pattern
   ├── Is the source actually untrusted? → Narrow source pattern
   ├── Is the sink actually dangerous in this context? → Narrow sink pattern
   └── Is the dataflow path actually possible? → May need engine fix
       │
3. Agent modifies the appropriate rule
   ├── Add pattern-not / pattern-not-inside to exclude safe patterns
   ├── Add metavariable-regex with negative lookahead
   ├── Add pattern-sanitizers (in taint mode)
   └── Add condition constraints
       │
4. Agent generates negative test case
   └── @NegativeRuleSample with the FP code
       │
5. Re-run analyzer, verify FP is eliminated without losing TP
```

### Example: Adding a Sanitizer

If `Encoder.htmlEncode()` is not recognized as XSS sanitization:

```yaml
# Modify the sink library rule to add a sanitizer
rules:
  - id: spring-xss-sinks
    mode: taint
    pattern-sinks:
      - patterns:
          - pattern: response.getWriter().write($UNTRUSTED)
          - focus-metavariable: $UNTRUSTED
    pattern-sanitizers:                    # ← Agent adds this
      - pattern: Encoder.htmlEncode(...)   # ← New sanitizer
      - pattern: StringEscapeUtils.escapeHtml4(...)
```

### Example: Excluding Safe Types

If Spring controllers with `int` parameters are triggering FP:

```yaml
# In the source library rule, add type exclusion
- metavariable-regex:
    metavariable: $TYPE
    regex: ^(?!(Integer|Long|int|long|float|double|boolean|Boolean|BindingResult))
```

## Scenario 3: Fixing False Negatives (FN)

### When

The analyzer misses a real vulnerability.

### Root Cause Analysis

FN occurs when one of these conditions is not met:

| Condition | Failure Mode | Fix Domain |
|-----------|-------------|------------|
| Source recognized | Untrusted entry point not matched | Pattern rules (add source) |
| Sink recognized | Dangerous operation not matched | Pattern rules (add sink) |
| Taint propagated | Taint lost at a library call | Approximations (YAML or code) |
| Path connected | Source and sink not linked | Pattern rules (add join on clause) |

### Pipeline for Missing Sources/Sinks

```
1. Agent receives FN report
   (vulnerable code sample that should be detected)
       │
2. Agent identifies what's missing
   ├── Is the source pattern covered? → Check source library rules
   ├── Is the sink pattern covered? → Check sink library rules
   └── Are they connected in a join rule? → Check security rule refs/on
       │
3. Agent adds the missing pattern
   ├── New pattern in existing source/sink library rule
   ├── New library rule file
   └── New on clause or ref in join rule
       │
4. Agent generates positive test case
   └── @PositiveRuleSample with the missed vulnerable code
       │
5. Re-run analyzer, verify FN is fixed
```

### Pipeline for Missing Taint Propagation (Key Scenario)

This is the most complex scenario. The engine will provide a **list of external methods where a dataflow fact was killed** — methods through which taint should propagate but doesn't because the engine has no model for them.

```
1. Engine reports external methods where taint was lost
   Example: com.example.lib.Wrapper#getValue — no passthrough rule
       │
2. Agent classifies each method
   ├── Simple getter/setter → YAML passThrough rule
   ├── Collection operation → YAML passThrough rule
   ├── Functional/lambda API → Code-based approximation needed
   ├── Async/threading API → Code-based approximation needed
   └── Irrelevant to taint → Skip (not a propagation issue)
       │
3. Agent generates appropriate artifacts
       │
4. Re-run analyzer, verify taint now propagates through
```

#### Generating YAML passThrough Rules

For simple methods where taint just flows through arguments:

```yaml
# Agent-generated passThrough for a missed library method
passThrough:
  - function: com.example.lib.Wrapper#getValue
    copy:
      - from: this
        to: result

  - function: com.example.lib.Wrapper#setValue
    copy:
      - from: arg(0)
        to: this

  - function: com.example.lib.DataTransformer#transform
    copy:
      - from: arg(0)
        to: result
      - from: this
        to: result
```

#### Generating Code-Based Approximations

For complex APIs involving lambdas or callbacks:

```java
@Approximate(com.example.lib.ReactiveProcessor.class)
public class ReactiveProcessor {
    public Object processAsync(@ArgumentTypeContext Function transform) throws Throwable {
        ReactiveProcessor self = (ReactiveProcessor) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        // Extract data, apply lambda, return result — makes data flow explicit
        Object data = self.getData();
        Object result = transform.apply(data);
        return result;
    }

    public void subscribe(@ArgumentTypeContext Consumer handler) {
        ReactiveProcessor self = (ReactiveProcessor) (Object) this;
        if (OpentaintNdUtil.nextBool()) {
            handler.accept(self.getData());
        }
    }
}
```

### Decision Tree: YAML Config vs Code-Based Approximation

```
Does the method involve lambdas/callbacks/functional interfaces?
├── YES → Code-based approximation required
│         (YAML cannot model lambda invocation)
└── NO
    Does the method involve complex internal state?
    ├── YES → YAML with <rule-storage> pattern
    │         (model internal state with synthetic fields)
    └── NO
        Is it a simple from→to propagation?
        ├── YES → YAML passThrough rule (simplest option)
        └── NO
            Does it require non-deterministic branching?
            ├── YES → Code-based approximation
            │         (use OpentaintNdUtil.nextBool())
            └── NO → YAML passThrough rule with conditions
```

## Scenario 4: Overriding Existing Approximations

### When

An existing YAML config rule or code-based approximation is incorrect or too conservative/aggressive.

### Pipeline

```
1. Agent identifies incorrect approximation
   (e.g., method marked as taint-preserving but actually sanitizes)
       │
2. Agent determines correction
   ├── Wrong taint flow direction → Fix copy from/to
   ├── Missing conditions → Add condition to restrict applicability
   ├── Incorrect approximation logic → Rewrite Java stub
   └── Should be a cleaner instead → Change rule type
       │
3. Agent generates corrected artifact
   ├── YAML: New rule with more specific matcher (takes precedence)
   ├── Java: New approximation (always overrides YAML for same method)
   └── Cleaner rule: To explicitly remove taint
       │
4. Validate correction
```

### Override Priority Chain

```
Code-based approximation    ← Agent CAN provide (highest priority)
    overrides
YAML config rules           ← Agent CAN provide
    overrides
Auto-generated defaults     ← Engine auto-generates for get* methods
    fall back to
Intra-procedural analysis   ← Engine analyzes callee body if available
    fall back to
Call-to-return passthrough  ← Taint preserved, method treated as no-op
```

**Key insight**: An agent-provided code-based approximation will always override YAML config for the same method. An agent-provided YAML rule will merge with (not replace) existing YAML rules unless using `JIRCombinedTaintRulesProvider` with OVERRIDE mode.

## Scenario 5: Handling the External Methods List

### Context

The engine can report a list of external methods encountered during analysis where no explicit taint propagation model exists. For these methods, the engine defaults to **call-to-return passthrough** (taint is preserved unchanged), which may be:
- **Correct** — the method doesn't affect taint (e.g., logging, metrics)
- **Too conservative** — taint should propagate through method arguments/return (FN)
- **Too aggressive** — taint should be cleaned (FP)

### Agent Workflow

```
1. Receive external methods list from engine
   Format: [method_signature, call_count, taint_facts_at_call]
       │
2. Prioritize by impact
   ├── Methods on hot taint paths (high call count + taint facts present)
   ├── Methods in known libraries (can look up API documentation)
   └── Methods in unknown libraries (may need code review)
       │
3. For each prioritized method, classify:
   │
   ├── PROPAGATOR — taint flows from input to output
   │   Agent action: Generate passThrough YAML rule
   │   Example: String.concat, List.get, Map.put
   │
   ├── TRANSFORMER — taint flows but through complex logic (lambdas)
   │   Agent action: Generate code-based approximation
   │   Example: Stream.map, CompletableFuture.thenApply
   │
   ├── SANITIZER — method neutralizes taint
   │   Agent action: Generate cleaner YAML rule
   │   Example: Encoder.htmlEncode, Validator.validate
   │
   ├── SOURCE — method introduces new taint
   │   Agent action: Generate source YAML rule
   │   Example: Request.getParameter, Environment.getProperty
   │
   ├── SINK — method is dangerous with tainted data
   │   Agent action: Generate sink YAML rule (and pattern rule)
   │   Example: Statement.execute, ProcessBuilder.command
   │
   └── NEUTRAL — method doesn't affect taint analysis
       Agent action: No action needed (default passthrough is correct)
       Example: Logger.info, System.currentTimeMillis
       │
4. Generate artifacts and re-run analysis
5. Review updated results for FP/FN changes
```

### Batch Processing Strategy

For large codebases with many external methods:

```
1. Group by package/library
   com.fasterxml.jackson.* → 47 methods
   org.springframework.* → 23 methods
   org.apache.commons.* → 15 methods
       │
2. Process library-by-library
   For each library:
   ├── Check if jar-split/ config already exists
   ├── Look up library documentation / source
   ├── Generate comprehensive passThrough rules for the library
   └── Save as new jar-split YAML file
       │
3. Validate incrementally
   After each library is configured, re-run and check results
```

## Common Patterns and Templates

### Template: PassThrough for Simple Getter

```yaml
passThrough:
  - function: com.example.Type#getField
    copy:
      - from: this
        to: result
```

### Template: PassThrough for Builder Pattern

```yaml
passThrough:
  - function: com.example.Builder#withField
    copy:
      - from: arg(0)
        to: this
      - from: arg(0)
        to: result
      - from: this
        to: result
```

### Template: PassThrough for Collection Wrapper

```yaml
passThrough:
  - function: com.example.Collection#add
    copy:
      - from: arg(0)
        to:
          - this
          - .com.example.Collection#<rule-storage>#java.lang.Object
  - function: com.example.Collection#get
    copy:
      - from:
          - this
          - .com.example.Collection#<rule-storage>#java.lang.Object
        to: result
```

### Template: PassThrough for Generic Pattern (All Getters in a Package)

```yaml
passThrough:
  - function:
      package: com.example.dto
      class:
        pattern: .*
      name:
        pattern: get.*
    copy:
      - from: this
        to: result
```

### Template: Code-Based Approximation for Functional API

```java
@Approximate(com.example.FunctionalApi.class)
public class FunctionalApi {
    public Object transform(@ArgumentTypeContext Function<Object, Object> fn) throws Throwable {
        FunctionalApi self = (FunctionalApi) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object input = self.getValue();
        return fn.apply(input);
    }

    public void consume(@ArgumentTypeContext Consumer<Object> consumer) {
        FunctionalApi self = (FunctionalApi) (Object) this;
        if (OpentaintNdUtil.nextBool()) {
            consumer.accept(self.getValue());
        }
    }

    public static FunctionalApi create(@ArgumentTypeContext Supplier<Object> supplier) {
        if (OpentaintNdUtil.nextBool()) return null;
        Object value = supplier.get();
        return FunctionalApi.of(value);
    }
}
```

### Template: Source Library Rule for New Framework

```yaml
rules:
  - id: new-framework-untrusted-source
    severity: NOTE
    message: Untrusted data from NewFramework handler
    options:
      lib: true
    languages: [java]
    patterns:
      - pattern-either:
          - patterns:
              - pattern: |
                  @$ANNOTATION
                  $RETURNTYPE $METHOD(..., $TYPE $UNTRUSTED, ...) { ... }
              - metavariable-pattern:
                  metavariable: $ANNOTATION
                  pattern-either:
                    - pattern: NewFramework.Handler
                    - pattern: NewFramework.Endpoint
              - metavariable-regex:
                  metavariable: $TYPE
                  regex: ^(?!(int|long|float|double|boolean))
```

### Template: Sink Library Rule (Taint Mode)

```yaml
rules:
  - id: new-vuln-sinks
    severity: NOTE
    message: Dangerous operation with untrusted data
    options:
      lib: true
    languages: [java]
    mode: taint
    pattern-sinks:
      - patterns:
          - pattern-either:
              - pattern: DangerousApi.execute($UNTRUSTED, ...)
              - pattern: (DangerousApi $API).$METHOD($UNTRUSTED, ...)
          - metavariable-regex:
              metavariable: $METHOD
              regex: (exec|run|eval|interpret)
          - focus-metavariable: $UNTRUSTED
```

## Integration Points and Constraints

### What the Agent CAN Do

1. Create/modify pattern rules (YAML) in `rules/ruleset/`
2. Create/modify YAML config rules (passThrough, source, sink, cleaner)
3. Create code-based approximation Java stubs
4. Generate test cases for rules
5. Override YAML config rules with more specific rules
6. Override YAML config rules with code-based approximations

### What the Agent CANNOT Do

1. Modify framework support (Spring, etc.) — provided as-is
2. Change the IFDS analysis algorithm
3. Change the access path abstraction mode
4. Change how the call graph is constructed
5. Modify the pattern matching engine semantics

### Validation Checklist

Before submitting any artifact, the agent should verify:

- [ ] YAML is valid and parseable
- [ ] Rule IDs are globally unique
- [ ] Library rules have `options.lib: true`
- [ ] Security rules have `metadata.cwe` and `metadata.short-description`
- [ ] Source/sink rules use consistent metavariable names (`$UNTRUSTED`)
- [ ] Join-mode `on` clauses reference valid aliases
- [ ] Test cases exist for all enabled non-library rules
- [ ] passThrough `from`/`to` positions are valid (this, arg(N), result, etc.)
- [ ] Code-based approximations compile and use `@Approximate` annotation
- [ ] No regressions: existing test cases still pass
