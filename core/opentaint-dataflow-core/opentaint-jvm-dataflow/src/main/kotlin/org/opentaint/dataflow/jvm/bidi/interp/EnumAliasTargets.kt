package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.ext.isEnum

/** New-core alias targets only. Explicit static operations keep their normal lowering. */
internal class EnumAliasTargets(private val cp: JIRClasspath) {
    fun reject(base: AccessPathBase, path: List<Accessor>): Boolean {
        if (base != AccessPathBase.ClassStatic || path.firstOrNull() !is ClassStaticAccessor) return false
        val field = path.getOrNull(1) as? FieldAccessor ?: return false
        val owner = cp.findClassOrNull(field.className) ?: return false
        val resolved = owner.declaredFields.firstOrNull {
            it.name == field.fieldName && it.type.typeName == field.fieldType
        } ?: return false

        // DIRTY HACK: Drop enum-constant static alias targets to stop the
        // EntityType FLOW matrix. This is not an immutability proof.
        return resolved.isStatic && resolved.isEnum
    }
}
