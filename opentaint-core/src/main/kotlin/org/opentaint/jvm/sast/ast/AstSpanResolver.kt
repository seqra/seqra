package org.opentaint.jvm.sast.ast

import org.opentaint.jvm.sast.sarif.JIRSarifTraits
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.sast.sarif.IntermediateLocation
import org.opentaint.jvm.sast.sarif.LocationSpan
import java.nio.file.Path

interface AstSpanResolver {
    fun computeSpan(sourceLocation: Path, location: IntermediateLocation): LocationSpan?
    fun getParameterName(sourceLocation: Path, inst: JIRInst, paramIdx: Int): String?
}

class AstSpanResolverProvider(traits: JIRSarifTraits) {
    private val javaSpanResolver = JavaAstSpanResolver(traits)
    private val kotlinSpanResolver = KotlinAstSpanResolver(traits)

    fun resolver(language: ClassIndex.Language): AstSpanResolver = when (language) {
        ClassIndex.Language.Java -> javaSpanResolver
        ClassIndex.Language.Kotlin -> kotlinSpanResolver
    }
}
