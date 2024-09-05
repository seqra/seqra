package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.reader.ClassMetaInfo
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClassTreeTest {

    private val classTree = ClassTree()
    private val lib1 = DummyCodeLocation("xxx")
    private val lib2 = DummyCodeLocation("yyy")

    private val info = ClassMetaInfo(
        interfaces = persistentListOf(),
        annotations = persistentListOf(),
        methods = persistentListOf(),
        fields = persistentListOf(),
        superClass = null,
        access = 0,
        name = ""
    )

    @Test
    fun `handle classes at top level`() {
        classTree.addClass(lib1, "Simple", info)
        assertNull(lib1.findNode("Simple1"))
        assertEquals("Simple", lib1.findNode("Simple")?.simpleName)
        assertNull(lib2.findNode("Simple"))
    }
    
    @Test
    fun `handle classes at intermediate level`() {
        classTree.addClass(lib1, "xxx.Simple", info)
        classTree.addClass(lib2, "xxx.Simple", info)
        classTree.addClass(lib1, "xxx.yyy.Simple", info)

        assertEquals("Simple", lib1.findNode("xxx.Simple")?.simpleName)
        assertEquals("Simple", lib2.findNode("xxx.Simple")?.simpleName)
        assertNull(lib2.findNode("xxx.yyy.Simple"))

        lib1.findNode("xxx.yyy.Simple").let {
            assertEquals("Simple", it?.simpleName)
            assertEquals("xxx.yyy.Simple", it?.fullName)
        }
    }

    @Test
    fun `handle classes at limited tree`() {
        val limitedTree = ClasspathClassTree(classTree, persistentListOf(lib1))
        classTree.addClass(lib2, "xxx.Simple", info)
        classTree.addClass(lib1, "xxx.Simple", info)
        classTree.addClass(lib2, "xxx.zzz.Simple", info)

        with(limitedTree.findClassOrNull("xxx.Simple")) {
            assertNotNull(this!!)
            assertEquals("Simple", simpleName)
            assertEquals(lib1, location)
        }

        assertNull(limitedTree.findClassOrNull("xxx.zzz.Simple"))
    }

    private fun ByteCodeLocation.findNode(name: String): ClassNode? {
        return classTree.findClassNodeOrNull(this, name)
    }
    
}