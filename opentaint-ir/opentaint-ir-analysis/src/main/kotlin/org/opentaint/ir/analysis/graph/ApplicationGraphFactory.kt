@file:JvmName("ApplicationGraphFactory")
package org.opentaint.ir.analysis.graph

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.impl.features.usagesExt
import java.util.concurrent.CompletableFuture

/**
 * Creates an instance of [SimplifiedJIRApplicationGraph], see its docs for more info.
 */
suspend fun JIRClasspath.newApplicationGraphForAnalysis(bannedPackagePrefixes: List<String>? = null): JIRApplicationGraph {
    val mainGraph = JIRApplicationGraphImpl(this, usagesExt())
    return if (bannedPackagePrefixes != null) {
        SimplifiedJIRApplicationGraph(mainGraph, bannedPackagePrefixes)
    } else {
        SimplifiedJIRApplicationGraph(mainGraph, defaultBannedPackagePrefixes)
    }
}

fun JIRClasspath.asyncNewApplicationGraphForAnalysis(
    bannedPackagePrefixes: List<String>? = null
): CompletableFuture<JIRApplicationGraph> {
    return GlobalScope.future {
        newApplicationGraphForAnalysis(bannedPackagePrefixes)
    }
}

val defaultBannedPackagePrefixes: List<String> = listOf(
    "kotlin.",
    "java.",
    "jdk.internal.",
    "sun.",
)