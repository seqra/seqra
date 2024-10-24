package org.opentaint.ir.impl.tree

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.impl.fs.ClassByteCodeSource
import org.opentaint.ir.impl.vfs.ClassVfsItem
import org.opentaint.ir.impl.vfs.ClasspathClassTree
import org.opentaint.ir.impl.vfs.GlobalClassesVfs
import org.opentaint.ir.impl.vfs.RemoveLocationsVisitor

class GlobalClassVFSTest {

    private val globalClassVFS = GlobalClassesVfs()
    private val lib1 = DummyCodeLocation("xxx")
    private val lib2 = DummyCodeLocation("yyy")

    @Test
    fun `handle classes at top level`() {
        globalClassVFS.addClass(lib1.classSource("Simple"))
        assertNull(lib1.findNode("Simple1"))
        assertEquals("Simple", lib1.findNode("Simple")?.name)
        assertNull(lib2.findNode("Simple"))
    }

    @Test
    fun `handle classes at intermediate level`() {
        globalClassVFS.addClass(lib1.classSource("xxx.Simple"))
        globalClassVFS.addClass(lib2.classSource("xxx.Simple"))
        globalClassVFS.addClass(lib1.classSource("xxx.yyy.Simple"))

        assertEquals("Simple", lib1.findNode("xxx.Simple")?.name)
        assertEquals("Simple", lib2.findNode("xxx.Simple")?.name)
        assertNull(lib2.findNode("xxx.yyy.Simple"))

        lib1.findNode("xxx.yyy.Simple").let {
            assertEquals("Simple", it?.name)
            assertEquals("xxx.yyy.Simple", it?.fullName)
        }
    }

    @Test
    fun `handle classes at limited tree`() {
        val limitedTree = ClasspathClassTree(globalClassVFS, persistentListOf(lib1))
        globalClassVFS.addClass(lib2.classSource("xxx.Simple"))
        globalClassVFS.addClass(lib1.classSource("xxx.Simple"))
        globalClassVFS.addClass(lib2.classSource("xxx.zzz.Simple"))

        with(limitedTree.firstClassOrNull("xxx.Simple")) {
            assertNotNull(this!!)
            assertEquals("Simple", name)
            assertEquals(lib1, location)
        }

        assertNull(limitedTree.firstClassOrNull("xxx.zzz.Simple"))
    }

    @Test
    fun `dropping locations`() {
        val limitedTree = ClasspathClassTree(globalClassVFS, persistentListOf(lib1))

        globalClassVFS.addClass(lib2.classSource("xxx.Simple"))
        globalClassVFS.addClass(lib1.classSource("xxx.Simple"))
        globalClassVFS.addClass(lib2.classSource("xxx.zzz.Simple"))

        globalClassVFS.visit(RemoveLocationsVisitor(setOf(lib2)))

        with(limitedTree.firstClassOrNull("xxx.Simple")) {
            assertNotNull(this!!)
            assertEquals("Simple", name)
            assertEquals(lib1, location)
        }

        assertNull(limitedTree.firstClassOrNull("xxx.zzz.Simple"))
    }
    @Test
    fun `total locations dropping`() {
        val limitedTree = ClasspathClassTree(globalClassVFS, persistentListOf(lib1))

        globalClassVFS.addClass(lib2.classSource("xxx.Simple"))
        globalClassVFS.addClass(lib1.classSource("xxx.Simple"))
        globalClassVFS.addClass(lib2.classSource("xxx.zzz.Simple"))

        globalClassVFS.visit(RemoveLocationsVisitor(setOf(lib1, lib2)))

        assertNull(limitedTree.firstClassOrNull("xxx.Simple"))
    }

    private fun JIRByteCodeLocation.findNode(name: String): ClassVfsItem? {
        return globalClassVFS.findClassNodeOrNull(this, name)
    }

    private fun JIRByteCodeLocation.classSource(name: String): ClassByteCodeSource {
        return ClassByteCodeSource(
            className = name,
            location = this,
            binaryByteCode = ByteArray(10)
        )
    }

}