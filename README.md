<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="logos/logo-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="logos/logo-light.svg">
    <img src="logos/logo-light.svg" alt="Opentaint" height="100">
  </picture>
</p>

<h1 align="center"> Security-focused static analyzer for Java and Kotlin web applications</h1>

<p align="center">
  Opentaint analyzes bytecode of Java and Kotlin web applications (with growing Spring support) using Semgrep-style YAML rules with CodeQL-grade dataflow to find vulnerabilities that source-only scanners miss.
</p>

<p align="center">
  <a href="https://github.com/seqra/opentaint/releases"><img src="https://img.shields.io/github/release/seqra/opentaint.svg" alt="GitHub release"></a>
  <a href="https://goreportcard.com/report/github.com/seqra/opentaint/v2"><img src="https://goreportcard.com/badge/github.com/seqra/opentaint/v2" alt="Go Report Card"></a>
  <a href="LICENSE.md"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License: Apache 2.0"></a>
  <a href="https://golang.org/"><img src="https://img.shields.io/badge/Go-1.25+-00ADD8?logo=go" alt="Go Version"></a>
  <a href="https://discord.gg/6BXDfbP4p9"><img src="https://img.shields.io/discord/1403357427176575036?logo=discord&label=Discord" alt="Discord"></a>
</p>

<p align="center"><b>Supported technologies and integrations</b></p>
<p align="center">
  <img src="logos/java-logo.svg" alt="Java" height="40">&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="logos/kotlin-logo.svg" alt="Kotlin" height="40">&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="logos/spring-boot-logo.svg" alt="Spring" height="40">&nbsp;&nbsp;&nbsp;&nbsp;
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="logos/github-logo-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="logos/github-logo-light.svg">
    <img src="logos/github-logo-light.svg" alt="GitHub" height="40">
  </picture>&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="logos/gitlab-logo.svg" alt="GitLab" height="40">
</p>

## Key Features

- **Spring-aware dataflow** — Models Spring annotations, persistence layer, and cross-controller paths. Catches stored injections where data written by one endpoint is exploited through another.

- **Java and Kotlin** — Analyzes compiled bytecode to precisely understand inheritance, generics, and library interactions and finds vulnerabilities that source-only scanners miss.

- **YAML rules** — Semgrep-style syntax, CodeQL-grade dataflow. Define security rules in readable YAML and get full inter-procedural taint analysis out of the box.

- **Source-available** — CLI is MIT licensed. Core engine uses FSL-1.1-ALv2, converting to Apache 2.0 two years after each release.


## Demo

https://github.com/user-attachments/assets/aba3733b-2959-4470-be0c-605d259e97b6

## Quick Start

**Install via Homebrew (Linux/macOS):**
```bash
brew install --cask seqra/tap/opentaint
```

**Install script (Windows PowerShell)**
```
irm https://raw.githubusercontent.com/seqra/opentaint/main/opentaint-cli/scripts/install/install.ps1 | iex
```

**Install script (Linux/macOS)**
```
curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/opentaint-cli/scripts/install/install.sh | bash
```

**Scan your project:**
```bash
opentaint scan --output results.sarif /path/to/your/spring/project
```

**Or use Docker:**
```bash
docker run --rm -v $(pwd):/project -v $(pwd):/output \
  ghcr.io/seqra/opentaint:latest \
  opentaint scan --output /output/results.sarif /project
```

For more options, see [Installation](#installation) and [Usage](#usage).

---

## What Opentaint Catches

Opentaint tracks data from controller parameters through your webb application to dangerous sinks.

**SQL Injection via JdbcTemplate**

```java
@GetMapping("/users/search")
public List<User> searchUsers(@RequestParam String name) {
    String sql = "SELECT * FROM users WHERE name = '" + name + "'";
    return jdbcTemplate.query(sql, userRowMapper);
}
```

Opentaint reports: `sql-injection-in-spring-app` at `GET /users/search` — untrusted input flows to SQL query.

**XSS in Controller Response**

```java
@GetMapping("/greet")
@ResponseBody
public String greet(@RequestParam String name) {
    return "<h1>Hello, " + name + "!</h1>";
}
```

Opentaint reports: `xss-in-spring-app` at `GET /greet` — user input returned without HTML escaping.

**SSRF via RestTemplate**

```java
@GetMapping("/fetch")
public String fetchUrl(@RequestParam String url) {
    return restTemplate.getForObject(url, String.class);
}
```

Opentaint reports: `ssrf-in-spring-app` at `GET /fetch` — user-controlled URL passed to HTTP client.

Each finding includes the HTTP endpoint, making it easy to understand your application's attack surface.

---

## Installation

| Method | Command |
|--------|---------|
| **Homebrew** (Linux/macOS) | `brew install --cask seqra/tap/opentaint` |
| **Install script** (Linux/macOS) | `curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/opentaint-cli/scripts/install/install.sh \| bash` |
| **Install script** (Windows PowerShell) | `irm https://raw.githubusercontent.com/seqra/opentaint/main/opentaint-cli/scripts/install/install.ps1 \| iex` |
| **Install script** (Windows CMD) | `curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/opentaint-cli/scripts/install/install.cmd -o install.cmd && install.cmd && del install.cmd` |
| **Docker** | See [Quick Start](#quick-start) or [Docker docs](docs/docker.md) |
| **Binary** | [Download from releases](https://github.com/seqra/opentaint/releases/latest) |
| **Go** | `go install github.com/seqra/opentaint/v2@latest` |

Release archives come in three variants: **`opentaint-full`** (binary + JARs + rules + JRE), **`opentaint`** (binary + JARs + rules), and **`opentaint-cli`** (binary only). Homebrew and install scripts default to `full`. For `go install`, run `opentaint pull` to download analyzer components.

For detailed instructions, see [Installation Guide](docs/installation.md).


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

For detailed usage, see [Usage Guide](docs/usage.md).

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

For detailed configuration, see [Configuration Guide](docs/configuration.md).


## CI/CD Integration

- **GitHub Actions:** [seqra/opentaint-action](https://github.com/seqra/opentaint-action)
- **GitLab CI:** [seqra/opentaint-gitlab](https://github.com/seqra/opentaint-gitlab)

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails | Ensure `mvn compile` or `gradle build` works; set `JAVA_HOME` |
| Out of memory | Use `--max-memory 16G` |
| Timeout | Use `--timeout 20m` |
| Re-download deps | `opentaint prune --yes && opentaint pull` |
| Debug | Use `--verbosity debug` |

For detailed troubleshooting, see [Troubleshooting Guide](docs/troubleshooting.md).

---

## Documentation

For comprehensive guides on all features, see the full [Documentation](docs/README.md).

---

## Support

- **Blog:** [opentaint.org/blog](https://opentaint.org/blog)
- **Issues:** [GitHub Issues](https://github.com/seqra/opentaint/issues)
- **Community:** [Discord](https://discord.gg/6BXDfbP4p9)
- **Email:** [seqradev@gmail.com](mailto:seqradev@gmail.com)
- **FAQ:** [FAQ](docs/faq.md)


## License

Opentaint is released under the [Apache 2.0 License](LICENSE.md).
