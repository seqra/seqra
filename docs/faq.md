## FAQ

**Why is OpenTaint a great choice for Spring applications?**

OpenTaint is purpose-built for Spring. It understands Spring annotations (`@GetMapping`, `@PostMapping`, etc.), Spring data access patterns (JdbcTemplate, JPA, Spring Data), and Spring-specific sanitizers like `HtmlUtils.htmlEscape()`. Every finding is automatically mapped to its HTTP endpoint, so you know exactly which APIs are affected.

**What Spring vulnerabilities does OpenTaint detect?**

OpenTaint detects 20+ vulnerability types including SQL injection (via JdbcTemplate, JPA, Hibernate), XSS in controllers, SSRF via RestTemplate/WebClient, SpEL injection, open redirects, path traversal, command injection, and more. Each rule includes Spring-specific detection patterns and remediation guidance.

**What languages are supported?**

Java and Kotlin are supported, with full support for Spring Boot, Spring MVC, and plain Java/Kotlin projects. Broader language support is planned.

**How does OpenTaint compare to Semgrep?**

OpenTaint interprets Semgrep-style rules using **interprocedural data-flow analysis**, going beyond simple pattern matching to track data across function calls and identify complex vulnerabilities that pure pattern matching would miss.

**How does OpenTaint compare to CodeQL?**

You get **CodeQL-like findings** without the learning curve of a specialized query language—rules are written with familiar code patterns.

**Is OpenTaint free to use?**

Yes! OpenTaint is free and open source. The core engine is licensed under [Apache 2.0](../LICENSE.md), and the CLI, CI integrations, and rules are licensed under [MIT](../cli/LICENSE).

**Can I use existing Semgrep rules?**

Yes! OpenTaint is compatible with Semgrep rule syntax, plus adds dataflow analysis capabilities.

**How do I integrate OpenTaint into my development workflow?**

OpenTaint can be integrated into your CI/CD pipeline using our [GitHub Action](https://github.com/seqra/opentaint/tree/main/github) or [GitLab CI template](https://github.com/seqra/opentaint/tree/main/gitlab). It also supports local development with SARIF output for your favorite IDE.
