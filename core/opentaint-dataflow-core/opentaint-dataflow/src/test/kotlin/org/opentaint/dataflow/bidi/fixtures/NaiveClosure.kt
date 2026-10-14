package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.ArrayDeque

/** Exact per-path closure. No production fact store, candidate index or subsumption is used. */
class NaiveClosure(private val program: FiniteProgramFixtures, private val config: RunConfig) {
    private val inputs = program.inputs(config)
    private val forward = config.direction == Direction.FORWARD
    private val zero = setOf(ZERO_PATTERN)
    private val work = ArrayDeque<() -> Unit>()
    private val processed = LinkedHashSet<ObservedEdge>()
    private val initials = LinkedHashSet<ObservedInitial>()
    private val rawSummaries = LinkedHashSet<ObservedEdge>()
    private val publications = LinkedHashSet<ObservedEdge>()
    private val selectedDemand = LinkedHashSet<ObservedEdge>()
    private val links = LinkedHashSet<ObservedLink>()
    private val requests = LinkedHashSet<ObservedRequest>()
    private val sourceHits = LinkedHashSet<ObservedSourceHit>()
    private val witnesses = LinkedHashSet<ObservedWitness>()
    private val subscriptions = LinkedHashSet<Part>()
    private val joined = HashSet<Joined>()
    private val literalInputs = HashMap<LiteralKey, MutableList<LinkedHashSet<LiteralInput>>>()
    private val literalCombinations = HashSet<LiteralCombination>()
    private val triggered = HashSet<Triple<MethodKey, CommonInst, Any>>()
    private val predicateSupports = RawPredicateSupports(program.cancellation)
    private val algebras = HashMap<MethodKey, ReferenceAlgebra>()
    private val references = HashMap<MethodKey, FormsReference>()
    private var invoked = false

    private data class Part(
        val callee: MethodKey,
        val caller: MethodKey,
        val premise: Set<Pattern>,
        val callerLayer: Layer,
        val call: CommonInst,
        val added: Conclusion,
        val zeroOnly: Boolean = false,
    )
    private data class Joined(val publication: ObservedEdge, val call: CommonInst, val parts: List<Part>)
    private data class LiteralKey(val method: MethodKey, val node: CommonInst, val rule: Any)
    private data class LiteralInput(val premise: Set<Pattern>, val conclusion: Conclusion)
    private data class LiteralCombination(val key: LiteralKey, val values: List<LiteralInput>)
    private data class Checks(val fired: List<Pair<SinkRule, Layer>>, val parts: List<Conclusion>)

    fun run(): RunObservation {
        check(!invoked) { "An oracle instance has one closure" }
        invoked = true
        for (root in inputs.roots) if (program.isKnown(root)) addInitial(root, ZERO_PATTERN)
        while (work.isNotEmpty()) {
            program.manager.cancellation.checkpoint()
            work.removeFirst().invoke()
        }
        return RunObservation(processed = processed.toSet(), initials = initials.toSet(),
            rawSummaries = rawSummaries.toSet(), publications = publications.toSet(),
            selectedDemand = selectedDemand.toSet(), links = links.toSet(), requests = requests.toSet(),
            sourceHits = sourceHits.toSet(), witnesses = witnesses.toSet())
    }

    private fun algebra(method: MethodKey): ReferenceAlgebra = algebras.getOrPut(method) {
        ReferenceAlgebra(config.mode,
            request = { premise, request -> raise(method, premise, request) },
            allowsSource = { node, edge -> !config.seededSources || inputs.seeds.any { seed ->
                seed is Seed.Source && seed.method == method && seed.statement == node && seed.edge == edge.forward
            } },
            sourceHit = { node, edge -> if (!forward) sourceHits += ObservedSourceHit(method, node, edge.forward) },
            manager = program.manager,
            conjunction = { edge, premise, conclusion, node, sink ->
                check(forward) { "Backward forms have independent conjunction branches" }
                for ((index, literal) in edge.literals.withIndex()) {
                    program.manager.cancellation.checkpoint()
                    when (val checked = checkLiteral(method, premise, conclusion, literal)) {
                        null -> Unit
                        else -> for (combination in literal(method, node, edge, edge.literals.size, index,
                            LiteralInput(premise, checked))) {
                            val pr = union(combination.map { it.premise })
                            val demand = combination.any { it.conclusion.demand }
                            sink(pr, normalize(edge.target, ExclusionSet.Empty, demand))
                        }
                    }
                }
            })
    }

