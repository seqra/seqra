package org.opentaint.ir.impl.features

import org.opentaint.ir.api.JIRClasspathFeature

class JIRFeaturesChain(val features: List<JIRClasspathFeature>) {

    fun newRequest(vararg input: Any) = JIRFeaturesRequest(features, *input)

}

class JIRFeaturesRequest(val features: List<JIRClasspathFeature>, vararg i: Any) {

    val input = i

    inline fun <reified T : JIRClasspathFeature, W> call(call: (T) -> W?): W? {
        var result: W? = null
        for (feature in features) {
            if (feature is T) {
                result = call(feature)
                if (result != null) {
                    break
                }
            }
        }
        if (result != null) {
            for (feature in features) {
                feature.on(result, *input)
            }
        }
        return result
    }

    inline fun <reified T : JIRClasspathFeature> run(call: (T) -> Unit) {
        for (feature in features) {
            if (feature is T) {
                call(feature)
            }
        }

    }

}