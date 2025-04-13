package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.JAVA_OBJECT
import org.opentaint.ir.impl.features.hierarchyExt
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@LifecycleTest
class SubclassesTest : BaseTest() {

    companion object : WithGlobalDB()

    private val withDB = WithDB()

    private val anotherDb = withDB.db
    private val anotherCp: JIRClasspath by lazy {
        runBlocking {
            anotherDb.awaitBackgroundJobs()
            anotherDb.classpath(allClasspath)
        }
    }

    @Test
    fun `Object subclasses should be the same`() {
        runBlocking {
            val hierarchy = cp.hierarchyExt()
            val anotherHierarchy = anotherCp.hierarchyExt()
            Assertions.assertEquals(
                hierarchy.findSubClasses(JAVA_OBJECT, false, includeOwn = true).count(),
                anotherHierarchy.findSubClasses(JAVA_OBJECT, false, includeOwn = true).count()
            )
        }
    }

    @AfterEach
    fun `cleanup another db`() = runBlocking {
        withDB.cleanup()
    }

}