    private fun reference(method: MethodKey): FormsReference = references.getOrPut(method) {
        FormsReference(program.ops, algebra(method))
    }

    private fun addInitial(method: MethodKey, initial: Pattern) {
        if (!program.isKnown(method) || !initials.add(ObservedInitial(method, initial))) return
        check(initial.fact.tail != Tail.ANY_TAINT || (config.restricted && forward))
        work += {
            val value = startFact(initial)
            val model = program.programMethod(method)
            val nodes = if (forward) listOf(method.statement) else buildList {
                add(program.inst(method, model.normalExit))
                if (initial.fact.base == AccessPathBase.Zero)
                    model.exceptionalExit?.let { add(program.inst(method, it)) }
            }
            for (node in nodes) {
                program.manager.cancellation.checkpoint()
                val forms = program.forms(method)
                val rules = if (forward) forms.entryRules(Direction.FORWARD)
                    else forms.exitRules(Direction.BACKWARD, node).rules
                val seeds = if (!forward && initial.fact.base == AccessPathBase.Zero) seedFacts(method, node) else emptyList()
                for (item in boundary(method, node, rules, listOf(setOf(initial) to value) + seeds))
                    putEdge(method, node, item.first, item.second)
            }
        }
    }

    private fun putEdge(method: MethodKey, node: CommonInst, premise: Set<Pattern>, conclusion: Conclusion) {
        val edge = ObservedEdge(method, premise, conclusion, node)
        if (processed.add(edge)) work += { process(edge) }
    }

    private fun process(edge: ObservedEdge) {
        val node = checkNotNull(edge.node)
        val call = program.callExpression(edge.method, node.location.index)
        if (call != null) {
            val plan = program.forms(edge.method).call(config.direction, node, call)
            when {
                edge.conclusion.fact.base == AccessPathBase.Zero -> {
                    emitAfter(edge.method, node, edge.premise, edge.conclusion)
                    if (isZero(edge.premise)) {
                        if (forward) walk(edge.method, node, plan,
                            listOf(PlanItem(edge.premise, edge.conclusion, null)), layer(edge.conclusion))
                        else {
                            val seeds = seedFacts(edge.method, node).map { PlanItem(it.first, it.second, null) }
                            if (seeds.isNotEmpty()) walk(edge.method, node, plan, seeds,
                                layer(edge.conclusion), from = CallPoint.BOUND)
                            for (callee in calleeStage(plan)?.callees.orEmpty()) {
                                subscribe(Part(callee, edge.method, edge.premise, layer(edge.conclusion),
                                    node, edge.conclusion, zeroOnly = true))
                                addInitial(callee, ZERO_PATTERN)
                            }
                        }
                    } else check(!forward) { "A concrete-premise REACH edge belongs to a backward run" }
                }
                edge.conclusion.fact.base !in plan.touched ->
                    emitAfter(edge.method, node, edge.premise, edge.conclusion)
                else -> walk(edge.method, node, plan, listOf(PlanItem(edge.premise, edge.conclusion, null)),
                    layer(edge.conclusion))
            }
            return
        }
        reference(edge.method).applier.statement(program.forms(edge.method).statement(config.direction, node),
            edge.premise, edge.conclusion, Place(node, config.run1, sources = true),
            sink = { pr, conclusion -> emitAfter(edge.method, node, pr, limit(conclusion, config.fieldLimit)) },
            untouched = { pr, conclusion -> emitAfter(edge.method, node, pr, conclusion) })
    }

