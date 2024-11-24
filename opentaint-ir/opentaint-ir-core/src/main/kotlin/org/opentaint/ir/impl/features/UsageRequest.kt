
package org.opentaint.ir.impl.features

import kotlinx.serialization.Serializable
import org.opentaint.ir.api.ClassSource

@Serializable
data class UsageFeatureRequest(
    val methodName: String?,
    val description: String?,
    val field: String?,
    val opcodes: Collection<Int>,
    val className: Set<String>
) : java.io.Serializable

class UsageFeatureResponse(
    val source: ClassSource,
    val offsets: ShortArray
)

