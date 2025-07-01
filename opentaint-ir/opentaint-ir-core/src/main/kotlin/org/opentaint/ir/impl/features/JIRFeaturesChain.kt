package org.opentaint.ir.impl.features

import org.opentaint.ir.api.jvm.JIRClasspathFeature
import org.opentaint.ir.api.jvm.JIRFeatureEvent
import org.opentaint.ir.api.jvm.JIRLookupExtFeature

class JIRFeaturesChain(val features: List<JIRClasspathFeature>) {

    val classLookups = features.filterIsInstance<JIRLookupExtFeature>()

    inline fun <reified T : JIRClasspathFeature> run(call: (T) -> Unit) {
        for (feature in features) {
            if (feature is T) {
                call(feature)
            }
        }
    }

    inline fun <reified T : JIRClasspathFeature, W> call(call: (T) -> W?): W? {
        val (result: W?, event: JIRFeatureEvent?) = features.firstNotNullOfOrNull { feature ->
            (feature as? T)?.let(call)?.let { result -> result to feature.event(result) }
        } ?: return null
        event?.let {
            features.forEach { feature -> feature.on(event) }
        }
        return result
    }
}

class JIRFeatureEventImpl(
    override val feature: JIRClasspathFeature,
    override val result: Any,
) : JIRFeatureEvent
