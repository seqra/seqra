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

AI generates production code faster than today's security tooling can keep up with. The code looks production-ready — yet it buries vulnerabilities in data flows that are fundamentally hard to catch. These include untrusted input winding through framework abstractions, cross-controller interactions with persistence layers, and async code. At the rate AI produces it, humans can't review this code at the depth it requires.

The tools meant to help aren't keeping up either — pattern matching engines catch surface-level issues but struggle to follow data flow across function and file boundaries, LLM agents burn tokens on every file and still produce inconsistent results, and enterprise analyzers that go further gate their analysis behind a paywall, with rule sets that rarely cover your stack.

The more AI writes code, the more you need formal methods underneath.

### Find what pattern matching engines miss

The engine runs IFDS-with-abduction — formal inter-procedural dataflow analysis. It tracks untrusted data from HTTP inputs to dangerous APIs across endpoints, persistence layers, object fields, aliased references, and async code. That includes multi-hop attack paths — cross-endpoint flows, stored injections, data through object fields and aliases — at monorepo scale. 100+ rules across 20+ vulnerability classes.

Models Spring data flow and the full Boot ecosystem, analyzing Java and Kotlin at bytecode level. More languages and frameworks ahead.

### One finding becomes total coverage

LLM security agents find things — but at token cost per file, with results that shift each run, and no guarantee of complete coverage. Code-native rules turn their findings into leverage. Every vulnerability an agent uncovers can be enacted as a rule — a source, a sink, and the data flow between them — which the agent can write itself. The engine applies that rule across the entire codebase, deterministically, in minutes of CPU. When a finding is a false positive, a sanitizer can be added to the rule — the refinement propagates to every match, permanently. One discovery compounds across the entire codebase.

The entire system is designed to work with AI agents. Formal analysis produces reproducible results agents can act on without introducing uncertainty. Rules read like code, not a proprietary DSL — so agents write and tune them the same way humans do.

### Open source, batteries included


Engine, CLI, GitHub Action, GitLab CI, rules — the entire stack, including the deep analysis, is released under [Apache 2.0](../LICENSE.md) and [MIT](../cli/LICENSE). No paid tier to unlock taint tracking. No vendor lock-in on your rule library. Other tools make you pay for it — Semgrep gates inter-procedural taint tracking behind a paid Pro tier, CodeQL requires GHAS for private repos. OpenTaint doesn't.

---

## What OpenTaint Catches

The engine tracks data from controller parameters through your web application to dangerous sinks.

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
opentaint scan                                            # Scan current directory
opentaint scan --output results.sarif                     # Scan with explicit output path
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
| `opentaint prune` | Remove stale artifacts and cached models |

**Options:** `--max-memory 16G`, `--timeout 5m`, `--severity error`, `--recompile`, `--config config.yaml`

For detailed usage, see [Usage Guide](usage.md).

---

## Configuration

```yaml
scan:
  timeout: 15m
  max_memory: 16G
output:
  debug: false   # true to enable debug output
  color: auto    # auto, always, never
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
| Stale cached model | Use `--recompile` to force recompilation |
| Debug | Use `--debug` |

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
