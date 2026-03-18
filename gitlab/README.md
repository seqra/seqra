# OpenTaint GitLab CI template

Run [OpenTaint](https://github.com/seqra/opentaint) static code analysis in your GitLab CI pipelines.
Generates a SARIF report for code scanning integration or further processing.


### Quick Start

### Scan

> **Note:** This template runs on **Linux x86\_64** environments and requires **Docker-in-Docker**.

### Example: Run OpenTaint

```yaml
include:
  - remote: https://raw.githubusercontent.com/seqra/opentaint/main/gitlab/opentaint.gitlab-ci.yml

stages:
  - analysis

opentaint-job:
  extends: .opentaint-template
  variables:
    PROJECT_ROOT: "."
```


### All Inputs

```yaml
include:
  - remote: https://raw.githubusercontent.com/seqra/opentaint/main/gitlab/opentaint.gitlab-ci.yml

stages:
  - analysis

opentaint-job:
  extends: .opentaint-template
  variables:
    # Relative path to the root of the analyzed project
    PROJECT_ROOT: "."
    # Tag of OpenTaint release
    OPENTAINT_VERSION: "v2.4.0"
    # Comma-separated paths to rule files or directories (e.g., "rules/custom.yml,rules/extra")
    RULES_PATH: "builtin"
    # Comma-separated severity levels to report: note, warning, error
    SEVERITY: "warning,error"
    # Scan timeout
    TIMEOUT: "15m"
```


## Artifacts

After the job completes, you’ll find:

* `opentaint-job:archive` in the job artifacts.
* These can be consumed by other CI jobs or uploaded to a code scanning service.


## Troubleshooting

* **Monorepos:** You can analyze only the project you need using `PROJECT_ROOT`.
* **Timeouts:** If the scan times out, increase `TIMEOUT` (e.g., `30m`).

## Changelog

See [CHANGELOG](CHANGELOG.md).

## License
This GitLab CI template is released under the [MIT License](LICENSE).

The [core analysis engine](https://github.com/seqra/opentaint/tree/main/core) is released under the [Apache 2.0 License](../LICENSE.md).
