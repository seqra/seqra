package org.opentaint.jvm.sast.se.api

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.RegisteredLocation
import kotlin.time.Duration

interface SastSeAnalyzer<Engine, Vuln> {
    fun analyzeTraces(
        cp: JIRClasspath,
        projectLocations: Set<RegisteredLocation>,
        ifdsEngine: Engine,
        ifdsTraces: List<Vuln>,
        timeout: Duration
    ): List<Vuln>

    companion object {
        private val logger = object : KLogging() {}.logger

        fun <Engine, Vuln> createSeEngine(): SastSeAnalyzer<Engine, Vuln>? {
            val implClassName = "${SastSeAnalyzer::class.qualifiedName}Impl"
            val implClass = runCatching { Class.forName(implClassName) }
                .onFailure { logger.error(it) { "Failed to found impl" } }
                .getOrNull() ?: return null

            val instance = runCatching { implClass.getConstructor().newInstance() }
                .onFailure { logger.error(it) { "Failed to create impl instance" } }
                .getOrNull() ?: return null

            @Suppress("UNCHECKED_CAST")
            return instance as SastSeAnalyzer<Engine, Vuln>
        }
    }
}
