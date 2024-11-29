package org.opentaint.opentaint-ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.ext.HierarchyExtension
import org.opentaint.opentaint-ir.impl.features.hierarchyExt
import org.opentaint.opentaint-ir.impl.tests.DatabaseEnvTest

@ExtendWith(CleanDB::class)
class ClassesTest : DatabaseEnvTest() {

    companion object : WithDB()

    override val cp: JIRClasspath = runBlocking { db.classpath(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }

}

