# OpenTaint + ZAP Security Scan Action

GitHub Action that combines [OpenTaint](https://github.com/seqra/opentaint) static analysis with [ZAP](https://www.zaproxy.org/) dynamic
testing to identify and validate security vulnerabilities

## Quick Start

```yaml
name: Security Scan
on: pull_request

permissions:
  contents: read
  security-events: write

env:
  APP_URL: http://localhost:8080 # Your app url

jobs:
  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Start application
        run: # Start your app here

      - name: Run security scan
        uses: seqra/opentaint/github/zap@v0
        with:
          mode: 'differential'
          target: $APP_URL
```

## Inputs

### Required

- `target` - Target URL for ZAP dynamic scan (must be a running application)

### Optional

- `mode` - Scan mode: `full` (scans current branch) or `differential` (compares PR against base branch). Default: `full`
- `template` - Path to ZAP automation template. Default: `template.yaml`
- `context-name` - Context name from template to use. Default: first context
- `artifact-name` - Name of uploaded artifact. Default: `opentaint-zap-scan-results`
- `upload-sarif` - Upload validated findings to GitHub Code Security. Default: `true`

### OpenTaint Options

- `project-root` - Project root path. Default: `.`
- `opentaint-version` - OpenTaint version selector. Default: `v0`
- `rules-path` - Custom rules directories (comma-separated)
- `opentaint-timeout` - Scan timeout. Default: `15m`

### ZAP Options

- `zap-docker-image` - ZAP Docker image. Default: `ghcr.io/zaproxy/zaproxy:stable`
- `zap-docker-env-vars` - Environment variables for ZAP container
- `zap-cmd-options` - Additional ZAP command line options

## Template

The action uses a [ZAP automation framework](https://www.zaproxy.org/docs/desktop/addons/automation-framework/) YAML
file

### Requirements

- At least one context in `env.contexts`
- API import job (`openapi` or `graphql`)
- At least one CWE policy with format `policy-CWE-{number}`

### Details

The action automatically:

- Adds a required JSON report if missing
- Normalizes all report directories to `/zap/wrk/zap-output`
- Generates CWE-specific contexts based on OpenTaint findings
- Creates activeScan jobs for matching CWEs

Policy naming: Use `policy-CWE-{number}` format (e.g., `policy-CWE-89` for SQL Injection, `policy-CWE-79` for XSS).

### Example

```yaml
env:
  contexts:
    - name: default-context
      urls:
        - http://localhost:8080

jobs:
  - type: openapi
    parameters:
      context: default-context
      targetUrl: http://localhost:8080
      apiUrl: http://localhost:8080/v3/api-docs

  - type: activeScan-config
    parameters:
      threadPerHost: 40

  - type: activeScan-policy
    parameters:
      name: policy-CWE-89
    policyDefinition:
      defaultStrength: INSANE
      defaultThreshold: 'OFF'
      rules:
        - id: 40018
          threshold: MEDIUM
```

See [template.yaml](template.yaml) for a complete example

## Artifacts

The action uploads an artifact with:

- `validated.sarif` - sarif with ZAP-confirmed vulnerabilities
- `zap-automation.yaml` - generated YAML automation file
- ZAP reports from `/zap/wrk/zap-output` folder
- Sarif from OpenTaint scan based on mode:
  - `full`: `opentaint.sarif` (all OpenTaint findings)
  - `differential`: `filtered-opentaint.sarif` (new findings only)

## Examples

- [example.yml](examples/example.yml) - Differential scan for pull requests
- [example-full-scan.yml](examples/example-full-scan.yml) - Full scan for the main branch

## Requirements

- Application must be running and accessible at target URL
- Java/Kotlin projects with Spring frameworks
- OpenAPI or GraphQL schema for API import
