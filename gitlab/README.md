# OpenTaint GitLab CI template

Run [OpenTaint](https://github.com/seqra/opentaint) static code analysis in your GitLab CI pipelines.
Generates a SARIF report for code scanning integration or further processing.


### Quick Start

### Scan

> **Note:** This template runs on **Linux x86\_64** environments.

The template does not pin a Docker image. Your job must run in an environment that provides:
`bash`, `git`, `curl`, `python3`, `tar`, and valid CA certificates.

On Debian/Ubuntu-based images, any missing tools are installed automatically via `apt-get`.
On other images, the job fails with an error listing the missing tools.

### Example: Run OpenTaint

```yaml
include:
  - remote: https://raw.githubusercontent.com/seqra/opentaint/gitlab/v0/gitlab/opentaint.gitlab-ci.yml

stages:
  - analysis

opentaint-job:
  extends: .opentaint-template
  image: ubuntu:24.04
  variables:
    PROJECT_ROOT: "."
```


### All Inputs

```yaml
include:
  - remote: https://raw.githubusercontent.com/seqra/opentaint/gitlab/v0/gitlab/opentaint.gitlab-ci.yml

stages:
  - analysis

opentaint-job:
  extends: .opentaint-template
  image: ubuntu:24.04
  variables:
    # Relative path to the root of the analyzed project
    PROJECT_ROOT: "."
    # OpenTaint version selector:
    # - latest (latest stable)
    # - v0 (latest stable in major v0)
    # - v0.1 (latest stable in minor v0.1)
    # - v0.1.0 (exact)
    # Default is 'v0'
    OPENTAINT_VERSION: "v0.1.0"
    # Comma-separated paths to rule files or directories (e.g., "rules/custom.yml,rules/extra")
    RULES_PATH: "builtin"
    # Comma-separated severity levels to report: note, warning, error
    SEVERITY: "warning,error"
    # Scan timeout
    TIMEOUT: "15m"
    # Java version for compilation (e.g., 8, 11, 17, 21, 25)
    JAVA_VERSION: ""
```


### Scan with a specific Java version

```yaml
include:
  - remote: https://raw.githubusercontent.com/seqra/opentaint/gitlab/v0/gitlab/opentaint.gitlab-ci.yml

stages:
  - analysis

opentaint-job:
  extends: .opentaint-template
  image: ubuntu:24.04
  variables:
    PROJECT_ROOT: "."
    JAVA_VERSION: "25"
```


## Artifacts

After the job completes, you’ll find:

* `opentaint-job:archive` in the job artifacts.
* These can be consumed by other CI jobs or uploaded to a code scanning service.


## Version Selection

There are two independent version selectors:

### CI template version

Controlled by the tag in the `include:` URL. Using a major-version tag ensures you always get the latest compatible template without manual updates.

* `gitlab/v0` — latest stable template in major version 0 (recommended)
* `gitlab/v0.1` — pin to a specific minor version
* `gitlab/v0.1.0` — pin to an exact version
* `gitlab/latest` — always use the latest template

```yaml
include:
  - remote: https://raw.githubusercontent.com/seqra/opentaint/gitlab/v0.1/gitlab/opentaint.gitlab-ci.yml
```

### OpenTaint CLI version

Controlled by the `OPENTAINT_VERSION` variable. This determines which release of the analysis engine is downloaded at runtime.

* `latest` — always use the latest stable release
* `v0` — use the latest stable release in major version 0 (default)
* `v0.1` — use the latest stable patch in minor version 0.1
* `v0.1.0` — pin an exact release

```yaml
variables:
  OPENTAINT_VERSION: "latest"
```

```yaml
variables:
  OPENTAINT_VERSION: "v0.1"
```


## Troubleshooting

* **Monorepos:** You can analyze only the project you need using `PROJECT_ROOT`.
* **Timeouts:** If the scan times out, increase `TIMEOUT` (e.g., `30m`).
* **Missing tools:** If you see `ERROR: Missing required tools`, set `image:` in your job to a Debian/Ubuntu-based image (e.g., `ubuntu:24.04`) or use a custom image that provides the required tools.

## Changelog

See [CHANGELOG](CHANGELOG.md).

## License
This GitLab CI template is released under the [MIT License](LICENSE).

The [core analysis engine](https://github.com/seqra/opentaint/tree/main/core) is released under the [Apache 2.0 License](../LICENSE.md).
