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
seqra project --output ./project-model --source-root /path/to/source \
  --classpath target/myapp.jar --package com.example

# Scan the project model
seqra scan --output results.sarif ./project-model
```

## Dependency Download Issues

### Re-downloading Dependencies

To force re-download of dependencies while keeping your configuration:

```bash
seqra prune --yes
seqra pull
```

### Download Location

Downloaded dependencies are stored in `~/.seqra/install/`:
- `~/.seqra/install/lib/seqra-project-analyzer.jar` — Security analyzer
- `~/.seqra/install/lib/seqra-project-auto-builder.jar` — Project compilation tools
- `~/.seqra/install/lib/rules/` — Security rules
- `~/.seqra/install/jre/` — Java runtime

For bundled installations (Homebrew, `seqra-full` archives), artifacts are stored next to the binary. The `~/.seqra/` root directory is used as a fallback cache with versioned filenames (e.g. `~/.seqra/analyzer_<version>.jar`).

## Java Runtime Issues

### Wrong Java Version for Your Project

If compilation fails due to Java version mismatch, set `JAVA_HOME` to match your project's requirements:

```bash
export JAVA_HOME=/path/to/java-17
seqra scan --output results.sarif /path/to/project
```

## Memory and Performance

### Out of Memory During Analysis

For large projects, increase the analyzer memory:

```bash
seqra scan --max-memory 16G --output results.sarif /path/to/project
```

### Analysis Timeout

For complex projects that take longer to analyze:

```bash
seqra scan --timeout 20m --output results.sarif /path/to/project
```

### Persistent Memory Settings

Add to your configuration file (`~/.seqra/config.yaml`):

```yaml
scan:
  max_memory: 16G
  timeout: 20m
```

## Logs and Debugging

### Enable Verbose Logging

```bash
seqra scan --verbosity debug --output results.sarif /path/to/project
```

### Common Log Locations

Temporary directory is shown in output during scan execution.
