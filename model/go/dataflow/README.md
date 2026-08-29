# Built-in Go models

Each child directory is one Go model module. The analyzer loads these modules by default.

Use one module for each dependency version line. This rule prevents dependency version
conflicts between models. The model module name must be `opentaint`. Put each model package
under the exact import path of the target package.

The `modelcontextprotocol-go-sdk-v1` module models server callbacks in MCP Go SDK 1.7.0.
It calls tool, prompt, resource, middleware, server option, and HTTP server factory handlers.
These calls let data flow analysis enter handlers that the SDK stores for a later request.
Before each call, the model converts an untrusted value to the request type. This value marks
the MCP request and the generic tool input as external input.

The MCP model imports the target SDK. It uses target types in function signatures. It has
one local partial `Server` type because Go requires a local receiver type for a method
declaration. The model does not declare target fields or other target structs.

The MCP pass-through rules are in `../config`. They model value flow through server setup
and error constructors. The Go model bodies model callback calls. Use both forms together
when a flow enters a stored callback and then passes through an SDK value operation.
