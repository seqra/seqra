# Analyzer core — specification

The [run-1 field-write contract](run1-field-writes.md) overrides F1 stores,
Empty-E fact production and static defaults. Prescan selects a NORMAL/DEMAND
S.* identity edge. A reached method publishes it through the ordinary
retained/live summary path; ordinary subscriptions/application/selection and
handoff use it. The abstraction emits no S.* initial. There is no special
run-1 static call handling or default iterator. Layer demotion preserves
the full AP payload and tail; it does not convert known STAR to ANY.
The older layer-driven normal-form rules below are superseded at this seam.
No F1 summary becomes an executable later strong record.

The selected [static-handling contract](static-handling.md) takes precedence
for static planning, the run-1 initial grammar/stores/calls, the F1→B2
boundary and later strong query/response behavior. In particular, all F1
publications become guidance only, including NORMAL singleton hints; they
are not executable records. Actual later queries authorize both start and
response independently of old guidance. This is an unimplemented design
target. [Static proofs](static-handling-proofs.md) separates design arguments,
checked local laws and engine refinement obligations.

Status: design spec of the new analyzer core (phase 2 of [bidirectional-task.md](../bidirectional-task.md)). This
document is normative. It uses two other specs and does not repeat them:

* [`ap.md`](ap.md): the fact, the edge, the AP operations, the runs, the stores and their theorems;
* [`interpreter.md`](interpreter.md): the micro edges of the IR and the order of the rules.

This document defines the ENTITIES of the analyzer, who owns which data, the METHOD ANALYZER, the COMMUNICATION
PIPELINE between the methods, the scheduling and the end of a run, and the ITERATION DRIVER with the hand-offs between
the runs. Appendix A gives the analysis of today's analyzer that the design starts from.

The formal model is in [`spec/lean`](lean): `Pipeline.lean`, `PipelineProofs.lean`, `PipelineAP.lean`,
`PipelineStore.lean`, `PipelineDriver.lean`, `PipelineSeeds.lean` (with `ForwardSeeds.lean`), `PipelineNDZ.lean` (with
`NDZ.lean`, `NDZeroBase.lean`), `PipelineAnyTaintEx.lean` and `PipelineAnyTaintExDriver.lean` (with the
`AnyTaintEx*.lean` files of the tail `[any-taint]` and its exclusion, and the `AnyTaint*.lean` files that they reuse,
`ap.md` §10.11), and, for the hand-off of the demand edges only, the `Handoff*.lean` files,
`PipelineHandoffDriver.lean` and `PipelineHandoffDriverExt.lean` (`ap.md` §10.12; §7.3 to §7.8). Every theorem named here
is machine-checked. [proof-status.md](proof-status.md) explains constructive
witnesses and the axiom audit, and lists the limits of each result.

The current AP rules include F72/F74/F75/F76/F77/F78. Checked local contracts, historical
protocol proofs, and remaining current pipeline/driver obligations are listed in
[proof-status.md](proof-status.md). This spec defines the required behavior; it
does not claim a full current iteration proof.

The selected F78 [run-1 request contract](run1-requests.md) takes precedence for static planning,
request ownership, initial emission and static-root summary composition. Its constructive proof scope is
[run1-request-proofs.md](run1-request-proofs.md). It retains caller N2, removes the callee input history and
deferred-answer memo, and uses one authoritative R plus one temporary P of the same RequestStorage class.
R borrows a physically shared immutable closed precomputed base B and stores only disjoint dynamic additions D;
no seed copy or initial notification wave is implied. F78 also fixes the selected JVM run-1 limit to exactly 1
and gives the complete initial grammar, distinct from the broader measured factories/driver.
This is a normative target; measured runtime `27e22bb1ed871651143538783473cb0d4fcbcf3e` is unchanged.
Historical pipeline/confirmation proofs keep their explicit scope; ghost input histories are not runtime stores.

Language: ASD-STE100 Simplified Technical English.

---

## 0. Scope

The analyzer core:

1. runs one run (`ap.md` §6): one direction, one field limit, one mode (run 1 or restricted), to a fixed point. Only
   run 1 has requests. A restricted run analyses with the abstract mark `*` where its demand has a `*` pattern, and
   with a concrete mark only where a concrete-mark demand exists (§3; `ap-history.md` F72);
2. exchanges the edges between the methods of a run with no loss (§5);
3. detects the end of a run (§6);
4. runs the sequence of runs and computes the hand-off from each run to the next: only the DEMAND EDGES and the
   seeds of the DEMAND vulnerabilities (§1, §7.3, §7.4);
5. stops the sequence by its STOP RULES and by ONE BUDGET for the whole analysis: each run gets the rest of the budget
   (§7.1). It LOCALIZES the remaining work: after each complete run it logs the FRONTIER, the part of the program that
   the later runs still analyse (§7.8);
6. gives the report: the vulnerabilities and the end of the analysis (§7.5, §9).

The core is for the JVM. Go is out of scope.

Out of scope:

* the IR interpreter (`interpreter.md`); §4.9 gives only its interface to the core;
* the prescan, the rule reduction and the `TaintAnalyzer` wiring (phase 3); §9 gives the interface;
* the trace resolution. The core keeps no store of a run for a trace resolver (§7.6). The phase-3 output holds every
  entry of the report, CONFIRMED and DEMAND, and gives each one a simple trace (§9; `ap-history.md` F68);
* the iteration policy: the field limit of each run, and a practical STOP STRATEGY that reads the frontier log
  (`continueAfter`, §7.8). The driver takes the policy as a parameter (§7.1). The stop rules and the one budget are
  in scope (item 5);
* the localization of the ZERO FACT. The zero fact still enters every callee in every run (the backward rule `zin`,
  the forward zero demand, §4.4), and its work is in the frontier log only as a measure (§7.8, §7.8;
  `ap-history.md` F70).

### 0.1 Assumptions

| # | Assumption |
|---|---|
| A1 | AP operations and stores satisfy [ap.md](ap.md), including its explicit construction and demand guards. The interpreter satisfies I1–I14 in [interpreter.md](interpreter.md). The proof scope is [proof-status.md](proof-status.md). |
| A2 | LINEARIZABLE SHARED ACTIONS. Registration, publication/R admission, immutable-view reads and subscriber-list reads have one order consistent with thread order. R is visible before its fresh notification; registration precedes current-R/input reads. Historical summary-only models do not by themselves prove F78. P3 (§5.3) is the implementation rule. |
| A3 | RELIABLE EVENTS. An event that a thread sends to the channel of a runner arrives exactly once, unless the run is cancelled. The send happens before the receive (Kotlin `Channel`). |
| A4 | READ-ONLY INPUTS. These inputs do not change during a run: the program, the rules, the lambda resolutions, the demand store, the record store and the seeds. The driver makes them before the run starts. The start of the run publishes them to every runner. |
| A5 | A handler (the code of one event) ends. Only the end of a run needs this (§6). The no-loss theorem does not need it (§5.5). |

---

### 0.2 Current O1/O2/O3 execution contract

Source/sink compilation follows signed folding before and after static evaluation, then eligible negative
projection (`interpreter.md` §4.2). Production holds one bound NNF DAG, separate action vectors and local array
OR choices. It does not build cubes, action products or a potential proof registry. FALSE has no footprint/effect;
TRUE uses its existing placement. Unary source operations use original facts and generic AP transfers, while
multi operations use compatible canonical leaf contributions. Both classes can occur in one condition.

Actor-owned `RulePredicateStore` keeps logical node IDs and shared immutable proof/support records. Source
contributions omit unused facts; sink contributions preserve the complete matched fact group. Repeated leaves
bind one header; shared OR uses may choose different branches through separate AND operands. Preserve all
required support outcomes, effective layers and fact growth without a cap. Report/confirmation uses scoped proof
keys and ordered support headers; the E6 callee/premise/layer/call join is unchanged.

Method forms can be reclaimed. A slot holds one managed forward/reverse payload; callbacks pin it through the
application and release it on failure/cancellation. Actors, pending deliveries, witnesses and seeds must not
retain compiled payloads. Keep method/context recipes and deterministic generation keys instead. Suspended calls
reacquire their plan. The cache uses the restored managed-reference API described in `ap-impl.md` §23.7;
cleanup disables the manager and drains its concurrent queue. No serialized registration/cleanup transition
is required by that API.
Compact owned vectors/maps/indexes preserve order, mutation safety, rule footprints and cut placement.

Backward hand-off and reversed end actions visit the whole active positive predicate at each matching witnessed
context/place, including unobserved OR branches. Trigger once per full method key, statement and occurrence,
also for CONFIRMED findings; do not limit it to one proof. TRUE needs no mark seed, FALSE none, and backward has no
AND join. All existing AP exclusions and iteration limits still apply. The new emitter/question/candidate
integration requires its own gates; historical O1/O2/O3 proofs do not prove that integration.

## 1. Terms

`ap.md` §1 defines the AP terms (fact, edge, premise set, layer, link, added fact, run, demand pattern, record, request,
and others; since F72 also the FLOW FORM of a `*` pattern, the FLOW PREMISE and the SHARING of the emission). This
document adds:

| Term | Meaning |
|---|---|
| method key | `ap.md` §1: the `MethodEntryPoint` of the method (context and FORWARD entry statement). The backward run uses the same key. Its start nodes are the forward exits (§4.4). An empty method has no method key (§4.4). |
| vulnerability key | `(rule, method, statement)`: the sink rule, the METHOD of the method key (without the context) and the sink statement (§4.7). One sink statement that the analysis reaches in several contexts is ONE vulnerability. |
| sink condition/proof | One bound NNF occurrence at a place, with an observed canonical proof and support headers (`interpreter.md` §4.2). Logical keys survive rebuilds. Numbered alternatives remain only a legacy/manual representation. |
| sink witness | One reached sink edge/set with bound condition/proof scope, ordered support headers, method key and run (§4.7). A manual literal form can retain its alternative index. |
| unit | A group of methods that one runner owns (today a package; `UnitResolver`). |
| runner | The single coroutine of one unit in one run. It runs one event at a time. |
| actor | The owner of a set of objects. Each method analyzer is an actor. Its runner runs it. |
| method analyzer | The actor of one method key in one run (§4). It owns the intra-procedural edges and every RUN store of its method. |
| event | One message in the channel of a runner (§5.1). Its HANDLER is the code that the runner runs for it. |
| subscription | The existing caller N2 input relation `(full caller edge/reference, call statement, original added input and admitted header)` (`ap.md` §8.4). It remains retained; the emitter has no second copy. |
| publication | A summary edge that the callee gives to its subscribers. In a restricted run it is the result of the restriction (`ap.md` §6.4): the INTERSECTION of the summary edge with one demand pattern, in the locations AND the marks (`ap-history.md` F71): the premise lies inside `D-c` with its mark, and the mark of the conclusion meets the mark of `D-p` (§4.6). It carries its premise key with the tail of each member and the exclusion of an `[any-taint]` member (§4.1, §4.6). The hand-off reads the publications, not the summary edges before the restriction (§7.3, §7.4). |
| summary selected for demand; demand edge (hand-off) | A stored publication selected by AP §8.5 for next-run demand construction. Selection preserves the layer: a selected normal multi-premise summary remains normal. Each publication gives a pattern per premise member. Other native normal singleton summaries supply records only. Zero demand is implicit. A demand-layer read-view result in the caller does not request reanalysis of the summarized callee. |
| DEMAND vulnerability | A vulnerability key whose state in the report is DEMAND after a complete forward run (§7.5): the latest complete forward run reports it, and NO complete forward run so far confirmed it. A key that an earlier run confirmed is CONFIRMED (final), also when the latest run reports it only in the demand layer. Only the sink witnesses of the DEMAND vulnerabilities are sink seeds of the hand-off (§7.3). The backward run also fires the sink seeds of a sink whose end fact a requirement reaches, also of a CONFIRMED vulnerability (§4.5 THE TRIGGER OF AN END FACT). |
| frontier | The part of the program that the later runs still analyse after a complete run: the method keys with a non-zero initial fact in the run, and the demand edges that the run hands off, per method key (§7.8). The driver logs the frontier of each complete run (THE FRONTIER LOG, §7.8). |
| storage with subscription | Publications and authoritative run-1 slot/mark RequestStorage R, with subscribed caller runners (§5.2). It holds no caller-keyed added payloads. |
| replay | The read of existing publications for a new subscription; F78 fresh requests additionally visit existing caller N2, and fresh FLOW input reads R (§5.3). |
| notification | The send of a new publication to every subscribed runner (§5.2). |
| delivery | The event that carries a notification to one runner (§5.3). |
| quiescence | No event, running handler, worklist item, pending publication, question replay, answer/climb delivery or unfinished local frontier (§6.2). |
| barrier | The point between two runs: the first run is quiescent, and the next run has not started (§7.2). |
| hand-off | What one run gives to the next run: demand edges and seeds (`ap.md` §8, §9.2; §7.3, §7.4). Records persist separately (`ap.md` §8.7). An R1-eligible native normal singleton summary gives no callee demand. |
| complete run | A run that ended at quiescence. A run that ended by a timeout, its memory guard or an exception (every `Throwable`, §6.3) is INCOMPLETE. An incomplete run adds nothing to the report and refutes nothing (§7.5). |

---

## 2. Entities and ownership

The layout is the layout of today: a manager, one runner per unit, one analyzer per method that keeps its own edges,
and storages with subscription between the methods. The differences: a NEW engine for each run, and one driver above
the runs.

```
IterationDriver                                   lifetime: the analysis
 ├ ApManager, MethodContextCache, RecordStore, VulnerabilityStore, executor (thread pool)
 └ for each run: RunManager(RunConfig)            lifetime: one run
     ├ summaryStorage(methodKey) → SummaryStorage one per method key, made on the first access by any thread
     └ for each unit: UnitRunner                  one coroutine
         ├ MethodAnalyzerStorage → RunMethodAnalyzer (one per method key)
         └ SubscriptionManager                    caller side: the subscriptions of the unit
```

| Entity | Lifetime | Thread | Owns | From today |
|---|---|---|---|---|
| `IterationDriver` | analysis | the caller thread | the run sequence (§7) and the shared objects below | new |
| `ApManager` | analysis | any (thread-safe) | the interners of the AP (`ap.md` §7.5) | the new AP (`ap.md` §7) |
| `MethodContextCache` | analysis | the runner of the method during a run | the parts of the method context that do not depend on the run (§4.8) | split of `JIRMethodAnalysisContext` |
| `RecordStore` | analysis (PERSISTENT) | the driver writes it at a barrier; read-only during a run | the records (`ap.md` §8.7) | new |
| `VulnerabilityStore` | analysis (PERSISTENT) | any runner adds (concurrent); the driver reads it at a barrier | the sink witnesses per vulnerability key, each with its alternative, its method key and its run (§4.7) | replaces the buckets of `TaintAnalysisUnitStorage` |
| `RunManager` | one run | the caller thread and the runners | unit routing, runner spawn, the map of the `SummaryStorage`s, the in-flight counter, the run status (§6) | `TaintAnalysisUnitRunnerManager` |
| `UnitRunner` | one run | its coroutine | the event loop, its analyzers, its `SubscriptionManager` | `TaintAnalysisUnitRunner` |
| `RunMethodAnalyzer` | one run | the runner of its unit | the RUN stores of its method (§4.1) | replaces `NormalMethodAnalyzer`; `EmptyMethodAnalyzer` goes (an empty method is never analysed, §4.4) |
| `SummaryStorage` | one run | the runner of its method inserts; subscribing runners register and read | publications, authoritative run-1 RequestStorage R and subscribed runners (§5.2); no supplier history or answer memo | `SummaryEdgeStorageWithSubscribers` plus F78 R/commit |
| `SubscriptionManager` | one run | the runner of its unit | the subscriptions of the unit, per callee (§5.3) | `SummaryEdgeSubscriptionManager` (today two per runner, internal and external; one is enough) |
| `DemandStore` | one run, read-only | any | the demand patterns of the run (`ap.md` §8.6) | new |

Ownership rules:

* O1. A method analyzer is the only writer of its RUN stores. Only its runner calls it.
* O2. A `SummaryStorage` has one INSERTING thread for publications and R commits: the runner of its method. A subscribing runner only registers its
  handler and reads (§5.3). `RunManager.summaryStorage(key)` makes exactly one storage per method key and run. It
  makes it atomically, on the first access by any thread (a `computeIfAbsent` on a concurrent map). The storage can
  exist before the runner of the method exists.
* O3. A `SubscriptionManager` is local to its runner. Another thread calls only its `notify`, which reads no state of
  the manager.
* O4. During a run, the other shared objects are read-only (A4), write-only (`VulnerabilityStore`), or thread-safe by
  their own contract (`ApManager`).
* O5. After a complete run, the driver reads the stores of the run that §7.6 keeps. No runner runs then (§7.2). After
  an incomplete run, the driver reads no store of the run (§7.5).

---

## 3. The run configuration

