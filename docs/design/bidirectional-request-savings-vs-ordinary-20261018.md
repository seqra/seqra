# Request savings compared with ordinary propagation

Date: 18 October 2026. Status: proof and counterexample for the
[entry-subscription proposal](bidirectional-bottom-up-request-processing-20261018.md).
Analyzer implementation unchanged. Latest measured conductor and ThingsBoard:
**run-1 / completed none / accepted none**, on f18b1c5bb.
There is no new project measurement here.

## 1. Answer

We can prove a saving on transparent relay chains. We cannot prove that the
current proposal always uses less work or memory than ordinary incremental
request propagation. It has a shared-bottleneck counterexample even when both
procedures request exactly the same final facts.

The earlier 64-versus-2,080 control compares incremental joining with repeated
full replay. It does not establish a gain over the analyzer before bottom-up
request routing.

## 2. Define the comparison

~~~text
Ordinary incremental:
  - per-method merged requests
  - fresh request marks join existing inputs
  - fresh inputs join known requests
  - duplicate marks stop at each method

Entry subscription:
  - requests retained at their source method
  - source/entry matching uses fixed prescan reachability
  - each eligible source/entry pair delivers directly to the caller original
  - duplicate requests stop at the final destination
~~~

Compare the same already-derived entry inputs, request marks and actual
supplier facts. Initial analysis and summary reuse are common work. The
ordinary analyzer already deduplicates initial analysis and stores reusable
summaries; this is not a new saving of the proposal.

Count logical request-mark memberships and forwarding events. Do not identify
these with allocated bytes, CPU seconds or mark-set message counts.
Use ordinary incremental propagation as the reference, not the regressed
global-wave implementation.

Historical runtimes also have frozen static coverage, admission and layer
policies. A transport theorem does not prove a whole-analyzer improvement
over 27e22 or ab6. A source-level bridge must identify a cohort where the
counted ordinary dynamic request work actually occurs.

In particular, ab6 already propagates frozen static mark coverage over the
call graph and suppresses an unchanged C.f/T check covered by that plan.
A simple literal C.f check through read-free relays cannot be used to claim
h × k dynamic savings against that runtime.

## 3. A scoped saving: one source through h relays

~~~text
actual W entry -> H1 -> H2 -> ... -> Hh -> reader B
      ^                                      |
      |------- request marks {T1,...,Tk} -----|

h = transparent intermediate methods
k = distinct requested marks
one entry offer, one downstream request source
~~~

Assume that all relays preserve the supplied field, full contexts agree,
no extra request source or candidate matches, and no frozen plan has already
eliminated the ordinary dynamic relay requests.

A conditional historical bridge is:

~~~java
// C.g arrives at entry H1; the relays preserve C.g.
B() {
    C.f = C.g;
    check ContainsMark(C.f, T);
}
~~~

The dynamic request concerns original C.g/T. If the frozen template inventory
contains C.f/T but no C.g/T or broader covering T template, that request is
uncovered and must take the ordinary dynamic path. Multiple T_i work the same
way. This is an explicit scope condition, not a measured project claim.

| Count, excluding the common final Base request store | Ordinary | Entry subscription | Saving |
| --- | ---: | ---: | ---: |
| Retained static request-mark memberships | (h + 1) × k | k | h × k |
| Forwarded request-mark cells | (h + 1) × k | k | h × k |
| Request forwarding batches, all k marks together | h + 1 | 1 | h |

The batch row excludes directory registration, eligible-entry notifications
and ordinary work messages. It is not a total message bound.

~~~text
Endpoint equality:
  ordinary produces Base(original, T_i) for each i
  direct produces   Base(original, T_i) for each i

Difference:
  (h + 1)k - k = hk

Strict membership/forwarding saving if h > 0 and k > 0.
~~~

The directory adds a request reference, an entry offer and an eligible pair.
A cold reachability search can traverse the h-method chain. Under ordinary
per-member request processing, the scoped work is therefore:

~~~text
ordinary relay member work:    Theta(hk + k)
direct member work + cold CG:  O(h + k)
~~~

This comparison assumes indexed singleton pattern matching and a reachability
result cached for later mark deltas. It excludes unrelated directory rows and
the cost of the common analyzer work. For k = 1, both have linear cold work
in h; the useful saving is relay request state and repeated later traversals.

## 4. Counterexample: shared merging before fan-out

