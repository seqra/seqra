# Core rollback and retest

Date: 13 October 2026. Status: revised-core tests pass and all eight fresh project scans are terminal.
OWASP passes the finding/completion gate. Conductor, ThingsBoard and PDF still fail resource gates.
Both new guard heaps have verified owner reports. The future optimizations remain unselected.

The user requested removal of the manager lifecycle rewrite and `WeakIdentityCache`. The manager source
is restored exactly from `0d0052936^`. The new weak-cache class and its test are removed. Rule validation
now checks each input directly. Diagnostic deduplication stores only rule class, declared ID, diagnostic
kind and reason strings; it does not retain bound rules or conditions. Anonymous warnings can repeat.

The managed method-form cache keeps its original `createRef` integration, callback pins, single-flight
builds and stable rebuild identities. Manager queue/lock-specific tests and wrapper-count diagnostics
are removed. Original public-API cleanup/re-enable tests and form eviction controls remain.

The [new proposal](../design/bidirectional-core-optimization-20261013.md) ranks optimizations using the
latest reviewed snapshots. The manager redesign and weak identity cache are optional N11/N12 candidates.
They require separate selection and design. The old proposal is marked superseded.

## Revision and verification

The rollback commit is `38a400c0d`. The project manifests record `bd6810823`, which adds only documentation
and reporting tools after that runtime change. No core, script, rule or specification change occurred during
the scans. Working-patch digests also include the preserved unrelated `issues.md` edit and later documentation
corrections. They do not identify a different analyzer implementation.

The full common suite passes 1,320 tests and the JVM suite passes 377 tests, with no failures, errors or
skips. Go, JVM SAST and root consumer test compilation and analyzer classes pass. The command completes
in 2 minutes 6 seconds. The 11 removed common tests exercise the removed weak-cache utility or the
rolled-back manager implementation; the remaining form and semantic controls are retained.
Command logs are in `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/rollback-retest/20261013T200523Z`.
The comparison controls pass 28 tests and the profile-tool controls pass 8 tests. Benchmark and comparison summaries now include reached-run,
complete-engine and accepted-forward-report fields. Actual start markers determine the latest reached run,
including failed or unfinished runs. Main is explicitly not applicable and missing evidence is unavailable.

## Latest fixed-project results