```kotlin
/** One run of ap.md §6.6. `Direction` is the enum of ap.md §8.7. */
class RunConfig(
    val index: Int,                          // 1, 2, 3, ...; the direction is FORWARD for an odd index
    val fieldLimit: Int,                     // ap.md §4.4; selected F78 JVM run 1: exactly 1; later positive/nondecreasing
    val demand: DemandStore?,                // null only in run 1
    val records: RecordStore,                // a read-only view; run 1 reads no record
    val seeds: SeedIndex,                    // after run 1: sink seeds (backward), source seeds (forward) (§4.7)
    val roots: List<MethodKey>,              // the root methods; the same in every run
) {
    val direction: Direction get() = if (index % 2 == 1) Direction.FORWARD else Direction.BACKWARD
    val run1: Boolean get() = index == 1
    val restricted: Boolean get() = index > 1
}
```

The mode decides these rules (`ap.md` §6.1):

| Rule | Run 1 (forward) | Restricted forward run | Backward run |
|---|---|---|---|
| initial facts of an added fact | F78 normal base/field abstract initials plus the special rule-free S-root seed and actual supplied concrete answers (`ap.md` §6.2); caller reads use field ownership and the untouched frame | the emission of `ap.md` §6.3 (§4.4; F72 R2): an entry pattern `D-c` with the mark `*` gives its FLOW FORM, one initial fact for every added fact under it (SHARING); a `D-c` with a concrete mark gives `a ∩ D-c` with the mark of `a`, only for an `a` with that mark: an added fact with the mark `*` or `*∖X` under a concrete pattern emits NOTHING (the user's decision: "The added fact can't satisfy the demand"); the zero fact for the zero added fact | the same emission, with the requirement as the added fact: a `*` pattern weakens the requirement to the FLOW form of the pattern (§4.4); after the hand-off normalization no entry pattern has the mark `*∖X` (§7.3); the zero fact enters every callee (rule `zin`) |
| the marks of the facts | `*` and `*∖X` (the policy facts and their FLOW edges), and concrete marks (the answers, the zero fact, the sources) | NOT CONCRETE since F72: the edges of a FLOW premise (the mark `*`) have the marks `*` and `*∖X` (FLOW trees, `ap.md` §7.2), and their callees can get added facts with these marks; the edges of a concrete premise and of the zero fact have concrete marks. Before F72 every restricted run was concrete | as the restricted forward run |
| a summary applies to an added fact `a` if | `applicable(j, a)` | location overlap for a canonical FLOW premise `(base, path, STAR, Empty, *)`; otherwise `inside(j, a)`, and for a noncanonical FLOW premise also `applicable(j, a)` (`ap.md` §4.3; F72 R3; §5.3 `matches`). This test applies after the restriction in the callee (the INTERSECTION with a demand pattern, in the locations and the marks, `ap.md` §6.4; §4.6). A callee has a non-zero premise only where a demand pattern of it emits one (§4.4): a callee whose only demand pattern is the zero demand publishes no summary of a non-zero premise, and its callers cross it by its records (next row) | as the restricted forward run; a zero-premise summary applies to the zero fact of the caller with no test and no restriction (rule `zret`) |
| records (`ap.md` §8.7 R3, R4) | none | same-direction native forward records by `byEntry`; backward records through transient leaf views by `byExit`. Match by `applicable || inside`. A must native record gives demand results on the `applicable`-only part. Native normal singleton summaries give no callee demand; a method with no demand or subtree seed has only zero analysis | native backward records by `byEntry`; transient views of forward records by `byExit`. An exact-premise must conclusion uses a concrete star premise carrying its exclusion and an exact normal target. A must forward premise becomes a may `[any]` demand target. Match the view itself by `applicable || inside`; never start it as an initial fact. Native normal singleton summaries give no callee demand; a demand-layer view result belongs to the caller |
| mark interests and static refinement | SummaryStorage owns R and the actor writes P (F78, `run1-requests.md`); the joined plan replaces Position transport. F75 collects T on every same-base normal abstract origin; the special root is rule-free | no request rule and no static rule (F72 R4). On a fact with the mark `*` or `*∖X`, a concrete mark gate or sink gives nothing; a selected-mark cleaner keeps every same-base fact with mark `*∖(X ∪ {T})`, including disjoint paths (F75, `ap.md` §4.7). No request is raised | as the restricted forward run (the backward run has no sink check) |
| sinks | the sink check (`ap.md` §4.9) | the sink check; on a `*` or `*∖X` fact it gives nothing (no request, `ap.md` §4.9) | no sink check; the sink seeds (`ap.md` §9.2) |
| unconditional sources | every source fires | only the source seeds fire (`ap.md` §6.1 rule 6); a zero-premise forward record still applies (`ap.md` §9.2) | every reversed source edge, with no seed filter (the backward run is on the full program, Lean `Reverse.Program.rev P`); it records the source hits (`ap.md` §8.11) |
| type filters | yes | yes | no |
| the tail `[any-taint]` (`ap.md` W8, S15) | a source rule with an `[any]` target gives `[any-taint]` on a normal input: a complete edge (on a demand input `[any]`, W8). A pass rule with an `[any]` target keeps `[any]`: a may, in the demand layer (`ap.md` W6). A normal `[any-taint]` conclusion can carry an EXCLUSION `E` of first accessors, `(x, p, [any-taint], E, T)`: an exclusion edge (the keep edge of a strong write, a `*/E'` summary) gives `E ∪ E'` and the result stays normal (§4.3). Only the demotions of §4.3 make `[any]` of it (demand, no exclusion): the field-limit cut, a cleaner `part` row other than `atAndBelow` and `below` one accessor below it, a may target, a demand-layer input and (in a restricted run) the must-record demotion. No premise has the `[any-taint]` tail | as run 1. Also the `[any-taint]` premise (a MUST-PREMISE) of the emission (§4.4), with the exclusion that the emission gives it: it starts as itself in the normal layer, and its normal edges are complete | no `[any-taint]` (a forward-only tail, `ap.md` W8). The seed of an `[any]` sink pattern and the reversal of an `[any]` condition literal give `[any]`, in the demand layer (`ap.md` W6). Every result of the reversal of a micro edge whose forward target is `[any]` (a pass rule with an `AnyField` target) is in the demand layer, also a `$` result (§4.3). So is every result of the reversal of a conjunctive micro edge (§4.3). A complete edge is a normal edge, as before F69 (`ap.md` §8.7 R1) |

No run has a liveness check (§4.3).

THE MARKS OF A RESTRICTED RUN (`ap-history.md` F72; the AP rules are in `ap.md` §4.3, §4.5, §6.1, §6.3, §6.4, §6.6,
§9.2). THE RULE: a restricted run analyses with a concrete mark only if a concrete-mark demand exists. Run 1 keeps its
requests; their answers make the concrete-mark summaries where the marks matter, and the hand-off gives them to the
next run as concrete patterns or retained normal singleton records. A forward demand with `*` gives a backward
demand with `*`, and a concrete demand stays concrete. The core part of each rule:

* R1, THE HAND-OFF NORMALIZATION (§7.3, §7.4). Every hand-off replaces the mark `*∖X` of a demand pattern by `*`, in
  `D-c` and in `D-p`: a larger demand, so it is sound. The summaries and the records keep `*∖X`: it blocks a mark that
  the summary cannot pass. A demand for a mark in `X` comes as its own demand edge, with the concrete mark;
* R2, THE EMISSION (§4.4). The initial fact is the WEAKEST fact inside `D-c` that covers the common part with the added
  fact (the requirement in the backward run). A `*` pattern gives its FLOW FORM, which does not depend on the added
  fact (SHARING); a concrete pattern gives `a ∩ D-c` with the mark of `a`, as before F72;
* R3, THE SATISFACTION (§5.3). A canonical FLOW premise `(base, path, STAR, Empty, *)` uses location overlap.
  Other FLOW shapes retain `inside` or `applicable`; concrete premises use `inside`;
* R4, NO REQUEST AFTER RUN 1, in both directions (§4.6). On a `*` or `*∖X` fact an operation that needs a concrete
  mark gives nothing (the table above);
* R5, THE RESTRICTION (§4.6). The mark-aware intersection of F71. A FLOW premise lies inside its own `*` pattern, and
  inside no concrete pattern;
* R6, THE CLAIM (PENDING, §11): every flow that needs a concrete mark is demanded by a concrete pattern or crossed by a
  concrete record, and every flow that a `*` pattern demands needs no concrete mark. So no request is needed after
  run 1, and every forward run still reports every real vulnerability that no earlier run confirmed. A sink fires only
  under a concrete premise (the method of a sink gets a concrete demand from its seed, and the chain of calls down
  stays concrete), so the confirmation does not change (§7.5).

WHAT F72 REPLACES: decision F35 (version 4), "no request after run 1, so a restricted run is CONCRETE", and its cost
(`ap.md` §6.3): NO SHARING, one initial fact per distinct added fact (path, tail, mark), so the premises are a product
of the marks. Since F72 a restricted run (forward or backward) can have `*` premises (FLOW premises), FLOW conclusions,
added facts and requirements with the marks `*` and `*∖X`, and `*∖X` marks on its summaries. Two consequences: the
backward weakening makes the backward demand coarser (for example the requirement `ret.*` in place of `ret`, §7.4), and
the restricted runs have no request rules. The current base rules are defined; their full mode/iteration proofs remain open (§11).

---

## 4. The method analyzer

The method analyzer `RunMethodAnalyzer` replaces `NormalMethodAnalyzer`. It keeps the pattern of today: the
intra-procedural edges stay inside the analyzer, the analyzer has a worklist, and its runner runs it in steps. One
class serves every direction and every mode. The `RunConfig` gives the differences of the mode. The direction changes
only the forms that the analyzer reads: the forward forms of the interpreter, or their reversals (§4.9).

### 4.1 State

| Field | Store | Content |
|---|---|---|
| `edges` | method edge store (`ap.md` §8.1) | the edges, keyed per kind (`ap.md` §7.2): REACH per (statement, premise key, layer); FLOW per (statement, premise key, layer, base, exclusion, mark exclusion); TAINT per (statement, premise key, layer, base), and a normal TAINT tree also per the one exclusion of its `[any-taint]` leaves (`ap.md` §7.2) |
| `initials` | initial fact store (`ap.md` §8.2) | the initial facts of the run: zero, emissions, answers |
| incoming input | transient handler argument (`ap.md` §8.3) | consume for direct emission, then release; no `links`/AddedFactStore, supplier index or historical payload seen-set |
| `summaries` | run summary store (`ap.md` §8.5) | Raw summaries per premise key and layer supply R1 records. Selected publications supply demand edges (§4.6; `ap-impl.md` DD17, `RunSummaryStore.addDemand`, `demandEdges()`). The hand-off reads only the stored demand edges (§7.3, §7.4). |
| `counters` | | Frontier/stop counts (§7.1, §7.8): demand-layer edges, summaries and admitted incoming contributions, record applications, nonzero initials and zero-premise edges. Incoming counts need no retained link; distinguish transport arrivals, N2 fresh deltas and new starts. Optional demotion counts retain their units. |
| `abstraction` | run-1 initial emitter (§4.6) | the joined static plan and borrowed R reads; no local cumulative questions, answer memo, caller inputs or Position transport |
| `sourceHits` | source hit store (`ap.md` §8.11) | backward run only: the unconditional sources of this method that a requirement reached |
| `conjunctions` | conjunction store (`ap.md` §8.9) | manual fixed-literal inputs and the E6 combinations of callee summaries with several premises (§5.4) |
| predicate state | `RulePredicateStore` (`ap.md` §8.9) | forward only: scoped observed source/sink proofs, canonical binding headers and matched sink-fact versions, including evaluated exit `S` parts; no compiled topology |
| `worklist` | `DeltaWorklist` (§4.3, §10) | the edge deltas to process: the `unchanged` queue with its set, and the `normal` queue |
| `pendingPublications` | | the publications that the analyzer has not yet given to its `SummaryStorage` (§4.6) |
| pending requests P | actor-owned empty-base RequestStorage, same class as R | every producer first filters effective R, then unions remaining marks; commit P then replace it at method fact-queue-empty. Never read P for membership. No deferred-answer memo or old input snapshots (§4.6). |
| `queued` | | true while the analyzer has a `Work` event in its runner (§6.2) |
| `context` | `MethodContextCache` entry and run part | the method graph, the alias analysis, the lambda resolutions, the rule context of the run (§4.8) |

The subscriptions are not in the analyzer. They are in the `SubscriptionManager` of the runner of the caller
(`ap.md` §8.4; §5.3).

THE PREMISE KEY HOLDS THE TAIL AND THE EXCLUSION. In a forward restricted run, an `[any-taint]` premise (a MUST-PREMISE) with its exclusion `E` and
an `[any]` premise with the same base, path and mark are two different premise keys (`InitialAp` with the tail
`ANY_TAINT` and the exclusion `E`, or with the tail `ANY`, `ap.md` §7.1). Two must-premises of one path with different
exclusions are two premise keys too. Two added facts of one method (an `[any-taint]` one and an `[any]` one) can give
them at one path. So `initials` holds both, and every store of the table that has the premise key in its key
(`edges`, `summaries`, `conjunctions`) keeps them apart. F78 run 1 has neither ANY nor ANY_TAINT initials;
its nonexact concrete STAR initial still starts an ANY fact. The backward run has no `[any-taint]`.
Lean: the must flag and the exclusion of the objects `AnyTaintEx.XObj` (`init M j must jex`, `edge M j must jex n f`).

### 4.2 Handlers

The runner calls these handlers. Each handler is part of one event (§5.1).

| Handler | When | Actions | `ap.md` |
|---|---|---|---|
| `addRootZero()` | the run starts at a root | the zero fact is an initial fact | §6.1 |
| incoming-input handler | a caller binds a fact into this method | emit directly (§4.4): run-1 abstract/catalog and active concrete answers, or fixed-demand intersection after run 1. Read R for concrete answers; retain no unrequested answer rows. Release the input. N2 owns its original admitted header and full ref separately. Count DEMAND input contributions without storing them. Zero emits zero. | E1, E2 |
| `addZeroEntry()` | backward: the zero fact of a caller reaches a call to this method | the zero fact is an initial fact (rule `zin`) | §9.2 |
| `addMarkRequests(initial, marks)` | run 1: an eligible N2 FLOW supplier routes interest to this original caller initial | filter effective R, union remaining independent marks into owner-only P; schedule existing Work for a nonempty contribution; commit at method fact-queue-empty (§4.6) | E5 |
| `applySummary(sub, pub)` | the `SubscriptionManager` matched a publication with a subscription of this method | one premise: apply the summary to the added fact (`ap.md` §4.3), then the stages after the callees stage (§4.5). Several premises: the combination of §5.4. | §4.3, E2, E4, E6 |
| `applyRecord(sub, record)` | a new subscription; a same-direction native record covers or contains the added fact | apply the record, then the stages after the callees stage (§4.5). Keep its `inside` and `applicable`-only results separate. The latter must-record results keep their exclusions through these stages; demote them immediately before the call field cut. Count one `recordCrossings` increment if either initial result batch is nonempty, before rewriting | AP §4.3, §8.7 R4 |
| `applyRecordView(sub, view)` | a new subscription; an opposite-direction transient leaf view covers or contains the added fact | apply the view directly with its premise exclusion and result layer, then the stages after the callees stage. Do not emit, start, persist, or demote its premise through `InitialAp`. Count each application with a result as a record crossing | AP §8.7 R3 |
| `step(quantum)` | a `Work` event | process at most `quantum` worklist items (§4.3) | §6.1 (S6: any order) |

A new initial fact `j` (from any handler) is event E3: the analyzer adds the start edges of `j` to the worklist
(§4.4).

### 4.3 The worklist and the step

* An item of the worklist is an edge delta (`EdgeDelta`, §10): (premise key, layer, node, conclusions). The
  conclusions are one of the kinds of `ap.md` §7.2: REACH, a FLOW tree or a TAINT tree. The edge is the fact BEFORE
  the statement of the node, as today.
* `edges.add` returns the delta of the merge (`ap.md` §7.2 T4). A null delta adds nothing, and the analyzer drops it.
  Else the analyzer adds the delta to the `normal` queue of the worklist.
* NO LIVENESS CHECK. No run drops a fact because its local is not live at the node. (Today the forward runs prune by
  `isReachable` of `JIRLocalVariableReachability`.) The alias analysis does not change: it keeps its own inputs, as
  today (§4.8). This is a decision (`ap-history.md` F67).
* The step takes one item and applies its node:
  * a non-call statement: the statement transfer (`ap.md` §4.2; `interpreter.md` §2, §4.4; the STATEMENT mode of
    §4.9) with the primitives that the interpreter puts at the statement (`interpreter.md` §5); then the field limit
    at the cut points (`ap.md` §4.4);
  * a call statement: the call steps (§4.5).
* THE LAYER OF AN ANY-TAIL RESULT (`ap.md` W6, W8). The AP sets it on each application of a micro edge, in every mode
  of §4.9.
  * FORWARD. The result of a micro edge with an `[any]` target (a may: a pass rule with an `AnyField` target) goes to
    the demand layer; the result of a micro edge with an `[any-taint]` target (a source rule, `ap.md` S15) keeps its
    layer. A demand-layer result with the `[any-taint]` tail becomes `[any]` and loses its exclusion. An exclusion does
    not demote a normal `[any-taint]/E` fact: the result carries the exclusion and keeps its layer (`ap.md` §4.1, §4.2,
    §4.7): an exclusion edge at the path of the fact gives `E ∪ E'`; the case below keeps `E`; the case above applies
    only if `E` admits the step down and gives the exclusion of the edge; a read through an accessor in `E` gives
    nothing, a read through another accessor gives the empty exclusion; a copy of the object keeps `E`; the cleaners
    `atAndBelow` and `below` at `P.f` add `f` to `E` (`below` also keeps `(x, P.f, $, T)`). These rows are not
    demotions. THE DEMOTIONS: only these operations make `[any]` of an `[any-taint]` fact, in the demand layer and
    with no exclusion (`ap.md` §2.2 THE DEMOTIONS):
    * the field-limit cut (`ap.md` §4.4);
    * a cleaner `part` row of `ap.md` §4.7 other than the `atAndBelow` and `below` rows one accessor below the fact: so
      the `exact` cleaner at the path of the fact or one accessor below it, and every cleaner two or more accessors
      below it;
    * a may target: the `[any]` target of a pass rule (W6);
    * a demand-layer input: a demand fact (also another input of a conjunction), a demand-layer summary edge or
      record, a demand link (an `[any]` added fact);
    * the must-record demotion (a restricted run; §4.2 `applyRecord`).

    Lean: `AnyTaintEx.w6tX`, `transferX`, `limitFX`, `cleanResX` (`partX`), `recLayerX`; the rows `AnyTaintEx.annX`.
  * BACKWARD. The run has no `[any-taint]` (`ap.md` W8). Every result of a reversed micro edge whose FORWARD target is
    `[any]` goes to the demand layer, whatever its tail, also a `$` result (`ap.md` §9.1): the may belongs to the
    forward rule. W6 puts every `[any]` result in the demand layer. The reversal of a source edge (forward target
    `[any-taint]`, a must) follows the ordinary rows of `ap.md` §4.1. This rule is argued, as the backward W6 (§11).
    THE REVERSAL OF A CONJUNCTION (`ap.md` §9.1; `ap-history.md` F70). The reversal of a CONJUNCTIVE micro edge with
    two or more positive literals (also of a conjunctive exit source, §4.4) gives EVERY result in the demand layer, as
    the reversal of a may: a requirement that reaches one literal is not a converse flow of the conjunction. So no
    backward summary through it is a record (`ap.md` §8.7 R1), and the hand-off gives it to the next
    forward run (§7.4 case 3), which analyses the callee with all the members. This fixes a false positive that
    existed before F70: a normal backward summary from the conclusion to ONE literal was a record, and its reversal
    (R3) was a forward record of one literal, which drops the other literals (§11). Argued (§11).

  The core reads the target tail of the FORWARD form of the micro edge (`MicroEdge.forward`, §4.9): `[any]` is a may
  (a pass rule), `[any-taint]` a must (a source). The reversed literal of a conjunctive edge carries
  `MicroEdge.conjunctive` (§4.9), a property of the form (the reversal made it), not of the rule kind. So the core
  needs no rule-kind flag, in either direction.
* A result AT AN END NODE of the run (§4.4) goes through the end rules and makes the summary edges (§4.6). This holds
  for every handler that makes such a result: `step`, and also `applySummary`, `applyRecord` and `zret` when the end
  node is a call (for example a backward end node whose forward entry statement is a call). An end node is a
  statement like every other: its transfer or its call steps come first. (Today: `handleStatementEdge`, the edge
  post-processor, then `tryEmmitSummaryEdge`; the new core has no post-processor.)
* A forward result AT AN EXCEPTIONAL EXIT goes through the exit rules of that exit (`interpreter.md` §4.7 steps 1 and
  2: the exit sources, the exit sinks and their end facts, with `Result` read as `exc`; the unconditional exit rules
  fire there on the zero fact, `interpreter.md` D26). Its results end there: an exceptional exit is not an end node
  and makes no summary edge.
* Each result goes to every successor node in the graph of the run, through `edges.add`.
* THE UNCHANGED PATH stays as today (`ap.md` §8.1). If the statement does not touch the base of an edge, the
  analyzer puts the edge for each successor into the `unchanged` queue with no `edges.add` (today
  `addSequentialUnchangedEdge`). The new core has no edge post-processor (`interpreter.md` D14), so an unchanged edge
  always goes on as it is.
* THE TWO QUEUES. The worklist (`DeltaWorklist`, §10) has two queues:
  * `unchanged`: the items of the unchanged path. A SET discards the repetitions: an item that the set holds does not
    go into the queue again (today `enqueuedUnchangedEdges`);
  * `normal`: the deltas of `edges.add`. The zero-to-zero items (REACH on `{zero}`) come first, then the other items
    in LIFO order, as today.

  The step always takes an `unchanged` item while that queue is not empty. The step takes a `normal` item only when
  the `unchanged` queue is empty. When the step finds the `unchanged` queue empty (after the last `unchanged` item
  put its successors into the queues), the analyzer drops the set (it starts a new empty set). The set stays at the
  end of a `Work` event (today the event end resets it). So a loop of statements that do not touch a base ends: the
  set holds the items of the loop until the `unchanged` queue is empty.
* The analyzer does not delay an edge by its depth. There is no fact-depth limit: the field limit of the run is the
  only depth bound (`ap.md` §4.4; `bidirectional-task.md` §5 item 1).

### 4.4 Initial facts, start nodes and start rules

For each incoming added fact, run 1 uses AP §6.2 and later runs use AP §6.3 directly.
No emitter input history or exact payload seen-set is created. Repeated arrivals may repeat matching work;
`initials.add` still deduplicates starts. N2 fresh deltas may control transport only with original-header correctness.
The restricted emission query is `near(base::path)` (AP §8.6); run the full
emission test on every candidate. A `*` pattern shares one FLOW premise; a
concrete pattern emits only a concrete matching added mark. No restricted request
is raised. Enforce AP §6.1's normalized-pattern and entry-tail guards.

`initials.add` deduplicates exact initial facts. Each new initial fact starts as
AP §6.5 specifies, at every start node of its kind, then takes the start rules.
F78 supplied request answers and direct class-point inputs add initials in run 1. The special S-root seed takes ordinary code transfer but no rule evaluation; use the same guard at entry, statements, calls and exits, per root component rather than the whole packed tree. Zero demand is implicit for every
forward method key; root zero is initial. Backward `addZeroEntry` enters zero
directly at each reached callee exit, without emission. A method whose only
forward demand is `(zero,none)` has only zero initial facts; its callers can still
read its records. Backward zero-premise seed paths can return to callers.

F78 §1.1 gives the exhaustive selected run-1 admission domain: canonical Zero;
abstract non-static base/root, special S/root and S/[C,f]; concrete non-static
base/root EXACT/STAR, S/[C,f] EXACT/STAR and S/[C] EXACT/STAR: ten families. All have
Empty E. No ANY/ANY_TAINT, abstract class, deeper initial or concrete empty S-root
initial exists. T may be numeric ID 0 on a nonzero base; only the Zero base has the
canonical Zero restriction. The JVM cut/layout/binding proof establishes the
bounded incoming domain before emission at run-1 limit exactly 1; there is no new
emitter cut. Generic factories/custom larger first limits are outside that
certificate. Validate queued answer adapters as well as local constructors.

Source/end-GEN outputs, source/sink seeds and joint-premise members are edge/proof
work, not arbitrary additional run-1 initial constructors. Run 1 reads no native
records and starts with empty hand-off seeds. F78 §1.3 specifies each starting
fact: abstract STAR is NORMAL FLOW, concrete EXACT is NORMAL exact TAINT, concrete
STAR is DEMAND ANY TAINT, and canonical Zero is NORMAL REACH. An incoming/starting
fact can be ANY; an InitialAp cannot. Nonexact field/class suppliers become STAR
initials, while exact ones remain EXACT. Supplied abstract field initials are
always emitted for each supplied selected structural slot independently of R(T),
using the closed plan and actual relevant wide activation, not all irrelevant
incoming fields. Concrete field initials additionally require actual matching T
and effective-R membership. The measured static answer code still emits ANY and
does not yet conform. Prove no-ANY emission/admission and the whole-premise summary,
native/opposite and reversal bridges separately; equal starting facts alone do
not discharge those obligations.

Start nodes, end nodes and their rules. The interpreter gives the forward ones; the backward ones are their reversal
(§4.9):

| Direction | Start nodes | Start rules | End nodes | End rules |
|---|---|---|---|---|
| forward | the entry statement of the method key | the ENTRY RULES (`interpreter.md` §4.3): the zero fact with the entry sinks and the entry-point sources; another fact with the filter by the context type | every normal exit | the EXIT RULES (`interpreter.md` §4.7). The exit rules also apply at an exceptional exit, with `Result` read as `exc`, but it is not an end node: no summary |
| backward | the zero fact: every forward exit, normal and exceptional (`ap.md` S4). Another initial fact: every normal exit (exceptions are out of scope, `interpreter.md` G1) | the reversed exit rules of that exit (normal or exceptional): the reversed exit sources and end-fact edges; and the sink seeds of its exit sinks (§4.7), which then take the reversed exit sources of that exit too (`interpreter.md` §4.9 SEEDS) | the forward entry statement | the reversed entry rules: the reversed entry-point sources and end-fact edges (no context filter: the backward run has no type filter) |

The exits: the normal exit is `JMethodExitNormalInst`; the exceptional exit is `JMethodExitExceptionalInst`.

THE BOUNDARY RULE STATEMENTS. The entry rules and the exit rules are rule statements in the STATEMENT mode (§4.9). Their
touched bases and keep edges are those of `interpreter.md` §4.3 and §4.7 (`ap-impl.md` §29, `keep`): the zero base with
its keep edge; at the exit, each base that a literal of an exit source reads, with the identity edge `b.* → b.*`; at the
start, each base with a context filter, with the identity edge and that filter as its operand filter. A source target
is a gen-only target (not touched). So a fact on a read base stays in the worklist (`interpreter.md` §4.7 step 1), an
initial fact passes unchanged or passes its context filter, and in the backward run a requirement on a read base or on a
target passes.

A CONJUNCTIVE EXIT SOURCE (an exit source with two or more positive literals in one alternative of its condition) is an
ND edge at the exit, as at a call (`interpreter.md` §4.7 step 1, D31; `ap-history.md` F68). It is a `ConjunctiveEdge`
of the exit rules (`RuleStatement.summary.conjunctions`, §4.9). Each literal stores its input in the conjunction store of
the method key, per (edge, exit statement, literal index) (`ap.md` §8.9). A full combination is an exit item with the
union of the premise sets (without the zero fact, `ap.md` §4.6), after the field limit. It goes through the exit steps 2
to 5 (`interpreter.md` §4.7) and becomes a summary at the normal exit, an ND summary if its premise set has two or more
members; the callers apply it by E6 (§5.4). At the exceptional exit it goes through step 2 only and ends there (§4.3).
It is not a rule error. The backward run reverses it into one micro edge per literal (`StatementSummary.reversed`,
§4.9), and every result of such a reversed edge is in the demand layer (§4.3 THE REVERSAL OF A CONJUNCTION).

The backward graph is the reversed graph (`ApplicationGraph.reversed`) with the EXIT WIRING. A node that reaches no
forward exit gets an edge to an exceptional exit (`interpreter.md` I11 (e); today `JIRBackwardExitWiringGraph` on
`saloed/backward-main`). Only the zero fact uses an exceptional exit. The `MethodContextCache` keeps the wired graph per
method (today the code computes the wiring again on every call).

AN EMPTY METHOD (no instruction: a native method, an abstract method, a method with no body) cannot be analysed. The
core never analyses it: it has no method key, no method analyzer and no `SummaryStorage`, and it is never a root. The
call resolver never resolves a call to an empty method: it drops the empty method from the callees of the call (§4.5).
So a call with an empty and a non-empty resolution result enters only the non-empty callee, and a flow through the empty
target is lost (`interpreter.md` G12). If every resolution result of a call is an empty method, the call is an
UNRESOLVED call (`interpreter.md` §3.7: the pass rules and the default identity). This is a decision (`ap-history.md`
F67). Today `EmptyMethodAnalyzer` publishes the identity summaries of the most abstract facts.

### 4.5 The call plan

A call is a small graph: six POINTS and the STAGES between them. This is the CALL PLAN. The interpreter gives the
FORWARD plan (`interpreter.md` §4.5, §4.6). The backward plan is its REVERSAL (`CallPlan.reversed`, §4.9). It gives the
backward call order of `interpreter.md` §4.9 step by step, so the analyzer runs one algorithm in both directions.

| Point | Coordinates | Content in the forward plan |
|---|---|---|
| `BEFORE` | caller | the caller fact before the call |
| `BOUND` | callee | the bound fact, before the cleaners; the RULE POINT of the call (its sinks) |
| `ADDED` | callee | the added fact: the entry of the callee |
| `RETURNED` | callee | a result of the callee: a summary result or an unresolved result |
| `REWRITTEN` | callee | a result after the summary rewriter |
| `AFTER` | caller | the result in the caller, before the field limit |

The forward stages of a JVM call `r = m(o, a1, …, an)` (the step numbers of `interpreter.md` §4.5):

| Stage | Forward | Content | Step |
|---|---|---|---|
| binding in | `BEFORE → BOUND` | the binding edges into the callee (with the zero binding), with the caller-side type filters (`interpreter.md` §3.1) | 2 |
| end facts | `BOUND → REWRITTEN` | the end-fact edges of the sinks of the call; GUARD: the sink triggers. It takes no input fact (THE END-FACT STAGE, below) | 3 |
| sources | `BOUND → REWRITTEN` | the rule statement of the call: the sources and the conjunctions (`interpreter.md` §4.1) | 4 |
| cleaners | `BOUND → ADDED` | the cleaners and the `RemoveAllMarks` kill on `S`, in the rule order | 5.1 |
| callees | `ADDED → RETURNED` | the resolved callees (with the lambdas of the prescan), never an empty method (§4.4): SUBSCRIBE and LINK (below) | 5.2 |
| unresolved | `ADDED → RETURNED` | the statement summary of the unresolved callee (`interpreter.md` §3.7); also of a call whose every resolution result is an empty method (§4.4) | 5.3 |
| constructor | `ADDED → REWRITTEN` | JVM `<init>`: the identity of every bound position (`S`, the receiver, the arguments); it skips the callee and the rewriter (`interpreter.md` §3.5) | 5.2 |
| rewriter | `RETURNED → REWRITTEN` | the summary rewriter (`interpreter.md` §5.2) | 6 |
| binding back | `REWRITTEN → AFTER` | the binding edges back, with the result-side type filters | 6 |
| aliases | `REWRITTEN → AFTER` | the alias edges `P.* → b.q.*` (`interpreter.md` §3.8 AC2); GUARD: the selection of AC3 and AC4 (THE ALIAS GUARD, below) | 6 |

At the CALLEES stage, retain the original bound/cleaned input and its full caller reference in existing N2,
then deliver a transient input to each selected callee. The callee consumes it for initial emission and releases
its transport payload; “link” in call equations denotes that semantic relation, not a second callee store.

The plan also has its TOUCHED caller bases (`S`, `o`, every `ai`, `r`; step 1) and the SINKS of the call at `BOUND`
(step 3).

HOW THE ANALYZER RUNS A PLAN (both directions):

* A caller fact on a base that the plan does not touch passes over the call (step 1). The zero base is never touched.
* A fact at a point goes through every stage that starts at that point. Each result arrives at the end point of its
  stage. The stages that start at one point read the same facts, so their order does not matter. (So the sinks see
  the uncleaned bound fact, as `interpreter.md` §4.5 asks.)
* An `Edges` stage applies its summary in the STAGE mode (§4.9 THE APPLICATION MODES): only its edges give results.
  The touched bases of the plan do the pass-over (step 1). The kill on `S` in the cleaners stage applies in the
  STATEMENT mode.
* A fact at the exit point of the plan gets the field limit and goes to the return node of the call.
  An `applicable`-only native must-record result is demoted there, immediately before this cut. Its pending
  demotion belongs to the resumed batch. Its fact and exclusion pass through the preceding stages unchanged
  by that pending demotion; cleaners, mappings and their statement cuts still apply their own rules.
* The callees stage is not local: SUBSCRIBE and LINK, and its results arrive later through `applySummary` and
  `applyRecord`, at its end point.
* At the rule point `BOUND`, the forward run checks the sinks of the call (§4.7). The backward run checks no sink: it
  fires the sink seeds of these sinks there (§4.7).
* The sources stage applies the source seeds (forward restricted run) and records the source hits (backward run) (§4.7).

THE END-FACT STAGE (`BOUND → REWRITTEN`) takes no input fact. Its trigger is a sink of the call at `BOUND`: a sink edge
of a plain sink, or a new full combination of a conjunctive sink (§4.7). On a trigger, the analyzer applies the
end-fact edges of that sink to the zero fact, in the layer of the sink edge or of the combination (`ap.md` §2.2,
§4.9). Each result is a zero-to-fact edge (premise set `{zero}`), and it arrives at `REWRITTEN`. The same rule holds
for the end facts of the entry sinks and of the exit sinks (§4.4). The backward run has no trigger: the reversed
end-fact edges apply to every requirement (THE REVERSAL, below).

THE TRIGGER OF AN END FACT (`ap.md` §9.2; `ap-history.md` F70). An end fact exists only after its sink triggers, so
its reversal must demand the trigger. When the reversed end-fact edge of a sink alternative `A` at the statement `s`
of the method key `M` applies to a requirement, it gives the zero fact (as before), and the analyzer also fires the
sink seeds of `A` at `(M, s)` (SINK SEEDS, §4.7), once per `(M, s, A)`. So the next forward run demands the witness of
the trigger, also when the vulnerability of `A` is CONFIRMED. The seeds of the hand-off do not change: only the DEMAND
vulnerabilities are seeded at the barrier (§7.3). The same rule holds for the reversed end-fact edges of an entry sink
and of an exit sink (§4.4); an unconditional sink (an entry sink, for example) has no sink seed (§4.7), so its trigger
needs only the zero fact. The analyzer makes these seeds from the forms of the sink (`SinkRule.patterns`), not from
the `SeedIndex`, so A4 holds. Argued: the model has no end facts (§4.5; [proof-status.md](proof-status.md)).

THE ALIAS GUARD (`Guard.MemoryEffect`; `interpreter.md` §3.8 AC3, AC4). Each forward result carries
`identityCandidate: Boolean?`. The flag is null before a stage classifies the result. The default identity of the
unresolved stage and the constructor stage set it to true. Sources, end facts and nonidentity pass rules set it to
false. A callee summary or record result sets it to true only when its premise is nonzero, its native conclusion is
NORMAL, and that conclusion equals the start fact of the premise (`ap.md` §6.5). Every other summary result sets it
to false, including zero-premise and DEMAND summaries (`ap-history.md` F67). An `applicable`-only native
must-record result also sets it to false, so aliases admit the result before its pending demotion at `AFTER`.
Bindings, cleaners and the rewriter keep
the flag. The alias stage admits a result exactly when its current layer is DEMAND or the flag is false. It skips
only a NORMAL candidate identity. The flag does not replace `StageKind`, which also selects statement edges and
source-seed places.

THE REVERSAL (`CallPlan.reversed`; the rules of `ap.md` §9.1, §9.2):

* The entry point and the exit point change places (backward: `AFTER` is the entry, `BEFORE` the exit). Each stage goes
  from its forward end point to its forward start point.
* A micro-edge stage gets the reversed summary (`StatementSummary.reversed`, §4.9).
* A GUARD is a forward-only selection, so the reversal drops it: the reversed alias edges apply to every requirement
  (`interpreter.md` AC5), and the reversed end-fact edges too. A reversed end-fact edge that applies also fires the
  sink seeds of its sink (THE TRIGGER OF AN END FACT, above). The reversal also drops every type filter.
* A primitive cleaner is its own reversal. Reverse the action order in the cleaners stage and the rewriter.
  A field action is reversed write, clean temporary, reversed read (F74); reverse each statement summary.
  The static kill keeps its keep edges.
* The callees stage stays the same: in the backward run it gives the backward summaries.
* The sinks stay at `BOUND`.
* The touched bases: the forward touched bases and the alias bases (the target bases of a stage that ends at the
  forward exit and that the forward plan does not touch). Forward, an alias base passes over the call. Backward, its
  requirement passes over by the PASS-OVER stage `AFTER → BEFORE` (kind `PASS_OVER`, §4.9), with the identity edge
  `b.* → b.*` of each alias base (`interpreter.md` A5). This stage is the exact reversal of the forward pass-over. A
  target base that the forward plan touches (an argument, `S`) gets no pass-over.

The reversed plan is the backward call order of `interpreter.md` §4.9:

| Backward step (`interpreter.md` §4.9) | Reversed stage |
|---|---|
| 1 relevance | the reversed touched bases |
| 1 the pass-over of the alias bases that the forward plan does not touch | `AFTER → BEFORE` |
| 2 reversed binding back and alias edges | `AFTER → REWRITTEN` |
| 3 reversed sources and end-fact edges; their results arrive at step 7 | `REWRITTEN → BOUND` |
| 4 reversed rewriter | `REWRITTEN → RETURNED` |
| 5.1 resolved callees | `RETURNED → ADDED` |
| 5.2 unresolved callee | `RETURNED → ADDED` |
| 5.3 JVM constructor: from step 2 to step 6 | `REWRITTEN → ADDED` |
| 6 reversed cleaners and the keep edges of the kill | `ADDED → BOUND` |
| 7 the seeds and the read positions | the rule point `BOUND` |
| 8 reversed binding in | `BOUND → BEFORE` |
| 9 the field limit | the exit point `BEFORE` |

The core reverses only a forward plan. The reversal is not an involution: it drops the guards and the filters, and it
adds identity edges.

SUBSCRIBE: the analyzer gives the subscription `(caller edge, s, a)` for the callee `m` to the `SubscriptionManager`
of its runner (§5.3). The replay applies the publications of `m` that `a` satisfies, and the records of `m` that apply
to `a`.

LINK: the analyzer sends the link `(a, caller edge)` to `m`. For `m` in another unit, it sends the event `LinkIn`. For
`m` in the same unit, it may call `m.addLink` directly (as today `submitMethodInitialFact`; §6.1 permits it).

The zero fact at a call. The zero base is not touched, so the zero fact passes over the call in both directions (rule
`zpass` in the backward run). It also acts at the rule point:

* forward: the zero binding takes it to `BOUND`. There the unconditional sinks fire, and the sources stage fires on it
  (in a forward restricted run, only the source seeds, §4.7). No cleaner acts on the zero base, so it goes on to
  `ADDED` and enters every resolved callee: a subscription and a link;
* backward: the zero fact at the call fires the sink seeds at `BOUND` (§4.7); they go on through the reversed binding
  in. The analyzer adds a ZERO SUBSCRIPTION for each resolved callee `m` (it matches the zero-premise publications of
  `m`, §5.3) and sends `ZeroIn` to `m` (rule `zin`). A zero-premise publication applies to the zero fact of the caller
  with no test (rule `zret`). Its results arrive at `ADDED`, the end point of the reversed callees stage.

### 4.6 Summary edges, publications and requests

After end rules, handle each raw summary delta `j→g` (event E4) in this order:

1. Add the raw delta to `summaries`. Count its demand-layer objects (§4.1).
2. Determine raw persistence eligibility using [ap.md](ap.md) §8.7 R1.
   Retain eligible normal singleton summaries for the barrier.
3. Build publications: run 1 publishes raw leaves; a restricted run publishes
   each result of AP §6.4 for the demand patterns returned by AP §8.6's query.
   A backward `{zero}`-premise balanced return publishes raw leaves (AP §9.2).
4. Select demand using AP §8.5: keep demand-layer summaries, normal multi-premise
   summaries, and normal backward zero-premise summaries. Selection is uniform
   within the premise-key/layer group. If selected, pass step 3's publications to
   `addDemand`. No second reduction or selection of narrowed pieces is needed.
5. Enqueue publications and flush them at the method-fact-queue-empty boundary before submitting pending F78 requests (§6.2 W3). A quantum yield may retain pending publications under the same counted Work token.

Publications retain the complete premise key, including each tail, must flag, and
field exclusion. Reduction reads the whole premise, conclusion marks, and field
exclusions; its rules and representability limits have one definition in AP §6.4.
A `*` entry pattern can publish FLOW and concrete premises inside it; a concrete
pattern cannot publish a FLOW premise. For several premise members, query the
union of candidates for each member. Apply AP §6.4's member-wise restriction,
testing each selected whole member, and retain the complete premise set. The
query never replaces that test.

F78 requests exist only in run 1; [run1-requests.md](run1-requests.md) is their detailed contract.
Each actual origin is a normal abstract InitialAp: StaticSlot(C,f) or BaseSlot(b). Validate K6 before early
returns. A concrete current fact can answer a request under its actual concrete/zero premise, but cannot
forward interest upstream. No root request, query role/full Pattern, E/mx key or deferred-answer memo exists.

Use the same RequestStorage class twice per method. SummaryStorage owns effective cumulative R = B union D:
B is the closed plan's physically shared immutable normal-static slot/mark rows, D only dynamic additions disjoint
from B. Bind B before the first subscription/input. The actor owns write-only empty-base pending P. Every producer,
local/remote/wide, subtracts effective R before union into P; no producer queries P. Other-method requests are normal
counted batches to that method's owner, including same-unit replay effects. Sender filters and abstraction reads
also use effective R. Equal pre-commit
proposals are permitted and merge in P. At method fact-queue-empty, flush normal publications, then execute:

```text
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Commit reads P synchronously, atomically admits `fresh = P - (B union D)`, sets `D = D union fresh` without changing
B, and queues grouped immutable fresh
notifications after releasing the lock. It retains no mutable P and invokes no solver/actor callback inline.
Later queued events write the replacement P. Keep existing Work counted through commit/replacement and all sends;
request-only actors schedule Work without a synthetic fact. There is no takeBatch API, unit barrier, bespoke
mailbox/latch, version cursor, pending quota, backpressure, overflow/rescan or new scheduler quantum.

Static planning visits the joined complete resolved graph and every effective IR/rule/model form, including
source AND sink NNF, input gates, generic excluded-mark targets and cleaners/rewriters. Compute finite recursive
closure. Already resolved rows are the shared B, not a second seed table. Do not copy B into P/D or notify it
recursively at startup: new-input handling reads full R, including B. Instantiate unresolved wide scopes lazily from
actual relevant inputs through the same R filter and P/commit/notify; metadata potential alone suppresses nothing.
P and D stay disjoint from B, so no fresh notification contains B. Changed static copies preserve the original
slot, such as C.g -> C.f or an argument -> static; precomputing a need on C.f does not discharge C.g/argument.
When the actual original static FLOW slot equals the queried slot, actual call-graph closure gives callee B(slot) subset caller B(slot).
Skip that whole B row before per-mark callbacks/transport. Dynamic D rows still use target-R difference;
changed-origin rows still route to the original caller slot. No role is added to the request key.
The special S-root keeps code/summary work but evaluates no rules. Normal fields always have abstract analyses;
requested concrete analyses are additive. Exact class points use their direct concrete path; broad components
remain MAY/DEMAND. F78's explicit structural ownership and untouched identity frame apply to all caller readers,
including native and reversed rows. Raw root identity/coarse-E summaries are not an unrestricted fallback.

For each real input, bind B before registration, register the subscriber and admit its original N2 header before LinkIn.
New TAINT input reads full R; fresh dynamic R additions visit existing N2 TAINT/FLOW; fresh FLOW joins full R.
This closes both arrival orders, including the precomputed case without an initial wave, with no
callee memo or redundant N2 fresh-TAINT request replay. Batched slot matching ignores field E and FLOW mark
exclusions. Every exclusion creator collects its same-origin mark, and inherited summary/call exclusions retain
that route. Set union absorbs duplicate interest. Ordinary AP/summary tests still enforce their exclusions.

Select relevant N2 handles once per fresh slot batch, then run the two-slot answer/forward relation. Preserve the
full original caller/premise/context/call and independent layers for summary/ND application. Project request sets
only onto the full destination method and original slot; that projection does not merge proof bindings. Queue all
answers/further requests, including local ones, before they can mutate a visited directory. Finite owner-local
visits check cancellation; no suspended snapshot/history protocol or hard latency promise is introduced.

Restricted analyzers have neither R/P nor requests. Input intersects fixed demand immediately. F75 retains the
same-base abstract residual with the selected mark excluded, without requests. Full NNF support, F74/F75/D23,
cleaner order, positive limits and candidate/end-GEN rules remain unchanged outside the explicit F78 contract.

### 4.7 Sinks, vulnerabilities, seeds and source hits

* A triggered sink adds its WITNESS under `(rule, method, statement)` in `VulnerabilityStore` (`ap.md` §8.10).
  The method has no context, so several contexts of one sink statement have one vulnerability key. Each witness
  retains its bound occurrence/proof scope, full method key, run and ordered sink-edge support headers.
  Optional confirmation reads certified joint premise support in that method key (§7.5). Unverified candidates,
  including all-NORMAL mixed-caller ND witnesses, retain `confirmed=false` and report as DEMAND.
* The store merges matched sink facts only within the same vulnerability key, occurrence/proof scope, method key,
  run and ordered premise/effective-layer/complete-fact-group shape. Different scopes or headers never merge.
  Structural proof equality does not suppress new matched-fact versions. Legacy/manual forms retain their
  alternative index as part of the shape.
* Bound NNF conditions use the actor-owned predicate store. Repeated canonical leaves require the same complete
  binding header; shared OR nodes can choose different branches in separate AND operands. A completed compatible
  root gives one scoped witness. The manual fixed-literal adapter uses ConjunctionStore (§4.9, `ap.md` §8.9).
* THE GLOBAL-STATE RULE (exit sinks; `interpreter.md` §4.7 step 3, D30). At a normal exit, the analyzer drops the
  EVALUATED statics of a ZERO-PREMISE item: if a part of an item on the static base `S` whose premise is the zero fact
  (a state that the method or its callees set) satisfies a mark literal (`ContainsMark`, `ContainsMarkOnAnyField`) of
  an exit sink, plain or conjunctive, the analyzer drops that part from the summary edge. The rest of the item stays.
  For a bound exit condition, the analyzer also stores the evaluated part as a canonical leaf contribution in the
  predicate store; the manual literal adapter stores it in the conjunction store. A stored input is an assumption for the later evaluations of the sink: a later item can complete
  the combination with it. The analyzer drops the part also when the combination is not complete (`ap-history.md`
  F67).
* A CALLER-SET `S` FACT (an item on `S` whose premise is not the zero fact) is evaluated: it can report
  (`interpreter.md` D21), and a conjunctive literal stores it as an input. The analyzer does not drop it: it returns to
  the caller through the callee summary, in run 1 also through the FLOW summary and its record (§7.4 THE STATIC BASE)
  (`ap-history.md` F68). As today: today's exit sinks run only on zero-premise edges
  (`JIRMethodExitRuleProvider.kt:18-19`), and only a reached sink drops (`JIRSequentTaintUtil.kt:76-85`). The false
  positives that this keeps (a later sink in the caller sees the state; the exit sink of the caller evaluates it again)
  are expected (`interpreter.md` G2, `ap.md` §11.1).
* SEEDS. After run 1, every run has seeds. The driver gives a `SeedIndex` per (method key, statement):
  * a backward run: the SINK SEEDS, the requirements of the sink witnesses of the DEMAND vulnerabilities after the
    forward run before (§1, §7.3; `ap.md` §9.2). A CONFIRMED vulnerability gives no seed of the hand-off: its state
    is final (§7.5). The backward run also fires, by itself, the sink seeds of a sink alternative whose reversed
    end-fact edge applies to a requirement, also of a CONFIRMED vulnerability (§4.5 THE TRIGGER OF AN END FACT).
    A bound predicate seed reacquires its matching form and visits every active positive leaf, including an
    unwitnessed OR branch. A manual sink gives one requirement per literal. An unconditional sink gives none. The requirement of an `[any]` sink pattern (`ContainsMarkOnAnyField`)
    has the tail `[any]`: the backward run has no `[any-taint]` (`ap.md` W8, §9.2), so its seed is in the demand
    layer (`ap.md` W6), as before F69. The seeds of a witness are at its method key and its statement;
  * a forward restricted run: the SOURCE SEEDS, the unconditional sources that the backward run before reached
    (`ap.md` §6.1 rule 6, §8.11).
* A sink seed enters as a zero-to-fact edge where the zero fact reaches its statement, cut by the field limit. A call
  sink seeds at the rule point `BOUND` of the reversed plan (§4.5). An exit sink seeds in the start rules (§4.4). A
  sink seed of the trigger of an end fact (§4.5) enters in the same way, at the place of its sink.
* A source seed is a filter on the SOURCES: the micro edges whose forward form (`MicroEdge.forward`, §4.9) goes from
  the zero fact to another base. In a forward restricted run, the analyzer applies a source only if `SeedIndex` has
  its forward form for that (method key, statement). The sources are at a statement (a read source, an exit source),
  in the sources stage of a call (§4.5) and in the entry and exit rules. The end facts of a sink and every other micro
  edge apply as usual.
* SOURCE HITS (backward run). When the analyzer applies a reversed source to a requirement and gets a result, it adds
  `(method key, statement, forward form)` to `sourceHits` (`ap.md` §8.11). This holds at each place of a reversed
  source: a statement, the reversed sources stage of a call (§4.5), and the reversed exit and entry rules (§4.4). The
  analyzer records the hit whatever `edges.add` gives: also when the store drops the zero result as a duplicate.
* The backward run has no sink check.
* THE SINK CHECK ON A `*` FACT (a forward restricted run; F72 R4). The check needs a concrete effective mark
  (`ap.md` §4.9). On a fact whose effective mark is `*` or `*∖X` it gives nothing and raises no request. A FLOW
  premise has only edges with abstract marks (§3), so no sink edge has a FLOW premise in its premise set: a sink
  fires only under a concrete premise or the zero fact (R6; PENDING, §11).

### 4.8 The method context

Today `JIRMethodAnalysisContext` holds the alias analysis, the local-variable reachability, the lambda resolutions,
the flow-function caches and the taint rule context. The new core splits it:

* the CACHED part, in `MethodContextCache`:
  * per method: the method graph, the alias analysis (with its own inputs, as today: the local-variable reachability is
    one of them; the core does not read it, §4.3), the lambda resolutions of the prescan and the wired backward graph.
    Every context of the method shares them (today the `EmptyMethodContext` twin analyzer does this);
  * per method: the forward forms of the interpreter that do not read the context of a method key, with their
    reversals (§4.9): the statement summaries and the exit rules (the entry marks of the exit rules come from the
    entry-point sources, which read no context);
  * per METHOD KEY (with its context): the forward forms that read the context, with their reversals: the call plans
    (the callees: the resolver reads the type constraints of the context, `interpreter.md` §3.6) and the entry rules
    (the start filter by the context type, `interpreter.md` §4.3). So a second context of a method never takes the
    callees or the start filter of the first one (`ap-impl.md` DD11);
* the RUN part, made by the run: the rule context bound to the run (the `VulnerabilityStore`, the conjunction store,
  the run mode).

Runs are sequential and each method actor owns its run state. Method/context owners can be shared across callers.
Atomic slot publication and single-flight payload construction prevent duplicate builds; actor ownership alone
is not a cache publication rule.

MANAGED FORM LIFETIME. The physical-method/context-key split above remains. `FormsCache.withForm` publishes lazy
slots atomically and builds one complete forward payload at a time; backward is lazy in that same payload.
`MethodForms` supplies withStatement/withCall/withEntryRules/withExitRules, and `DirectedForms` supplies
withStatement/withCall/withStartRules/withEndRules. Core application, boundary work and call resume stay inside
these callbacks. Getter-only compatibility access is not an application pin. Failed forward build publishes
nothing; failed reverse retains forward; recursive same-slot build fails. Disabled managed caching has no strong
fallback. Logical rule/action/temporary/proof keys survive cache eviction and are independent of run/direction/limit
(interpreter I5).

### 4.9 The interpreter interface and the reversal

The interpreter (`interpreter.md`) gives the FORWARD semantics only. The core calls it only through this interface,
and the core makes every backward form by a reversal. The core owns the order of the actions; the interpreter gives
the micro edges and the rules.

```kotlin
interface Interpreter {
    /** §4.4: the forward entry statement, and the forward exits (normal and exceptional). A method key is never an
     *  empty method (§4.4), so a method key always has its entry statement. */
    fun entryNode(method: MethodKey): CommonInst
    fun exitNodes(method: MethodKey): List<ExitNode>
    /** interpreter.md §4.3 and §4.7: the entry rules (they read the context: cached per method key, §4.8) and the exit
     *  rules (cached per method). */
    fun entryRules(method: MethodKey): RuleStatement
    fun exitRules(method: MethodKey, exit: CommonInst): ExitRules
    /** interpreter.md I1: the touched bases, the micro edges and the type filters of a non-call statement. */
    fun statementSummary(method: MethodKey, statement: CommonInst): StatementSummary
    /** §4.5: the forward plan of a call. Its callees stage has no empty method; a call whose every resolution result
     *  is an empty method has the unresolved stage (§4.4). The callees read the context of `caller`: cached per method
     *  key (§4.8). */
    fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr): CallPlan
    /** a summary edge exists only for these bases (not a local; today isValidMethodExitFact). */
    fun isSummaryBase(base: AccessPathBase): Boolean
}

