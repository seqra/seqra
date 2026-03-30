# Pattern Rules Design Document

## Overview

Pattern rules are Semgrep-compatible YAML files that define **what** the analyzer should look for in the target codebase. They describe vulnerable dataflow patterns by composing sources, sinks, sanitizers, and structural code patterns. All rules live under `rules/ruleset/`.

## Directory Layout

```
rules/ruleset/
├── java/
│   ├── security/          # Executable rules (one per vulnerability class)
│   │   ├── sqli.yaml
│   │   ├── xss.yaml
│   │   ├── command-injection.yaml
│   │   ├── path-traversal.yaml
│   │   └── ... (22 files)
│   └── lib/               # Reusable library rules (non-executable)
│       ├── generic/        # Framework-agnostic sources/sinks
│       │   ├── servlet-untrusted-data-source.yaml
│       │   ├── command-injection-sinks.yaml
│       │   ├── path-traversal-sinks.yaml
│       │   └── ... (17 files)
│       └── spring/         # Spring-specific sources/sinks
│           ├── untrusted-data-source.yaml
│           ├── jdbc-sqli-sinks.yaml
│           └── ... (6 files)
└── test/                   # Test samples and coverage enforcement
```

## Rule File Structure

Every rule file is a YAML document with a top-level `rules:` list. Each entry is a single rule.

### Common Fields (All Modes)

```yaml
rules:
  - id: <unique-rule-id>          # Required. Globally unique identifier.
    severity: ERROR | WARNING | NOTE   # Required. ERROR = critical, WARNING = medium, NOTE = library/informational.
    message: >-                   # Required. Human-readable finding message.
      Description of the vulnerability
    metadata:                     # Required for security rules. Structured metadata.
      cwe: CWE-xxx               # CWE identifier(s)
      short-description: ...     # One-line summary
      full-description: |-       # Multiline markdown with code examples (vulnerable + safe)
        ...
      references:                # External links (OWASP, CWE, etc.)
        - https://...
      provenance: ...            # Upstream rule source
      license: ...               # License info
    languages:
      - java                     # Target language
    options:                     # Optional flags
      lib: true                  # Marks as non-executable library rule
      disabled: "reason"         # Disables rule with explanation
```

## Three Pattern Modes

### Mode 1: Simple Pattern Matching (Default)

No `mode:` key needed. Uses structural code patterns to find matches.

#### Pattern Operators

| Operator | Semantics |
|----------|-----------|
| `pattern` | Match a single code pattern |
| `patterns` | Conjunction (AND) — all sub-patterns must match |
| `pattern-either` | Disjunction (OR) — any sub-pattern matches |
| `pattern-inside` | Match must occur inside another pattern |
| `pattern-not` | Negation — exclude matches fitting this pattern |
| `pattern-not-inside` | Exclude matches inside another pattern |
| `metavariable-regex` | Constrain a captured metavariable by regex |
| `metavariable-pattern` | Constrain a captured metavariable by sub-pattern |
| `focus-metavariable` | Narrow the match region to a specific metavariable |

#### Metavariables

Metavariables (prefixed with `$`) capture parts of the matched code:

