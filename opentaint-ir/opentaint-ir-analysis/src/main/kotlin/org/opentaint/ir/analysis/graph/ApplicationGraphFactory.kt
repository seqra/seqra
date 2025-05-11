@file:JvmName("ApplicationGraphFactory")

package org.opentaint.ir.analysis.graph

import kotlinx.coroutines.DelicateCoroutinesApi
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

/**
 * Async adapter for calling [newApplicationGraphForAnalysis] from Java.
 *
 * See also: [answer on StackOverflow](https://stackoverflow.com/a/52887677/3592218).
 */
@OptIn(DelicateCoroutinesApi::class)
fun JIRClasspath.newApplicationGraphForAnalysisAsync(
    bannedPackagePrefixes: List<String>? = null,
): CompletableFuture<JIRApplicationGraph> =
    GlobalScope.future {
        newApplicationGraphForAnalysis(bannedPackagePrefixes)
    }

val defaultBannedPackagePrefixes: List<String> = listOf(
    "kotlin.",
    "java.",
    "jdk.internal.",
    "sun.",
    "com.sun.",
    "javax.",
)
