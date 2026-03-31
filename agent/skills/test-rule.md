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

```bash
opentaint agent test-rules ./agent-test-compiled \
  --ruleset builtin --ruleset ./agent-rules
```

### 5. Interpret results

Read `test-result.json` in the output directory:

- **success**: Test passed (positive triggered, negative didn't)
- **falseNegative**: Positive sample did NOT trigger -> rule patterns too narrow
- **falsePositive**: Negative sample DID trigger -> rule patterns too broad
- **skipped**: Rule not found -> check `value` path and `id` match the rule file
- **disabled**: Rule is disabled

## Annotation Fields

- `value`: Path to rule YAML file, relative to ruleset root
- `id`: Rule ID within that file

## Troubleshooting

- **falseNegative**: Broaden source/sink patterns, check metavariable names match
- **falsePositive**: Add `pattern-not`, `pattern-sanitizers`, or narrow `metavariable-regex`
- **skipped**: Verify rule file path and ID, check rule is not disabled
