package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.testing.tests.DatabaseEnvTest
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(CleanDB::class)
class ClassesTest : DatabaseEnvTest() {

    companion object : WithDB()

    override val cp: JIRClasspath = runBlocking { db.classpath(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }

}

