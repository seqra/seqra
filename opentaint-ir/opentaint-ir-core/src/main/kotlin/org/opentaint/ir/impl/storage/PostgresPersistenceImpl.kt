package org.opentaint.opentaint-ir.impl.storage

import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.opentaint.opentaint-ir.impl.FeaturesRegistry
import org.opentaint.opentaint-ir.impl.fs.JavaRuntime

class PostgresPersistenceImpl(
    javaRuntime: JavaRuntime,
    featuresRegistry: FeaturesRegistry,
    jIRdbUrl: String? = null,
    private val clearOnStart: Boolean
) : AbstractJIRDatabasePersistenceImpl(javaRuntime, featuresRegistry, clearOnStart) {

    override val jooq: DSLContext
    private val dataSource: HikariDataSource

    init {
        dataSource = HikariDataSource().also {
            it.maximumPoolSize = 80
            it.transactionIsolation = "TRANSACTION_READ_COMMITTED"
            it.jdbcUrl = jIRdbUrl
        }
        jooq = DSL.using(dataSource, SQLDialect.POSTGRES, Settings().withExecuteLogging(false))
        write {
            if (clearOnStart) {
                jooq.executeQueriesFrom("postgres/drop-schema.sql")
            }
            jooq.executeQueriesFrom("postgres/create-schema.sql")
        }
    }

    override fun close() {
        dataSource.close()
    }

    override fun createIndexes() {
        write {
            jooq.executeQueriesFrom("postgres/create-constraint-function.sql", asSingle = true)
            jooq.executeQueriesFrom("postgres/add-indexes.sql")
        }
    }

    override fun getScript(name: String): String {
        return javaClass.classLoader.getResourceAsStream("postgres/$name")?.reader()?.readText()
            ?: throw IllegalStateException("no sql script for postgres/$name found")
    }

}