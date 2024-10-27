package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.impl.index.hierarchyExt
import org.opentaint.ir.impl.tests.DatabaseEnvTest
import org.opentaint.ir.jirdb

class ClassesTest : DatabaseEnvTest() {
    companion object : LibrariesMixin {
        var db: JIRDB? = runBlocking {
            jirdb {
                persistent {
                    clearOnStart = false
                }
                predefinedDirOrJars = allClasspath
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            db?.close()
            db = null
        }
    }

    override val cp: JIRClasspath = runBlocking { db!!.classpath(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = cp.hierarchyExt

}

