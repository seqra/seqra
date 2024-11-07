package org.opentaint.ir.impl.fs

import java.io.File

class JmodLocation(file: File) : JarLocation(file, true) {

    override fun createRefreshed() = JmodLocation(jarOrFolder)

    override val jarWithClasses: JarWithClasses?
        get() {
            val jarWithClasses = super.jarWithClasses ?: return null
            return JarWithClasses(jar = jarWithClasses.jar, jarWithClasses.classes.mapKeys { (key, _) ->
                key.removePrefix("classes.")
            })
        }

    override val classNames: Set<String>?
        get() = super.classNames?.map { it.removePrefix("classes.") }?.toSet()


    override fun resolve(classFullName: String): ByteArray? {
        return super.resolve("classes.$classFullName")
    }
}