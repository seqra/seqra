# Configuration

OpenTaint can be configured through a configuration file or environment variables. Command-line flags always take precedence over configuration file settings and environment variables.

## Configuration File

Create a YAML configuration file and specify it with the `--config` flag:

```bash
opentaint scan --config /path/to/config.yaml /path/to/project
```

### Example Configuration

```yaml
# Scan settings
scan:
  timeout: 15m
  max_memory: 16G

# Output (terminal-side controls)
output:
  debug: false   # true streams JAR output to stderr and shows debug-only fields
  color: auto    # auto, always, never
  quiet: false   # suppress spinners, progress bars, JAR streaming

# Java runtime settings
java:
  version: 23
```

### Available Options

| Setting | Description | Default |
|---------|-------------|---------|
| `scan.timeout` | Analysis timeout duration | `15m` |
| `scan.max_memory` | Maximum memory for analyzer (e.g., `8G`, `1024m`) | `8G` |
| `output.debug` | Enable debug output (stream JAR subprocess output, show debug fields) | `false` |
| `output.color` | Color mode: `auto`, `always`, `never` | `auto` |
| `output.quiet` | Suppress interactive console output (spinners, progress bars, JAR streaming) | `false` |
| `java.version` | Java version for running the analyzer | `23` |

The per-run log file (`~/.opentaint/logs/<project>/<timestamp>.log`) always
captures full JAR subprocess output regardless of these flags. They control
only what is shown on the terminal.

## Environment Variables

All configuration options can also be set via environment variables with the `OPENTAINT_` prefix. Use underscores to separate nested keys:

```bash
export OPENTAINT_SCAN_TIMEOUT=30m
export OPENTAINT_SCAN_MAX_MEMORY=16G
export OPENTAINT_OUTPUT_DEBUG=true
export OPENTAINT_OUTPUT_COLOR=always
export OPENTAINT_OUTPUT_QUIET=false
export OPENTAINT_JAVA_VERSION=23

opentaint scan /path/to/project
```

## Priority Order

Configuration values are resolved in this order (highest to lowest priority):

1. Command-line flags
2. Environment variables
3. Configuration file
4. Default values

## Persistent Configuration

For persistent settings, create a configuration file at `~/.opentaint/config.yaml`:

```yaml
scan:
  max_memory: 16G
  timeout: 20m
```

Then use it with every scan:

```bash
opentaint scan --config ~/.opentaint/config.yaml /path/to/project
```
