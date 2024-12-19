package org.opentaint.ir.testing.persistence

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.testing.WithRestoredDB
import org.opentaint.ir.testing.allClasspath
import org.opentaint.ir.testing.tests.DatabaseEnvTest
import org.opentaint.ir.testing.withDB

class RestoredDBTest : DatabaseEnvTest() {

    companion object : WithRestoredDB()

    override val cp: JIRClasspath
        get() = runBlocking {
            val withDB = this@RestoredDBTest.javaClass.withDB
            withDB.db.classpath(allClasspath)
        }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }

}