class ExitNode(val node: CommonInst, val exceptional: Boolean)

/** One micro edge with the forward form of the edge (the edge itself in a forward summary). A SOURCE is an edge
 *  whose forward form goes from the zero fact to another base; the forward form identifies it in both runs (§4.7).
 *  The interpreter gives the target tail `[any-taint]` to a source rule with an `[any]` target (unconditional,
 *  conditional or conjunctive, and an end-fact action; a must); a pass rule with an `[any]` target keeps `[any]` (a
 *  may); a pass rule with an `AnyField` position on its premise side is a rule error (ap.md S15, W6; interpreter.md
 *  I14, D33). A reversed edge keeps its forward form: the backward AP reads its forward target tail, and every result
 *  of an edge whose forward target is `[any]` is in the demand layer (§4.3). `conjunctive`: a reversed literal of a
 *  conjunctive edge (`StatementSummary.reversed`); every result of it is in the demand layer too (§4.3 THE REVERSAL OF
 *  A CONJUNCTION). No other flag tells the rule kind. */
class MicroEdge(val edge: PathEdge, val forward: PathEdge, val conjunctive: Boolean = false)

/** interpreter.md I1: a statement summary. The AP applies it (ap.md §4.2). `typeFilters`: the operand filters, on the
 *  input (interpreter.md §2.1 step 3). `resultFilters`: the lhs and binding-back filters, on the results
 *  (interpreter.md §2.1 step 5, §3.1). One base can have both (`x = x.f`), so they are two maps. */
