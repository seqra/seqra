<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="logos/logo-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="logos/logo-light.svg">
    <img src="logos/logo-light.svg" alt="Seqra" height="100">
  </picture>
</p>

<h1 align="center"> Security-focused static analyzer for Java and Kotlin web applications</h1>

<p align="center">
  Seqra analyzes bytecode of Java and Kotlin web applications (with first-class Spring support) using Semgrep-style YAML rules with CodeQL-grade dataflow to find vulnerabilities that source-only scanners miss.
</p>

<p align="center">
  <a href="https://github.com/seqra/seqra/releases"><img src="https://img.shields.io/github/release/seqra/seqra.svg" alt="GitHub release"></a>
  <a href="https://goreportcard.com/report/github.com/seqra/seqra/v2"><img src="https://goreportcard.com/badge/github.com/seqra/seqra/v2" alt="Go Report Card"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
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

**Install via Go:**
```bash
go install github.com/seqra/seqra/v2@latest
```

**Scan your project:**
```bash
seqra scan --output results.sarif /path/to/your/spring/project
```

**Or use Docker:**
```bash
docker run --rm -v $(pwd):/project -v $(pwd):/output \
  ghcr.io/seqra/seqra:latest \
  seqra scan --output /output/results.sarif /project
```

For more options, see [Installation](#installation) and [Usage](#usage).

---

## What Seqra Catches

Seqra tracks data from controller parameters through your webb application to dangerous sinks.

**SQL Injection via JdbcTemplate**

```java
@GetMapping("/users/search")
public List<User> searchUsers(@RequestParam String name) {
    String sql = "SELECT * FROM users WHERE name = '" + name + "'";
    return jdbcTemplate.query(sql, userRowMapper);
}
```

Seqra reports: `sql-injection-in-spring-app` at `GET /users/search` — untrusted input flows to SQL query.

**XSS in Controller Response**

```java
@GetMapping("/greet")
@ResponseBody
public String greet(@RequestParam String name) {
    return "<h1>Hello, " + name + "!</h1>";
}
```

Seqra reports: `xss-in-spring-app` at `GET /greet` — user input returned without HTML escaping.

**SSRF via RestTemplate**

```java
@GetMapping("/fetch")
public String fetchUrl(@RequestParam String url) {
    return restTemplate.getForObject(url, String.class);
}
```

Seqra reports: `ssrf-in-spring-app` at `GET /fetch` — user-controlled URL passed to HTTP client.

Each finding includes the HTTP endpoint, making it easy to understand your application's attack surface.

---

## Installation

| Method | Command |
|--------|---------|
| **Go** (recommended) | `go install github.com/seqra/seqra/v2@latest` |
| **Docker** | See [Quick Start](#quick-start) or [Docker docs](docs/docker.md) |
| **Binary** | [Download from releases](https://github.com/seqra/seqra/releases/latest) |

After installation, run `seqra pull` to download analyzer components (or let `seqra scan` download them automatically).

For detailed instructions, see [Installation Guide](docs/installation.md).


## Usage

```bash
seqra scan --output results.sarif /path/to/project    # Scan project
seqra summary --show-findings results.sarif           # View results
```

| Command | Description |
|---------|-------------|
| `seqra scan` | Analyze projects (auto-detects Maven/Gradle) |
| `seqra compile` | Build project model separately |
| `seqra project` | Create model from precompiled JARs |
| `seqra summary` | View SARIF results |
| `seqra pull` | Download dependencies |

**Options:** `--max-memory 16G`, `--timeout 5m`, `--severity error`, `--config config.yaml`

For detailed usage, see [Usage Guide](docs/usage.md).

---

## Configuration

```yaml
scan:
  timeout: 15m
  max_memory: 16G
log:
  verbosity: info  # debug, info, warn, error
```

Or use environment variables: `SEQRA_SCAN_TIMEOUT=30m`, `SEQRA_SCAN_MAX_MEMORY=16G`

For detailed configuration, see [Configuration Guide](docs/configuration.md).


## CI/CD Integration

- **GitHub Actions:** [seqra/seqra-action](https://github.com/seqra/seqra-action)
- **GitLab CI:** [seqra/seqra-gitlab](https://github.com/seqra/seqra-gitlab)

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails | Ensure `mvn compile` or `gradle build` works; set `JAVA_HOME` |
| Out of memory | Use `--max-memory 16G` |
| Timeout | Use `--timeout 20m` |
| Re-download deps | `rm -rf ~/.seqra/autobuilder ~/.seqra/analyzer ~/.seqra/rules ~/.seqra/jdk && seqra pull` |
| Debug | Use `--verbosity debug` |

For detailed troubleshooting, see [Troubleshooting Guide](docs/troubleshooting.md).

---

## Documentation

For comprehensive guides on all features, see the full [Documentation](docs/README.md).

---

## Support

- **Blog:** [seqra.dev/blog](https://seqra.dev/blog)
- **Issues:** [GitHub Issues](https://github.com/seqra/seqra/issues)
- **Community:** [Discord](https://discord.gg/6BXDfbP4p9)
- **Email:** [seqradev@gmail.com](mailto:seqradev@gmail.com)
- **FAQ:** [FAQ](docs/faq.md)


## License

This CLI is released under the [MIT License](LICENSE).

The [core analysis engine](https://github.com/seqra/seqra-jvm-sast) is source-available under the [Functional Source License (FSL-1.1-ALv2)](https://fsl.software/), which converts to Apache 2.0 two years after each release. You can use Seqra for free, including for commercial use, except for competing products or services.
