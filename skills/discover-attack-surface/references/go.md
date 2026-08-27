# Discover attack surface for Go

## Workflow

### 1. Check built-in coverage

Built-in Go source rules are in `go/lib/` under the rules root from `opentaint health --rules`. Search for the package, type, or function name. Read each matching rule to confirm that its patterns cover the plan member. Project rules are in `.opentaint/rules/go`.

### 2. Classify the plan members

- A package function has a name such as `example.com/module/pkg.Read`.
- A method has a name such as `(*example.com/module/pkg.Reader).Read` or `(example.com/module/pkg.Reader).Read`.
- The signature is `args:<count>`. It is the number of explicit function arguments. A receiver is not an explicit argument.
- Copy the method and signature to the source tracking unit without a change.

Use `go list -m -json all` in the project to find the module path, version, replacement, and source directory. Use `go doc <import-path>.<name>` for public API text. Read the dependency source in the module directory when the API text is not sufficient.

### 3. Write the source units

Use the module identity in `dependencies`. The normal form is `module/path@version`, for example `github.com/modelcontextprotocol/go-sdk@v1.7.0`. If the module has a local replacement, use the replacement path and state this in the source note.
