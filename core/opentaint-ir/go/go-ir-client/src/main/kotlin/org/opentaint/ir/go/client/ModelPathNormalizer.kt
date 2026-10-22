package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.*

internal data class ModelPathNormalizer(
    val replacements: Map<String, String>,
    val targets: Set<String>,
    private val orderedModelPaths: List<String>,
) {
    fun path(value: String): String = replacements[value] ?: value

    fun symbol(value: String): String = orderedModelPaths.fold(value) { result, modelPath ->
        result.replace(modelPath, replacements.getValue(modelPath))
    }

    companion object {
        fun create(program: GoIRProgram): ModelPathNormalizer {
            val replacements = program.packages.values
                .filterNot { it.isDependency || it.isStdlib || it.importPath == UNIVERSE_PACKAGE_PATH }
                .associate { pkg ->
                    require(pkg.importPath.startsWith(GO_MODEL_PREFIX)) {
                        "Go model package \"${pkg.importPath}\" is not model-addressed, " +
                            "its import path must be ${GO_MODEL_PREFIX}<target-import-path>"
                    }
                    val target = pkg.importPath.removePrefix(GO_MODEL_PREFIX)
                    require(target.isNotEmpty()) {
                        "Go model package \"${pkg.importPath}\" has an empty target package"
                    }
                    pkg.importPath to target
                }
            require(replacements.isNotEmpty()) {
                "Go model contains no model-addressed packages, expected at least one package " +
                    "under $GO_MODEL_PREFIX"
            }
            return ModelPathNormalizer(
                replacements,
                replacements.values.toSet(),
                replacements.keys.sortedByDescending(String::length),
            )
        }
    }
}

private const val GO_MODEL_PREFIX = "opentaint/"
private const val UNIVERSE_PACKAGE_PATH = "<universe>"

internal class GoIRModelPathTransformer(private val normalizer: ModelPathNormalizer) : GoIRTransformer {
    override fun packagePath(importPath: String) = normalizer.path(importPath)
    override fun symbolName(fullName: String) = normalizer.symbol(fullName)
    override fun resolvePackage(importPath: String, next: () -> GoIRPackage?): GoIRPackage? {
        val pkg = next() ?: return null
        if (pkg.importPath !in normalizer.replacements) return pkg
        return object : GoIRPackage by pkg {
            override val isStdlib = false
            override val isDependency = true
        }
    }
}
