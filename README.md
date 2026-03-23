<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="logos/opentaint-logo-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="logos/opentaint-logo-light.svg">
    <img src="logos/opentaint-logo-light.svg" alt="OpenTaint" height="100">
  </picture>
</p>

<h3 align="center">The open source taint analysis engine for the AI era</h3>

<p align="center">
  Enterprise-grade dataflow analysis with code-native rules — no paywall, no pattern-matching compromises.
</p>

<p align="center">
  <a href="https://github.com/seqra/opentaint/releases"><img src="https://img.shields.io/github/release/seqra/opentaint.svg" alt="GitHub release"></a>
  <a href="https://goreportcard.com/report/github.com/seqra/opentaint/cli"><img src="https://goreportcard.com/badge/github.com/seqra/opentaint/cli" alt="Go Report Card"></a>
  <a href="LICENSE.md"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License: Apache 2.0"></a>
  <a href="https://golang.org/"><img src="https://img.shields.io/badge/Go-1.25+-00ADD8?logo=go" alt="Go Version"></a>
  <a href="https://discord.gg/6BXDfbP4p9"><img src="https://img.shields.io/discord/1403357427176575036?logo=discord&label=Discord" alt="Discord"></a>
</p>

<p align="center">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="public/opentaint-frame-light-2.png">
  <source media="(prefers-color-scheme: light)" srcset="public/opentaint-frame-dark-2.png">
  <img src="public/opentaint-frame-dark-2.png" alt="OpenTaint summary output" width="720">
</picture>

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

<p align="center"><b>Roadmap</b></p>
<p align="center">
  <img src="logos/python-logo.svg" alt="Python" height="40">&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="logos/go-logo.svg" alt="Go" height="40">&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="logos/csharp-logo.svg" alt="C#" height="40">&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="logos/javascript-logo.svg" alt="JavaScript" height="40">&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="logos/typescript-logo.svg" alt="TypeScript" height="40">
</p>

<div align="center">
<details>
  <summary><b>More screenshots</b></summary>
  <p align="center">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="public/opentaint-frame-light-1.png">
      <source media="(prefers-color-scheme: light)" srcset="public/opentaint-frame-dark-1.png">
      <img src="public/opentaint-frame-dark-1.png" alt="OpenTaint scan output" width="720">
    </picture>
  </p>
  </p>  <p align="center">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="public/opentaint-frame-light-3.png">
      <source media="(prefers-color-scheme: light)" srcset="public/opentaint-frame-dark-3.png">
      <img src="public/opentaint-frame-dark-3.png" alt="OpenTaint summary output" width="720">
    </picture>
  </p>
  </p>  <p align="center">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="public/opentaint-frame-light-4.png">
      <source media="(prefers-color-scheme: light)" srcset="public/opentaint-frame-dark-4.png">
      <img src="public/opentaint-frame-dark-4.png" alt="OpenTaint summary output" width="720">
    </picture>
  </p>
  </p>  <p align="center">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="public/opentaint-frame-light-5.png">
      <source media="(prefers-color-scheme: light)" srcset="public/opentaint-frame-dark-5.png">
      <img src="public/opentaint-frame-dark-5.png" alt="OpenTaint summary output" width="720">
    </picture>
  </p>
</details>
</div>

---

## Why OpenTaint

- **AI agent-ready.** Agents operate the rules, the CLI, the output. Scan code, triage findings, fix vulnerabilities, refine rules.
- **Cutting-edge dataflow analysis.** Inter-procedural taint tracking across endpoints, persistence layers, aliases, and async code.
- **Enterprise-grade, finds real trophies.** Powerful, precise, and performant at scale. Catches exploitable vulnerabilities.
- **Rules that read like code.** Write and refine taint rules the same way you write application code — or let your AI agent do it.
- **Open source, batteries included.** Engine, CLI, GitHub Action, GitLab CI, rules. Apache 2.0 and MIT licensed.

## Quick Start

**Install via Homebrew (Linux/macOS):**
```bash
brew install --cask seqra/tap/opentaint
```

