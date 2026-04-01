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

### 2b. If `opentaint compile` fails — manual build + `opentaint project`

If the autobuilder cannot build the project, build it manually first, then create the project model:

1. **Build the project manually**:
```bash
# Gradle
./gradlew build -x test

# Maven
mvn package -DskipTests
```

2. **Create the project model with `opentaint project`**:

> **CRITICAL**: Always specify `--package` to restrict analysis to project code only.
> Without `--package`, the analyzer will attempt to analyze ALL classes including third-party
> libraries, and will hang or run for hours.

```bash
opentaint project \
  --output ./opentaint-project \
  --source-root /path/to/src \
  --classpath /path/to/app.jar \
  --package com.example.app
```

For multi-module projects, use multiple `--classpath` and `--package` flags:

```bash
opentaint project \
  --output ./opentaint-project \
  --source-root /path/to/project \
  --classpath /path/to/module1/build/libs/module1.jar \
  --classpath /path/to/module2/build/libs/module2.jar \
  --package com.example.module1 \
  --package com.example.module2
```

### 3. Verify

Check that `./opentaint-project/project.yaml` exists and is non-empty.

## Troubleshooting

- **Build tool not found**: Install Gradle/Maven or use a wrapper (`./gradlew`, `./mvnw`)
- **Java version mismatch**: Set `JAVA_HOME` to the version required by the project
- **Compilation errors**: Check the autobuilder log, fix build issues, retry
- **Missing dependencies**: Ensure all submodules are initialized (`git submodule update --init`)
- **Autobuilder fails**: Build the project manually (see 2b above), then use `opentaint project` with the compiled artifacts
- **Analysis hangs**: You likely forgot `--package` — the analyzer is processing third-party libraries. Re-run `opentaint project` with `--package` to restrict to project code
