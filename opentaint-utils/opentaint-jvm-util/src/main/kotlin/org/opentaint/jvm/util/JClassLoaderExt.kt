package org.opentaint.jvm.util

import org.opentaint.ir.api.jvm.JcClassOrInterface

interface JClassLoaderExt {
    fun loadClass(jcClass: JcClassOrInterface, initialize: Boolean = true): Class<*>
}
