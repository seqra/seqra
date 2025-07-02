/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

@file:JvmName("ApplicationGraphFactory")

package org.opentaint.dataflow.jvm.graph

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.impl.features.usagesExt
import java.util.concurrent.CompletableFuture

/**
 * Creates an instance of [SimplifiedJIRApplicationGraph], see its docs for more info.
 */
suspend fun JIRClasspath.newApplicationGraphForAnalysis(
    bannedPackagePrefixes: List<String>? = null,
): JIRApplicationGraph {
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
