# OpenTaint GitHub Action

Run [OpenTaint](https://github.com/seqra/opentaint) static analysis in your CI, generate a SARIF report, and optionally upload it to GitHub Code Scanning.


## Usage

> **Note:** The action expects **Linux** runners.

### Prerequisites

OpenTaint analyzes compiled bytecode of your project. Before running this action, ensure your CI environment is configured to compile the project. For example:

- **Java/Kotlin projects:** Set up a JDK using `actions/setup-java@v5`

### Quick Start

### Scan

```yaml
name: OpenTaint Analysis
on:
    workflow_dispatch

jobs:
  opentaint:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout your repository
        uses: actions/checkout@v6

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Run OpenTaint code analysis
        uses: seqra/opentaint/github@v2
```


### Scan and upload to GitHub code scanning alerts

```yaml
name: OpenTaint Analysis
on:
    workflow_dispatch

# Required for Code Scanning upload
permissions:
  contents: read
  security-events: write

jobs:
  opentaint:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout your repository
        uses: actions/checkout@v6

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Run OpenTaint code analysis
        uses: seqra/opentaint/github@v2
        with:
          upload-sarif: 'true'
          artifact-name: 'sarif'
```


### All Inputs

```yaml
name: OpenTaint Analysis
on:
    workflow_dispatch

# Required for Code Scanning upload
permissions:
  contents: read
  security-events: write

jobs:
  opentaint:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout your repository
        uses: actions/checkout@v6

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Run OpenTaint code analysis
        uses: seqra/opentaint/github@v2
        with:
            # Relative path under $GITHUB_WORKSPACE to the root of the analyzed project
            project-root: '.'

            # Should opentaint-action upload sarif to GitHub Code Security
            upload-sarif: 'false'

            # OpenTaint version selector:
            # - latest (latest stable)
            # - v0 (latest stable in major v0)
            # - v0.1 (latest stable in minor v0.1)
            # - v0.1.1 (exact)
            # Default is 'v0'
            opentaint-version: 'v0'

            # Paths to custom rules directories (comma-separated)
            # By default it uses builtin rules
            rules-path: 'builtin'

            # Name of uploaded artifact
            artifact-name: 'opentaint.sarif'

            # Log level
            verbosity: 'info'

            # Scan timeout
            timeout: '15m'

            # Severity levels to report (comma-separated)
            # Valid values: note, warning, error
            severity: 'warning,error'
```


## Artifacts

After the job completes, you’ll find:

* A SARIF artifact named `sarif` (configurable) will be uploaded to the workflow run.
* If `upload-sarif: 'true'`, the SARIF is also sent to **Security → Code scanning alerts** in your repo.


## Version Selection

`opentaint-version` supports flexible selectors so you do not need to update this action for every OpenTaint release:

* `latest` - always use the latest stable release
* `v0` - use the latest stable release in major version 0 (default)
* `v0.1` - use the latest stable patch in minor version 0.1
* `v0.1.1` - pin an exact release

Examples:

```yaml
with:
  opentaint-version: 'latest'
```

```yaml
with:
  opentaint-version: 'v0.1'
```


## Permissions

* For **artifact upload**: default permissions are fine.
* For **Code Scanning upload**: add

  ```yaml
  permissions:
    contents: read
    security-events: write
  ```


## Troubleshooting

* **"Compilation has failed:"** OpenTaint needs to compile your project to analyze bytecode. Ensure you have set up the required build tools (e.g., JDK via `actions/setup-java@v5`) before running this action. See [Prerequisites](#prerequisites).
* **Monorepos:** You can analyze only the project you need using `project-root`.
* **Timeouts:** If the scan times out, increase `timeout` (e.g., `30m`).


## Changelog
See [CHANGELOG](CHANGELOG.md).


## License
This GitHub action is released under the [MIT License](LICENSE).

The [core analysis engine](https://github.com/seqra/opentaint/tree/main/core) is released under the [Apache 2.0 License](../LICENSE.md).
