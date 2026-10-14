package org.opentaint.dataflow.jvm.bidi.interp

import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.AbstractObjectSet
import it.unimi.dsi.fastutil.objects.ObjectIterator
import it.unimi.dsi.fastutil.objects.ObjectSet
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.SoftReferenceManager
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.jvm.graph.JApplicationGraph
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Analysis-lifetime owners of method data and the forms of each method key. */
class JIRMethodEntries(
    val graph: JApplicationGraph,
    val languageManager: JIRLanguageManager,
    val callResolver: JIRCallResolver,
    val rules: TaintRulesProvider,
    val aliasParams: JIRLocalAliasAnalysis.Params,
    val cancellation: Cancellation,
    prescanLambdas: PrescanLambdas,
    internal val formReferences: SoftReferenceManager? = null,
    internal val formStatistics: org.opentaint.dataflow.bidi.interp.FormCacheStatistics? =
        org.opentaint.dataflow.bidi.interp.FormCacheStatistics.ifEnabled(),
    run1Adjacency: Map<CommonMethod, Set<CommonMethod>>? = null,
) {
    val formOwner = org.opentaint.dataflow.bidi.interp.FormOwner()
    init {
        val installed = graph.cp.features.orEmpty()
        check(installed.any { it is LambdaAnonymousClassFeature } &&
            installed.any { it is LambdaExpressionToAnonymousClassTransformerFeature }) {
            "The linked lambda features must be installed before method forms are built"
        }
    }

    /** Own all three collection levels before any method lazily reads its call targets. */
    val prescanLambdas: PrescanLambdas = Collections.unmodifiableMap(
        LinkedHashMap<JIRMethod, Int2ObjectMap<Set<JIRMethod>>>().apply {
            for ((caller, byIndex) in prescanLambdas) {
                cancellation.checkpoint()
                val copied = Int2ObjectOpenHashMap<Set<JIRMethod>>()
                for (entry in byIndex.int2ObjectEntrySet()) {
                    cancellation.checkpoint()
                    val methods = LinkedHashSet<JIRMethod>()
                    for (method in entry.value) {
                        cancellation.checkpoint()
                        methods += method
                    }
                    copied.put(entry.intKey, Collections.unmodifiableSet(methods))
                }
                put(caller, Int2ObjectMaps.unmodifiable(DetachedEntriesMap(copied)))
            }
        })

    @Volatile private var run1Adjacency = run1Adjacency

    internal fun requireRun1CatalogTarget(caller: CommonMethod, callee: CommonMethod) {
        val graph = run1Adjacency ?: return
        check(callee in graph[caller].orEmpty()) {
            "Resolved target is absent from the completed prescan static catalog: $caller -> $callee"
        }
    }

    fun releaseRun1CatalogCoverage() { run1Adjacency = null }

    private val byMethod = ConcurrentHashMap<JIRMethod, JIRMethodEntry>()
    private val byKey = ConcurrentHashMap<MethodKey, JIRMethodForms>()

    operator fun get(key: MethodKey): JIRMethodEntry = get(key.method as JIRMethod)
    operator fun get(method: JIRMethod): JIRMethodEntry =
        byMethod.computeIfAbsent(method) { JIRMethodEntry(it, this) }

    /** Both directions keep the same key owner; the method runner owns its form slots. */
    fun forms(interp: JIRInterpreter, key: MethodKey): JIRMethodForms =
        byKey.computeIfAbsent(key) { JIRMethodForms(interp, get(it), it) }

    /** Fastutil's wrapper protects its set but leaves hash-map entries mutable. */
    private class DetachedEntriesMap<V>(private val owned: Int2ObjectMap<V>) : AbstractInt2ObjectMap<V>() {
        override val size: Int get() = owned.size
        override fun get(key: Int): V? = owned[key]
        override fun containsKey(key: Int): Boolean = owned.containsKey(key)

        private val detached = object : AbstractObjectSet<Int2ObjectMap.Entry<V>>() {
            override val size: Int get() = owned.size

            override fun iterator(): ObjectIterator<Int2ObjectMap.Entry<V>> {
                val source = owned.int2ObjectEntrySet().iterator()
                return object : ObjectIterator<Int2ObjectMap.Entry<V>> {
                    override fun hasNext(): Boolean = source.hasNext()
                    override fun remove(): Nothing = throw UnsupportedOperationException("Immutable lambda entries")
                    override fun next(): Int2ObjectMap.Entry<V> {
                        val entry = source.next()
                        return AbstractInt2ObjectMap.BasicEntry(entry.intKey, entry.value)
                    }
                }
            }
        }

        override fun int2ObjectEntrySet(): ObjectSet<Int2ObjectMap.Entry<V>> = detached
    }
}
