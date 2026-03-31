# Skill: Discover Entry Points

Identify the attack surface of the target project by reading source code and project structure.

## Prerequisites

- Target project source code accessible
- Project has been built (build-project skill complete)

## Procedure

### 1. Search for entry points by type

Look for these patterns in the source code:

- **Spring controllers**: `@RestController`, `@Controller`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`
- **Servlet handlers**: Classes extending `HttpServlet` with `doGet`, `doPost`, etc.
- **JAX-RS endpoints**: `@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE`
- **Message handlers**: `@JmsListener`, `@KafkaListener`, `@RabbitListener`
- **CLI entry points**: `main(String[])` methods that process external input
- **Scheduled tasks**: `@Scheduled` methods that read external state

### 2. For each entry point, determine

- What external data it receives (HTTP params, headers, body, message payload)
- What operations it performs (DB queries, file I/O, command exec, HTTP calls)
- Which vulnerability classes are relevant (SQLi, XSS, command injection, path traversal, SSRF, XXE)

### 3. Examine dependencies

Read `build.gradle`, `pom.xml`, or `project.yaml` for:
- Web frameworks (Spring Boot, Micronaut, Quarkus)
- Database libraries (JDBC, JPA/Hibernate, MyBatis)
- Template engines (Thymeleaf, FreeMarker, Velocity)
- HTTP clients (OkHttp, Apache HttpClient, RestTemplate, WebClient)

### 4. Record findings

Document entry points, data sources, and relevant vulnerability classes in `opentaint-analysis-plan.md`.

## Engine Notes

- Spring projects: The analyzer auto-discovers Spring endpoints when `--project-kind spring-web` is set
- Generic projects: The analyzer uses all public/protected methods from public project classes
- Targeted analysis: Use `--debug-run-analysis-on-selected-entry-points "com.example.Class#method"` for focused testing
