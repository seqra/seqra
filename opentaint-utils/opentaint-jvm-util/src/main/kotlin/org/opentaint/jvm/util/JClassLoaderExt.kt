package org.opentaint.jvm.util

import org.opentaint.ir.api.jvm.JIRClassOrInterface

interface JClassLoaderExt {
    fun loadClass(jirClass: JIRClassOrInterface, initialize: Boolean = true): Class<*>
}
