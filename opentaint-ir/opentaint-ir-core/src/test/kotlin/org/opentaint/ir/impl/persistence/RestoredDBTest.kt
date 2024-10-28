package org.opentaint.ir.impl.persistence

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.impl.LibrariesMixin
import org.opentaint.ir.impl.index.hierarchyExt
import org.opentaint.ir.impl.tests.DatabaseEnvTest
import org.opentaint.ir.jirdb
import java.nio.file.Files

class RestoredDBTest : DatabaseEnvTest() {

    companion object : LibrariesMixin {

        private val jdbcLocation = Files.createTempFile("jirdb-", null).toFile().absolutePath

        var tempDb: JIRDB? = newDB()

        var db: JIRDB? = newDB {
            tempDb?.close()
            tempDb = null
        }

        private fun newDB(before: () -> Unit = {}): JIRDB {
            before()
            return runBlocking {
                jirdb {
                    persistent {
                        location = jdbcLocation
                    }
                    predefinedDirOrJars = allClasspath
                    useProcessJavaRuntime()
                }.also {
                    it.awaitBackgroundJobs()
                }
            }
        }


        @AfterAll
        @JvmStatic
        fun cleanup() {
            runBlocking {
                db?.awaitBackgroundJobs()
            }
            db?.close()
            db = null
        }
    }

    override val cp = runBlocking { db!!.classpath(allClasspath) }
    override val hierarchyExt: HierarchyExtension
        get() = cp.hierarchyExt


}

