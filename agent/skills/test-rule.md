# Skill: Test Rule

Create test samples for a rule and verify it works correctly.

## Prerequisites

- `opentaint` CLI available
- Rules created (create-rule skill)
- Target project dependencies known

## Procedure

### 1. Bootstrap test project

```bash
opentaint agent init-test-project ./agent-test-project \
  --dependency "javax.servlet:javax.servlet-api:4.0.1"
```

Or manually create a Gradle project with the test utility JAR and required dependencies.

### 2. Create test samples

Create Java files in `src/main/java/test/` with `@PositiveRuleSample` and `@NegativeRuleSample` annotations:

```java
package test;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.opentaint.sast.test.util.NegativeRuleSample;
import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.Statement;

public class MyVulnTest {
    private Connection db;

    @PositiveRuleSample(value = "java/security/my-vuln.yaml", id = "my-vulnerability")
    public void vulnerable(HttpServletRequest req) throws Exception {
        String input = req.getParameter("id");
        Statement stmt = db.createStatement();
        stmt.executeQuery("SELECT * FROM users WHERE id = " + input);
    }

    @NegativeRuleSample(value = "java/security/my-vuln.yaml", id = "my-vulnerability")
    public void safe(HttpServletRequest req) throws Exception {
        String input = req.getParameter("id");
        var pstmt = db.prepareStatement("SELECT * FROM users WHERE id = ?");
        pstmt.setString(1, input);
        pstmt.executeQuery();
    }
}
```

### 3. Build test project

```bash
opentaint compile ./agent-test-project -o ./agent-test-compiled
```

### 4. Run rule tests

**Always specify `-o`** so results are written to a known location:

```bash
opentaint agent test-rules ./agent-test-compiled \
  -o ./agent-test-results \
  --ruleset builtin --ruleset ./agent-rules
```

### 5. Interpret results

Read `./agent-test-results/test-result.json`:

- **success**: Test passed (positive triggered, negative didn't)
- **falseNegative**: Positive sample did NOT trigger -> rule patterns too narrow
- **falsePositive**: Negative sample DID trigger -> rule patterns too broad
- **skipped**: Rule not found -> check `value` path and `id` match the rule file
- **disabled**: Rule is disabled

## Testing Spring-app rules

Some rules only fire inside a full Spring MVC entry-point graph (controllers, beans, dispatcher). A plain unit-like sample with `@PositiveRuleSample` on a bare method will not trigger them, because the tainted data must flow from a discovered `@Controller` entry point.

For these rules, create **one dedicated Gradle sub-project per sample**. Each sub-project represents a complete, minimal Spring application containing **exactly one** `@PositiveRuleSample` or `@NegativeRuleSample` annotation. Split positive and negative cases into separate sub-projects, e.g. `xss-spring-test-positive` and `xss-spring-test-negative`.

### How detection works

`TestProjectAnalyzer` computes a `testSetName` per module as `module.moduleSourceRoot.relativeTo(project.sourceRoot)`, with `/` replaced by `-` (see `core/src/main/kotlin/org/opentaint/jvm/sast/project/TestProjectAnalyzer.kt`). If the name starts with `spring-app-tests`, the module is treated as a Spring test set:

- All sample annotations in the module are collected as usual.
- Each sample is wrapped in a `SpringTestSample` that uses the Spring dispatcher method as the analysis entry point instead of the annotated method itself.
- Taint therefore originates from real `@Controller` request parameters and must reach the annotated sink method through normal Spring wiring.

Consequence: the annotated method is only a marker for **which rule to run and the expected verdict**. The actual vulnerable/safe flow must be reachable from a controller in the same module. Keep each module to a single annotation so the verdict is unambiguous.

### Project layout

Use a multi-module Gradle build where every `spring-app-tests/<name>` directory is its own sub-project:

```
agent-test-project/
├── settings.gradle.kts
├── build.gradle.kts
└── spring-app-tests/
    ├── xss-spring-test-positive/
    │   ├── build.gradle.kts
    │   └── src/main/java/test/
    │       ├── VulnerableController.java    // @Controller with the tainted flow
    │       └── VulnerableSink.java          // carries the single @PositiveRuleSample
    └── xss-spring-test-negative/
        ├── build.gradle.kts
        └── src/main/java/test/
            ├── SafeController.java
            └── SafeSink.java                // carries the single @NegativeRuleSample
```

`settings.gradle.kts` should auto-discover every `spring-app-tests/*/build.gradle.kts` so adding a new case only requires a new directory. See `rules/test/settings.gradle.kts` for a reference implementation.

### Required dependencies

Each Spring sub-project must pull in at least:

- `compileOnly` on `opentaint-sast-test-util` (for the sample annotations)
- `org.springframework:spring-webmvc` and `spring-context` (so `@Controller` is recognized)
- Any libraries used by the sample itself (servlet-api, JDBC, etc.)

### Compile and run

Compile and test the multi-module project the same way as a regular test project:

```bash
opentaint compile ./agent-test-project -o ./agent-test-compiled
opentaint agent test-rules ./agent-test-compiled \
  -o ./agent-test-results \
  --ruleset builtin --ruleset ./agent-rules
```

Each `spring-app-tests/<name>` sub-project becomes an independent test set and appears as its own entry in `test-result.json`.

### Common pitfalls

- **No `@Controller` in the module** -> `TestProjectAnalyzer` logs `No spring entry point found` and the sample is analyzed without Spring context, usually producing a false negative. Always include a controller that reaches the sink.
- **More than one annotation per module** -> the module still runs, but results become ambiguous; keep it to one sample per sub-project.
- **Module path does not start with `spring-app-tests`** -> `isSpringAppTestSet()` returns `false` and the sample is analyzed as a regular method-level test, so Spring-specific flows will not be triggered.

## Annotation Fields

- `value`: Path to rule YAML file, relative to ruleset root (e.g. `java/security/my-vuln.yaml`)
- `id`: Short rule ID within that file (the `id` field from the YAML, e.g. `my-vulnerability`)

**Note**: The annotation `id` field uses the **short** rule ID (as written in the YAML file).
This is different from `--rule-id` in `opentaint scan`, which requires the **full** rule ID
in the format `<ruleSetRelativePath>:<shortId>` (e.g. `java/security/my-vuln.yaml:my-vulnerability`).

## Troubleshooting

- **falseNegative**: Broaden source/sink patterns, check metavariable names match
- **falsePositive**: Add `pattern-not`, `pattern-sanitizers`, or narrow `metavariable-regex`
- **skipped**: Verify rule file path and ID, check rule is not disabled
