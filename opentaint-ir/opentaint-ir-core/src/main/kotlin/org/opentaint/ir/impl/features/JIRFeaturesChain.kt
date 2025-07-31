package org.opentaint.ir.impl.features

import org.opentaint.ir.api.jvm.JIRClasspathFeature
import org.opentaint.ir.api.jvm.JIRFeatureEvent
import org.opentaint.ir.api.jvm.JIRLookupExtFeature

class JIRFeaturesChain(val features: List<JIRClasspathFeature>) {

    val featuresArray = features.toTypedArray()
    val classLookups = features.filterIsInstance<JIRLookupExtFeature>()

    inline fun <reified T : JIRClasspathFeature> run(call: (T) -> Unit) {
        for (feature in featuresArray) {
            if (feature is T) {
                call(feature)
            }
        }
    }

    inline fun <reified T : JIRClasspathFeature, W> call(call: (T) -> W?): W? {
        for (feature in featuresArray) {
            if (feature is T) {
                val result = call(feature)
                if (result != null) {
                    val event = feature.event(result)
                    if (event != null) {
                        for (anyFeature in featuresArray) {
                            anyFeature.on(event)
                        }
                    }
                    return result
                }
            }
        }
        return null
    }
}

class JIRFeatureEventImpl(
    override val feature: JIRClasspathFeature,
    override val result: Any,
) : JIRFeatureEvent