All eight direct Gradle `runProjectAnalyzer` scans use the original models, heaps and budgets. Main remains
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`, with its existing opt-in guard heap hook. The actual process
captures confirm Temurin 17, batch 2,000 and disabled multi-release loading on both sides. Run-1 starts
at limit 1. No input, heap, timeout, exclusion policy or positive-limit sequence is changed.

Evidence is in `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/rollback-projects/20261013T200919Z`.
Each bundle has command/configuration/process metadata, logs, continuous JFR, resources and SARIF.
`comparison` contains strict finding differences and iteration fields. `versus-pre-rollback.json` checks
the previous primary outputs separately. Model hashes/configuration comparisons match; this is not a
continuous proof of input immutability. The disclosed cached OWASP release-8 input remains in use.

| Project | Latest reached | Last complete engine | Last accepted forward report | Main / bidi unique | Missing / added |
| --- | --- | --- | --- | ---: | ---: |
| OWASP | run-3 F/L3 COMPLETE | run-3 | run-3 | 4,112 / 4,112 | 0 / 0 |
| Conductor | run-2 B/L2 OOM | run-1 | run-1 | 7 / 300 | 0 / 293 |
| ThingsBoard | run-1 F/L1 OOM | None | None | 14 / 0 | 14 / 0 |
| Stirling-PDF | run-4 B/L4 TIMEOUT | run-3 | run-3 | 17 / 56 | 3 / 42 |

All main scans complete, all SARIF files are valid and no prescan error is recorded. The strict key is
rule ID, sink fingerprint and full method signature. The rollback outputs match all pre-rollback sink
identities, occurrence counts and exported sink-location spans. This is output parity, not proof of
complete propagation in a failed scan.

OWASP has 9,179 CONFIRMED occurrences. Conductor retains 3 CONFIRMED and 298 DEMAND attempts, with
300 exported identities after the known virtual-location rejection. Its 293 additions remain incomplete
results, not certified false positives. ThingsBoard fails before its first report barrier, so all 14 main
identities are absent from its valid empty report. PDF retains 40 CONFIRMED and 88 DEMAND occurrences.
Its same three strict losses remain: two exit-location differences and one absent command sink. The
[PDF investigation](bidirectional-o1-o3-projects/Stirling-PDF.md) records the source-backed location
mapping and bounded pre-rollback command-flow evidence. Do not relax the strict comparison or claim a
new earliest normal-path loss from the unchanged counts.

## Performance and iterations

| Project | Main IFDS / command | Revised bidi IFDS / command | Result |
| --- | ---: | ---: | --- |
| OWASP | 80.977 / 126.236 s | 299.478 / 339.585 s | Complete, slower than main |
| Conductor | 49.603 / 92.177 s | 156.341 / 201.377 s | OOM |
| ThingsBoard | 179.367 / 219.421 s | 444.826 / 480.166 s | OOM |
| Stirling-PDF | 19.603 / 48.301 s | 1,080.010 / 1,108.818 s | Timeout |

| Project | run-1 engine / barrier | run-2 engine / barrier | run-3 engine / barrier | run-4 engine |
| --- | ---: | ---: | ---: | ---: |
| OWASP | 179.131 / 0.583 s | 43.941 / 0.051 s | 40.774 / 0.077 s | — |
| Conductor | 37.554 / 0.916 s | 92.396 / — s, OOM | — | — |
| ThingsBoard | 289.365 / — s, OOM | — | — | — |
| Stirling-PDF | 28.757 / 0.201 s | 19.357 / 0.076 s | 47.611 / 7.760 s | 958.840 s, TIMEOUT |

Engine time includes guard GC and dump time. The Conductor dump interval is 17.441 seconds; the
ThingsBoard dump interval is 27.334 seconds. Do not add them to engine time again. IFDS includes prescan;
command time also includes Gradle/report work. These are single shared-host samples. Shorter failed scans
and differences from the earlier b0 timings are not an isolated performance benefit from the rollback.

## Profiles and new guard heaps

All four bidi scans save successful 30-second normal asynchronous CPU captures and continuous JFR.
PDF also saves a successful near-limit capture. Main full scans finish before the requested normal
capture window, so their asynchronous windows are unavailable; continuous JFR remains available.

The bounded normal CPU review verifies OWASP run-1, Conductor run-2 and ThingsBoard run-1. GC frames
occur in 65.04%, 66.79% and 35.30% of their respective sample weights. These are inclusive CPU samples,
including concurrent/native GC, not wall pause time. Full group predicates and supporting frames are
saved in `normal-cpu-review-2`, which preserves review 1. PDF's normal capture is verified in run-3:
57.40% GC, 3.62% trie subtraction and 17.53% staging. Its near-limit run-4 capture has 12,582 samples:
77.02% GC, 8.00% trie subtraction and 10.14% staging. The groups overlap. All five captures complete
with return code 0, fit within their recorded engines and have no logged snapshot overlap. A group
with no sampled match does not prove no execution. Allocation and Java CPU event exports from the
new continuous JFR recordings have not been performed; older JFR weights remain revision-specific.

The new Conductor **run-2** guard heap is 11,693,412,724 bytes. The new ThingsBoard **run-1** guard heap
is 18,694,343,873 bytes. Capture occurs before cancellation. File size is not live heap size. Their fresh
23-query owner reports compare standing requests, queue storage, active subscription state, cached form
referents and original-manager bookkeeping. Each query's field availability must be verified before a
byte/count claim. No missing manager field is treated as zero.

### Conductor: fresh run-2 memory owners

The new reachable heap has 220,635,739 objects and 7,638,598,880 bytes. The actual manager is at run-2,
limit 2, before cancellation, with 2,723,788 handled events and five pending reservations. All 219 runners
are open and own 17,525 actors. Only four queue entries remain, with 177,074 allocated queue cells.
This capture puts the remaining cost in stored protocol/AP state rather than a large pending queue.

| Complete selected root set | Retained-union bytes |
| --- | ---: |
| Active actors, runners and subscription managers | 2,892,449,240 |
| Active subscription managers | 1,538,287,888 |
| Accepted subscription parts | 607,697,168 |
| Delta-part positional indexes | 509,006,864 |
| Added-key positional indexes | 259,069,008 |
| Actor method-edge stores | 720,938,808 |
| Method recipes and form-cache owners | 208,680,936 |

The 18,750 callee directories contain 2,165,026 accepted-part list entries, 679,558 cumulative exact
groups and 33,855 zero references. These are logical directory counts, separate from object counts.
Within the delta-index union, 7,397,171 linked-map entries use 295,886,840 shallow bytes and 6,918,460
boxed integers use 110,695,360 bytes. Within the accepted-part union, trie nodes and their accessor/child
arrays use 452,468,200 bytes. The table's unions overlap and cannot be added or treated as recoverable savings.

Both the whole-heap histogram and the empty-slot complement contain all 257,075 form slots, which proves
zero cached nonnull referents. The two direct cached-payload queries produce no result; their byte fields
remain unavailable. Two payload objects occur in the global histogram, but this report does not request
their root paths. All four original managers have atomic enabled value 0. Wrapper queue cardinality is
unrequested and is not inferred from head/tail addresses.

The frozen report contains 23 queries: 19 nonempty CSV results, one header-only CSV, one empty selection
and two no-result payload queries. The actual archive name is explicitly bound because MAT shortened
the requested suffix; the original manifest/XML are preserved. Archive SHA/CRC and extracted member CRCs
pass. Detailed evidence is in `heap-analysis/rollback-comparison/conductor/job-20261013T205303.000792Z/review`
under the common evidence root. Rebuildable indexes were removed only after verification, to make room
for the next parse. Raw heaps, profiles, archives and reports remain intact.

The corresponding b0 unions were 2,853,512,400 bytes for combined active core, 1,487,146,248 for
subscriptions and 566,535,456 for accepted parts. Different handled work and group cardinalities prevent
an isolated rollback saving/regression claim. Both snapshots support subscription representation as a priority.

### ThingsBoard: fresh run-1 memory owners

The new reachable heap has 324,888,652 objects and 11,387,231,968 bytes. The actual manager is at
run-1, limit 1, before cancellation, with 197,740,423 handled events and 15,810,688 pending reservations.
All 562 runners are open and own 76,943 actors. Queues contain 15,764,679 entries and allocate
51,339,702 array cells. Standing stores contain 24,981,269 seen pairs and 21,178 positional base roots.
There are 14,718 nonempty stores. Blank bucket-capacity fields on empty stores are not numeric zero exports.

| Complete selected root set | Retained-union bytes |
| --- | ---: |
| Active actors, runners and subscription managers | 5,798,008,504 |
| Standing request stores | 3,095,473,728 |
| Queue backing-array roots | 1,214,843,344 |
| Queue object roots | 1,214,901,792 |
| Active subscription managers | 586,470,056 |
| Actor method-edge stores | 377,489,656 |
| Method recipes and form-cache owners | 684,922,856 |

Within the request-store union, pair bodies use 599,550,456 bytes, linked-map entries 999,250,760,
hash-map nodes 800,078,304 and hash-map table arrays 392,402,320. One Pair is shared between seen and
index storage; the repeated cost is index/container entries, not duplicate Pair bodies. Within the
queue-backing union, entry bodies use 630,587,160 bytes, request messages 378,212,496 and object arrays
205,411,552. These are overlapping owner measurements, not an additive savings estimate.

The whole histogram and empty-slot complement both contain all 940,423 form slots, proving zero
cached nonnull referents. As with Conductor, direct payload-byte exports have no result and remain
unavailable. All four original managers have atomic enabled value 0; queue-wrapper cardinality is not
requested. Thus managed forms are cleared through the restored original API, while large standing
requests and pending events remain. Further compiled-form eviction alone cannot remove those owners.

All 23 query titles are accounted for: 21 nonempty CSV results and two no-result payload queries. Archive
SHA/CRC, frozen report SHA and extracted member CRCs pass. Detailed evidence is in
`heap-analysis/rollback-comparison/thingsboard/job-20261013T210411.014716Z/review` under the common
evidence root. The old b0 request-store union was 2,885,461,840 bytes; the old queue backing union was
1,210,199,096. Different request cardinality, queue capacity and handled work prevent assigning the byte
difference to the rollback. Both captures identify the same optimization targets.
Verified rebuildable MAT indexes were removed after the review; raw dumps, recordings, archives and
exported reports are preserved.

The older OWASP warm heap captures run-1; its replay reaches run-3. PDF has no new guard heap and its
older checkpoint heaps capture run-3. Do not describe those heaps as final run-4 memory evidence. Main
has no bidi iteration. The new proposal uses verified fresh Conductor and ThingsBoard owners. Older
warm-heap, singleton, provider and JFR allocation figures retain their explicit pre-rollback attribution.
