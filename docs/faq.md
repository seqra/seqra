## FAQ

**Why is Seqra a great choice for Spring applications?**

Seqra is purpose-built for Spring. It understands Spring annotations (`@GetMapping`, `@PostMapping`, etc.), Spring data access patterns (JdbcTemplate, JPA, Spring Data), and Spring-specific sanitizers like `HtmlUtils.htmlEscape()`. Every finding is automatically mapped to its HTTP endpoint, so you know exactly which APIs are affected.

**What Spring vulnerabilities does Seqra detect?**

Seqra detects 20+ vulnerability types including SQL injection (via JdbcTemplate, JPA, Hibernate), XSS in controllers, SSRF via RestTemplate/WebClient, SpEL injection, open redirects, path traversal, command injection, and more. Each rule includes Spring-specific detection patterns and remediation guidance.

**What languages are supported?**

Java and Kotlin are supported, with full support for Spring Boot, Spring MVC, and plain Java/Kotlin projects. Broader language support is planned.

**How does Seqra compare to Semgrep?**

Seqra interprets Semgrep-style rules using **interprocedural data-flow analysis**, going beyond simple pattern matching to track data across function calls and identify complex vulnerabilities that pure pattern matching would miss.

**How does Seqra compare to CodeQL?**

You get **CodeQL-like findings** without the learning curve of a specialized query language—rules are written with familiar code patterns.

**Is Seqra free to use?**

Yes! You can use Seqra for free, including on private repos and for internal commercial use (excluding competing uses). The analyzer code is source-available under the **Functional Source License (FSL-1.1-ALv2)**, which converts to Apache 2.0 after two years from the release date.

**Can I use existing Semgrep rules?**

Yes! Seqra is compatible with Semgrep rule syntax, plus adds dataflow analysis capabilities.

**How do I integrate Seqra into my development workflow?**

Seqra can be integrated into your CI/CD pipeline using our [GitHub Action](https://github.com/seqra/seqra-action) or [GitLab CI template](https://github.com/seqra/seqra-gitlab). It also supports local development with SARIF output for your favorite IDE.
