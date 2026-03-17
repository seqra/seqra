package org.opentaint.jvm.util

import org.opentaint.ir.api.jvm.JIRClassOrInterface

interface JIRlassLoaderExt {
    fun loadClass(jIRClass: JIRClassOrInterface, initialize: Boolean = true): Class<*>
}
