package org.opentaint.ir.impl.storage

import com.zaxxer.hikari.HikariDataSource
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.fs.JavaRuntime
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL

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
            it.maximumPoolSize = 20
            it.jdbcUrl = jIRdbUrl
        }
        jooq = DSL.using(dataSource, SQLDialect.POSTGRES, Settings().withExecuteLogging(false))
        write {
            if (clearOnStart) {
                jooq.executeQueries(getScript("drop-schema.sql"))
            }
            jooq.executeQueries(getScript("create-schema.sql"))
        }
    }

    override fun close() {
        super.close()
        dataSource.close()
    }

    override fun <T> write(action: (DSLContext) -> T): T {
        return action(jooq)
    }

    override fun createIndexes() {
        jooq.executeQueries("create-constraint-function.sql", asSingle = true)
        jooq.executeQueries("add-indexes.sql")
    }

    override fun getScript(name: String): String {
        return javaClass.classLoader.getResourceAsStream("postgres/$name")?.reader()?.readText()
            ?: throw IllegalStateException("no sql script for postgres/$name found")
    }

}