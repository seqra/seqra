package org.opentaint.jvm.util.types

import org.opentaint.ir.api.jvm.ByteCodeIndexer
import org.opentaint.ir.api.jvm.JcClasspath
import org.opentaint.ir.api.jvm.JcDatabase
import org.opentaint.ir.api.jvm.JcDatabasePersistence
import org.opentaint.ir.api.jvm.JcFeature
import org.opentaint.ir.api.jvm.JcSettings
import org.opentaint.ir.api.jvm.JcSignal
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.storage.StorageContext
import org.opentaint.ir.impl.fs.className
import org.opentaint.ir.impl.storage.execute
import org.objectweb.asm.tree.ClassNode
import org.opentaint.jvm.util.ApproximationPaths
import java.util.concurrent.ConcurrentHashMap

object TypeScorer

fun JcSettings.installClassScorer() {
    installFeatures(ClassScorer(TypeScorer, ::scoreClassNode))
}

typealias ScoreCache<Result> = ConcurrentHashMap<Long, Result>

class ScorerIndexer<Result : Comparable<Result>>(
    private val persistence: JcDatabasePersistence,
    private val location: RegisteredLocation,
    private val cache: ScoreCache<Result>,
    private val scorer: (RegisteredLocation, ClassNode) -> Result,
    approximationPaths: ApproximationPaths,
) : ByteCodeIndexer {
    private val interner = persistence.symbolInterner

    private val bad: Boolean = approximationPaths.presentPaths.any { it in location.path }

    override fun index(classNode: ClassNode) {
        val clazzSymbolId = interner.findOrNew(classNode.name.className)
        if (bad) {
            @Suppress("UNCHECKED_CAST")
            cache[clazzSymbolId] = Double.NEGATIVE_INFINITY as Result
        } else {
            cache[clazzSymbolId] = scorer(location, classNode)
        }
    }

    override fun flush(context: StorageContext) {
        context.execute(
            sqlAction = { error("SQL based persistence is not supported") },
            noSqlAction = {
                interner.flush(context)
            }
        )
    }

    fun getScore(name: String): Result? {
        val clazzSymbolId = interner.findOrNew(name)
        return cache[clazzSymbolId]
    }

    val allClassesSorted by lazy {
        cache.entries
            .sortedByDescending { it.value }
            .asSequence()
            .map { (id, result) -> result to persistence.findSymbolName(id) }
    }
}

class ClassScorer<Result : Comparable<Result>>(
    val key: Any,
    private val scorer: (RegisteredLocation, ClassNode) -> Result,
    private val approximationPaths: ApproximationPaths = ApproximationPaths()
) : JcFeature<Any?, Any?> {
    private val indexers = ConcurrentHashMap<Long, ScorerIndexer<Result>>()

    override fun newIndexer(jcdb: JcDatabase, location: RegisteredLocation): ByteCodeIndexer =
        indexers.getOrPut(location.id) {
            ScorerIndexer(jcdb.persistence, location, ConcurrentHashMap(), scorer, approximationPaths)
        }

    override fun onSignal(signal: JcSignal) {
    }

    override suspend fun query(classpath: JcClasspath, req: Any?): Sequence<Any?> {
        return emptySequence()
    }

    fun getScore(location: RegisteredLocation, name: String): Result? =
        indexers[location.id]?.getScore(name)

    fun sortedClasses(location: RegisteredLocation): Sequence<Pair<Result, String>> =
        indexers[location.id]?.allClassesSorted.orEmpty()
}