package org.opentaint.ir.impl

import com.google.common.cache.AbstractCache
import com.google.common.collect.Iterators
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.findClassOrNull
import org.opentaint.ir.impl.fs.BuildFolderLocation
import org.opentaint.ir.impl.storage.PersistentLocationRegistry
import org.opentaint.ir.jirdb
import java.io.File
import java.nio.file.Files
import java.util.*

class DatabaseLifecycleTest : LibrariesMixin {

    private var db: JIRDBImpl? = runBlocking {
        jirdb {
            useProcessJavaRuntime()

        } as JIRDBImpl
    }
    private val tempFolder = Files.createTempDirectory("classpath-copy-" + UUID.randomUUID()).toFile()

    private val testDirClone: File get() = File(tempFolder, "test")
    private val guavaLibClone: File get() = File(tempFolder, guavaLib.name)


    @BeforeEach
    fun cloneClasspath() {
        allClasspath.forEach {
            if (it.isFile) {
                it.copyTo(File(tempFolder, it.name))
            } else if (it.absolutePath.replace(File.separator, "/").contains("build/classes/kotlin/test")) {
                // copy only test kotlin resources
                it.copyRecursively(File(tempFolder, it.name), true)
            }
        }
    }

    @Test
    fun `refresh is working when build dir is removed`() = runBlocking {
        val cp = db!!.classpath(listOf(testDirClone))
        val barKt = cp.findClass<BarKt>()

        assertTrue(testDirClone.deleteRecursively())
        assertNull(barKt.methods.first().body())

        db!!.refresh()

        withRegistry {
            assertEquals(1, snapshots.size)
            assertEquals(1, locations.filterIsInstance<BuildFolderLocation>().size)
        }

        assertNotNull(cp.findClassOrNull<BarKt>())
        cp.close()
        db!!.refresh()
        withRegistry {
            assertTrue(snapshots.isEmpty())
            assertTrue(locations.all { it !is BuildFolderLocation })
        }
    }

    @Test
    fun `method could be read from build dir`() = runBlocking {
        val cp = db!!.classpath(listOf(testDirClone))
        val barKt = cp.findClassOrNull<BarKt>()
        assertNotNull(barKt!!)

        assertNotNull {
            runBlocking {
                barKt.methods.first().body()
            }
        }
    }

    @Test
    fun `refresh is working when jar is removed`() = runBlocking {
        val cp = db!!.classpath(listOf(guavaLibClone))
        val abstractCacheClass = cp.findClassOrNull<AbstractCache<*,*>>()
        assertNotNull(abstractCacheClass!!)
        db!!.awaitBackgroundJobs() // is required for deleting jar

        assertTrue(guavaLibClone.delete())
        assertNull(abstractCacheClass.methods.first().body())

        db!!.refresh()
        withRegistry {
            assertEquals(1, snapshots.size)
        }

        assertNotNull(cp.findClassOrNull<AbstractCache<*,*>>())
        cp.close()
        db!!.refresh()
        withRegistry {
            assertTrue(snapshots.isEmpty())
            assertEquals(db!!.javaRuntime.allLocations.size, locations.size)
        }
    }

    @Test
    fun `method body could be read from jar`() = runBlocking {
        val cp = db!!.classpath(listOf(guavaLibClone))
        val abstractCacheClass = cp.findClassOrNull<AbstractCache<*,*>>()
        assertNotNull(abstractCacheClass!!)

        assertNotNull(
            abstractCacheClass.methods.first().body()
        )
    }

    @Test
    fun `simultaneous access to method body`() = runBlocking {
        val cps = (1..10).map { db!!.classpath(listOf(guavaLibClone)) }

        suspend fun JIRClasspath.accessMethod() {
            val abstractCacheClass = findClassOrNull<AbstractCache<*,*>>()
            assertNotNull(abstractCacheClass!!)

            assertNotNull(
                abstractCacheClass.methods.first().body()
            )
        }

        BackgroundScope.launch {
            cps.map {
                async {
                    it.accessMethod()
                    it.close()
                }
            }.joinAll()
        }.join()
        db!!.refresh()

        withRegistry {
            assertTrue(snapshots.isEmpty())
        }
    }

    @Test
    fun `jar should not be blocked after method read`() = runBlocking {
        val cp = db!!.classpath(listOf(guavaLibClone))
        val clazz = cp.findClassOrNull<Iterators>()
        assertNotNull(clazz!!)
        assertNotNull(clazz.methods.first().body())
        db!!.awaitBackgroundJobs()
        assertTrue(guavaLibClone.delete())
    }

    @AfterEach
    fun cleanup() {
        tempFolder.deleteRecursively()

        runBlocking {
            db!!.awaitBackgroundJobs()
            db!!.close()
        }
        db = null
    }

    private fun withRegistry(action: PersistentLocationRegistry.() -> Unit) {
        (db!!.locationsRegistry as PersistentLocationRegistry).action()
    }
}