package org.opentaint.ir.impl

import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.spi.CommonSPI
import org.opentaint.ir.api.spi.SPILoader
import org.opentaint.ir.impl.fs.JavaRuntime

class JIRDatabaseException(message: String) : RuntimeException(message)

/**
 * Service Provider Interface to load pluggable implementation of [JIRDatabasePersistence].
 */
interface JIRDatabasePersistenceSPI : CommonSPI {

    /**
     * Id of [JIRDatabasePersistence] which is used to select particular persistence implementation.
     * It can be an arbitrary unique string, but use of fully qualified name of the class
     * implementing [JIRDatabasePersistenceSPI] is preferable.
     */
    override val id: String

    /**
     * Creates new instance of [JIRDatabasePersistence] specified [JavaRuntime] and [JIRSettings].
     * @param runtime - Java runtime which database persistence should be created for
     * @param settings - settings to use for creation of [JIRDatabasePersistence] instance
     * @return new [JIRDatabasePersistence] instance
     */
    fun newPersistence(runtime: JavaRuntime, settings: JIRSettings): JIRDatabasePersistence

    /**
     * Creates new instance of [LocationsRegistry] and bind it to specified [JIRDatabase].
     * Implementation of [LocationsRegistry] is specific to persistence provided by this SPI.
     * [LocationsRegistry] is always being created _after_ corresponding [JIRDatabasePersistence]
     * is created.
     * @param jIRdb - [JIRDatabase] which [LocationsRegistry] is bound to
     */
    fun newLocationsRegistry(jIRdb: JIRDatabase): LocationsRegistry

    companion object : SPILoader() {

        @JvmStatic
        fun getProvider(id: String): JIRDatabasePersistenceSPI {
            return loadSPI(id) ?: throw JIRDatabaseException("No JIRDatabasePersistenceSPI found by id = $id")
        }
    }
}