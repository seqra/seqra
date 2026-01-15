# Docker Usage

Seqra is available as a Docker image with all dependencies included. This provides a consistent, isolated environment for security analysis without requiring local installation of Seqra, Java, or other dependencies.


## Quick Start

Pull the image and scan a project:

```bash
docker pull ghcr.io/seqra/seqra:latest

docker run --rm \
  -v /path/to/your/project:/project \
  -v /path/to/output:/output \
  ghcr.io/seqra/seqra:latest \
  seqra scan --output /output/results.sarif /project
```


## Pulling the Image

The Seqra Docker image is available from GitHub Container Registry.

Pull the latest version:
```bash
docker pull ghcr.io/seqra/seqra:latest
```

Pull a specific version (recommended for reproducible builds):
```bash
docker pull ghcr.io/seqra/seqra:v1.2.3
```

**Available platforms:** `linux/amd64`, `linux/arm64`


## Running Scans

### Basic Scan

Mount your project directory and an output directory, then run the scan:

```bash
docker run --rm \
  -v /path/to/your/project:/project \
  -v /path/to/output:/output \
  ghcr.io/seqra/seqra:latest \
  seqra scan --output /output/results.sarif /project
```

**Volume mounts explained:**
- `/path/to/your/project:/project` - Mounts your Java project as read-only
- `/path/to/output:/output` - Mounts a directory for the SARIF output file

### Scan with Custom Ruleset

To use a custom ruleset, mount it as an additional volume:

```bash
docker run --rm \
  -v /path/to/your/project:/project \
  -v /path/to/output:/output \
  -v /path/to/rules:/rules \
  ghcr.io/seqra/seqra:latest \
  seqra scan --ruleset /rules/custom-rules.yaml --output /output/results.sarif /project
```

### Scan with Increased Memory

For large projects, increase the analyzer memory:

```bash
docker run --rm \
  -v /path/to/your/project:/project \
  -v /path/to/output:/output \
  ghcr.io/seqra/seqra:latest \
  seqra scan --max-memory 16G --output /output/results.sarif /project
```

### Scan with Configuration File

Mount a configuration file to customize behavior:

```bash
docker run --rm \
  -v /path/to/your/project:/project \
  -v /path/to/output:/output \
  -v /path/to/config.yaml:/config/seqra.yaml \
  ghcr.io/seqra/seqra:latest \
  seqra scan --config /config/seqra.yaml --output /output/results.sarif /project
```

### Two-Step Workflow: Compile and Scan

For more control over the analysis process, you can separate the compilation and scanning steps. This is useful when you want to:
- Reuse a compiled database for multiple scans with different rulesets
- Debug compilation issues separately from scanning
- Share compiled databases across team members

**Step 1: Compile the project**

```bash
docker run --rm \
  -v /path/to/your/project:/project \
  -v /path/to/database:/database \
  ghcr.io/seqra/seqra:latest \
  seqra compile --output /database /project
```

**Step 2: Scan the compiled database**

```bash
docker run --rm \
  -v /path/to/database:/database \
  -v /path/to/output:/output \
  ghcr.io/seqra/seqra:latest \
  seqra scan --output /output/results.sarif /database
```

## Viewing Results

After the scan completes, the SARIF file will be available in your output directory. View the summary from the command line:

```bash
docker run --rm \
  -v /path/to/output:/output \
  ghcr.io/seqra/seqra:latest \
  seqra summary --show-findings /output/results.sarif
```

For more ways to view and analyze results, see [View and Analyze Results](../README.md#view-and-analyze-results) in the main documentation.


## Image Details

- **Base:** Pre-configured with all Seqra dependencies
- **Working directory:** `/home/seqra`
- **Pre-installed components:**
  - Seqra CLI
  - Seqra autobuilder and analyzer JARs
  - Security rules
  - Java runtime (Temurin JDK)


## CI/CD Examples

### GitHub Actions

```yaml
jobs:
  security-scan:
    runs-on: ubuntu-latest
    container:
      image: ghcr.io/seqra/seqra:latest
    steps:
      - uses: actions/checkout@v4

      - name: Run Seqra scan
        run: seqra scan --output results.sarif .

      - name: Upload SARIF results
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: results.sarif
```

> **Note:** For a more streamlined GitHub integration, consider using the dedicated [seqra-action](https://github.com/seqra/seqra-action).

### GitLab CI

```yaml
seqra-scan:
  image: ghcr.io/seqra/seqra:latest
  script:
    - seqra scan --output results.sarif .
  artifacts:
    paths:
      - results.sarif
    reports:
      sast: results.sarif
```

> **Note:** For a more streamlined GitLab integration, see [seqra-gitlab](https://github.com/seqra/seqra-gitlab).


## Troubleshooting

### Permission Issues

If you encounter permission errors when writing output files, ensure the output directory exists and is writable:

```bash
mkdir -p /path/to/output
docker run --rm \
  -v /path/to/your/project:/project \
  -v /path/to/output:/output \
  ghcr.io/seqra/seqra:latest \
  seqra scan --output /output/results.sarif /project
```

### Verbose Logging

Enable debug logging to troubleshoot issues:

```bash
docker run --rm \
  -v /path/to/your/project:/project \
  -v /path/to/output:/output \
  ghcr.io/seqra/seqra:latest \
  seqra scan --verbosity debug --output /output/results.sarif /project
```

### View Available Commands

```bash
docker run --rm ghcr.io/seqra/seqra:latest seqra --help
docker run --rm ghcr.io/seqra/seqra:latest seqra scan --help
```
