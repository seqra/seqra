package org.opentaint.ir.go.client

import org.opentaint.ir.go.proto.ProtoProgram

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
        fun create(program: ProtoProgram): ModelPathNormalizer {
            val replacements = program.packagesList
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
