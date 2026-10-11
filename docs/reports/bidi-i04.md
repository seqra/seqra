# I04 rule forms and lowered cleaners

`JIRRuleForms` validates the whole rule before it builds forms. It checks
all raw action positions and mark literals, including ignored actions,
negated literals and alternatives removed by static evaluation. Invalid
rules give no partial forms. Pass-rule mark conditions use the specified
logged exception; I05 applies that exception in the call adapter.

The forms resolve positions, source tails, DNF products, array sink
alternatives, end facts and pass edges. Static-read rule queries use a
null fact. Named nonstatic field cleaners use a fresh typed temporary:
read, root clean, strong write and projection. The reversed action keeps
the same temporary. Root, array and static cleaners keep their direct
forms. The tests compare complete raw and tree results at limits 1–3.

Initial factories and persistent stores now reject temporary bases before
mutation. Transient facts remain available during a field action. A
temporary cleaner request retains the original incoming premise.

## Tests and corrections

Tests preceded the implementation. Missing-API logs are
`/tmp/bidi-i04-validation-red.log` and `/tmp/bidi-i04-forms-red.log`.
Temporary boundary failures are in `/tmp/bidi-i04-temporary-red.log` and
`/tmp/bidi-i04-initial-red.log`. The plan was updated before corrections:
whole-action validation, valid fixture types, a Kotlin block body, K6
premises, temporary factory guards and the reachable run-1 static kill.
No new Lean proof or behavior decision was required.

| Class | Tests | Main checks |
|---|---:|---|
| JIRRuleFormsTest | 21 | Positions, owned paths, source/pass tails, DNF, conjunctions, sink arrays, end facts and static reads. |
| JIRRuleValidationTest | 11 | Complete raw validation, invalid siblings, discarded/negated literals, static constraints and concurrent validation. |
| JIRCleanerMappingTest | 7 | Reach, type filters, string companions, action order and reversal. |
| JIRLoweredCleanerEffectTest | 16 | Independent raw/tree results, exclusions, cuts, requests, temporary projection, primitive policy and static kills. |
| TemporaryStorageBoundaryTest | 7 | Initial factories and each persistent store reject temporary endpoints without mutation. |

## Final gate

The clean common/JVM test gate and Go, JVM SAST and application compile
checks passed in 55 seconds (`/tmp/bidi-i04-final-gate-2.log`).

| Suite | Classes | Tests | Failures / errors / skips |
|---|---:|---:|---|
| Common dataflow | 72 | 755 | 0 / 0 / 0 |
| JVM dataflow | 13 | 226 | 0 / 0 / 0 |

All 607 new common and 112 new JVM annotated tests have void JVM returns
and occur in fresh XML. Discovery logs are
`/tmp/bidi-i04-{common,jvm}-discovery.txt`; XML copies are under
`/tmp/bidi-i04/{opentaint-dataflow,opentaint-jvm-dataflow}/xml`.
`git diff --check` passes. I04 is complete.