    private fun emitAfter(method: MethodKey, node: CommonInst, premise: Set<Pattern>, conclusion: Conclusion) {
        val model = program.programMethod(method)
        val summaryEnd = if (forward) node.location.index == model.normalExit else node == method.statement
        val exceptional = forward && node.location.index == model.exceptionalExit
        if (summaryEnd || exceptional) {
            val exit = if (forward) program.forms(method).exitRules(Direction.FORWARD, node)
                else ExitRules(program.forms(method).entryRules(Direction.BACKWARD), false, emptySet())
            boundary(method, node, exit.rules, listOf(premise to conclusion)) { pr, value, checks ->
                if (summaryEnd && program.interpreter.isSummaryBase(value.fact.base)) {
                    val dropStatic = exit.globalStateDrop && isZero(pr) && value.fact.base == STATIC && value in checks.parts
                    val mark = (value.fact.mark as? MarkSlot.Concrete)?.mark
                    val removeMark = isZero(pr) && mark != null &&
                        (value.fact.base == AccessPathBase.This || value.fact.base is AccessPathBase.Argument) &&
                        mark in exit.entryMarks
                    if (!dropStatic && !removeMark) summary(method, pr, value)
                }
            }
        }
        val next = if (forward) model.successors[node.location.index] else
            model.successors.indices.filter { node.location.index in model.successors[it] }
        for (index in next) putEdge(method, program.inst(method, index), premise, conclusion)
    }

    private fun boundary(method: MethodKey, node: CommonInst, rules: RuleStatement,
                         startInputs: List<Pair<Set<Pattern>, Conclusion>>,
                         after: (Set<Pattern>, Conclusion, Checks) -> Unit = { _, _, _ -> }): List<Pair<Set<Pattern>, Conclusion>> {
        val items = ArrayList<Pair<Set<Pattern>, Conclusion>>()
        val seen = HashSet<Pair<Set<Pattern>, Conclusion>>()
        val add: ReferenceSink = { pr, value ->
            val item = pr to limit(value, config.fieldLimit)
            if (seen.add(item)) items += item
        }
        val applier = reference(method).applier
        fun applyInput(pr: Set<Pattern>, value: Conclusion) =
            applier.statement(rules.summary, pr, value, Place(node, config.run1, sources = true), add)
        for ((pr, value) in startInputs) applyInput(pr, value)
        var index = 0
        while (index < items.size) {
            program.manager.cancellation.checkpoint()
            val (pr, value) = items[index++]
            val checks = sinks(method, node, pr, value, rules.sinks)
            for ((sink, layer) in checks.fired)
                applier.gen(sink.endFacts, zero, Conclusion(ZERO_FACT, ExclusionSet.Empty, layer == Layer.DEMAND), add)
            if (!forward) for (sink in rules.sinks) {
                val ends = rules.endFacts.edges.filter { edge -> sink.endFacts.any { it.forward == edge.forward } }
                var applied = false
                applier.gen(ends, pr, value) { p, result -> applied = true; add(p, result) }
                if (applied) for ((seedPr, seed) in triggerSeeds(method, node, sink)) applyInput(seedPr, seed)
            }
            after(pr, value, checks)
        }
        return items
    }

    private fun summary(method: MethodKey, premise: Set<Pattern>, conclusion: Conclusion) {
        val raw = ObservedEdge(method, premise, conclusion)
        if (!rawSummaries.add(raw)) return
        val selected = conclusion.demand || premise.size > 1 || (!forward && isZero(premise))
        val pieces = if (config.run1 || (!forward && isZero(premise))) setOf(conclusion) else buildSet {
            for (member in premise) for ((target, demand) in inputs.demand) if (target == method)
                restrict(member, conclusion, demand)?.let { add(it) }
        }
        for (piece in pieces) {
            val pub = ObservedEdge(method, premise, piece)
            if (selected) selectedDemand += pub
            if (publications.add(pub)) work += {
                for (part in subscriptions.toList()) if (part.callee == method) match(part, pub)
            }
        }
    }

