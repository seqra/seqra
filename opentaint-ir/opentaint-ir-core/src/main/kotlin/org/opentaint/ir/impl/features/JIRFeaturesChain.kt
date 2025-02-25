package org.opentaint.ir.impl.features

import org.opentaint.ir.api.JIRClasspathFeature
import org.opentaint.ir.api.JIRFeatureEvent
import org.opentaint.ir.api.JIRLookupExtFeature

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
        var result: W? = null
        var event: JIRFeatureEvent? = null
        for (feature in features) {
            if (feature is T) {
                result = call(feature)
                if (result != null) {
                    event = feature.event(result)
                    break
                }
            }
        }
        if (result != null && event != null) {
            for (feature in features) {
                feature.on(event)
            }
        }
        return result
    }
}

class JIRFeatureEventImpl(
    override val feature: JIRClasspathFeature,
    override val result: Any,
) : JIRFeatureEvent