- `$VAR` — single expression or identifier
- `$...ARGS` — zero or more expressions (variadic)
- `$_` — anonymous wildcard (don't-care)

#### Example: Structural Pattern

```yaml
rules:
  - id: wicket-xss
    severity: WARNING
    message: XSS via Wicket setEscapeModelStrings
    languages: [java]
    patterns:
      - pattern: |
          (org.apache.wicket.$A $OBJ).setEscapeModelStrings(false);
```

#### Example: Pattern with Metavariable Constraints

```yaml
patterns:
  - pattern-either:
      - pattern: |
          $RETURNTYPE $METHOD(HttpServletRequest $UNTRUSTED, ...) { ... }
  - metavariable-pattern:
      metavariable: $METHOD
      pattern-either:
        - pattern: doGet
        - pattern: doPost
        - pattern: doPut
```

### Mode 2: Taint Mode

Explicitly declares `mode: taint`. Used to define source/sink/sanitizer triples within a single rule file.

```yaml
rules:
  - id: rule-id
    mode: taint
    pattern-sources:             # Where tainted data originates
      - patterns:
          - pattern: ...
    pattern-sinks:               # Where tainted data is dangerous
      - patterns:
          - pattern-either:
              - pattern: $DB.execute($UNTRUSTED, ...)
          - focus-metavariable: $UNTRUSTED    # Narrow to the tainted arg
    pattern-sanitizers:          # What makes data safe
      - pattern: Encoder.encode(...)
    pattern-propagators:         # Custom propagation through methods
      - pattern: ...
        from: $FROM
        to: $TO
```

**Key feature**: `focus-metavariable` in sinks narrows the match to the specific tainted expression, not the entire call.

**Used primarily for library sink rules** that define only `pattern-sinks` (no sources), relying on join-mode composition to supply sources.

### Mode 3: Join Mode (Primary Composition Mechanism)

Explicitly declares `mode: join`. Composes library rules to form complete vulnerability detectors.

```yaml
rules:
  - id: sql-injection
    mode: join
    join:
      refs:
        - rule: java/lib/generic/servlet-untrusted-data-source.yaml#java-servlet-untrusted-data-source
          as: servlet-source
        - rule: java/lib/spring/untrusted-data-source.yaml#spring-untrusted-data-source
          as: spring-source
        - rule: java/lib/spring/jdbc-sqli-sinks.yaml#spring-sqli-sink
          as: sink
      on:
        - 'servlet-source.$UNTRUSTED -> sink.$UNTRUSTED'
        - 'spring-source.$UNTRUSTED -> sink.$UNTRUSTED'
```

#### Reference Syntax

```
rule: <path-relative-to-ruleset-root>#<rule-id>
as: <local-alias>
```

The path is relative to `rules/ruleset/`. The `#rule-id` fragment selects which rule from a multi-rule file.

#### `on` Clause Syntax

```
'<source-alias>.$METAVAR -> <sink-alias>.$METAVAR'
```

- `->` denotes a **dataflow relationship**: the value captured by `$METAVAR` in the source must flow (through taint propagation) to the same `$METAVAR` in the sink.
- Multiple `on` clauses act as **alternatives (OR)** — any match triggers a finding.
- The metavariable `$UNTRUSTED` is the conventional name for the tainted data binding point across source and sink rules.

## Library Rules

Library rules are reusable building blocks marked with `options.lib: true`. They are **never executed standalone** — they exist only to be referenced by join-mode rules.

### Source Library Rules

Define where untrusted data enters the application:

```yaml
rules:
  - id: java-servlet-untrusted-data-source
    options:
      lib: true
    severity: NOTE
    patterns:
      - pattern-either:
          - patterns:
              - pattern: |
                  $RETURNTYPE $ENTRYPOINT(HttpServletRequest $UNTRUSTED, ...) { ... }
              - metavariable-pattern:
                  metavariable: $ENTRYPOINT
                  pattern-either:
                    - pattern: doGet
                    - pattern: doPost
```

Captures `$UNTRUSTED` at the point where user-controlled data enters.

### Sink Library Rules

Define where tainted data becomes dangerous. Can use either pattern mode or taint mode:

**Pattern-based sink** (simple structure):
```yaml
rules:
  - id: command-injection-sinks
    options:
      lib: true
    patterns:
      - pattern-either:
          - pattern: new ProcessBuilder($UNTRUSTED, ...)
          - pattern: Runtime.$EXEC($UNTRUSTED, ...)
      - metavariable-regex:
          metavariable: $EXEC
          regex: (exec|loadLibrary|load)
```

**Taint-mode sink** (for complex matching with focus):
```yaml
rules:
  - id: spring-sqli-sink
    mode: taint
    options:
      lib: true
    pattern-sinks:
      - patterns:
          - pattern-either:
              - pattern: (Statement $S).execute($UNTRUSTED)
              - pattern: (JdbcTemplate $T).$METHOD($UNTRUSTED, ...)
          - metavariable-regex:
              metavariable: $METHOD
              regex: (query|update|execute|batchUpdate)
          - focus-metavariable: $UNTRUSTED
```

## How the Engine Processes Pattern Rules

1. **Rule loading**: YAML files are parsed and rules are categorized by mode
2. **Join resolution**: Join-mode rules resolve their `refs` to load referenced library rules
3. **Pattern compilation**: Code patterns are compiled into Semgrep-compatible matchers
4. **Dataflow binding**: In join mode, `$UNTRUSTED` from sources and sinks are linked via the `->` operator. The engine performs taint analysis to determine if data flows from source to sink.
5. **Result generation**: Matches produce SARIF findings with vulnerability traces

## Agent Interaction Points

### Generating New Rules

An agent can generate new security rules by:

1. **Creating source library rules** — define new entry points for untrusted data
2. **Creating sink library rules** — define new dangerous operations
3. **Creating join-mode rules** — compose sources and sinks into vulnerability detectors

### Fixing False Positives (FP)

An agent can fix FP by:

1. **Adding `pattern-not` / `pattern-not-inside`** to exclude safe patterns
2. **Adding `pattern-sanitizers`** (in taint mode) to recognize sanitization
3. **Adding `metavariable-regex`** with negative lookaheads to exclude safe types/methods
4. **Setting `options.disabled`** with a reason to disable overly broad rules

### Fixing False Negatives (FN)

An agent can fix FN by:

1. **Adding new patterns to `pattern-either`** in source/sink library rules
2. **Creating new library rules** for uncovered frameworks/APIs
3. **Adding new `on` clauses** in join-mode rules to link new source/sink combinations
4. **Widening `metavariable-regex`** to accept more matching patterns

### Constraints for Agent-Generated Rules

1. All rules **must** follow the YAML schema above
2. Library rules **must** have `options.lib: true` and `severity: NOTE`
3. Security rules **must** have `metadata.cwe` and `metadata.short-description`
4. Source rules **must** capture `$UNTRUSTED` (or equivalent metavariable)
5. Sink rules **must** use the same metavariable name for the tainted position
6. Join-mode `on` clauses **must** reference aliases defined in `refs`
7. Rule ids **must** be globally unique
8. Each enabled non-lib rule **must** have corresponding test coverage (`@PositiveRuleSample` / `@NegativeRuleSample`)

## Testing Rules

Rules are tested via annotated Java code samples in `rules/test/`:

```java
@PositiveRuleSample(ruleId = "sql-injection")
public void vulnerable(HttpServletRequest req) {
    String input = req.getParameter("id");
    db.execute("SELECT * FROM users WHERE id = " + input);
}

@NegativeRuleSample(ruleId = "sql-injection")
public void safe(HttpServletRequest req) {
    String input = req.getParameter("id");
    db.execute("SELECT * FROM users WHERE id = ?", input);
}
```

The `checkRulesCoverage` Gradle task enforces that all enabled, non-library rules have test samples.
