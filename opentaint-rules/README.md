# Seqra Security Rules

[![GitHub release](https://img.shields.io/github/release/seqra/seqra-rules.svg)](https://github.com/seqra/seqra-rules/releases)

A curated collection of security rules for [Seqra](https://github.com/seqra/seqra), a static analysis engine for Java and Kotlin that combines Semgrep-style pattern matching with dataflow/taint analysis.

The repository provides:

- A logically structured set of executable security rules for real-world Java/Kotlin applications
- A shared library of reusable rule components (sources, sinks, propagators, etc.)
- A test suite that validates rule behavior and enforces coverage for all enabled rules

---

## Repository Structure

```text
.
├─ rules/java/
│  ├─ security/       # Executable rules run against user code (one file per vulnerability class)
│  └─ lib/            # Reusable rule fragments, not executed directly (marked as lib: true)
└─ test/
   └─ src/main/java/
      └─ security/  # Rule tests with @PositiveRuleSample / @NegativeRuleSample
```

### `rules/`: Executable Security Rules

All rules that are intended to run on user code live under `rules/`. Each file groups a *class of vulnerability*.

Example:

```text
rules/java/security/
  command-injection.yaml
  sqli.yaml
  xss.yaml
  xxe.yaml
```

Characteristics:

- Rules are written in **Semgrep-compatible YAML**.
- Each rule entry has an `id`, `severity`, `message`, `metadata`, `languages`, and pattern/mode fields (`mode: taint`, `pattern`, `patterns`, `pattern-either`, `pattern-sources`, `pattern-sinks`, etc.).
- Rules in `rules/` are considered **executable** unless:
  - `options.disabled: <reason>` — the rule is disabled
  - `options.lib: true` — the rule is a library component (should normally reside in `lib/`)

### `lib/`: Reusable Rule Components

The `lib/` directory contains rule fragments that are **not executed standalone**. They are building blocks (sources, sinks, propagators, etc.) that other rules compose via `mode: join` or standard taint rules.

Structure is by technology, example:

```text
lib/
  java/
    generic/
      command-injection-sinks.yaml
      servlet-sqli-sinks.yaml
      servlet-untrusted-data-source.yaml
      servlet-xss-sinks.yaml
      xxe-sinks.yaml
    spring/
      jdbc-sqli-sinks.yaml
      spring-xss-sinks.yaml
      untrusted-data-source.yaml
```

All library rules are marked:

```yaml
rules:
  - id: java-servlet-untrusted-data-source
    options:
      lib: true
    ...
```

Key points:

- **`lib: true`** explicitly marks a rule as non-executable; it will not be run by Seqra as a top-level rule.
- Library rules are typically:
  - Source definitions (`*untrusted-data-source*`)
  - Sink definitions (`*sinks*`)
  - Propagation or helper patterns shared across multiple vulnerabilities

---

## Join Mode

Many rules under `rules/` combine multiple library rules using **`mode: join`**.

Example (from `rules/java/security/ssrf.yaml`):

```yaml
- id: ssrf-in-servlet-app
  languages:
    - java
  mode: join
  join:
    refs:
      - rule: java/lib/generic/servlet-untrusted-data-source.yaml#java-servlet-untrusted-data-source
        as: untrusted-data
      - rule: java/lib/generic/ssrf-sinks.yaml#java-ssrf-sink
        as: sink
    on:
      - 'untrusted-data.$UNTRUSTED -> sink.$UNTRUSTED'
```

Semantics:

- `mode: join` derives a composite rule from other rules referenced in `join.refs`.
- `refs` defines:
  - `rule`: path to the library rule file plus `#<rule-id>` inside that YAML
  - `as`: local alias for referencing captures/variables from that rule
- `on` describes how to correlate matches from referenced rules:
  - `untrusted-data.$UNTRUSTED -> sink.$UNTRUSTED` expresses a **dataflow relationship** between the `$UNTRUSTED` captured in the source rule and the same `$UNTRUSTED` captured in the sink rule.

This join mode is **based on Semgrep's join mode**, but Seqra extends it with custom features (such as the `->` notation in the `on` section) to express taint-style flows across multiple rule components.

---

## Rule Semantics

Rules follow Semgrep syntax and concepts:

- **Pattern-based** rules:
  - `pattern`, `patterns`, `pattern-either`, `pattern-inside`, `pattern-not-inside`, `metavariable-regex`, etc.
- **Taint-style rules**:
  - `mode: taint`
  - `pattern-sources`, `pattern-propagators`, `pattern-sanitizers`, `pattern-sinks`
  - Dataflow through methods, fields, and variables
- **Metadata**:
  - `cwe`, `short-description`, `full-description` (where provided)
  - External references (OWASP, CWE, upstream rule sources)
  - Optional `license` and `provenance`

---

## Testing and Rule Coverage

Rule behavior is validated via Java test snippets under:

```text
test/src/main/java/security/
```

Each test class declares **inline code samples** annotated with:

- `@PositiveRuleSample(...)` — code that **must** trigger a specific rule
- `@NegativeRuleSample(...)` — code that **must not** trigger that rule (not shown above but typically paired with positives)

Annotation usage (conceptually):

```java
@PositiveRuleSample(
    value = "java/security/xss.yaml",
    id = "xss-in-servlet-app"
)
class SomeServletXssSample {
    // vulnerable code here
}
```

### Rule Coverage Enforcement

The CI helper `RuleCoverageCheck` (in `test/src/main/java/rules/RuleCoverageCheck.java`) enforces:

1. **YAML validity** for every file in `rules/`:
   - Root is a map and contains a `rules` list.
   - Each rule has a non-blank `id`.
2. **Test coverage for all active rules**:
   - Active rules are those in `rules/` where:
     - `options.disabled` is not `true`, and
     - `options.lib` is not `true`
   - Each such rule must have at least one `@PositiveRuleSample` referencing:
     - `value = "<relative-path-to-rule-yaml>"` (e.g. `java/security/xss.yaml`)
     - `id = "<rule-id>"` (the rule's `id` value)

If any active rule is not covered by a positive sample, or if any YAML is invalid, the checker:

- Prints detailed errors (uncounted rules, invalid YAML, etc.)
- Exits with a non-zero status (breaking the build/CI)

---

## Gradle Integration

This repository exposes a Gradle verification task:

- **`verification/checkRulesCoverage`**

Behavior:

- Runs the `RuleCoverageCheck` helper
- Ensures:
  - All rule YAMLs in `rules/` are syntactically valid
  - Every enabled, non-lib rule has at least one positive test sample

Usage (from the `test/root` subdirectory):

```bash
cd test/root
../gradlew verification/checkRulesCoverage
```

On success:

- `"Rule coverage check passed: all rules valid and covered."` is printed.

On failure:

- It prints all problems (invalid YAML, uncovered rules) and fails the task.

---

## Adding or Modifying Rules

When introducing or changing rules, follow these guidelines:

1. **Choose the correct location**
   - Executable vulnerability rules → `java/security/<vuln-class>.yaml`
   - Shared sources, sinks, or helpers → `java/lib/generic/` or `java/lib/spring/`

2. **Mark library-only rules**
   - Add `options.lib: true` for library fragments in `lib/` (or exceptionally in `rules/` if they are not meant to be executed directly).

3. **Avoid duplicates**
   - Reuse existing library rules from `lib/` and compose them via `mode: join` where applicable.

4. **Update tests**
   - Add at least one `@PositiveRuleSample` (and typically `@NegativeRuleSample`) under `test/src/main/java/security/`.
   - Reference the rule by:
     - `value = "<relative YAML path under project root>"`
     - `id = "<rule id>"`

5. **Run coverage checks**
   - From the `test/root` subdirectory execute `../gradlew verification/checkRulesCoverage` to ensure:
     - No YAML errors
     - All executable rules are covered by tests

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

Rule content may incorporate or adapt patterns originally published under various open-source licenses (for example, from community rule sets). Where applicable, original provenance and license information is recorded in rule `metadata`.