class StatementSummary(val touched: Set<AccessPathBase>, val edges: List<MicroEdge>,
                       val conjunctions: List<ConjunctiveEdge>, val typeFilters: Map<AccessPathBase, TypeFilter>,
                       val resultFilters: Map<AccessPathBase, TypeFilter> = emptyMap(),
                       val sources: List<SourceRuleOccurrence> = emptyList()) {
    /** ap.md §9.1, §9.2: each edge reversed; a conjunctive edge gives one independent edge per literal (requirements,
     *  each with `conjunctive = true`: every result of it is in the demand layer, §4.3); the identity edge
     *  `b.* → b.*` for each target base that the summary does not touch (A5); the touched bases with the targets;
     *  no type filter. The reversal gives no `[any-taint]` (ap.md W8, §9.1): the reversal of an `[any]` condition
     *  literal (`ContainsMarkOnAnyField`, also of a conjunctive edge) has the target tail `[any]`, and a forward target
     *  `[any-taint]` becomes the premise `[any]`. Each reversed edge keeps its `forward` form (§4.3). */
    fun reversed(): StatementSummary
}

/** The rules of a method boundary (the entry rules, the exit rules): a statement summary with the sources (STATEMENT
 *  mode; its touched bases and keep edges: §4.4 THE BOUNDARY RULE STATEMENTS, interpreter.md §4.3, §4.7), the end-fact
 *  edges of its sinks (GEN mode) and the sinks. At an exit, `summary.sources` holds bound conditional source predicates;
 *  `summary.conjunctions` remains the manual ND-edge adapter (§4.4). The reversal reverses the summary, keeps the sinks as the place of the sink seeds and
 *  drops the context filter. */