**Install script (Windows PowerShell)**
```
irm https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.ps1 | iex
```

**Install script (Linux/macOS)**
```
curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.sh | bash
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

## About OpenTaint

AI-generated code is scaling codebases fast. Pattern matchers produce too many false positives. Enterprise taint analyzers that work are paywalled. AI agents in a security role give no formal guarantees.

OpenTaint does real inter-procedural taint analysis. IFDS-with-abduction engine. Tracks untrusted data from HTTP inputs to dangerous APIs — across endpoints, persistence layers, object fields, aliased references, async code. Models Spring data flow, the full Boot ecosystem. Java and Kotlin at bytecode level. More languages ahead.

Enterprise-grade. Powerful, precise, performant at scale. Handles large monorepo codebases. Tracks complex multi-hop attack paths — cross-endpoint flows, data through persistence layers, stored injections.

Rules look like code. Humans and AI agents read, write, and tune them — no proprietary DSL. The engine translates rules into full taint configurations: sources, sinks, sanitizers, propagators, taint marks.

Fully open source. CLI, GitHub Action, GitLab CI, rules — all included. [Apache 2.0](LICENSE.md) and [MIT](cli/LICENSE) licensed.

---

## What OpenTaint Catches

OpenTaint tracks data from controller parameters through your webb application to dangerous sinks.

**SQL Injection via JdbcTemplate**

```java
@GetMapping("/users/search")
public List<User> searchUsers(@RequestParam String name) {
    String sql = "SELECT * FROM users WHERE name = '" + name + "'";
    return jdbcTemplate.query(sql, userRowMapper);
}
```

OpenTaint reports: `sql-injection-in-spring-app` at `GET /users/search` — untrusted input flows to SQL query.

**XSS in Controller Response**

```java
@GetMapping("/greet")
@ResponseBody
public String greet(@RequestParam String name) {
    return "<h1>Hello, " + name + "!</h1>";
}
```

OpenTaint reports: `xss-in-spring-app` at `GET /greet` — user input returned without HTML escaping.

**SSRF via RestTemplate**

```java
@GetMapping("/fetch")
public String fetchUrl(@RequestParam String url) {
    return restTemplate.getForObject(url, String.class);
}
```

OpenTaint reports: `ssrf-in-spring-app` at `GET /fetch` — user-controlled URL passed to HTTP client.

Each finding includes the HTTP endpoint, making it easy to understand your application's attack surface.

---

## Installation

| Method | Command |
|--------|---------|
| **Homebrew** (Linux/macOS) | `brew install --cask seqra/tap/opentaint` |
| **Install script** (Linux/macOS) | `curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.sh \| bash` |
| **Install script** (Windows PowerShell) | `irm https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.ps1 \| iex` |
| **Install script** (Windows CMD) | `curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.cmd -o install.cmd && install.cmd && del install.cmd` |
| **Docker** | See [Quick Start](#quick-start) or [Docker docs](docs/docker.md) |
| **Binary** | [Download from releases](https://github.com/seqra/opentaint/releases/latest) |

Release archives come in three variants: **`opentaint-full`** (binary + JARs + rules + JRE), **`opentaint`** (binary + JARs + rules), and **`opentaint-cli`** (binary only). Homebrew and install scripts default to `full`.

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

For detailed troubleshooting, see [Troubleshooting Guide](docs/troubleshooting.md).

---

## Documentation

For comprehensive guides on all features, see the full [Documentation](docs/README.md).

---

## Support

- **Issues:** [GitHub Issues](https://github.com/seqra/opentaint/issues)
- **Community:** [Discord](https://discord.gg/6BXDfbP4p9)
- **Email:** [seqradev@gmail.com](mailto:seqradev@gmail.com)
- **FAQ:** [FAQ](docs/faq.md)


## License

The [core analysis engine](core/) is released under the [Apache 2.0 License](LICENSE.md). The [CLI](cli/), [GitHub Action](github/), [GitLab CI template](gitlab/), and [rules](rules/) are released under the [MIT License](cli/LICENSE).
