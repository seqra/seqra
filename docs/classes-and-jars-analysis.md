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
  --module "sources/app:com.example.app:classes/app" \
  --dependency /path/to/lib/commons-lang3.jar
```

#### Module Configuration Format

The `--module` flag uses the format: `sourceRoot:packages:classes`

- **sourceRoot**: Path to module source directory
- **packages**: Comma-separated list of Java packages
- **classes**: Comma-separated list of paths to compiled classes or JARs

#### Path Resolution

The project command intelligently handles path resolution:
- All input paths are converted to absolute paths for validation
- When generating `project.yaml`, paths are converted to relative paths if they are within the output directory
- This ensures the `project.yaml` file is portable when the entire project model directory is moved
-
### Examples

#### Single Module Project

```bash
seqra project --output . --source-root . \
  --module ".:com.example.myapp:target/classes"
```

#### Multi-Module Project with Dependencies

```bash
seqra project --output . --source-root . \
  --java-toolchain /usr/lib/jvm/java-21-openjdk-amd64 \
  --dependency lib/spring-boot-starter-web-3.2.0.jar \
  --dependency lib/jackson-core-2.15.2.jar \
  --module "module-core:com.example.core:module-core/target/classes" \
  --module "module-web:com.example.web,com.example.controller:module-web/target/classes"
```

#### Using Pre-built JARs

```bash
seqra project --output . --source-root src \
  --module "src/app:com.example.service:dist/myapp.jar" \
  --module "src/lib:com.example.service:dist/mylib.jar" \
```

## Usage with Scan Command

Once you have a `project.yaml` file, you can scan your project:

```bash
# Scan project directory if project.yaml exists there, or compile the project first, then scan
seqra scan /path/to/project
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
  --module "src/main/java:com.example.app:target/classes" \
  --dependency ~/.m2/repository/org/springframework/spring-core/6.0.0/spring-core-6.0.0.jar
```

### Gradle Project
```bash
seqra project --output . --source-root . \
  --module "src/main/java:com.example.app:build/classes/java/main" \
  --dependency build/libs/dependencies/guava-32.1.2-jre.jar
```

### Legacy Project with Multiple JARs
```bash
seqra project --output . --source-root src \
  --module "src:com.legacy.app:dist/app.jar,dist/utils.jar" \
  --dependency lib/commons-io-2.11.0.jar
```
