# Documentation

## Contents

- [About OpenTaint](#about-opentaint)
- [What OpenTaint Catches](#what-opentaint-catches)
- [Installation](#installation)
- [Usage](#usage)
- [Configuration](#configuration)
- [CI/CD Integration](#cicd-integration)
- [Troubleshooting](#troubleshooting)
- [Support](#support)
- [License](#license)

## Guides

- [Installation Guide](installation.md) - Full installation instructions
- [Usage Guide](usage.md) - Comprehensive usage reference
- [Configuration Guide](configuration.md) - All configuration options
- [Docker](docker.md) - Run OpenTaint in containers and CI/CD pipelines
- [Precompiled Classes and JARs Analysis](classes-and-jars-analysis.md) - Analyze pre-built artifacts when source compilation isn't available
- [Spring Boot Endpoint Extraction](spring-boot-endpoints.md) - Automatic HTTP endpoint mapping for Spring applications
- [Troubleshooting Guide](troubleshooting.md) - Detailed troubleshooting
- [FAQ](faq.md) - Frequently asked questions

---

## About OpenTaint

AI-generated code is scaling codebases fast. Pattern matching scanners can't keep up — they produce too many false positives. Enterprise taint analyzers that actually work are paywalled. And AI agents, while great at writing code and reviewing it for security, still give no formal guarantees.

### Real taint analysis, not just pattern matching

OpenTaint runs an IFDS-with-abduction engine — formal inter-procedural dataflow analysis. It tracks untrusted data from HTTP inputs to dangerous APIs across endpoints, persistence layers, object fields, aliased references, and async code. That includes complex multi-hop attack paths — cross-endpoint flows, data through persistence layers, stored injections — at monorepo scale.

Currently models Spring data flow and the full Boot ecosystem, analyzing Java and Kotlin at bytecode level. More languages ahead.

### Deterministic analysis underneath AI

Because the analysis is formal, its results are reproducible — agents can operate on them without adding uncertainty. Rules read like code, not a proprietary DSL, so both humans and agents write and tune them the same way. The engine translates those rules into full taint configurations: sources, sinks, sanitizers, propagators, taint marks.

### Open source, not paywalled

Engine, CLI, GitHub Action, GitLab CI, rules — all included under [Apache 2.0](../LICENSE.md) and [MIT](../cli/LICENSE).

---

## What OpenTaint Catches

OpenTaint tracks data from controller parameters through your web application to dangerous sinks.

Consider a search endpoint that concatenates user input into SQL:

```java
// UserController.java
@GetMapping("/users/search")
public List<User> searchUsers(@RequestParam String name) {
    String sql = "SELECT * FROM users WHERE name = '" + name + "'";
    return jdbcTemplate.query(sql, userRowMapper);
}
```

OpenTaint reports: `sql-injection-in-spring-app` at `GET /users/search` — untrusted input flows to SQL query.

Consider a greeting endpoint that reflects user input without escaping:

```java
// GreetingController.java
@GetMapping("/greet")
@ResponseBody
public String greet(@RequestParam String name) {
    return "<h1>Hello, " + name + "!</h1>";
}
```

OpenTaint reports: `xss-in-spring-app` at `GET /greet` — user input returned without HTML escaping.

Consider a proxy endpoint that passes a user-controlled URL directly to an HTTP client:

```java
// ProxyController.java
@GetMapping("/fetch")
public String fetchUrl(@RequestParam String url) {
    return restTemplate.getForObject(url, String.class);
}
```

OpenTaint reports: `ssrf-in-spring-app` at `GET /fetch` — user-controlled URL passed to HTTP client.

Each finding includes the HTTP endpoint, making it easy to map your application's attack surface.

---

## Installation

| Method | Command |
|--------|---------|
| **Homebrew** (Linux/macOS) | `brew install --cask seqra/tap/opentaint` |
| **Install script** (Linux/macOS) | `curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.sh \| bash` |
| **Install script** (Windows PowerShell) | `irm https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.ps1 \| iex` |
| **Install script** (Windows CMD) | `curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.cmd -o install.cmd && install.cmd && del install.cmd` |
| **Docker** | See [Docker docs](docker.md) |
| **Binary** | [Download from releases](https://github.com/seqra/opentaint/releases/latest) |

Release archives come in three variants: **`opentaint-full`** (binary + JARs + rules + JRE), **`opentaint`** (binary + JARs + rules), and **`opentaint-cli`** (binary only). Homebrew and install scripts default to `full`.

For detailed instructions, see [Installation Guide](installation.md).

---

## Usage

```bash
opentaint scan --output results.sarif /path/to/project    # Scan project
opentaint summary --show-findings results.sarif           # View results
opentaint summary --show-findings --verbose-flow --show-code-snippets results.sarif  # Full detail
```

| Command | Description |
|---------|-------------|
| `opentaint scan` | Analyze projects (auto-detects Maven/Gradle) |
| `opentaint compile` | Build project model separately |
| `opentaint project` | Create model from precompiled JARs |
| `opentaint summary` | View SARIF results |
| `opentaint pull` | Download dependencies |
| `opentaint update` | Update to latest version |
| `opentaint prune` | Remove stale downloaded artifacts |

**Options:** `--max-memory 16G`, `--timeout 5m`, `--severity error`, `--config config.yaml`

For detailed usage, see [Usage Guide](usage.md).

---

## Configuration

```yaml
scan:
  timeout: 15m
  max_memory: 16G
log:
  verbosity: info  # info, debug
  color: auto      # auto, always, never
```

Or use environment variables: `OPENTAINT_SCAN_TIMEOUT=30m`, `OPENTAINT_SCAN_MAX_MEMORY=16G`

For detailed configuration, see [Configuration Guide](configuration.md).

---

## CI/CD Integration

- **GitHub Actions:** [seqra/opentaint/github](https://github.com/seqra/opentaint/tree/main/github)
- **GitLab CI:** [seqra/opentaint/gitlab](https://github.com/seqra/opentaint/tree/main/gitlab)

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails | Ensure `mvn compile` or `gradle build` works; set `JAVA_HOME` |
| Out of memory | Use `--max-memory 16G` |
| Timeout | Use `--timeout 20m` |
| Re-download deps | `opentaint prune --yes && opentaint pull` |
| Debug | Use `--verbosity debug` |

For detailed troubleshooting, see [Troubleshooting Guide](troubleshooting.md).

---

## Support

- **Issues:** [GitHub Issues](https://github.com/seqra/opentaint/issues)
- **Community:** [Discord](https://discord.gg/6BXDfbP4p9)
- **Email:** [seqradev@gmail.com](mailto:seqradev@gmail.com)
- **FAQ:** [FAQ](faq.md)

---

## License

The [core analysis engine](../core/) is released under the [Apache 2.0 License](../LICENSE.md). The [CLI](../cli/), [GitHub Action](../github/), [GitLab CI template](../gitlab/), and [rules](../rules/) are released under the [MIT License](../cli/LICENSE).