class RuleStatement(val summary: StatementSummary, val endFacts: StatementSummary, val sinks: List<SinkRule>) {
    fun reversed(): RuleStatement
}

/** The exit rules (interpreter.md §4.7) and, forward only, the global-state drop (step 3: the analyzer drops the part
 *  of a zero-premise `S` item that satisfies a mark literal of an exit sink, and stores it as the input of that literal
 *  of a conjunctive exit sink; a caller-set `S` item is evaluated, not dropped, §4.7) and the removal of the entry marks
 *  of a zero-premise fact (step 4: every leaf with an entry mark, at any depth, of a zero-premise fact on `this` or
 *  `arg(i)`, also an `[any-taint]` leaf with its exclusion; a deviation from today, which removes only the root leaf:
 *  interpreter.md D35). The reversal drops the two removals (interpreter.md §4.9). */
class ExitRules(val rules: RuleStatement, val globalStateDrop: Boolean, val entryMarks: Set<TaintMark>) {
    fun reversed(): RuleStatement
}

/** ap.md §4.9: one bound positive sink condition at one place. Production NNF forms carry occurrenceId and
 *  predicate; patterns are the owned active positive leaves, with local array choices in the predicate. A witness
 *  identifies an observed proof and its support headers. No eager alternative product is required.
 *  Legacy/manual forms can omit the predicate and keep alternative as their literal-adapter index.
 *  The zero pattern is unconditional. A ContainsMarkOnAnyField leaf has the may tail, including in its seed.
 *  endFacts holds GEN edges: zero.$ -> P.$ (T), or zero.$ -> P.[any-taint] (T) for AnyField (I14). */
class SinkRule(val rule: RuleId, val alternative: Int, patterns: List<Pattern>, endFacts: List<MicroEdge>,
               val occurrenceId: RuleOccurrenceId? = null, val predicate: RulePredicate? = null) {
    val joinKey: Any get() = occurrenceId ?: this
    val patterns: List<Pattern>  // owned pattern paths
    val endFacts: List<MicroEdge>  // owned vector; retain the supplied edge objects
    fun seedPatterns(): List<Pattern>  // empty for the zero pattern; otherwise all active positive leaves
}

/** ap.md §4.6: a conjunctive micro edge `x1.ρ1.t1(T1) ∧ … ∧ xk.ρk.tk(Tk) → z.π.t(T)`, k >= 2: an ND source
 *  (interpreter.md §5.3): in the sources stage of a call (§4.5), or at an exit (§4.4). Each literal and the
 *  target have a concrete mark and no `*` tail (S9, W7). The `[any]` target of a conjunctive source is
 *  `[any-taint]` (ap.md §4.6, S15). A literal reads the exclusion of an `[any-taint]/E` input as part of its location
 *  set (ap.md §4.6). Its reversal gives one micro edge per literal, and every result of it is in the demand layer
 *  (§4.3 THE REVERSAL OF A CONJUNCTION). */
class ConjunctiveEdge(val literals: List<Pattern>, val target: PathFact)

/** ap.md §4.8: the type filter `filter(b, may)` of one base; `may` is prefix-closed (S5; today a
 *  `FactTypeChecker.FactApFilter`). `markPolicy`: the primitive mark policy of interpreter.md §5.1, or null (no level
 *  of the static type is primitive or boxed). */
class TypeFilter(val may: FactApFilter, val markPolicy: MarkPolicy? = null)

/** interpreter.md §5.1 `markPolicyKeeps`: keeps `mark` on the value at `[e]^elements` below the base (`elements = 0`:
 *  the base itself; `elements = k`: the k-th element type). A leaf below a field or a class accessor has no policy. */
fun interface MarkPolicy { fun keeps(mark: TaintMark, elements: Int): Boolean }

/** ap.md §4.7: one primitive cleaner; `mark == null`: every mark. Never on the zero base.
 *  A field action lowers to CleanStep.Field (F74, interpreter.md §5.2). */
class Cleaner(val base: AccessPathBase, val path: PathNode?, val reach: CleanReach, val mark: TaintMark?)
enum class CleanReach { EXACT, BELOW, AT_AND_BELOW }

/** One action of the cleaners stage. A generated field action uses an action-scoped TemporaryKey.
 *  Manual actions use a fresh temporary; neither form can escape into persistent stores.
 *  Each statement uses STATEMENT mode and its field-limit cut. Project away the temporary at the action's end,
 *  in both directions. Reverse the action order and each action's operations (F74). */
sealed interface CleanStep {
    class Clean(val cleaner: Cleaner) : CleanStep
    class Kill(val keepEdges: StatementSummary) : CleanStep
    class Field(val temporary: AccessPathBase, val read: StatementSummary,
                val cleaner: Cleaner, val write: StatementSummary) : CleanStep
    fun reversed(): CleanStep
}

enum class CallPoint { BEFORE, BOUND, ADDED, RETURNED, REWRITTEN, AFTER }

/** A forward-only selection of the inputs of a stage (§4.5). The reversal drops it. */
sealed interface Guard {
    /** The end-fact stage: it applies on a trigger of `sink`, to the zero fact (§4.5 THE END-FACT STAGE). The reversal
     *  drops the selection, but the reversed `END_FACTS` stage keeps `sink`: a reversed end-fact edge that applies
     *  fires the sink seeds of `sink` (§4.5 THE TRIGGER OF AN END FACT). */
    class SinkTriggered(val sink: SinkRule) : Guard
    /** The alias stage skips only NORMAL identity results (§4.5 THE ALIAS GUARD). */
    data object MemoryEffect : Guard {
        fun admits(identityCandidate: Boolean, layer: Layer): Boolean = layer == Layer.DEMAND || !identityCandidate
    }
}

/** The kind of an `Edges` stage (ap-impl.md §23.5). SOURCES and UNRESOLVED hold statement micro edges: the static
 *  exception of run 1 acts on them (THE APPLICATION MODES, below). The source seeds and the source hits act on SOURCES
 *  (§4.7). PASS_OVER: reversed plans only, the identity of the alias bases (§4.5 THE REVERSAL). */
enum class StageKind { BIND_IN, END_FACTS, SOURCES, UNRESOLVED, CONSTRUCTOR, BIND_BACK, ALIASES, PASS_OVER }

/** One stage of a call plan (§4.5): it takes the facts at `from` to `to`. */
sealed interface CallStage {
    val from: CallPoint
    val to: CallPoint
    fun reversed(): CallStage
    /** A binding, the sources, the end facts, the unresolved summary, the constructor identity, the aliases, and (in a
     *  reversed plan only) the pass-over of the alias bases (STAGE mode; `kind`). A guard is a forward-only selection
     *  of the inputs (the sink trigger, AC3 and AC4); the reversal drops it. */
    data class Edges(override val from: CallPoint, override val to: CallPoint, val kind: StageKind,
                     val summary: StatementSummary, val guard: Guard? = null) : CallStage
    /** Both action lists: BOUND -> ADDED cleaners, RETURNED -> REWRITTEN rewriter. Preserve their endpoints,
     *  action order, statement cuts and temporary projection. */
    data class Actions(override val from: CallPoint, override val to: CallPoint, val steps: List<CleanStep>) : CallStage
    data class Callees(override val from: CallPoint, override val to: CallPoint, val callees: List<MethodKey>) : CallStage
}

