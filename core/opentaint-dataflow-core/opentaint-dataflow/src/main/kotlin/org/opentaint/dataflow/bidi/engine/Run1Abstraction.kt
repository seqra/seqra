package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.*

/** Run-1 emission retains checked answer shapes, never suppliers or caller histories. */
internal class Run1Abstraction(
    private val ops: ApOps,
    private val profile: Run1StaticProfile?,
    private val answer: (InitialAp) -> Unit,
    private val publish: (QuestionDelta) -> Unit,
    private val statistics: RequestBatchStatistics?,
    private val policyStatistics: Run1AbstractionStatistics?,
) {
    private val questions = LinkedHashMap<AccessPathBase, LinkedHashMap<MarkQuestionKey, RequestMarkSet>>()
    private val pending = LinkedHashMap<MarkQuestionKey, LinkedHashMap<RequestAnswerShape, RequestMarkSetBuilder>>()
    private val publications = LinkedHashMap<MarkQuestionKey, RequestMarkSetBuilder>()
    private val answers = LinkedHashSet<InitialAp>()
    private var released = false
    private var flushing = false
    private var policyCount = 0L
    private var policyMembers = 0L
    private var pendingMembers = 0L
    private var pendingShapes = 0L
    private var pendingCapacity = 0L
    private var firstPendingAt = 0L

    val hasPending: Boolean get() = publications.isNotEmpty() || answers.isNotEmpty()
    internal val pendingAnswerShapeCount: Long get() = pendingShapes
    internal val pendingAnswerMarkCount: Long get() = pendingMembers

    private fun known(key: MarkQuestionKey) {
        questions.getOrPut(key.initial.base) { LinkedHashMap() }.putIfAbsent(key, RequestMarkSet.EMPTY)
    }

    /** The root is unconditional. Structural static positions and concrete answers are additive. */
    fun emit(ref: CallerRef, input: Facts) {
        check(!released)
        ops.manager.cancellation.checkpoint()
        val root = ops.policy(input)
        answer(root)
        if (input is Reach) return
        known(MarkQuestionKey.dynamic(root))
        val selectedCatalog = LinkedHashSet<MarkQuestionKey>()
        if (input.base == STATIC && profile != null) {
            val structural = StaticSupplierCursor(input, profile, ops.manager, positions = true)
            while (!structural.isComplete) structural.step(256, position = { path ->
                policyStatistics?.structural()
                val initial = ops.manager.initial(STATIC, ops.manager.path(path), Tail.STAR,
                    ExclusionSet.Empty, MarkSlot.STAR)
                // Starting a method can register more questions. Do it before selecting the rows below.
                answer(initial)
                known(MarkQuestionKey.dynamic(initial))
            }, interest = { error("A structural cursor cannot return a mark question") })
            val catalog = StaticSupplierCursor(input, profile, ops.manager, positions = false)
            while (!catalog.isComplete) catalog.step(256,
                position = { error("A catalog cursor cannot return a structural slot") }, interest = { row ->
                    val initial = ops.manager.initial(STATIC, ops.manager.path(row.slot), Tail.STAR,
                        ExclusionSet.Empty, MarkSlot.STAR)
                    val key = MarkQuestionKey(initial, row.pattern, MarkQuestionRole.valueOf(row.role.name))
                    selectedCatalog.add(key)
                    requireSelection(key, row.marks)
                })
            if (profile.cleanerMarkCount != 0) {
                val key = MarkQuestionKey(root, root.toPattern(), MarkQuestionRole.CLEANER)
                selectedCatalog.add(key)
                requireSelection(key, profile.cleanerMarks)
            }
        }
        // FLOW matching belongs to caller-owned N2. Its fresh input and question replay covers
        // both orders without retaining its origin or its payload at this emitter.
        if (input !is TaintTree) return
        val counts = statistics?.let { RequestBatchStatistics.Counts() }
        for ((key, active) in questions[input.base].orEmpty()) {
            ops.manager.cancellation.checkpoint()
            // The existing static profile directory already selects catalog candidates for this
            // supplier. Dynamic questions remain active even for a new wide ancestor supplier.
            if (key.role != MarkQuestionRole.DYNAMIC && key !in selectedCatalog) continue
            val cursor = RequestGroupCursor(ops.manager, key.initial, AllConcreteMarks, input, ref, key.pattern)
            if (counts != null) counts.groupGeometryTests++
            while (!cursor.isComplete) cursor.stepShapes(256, onAnswer = { shape, mark ->
                if (mark in active) {
                    queueAnswer(shape.initial(ops.manager, mark))
                    if (counts != null) counts.answers++
                } else {
                    val rows = pending.getOrPut(key) { LinkedHashMap() }
                    val marks = rows.getOrPut(shape) {
                        pendingShapes++
                        policyStatistics?.memoChange(1, 0)
                        RequestMarkSetBuilder()
                    }
                    val oldCapacity = marks.primitiveCapacity
                    if (marks.add(mark)) {
                        pendingMembers++
                        policyStatistics?.memoChange(0, 1)
                    }
                    pendingCapacity += marks.primitiveCapacity - oldCapacity
                }
            }, onClimb = { error("A concrete supplier cannot climb") })
            if (counts != null) {
                counts.groupGeometrySteps += cursor.geometryTests
                counts.memberScans += cursor.memberScans
            }
        }
        if (counts != null) statistics?.record(counts)
        observePending()
    }

    fun requireMark(initial: InitialAp, mark: TaintMark) = requireMarks(initial, RequestMarkSet.of(mark))

    fun requireMarks(initial: InitialAp, marks: RequestMarkSet) {
        val normalized = if (initial.exclusion == ExclusionSet.Empty) initial else
            ops.manager.initial(initial.base, initial.path, initial.tail, ExclusionSet.Empty, initial.mark)
        requireSelection(MarkQuestionKey.dynamic(normalized), marks)
    }

    private fun requireSelection(key: MarkQuestionKey, marks: RequestMarkSelection) {
        check(!released)
        ops.manager.cancellation.checkpoint()
        val initial = key.initial
        check(initial.tail == Tail.STAR && initial.exclusion == ExclusionSet.Empty && initial.mark == MarkSlot.STAR) {
            "A run-1 mark question needs an abstract initial with an empty exclusion"
        }
        known(key)
        val directory = checkNotNull(questions[initial.base])
        val old = checkNotNull(directory[key])
        val fresh = RequestMarkSetBuilder()
        val cursor = marks.cursor()
        var duplicates = 0
        while (!cursor.isComplete) cursor.step(256) { mark ->
            ops.manager.cancellation.checkpoint()
            if (mark !in old) fresh.add(mark) else duplicates++
        }
        val delta = fresh.takeSnapshot()
        policyStatistics?.admission(old.isEmpty && !delta.isEmpty, delta.size, duplicates)
        if (delta.isEmpty) return
        if (old.isEmpty) policyCount++
        policyMembers += delta.size
        directory[key] = RequestMarkSetBuilder().apply { addAll(old); addAll(delta) }.takeSnapshot()
        val rows = pending[key]
        if (rows != null) {
            val iterator = rows.entries.iterator()
            while (iterator.hasNext()) {
                ops.manager.cancellation.checkpoint()
                val (shape, stored) = iterator.next()
                delta.forEachMark { mark ->
                    ops.manager.cancellation.checkpoint()
                    val oldCapacity = stored.primitiveCapacity
                    if (stored.remove(mark)) {
                        pendingMembers--
                        policyStatistics?.memoChange(0, -1, consumed = 1)
                        queueAnswer(shape.initial(ops.manager, mark))
                    }
                    pendingCapacity += stored.primitiveCapacity - oldCapacity
                }
                if (stored.isEmpty) {
                    pendingCapacity -= stored.primitiveCapacity
                    iterator.remove()
                    pendingShapes--
                    policyStatistics?.memoChange(-1, 0)
                }
            }
            if (rows.isEmpty()) pending.remove(key)
        }
        publications.getOrPut(key) { RequestMarkSetBuilder() }.addAll(delta)
        markPendingTime()
        observePending()
    }

    private fun queueAnswer(initial: InitialAp) {
        answers.add(initial)
        markPendingTime()
    }

    /** Only projected answers and question deltas survive to this method service point. */
    fun flush(quantum: Int): Boolean {
        require(quantum > 0)
        check(!released && !flushing)
        flushing = true
        val counts = statistics?.let { RequestBatchStatistics.Counts() }
        try {
            if (firstPendingAt != 0L) statistics?.serviceDelay(System.nanoTime() - firstPendingAt)
            var remaining = quantum
            while (remaining-- > 0 && hasPending) {
                ops.manager.cancellation.checkpoint()
                if (answers.isNotEmpty()) {
                    val iterator = answers.iterator()
                    val initial = iterator.next()
                    iterator.remove()
                    answer(initial)
                } else {
                    val iterator = publications.entries.iterator()
                    val (key, marks) = iterator.next()
                    iterator.remove()
                    publish(QuestionDelta(key, marks.takeSnapshot()))
                    if (counts != null) counts.phases++
                }
            }
            if (hasPending && counts != null) counts.continuations++
            if (!hasPending) firstPendingAt = 0L
            return !hasPending
        } finally {
            flushing = false
            if (counts != null) statistics?.record(counts)
            observePending()
        }
    }

    private fun markPendingTime() {
        if (firstPendingAt == 0L) firstPendingAt = System.nanoTime()
    }

    private fun observePending() {
        // The old raw-delta and captured-root gauges are deliberately zero after removal.
        statistics?.pending(pendingMembers, pendingCapacity, 0, 0, 0)
    }

    fun release() {
        if (released) return
        released = true
        questions.clear()
        pending.clear()
        publications.clear()
        answers.clear()
        policyStatistics?.memoChange(-pendingShapes, -pendingMembers)
        pendingMembers = 0
        pendingShapes = 0
        pendingCapacity = 0
        policyStatistics?.release(policyCount, policyMembers)
        policyCount = 0
        policyMembers = 0
    }

    private object AllConcreteMarks : RequestMarkSelection {
        override fun containsId(id: Int): Boolean = true
        override fun cursor(): RequestMarkCursor = error("Only TAINT leaves can enumerate the supplier's marks")
    }
}
