# Skill: Build Project

Build a target project and produce a `project.yaml` model for analysis.

## Prerequisites

- `opentaint` CLI available
- Java 21+ installed
- For Gradle/Maven: build tool installed, project builds independently

## Procedure

### 1. Determine project type

Examine directory contents:
- `build.gradle` or `build.gradle.kts` -> Gradle
- `pom.xml` -> Maven
- Pre-compiled JARs/WARs -> classpath mode
- Existing `project.yaml` in a subdirectory -> already compiled

### 2a. Gradle/Maven projects (autobuilder)

```bash
opentaint compile /path/to/project -o ./opentaint-project
```

### 2b. Pre-compiled artifacts (manual project model)

```bash
opentaint project \
  --output ./opentaint-project \
  --source-root /path/to/src \
  --classpath /path/to/app.jar \
  --package com.example.app
```

### 3. Verify

Check that `./opentaint-project/project.yaml` exists and is non-empty.

## Troubleshooting

- **Build tool not found**: Install Gradle/Maven or use a wrapper (`./gradlew`, `./mvnw`)
- **Java version mismatch**: Set `JAVA_HOME` to the version required by the project
- **Compilation errors**: Check the autobuilder log, fix build issues, retry
- **Missing dependencies**: Ensure all submodules are initialized (`git submodule update --init`)
- **Fallback**: If autobuilder fails, build the project manually, then use `opentaint project` with the compiled artifacts