/** §4.5: the plan of one call. The interpreter gives the forward plan: entry BEFORE, exit AFTER. */
class CallPlan(val touched: Set<AccessPathBase>, val stages: List<CallStage>, val sinks: List<SinkRule>,
               val entry: CallPoint, val exit: CallPoint) {
    /** §4.5 THE REVERSAL: also the PASS_OVER stage `AFTER → BEFORE` of the alias bases. */
    fun reversed(): CallPlan
}
```

What the core uses in each direction:

| Place | Forward | Backward |
|---|---|---|
| start nodes | `entryNode` | `exitNodes` (the zero fact at every one; another fact at the normal ones) |
| start rules | `entryRules` | `exitRules(…).reversed()` and the sink seeds of the exit sinks |
| a non-call statement | `statementSummary` | `statementSummary(…).reversed()` |
| a call | `callPlan` | `callPlan(…).reversed()` |
| end nodes | the normal `exitNodes` | `entryNode` |
| end rules | `exitRules` | `entryRules(…).reversed()` |

A reversal of a statement summary is `Reverse.Stmt.rev` of the Lean model; the reversal of the bindings is
`Reverse.Call.rev`. The core caches the forward forms and their reversals in the `MethodContextCache` (§4.8): per
(method, statement) for a statement summary and the exit rules; per (method key, statement) for a call plan; per method
key for the entry rules. The
AP operations of `ap.md` §4 apply the micro edges; the interpreter never applies them. `PathFact`, `Pattern` and
`PathEdge` are the types of `ap.md` §3.4 and §4.1. `SinkRule`, `ConjunctiveEdge`, `TypeFilter` and `Cleaner` (above)
are the short forms of the sink of `ap.md` §4.9, the conjunctive edge of §4.6, the type filter of §4.8 and the cleaner
of §4.7.

THE APPLICATION MODES. The core applies a `StatementSummary` in one of three modes. The place of the form gives the
mode, not a field of the form:

| Mode | Forms | Rule |
|---|---|---|
| STATEMENT | a statement summary (`statementSummary`), the rule summary of a method boundary (`RuleStatement.summary`, with the touched bases and keep edges of §4.4 THE BOUNDARY RULE STATEMENTS), the kill on `S` (`CleanStep.Kill.keepEdges`), the read and write of a field cleaner (`CleanStep.Field`) | `interpreter.md` §2.1 steps 2 to 5: a fact on an untouched base passes; a fact on a touched base keeps only what an edge gives |
| STAGE | the summary of a call stage (`CallStage.Edges.summary`) | only the edges give results. The touched bases of the plan (`CallPlan.touched`) do the pass-over (§4.5). A stage summary has every base of its edges in its touched set, so its reversal adds no identity edge (the reversed plan has the PASS_OVER stage of the alias bases instead, §4.5) |
| GEN | the end-fact edges at the method boundaries (`RuleStatement.endFacts`, the `SinkRule.endFacts` of an entry or exit sink) | the input stays where it is; the edges add results (`interpreter.md` §4.1 END FACTS, §4.7 step 2). An end-fact edge reads the zero fact: on a trigger of its sink, it applies to the zero fact in the layer of the sink edge or of the combination (§4.5 THE END-FACT STAGE). Reversed, it applies to every requirement and fires the sink seeds of its sink (§4.5 THE TRIGGER OF AN END FACT). At a call the end facts are the end-fact `Edges` stage (`END_FACTS`, STAGE mode, on the zero fact, on a trigger, §4.5), whose reversal adds no identity |

F78 STATIC ADAPTER. The special S-root component takes ordinary code transfer, with its deeper-read
suppression, but no rule evaluation in any mode or rule stage. Ordinary packed field branches keep the complete
stages. No normal abstract class slot or Position output is introduced. Root-origin summary/record readers use
structural ownership and the untouched identity frame (AP §4.10), not unrestricted raw root outputs. Generic
abstract excluded-mark targets collect their origin marks as F78 requires; fact-result exclusions stay intact.

---

## 5. The communication pipeline

### 5.1 Events and channels

Each runner has one channel and one local priority queue, as today. The events:

| Event | Goes to the channel of | Handler | `ap.md` | From today |
|---|---|---|---|---|
| `Start(root)` | the runner of `root` (sent by the driver) | `addRootZero` | §6.1 | the `MethodWithContext` start event |
| incoming added input | the runner of `callee` | direct initial emission, then release payload | E1, E2 | replaces retaining `LinkIn` payloads in a callee store |
| `ZeroIn(callee)` | the runner of `callee` (backward) | `addZeroEntry` | §9.2 `zin` | `ExternalInputFact.InputZero` |
| request batch `(method, slot -> marks)` | the runner of `method` (run 1) | filter effective R, merge remaining marks into P and schedule Work if nonempty; commit at its method barrier | E5, F78 | independent marks, no caller mutation on publisher thread |
| `Delivery(callee, publications)` | the SUBSCRIBED runner; the publisher's thread sends it | `SubscriptionManager.onDelivery` | E4 | `NewSummaryEdgeEvent` |
| fresh request delivery (run 1) | the subscribed caller runner | batch two-slot matching over current N2; queue concrete answers/upstream sets | E5, F78 | no full query or callee input copy |
| concrete answer delivery (run 1) | the callee emitter runner | emit the evaluated full initial; exact initial dedup | E3 | no supplier payload retained |
| `Work(analyzer)` | the runner of the analyzer (its local queue) | `step` | | the `MethodAnalyzer` event |

Removed:

* the side-effect requirements and summaries, and the mark-unfold requests (`TaintMarkFieldUnfoldRequest`): the
  requests of run 1 replace them (§4.6);
* the delayed-analysis events (`MethodAnalysisDelayed`, `DelayedAnalysisResume`): the field limit replaces them;
* `LambdaResolvedEvent`: the prescan resolves every lambda before run 1 (§9). The call plan reads the lambdas from the
  context cache.

Rules:

* E-1. A handler sends events only through the channels, or by the direct calls that §6.1 permits.
* E-2. N2 retains the original caller reference `(full method key, premise key, caller layer, call statement)`
  and independent input/link layer. Initial emission has no caller dimension. Eligible climbs carry the original
  caller method/initial, not a reconstructed abstraction of the final bound value.
* E-3. Duplicate arrivals are permitted. Interest, initial and N2 logical deduplication drop only equal obligations
  under their full keys. Removing emitter admission can repeat matching work, not starts. `edges.add` keeps AP §8.1's
  subsumption contract. No full callee input seen-set may be introduced to recover the removed gate.

### 5.2 The storage with subscription (callee side)

F78 REQUEST EXTENSION. One SummaryStorage per full method key owns authoritative RequestStorage R. Its initial
view borrows the same frozen B as the conservative closed plan; only dynamic residual D is allocated for admissions.
There is no copied precomputed map in P/D or per subscriber. Bind this view before any subscription/input; do not
publish an initial B notification wave. The emitter borrows effective R; it owns only write-only empty-base pending
P of the same class. Every producer filters effective R before P insertion. Commit P then replace it
at the method queue-empty boundary (§4.6). Commit synchronizes union, computes fresh slot/mark sets and queues
immutable grouped notifications outside its lock. It does not retain P or run handlers inline. Registration
precedes N2 admission and current-R/input reads; commit precedes notification. Use one linearizable registry/read/
admission boundary. A late subscriber's new input is covered by current-R/emitter handling; an earlier subscriber
receives fresh dynamic notification. New TAINT/FLOW handling reads all of R, so binding B needs no notification.
Delayed wide activation remains a fresh dynamic contribution. Initial/interest duplicates are harmless.
Normal publication P1–P6 remain separate.

The following sketch illustrates the unchanged publication channel only; F78 R/commit and static reader guards are normative above.

One `SummaryStorage` per method key of the run (O2). It keeps the publications in a path trie keyed by
`base :: premise path`, one entry per member of a premise set, and the list of the subscribed runners. An entry keeps
the tail of its member and the exclusion of an `[any-taint]` member: an `[any-taint]` premise and an `[any]` premise at
one path are two entries (§4.1).

```kotlin
class SummaryStorage(val method: MethodKey) {
    private val lock = Any()
    private val published = PublicationIndex()                  // path trie, ap.md §8; delta on insert (T4)
    private val subscribers = ConcurrentLinkedQueue<SubscriptionManager>()

    /** Only the runner of `method` calls it. P2: insert, then notify. */
    fun publish(pubs: List<Publication>) {
        val delta = synchronized(lock) { published.addAll(pubs) }            // the new part only
        if (delta.isEmpty()) return
        for (s in subscribers) s.notify(method, delta)                         // one Delivery per runner
    }

    fun addSubscriber(s: SubscriptionManager) { subscribers.add(s) }

    /** P3: a linearizable read. The candidates for the added fact `a`; complete for `matches` (§5.3). */
    fun candidates(a: Pattern, config: RunConfig): List<Publication> =
        synchronized(lock) { published.candidates(a, config) }
}
```

P3 asks for the lock on the read (Appendix A gives the defect of today). An implementation may replace the lock by an
immutable snapshot that the writer publishes with a volatile write.

### 5.3 The subscription manager (caller side)

F78 REQUEST REPLAY. Existing caller N2 keeps actual original inputs and headers. A fresh request batch visits
current eligible inputs using StaticSlot(C,f)/BaseSlot(b), ignoring E/mx only for this request join. Fresh FLOW
input/growth reads R; fresh TAINT is answered by LinkIn emission, so no duplicate N2 fresh-TAINT request arm is
needed. An exact-only delta can clear E without changing the admitted key. Ordinary summary matching/replay keeps
its original-header/fresh-delta requirements below.

| ID | Required request condition |
|---|---|
| QP1 | Bind shared B before any subscription/input; register subscriber and insert N2 input before LinkIn; commit R before queued fresh notification. |
| QP2 | New input/full effective R and new dynamic R/current N2 cover both arrival orders, including B without an initial wave. Initial dedup never removes another caller's changed-origin request route. |
| QP3 | Validate K6, answer TAINT independently, forward only actual normal abstract FLOW origins. Ignore E/mx for requests, not for ordinary summaries. |
| QP4 | Use finite owner-local visits and grouped effects. Queue all results, including local results, before directory mutation. No new suspended traversal/history protocol. |
| QP5 | Reserve all events/Work before visibility. Retain Work through commit(P), P replacement and sends; incomplete runs certify no closure. |

There is no emitter supplier store, answer memo, query-role relation, request-by-input seen product or subscriber
copy of cumulative R. F78 §§5–7 defines static frame, normal method barrier and the complete request protocol.

One `SubscriptionManager` per runner. Per callee, it keeps the subscriptions of the unit in a path trie keyed by
`base :: added fact path` (`ap.md` §8.4).

The N2 representation stores one cumulative entry per full caller reference and admitted conclusion group.
Its immutable Facts contain every accepted leaf for that key. A stable handle occurs once at each relevant
index position. There is no permanent list or index of historical delta parts. The admitted key is taken
from the incoming group, not from the fresh delta: a must group with nonempty E can grow an exact-only
delta whose normalized E is Empty. Zero-only subscriptions keep their separate full-reference, first-wins rule.

`CalleeSubscriptions.add` returns a fresh immutable Subscription delta for replay, or null on no growth.
Replay captures this delta when it is admitted. A delayed replay must not read a later cumulative value.
Delivery snapshots the current candidate values before any application callback. It selects each candidate
with the same full `matches` operation as replay. A callback can add a subscription; that new growth has
its own registered replay and cannot depend on a live candidate iterator.

```kotlin
// SubscriptionManager excerpts. active(), byCallee and replayRecords are its owner/read helpers.
fun subscribe(sub: Subscription) {
    val port = active()
    val storage = port.summaryStorage(sub.callee)
    val entry = byCallee.getOrPut(sub.callee) {
        CalleeSubscriptions(port.ops).also { storage.addSubscriber(this) }   // P1: register first
    }
    val fresh = entry.add(sub) ?: return
    port.steps.replay { replay(storage, fresh) }                            // retain this immutable delta
}

private fun replay(storage: SummaryStorage, fresh: Subscription) {
    val port = active()
    for ((pub, member) in storage.candidates(fresh, port.config.mode))       // P3
        applyMatch(fresh, pub, member)
    if (!fresh.zeroOnly && port.config.restricted) replayRecords(fresh)     // R3/R4: previous-run native/view reads
}

fun notify(callee: MethodKey, pubs: List<Publication>): Boolean =
    active().post(RunEvent.Delivery(callee, pubs))                            // no caller-state read here

fun onDelivery(callee: MethodKey, pubs: List<Publication>) {
    val port = active()
    val entry = byCallee[callee] ?: return
    for (pub in pubs) for (member in 0 until pub.premise.size)
        for (part in entry.candidates(pub, member, port.config.mode))        // owned cumulative snapshot, P6
            applyMatch(part, pub, member)
}

private fun applyMatch(part: Subscription, pub: Publication, member: Int) {
    val port = active()
    val selected = matches(part, pub, member, port.ops, port.config.mode) ?: return
    port.applier(part.caller).applySummary(part.copy(added = selected), pub, member)
}

/** P4: replay and delivery use the same complete selected-Facts operation. */
fun matches(sub: Subscription, pub: Publication, member: Int, ops: ApOps, mode: ApMode): Facts? =
    if (sub.zeroOnly) sub.added.takeIf { pub.premise.isZero }
    else ops.satisfying(sub.added, pub.premise.member(member), mode)

/** ap.md §8.7 R4, Lean `DR.retRec`: a record applies by `applicable` or by `inside`. A record with an `[any-taint]`
 *  premise that only `applicable` accepts gives its results in the demand layer (§4.2 `applyRecord`; Lean
 *  `AnyTaintEx.DRX.retRec` with `recLayerX`). Apply that demotion after the returned stages, before the call
 *  field cut (§4.5). In a restricted run `inside` reads the exclusions of both sides
 *  (ap.md §4.3; Lean `AnyTaintEx.satX`). */
