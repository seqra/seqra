
package org.opentaint.ir.impl.bytecode

import org.objectweb.asm.ClassWriter
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.findClass


/**
 * ASM class writer with opentaint-ir specific resolution of common superclasses
 */
class JIRDatabaseClassWriter(val classpath: JIRClasspath, flags: Int) : ClassWriter(flags) {

    /*
   * We need to overwrite this method here, as we are generating multiple classes that might reference each other. See
   * asm4-guide, top of page 45 for more information.
   *
   * @see org.objectweb.asm.ClassWriter#getCommonSuperClass(java.lang.String, java.lang.String)
   */
    override fun getCommonSuperClass(type1: String, type2: String): String {
        val typeName1 = type1.replace('/', '.')
        val typeName2 = type2.replace('/', '.')
        val jirClass1 = classpath.findClass(typeName1)
        val jirClass2 = classpath.findClass(typeName2)
        val super1 = jirClass1.allSuperClasses
        val super2 = jirClass2.allSuperClasses

        // If these two classes haven't been loaded yet or are phantom, we take
        // java.lang.Object as the common superclass
        return when {
            super1.isEmpty() || super2.isEmpty() -> "java/lang/Object"
            else -> {
                super1.firstOrNull { super2.contains(it) }?.name?.replace(".", "/")
                    ?: throw RuntimeException("Could not find common super class for $type1 and $type2")
            }

        }
    }

    private val JIRClassOrInterface.allSuperClasses: List<JIRClassOrInterface>
        get() {
            val result = arrayListOf<JIRClassOrInterface>()
            var jirClass = superClass
            while (jirClass != null) {
                result.add(jirClass)
                jirClass = jirClass.superClass
            }
            return result
        }
}