    private fun walk(method: MethodKey, node: CommonInst, plan: CallPlan, items: List<PlanItem>,
                     callerLayer: Layer, from: CallPoint = plan.entry, pendingDemotion: Boolean = false) {
        val hooks = PlanHooks(guards = forward,
            atBound = { batch -> batch.flatMap { sinks(method, node, it.premise, it.c, plan.sinks).fired }.distinct() },
            callees = { stage, item ->
                for (callee in stage.callees) {
                    val part = Part(callee, method, item.premise, callerLayer, node, item.c)
                    subscribe(part)
                    link(part)
                }
                emptyList()
            },
            clean = { cleaner, item ->
                val result = cleanRes(cleaner, item.c)
                if (config.run1) result.request?.let { raise(method, item.premise, RequestKind.Mark(it)) }
                result.facts.map { item.copy(c = it) }
            },
            statementCut = { item -> item.copy(c = limit(item.c, config.fieldLimit)) },
            exit = { item -> item.copy(c = limit(item.c, config.fieldLimit)) },
            trigger = { sink -> triggerSeeds(method, node, sink).map { PlanItem(it.first, it.second, null) } })
        for ((point, item) in reference(method).run(plan, items, Place(node, config.run1, sources = false),
            hooks, from, pendingDemotion)) if (point == plan.exit)
            emitAfter(method, node, item.premise, item.c)
    }

    private fun link(part: Part) {
        if (!program.isKnown(part.callee)) return
        val added = Pattern(part.added.fact, part.added.exclusion)
        val link = ObservedLink(part.callee, part.caller, part.premise, part.callerLayer, part.call,
            layer(part.added), added)
        if (!links.add(link)) return
        work += {
            when {
                config.run1 -> addInitial(part.callee, policy(added))
                added.fact.base == AccessPathBase.Zero -> addInitial(part.callee, ZERO_PATTERN)
                else -> for ((method, demand) in inputs.demand) if (method == part.callee)
                    emit(demand.entry, added)?.let { addInitial(method, it) }
            }
            for (request in requests.toList()) if (request.method == part.callee) act(request, link)
        }
    }

    private fun subscribe(part: Part) {
        if (!program.isKnown(part.callee) || !subscriptions.add(part)) return
        work += {
            for (pub in publications.toList()) if (pub.method == part.callee) match(part, pub)
            if (config.restricted && !part.zeroOnly) {
                val seen = HashSet<RecordLeafView>()
                for (record in inputs.records) if (record.method == part.callee) {
                    if (record.direction == config.direction) native(part, record)
                    else reverseRecordLeaf(record.direction, record.premise, record.conclusion)?.let { view ->
                        if (seen.add(view)) view(part, view)
                    }
                }
            }
        }
    }

    private fun match(part: Part, pub: ObservedEdge) {
        if (part.zeroOnly) {
            if (isZero(pub.premise)) resume(part, listOf(PlanItem(part.premise, pub.conclusion, false)))
            return
        }
        if (pub.premise.size == 1) {
            val premise = pub.premise.single()
            if (satisfies(premise, Pattern(part.added.fact, part.added.exclusion), config.restricted))
                reduce(part, premise, pub.conclusion)?.let { resume(part, listOf(it)) }
            return
        }
        check(forward) { "Backward publications cannot be conjunctive" }
        val choices = pub.premise.map { member -> subscriptions.filter { candidate ->
            !candidate.zeroOnly && candidate.callee == pub.method && candidate.caller == part.caller &&
                candidate.call == part.call && candidate.added.fact.base != AccessPathBase.Zero &&
                satisfies(member, Pattern(candidate.added.fact, candidate.added.exclusion), config.restricted)
        } }
        for (parts in product(choices)) if (joined.add(Joined(pub, part.call, parts))) {
            val pr = union(parts.map { it.premise })
            val demand = pub.conclusion.demand || parts.any { it.added.demand }
            val result = normalize(pub.conclusion.fact, pub.conclusion.exclusion, demand)
            resume(parts.first(), listOf(PlanItem(pr, result, false)))
        }
    }

    private fun native(part: Part, record: RawRecord) {
        val added = Pattern(part.added.fact, part.added.exclusion)
        if (!applicable(record.premise, added) && !inside(record.premise, added)) return
        val pending = record.premise.fact.tail == Tail.ANY_TAINT && !inside(record.premise, added)
        val result = reduce(part, record.premise, record.conclusion) ?: return
        resume(part, listOf(if (pending) result.copy(identityCandidate = false) else result), pending)
    }

