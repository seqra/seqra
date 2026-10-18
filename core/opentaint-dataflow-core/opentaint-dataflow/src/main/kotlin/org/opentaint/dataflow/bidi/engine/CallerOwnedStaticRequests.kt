package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.ir.api.common.CommonMethod

data class StaticDemandViewKey(val callee: MethodKey, val field: Run1Slot.StaticField)

/** Run-scoped static request directory and shared callee/field unions.
 * Sources retain getters for authoritative method requests, not mark copies.
 * All external callbacks run synchronously OUTSIDE the directory lock and
 * must acquire their counted owner delivery before returning. */
class CallerOwnedStaticRequests(
    private val ap: ApManager,
    val reachability: FixedPrescanReachability,
) {
    private data class Group(val component: Int?, val unknownMethod: CommonMethod?)
    private class Source(val key: StaticNeedKey, val snapshot: () -> RequestMarkSet)
    private class View(val key: StaticDemandViewKey) {
        val marks = RequestStorage()
        val subscribers = LinkedHashMap<Any, (RequestMarkSet) -> Unit>()
    }
    private class SourceGroup(val method: CommonMethod) {
        val sources = LinkedHashMap<StaticNeedKey, Source>()
    }
    private class ViewGroup(val method: CommonMethod) {
        val views = LinkedHashMap<StaticDemandViewKey, View>()
    }
    private data class SourcesAt(val method: CommonMethod, val sources: List<Source>)
    private data class ViewsAt(val method: CommonMethod, val views: List<View>)

    private val lock = Any()
    private val sources = LinkedHashMap<StaticNeedKey, Source>()
    private val views = LinkedHashMap<StaticDemandViewKey, View>()
    private val sourceDirectory = LinkedHashMap<Run1Slot.StaticField, LinkedHashMap<Group, SourceGroup>>()
    private val viewDirectory = LinkedHashMap<Run1Slot.StaticField, LinkedHashMap<Group, ViewGroup>>()
    private var closed = false

    private fun group(method: CommonMethod): Group = reachability.component(method)?.let { Group(it, null) }
        ?: Group(null, method)

    fun containsPrescanEdge(caller: CommonMethod, callee: CommonMethod): Boolean =
        reachability.containsEdge(caller, callee)

    /** Register BEFORE a first source delta is observable. The snapshot getter
     * is invoked only after releasing our lock, avoiding SummaryStorage lock
     * inversion. A racing snapshot/delta can duplicate offers, never lose one. */
    fun registerSource(key: StaticNeedKey, snapshot: () -> RequestMarkSet): Boolean {
        ap.cancellation.checkpoint()
        val matching = synchronized(lock) {
            checkOpen()
            if (key in sources) return false
            val source = Source(key, snapshot)
            sources[key] = source
            sourceDirectory.getOrPut(key.slot, ::LinkedHashMap)
                .getOrPut(group(key.method.method)) { SourceGroup(key.method.method) }.sources[key] = source
            snapshotViews(key.slot)
        }
        val recipients = reachableViews(key.method.method, matching)
        if (recipients.isNotEmpty()) {
            val current = snapshot()
            for (view in recipients) offer(view, current)
        }
        return true
    }

    /** Marks remain authoritative in the source's SummaryStorage. This call
     * publishes only its already committed immutable delta. */
    fun publishSourceDelta(key: StaticNeedKey, fresh: RequestMarkSet) {
        ap.cancellation.checkpoint()
        if (fresh.isEmpty) return
        val matching = synchronized(lock) {
            checkOpen()
            check(key in sources) { "A static source must be registered before publishing its delta" }
            snapshotViews(key.slot)
        }
        for (view in reachableViews(key.method.method, matching)) offer(view, fresh)
    }

    /** One structural callsite subscriber per view. Its callback must post to
     * the caller owner; complete input rows remain in that caller's relation. */
    fun subscribeView(
        key: StaticDemandViewKey,
        subscriberKey: Any,
        notify: (RequestMarkSet) -> Unit,
    ): Boolean {
        ap.cancellation.checkpoint()
        val (view, previous, matching) = synchronized(lock) {
            checkOpen()
            var created = false
            val view = views.getOrPut(key) {
                created = true
                View(key).also {
                    viewDirectory.getOrPut(key.field, ::LinkedHashMap)
                        .getOrPut(group(key.callee.method)) { ViewGroup(key.callee.method) }.views[key] = it
                }
            }
            if (subscriberKey in view.subscribers) return false
            view.subscribers[subscriberKey] = notify
            Triple(view, view.marks.borrow(key.field).dynamic,
                if (created) snapshotSources(key.field) else emptyList())
        }
        // Registration precedes both old-state reads. A source update that
        // races this replay is covered by the snapshot or its delta, or both.
        if (!previous.isEmpty) notify(previous)
        for (sources in matching) {
            ap.cancellation.checkpoint()
            if (!reachability.isReachable(key.callee.method, sources.method)) continue
            for (source in sources.sources) {
                ap.cancellation.checkpoint()
                offer(view, source.snapshot())
            }
        }
        return true
    }

    private fun offer(view: View, offered: RequestMarkSet) {
        ap.cancellation.checkpoint()
        if (offered.isEmpty) return
        val (fresh, recipients) = synchronized(lock) {
            checkOpen()
            val delta = view.marks.addAll(view.key.field, offered, ap.cancellation::checkpoint)
            delta to if (delta.isEmpty) emptyList() else view.subscribers.values.toList()
        }
        if (fresh.isEmpty) return
        for (recipient in recipients) {
            ap.cancellation.checkpoint()
            recipient(fresh)
        }
    }

    private fun reachableViews(source: CommonMethod, groups: List<ViewsAt>): List<View> = buildList {
        for (group in groups) {
            ap.cancellation.checkpoint()
            if (reachability.isReachable(group.method, source)) addAll(group.views)
        }
    }

    /** Called with lock held; no source getter or receiver callback is read. */
    private fun snapshotSources(field: Run1Slot.StaticField): List<SourcesAt> =
        sourceDirectory[field]?.values?.map { SourcesAt(it.method, it.sources.values.toList()) }.orEmpty()

    private fun snapshotViews(field: Run1Slot.StaticField): List<ViewsAt> =
        viewDirectory[field]?.values?.map { ViewsAt(it.method, it.views.values.toList()) }.orEmpty()

    fun viewMarks(key: StaticDemandViewKey): RequestMarkSet = synchronized(lock) {
        views[key]?.marks?.borrow(key.field)?.dynamic ?: RequestMarkSet.EMPTY
    }

    data class Snapshot(val sources: Int, val views: Int, val subscribers: Int, val viewMarkMembers: Long)
    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(sources.size, views.size, views.values.sumOf { it.subscribers.size },
            views.values.sumOf { it.marks.dynamicMembers })
    }

    data class CacheStatistics(
        val sourceReferences: Int,
        val demandViews: Int,
        val subscriberReferences: Int,
        val viewMarkMembers: Long,
        val sourceSccGroups: Int,
        val viewSccGroups: Int,
        val prescanComponents: Int,
        val cachedReachabilityPairs: Int,
    )

    /** Counts the explicit view/reference caches without reading/copying the
     * authoritative source mark payloads from their SummaryStorage owners. */
    fun cacheStatistics(): CacheStatistics = synchronized(lock) {
        CacheStatistics(sources.size, views.size, views.values.sumOf { it.subscribers.size },
            views.values.sumOf { it.marks.dynamicMembers },
            sourceDirectory.values.sumOf { it.size }, viewDirectory.values.sumOf { it.size },
            reachability.componentCount, reachability.cachedPairs)
    }

    /** The run owner calls this only after every publisher and receiver joins. */
    fun clear() = synchronized(lock) {
        sources.clear()
        views.clear()
        sourceDirectory.clear()
        viewDirectory.clear()
        closed = true
    }

    private fun checkOpen() { check(!closed) { "The run's static request directory was released" } }
}
