package org.opentaint.ir.testing.persistence

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.JIRSettings
import org.opentaint.ir.impl.features.Builders
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.opentaint-ir
import org.opentaint.ir.impl.storage.LocationState
import org.opentaint.ir.impl.storage.SQLitePersistenceImpl
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.opentaint.ir.testing.allClasspath
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class IncompleteDataTest {

    private val jdbcLocation = Files.createTempFile("jIRdb-", null).toFile().absolutePath
    private lateinit var javaHome: File

    @BeforeEach
    fun setupDB() {
        javaHome = JIRSettings().useProcessJavaRuntime().jre
        runBlocking {
            newDB(true).also {
                it.close()
            }
        }
    }

    @Test
    fun `if runtime is not processed schema should be dropped`() {
        withPersistence { jooq ->
            jooq.update(BYTECODELOCATIONS)
                .set(BYTECODELOCATIONS.STATE, LocationState.AWAITING_INDEXING.ordinal)
                .execute()
        }
        val db = newDB(true)
        db.persistence.read {
            val count = it.fetchCount(
                BYTECODELOCATIONS,
                BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal)
            )
            assertEquals(0, count)
        }
    }

    @Test
    fun `if runtime is processed unprocessed libraries should be outdated`() {
        val ids = arrayListOf<Long>()
        withPersistence { jooq ->
            jooq.update(BYTECODELOCATIONS)
                .set(BYTECODELOCATIONS.STATE, LocationState.AWAITING_INDEXING.ordinal)
                .where(BYTECODELOCATIONS.RUNTIME.isFalse)
                .execute()
            jooq.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.RUNTIME.isFalse)
                .fetch {
                    ids.add(it.id!!)
                }
        }
        val db = newDB(true)
        db.persistence.read {
            it.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal))
                .fetch {
                    assertTrue(
                        ids.contains(it.id!!),
                        "expected ${it.path} to be in PROCESSED state buy is in ${LocationState.values()[it.state!!]}"
                    )

                }
        }
    }

    private fun withPersistence(action: (DSLContext) -> Unit) {
        val persistence = SQLitePersistenceImpl(
            JavaRuntime(javaHome), FeaturesRegistry(emptyList()), jdbcLocation, false
        )
        persistence.use {
            it.write {
                action(it)
            }
        }
    }

    private fun newDB(awaitBackground: Boolean) = runBlocking {
        opentaint-ir {
            useProcessJavaRuntime()
            persistent(jdbcLocation)
            installFeatures(Usages, Builders)
            loadByteCode(allClasspath)
        }.also {
            if (awaitBackground) {
                it.awaitBackgroundJobs()
            }
        }
    }

}