    private fun view(part: Part, view: RecordLeafView) {
        val added = Pattern(part.added.fact, part.added.exclusion)
        if (!applicable(view.premise, added) && !inside(view.premise, added)) return
        val outcome = concat(part.added, summaryEdge(view.premise, view.result), edgeDemand = view.result.demand,
            restricted = config.restricted)
        check(outcome !is EdgeOutcome.Request && outcome !is EdgeOutcome.PositionRequest)
        if (outcome is EdgeOutcome.Fact)
            resume(part, listOf(PlanItem(part.premise, outcome.conclusion, recordLeafIdentity(view))))
    }

    private fun reduce(part: Part, premise: Pattern, conclusion: Conclusion): PlanItem? {
        val outcome = concat(part.added, summaryEdge(premise, conclusion), edgeDemand = conclusion.demand,
            restricted = config.restricted)
        check(outcome !is EdgeOutcome.Request && outcome !is EdgeOutcome.PositionRequest)
        if (outcome !is EdgeOutcome.Fact) return null
        val identity = premise.fact.base != AccessPathBase.Zero && !conclusion.demand && conclusion == startFact(premise)
        return PlanItem(part.premise, outcome.conclusion, identity)
    }

    private fun resume(part: Part, items: List<PlanItem>, pendingDemotion: Boolean = false) {
        if (items.isEmpty()) return
        val expression = checkNotNull(program.callExpression(part.caller, part.call.location.index))
        val plan = program.forms(part.caller).call(config.direction, part.call, expression)
        val stage = checkNotNull(calleeStage(plan))
        walk(part.caller, part.call, plan, items, part.callerLayer, stage.to, pendingDemotion)
    }

    private fun checkLiteral(method: MethodKey, premise: Set<Pattern>, conclusion: Conclusion, literal: Pattern): Conclusion? =
        when (val checked = markCheck(premise.first(), conclusion, literal, config.restricted)) {
            CheckResult.None -> null
            is CheckResult.Request -> { raise(method, premise, RequestKind.Mark(checked.mark)); null }
            CheckResult.Holds -> conclusion.copy(demand = conjDemand(conclusion, literal))
        }

    private fun sinks(method: MethodKey, node: CommonInst, premise: Set<Pattern>, conclusion: Conclusion,
                      sinks: List<SinkRule>): Checks {
        if (!forward) return Checks(emptyList(), emptyList())
        val fired = ArrayList<Pair<SinkRule, Layer>>()
        val parts = ArrayList<Conclusion>()
        for (sink in sinks) {
            val predicate = sink.predicate
            if (predicate != null && !predicate.isTrue) {
                for ((index, pattern) in predicate.leaves.withIndex()) {
                    val checked = checkLiteral(method, premise, conclusion, pattern) ?: continue
                    parts += checked
                    for (combination in predicateSupports.add(method, node, checkNotNull(sink.occurrenceId),
                        predicate, index, premise, checked)) {
                        witnesses += ObservedWitness(sink.rule, method, node, sink.alternative, config.index,
                            combination.inputs.map { ObservedSinkEdge(it.premise, it.layer, it.facts, it.group) },
                            sink.endFacts.mapTo(LinkedHashSet()) { it.edge.to }, proof = combination.proof)
                        fired += sink to combination.layer
                    }
                }
                continue
            }
            for ((index, pattern) in sink.patterns.withIndex()) {
                val checked = checkLiteral(method, premise, conclusion, pattern) ?: continue
                parts += checked
                val input = LiteralInput(premise, checked)
                val combinations = if (!sink.conjunctive) listOf(listOf(input)) else
                    literal(method, node, sink, sink.patterns.size, index, input)
                for (combination in combinations) {
                    val effectiveLayer = if (combination.any { it.conclusion.demand }) Layer.DEMAND else Layer.NORMAL
                    witnesses += ObservedWitness(sink.rule, method, node, sink.alternative, config.index,
                        combination.map { ObservedSinkEdge(it.premise, layer(it.conclusion), setOf(it.conclusion)) },
                        sink.endFacts.mapTo(LinkedHashSet()) { it.edge.to },
                        proof = sink.occurrenceId?.let { ProofKey(it, emptyList()) })
                    fired += sink to effectiveLayer
                }
            }
        }
        return Checks(fired.distinct(), parts)
    }

