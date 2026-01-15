# Precompiled Classes and JARs Analysis


Seqra supports analyzing pre-compiled Java classes and JARs through a `project.yaml` configuration file. This enables security analysis when you cannot compile the project due to unavailable build environment, or when working with compiled bytecode only.

## File Structure

```yaml
sourceRoot: /path/to/source/root
javaToolchain: /path/to/java/toolchain  # optional
modules:
  - moduleSourceRoot: /path/to/module1/src
    packages:
      - com.example.module1
      - com.example.shared
    moduleClasses:
      - /path/to/module1/classes
      - /path/to/module1/target/classes
  - moduleSourceRoot: /path/to/module2/src
    packages:
      - com.example.module2
    moduleClasses:
      - /path/to/module2.jar
dependencies:  # optional
  - /path/to/dependency1.jar
  - /path/to/dependency2.jar
```

## Field Descriptions

### sourceRoot (required)
The root directory containing your project's source code. This can be an absolute or relative path.

### javaToolchain (optional)
Path to the Java toolchain directory. If not specified, the system's default Java installation will be used. This field is optional and can be omitted from the YAML file.

### modules (required)
Array of module configurations. Each module represents a logical unit of your project. At least one module configuration is required.

#### Module Fields

- **moduleSourceRoot** (required): Path to the module's source code directory
- **packages** (required): List of Java packages contained in this module
- **moduleClasses** (required): List of paths to compiled classes or JAR files for this module

### dependencies (optional)
Array of paths to JAR files that your project depends on. These are typically third-party libraries. This field is optional and can be omitted from the YAML file.

## Creating project.yaml

### Automatic Creation (Recommended)

The `compile` command automatically creates `project.yaml` during compilation:

```bash
seqra compile /path/to/java/project --output ./project-model
```

This generates a complete project model in an output directory with the correct `project.yaml` configuration.

### Manual Creation for Pre-compiled Projects

If you already have compiled classes or JARs, use the `project` command to create `project.yaml`:

```bash
seqra project --output ./path/to/project --source-root /path/to/sources \
  --classpath /path/to/app.jar \
  --package com.example.app \
  --dependency /path/to/lib/commons-lang3.jar
```

#### Command Flags

- **--output**: Output directory for project.yaml (required)
- **--source-root**: Source root directory (required)
- **--classpath**: Classpath entries (classes or JAR files, required, can be specified multiple times)
- **--package**: Java packages (required, can be specified multiple times)
- **--dependency**: Project dependencies (JAR files, optional)

#### Path Resolution

The project command intelligently handles path resolution:
- All input paths are converted to absolute paths for validation
- When generating `project.yaml`, paths are converted to relative paths if they are within the output directory
- This ensures the `project.yaml` file is portable when the entire project model directory is moved

### Examples

#### Single Module Project

```bash
seqra project --output . --source-root . \
  --classpath target/classes \
  --package com.example.myapp
```

#### Multi-Module Project with Dependencies

```bash
seqra project --output . --source-root . \
  --dependency lib/spring-boot-starter-web-3.2.0.jar \
  --dependency lib/jackson-core-2.15.2.jar \
  --classpath module-core/target/classes \
  --classpath module-web/target/classes \
  --package com.example.core \
  --package com.example.web \
  --package com.example.controller
```

#### Using Pre-built JARs

```bash
seqra project --output . --source-root src \
  --classpath dist/myapp.jar \
  --classpath dist/mylib.jar \
  --package com.example.service
```

## Usage with Scan Command

Once you have a `project.yaml` file, you can scan your project. The scan command will use `project.yaml` if it exists, or compile the project first:

```bash
seqra scan /path/to/project --output results.sarif
```

The scan command automatically detects whether the provided path contains a `project.yaml` file:
- If `project.yaml` exists: Uses it directly (scan-only mode)
- If `project.yaml` doesn't exist: Compiles the project first, then scans

## Best Practices

1. **Use relative paths when possible** - the project command automatically converts absolute paths to relative paths when they are within the output directory
2. **Include all relevant packages** in each module to ensure complete analysis
3. **Specify all dependencies** that your code references to avoid missing symbols

## Common Use Cases

### Maven Project
```bash
seqra project --output . --source-root . \
  --classpath target/classes \
  --package com.example.app \
  --dependency ~/.m2/repository/org/springframework/spring-core/6.0.0/spring-core-6.0.0.jar
```

### Gradle Project
```bash
seqra project --output . --source-root . \
  --classpath build/classes/java/main \
  --package com.example.app \
  --dependency build/libs/dependencies/guava-32.1.2-jre.jar
```

### Legacy Project with Multiple JARs
```bash
seqra project --output . --source-root src \
  --classpath dist/app.jar \
  --classpath dist/utils.jar \
  --package com.legacy.app \
  --dependency lib/commons-io-2.11.0.jar
```
