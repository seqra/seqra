# OpenTaint Security Rules

[![GitHub release](https://img.shields.io/github/v/tag/seqra/opentaint?filter=rules/v*&label=rules)](https://github.com/seqra/opentaint/releases)

A curated collection of security rules for [OpenTaint](https://github.com/seqra/opentaint), a static analysis engine for Java and Kotlin that combines Semgrep-style pattern matching with dataflow/taint analysis.

The repository provides:

- A logically structured set of executable security rules for real-world Java/Kotlin applications
- A shared library of reusable rule components (sources, sinks, propagators, etc.)
- A test suite that validates rule behavior and enforces coverage for all enabled rules

---

## Repository Structure

```text
.
├─ ruleset/java/
│  ├─ security/       # Executable rules run against user code (one file per vulnerability class)
│  └─ lib/            # Reusable rule fragments, not executed directly (marked as lib: true)
└─ test/
   └─ src/main/java/
      └─ security/  # Rule tests with @PositiveRuleSample / @NegativeRuleSample
```

### `ruleset/`: Executable Security Rules

All rules that are intended to run on user code live under `ruleset/`. Each file groups a *class of vulnerability*.

Example:

```text
ruleset/java/security/
  command-injection.yaml
  sqli.yaml
  xss.yaml
  xxe.yaml
```

Characteristics:

- Rules are written in **Semgrep-compatible YAML**.
- Each rule entry has an `id`, `severity`, `message`, `metadata`, `languages`, and pattern/mode fields (`mode: taint`, `pattern`, `patterns`, `pattern-either`, `pattern-sources`, `pattern-sinks`, etc.).
- Rules in `ruleset/` are considered **executable** unless:
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
      servlet-response-injection-sinks.yaml
      xxe-sinks.yaml
    spring/
      jdbc-sqli-sinks.yaml
      spring-response-injection-sinks.yaml
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

- **`lib: true`** explicitly marks a rule as non-executable; it will not be run by OpenTaint as a top-level rule.
- Library rules are typically:
  - Source definitions (`*untrusted-data-source*`)
  - Sink definitions (`*sinks*`)
  - Propagation or helper patterns shared across multiple vulnerabilities

---

## Join Mode

Many rules under `ruleset/` combine multiple library rules using **`mode: join`**.

Library rules expose their roles through tags:

```yaml
- id: java-ssrf-sink
  options:
    lib: true
  tags:
    - ssrf-sink
```

Example (from `ruleset/java/security/ssrf.yaml`):

```yaml
- id: ssrf
  languages:
    - java
  mode: join
  join:
    refs:
      - tag: untrusted-data-source
        as: source
      - tag: ssrf-sink
        as: sink
    on:
      - 'source.$UNTRUSTED -> sink.$UNTRUSTED'
```

Semantics:

- `mode: join` derives a composite rule from other rules referenced in `join.refs`.
- Each `ref` selects a library rule and assigns a local alias:
  - `tag` selects every enabled, non-excluded rule with that tag in the join's language. Adding the
    same tag to a custom rule extends the join.
  - `rule` selects one rule by `<path>#<rule-id>`.
  - `exclude` removes specific rule references (`<rule-id>` in the same file or
    `<path>#<rule-id>`) from a `tag` ref. It is not valid with `rule`, and its scope is limited to
    that one ref.
  - `as` defines the alias used in `on`.
- `on` correlates captures from the referenced rules. For example,
  `source.$UNTRUSTED -> sink.$UNTRUSTED` requires dataflow from the source capture
  to the sink capture.

Java joins share the `untrusted-data-source` tag and select sinks by vulnerability family.
Path traversal excludes the ordinary Spring source locally and explicitly includes its
untagged path-specific source, preserving the non-wildcard path-variable exclusion:

```yaml
refs:
  - tag: untrusted-data-source
    as: source
    exclude:
      - java/lib/spring/untrusted-data-source.yaml#spring-untrusted-data-source
  - rule: java/lib/spring/untrusted-path-source.yaml#spring-untrusted-path-source
    as: spring-path-source
  - tag: path-traversal-sink
    as: sink
on:
  - 'source.$UNTRUSTED -> sink.$UNTRUSTED'
  - 'spring-path-source.$UNTRUSTED -> sink.$UNTRUSTED'
```

Tags are language-scoped. Use `rule` for private components that should not be extended by tags.

This join mode is **based on Semgrep's join mode**, but OpenTaint extends it with custom features (such as the `->` notation in the `on` section) to express taint-style flows across multiple rule components.

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

### Whole-Object Taint: the `$VAR*` Star Operator

A metavariable occurrence in pattern text can be **starred** — `$VAR*` — to mark it as
**whole-object** taint scope: the metavariable's value *and* all of its nested fields, at
any depth (`{ $VAR, $VAR.* }`), instead of just the value itself.

- **Adjacency matters.** The `*` must directly abut the metavariable with no whitespace.
  `$X*` is the star operator; `$X * y` (space before `*`) is ordinary multiplication.
  Write multiplication with a space to avoid ambiguity.
- **Where it's valid**: any metavariable occurrence inside pattern text —
  `pattern-sources`, `pattern-sinks`, `pattern-sanitizers`, `pattern-propagators`,
  `pattern-not` / `pattern-not-inside`. It's a per-occurrence annotation, not part of the
  metavariable's identity: `$X` and `$X*` in the same rule still bind to the same value.
- **Not valid** in the `focus-metavariable` YAML field — that field always stays a plain,
  starless name.
- Per operation: a starred **source** taints the value and all its fields; a starred
  **sink**/condition matches if the value *or* any of its fields is tainted; a starred
  **sanitizer** clears taint on the value and all its fields; a starred **propagator**
  copies taint from/to the value and all its fields on the starred side.

Example — a sink that should fire when a *field* of the returned object is tainted, not
just the top-level value:

```yaml
# before: only matches when $X itself carries a taint mark
pattern-sinks:
  - patterns:
      - pattern: return $X;
```

```yaml
# after: also matches when a nested field of the returned object is tainted
pattern-sinks:
  - patterns:
      - pattern: return $X*;
```

#### Sinks: the star only takes effect under `focus-metavariable`

For a starred **sink** metavar to actually widen the check, the occurrence must be pinned
with `focus-metavariable`. A bare `pattern` with no focus collapses the sink to a generic
"is *any* argument tainted" position check, which ignores the star entirely — starring the
metavar in that shape is a no-op.

```yaml
# correct: focus-metavariable pins $Y as the sink position, so $Y* is honored
pattern-sinks:
  - patterns:
      - pattern: Sink($Y*)
      - focus-metavariable: $Y
```

This applies to both Java and Go rules.

#### `pattern-not` and the star operator (current limitation)

`pattern-not` is a structural code-shape restriction, not a taint-scope annotation, but its
support for the star operator is currently limited. When a `pattern-not` occurrence shares a
taint metavar with a positive occurrence at the *same position*, the star must match:

- `pattern-not $X*` against a positive `$X*` — supported, excludes the match.
- `pattern-not $X` against a positive plain `$X` — supported (unstarred/unstarred), excludes
  the match.
- A positive `$X*` combined with an **unstarred** `pattern-not $X` at the same position is
  **not yet supported**. The scoped "keep the field, drop the base" semantics this would
  imply isn't implemented; the analyzer emits a non-fatal load-time diagnostic and, for now,
  treats the combination as a full (exclude-all) match — the rule still loads.

If your positive occurrence is starred, star the corresponding `pattern-not` occurrence too:

```yaml
# not yet supported: emits a load-time diagnostic, treated as a full exclusion
pattern-sources:
  - patterns:
      - pattern: |
          $METHOD(..., @PathVariable $TYPE $UNTRUSTED*, ...) { ... }
      - pattern-not: |
          $METHOD(..., @PathVariable $TYPE $UNTRUSTED, ...) { ... }
```

```yaml
# write this instead — star the pattern-not occurrence to match the positive
pattern-sources:
  - patterns:
      - pattern: |
          $METHOD(..., @PathVariable $TYPE $UNTRUSTED*, ...) { ... }
      - pattern-not: |
          $METHOD(..., @PathVariable $TYPE $UNTRUSTED*, ...) { ... }
```

A scoped exclusion (drop only the field-taint arm while keeping the base-value arm live) is a
possible future refinement — it is not implemented today.

#### Go support

`$VAR*` works in Go rules with the same semantics as Java — `$X` is base-only taint, `$X*` is
base-plus-all-nested-fields — across `pattern-sources`, `pattern-sinks`, and
`pattern-sanitizers`.

**Behavior change for existing Go rules:** plain `$X` sink checks are now strictly
base-only. Previously, a Go sink's `$X` matched coarsely (base value *or* any field/struct/map
taint on it). If a Go rule relies on field-taint matching at a sink, it must now star the
occurrence (`$X*`) to keep matching — see the [Migration Notes](#migration-notes) below.

#### Known limitations

- **Go typed-metavar star doesn't parse yet.** `$C* : T` (or `Type $X* = ...`) is not
  supported in Go patterns. Use the bare-metavar form with `focus-metavariable` instead
  (e.g. a receiver `$C` pinned via `focus-metavariable: $C`, dropping the type constraint).
- **The `pattern-not` coincidence diagnostic only fires for method-signature-level
  coincidences** (e.g. a `pattern-not` on the same formal-parameter position as the starred
  positive, as in the example above) — not for call-argument-shaped coincidences. The latter
  still safely resolve to a full exclusion, but without the load-time diagnostic.

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

1. **YAML validity** for every file in `ruleset/`:
   - Root is a map and contains a `rules` list.
   - Each rule has a non-blank `id`.
2. **Test coverage for all active rules**:
   - Active rules are those in `ruleset/` where:
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
   - All rule YAMLs in `ruleset/` are syntactically valid
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
   - Add `options.lib: true` for library fragments in `lib/` (or exceptionally in `ruleset/` if they are not meant to be executed directly).

3. **Avoid duplicates**
   - Reuse library rules from `lib/` and compose them with `mode: join` where applicable.
   - Reference join sources and sinks by `tag`, and tag each new library source or sink.

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

## Migration Notes

### Spring controller-return sinks: implicit whole-object taint removed

Previously, OpenTaint's Spring integration applied an **implicit** whole-object/any-field
widening to *every* controller-return taint sink, via a hardcoded internal mechanism
(`SpringRuleProvider`) that rewrote any method-exit sink whose position was the return
value into an any-field check — regardless of whether the rule itself asked for it. The
same mechanism implicitly tainted every field of a Spring controller-parameter source, not
just the parameter value.

That hardcoded mechanism has been **removed**. The bundled Spring rules that relied on it
(`spring-response-injection-sink`, `spring-xss-html-response-sink`,
`spring-unvalidated-redirect-sink`, and the Spring untrusted-data/path sources) have been
updated to opt in explicitly with the `$VAR*` star operator described above, so their
behavior is unchanged.

**If you maintain custom rules**, this is a behavior change to be aware of: a custom rule
with a return-value sink inside a Spring controller —

```yaml
pattern-sinks:
  - patterns:
      - pattern: return $X;
```

— **no longer implicitly matches** when only a field of the returned object is tainted
(rather than `$X` itself). To restore that behavior, star the occurrence:

```yaml
pattern-sinks:
  - patterns:
      - pattern: return $X*;
```

Likewise, a custom source rule matching a Spring controller parameter now taints only the
parameter value unless you star the occurrence (`$VAR*`) to also taint its fields.

### Go: sink `$X` is now strictly base-only

Go's `$VAR*` star operator support (see above) came with a related default-semantics fix:
previously, a Go sink pattern's plain `$X` matched coarsely — it fired on taint anywhere on
the value, including its fields, structs, and maps. That coarse default has been corrected:
a plain `$X` sink now checks the base value only, matching Java's semantics.

**If you maintain custom Go rules**, this is a behavior change to be aware of: a sink rule
that used to rely on `$X` catching field/struct/map taint —

```yaml
pattern-sinks:
  - patterns:
      - pattern: Sink($X)
      - focus-metavariable: $X
```

— no longer matches when only a field of `$X` is tainted. Star the occurrence to restore
that behavior:

```yaml
pattern-sinks:
  - patterns:
      - pattern: Sink($X*)
      - focus-metavariable: $X
```

---

## License

This project is released under the [MIT License](LICENSE).

The [core analysis engine](https://github.com/seqra/opentaint/tree/main/core) is released under the [Apache 2.0 License](../LICENSE.md).

Rule content may incorporate or adapt patterns originally published under various open-source licenses (for example, from community rule sets). Where applicable, original provenance and license information is recorded in rule `metadata`.
