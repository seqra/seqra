package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.GoIRProgram

/** Model policy and transformer installation, separate from wire deserialization. */
internal object GoIRModelLoader {
    fun apply(build: GoIRDeserializer.DeserializedGoIRBuild): GoIRProgram {
        if (build.models.isEmpty()) return build.program
        val definitions = build.models.map { model ->
            val normalizer = try {
                ModelPathNormalizer.create(model.program)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("invalid Go model ${model.source}: ${error.message}", error)
            }
            val program = GoIRDeserializer().deserializeProgram(
                model.program,
                GoIRDeserializer.DeserializationConfig(normalizer, normalizer.replacements.keys),
            )
            GoIRModelDefinition(model.source, program, normalizer.targets)
        }
        return GoIRTransformerChain(build.program, listOf(GoIRModelTransformer(build.program, definitions)))
    }
}
