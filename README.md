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
  <p align="center">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="public/opentaint-frame-light-3.png">
      <source media="(prefers-color-scheme: light)" srcset="public/opentaint-frame-dark-3.png">
      <img src="public/opentaint-frame-dark-3.png" alt="OpenTaint summary output" width="720">
    </picture>
  </p>
  <p align="center">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="public/opentaint-frame-light-4.png">
      <source media="(prefers-color-scheme: light)" srcset="public/opentaint-frame-dark-4.png">
      <img src="public/opentaint-frame-dark-4.png" alt="OpenTaint summary output" width="720">
    </picture>
  </p>
  <p align="center">
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

AI-generated code is scaling codebases fast. Pattern matchers can't keep up — too many false positives, too many missed vulnerabilities. Enterprise taint analyzers that actually work are paywalled.

- **Finds what pattern matchers miss.** Inter-procedural dataflow engine tracks untrusted data across function boundaries, persistence layers, aliases, and async code — 100+ rules across 20+ vulnerability classes, out of the box.
- **Deterministic by design.** Formal analysis produces the same results every run. CI pipelines and AI agents get reproducible findings they can act on — no flaky scans, no hallucinated vulnerabilities.
- **Rules that read like code.** Taint rules use Semgrep-compatible YAML with real code patterns — no proprietary DSL to learn.
- **Open source, batteries included.** Engine, CLI, GitHub Action, GitLab CI, rules — all included. Apache 2.0 and MIT licensed.

## Quick Start

**Install script (Linux/macOS)**
```
curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.sh | bash
```

**Install via Homebrew (Linux/macOS):**
```bash
brew install --cask seqra/tap/opentaint
```

**Install script (Windows PowerShell)**
```
irm https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.ps1 | iex
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

For more options, see [Installation](docs/README.md#installation) and [Usage](docs/README.md#usage).

---

## Documentation

For detailed guides on installation, usage, configuration, CI/CD integration, and more — see the **[Documentation](docs/README.md)**.

## Support

- **Issues:** [GitHub Issues](https://github.com/seqra/opentaint/issues)
- **Community:** [Discord](https://discord.gg/6BXDfbP4p9)
- **Email:** [seqradev@gmail.com](mailto:seqradev@gmail.com)

## License

The [core analysis engine](core/) is released under the [Apache 2.0 License](LICENSE.md). The [CLI](cli/), [GitHub Action](github/), [GitLab CI template](gitlab/), and [rules](rules/) are released under the [MIT License](cli/LICENSE).
