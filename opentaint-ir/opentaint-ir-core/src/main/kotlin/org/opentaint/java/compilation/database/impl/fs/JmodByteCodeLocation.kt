package org.opentaint.java.compilation.database.impl.fs

import java.io.File
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile

class JmodByteCodeLocation(file: File, syncLoadClassesOnlyFrom: List<String>?) :
    JarFileLocationImpl(file, syncLoadClassesOnlyFrom) {

    override fun createRefreshed() = JmodByteCodeLocation(file, syncLoadClassesOnlyFrom)

    override fun jarClasses(): Pair<JarFile, Map<String, Pair<JarFile, JarEntry>>>? {
        val (jar, classes) = super.jarClasses() ?: return null
        return jar to classes.mapKeys { (key, resources) ->
            key.removePrefix("classes.")
        }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        return super.resolve("classes.$classFullName")
    }
}