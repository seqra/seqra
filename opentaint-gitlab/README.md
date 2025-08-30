# Seqra GitLab CI template

Run [Seqra](https://github.com/seqra/seqra) static code analysis in your GitLab CI pipelines.
Generates a SARIF report for code scanning integration or further processing.


### Quick Start

### Scan

> **Note:** This template runs on **Linux x86\_64** environments and requires **Docker-in-Docker**.

### Example: Run Seqra

```yaml
include:
  - remote: https://raw.githubusercontent.com/seqra/seqra-gitlab/refs/heads/main/seqra.gitlab-ci.yml

stages:
  - analysis

seqra-job:
  extends: .seqra-template
  variables:
    PROJECT_ROOT: "."
```


### All Inputs

```yaml
include:
  - remote: https://raw.githubusercontent.com/seqra/seqra-gitlab/refs/heads/main/seqra.gitlab-ci.yml

stages:
  - analysis

seqra-job:
  extends: .seqra-template
  variables:
    # Relative path to the root of the analyzed project
    PROJECT_ROOT: "."
    # Tag of seqra release
    SEQRA_VERSION: "v2.4.0"
    # Comma-separated paths to rule files or directories (e.g., "rules/custom.yml,rules/extra")
    RULES_PATH: "builtin"
    # Comma-separated severity levels to report: note, warning, error
    SEVERITY: "warning,error"
    # Scan timeout
    TIMEOUT: "15m"
```


## Artifacts

After the job completes, you’ll find:

* `seqra-job:archive` in the job artifacts.
* These can be consumed by other CI jobs or uploaded to a code scanning service.


## Troubleshooting

* **Monorepos:** You can analyze only the project you need using `PROJECT_ROOT`.
* **Timeouts:** If the scan times out, increase `TIMEOUT` (e.g., `30m`).

## Changelog

See [CHANGELOG](CHANGELOG.md).

## License
This project is released under the [MIT License](LICENSE).

The [core analysis engine](https://github.com/seqra/seqra-jvm-sast) is source-available under the [Functional Source License (FSL-1.1-ALv2)](https://fsl.software/), which converts to Apache 2.0 two years after each release. You can use Seqra for free, including for commercial use, except for competing products or services.
