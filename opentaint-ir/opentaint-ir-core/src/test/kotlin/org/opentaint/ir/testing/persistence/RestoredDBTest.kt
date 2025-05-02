package org.opentaint.ir.testing.persistence

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.ext.HierarchyExtension
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.testing.LifecycleTest
import org.opentaint.ir.testing.WithRestoredDB
import org.opentaint.ir.testing.allClasspath
import org.opentaint.ir.testing.tests.DatabaseEnvTest
import org.opentaint.ir.testing.withDB

@LifecycleTest
class RestoredDBTest : DatabaseEnvTest() {

    companion object : WithRestoredDB()

    override val cp: JIRProject by lazy {
        runBlocking {
            val withDB = this@RestoredDBTest.javaClass.withDB
            withDB.db.classpath(allClasspath)
        }
    }

    override val hierarchyExt: HierarchyExtension by lazy { runBlocking { cp.hierarchyExt() } }

}