fun recordApplies(p: Pattern, a: Pattern): Boolean = applicable(p, a) || inside(p, a)
```

`byEntry` and `byExit` use `around` (AP §8.7 R2). For a reader in the other
direction, `rec.reversedAt(a)` computes one transient `RecordLeafView` per
permitted nearby leaf. AP §8.7 R3 owns its shape, mark, exclusion, and layer.
Use a `Pattern` premise, not an `InitialAp`. Apply it after the ordinary record
match `applicable || inside`; do not re-use restricted callee `satisfies` alone.
An exact field requirement below a star view applies by `applicable` even when
`inside` is false.

For `P.$(U)→x.f.[any-taint]/E(T)`, the view is
`x.f.*/E(T)→P.$(U)`, normal. Preserve `E`; discard the incoming suffix at the
exact target. For a must forward premise, replace its old tail by may `[any]`,
forget its old exclusion, and force every view result to DEMAND. Such a view
cannot produce a persisted normal backward record. No backward fact has
`[any-taint]`. Generic micro-edge reversal remains AP §9.1's operation.

The exact-premise view has a checked local annotated-converse certificate.
Actual-flow exactness also needs S14 exactness of the native input record.
Current native-record and generic-reversal proofs do not certify concrete-star
views as native initial kinds. Full read-view/closure and X integration remain
open in [proof-status.md](proof-status.md). F77 omits callee demand for every
R1-eligible native normal singleton summary, including must-premise records.
Its read view can produce caller DEMAND without requesting callee reanalysis.
Select before restriction and retain the whole raw record with all exclusions.

A must-premise needs every location of it, so a summary of an `[any-taint]` premise applies only to an added fact that
the premise lies INSIDE, with the exclusions of both (`ap.md` §4.3; Lean `AnyTaintEx.SatInsideX`: the exactness needs
it, `satX` has it, `AnyTaintEx.satX_inside`). An `[any-taint]` premise occurs only in a forward restricted run, and
there `matches` is `inside`; so `matches` needs no other test. A FLOW PREMISE (the mark `*`, a restricted run since
F72) is satisfied by `inside` or by `applicable` (`ap.md` §4.3; F72 R3; Lean: `Abs.satW` in the base model; the X-tail extension is pending): it passes the mark
of the added fact, as the policy premise of run 1 and a `*`-premise record do. `satisfies` reads the mark of the
premise, so `matches` needs no other test. A concrete premise is satisfied by `inside` only. A normal edge of a
must-premise is END-EXACT: every
admitted location of its conclusion gets its value from some admitted location of the premise. It is not exact pair
by pair (`AnyTaintEx.EndExactX`; `AnyTaintExact.CexApp.record_not_pair_exact`).

The index queries (`ap.md` §8; keys `base :: path`):

| Test | Replay (publications for a new `a`) | Delivery (subscriptions for a new `j`) |
|---|---|---|
| run 1, `applicable(j, a)`: `a` at or below `j` | `lookupPrefixes(a.base :: a.path)` | `lookupExtensions(j.base :: j.path)` |
| restricted, `inside(j, a)`: `j` at or below `a` | `lookupExtensions(a.base :: a.path)` | `lookupPrefixes(j.base :: j.path)` |
| restricted, canonical FLOW: overlap; other FLOW: `inside(j, a)` or `applicable(j, a)` | both lookups of the two rows above, on the publications of FLOW premises | both lookups of the two rows above, for a new FLOW premise `j` |
| a record, `applicable` or `inside` | `around(a.base :: a.path)` on `byEntry`, and on `byExit` with the reversed premise as the key | (records are read-only) |

Each lookup returns every entry that its exact test accepts (`PipelineStore.replay_run1`, `deliver_run1`,
`replay_restricted`, `deliver_restricted`, `record_lookup`). For a FLOW premise the union of the two candidate sets
contains every accepted entry, including the canonical overlap branch. Both paths then call `matches`; the
candidate queries stay unchanged.

The subscription insert deduplicates exactly. It never drops an accepted logical leaf because a stored leaf subsumes it.
Reason: the run-1 test is not monotone in the added fact (a fact above `j` does not satisfy `j`). So a subsumed
subscription can match a publication that the stored one does not match. The same holds for the semantic supplier relation retained in N2; no emitter link store is implied.
Cumulative storage is an exact leaf union inside one full admitted key; it does not use spatial subsumption
or annotation folding to remove a subscription.

P5 is defined on decoded logical subscriptions, not on delta object identity. Each accepted leaf remains in
its full-key cumulative entry until run release. A transient replay delta can be released after replay.
[CumulativeSubscriptions.lean](lean/ApSpec/CumulativeSubscriptions.lean) proves the exact accepted-leaf
encoding after finite growth, logical persistence, fresh-delta union and position/effect lifting. The old
`Pipeline` definitions remain the reference logical protocol. Instantiating its no-loss theorem with the
new storage also needs the current AP selection/transfer law. The generic lifting theorem exposes this
condition; it does not prove it for every current AP shape or certify whole-engine closure.

THE PROTOCOL CONDITIONS. The model step `Step` (§5.5) builds in P1 to P6, A3 and the exact part of E-3 (the
subsumption part is `Pipeline.StepD`, §5.5). P1 to P4 are necessary: each has a counterexample in
`PipelineProofs.lean`, a reachable quiescent state that misses an object of the closure.

| # | Condition | Current implementation | In the model |
|---|---|---|---|
| P1 | REGISTER BEFORE READ: the subscriber registers its handler on the storage before the replay read. | yes (the first `getOrPut` registers) | `proc`; counterexample `PCex.cex_P1` |
| P2 | INSERT BEFORE NOTIFY: the publisher inserts the publication before it reads the handler list. | yes (`SummaryStorage.publish`) | `proc`, `notify`; counterexample `PCex.cex_P2` |
| P3 | LINEARIZABLE READ: the replay read sees every publication whose insert came before it (A2). | read and publication insertion use the same `SummaryStorage` lock | `replay`; counterexample `PCex.cex_P3` |
| P4 | ONE MATCH FUNCTION: the replay and the delivery use the same exact test on complete candidate sets. | shared `matches`; cumulative candidates are captured before callbacks | `replay`, `deliver` both use `join`; counterexample `PCex.cex_P4` |
| P5 | LOGICAL NO REMOVAL: handler registration, accepted subscription leaves under their full keys, and the stored publication denotation stay until the run ends. Historical subscription delta objects may be released. | cumulative subscriptions retain exact accepted leaves; teardown is after join | reference `Step` has no logical removal; `CumulativeSubscriptions.growth_trace_encodes` and `accepted_persists` establish the cumulative subscription representation |
| P6 | MATCH AT DELIVERY: a delivery matches against the subscriptions of the runner when the runner handles it. | yes (`SubscriptionManager.onDelivery`) | `deliver`. The no-loss theorem would also hold with a match at notification time. The code needs P6 because the subscriptions are local to their runner (O3). |

### 5.4 Summaries with several premises (E6)

A publication `{j1, …, jk} → g` (`ap.md` §4.6) reaches the caller by the replay or the delivery like every other
publication. It can be NORMAL or DEMAND. The caller combines it in its conjunction store (`ap.md` §8.9):

* Key: (callee, premise key, layer of the publication, call statement). Value: the merged conclusion that has arrived
  so far, and per premise index `m` the checked bindings at that call statement whose added fact satisfies `jm`.
  A binding is `(full CallerRef, linkLayer)`; it contains no matched root or cumulative Facts version.
* Create a binding only after full `matches`, the nonzero-member guard and the TAINT-input guard. It goes under
  EVERY index that the input satisfies. No member is the zero fact (`ap.md` §4.6: a conjunction
  drops the zero fact), so the zero subscription never takes part (`PipelineNDZ.clDNz_ndpub_no_zero`,
  `clDNz_ndpub_zero_sub`; run 1; a restricted run is argued, `ap.md` §11.2). The backward run has no summary with several
  premises: it reverses a conjunction into one micro edge per literal, with every result in the demand layer
  (`ap.md` §9.1, §9.2; §4.3 THE REVERSAL OF A CONJUNCTION).
* A new binding under an index: combine it with the stored bindings of the other indexes (one per index,
  every combination), and apply the stored conclusion to each full combination.
* A new conclusion delta: apply it to every full combination.

The combination is NORMAL if the publication and every participating added fact on its link are NORMAL;
otherwise it is DEMAND. The binding back and field limit then apply as usual. Several premises do not force DEMAND,
and selecting the publication for the next-run hand-off does not change its layer (`ap.md` §8.5).

Union all full caller premise keys and keep the ordered binding references for resume/support scope. The result
layer joins publication and link layers. `CallerRef.callerLayer` remains in the full reference and resume
context; it is not a contribution to this AP layer join. W7 makes the target uncorrelated, so successfully
matched input roots do not affect this transfer. Canonical result construction still applies W8's demand
normalization and clears E as required. Do not use this projection before matching or in source/sink joins,
which can retain matched facts for different purposes.

`CumulativeSubscriptions.projected_choices` proves the ordered Cartesian binding quotient in both
directions. `combination_effects_quotient` preserves every full ordered combination effect;
`combination_root_insensitive` proves equal output descriptors for equal checked bindings. These are local
representation results. They retain all conclusion growth, full contexts, caller premise keys, layers,
call sites and member slots. They do not prove the current AP matcher or whole pipeline instance.

The key does not contain the conclusion. So a combination meets every delta of the conclusion, in any order of the
replays and the deliveries. Both sides of this join are in the caller.

### 5.5 The no-loss theorem

The protocol requires: at quiescence, every satisfying processed
(subscription, publication) pair has its application processed, and every enabled
local rule has fired. Replay and notification can race; their union must cover
every pair. Exact duplicate suppression can process a pair once.

The constructive generic protocol proof and its encoding conditions are in
[proof-status.md](proof-status.md). F78 finite protocol/frame results and their checked scope are listed in [run1-request-proofs.md](run1-request-proofs.md).
The complete F72/F75/F78 JVM/packed-AP pipeline instance remains a separate obligation; generic summary completeness
alone does not prove AP closure coverage, the frame adapter or emitter-store removal.

---

## 6. Scheduling and the end of a run

### 6.1 The runner loop

The loop of today stays: an unlimited channel, a local priority queue, a quantum of `RUNNER_STEPS_QUANT` steps per
`Work` event, and the priority of the analyzers with zero-fact work. Changes:

* The events of §5.1 replace the old events.
* Two direct calls are permitted, as today:
  1. the analyzer calls `subscribe` of its own `SubscriptionManager`. The replay then calls `applySummary` and
     `applyRecord` of the SAME analyzer. These only add to its edge store and its worklist;
  2. the analyzer may consume an added input in a callee of the same unit (§4.5). This performs initial admission and queues work/events; it does not retain a caller-separated supplier or run the callee to closure.
     A recursive input to the same analyzer is permitted under the same ownership and no-mutating-visit rules.

  Every other interaction between two analyzers is an event.
* The priority keys must not change while the event is in the queue. Today `EventComparator` reads `analyzerSteps` and
  the zero-work flag, which change. This breaks the heap order but loses no event.

The runner interface that the analyzer uses:

```kotlin
interface RunnerPort {
    val config: RunConfig
    val interpreter: Interpreter
    val contexts: MethodContextCache
    val vulnerabilities: VulnerabilityStore
    /** Q1: count, then put into the channel of the target runner (§5.1: by `callee`, `method`, or THIS runner for
     *  a Delivery, which the publisher's thread sends through the subscriber's `notify`). */
    fun send(event: RunEvent)
    fun enqueue(analyzer: RunMethodAnalyzer)               // W1: a Work event if the analyzer is not queued
    fun analyzer(key: MethodKey): RunMethodAnalyzer        // this unit only; made on demand
    fun subscribe(sub: Subscription)                       // the SubscriptionManager of this runner
    fun summaryStorage(key: MethodKey): SummaryStorage     // RunManager.summaryStorage: any unit (O2)
    fun link(callee: MethodKey, link: Link)                // same unit: a direct addLink; else LinkIn
}
```

### 6.2 The in-flight counter

The counter protocol of today stays, with one counter object per run:

* Q1. Increment before send: the counter goes up before the event goes into a channel or a local queue.
* Q2. Decrement after the handler: the counter goes down after the handler ends, after all its sends.
* Q3. A guard during the start: the run counts one phantom event while it sends the `Start` events.
* Q4. One counter per run: an event of an old run cannot change the counter of a new run.

Local work is inside an event:

* W1. An action that adds a worklist item, a pending publication or deferred request work to an analyzer with `queued = false` sets `queued`
  and sends `Work` (counted by Q1). This includes the direct `addLink`, the replay's `applySummary` and a request
  answer. A request-only arrival reserves Work before its parent event releases its reservation.
* W2. When the quantum ends and work is left, the runner puts the analyzer back into its local queue. The `Work`
  event stays counted.
* W3. At method fact-queue-empty, flush pending summaries, call `summaryStorage.commit(temporary)`, then
  replace temporary with a new RequestStorage. Commit queues immutable fresh notifications and does not reenter
  actor handlers. Keep the same Work counted through all three actions and secured sends. A quantum yield can
  retain publications/P under that token. End Work only when facts, pending publications/P and active submission
  are empty, with required child events already reserved. Requests arriving as queued events after the final
  owner check carry their own count and schedule Work. No unit barrier or new scheduling policy is selected.

THEOREM (`Pipeline.Quiesce.creach_inv`, `cnt_zero_iff`, `done_iff`, `done_final`). With Q1 to Q3, the counter equals
the number of events in the channels and the local queues plus the number of running handlers. So it is zero exactly
when no event waits and no handler runs, and after that no step is possible. `Pipeline.Quiesce.bad_early_done`: if the
counter goes down when the handler STARTS (not Q2), the run can end while an event is still pending. With W1 to W3,
zero means quiescence (argued, §11).

### 6.3 Abnormal end

* A timeout, the memory guard (`MemoryManager`) or an exception makes the run INCOMPLETE. The `RunManager` returns
  the status: `COMPLETE`, `TIMEOUT`, `OOM` or `FAILED`.
* AN EXCEPTION IS EVERY `Throwable`, an `Error` too (for example a JVM `OutOfMemoryError` or a `StackOverflowError`).
  In a runner, in the code of a run on the caller thread, or at the barrier (§7.1), it gives `FAILED` (phase 3:
  `EXCEPTION`, §9), and the driver returns the report so far. A JVM `OutOfMemoryError` does not give `OOM`: the status
  `OOM` comes only from a memory guard, the guard of a run or the guard of the barrier (§7.2 B4) (`ap-history.md` F68).
* THE FIRST END WINS. The status of a run is set once, by a compare-and-set. The quiescence sets `COMPLETE` in the
  same way. So a late end (the memory guard or the timeout while the `RunManager` joins the runners) does not turn a
  complete run into an incomplete one. One exception: the join overrides the first end. A runner that does not stop
  makes the run `FAILED`, also after the quiescence, because its stores can still change.
* EVERY CANCELLATION HAS A KNOWN CAUSE. The status is set before every cancel, so every cancel has a known cause: the
  timeout, a memory guard (of a run or of the barrier), or a runner failure. Three ends of a run cancel the analysis:
  the timeout of the run (`TIMEOUT`), the memory guard of the run (`OOM`) and a runner exception (`FAILED`). Each one
  is `RunManager.fail(status)`: it first sets the status of the run by the compare-and-set, then cancels the
  `Cancellation` and completes the run. After the quiescence or an earlier end, `fail` does nothing (the first end
  wins). So a runner exception ends the run at once, with no wait for the timeout, and the other runners stop at their
  next checkpoint. After a cancel, a handler can throw `Cancellation.Cancelled` (a checkpoint of the `ApManager` or of
  the alias analysis). The runner loop catches it and only stops: the run keeps the status of its cause. So the status
  always gives the real reason, and there is no `CANCELLED` status and no external cancel. The `RunManager` activates
  its `Cancellation` when it is made (in its constructor), before a runner starts, so a cancel is never undone by a
  later activation. The guard of the barrier also cancels, with the status `OOM` of the iteration; no runner is alive
  then (§7.2 B4).
* A throw in the code of a run on the caller thread (for example the routing of the `Start` events or the wait for the
  end) is `fail(FAILED)`: the `RunManager` still joins its runners and returns `FAILED`.
* The hand-off of an incomplete run is not complete. The theorems of `ap.md` §6.6 do not apply to the runs after it,
  so the driver does not start a run after an incomplete run (§7.1). The incomplete run adds nothing to the report
  (§7.5).
* Each run has its own coroutine scope with a `SupervisorJob`. The exception of one runner does not cancel the scope
  of a later run. (Today one failed runner cancels `analyzerScope` for every later run.)
* The `RunManager` joins every runner coroutine before it returns, on every exit, also when its own code throws. If a
  runner does not stop, the run ends as `FAILED` (the driver reads none of its stores), the analysis stops, and no
  later run starts. (Today a runner that does not stop in `cancellationTimeout` stays and can change the next run.)
* A cancellation must also complete the run. Today every `cancel()` of the analysis comes with its status
  (`updateFailureStatus(TIMEOUT)` or `(OOM)`) and with `analysisCompletion.complete`; keep the three calls together
  (`RunManager.fail`).

---

## 7. The iteration driver and the hand-offs

### 7.1 The run sequence

```kotlin
interface IterationPolicy {
    fun fieldLimit(runIndex: Int): Int                               // not decreasing (ap.md W3); selected F78 JVM run 1: 1
    /** Asked only after a complete FORWARD run that no stop rule ended. `frontiers`: the frontier of every complete run
     *  so far, in run order; the last one is that of `run` (§7.8). A practical stop strategy reads them (out of
     *  scope). */
    fun continueAfter(run: RunConfig, result: RunResult, frontiers: List<Frontier>): Boolean
    /** The timeout of one run. `remaining`: the ONE budget of the analysis minus the time so far (§0). Default: the
     *  run gets the rest of the budget. */
    fun timeout(run: RunConfig, remaining: Duration): Duration = remaining
}

/** What one run and its barrier give: the next run, or the end of the analysis. */
sealed interface Next {
    class Run(val config: RunConfig) : Next
    class End(val status: RunStatus, val reason: EndReason = EndReason.ABNORMAL) : Next
}

