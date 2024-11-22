
package org.opentaint.ir.impl.persistence

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.impl.WithRestoredDB
import org.opentaint.ir.impl.allClasspath
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.impl.tests.DatabaseEnvTest
import org.opentaint.ir.impl.withDB

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

