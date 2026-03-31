# Skill: Create Rule

Create pattern rules for detecting specific vulnerability classes.

## Prerequisites

- `opentaint` CLI available
- Understanding of the target vulnerability (source, sink, sanitizers)

## Procedure

### 1. Check existing coverage

```bash
RULES_DIR=$(opentaint agent rules-path)
ls $RULES_DIR/java/lib/generic/
ls $RULES_DIR/java/lib/spring/
ls $RULES_DIR/java/security/
```

Read existing rules to understand patterns already covered.

### 2. Create rule directory structure

```
agent-rules/
  java/
    lib/
      my-source.yaml
      my-sink.yaml
    security/
      my-vuln.yaml
```

### 3. Create library rules

**Source rule** (`agent-rules/java/lib/my-source.yaml`):

```yaml
rules:
  - id: my-custom-source
    options:
      lib: true
    severity: NOTE
    message: Custom untrusted data source
    languages: [java]
    patterns:
      - pattern-either:
          - patterns:
              - pattern: |
                  $RETURNTYPE $METHOD(HttpServletRequest $UNTRUSTED, ...) { ... }
              - metavariable-pattern:
                  metavariable: $METHOD
                  pattern-either:
                    - pattern: doGet
                    - pattern: doPost
```

**Sink rule** (`agent-rules/java/lib/my-sink.yaml`):

```yaml
rules:
  - id: my-custom-sink
    options:
      lib: true
    severity: NOTE
    message: Custom dangerous operation
    languages: [java]
    mode: taint
    pattern-sinks:
      - patterns:
          - pattern-either:
              - pattern: (java.sql.Statement $S).executeQuery($UNTRUSTED)
              - pattern: (java.sql.Statement $S).execute($UNTRUSTED)
          - focus-metavariable: $UNTRUSTED
```

### 4. Create security rule (join mode)

```yaml
rules:
  - id: my-vulnerability
    severity: ERROR
    message: >-
      Untrusted data flows to dangerous operation
    metadata:
      cwe: CWE-89
      short-description: SQL Injection via untrusted input
    languages: [java]
    mode: join
    join:
      refs:
        - rule: java/lib/my-source.yaml#my-custom-source
          as: source
        - rule: java/lib/my-sink.yaml#my-custom-sink
          as: sink
      on:
        - 'source.$UNTRUSTED -> sink.$UNTRUSTED'
```

### 5. Reference built-in library rules

```yaml
refs:
  - rule: java/lib/generic/servlet-untrusted-data-source.yaml#java-servlet-untrusted-data-source
    as: servlet-source
  - rule: java/lib/spring/untrusted-data-source.yaml#spring-untrusted-data-source
    as: spring-source
```

### 6. Run analysis with specific rules

```bash
opentaint scan ./opentaint-project/project.yaml \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id my-vulnerability
```

## Constraints

- Library rules MUST have `options.lib: true` and `severity: NOTE`
- Security rules MUST have `metadata.cwe` and `metadata.short-description`
- Source/sink metavariable names must match across `refs` and `on` clauses
- The `rule:` path in `refs` is relative to the ruleset root
- Rule IDs must be globally unique
- Library rules referenced via `refs` in join-mode rules are auto-included by `--rule-id`
- For simple structural patterns (no dataflow), omit `mode:` (uses default mode)

## FP/FN Fixes

- **FP**: Add `pattern-not`, `pattern-not-inside`, `pattern-sanitizers`, or `metavariable-regex`
- **FN**: Add patterns to `pattern-either`, create new library rules, add new `on` clauses
