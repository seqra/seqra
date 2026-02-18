# Troubleshooting

## Build Issues

> **Note:** Only Maven and Gradle projects are supported for automatic compilation.

### Project Doesn't Compile

- Check that all project dependencies are available in your local environment
- Ensure your `JAVA_HOME` is set to a compatible Java version for your project
- Verify your project builds successfully with `mvn compile` or `gradle build`

### Missing Dependencies or Complex Build

For projects with complex build configurations, use precompiled JARs instead:

```bash
# Create a project model from compiled artifacts
opentaint project --output ./project-model --source-root /path/to/source \
  --classpath target/myapp.jar --package com.example

# Scan the project model
opentaint scan --output results.sarif ./project-model
```

## Dependency Download Issues

### Re-downloading Dependencies

To force re-download of dependencies while keeping your configuration:

```bash
opentaint prune --yes
opentaint pull
```

### Download Location

Downloaded dependencies are stored in `~/.opentaint/install/`:
- `~/.opentaint/install/lib/opentaint-project-analyzer.jar` — Security analyzer
- `~/.opentaint/install/lib/opentaint-project-auto-builder.jar` — Project compilation tools
- `~/.opentaint/install/lib/rules/` — Security rules
- `~/.opentaint/install/jre/` — Java runtime

For bundled installations (Homebrew, `opentaint-full` archives), artifacts are stored next to the binary. The `~/.opentaint/` root directory is used as a fallback cache with versioned filenames (e.g. `~/.opentaint/analyzer_<version>.jar`).

## Java Runtime Issues

### Wrong Java Version for Your Project

If compilation fails due to Java version mismatch, set `JAVA_HOME` to match your project's requirements:

```bash
export JAVA_HOME=/path/to/java-17
opentaint scan --output results.sarif /path/to/project
```

## Memory and Performance

### Out of Memory During Analysis

For large projects, increase the analyzer memory:

```bash
opentaint scan --max-memory 16G --output results.sarif /path/to/project
```

### Analysis Timeout

For complex projects that take longer to analyze:

```bash
opentaint scan --timeout 20m --output results.sarif /path/to/project
```

### Persistent Memory Settings

Add to your configuration file (`~/.opentaint/config.yaml`):

```yaml
scan:
  max_memory: 16G
  timeout: 20m
```

## Logs and Debugging

### Enable Verbose Logging

```bash
opentaint scan --verbosity debug --output results.sarif /path/to/project
```

### Common Log Locations

Temporary directory is shown in output during scan execution.
