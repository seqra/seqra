package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.impl.features.hierarchyExt
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@LifecycleTest
abstract class SubClassesTest : BaseTest() {

    companion object : WithGlobalDB()

    protected abstract val withDB: JIRDatabaseHolder

    private val anotherDb: JIRDatabase get() = withDB.db
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

class SubClassesSqlTest : SubClassesTest() {

    override val withDB: JIRDatabaseHolder = WithDB()
}

class SubClassesRAMTest : SubClassesTest() {

    override val withDB: JIRDatabaseHolder = WithRAMDB()
}
