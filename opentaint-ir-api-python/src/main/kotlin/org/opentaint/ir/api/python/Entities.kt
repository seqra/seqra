package org.opentaint.ir.api.python

import java.io.Closeable

/**
 * Top-level container for an analyzed Python project.
 */
interface PIRClasspath : Closeable {
    val modules: List<PIRModule>
    fun findModuleOrNull(name: String): PIRModule?
    fun findClassOrNull(qualifiedName: String): PIRClass?
    fun findFunctionOrNull(qualifiedName: String): PIRFunction?
    val pythonVersion: String
    val mypyVersion: String
}

/**
 * Settings for creating a PIRClasspath.
 */
data class PIRSettings(
    val sources: List<String>,
    val pythonExecutable: String = "python3",
    val pythonVersion: String? = null,
    val mypyFlags: List<String> = emptyList(),
    val searchPaths: List<String> = emptyList(),
    val serverModule: String = "pir_server",
    val serverStartupTimeout: java.time.Duration = java.time.Duration.ofSeconds(30),
    val rpcTimeout: java.time.Duration = java.time.Duration.ofSeconds(120),
    val embeddedServer: Boolean = true,
)

/**
 * A diagnostic message produced during IR construction.
 */
data class PIRDiagnostic(
    val severity: PIRDiagnosticSeverity,
    val message: String,
    val functionName: String,
    val exceptionType: String,
)

enum class PIRDiagnosticSeverity { WARNING, ERROR }

/**
 * A Python module (.py file).
 */
interface PIRModule {
    val name: String
    val path: String
    val classes: List<PIRClass>
    val functions: List<PIRFunction>
    val fields: List<PIRField>
    val moduleInit: PIRFunction
    val imports: List<String>
    val classpath: PIRClasspath
    val diagnostics: List<PIRDiagnostic>
}

/**
 * A Python class.
 */
interface PIRClass {
    val name: String
    val qualifiedName: String
    val baseClasses: List<String>
    val mro: List<String>
    val methods: List<PIRFunction>
    val fields: List<PIRField>
    val nestedClasses: List<PIRClass>
    val properties: List<PIRProperty>
    val decorators: List<PIRDecorator>
    val isAbstract: Boolean
    val isDataclass: Boolean
    val isEnum: Boolean
    val module: PIRModule
}

/**
 * A Python function or method.
 */
interface PIRFunction {
    val name: String
    val qualifiedName: String
    val parameters: List<PIRParameter>
    val returnType: PIRType
    val cfg: PIRCFG
    val decorators: List<PIRDecorator>
    val isAsync: Boolean
    val isGenerator: Boolean
    val isStaticMethod: Boolean
    val isClassMethod: Boolean
    val isProperty: Boolean
    val closureVars: List<String>
    val enclosingClass: PIRClass?
    val module: PIRModule
}

/**
 * A function parameter.
 */
interface PIRParameter {
    val name: String
    val type: PIRType
    val kind: PIRParameterKind
    val hasDefault: Boolean
    val index: Int
}

enum class PIRParameterKind {
    POSITIONAL_ONLY,
    POSITIONAL_OR_KEYWORD,
    VAR_POSITIONAL,
    KEYWORD_ONLY,
    VAR_KEYWORD,
}

/**
 * A field (module-level variable, class variable, or instance variable).
 */
interface PIRField {
    val name: String
    val type: PIRType
    val isClassVar: Boolean
    val hasInitializer: Boolean
}

/**
 * A Python property (with optional getter/setter/deleter).
 */
interface PIRProperty {
    val name: String
    val type: PIRType
    val getter: PIRFunction?
    val setter: PIRFunction?
    val deleter: PIRFunction?
}

/**
 * A decorator applied to a class or function.
 */
interface PIRDecorator {
    val name: String
    val qualifiedName: String
    val arguments: List<String>
}
