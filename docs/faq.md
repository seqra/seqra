## FAQ

**What languages are supported?**

Java and Kotlin are supported. Broader language support is planned.

**How does Opentaint compare to Semgrep?**

Opentaint interprets Semgrep-style rules using **interprocedural data-flow analysis**, going beyond simple pattern matching to track data across function calls and identify complex vulnerabilities that pure pattern matching would miss.

**How does Opentaint compare to CodeQL?**

You get **CodeQL-like findings** without the learning curve of a specialized query language—rules are written with familiar code patterns.

**Is Opentaint free to use?**

Yes! You can use Opentaint for free, including on private repos and for internal commercial use (excluding competing uses). The analyzer code is source-available under the **Functional Source License (FSL-1.1-ALv2)**, which converts to Apache 2.0 after two years from the release date.

**Can I use existing Semgrep rules?**

Yes! Opentaint is compatible with Semgrep rule syntax, plus adds dataflow analysis capabilities.

**How do I integrate Opentaint into my development workflow?**

Opentaint can be integrated into your CI/CD pipeline using our [GitHub Action](https://github.com/seqra/opentaint-action) or [GitLab CI template](https://github.com/seqra/opentaint-gitlab). It also supports local development with SARIF output for your favorite IDE.