class IterationDriver(private val policy: IterationPolicy, private val shared: SharedObjects,
                      private val budget: Duration) {                // ONE budget for the whole analysis (§0)
    fun analyze(roots: List<MethodKey>): Report {
        val start = TimeSource.Monotonic.markNow()
        val report = ReportBuilder()                                  // §7.5
        val frontiers = ArrayList<Frontier>()                         // §7.8: one per complete run
        var index = 1                                                 // the current run: AnalysisEnd names it
        fun end(status: RunStatus, reason: EndReason) = report.build(AnalysisEnd(status, index,
            if (index % 2 == 1) Direction.FORWARD else Direction.BACKWARD, reason))
        try {                                                         // ONE GUARDED REGION: the whole loop body (§6.3)
            require(policy.fieldLimit(1) == 1) { "F78: selected JVM run 1 needs field limit exactly 1" }
            var config = RunConfig(1, policy.fieldLimit(1), demand = null, records = shared.records.view(),
                seeds = SeedIndex.EMPTY, roots = roots)
            while (true) {
                index = config.index
                when (val next = runOnce(config, report, frontiers,
                        policy.timeout(config, budget - start.elapsedNow()))) {
                    is Next.Run -> config = next.config               // the result of the run is garbage now (§7.6)
                    is Next.End -> return end(next.status, next.reason)
                }
            }
        } catch (e: Throwable) {                                      // an Error too: FAILED, never OOM (§6.3)
            logger.error(e) { "Run $index failed; the analysis ends with the report so far" }
            return end(RunStatus.FAILED, EndReason.ABNORMAL)          // the report so far: the earlier runs stay
        }
    }

    /** One run and its barrier. The `RunResult` is a local of this frame, so no live slot keeps it during the next run
     *  (§7.6). */
    private fun runOnce(config: RunConfig, report: ReportBuilder, frontiers: MutableList<Frontier>,
                        timeout: Duration): Next {
        val result = RunManager(config, shared).run(timeout)          // a new engine (§2); it joins its runners (§6.3)
        if (result.status != RunStatus.COMPLETE)                      // §7.5: adds nothing, refutes nothing
            return Next.End(result.status)
        // THE BARRIER (§7.2): no runner runs now. Its own memory guard (B4): a hit cancels the Cancellation, and the
        // barrier stops at its next checkpoint.
        val guard = MemoryManager(shared.refManager, BARRIER_MEMORY_THRESHOLD) { shared.cancellation.cancel() }
        val next = try {
            guard.runWithMemoryManager { barrier(config, result, report, frontiers) }   // soft references stay enabled
        } catch (e: Cancellation.Cancelled) {
            Next.End(RunStatus.OOM)                                   // the barrier guard is the only canceller here
        }
        return if (shared.cancellation.isActive()) next else Next.End(RunStatus.OOM)   // a hit after the last checkpoint
    }

    private fun barrier(config: RunConfig, result: RunResult, report: ReportBuilder,
                        frontiers: MutableList<Frontier>): Next {
        val forward = config.direction == Direction.FORWARD
        if (forward) {
            validateIfConfigured(result)                               // §7.5: absent evidence leaves confirmed=false
            report.add(config, result)                                // §7.5: unverified candidates are DEMAND
        }
        shared.records.persist(config, result)                        // ap.md §8.7 R1: eligible raw normal singleton summaries
        val handOff = handOffOf(config, result, report)               // §7.3, §7.4: the demand edges and the seeds
        val frontier = frontierOf(config, result, handOff, report)    // §7.8
        frontiers += frontier
        logger.info { frontier.toString() }                           // THE FRONTIER LOG (§7.8), every complete run
        if (forward && !report.hasDemandVulnerability())              // no DEMAND vulnerability, so no seed
            return Next.End(RunStatus.COMPLETE, EndReason.STOP_RULE)
        if (forward && result.demandLayerEdges == 0L)                 // includes incoming DEMAND contribution counters; no stored links
            return Next.End(RunStatus.COMPLETE, EndReason.NO_DEMAND_EDGE)
        if (forward && !policy.continueAfter(config, result, frontiers))
            return Next.End(RunStatus.COMPLETE, EndReason.POLICY)
        return Next.Run(nextConfig(config, handOff))
    }

    private fun nextConfig(config: RunConfig, handOff: HandOff): RunConfig {
        val limit = policy.fieldLimit(config.index + 1)
        require(limit >= config.fieldLimit) { "ap.md W3: the field limit must not decrease" }
        return RunConfig(config.index + 1, limit, handOff.demand, shared.records.view(), handOff.seeds, config.roots)
    }

    companion object {
        /** Today's threshold of the confirmation: `TRACE_GENERATION_MEMORY_THRESHOLD`
         *  (TaintAnalysisUnitRunnerManager.kt:645). */
        const val BARRIER_MEMORY_THRESHOLD = 0.99
    }
}
```

* The run sequence is `ap.md` §6.6: run 1 (forward), run 2 (backward), run 3 (forward), and so on.
* THE STOP RULES (`ap-history.md` F70). After a complete forward run, the barrier applies these rules, in this order:
  1. `STOP_RULE`: after the run the report has NO DEMAND VULNERABILITY (§1): every vulnerability that the run reports
     is CONFIRMED, by this run or by an earlier complete forward run (`ReportBuilder.hasDemandVulnerability`, §10).
     Then the hand-off gives no sink seed (§7.3), so the next backward run has no requirement except the zero fact.
     Confirmation is final (§7.5). Under AP §6.6's no-loss contract, every real
     vulnerability is confirmed at this stop. The full F77 contract and the
     claim that later runs repeat only zero work and records remain open (§7.7).
  2. `NO_DEMAND_EDGE` (the user's rule, `task.md`): the run has NO DEMAND-LAYER OBJECT: no demand-layer edge delta,
     no demand-layer summary delta and no admitted DEMAND input contribution (`ap.md` §8.3), under the
     counter definition of §4.1. No retained input table is required. An unverified candidate can remain DEMAND
     even when all its observed edges are normal. This stop does not validate same-call support, an ND guard, or a
     trace. Keep the candidate in the output. Full later-refinement/no-loss claims remain open (§7.7);
  3. `POLICY`: `continueAfter(config, result, frontiers)` gave false (a practical stop strategy that reads the
     frontier log, §7.8; out of scope).

  Under that no-loss contract, every stop after a complete forward run retains
  every real vulnerability as CONFIRMED or DEMAND. Historical driver instances
  prove this for their versions; the F77 instance is open (§7.7). `STOP_RULE`
  leaves only confirmed entries. The claim that `NO_DEMAND_EDGE` permits no later
  confirmation is open. `POLICY` can stop while later refinement remains possible.
* The driver applies the stop rules and asks `continueAfter` only after a complete FORWARD run. A complete backward run
  always goes on to the next forward run: its only output is the hand-off of that run (§7.4). So the iteration always
  ends after a forward run, or at an abnormal end (§7.7).
* The selected F78 JVM driver checks the field limit of each run: run 1 is exactly 1 (`ap.md` S12), and the limit does not
  decrease (`ap.md` W3). A policy that breaks either is an error: the `require` fails, and the iteration ends as at a
  throw in the guarded region (`FAILED`; for run 1 the report has no entry).
  The measured generic driver only requires a positive first limit; its custom larger-start domain is not an F78
  closed-initial proof and remains unchanged by this specification edit.
* THE BUDGET. The analysis has ONE budget (`budget`, a parameter of the driver, §0). THE TIMEOUT OF A RUN is
  `policy.timeout(config, remaining)`, where `remaining` is the budget minus the time so far: by default each run gets
  the whole rest of the budget. The `RunManager` gets it in `run(timeout)`. A run that its timeout ends is incomplete
  (§6.3), and the analysis ends with the report of the earlier complete forward runs (§7.5).
* THE FRONTIER LOG. After EVERY complete run (forward and backward, also the last one), the barrier computes the
  hand-off and logs the frontier of the run (§7.8). The stop rules do not read the frontier. `continueAfter` gets the
  frontiers of every complete run so far.
* THE END OF THE ANALYSIS is an output: `Report.end = AnalysisEnd(status, run, direction, reason)` (§10). `run` and
  `direction` are those of the last run. The reasons:
  * `STOP_RULE`: a complete forward run with no DEMAND vulnerability; `status` is `COMPLETE`;
  * `NO_DEMAND_EDGE`: a complete forward run with a DEMAND vulnerability and no demand-layer object (edge delta,
    summary delta, incoming input contribution); `status` is `COMPLETE`;
  * `POLICY`: `continueAfter` gave false after a complete forward run; `status` is `COMPLETE`;
  * `ABNORMAL`: an incomplete run (`status` is its status: `TIMEOUT`, `OOM` or `FAILED`), a throw in the guarded
    region of the driver (`status` is `FAILED`), or a hit of the memory guard of the barrier (`status` is `OOM`).
* ONE GUARDED REGION. The whole loop body of the driver is one guarded region (`catch (e: Throwable)`): the
  `RunConfig` of run 1 and its check, `RunManager(...)`, `run(...)` and the barrier (the confirmation, `persist`, the
  hand-off, the frontier log). A throw there, an `Error` too (§6.3), ends the iteration with `FAILED`. The driver
  returns the report so far: the results of the earlier runs stay (`ap-history.md` F67, F68). The `RunManager` joins
  its runners on every exit (§6.3), so no runner of the run runs when the driver returns.
* THE MEMORY GUARD OF THE BARRIER (§7.2 B4). A hit at the barrier ends the iteration with `AnalysisEnd(OOM, run,
  direction, ABNORMAL)` and the report so far. The barrier has no deadline (§11).

### 7.2 The barrier

* B1. The driver computes the hand-off only after the run is complete and no runner of the run is alive (§6.3).
* B2. The next run starts only after the hand-off is complete.
* B3. The driver may read the stores of the finished run in parallel, read-only.
* B4. The barrier runs under its own memory guard (`MemoryManager`), as today's confirmation: the threshold of today
  (`TRACE_GENERATION_MEMORY_THRESHOLD = 0.99`) and the same mechanism (`TaintAnalysisUnitRunnerManager.kt:374-384`,
  `:645`). The soft-reference managers stay enabled during the barrier, so `persist` interns with live tables and the
  persisted records share nodes. A hit cancels the `Cancellation`; the loops of the barrier (the support, `persist`,
  the hand-off, the frontier) call `Cancellation.checkpoint` once per analyzer, so the barrier stops at its next
  checkpoint. The
  iteration then ends with `AnalysisEnd(OOM, run, direction, ABNORMAL)` and the report so far (§7.1). The driver also
  checks the `Cancellation` after the barrier, so a hit after the last checkpoint is not lost (the next `RunManager`
  would activate the `Cancellation` again). The barrier has no deadline (§11).

With B1, the hand-off reads a complete run's stored selected publications (§4.6)
and builds AP §9.2's patterns. The historical `PipelineDriver.result_D`,
`result_DR`, `result_DB`, `PipelineAnyTaintEx.result_D6X`, and `result_DRX`
establish closure delivery for their versions. `PipelineHandoffDriver.driver_iterationNX`
uses the previous selector; it is not an F77 driver instance (§7.7).

### 7.3 Forward run `n` to backward run `n + 1`

At the barrier, build an immutable demand store from each method's stored demand
pieces. Apply AP §9.2's forward-to-backward construction and normalization; do
not select again on a reduced piece. The store owns the complete entry and
exit patterns and their base/path indexes (AP §8.6).

Build `SeedIndex` from all witnesses of the report's DEMAND keys after run `n`.
Confirmed keys add no hand-off sink seed. The interpreter forms supply each
alternative's patterns. The backward engine can add the end-fact trigger seeds
defined in AP §9.2 during the run. Retain raw normal single-premise records and
make their transient leaf read views available (AP §8.7). Every R1-eligible normal
singleton summary gives no callee demand, including must-premise records. A
demand-layer view result belongs to the caller and follows the usual propagation
rules there. Do not synthesize a source hit
from a view; source hits require actual reversed source edges (AP §9.2).

### 7.4 Backward run `n + 1` to forward run `n + 2`

Build demand using AP §9.2's three cases: implicit zero demand, zero-premise seed
paths with no exit pattern, and selected nonzero publications. Normal nonzero
singleton summaries supply records only. Normalize both pattern marks. Build source `SeedIndex` from `sourceHits`.
The engine does not filter records by source seeds: a recorded call can supply a
source result without replaying its source.

Persist raw normal nonzero single-premise backward summaries (AP §8.7) and expose
their permitted reversal. Every input remains read-only during the next run.
The static base is touched/bound at calls by the interpreter; the driver carries
no separate static input between runs.

### 7.5 Confirmation and the report

After each COMPLETE forward run, commit its reached witnesses atomically to the report. A local ND sink can
combine emitted initials from different callers; keep its scoped proof, complete premise members, layers,
matched fact headers. Preserve source-hit and hand-off seed identities separately; SinkWitness has no
witness-to-source-hit map. Such an unverified witness has `confirmed=false` and state DEMAND,
even if every edge is NORMAL. Caller-free initial admission is not a support certificate.

This iteration need not implement a concrete trace engine. Its Simple output is explicitly candidate output.
Deleting the callee input history must remove the old unconditional `Support` scan. Do not set `confirmed=true`
merely to retain the former number of confirmed findings or to make a stop rule fire.

If exact analysis confirmation is selected, query the EXISTING joined N2 input relation before subscription
release. Establish AP §4.9's admissible members, NORMAL sink edges and least same-call support from root zero.
Every member must be supplied at one caller/call scope by normal inputs with supported caller premises. Preserve
must E and independent caller/input layers. Use targeted borrowed reads, not another global supplier collection.
If the certificate is not available, retain the DEMAND candidate. Historical confirmation theorems cover only
their explicit support hypotheses, not validation-free emission.

END-GEN LIMIT. A mixed local `x:T AND y:U` sink can generate `Return:V` under zero; a caller's full-ND return check
cannot reject a zero-premise generated summary. Preserve that zero-GEN semantics. Its derived candidate also
needs later validation of the generating guard at one caller, or an explicit derivation obligation. Normal zero
alone does not discharge it. No trace filtering is claimed to have happened now.

A validated key is final and CONFIRMED if any witness has the required certificate. Every other key from the
latest complete forward run is DEMAND. An older unconfirmed key absent from that run leaves the report. Output
every report entry with its state and Simple trace. Mechanically removing an older candidate is not a theorem
that every concrete flow was refuted; current no-loss/mode/iteration proof obligations remain open.

An incomplete run adds no evidence and refutes nothing. Keep earlier complete forward report state and set
`Report.end` to the cause. If run 1 is incomplete, the report/output are empty. Memory-guard abort is OOM; any
`Throwable` is FAILED. Cancellation has a known cause set before cancel (§6.3).

### 7.6 What stays after a run

| Data | Stays until | Read by |
|---|---|---|
| raw summaries, selected demand publications and backward source hits | end of the barrier | persist, hand-off and frontier (§§7.3–7.4) |
| run counters | frontier is logged | stop rules and frontier |
| N2 subscriptions | joined end of run; optional exact support may extend release through its targeted barrier read | summary/record matching, run-1 question replay, optional support |
| authoritative R, actor pending P and queued request/answer batches | all accepted work joins, then release | run-1 abstraction and caller N2 only; no deferred-answer memo |
| method edges and initial deduplication | joined end of run | no barrier consumer after counters are captured |
| frontier counts/method keys | end of the analysis | policy |
| RecordStore, VulnerabilityStore, method context and AP managers | end of analysis | subsequent runs and report |

No callee added-fact history exists in any run. A semantic admitted-input relation in an equation is ghost state,
not a runtime owner. The unresolved-call observer uses a minimal observation-identity memo with checked callback
timing/rebuild semantics; it retains no Facts, supplier roots or CallerRefs.

The barrier reads only retained summaries, demand edges, source hits, counters, vulnerabilities/report and seed
form descriptors. Optional exact confirmation additionally borrows joined N2 before its release. A subscription
already frozen/released is not readable. Complete the support read before dropping its data; cancellation cannot
publish a partial certificate. The barrier has its memory guard (§7.2).

The driver retains a RunResult only in the barrier frame. All request/policy state and caller input relations are
released before the next run. No store survives for a future trace resolver; later trace work needs a separate
explicit evidence/lifetime design. An incomplete run releases its state and adds no report evidence.

### 7.7 The driver theorems

The driver must preserve AP §6.6 contract B, retain the AP §8.7 records, and hand
off exactly the data of §§7.3–7.4 after each complete run. It must retain confirmed
entries and discard incomplete-run evidence (§7.5).

The historical concrete-driver theorems and current open obligations are listed
in [proof-status.md](proof-status.md). Current local demand/index/exactness results
do not establish full iteration or confirmation support. Do not claim those
properties from an earlier driver instance.

### 7.8 Localization and the frontier

Demand restricts nonzero analysis; persistent records supply permitted call results.
Full current method-exclusion and round-to-round narrowing are open (AP §6.6).
Do not infer exact narrowing from a stable count: normalization can enlarge marks
and reduction has representability limits. A method with no demand edge and no
seed in its call subtree is intended to leave nonzero analysis. Zero work still
repeats in every run.

After **every complete run**, including the final one, the barrier logs a
`Frontier` with method keys and counts, not edges. Compute it in one pass over
analyzer counters and the hand-off:

| Field | Meaning |
|---|---|
| `analysed` | Method keys with nonzero initial facts |
| `demandEdges` | Stored outgoing demand pieces per method key |
| `recordCrossings` | Record applications at calls, including permitted reversed records |
| `demandVulnerabilities`, `confirmedVulnerabilities` | Report states after a forward run |
| `seeds` | Outgoing sink seeds (forward) or source seeds (backward) |
| `zeroOnly`, `zeroOnlyEdges` | Method keys analysed only from zero, and their edge counts |
| `demandByCause` (optional) | Demotions by cut, may target, cleaner, must record, or demand input |

Keep the log until analysis ends. `continueAfter` receives the logs of complete
runs and can use size, progress, timing, and remaining budget. The configured
policy owns that choice. Stop checks run only after complete forward runs; an
incomplete next run would leave the same report. Logging supplies no pruning rule.

---

## 8. Code reuse

Existing modules and migration background are in
[implementation-background.md](implementation-background.md). The current
implementation plan is [analyzer-impl.md](analyzer-impl.md).

## 9. Interface to the prescan and the output

* PRESCAN (phase 3). The prescan runs the current core. It gives the new core:
  * the reduced rule set (`relevantRuleIds`, the prescan rule ids);
  * the lambda resolutions per call site (the prescan lambdas: the values of the `TrackerWithSubscriber` of each call
    site);
  * the fact type checker and the external method tracker;
  * the root methods.

  F78 additionally exports the existing complete resolved call graph, effective IR/rule/model forms, finite complete
  static field/class universe and structural/mark descriptors before prescan release. Visit both source and sink NNF,
  input gates, generic excluded targets and cleaners/rewriters. Compute recursive union closure and lazy wide-scope
  activation without retaining live prescan contexts. The runtime may not silently accept a target/field outside
  that closed domain. Shared immutable neutral descriptors are distinct from prescan execution state.

  The core copies resolutions/descriptors once before run 1 and retains no reference to a prescan context.
* PRESCAN MEMORY. The caller gathers the prescan info above. Then, before run 1, it releases the WHOLE prescan state:
  the prescan runners, the unit storage, the analyzers, the AP manager of the prescan and `JIRAnalysisManager.contexts`.
  No prescan edge, summary or alias analysis stays alive during the runs (`ap-history.md` F67).
* OUTPUT (phase 3). The core gives the `Report` (§10): the entries, each with its state CONFIRMED or DEMAND (§7.5) and
  the witnesses of its key in the run of that state (the fields of `ap.md` §8.10), and `end`, the end of the analysis
  (`AnalysisEnd`, §7.1). The phase-3 output holds EVERY entry of the report: the CONFIRMED vulnerabilities and the
  DEMAND vulnerabilities of the latest complete forward run, each with the simple trace (TRACE, below; `ap-history.md`
  F68). The method key of an output vulnerability is the method key of a confirmed witness if the entry has one, else
  of the first witness. Phase 3 maps `end` to today's `TaintAnalyzer.Status`. If run 1 is incomplete, the report has no
  entry and the output is empty; the status gives the cause. Today a full scan that times out outputs the
  vulnerabilities that it found before the timeout (`TaintAnalyzer.kt:157-223`): this is a deviation from today (§7.5,
  §11).
* TRACE. The trace resolution is out of scope. The core keeps no store of a run for a trace resolver (§7.6). The
  phase-3 output gives every entry, CONFIRMED and DEMAND, a SIMPLE trace: the trace with only the sink statement (today
  `TracePathGenerationResult.Simple`,
  `core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/ap/ifds/trace/path/TracePath.kt:48-51`).
  Phase 3 logs the count of the entries per state (CONFIRMED, DEMAND).
* NO EXTERNAL CANCEL (phase 3). The phase-3 entry has no `cancel()`: the timeout of a run, a memory guard (of a run or
  of the barrier) and a runner exception are the only causes of a cancellation (§6.3). So phase 3 maps the status one
  to one: `COMPLETE` → `OK`, `TIMEOUT` → `TIMEOUT`, `OOM` → `OOM`, `FAILED` → `EXCEPTION`.
* THE PHASE-3 GUARD. Phase 3 calls the analysis inside `runCatching`, as today (`TaintAnalyzer.kt:157-158`). A throw
  outside the driver (the setup of the analysis) gives an empty output and the status `EXCEPTION`. A throw inside the
  driver never reaches phase 3: the driver returns the report so far (§7.1).

---

## 10. Reference code

[analyzer-impl.md](analyzer-impl.md) gives the current API and algorithm proposal.
The normative event, storage, and ordering contracts are §§2–7 above.
Historical reference code is in
[implementation-background.md](implementation-background.md).

## 11. Limits

[ap.md](ap.md) §11 states the analysis limits and [proof-status.md](proof-status.md)
is the single proof-status list. In particular, current mode coverage, X-tail
integration, confirmation support, localization, and the pipeline/driver instance
remain open. F75 repairs the checked root-EXACT loss; the old root example is not
an unresolved behavior decision.

The runner retains exact N2 subscriptions; the emitter retains no links or answer memo. F78 request replay must satisfy QP1–QP5 and the static frame reader contract.
Tree publications must denote
the union of their path deltas. Every implementation shortcut must preserve the
event joins, raw-summary persistence and crossing tests, barriers, and per-leaf AP operations. A
generic quiescence theorem or historical tree proof is insufficient to establish
these obligations for a different encoding.

Unresolved-call effects, aliases, exceptions, end facts, interpreter rule forms,
and trace resolution have the limits stated in the AP and interpreter specs.
The current base model does not establish these combined effects.

## 12. The formal model

The model, assumptions, checked protocol results, and open current encodings are
listed in [proof-status.md](proof-status.md). Build and audit [lean](lean) there.

## 13. Test plan (TDD)

[validation-plan.md](validation-plan.md) contains the protocol, driver, report,
and interpreter checks. Tests must use current AP semantics and raw-summary
ownership. Historical test results do not prove the current encoding.

## Appendix A. Today's analyzer

See [implementation-background.md](implementation-background.md).
