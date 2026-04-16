# Spring XSS Rule: Dynamic Verification and Content-Type Discrimination

**Date:** 2026-04-17
**Rule:** `rules/ruleset/java/lib/spring/spring-xss-html-response-sinks.yaml`
**Samples:** `rules/test/src/main/java/security/xss/XssHtmlResponseSpringSamples.java`

## Problem

The current Spring XSS sink rule distinguishes TP/FP only at the
annotation level (`produces = "<string literal>"`). It cannot see
content-type signals that the handler sets programmatically on a
`ResponseEntity` builder or via `HttpHeaders`. Three consequences:

1. `ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(untrusted)`
   is flagged (false positive) even though the browser will not render
   HTML.
2. `ResponseEntity<byte[]>` with no explicit content type (the
   Stirling-PDF `ConvertEmlToPDF#convertEmlToPdf` error-path shape at
   line 143 of commit `c7b713a`) is *not* flagged today — but it *is*
   XSS, because Spring's `ByteArrayHttpMessageConverter` advertises
   `application/octet-stream, */*` as supported types and content
   negotiation with `Accept: text/html` produces `Content-Type: text/html`.
3. The TP/FP posture of each variant is asserted without runtime
   evidence. Future edits can silently flip labels.

## Goals

- For every sink pattern in the rule, exist a compiled static sample
  (`@PositiveRuleSample` or `@NegativeRuleSample`) whose label is
  grounded in a runtime probe of a live Spring handler.
- Add patterns to the rule so `ResponseEntity` / `ResponseEntity<T>`
  handlers that set an explicit non-HTML content type on the builder
  (or via `HttpHeaders`) are *not* flagged.
- Add a sample reproducing the Stirling-PDF shape and have the rule
  flag it (it is a real XSS).

## Non-goals

- Committing a persistent runtime test harness. Dynamic verification
  runs in a throwaway directory and is discarded. Only static samples
  and rule changes are committed.
- Supporting arbitrary content-type expressions (e.g.
  `headers.setContentType(someVariable)`). We only discriminate on
  literal `MediaType.*` constants and string literals.
- Cross-method flow. The rule discriminates at method scope; if a
  handler has multiple returns with different content types, the
  method is treated as safe when any return sets a non-HTML type.

## Dynamic verification harness (ephemeral)

Location: `/tmp/xss-verify/` — **not** committed.

- Spring Boot 2.7.x (aligns with `spring-web:5.3.39` already used in
  `rules/test/build.gradle.kts`).
- One `@Controller` per matrix row; each endpoint reflects a
  `@RequestParam` into its response.
- MockMvc (or `TestRestTemplate` against an embedded Tomcat) fires two
  requests per endpoint:
  - `Accept: text/html` + `name=<script>alert(1)</script>`
  - `Accept: */*` + same payload
- Recorded for each run: response status, `Content-Type` header,
  whether the body contains the raw, unescaped `<script>` substring.
- Verdict: **TP** if `Content-Type` is `text/html*` **and** body
  contains raw `<script>`; **FP** otherwise.

A short Markdown table of verdicts is produced and pasted into the
implementation plan for traceability, but the harness itself is
discarded once the truth table is frozen.

### Stirling-PDF reproduction

Separately spin up a controller with the exact shape from
`ConvertEmlToPDF#convertEmlToPdf` error path (commit `c7b713a`, line 143):

```java
@Controller
class StirlingShape {
    @PostMapping("/eml/pdf")
    public ResponseEntity<byte[]> convert(@RequestParam String filename) {
        String err = "Conversion failed for " + filename;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(err.getBytes(StandardCharsets.UTF_8));
    }
}
```

Fire `Accept: text/html` + `filename=<script>alert(1)</script>`.
Expected verdict: `Content-Type: text/html`, body contains raw
`<script>`, XSS confirmed.

## Sample matrix (committed to `XssHtmlResponseSpringSamples.java`)

| # | Body type | Content-type signal | Label |
|---|---|---|---|
| 1 | `String` | none | `@PositiveRuleSample` |
| 2 | `String` | `produces="text/html"` | `@PositiveRuleSample` |
| 3 | `String` | `produces="application/json"` | `@NegativeRuleSample` |
| 4 | `String` | `produces="text/plain"` | `@NegativeRuleSample` |
| 5 | `String` | `produces="application/pdf"` | `@NegativeRuleSample` |
| 6 | `String` | `produces="application/octet-stream"` | `@NegativeRuleSample` |
| 7 | `ResponseEntity<String>` | none | `@PositiveRuleSample` |
| 8 | `ResponseEntity<String>` | `produces="application/json"` | `@NegativeRuleSample` |
| 9 | `ResponseEntity<String>` | `.contentType(MediaType.TEXT_HTML)` | `@PositiveRuleSample` |
| 10 | `ResponseEntity<String>` | `.contentType(MediaType.APPLICATION_JSON)` | `@NegativeRuleSample` |
| 11 | `ResponseEntity<String>` | `.header("Content-Type", "text/html")` | `@PositiveRuleSample` |
| 12 | `ResponseEntity<String>` | `.header("Content-Type", "application/json")` | `@NegativeRuleSample` |
| 13 | `ResponseEntity<String>` | `HttpHeaders.setContentType(APPLICATION_JSON)` via `new ResponseEntity<>(body, headers, status)` | `@NegativeRuleSample` |
| 14 | `ResponseEntity` (raw) | none | `@PositiveRuleSample` |
| 15 | `ResponseEntity` (raw) | `.contentType(APPLICATION_JSON)` | `@NegativeRuleSample` |
| 16 | `ResponseEntity<byte[]>` | none — Stirling-PDF shape | `@PositiveRuleSample` |
| 17 | `ResponseEntity<byte[]>` | `produces="text/html"` (existing) | `@PositiveRuleSample` |
| 18 | `ResponseEntity<byte[]>` | `.contentType(APPLICATION_PDF)` | `@NegativeRuleSample` |
| 19 | `ResponseEntity<byte[]>` | `.contentType(APPLICATION_OCTET_STREAM)` | `@NegativeRuleSample` |
| 20 | `void` + `HttpServletResponse` | `setContentType("application/json")` + writer | `@NegativeRuleSample` |
| 21 | `void` + `HttpServletResponse` | `setHeader("Content-Type","application/json")` + writer | `@NegativeRuleSample` |