~~~java
A(p1, p2, p3, p4, choice) {
    switch (choice) {
        case 1: C.g = p1; break;
        case 2: C.g = p2; break;
        case 3: C.g = p3; break;
        case 4: C.g = p4; break;
    }
    H();
}
H()  { B1(); B2(); B3(); B4(); }
Bi() {
    C.fi = C.g;
    check ContainsMark(C.fi, T);
} // four distinct methods/fi fields; no frozen C.g/T coverage
~~~

Consider the request subsystem after these actual FLOW inputs reach H's entry:

~~~text
H entry offers:
  (H, C.g, A/p1), (H, C.g, A/p2), (H, C.g, A/p3), (H, C.g, A/p4)

Outgoing requests:
  B1/C.g/T, B2/C.g/T, B3/C.g/T, B4/C.g/T
~~~

The inputs share one callee entry/call site but retain different original
premises. The entry-offer key cannot merge those originals.

~~~text
Ordinary:
  4 reader requests -> H/C.g/{T}, merged once
  H/C.g/{T} -> 4 caller originals
  = 8 logical request-mark offers before destination combining

Current entry-subscription proposal:
  4 source subscriptions × 4 reachable entry offers
  = 16 eligible pairs and 16 direct Base offers before destination combining
  destination P/A reduces these to 4 distinct Base requests afterwards
~~~

Both procedures ultimately request exactly A/p1/T through A/p4/T.
The direct procedure pays for duplicate offers before that final deduplication.
SCC grouping reduces reachability tests, not the 16 qualified pairs.

For r >= 1 readers with the same requested mark and p distinct originals:

~~~text
ordinary dynamic mark offers:   r + p
direct mark offers:             r × p
proposal's eligible references: r × p

r = p = n:
  ordinary dynamic mark offers = 2n
  direct mark offers           = n^2
~~~

With no reader, both procedures make zero offers.
Frozen coverage can reduce ordinary dynamic forwarding further. This is a
request-component counterexample; deriving the inputs and executing the
method bodies are shared costs, not asserted to be O(n) for the whole project.
Destination message combining can reduce physical envelope counts; it does not
erase these offer attempts or the proposal's eligible references.
No context or ND truth is pooled in this comparison.

## 5. Necessary design consequence

The current proposal cannot use “strict improvement over ordinary propagation”
as its acceptance theorem. Its source-owned P/A sets prevent duplicate
source/entry pairs, but do not preserve merging between different request sources.

An **unselected refinement** can restore that merge before original fan-out:

~~~kotlin
// One productive entry/field group, shared by all reachable reader sources.
fun needAtEntry(group: EntryFieldKey, offered: MarkSet) {
    val fresh = offered - group.knownMarks
    if (fresh.isEmpty) return
    group.knownMarks += fresh // publish before fan-out
    for (original in group.originals)
        deliver(original, fresh)
}

fun newOriginalAtEntry(group: EntryFieldKey, original: Original) {
    if (group.originals.add(original))
        deliver(original, group.knownMarks)
}
~~~

This stores a merged mark union at productive entry groups, while transparent
relays still keep no inherited request rows. In the counterexample it restores
r incoming merges plus p outward offers. It needs a separate ownership and
both-arrival-orders proof before selection. It does not by itself prove a
global improvement: matching groups and widened prescan interest still cost work.

## 6. Proof boundary

The [constructive cost model](../../spec/lean/ApSpec/EntryRequestSavings.lean)
and [focused audit](../../spec/lean/entry-request-savings-audit.lean) establish:

~~~text
same final Base endpoint union in each restricted cohort
chain: exact h × k membership/forwarding saving
shared bottleneck: r + p versus r × p
explicit ordinary per-method deduplication, not a naive path oracle
~~~

The build and audit pass: **171 declarations, zero unexpected axioms,
14 executable controls**. Main theorem names are:

~~~text
chain_endpoint_unions_equal
strict_chain_mark_saving
strict_chain_membership_saving
SharedBottleneck.ordinary_endpoint_from_execution
SharedBottleneck.admitted_mark_never_retransmits
SharedBottleneck.endpoint_unions_equal
SharedBottleneck.direct_can_do_more_work
~~~

These finite protocol theorems do not prove actual-entry generation, full AP
semantics, ordinary frozen-plan cost, index geometry, JVM scheduling, byte
retention or project elapsed time. Those remain separate refinement and
measurement obligations under the proof guide.
