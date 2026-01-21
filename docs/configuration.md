# Configuration

Seqra can be configured through a configuration file or environment variables. Command-line flags always take precedence over configuration file settings and environment variables.

## Configuration File

Create a YAML configuration file and specify it with the `--config` flag:

```bash
seqra scan --config /path/to/config.yaml /path/to/project
```

### Example Configuration

```yaml
# Scan settings
scan:
  timeout: 15m
  max_memory: 16G

# Logging
log:
  verbosity: info  # debug, info, warn, error, fatal, panic

# Java runtime settings
java:
  version: 23

# Suppress interactive output
quiet: false
```

### Available Options

| Setting | Description | Default |
|---------|-------------|---------|
| `scan.timeout` | Analysis timeout duration | `15m` |
| `scan.max_memory` | Maximum memory for analyzer (e.g., `8G`, `1024m`) | `8G` |
| `log.verbosity` | Log level: debug, info, warn, error, fatal, panic | `info` |
| `java.version` | Java version for running the analyzer | `23` |
| `quiet` | Suppress interactive console output | `false` |

## Environment Variables

All configuration options can also be set via environment variables with the `SEQRA_` prefix. Use underscores to separate nested keys:

```bash
export SEQRA_SCAN_TIMEOUT=30m
export SEQRA_SCAN_MAX_MEMORY=16G
export SEQRA_LOG_VERBOSITY=debug
export SEQRA_JAVA_VERSION=23

seqra scan --output results.sarif /path/to/project
```

## Priority Order

Configuration values are resolved in this order (highest to lowest priority):

1. Command-line flags
2. Environment variables
3. Configuration file
4. Default values

## Persistent Configuration

For persistent settings, create a configuration file at `~/.seqra/config.yaml`:

```yaml
scan:
  max_memory: 16G
  timeout: 20m
```

Then use it with every scan:

```bash
seqra scan --config ~/.seqra/config.yaml --output results.sarif /path/to/project
```