Rows whose labels the dynamic run contradicts are flipped before
commit, with the runtime evidence recorded inline in the plan.

## Rule changes

The existing rule has four sink blocks:

- **1a** String return without `produces`
- **1b** `ResponseEntity<String>` return
- **2** Direct `HttpServletResponse` writer with `text/html` content type
- **3** `produces = "text/html"` on any return type

Changes per block:

### String return (1a)

- Add negative exclusions for `produces = "application/pdf"` and
  `produces = "application/octet-stream"` (currently only json +
  text/plain). Keeps existing JSON + text/plain exclusions.

### ResponseEntity<String> (1b)

Add new `pattern-not-inside` exclusions matching the method body:

```yaml
- pattern-not-inside: |
    ResponseEntity<String> $M(...) {
      ...
      $X.contentType(MediaType.APPLICATION_JSON)
      ...
    }
- pattern-not-inside: |
    ResponseEntity<String> $M(...) {
      ...
      $X.header("Content-Type", "application/json")
      ...
    }
- pattern-not-inside: |
    ResponseEntity<String> $M(...) {
      ...
      $H.setContentType(MediaType.APPLICATION_JSON)
      ...
    }
```

(plus equivalents for `APPLICATION_PDF`, `APPLICATION_OCTET_STREAM`,
`TEXT_PLAIN`.)

### New block: `ResponseEntity` (raw) and `ResponseEntity<byte[]>`

Introduce two new sink blocks mirroring the `ResponseEntity<String>`
shape but with different `pattern-inside` method signatures:

```yaml
- pattern-inside: |
    ResponseEntity $M(...) { ... }
```

and

```yaml
- pattern-inside: |
    ResponseEntity<byte[]> $M(...) { ... }
```

Each with the same `pattern-not-inside` exclusions as 1b plus
`APPLICATION_PDF` / `APPLICATION_OCTET_STREAM`. This is what flags
the Stirling-PDF shape and suppresses the `.contentType(APPLICATION_PDF)`
FPs.

### Servlet writer (2)

Add negative exclusion so that a handler which sets
`setContentType("application/json")` before calling the writer is
*not* flagged. Today the rule's pattern-inside only enumerates HTML
variants, so JSON handlers are silently missed — good — but we should
add the symmetric negative sample for documentation.

### Produces = text/html (3)

Already flags all body types via the `$RETURNTYPE $METHOD(...)`
pattern. No change needed, but the sample matrix exercises it for
`String`, `ResponseEntity<String>`, and `ResponseEntity<byte[]>`.

## Risk: `pattern-not-inside` for generic types on this branch

This branch (`misonijnik/match-generic-types`) changed how generic
type arguments are matched (see commits `7b5e263b`, `39f84cfc`). The
new `pattern-not-inside: ResponseEntity<$T> $M(...) { ... }` from the
current uncommitted diff already depends on that change; the new
blocks follow the same pattern. If the generic-matching logic has
regressions with `pattern-not-inside`, we may need to fall back to
listing concrete parameterizations (`<String>`, `<byte[]>`, `<Resource>`).
I'll detect this in the plan step where I load each revised rule into
opentaint and diff the fired locations.

## Risk: builder call inside a larger expression

The `pattern-not-inside: ... $X.contentType(...) ...` match relies on
the call appearing as a statement-level expression inside the method
body. Chained in a single return (`return ResponseEntity.ok().contentType(...).body(u)`)
the call is *inside* the return expression, not a standalone
statement. I'll probe whether opentaint's `...` in method body scope
matches nested expressions; if not, I'll duplicate the exclusion to
cover `return $X.contentType(...).body(...);` as a standalone pattern.

## Verification gate before commit

- Every row in the sample matrix has a dynamic-run verdict recorded in
  the implementation plan.
- Running `./gradlew :rules/test:checkRulesCoverage` passes.
- Running opentaint against `rules/test` produces, for each new
  sample, exactly the expected set of findings (no new TPs missing,
  no new FPs introduced on unchanged code).
- The existing diff-against-branch behavior of the CI is preserved.
