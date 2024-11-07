package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.impl.index.hierarchyExt
import org.opentaint.ir.impl.tests.DatabaseEnvTest

@ExtendWith(CleanDB::class)
class ClassesTest : DatabaseEnvTest() {
    companion object : WithDB()

    override val cp: JIRClasspath = runBlocking { db!!.classpath(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }

}

