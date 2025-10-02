package org.opentaint.jvm.sast.project

import com.jetbrains.rd.util.ConcurrentHashMap
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature
import org.opentaint.ir.impl.features.classpaths.AbstractJIRResolvedResult.JIRResolvedClassResultImpl

class ProjectClassPathExtensionFeature : JIRClasspathExtFeature {
    private val classPathExtension = ConcurrentHashMap<String, JIRClassOrInterface>()

    override fun tryFindClass(classpath: JIRClasspath, name: String): JIRClasspathExtFeature.JIRResolvedClassResult? {
        val clazz = classPathExtension[name]
        if (clazz != null) {
            return JIRResolvedClassResultImpl(name, clazz)
        }
        return null
    }

    fun extendClassPath(cls: JIRClassOrInterface) {
        classPathExtension[cls.name] = cls
    }
}
