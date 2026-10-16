# Static handling: index to the unified specification

Status: compatibility index. The single normative contract is
[Run-1 requests, field writes and static summaries](run1-field-writes.md).
Static requests, selective field preservation, ordinary N/D defaults and
later strong reconstruction are specified together there. This file retains
older link targets; it adds no separate rules.

Latest measured iteration: **run-1, FORWARD, limit 1; OOM**. None completed;
no accepted forward report. No new analyzer measurement was made.

## 1. Domain and phase boundary

See [domain, request slots and nine initial families](run1-field-writes.md#21-request-domain-and-all-initial-families).

## 2. Prescan inventories

See [field/mark requests](run1-field-writes.md#22-prescan-field-and-static-mark-requests),
their [complete lifecycle](run1-field-writes.md#221-static-request-lifecycle)
and [static effect closure](run1-field-writes.md#5-prescan-static-write-closure).

## 3. Run-1 initials and request ownership

See [initial families](run1-field-writes.md#21-request-domain-and-all-initial-families),
[authoritative R and temporary P](run1-field-writes.md#23-one-effective-request-relation-and-temporary-batching),
[matching and emission gates](run1-field-writes.md#24-matching-rows-and-independent-initial-emission-gates),
[caller routing/replay](run1-field-writes.md#25-dynamic-routing-back-to-the-caller)
and [static/dynamic coverage](run1-field-writes.md#26-staticdynamic-split-and-coverage-obligations).

## 4. Run-1 stores and calls

See [selective field preservation](run1-field-writes.md#3-preservation-and-pre-store-values),
[Empty E](run1-field-writes.md#4-empty-field-exclusions-throughout-run-1)
and [ordinary default summary storage/application](run1-field-writes.md#6-ordinary-default-static-summaries).
Calls keep ordinary input delivery, subscription and summary application.

## 5. Safe transition from run-1 to run-2

See [ordinary handoff and the general F1 guidance/candidate boundary](run1-field-writes.md#7-ordinary-handoff-and-strong-run-2-analysis).

## 6. Strong static handling on run-2 and later

### 6.1 Current queries supply missing callee analyses

See [later current-query handling](run1-field-writes.md#72-current-queries-supply-missing-callee-analyses).

### 6.2 S→S relation

See [strong S-to-S relation](run1-field-writes.md#73-ss-relation).

### 6.3 Calls, reversal and persistent records

See [later calls and records](run1-field-writes.md#74-calls-reversal-and-persistent-records).

## 7. Correctness target and required validation

See [complete proof obligations](run1-field-writes.md#8-correctness-and-implementation-obligations),
[request/static proofs](static-handling-proofs.md),
[field-write/default proofs](run1-field-write-proofs.md) and
[checked proof status](proof-status.md). Existing receipts retain their source-bound scope.