    private fun literal(method: MethodKey, node: CommonInst, rule: Any, arity: Int, index: Int,
                        input: LiteralInput): List<List<LiteralInput>> {
        val key = LiteralKey(method, node, rule)
        val slots = literalInputs.getOrPut(key) { MutableList(arity) { LinkedHashSet() } }
        check(slots.size == arity)
        if (!slots[index].add(input)) return emptyList()
        return product(slots.map { it.toList() }).filter { literalCombinations.add(LiteralCombination(key, it)) }
    }

    private fun raise(method: MethodKey, premise: Set<Pattern>, kind: RequestKind) {
        check(config.run1) { "Restricted runs have no requests" }
        val initial = premise.single()
        check(initial.fact.mark == MarkSlot.STAR && initial.fact.tail == Tail.STAR && initial.exclusion == ExclusionSet.Empty)
        val request = ObservedRequest(method, initial, kind)
        if (requests.add(request)) work += {
            for (link in links.toList()) if (link.callee == method) act(request, link)
        }
    }

    private fun act(request: ObservedRequest, link: ObservedLink) {
        val query = when (val kind = request.kind) {
            is RequestKind.Mark -> request.premise
            is RequestKind.Position -> Pattern(PathFact(STATIC, kind.path.toList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
        }
        if (!overlap(query, link.added)) return
        when (val kind = request.kind) {
            is RequestKind.Mark -> when (val mark = link.added.fact.mark) {
                is MarkSlot.Concrete -> if (mark.mark == kind.mark)
                    addInitial(request.method, answer(request.premise, link.added, kind.mark))
                is MarkSlot.Star -> if (kind.mark !in mark.excluded) raise(link.caller, link.premise, kind)
            }
            is RequestKind.Position -> when {
                link.added.fact.path.startsWith(kind.path.toList()) ->
                    addInitial(request.method, Pattern(PathFact(STATIC, kind.path.toList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty))
                link.premise.singleOrNull()?.fact?.base == STATIC -> raise(link.caller, link.premise, kind)
            }
        }
    }

    private fun seedFacts(method: MethodKey, node: CommonInst): List<Pair<Set<Pattern>, Conclusion>> =
        rawSinkSeedRequirements(program, inputs, method, node)
            .map { zero to limit(normalize(it.fact, ExclusionSet.Empty, false), config.fieldLimit) }

    private fun triggerSeeds(method: MethodKey, node: CommonInst, sink: SinkRule): List<Pair<Set<Pattern>, Conclusion>> =
        if (!triggered.add(Triple(method, node, sink.joinKey))) emptyList() else sink.seedPatterns().map {
            zero to limit(normalize(it.fact, ExclusionSet.Empty, false), config.fieldLimit)
        }

    private fun union(premises: List<Set<Pattern>>): Set<Pattern> {
        val values = premises.flatten().filterTo(LinkedHashSet()) { it.fact.base != AccessPathBase.Zero }
        return if (values.isEmpty()) zero else values
    }

    private fun <T> product(slots: List<List<T>>): List<List<T>> {
        var result: List<List<T>> = listOf(emptyList())
        for (slot in slots) {
            program.manager.cancellation.checkpoint()
            result = result.flatMap { before -> slot.map { before + it } }
        }
        return result
    }

    private fun calleeStage(plan: CallPlan): CallStage.Callees? =
        plan.stages.firstNotNullOfOrNull { it as? CallStage.Callees }
    private fun isZero(premise: Set<Pattern>): Boolean = premise.size == 1 && premise.single().fact.base == AccessPathBase.Zero
    private fun layer(conclusion: Conclusion): Layer = if (conclusion.demand) Layer.DEMAND else Layer.NORMAL
}
