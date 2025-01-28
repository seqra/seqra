package org.opentaint.ir.impl.bytecode

import kotlinx.metadata.Flag
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmType
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.signature
import mu.KLogging
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.impl.features.classpaths.KotlinMetadata
import org.opentaint.ir.impl.features.classpaths.KotlinMetadataHolder

val logger = object : KLogging() {}.logger

val JIRClassOrInterface.kMetadata: KotlinMetadataHolder?
    get() {
        return extensionValue(KotlinMetadata.METADATA_KEY)
    }

val JIRMethod.kmFunction: KmFunction?
    get() =
        enclosingClass.kMetadata?.functions?.firstOrNull { it.signature?.name == name && it.signature?.desc == description }

val JIRMethod.kmConstructor: KmConstructor?
    get() =
        enclosingClass.kMetadata?.constructors?.firstOrNull { it.signature?.name == name && it.signature?.desc == description }

val JIRParameter.kmParameter: KmValueParameter?
    get() {
        try {
            method.kmFunction?.let {
                // Shift needed to properly handle extension functions
                val shift = if (it.receiverParameterType != null) 1 else 0

                // index - shift could be out of bounds if generated JVM parameter is fictive
                // E.g., see how extension functions and coroutines are compiled
                return it.valueParameters.getOrNull(index - shift)
            }

            return method.kmConstructor?.valueParameters?.get(index)
        } catch (e: Exception) {
            return null
        }
    }

// If parameter is a receiver parameter, it doesn't have KmValueParameter instance, but we still can get KmType for it
val JIRParameter.kmType: KmType?
    get() =
        kmParameter?.type ?: run {
            if (index == 0)
                method.kmFunction?.receiverParameterType
            else
                null
        }

val JIRField.kmType: KmType?
    get() =
        enclosingClass.kMetadata?.properties?.let { property ->
            // TODO: maybe we need to check desc here as well
            property.firstOrNull { it.fieldSignature?.name == name }?.returnType
        }

val JIRMethod.kmReturnType: KmType?
    get() =
        kmFunction?.returnType

val KmType.isNullable: Boolean
    get() = Flag.Type.IS_NULLABLE(flags)