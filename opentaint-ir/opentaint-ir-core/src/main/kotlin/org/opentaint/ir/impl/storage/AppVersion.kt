package org.opentaint.ir.impl.storage

import mu.KLogging
import org.opentaint.ir.api.storage.StorageContext
import org.opentaint.ir.impl.storage.jooq.tables.references.APPLICATIONMETADATA
import java.util.*

data class AppVersion(val version: String) : Comparable<AppVersion> {

    companion object : KLogging() {

        val currentAppVersion = current()
        private val defaultVersion = AppVersion("0.0.0")

        fun read(context: StorageContext): AppVersion {
            return try {
                val appVersion = context.execute(
                    sqlAction = {
                        context.dslContext.selectFrom(APPLICATIONMETADATA).fetch().firstOrNull()
                    },
                    noSqlAction = {
                        context.txn.all("ApplicationMetadata").firstOrNull()?.let { it["version"] }
                    }
                )
                appVersion?.run {
                    logger.info("Restored app version is $version")
                    parse(appVersion.version!!)
                } ?: currentAppVersion
            } catch (e: Exception) {
                logger.info("fail to restore app version. Use [$defaultVersion] as fallback")
                defaultVersion
            }
        }

        private fun current(): AppVersion {
            val clazz = AppVersion::class.java
            val pack = clazz.`package`
            val version = pack.implementationVersion ?: Properties().also {
                it.load(clazz.getResourceAsStream("/opentaint-ir.properties"))
            }.getProperty("opentaint.ir.version")
            return parse(version)
        }

        private fun parse(version: String): AppVersion {
            return AppVersion(version)
        }
    }

    fun write(context: StorageContext) {
        context.execute(
            sqlAction = {
                val jooq = context.dslContext
                jooq.deleteFrom(APPLICATIONMETADATA).execute()
                jooq.insertInto(APPLICATIONMETADATA)
                    .set(APPLICATIONMETADATA.VERSION, version)
                    .execute()
            },
            noSqlAction = {
                val txn = context.txn
                val metadata = txn.all("ApplicationMetadata").firstOrNull()
                    ?: context.txn.newEntity("ApplicationMetadata")
                metadata["version"] = version
            }
        )
    }

    override fun compareTo(other: AppVersion): Int {
        return version.compareTo(other.version)
    }

    override fun toString(): String {
        return "[$version]"
    }
}