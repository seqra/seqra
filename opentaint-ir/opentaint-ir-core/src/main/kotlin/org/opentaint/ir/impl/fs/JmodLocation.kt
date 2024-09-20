package org.opentaint.ir.impl.fs

import java.io.File
import java.io.InputStream

class JmodLocation(file: File, syncLoadClassesOnlyFrom: List<String>?) :
    JarLocation(file, syncLoadClassesOnlyFrom, true) {

    override fun createRefreshed() = JmodLocation(file, syncLoadClassesOnlyFrom)

    override val jarWithClasses: JarWithClasses?
        get() {
            val jarWithClasses = super.jarWithClasses ?: return null
            return JarWithClasses(jar = jarWithClasses.jar, jarWithClasses.classes.mapKeys { (key, _) ->
                key.removePrefix("classes.")
            })
        }

    override suspend fun resolve(classFullName: String): InputStream? {
        return super.resolve("classes.$classFullName")
    }
}