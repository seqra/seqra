# Spring Boot Endpoint Extraction


Seqra provides built-in support for extracting URL path information from Spring Boot applications. This feature automatically maps controllers to their endpoints and includes this information in SARIF reports, making it easier to understand your application's web attack surface.


## Overview


When scanning Spring Boot applications, Seqra automatically detects related controller methods annotated with Spring mapping annotations (like `@GetMapping`, `@PostMapping`, etc.) and extracts the corresponding HTTP endpoints. This information is included in the SARIF report's `relatedLocations` section, providing a comprehensive view of your application's API surface.


**Important**: Endpoint extraction works for **every rule match** that occurs within or relates to Spring controller methods, regardless of the specific pattern used. Whether your rule targets security vulnerabilities, code quality issues, or any other aspect of controller code, Seqra will automatically include the related endpoint information in the results.


## How It Works


Seqra analyzes controller classes and methods to build a mapping between:
- **Controller methods** (the actual Java code)
- **HTTP endpoints** (the URLs and HTTP methods they handle)

This mapping is automatically included in SARIF reports for **any rule match** within controller methods, allowing security tools and developers to understand which code corresponds to which API endpoints.


## Example


Consider this Spring Boot controller method:


```java
@GetMapping("/profile/display")
@ResponseBody
public String displayUserProfile(
       @RequestParam(defaultValue = "Welcome") String message) {
   // Implementation here
}
```


When using a Seqra rule with this pattern:


```yaml
- pattern: |
   @$ANNOTATION(...)
   $METHOD(...) {
     ...
   }
```


Seqra produces a SARIF result where the `relatedLocations` entry contains detailed endpoint information:


```json
{
 "relatedLocations": [
   {
     "logicalLocations": [
       {
         "name": "org.example.UserProfileController#displayUserProfile",
         "fullyQualifiedName": "GET /profile/display",
         "kind": "function"
       }
     ],
     "physicalLocation": {
       "artifactLocation": {
         "uri": "src/main/java/org/example/UserProfileController.java"
       },
       "region": {
         "startLine": 18
       }
     }
   }
 ]
}
```


### Field Descriptions


- **`name`**: The controller and method identifier in the format `ClassName#methodName`
- **`fullyQualifiedName`**: The HTTP method and URL endpoint (e.g., `GET /profile/display`)
- **`kind`**: Always set to `"function"` for controller methods
- **`physicalLocation`**: Shows where the controller is defined in the source code




## Example Rules


### Controller Detection Rule
```yaml
rules:
 - id: controller
   languages:
     - java
   severity: INFO
   message: Spring controller method detected
   metadata:
     license: MIT
     shortDescription: Detects Spring controller methods
   patterns:
     - pattern: |
         @$ANNOTATION(...)
         $METHOD(...) {
           ...
         }
     - metavariable-pattern:
         metavariable: $ANNOTATION
         pattern-either:
         - pattern: RequestMapping
         - pattern: DeleteMapping
         - pattern: GetMapping
         - pattern: PatchMapping
         - pattern: PostMapping
         - pattern: PutMapping
```
We will refer to this rule as `controller.yaml`.


## Example Project


You can see this feature in action with our [demo Spring project](https://github.com/seqra/seqra-java-spring-demo).


```bash
seqra scan seqra-java-spring-demo --ruleset controller.yaml -o report.sarif
```


This command will generate a SARIF report containing endpoint mappings for all controllers in the demo application.


## Integration with Security Tools


Various security tools can consume endpoint information to provide enhanced context for findings and their impact on specific API endpoints. This is particularly valuable for:

- **Documentation generators and code review tools**: Automatically linking code issues to API documentation and providing API context for code quality issues
- **Security scanners and penetration testing**: Mapping code-level findings to testable endpoints and back
