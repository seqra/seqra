# Skill: Create YAML Config

Create YAML passThrough propagation rules for library methods.

## Prerequisites

- External methods list analyzed (analyze-findings skill)
- Understanding of which methods need propagation rules

## Procedure

### 1. Create config file

Create `agent-config/custom-propagators.yaml` with `passThrough:` rules.

### 2. Common patterns

**Simple getter** (taint on `this` to `result`):
```yaml
passThrough:
  - function: com.example.lib.DataWrapper#getValue
    copy:
      - from: this
        to: result
```

**Argument-to-result**:
```yaml
passThrough:
  - function: com.example.lib.Converter#convert
    copy:
      - from: arg(0)
        to: result
```

**Builder pattern**:
```yaml
passThrough:
  - function: com.example.lib.Builder#withName
    copy:
      - from: arg(0)
        to: this
      - from: arg(0)
        to: result
      - from: this
        to: result
```

**Object with internal state** (using `<rule-storage>`):
```yaml
passThrough:
  # Store taint
  - function: com.example.lib.Container#put
    copy:
      - from: arg(0)
        to:
          - this
          - .com.example.lib.Container#<rule-storage>#java.lang.Object
  # Retrieve taint
  - function: com.example.lib.Container#get
    copy:
      - from:
          - this
          - .com.example.lib.Container#<rule-storage>#java.lang.Object
        to: result
```

**Package-wide getter pattern**:
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

**Conditional propagation**:
```yaml
passThrough:
  - function: com.example.lib.Parser#parse
    condition:
      typeIs:
        position: arg(0)
        type: java.lang.String
    copy:
      - from: arg(0)
        to: result
```

### 3. Run with config

`--approximations-config` is repeatable. Each occurrence is OVERRIDE-merged with the default.

```bash
opentaint scan --project-model ./opentaint-project \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --approximations-config ./agent-config/custom-propagators.yaml
```

## Reference

### Position values
- `this`, `result`, `arg(0)`, `arg(1)`, ..., `arg(*)`
- Position modifiers (YAML list): `.[*]` (array element), `.ClassName#fieldName#fieldType` (field), `.<rule-storage>` (synthetic state)

### Function matching
- Simple: `package.Class#method`
- Complex: `{package, class, name}` with optional `pattern:` regex

### Overrides
- `overrides: true` (default): applies to class and all subclasses
- `overrides: false`: exact class only

### Conditions
`typeIs`, `annotatedWith`, `isConstant`, `isNull`, `constantMatches`, `tainted`, `numberOfArgs`, `methodAnnotated`, `classAnnotated`, `methodNameMatches`, `classNameMatches`, `isStaticField`, `anyOf`, `allOf`, `not`

## When to use YAML vs code-based approximation

- Simple from-to propagation -> **YAML** (this skill)
- Lambda/callback invocation -> **Code-based** (create-approximation skill)
- Non-deterministic branching -> **Code-based**
