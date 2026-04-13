# `extractMatchingSuffix` — Implementation Plan (Final)

## Goal

Given an `AccessTree.AccessNode` (a DAG) and a suffix (`AccessPath.AccessNode`, a linked list of accessors), split the DAG into groups based on how many trailing accessors of each leaf-to-root path match the suffix.

**Result:** `List<Pair<AccessNode, AccessPath.AccessNode?>>` — a list of (sub-tree, remaining unmatched suffix or `null` if fully matched).

---

## Algorithm Overview (4 Stages)

### Stage 1 — Intern & Build Predecessor Map (implemented)

Intern the DAG for structural deduplication, then build a predecessor map:
`predecessors[childId][accessor] = BitSet of parentIds`

### Stage 2 — Match Reversed Suffix (implemented)

Walk from leaves (isAbstract/isFinal) upward through predecessors, consuming suffix elements.
`nodeMatch[nodeId] = BitSet of suffixLengths`

**Semantics:** `suffixLength=0` = full match (all consumed), `suffixLength=suffixSize` = no match (leaf starting point).

### Stage 3 — Split Nodes (implemented)

For each level `k` from 0 to suffixSize:
- **k < suffixSize (edge levels):** For each node with `nodeMatch[nodeId].get(k)`, extract edge `suffix[k] → child`.
  - Level 0 (full match): extract the original child (full sub-tree).
  - Level k > 0: extract child with deeper suffix path removed via `removeSuffixTail`.
  - Remove the extracted edge from `splitNodes[nodeId]` (copy-on-write).
- **k = suffixSize (leaf level):** Extract `isAbstract`/`isFinal` flag from leaf nodes.

**DAG safety:** Nodes shared between paths are NOT mutated. Only edges from parent to child are extracted/removed in `splitNodes`.

**Domination:** `removeSuffixTail` recursively removes `suffix[k+1..suffixSize-1]` + leaf flag from the extracted child, ensuring deeper matches are excluded from shallower levels.

### Stage 4 — Rebuild Trees (implemented)

For each level k (0..suffixSize):
- Walk predecessors upward from extracted nodes to root.
- **Edge filtering:** Only follow predecessor edges that still exist in `splitNodes[parentId]`. Edges removed by better-match extractions are skipped.
- Merge at shared ancestors via `mergeAdd`.

For the "no match" remainder:
- Rebuild tree from root, recursively replacing each node with its `splitNodes` version.
- Uses `transformAccessors` for copy-on-write reconstruction.

### `buildRemainingSuffix`

Builds the unmatched suffix portion as `AccessPath.AccessNode`:
- `suffixLength=0` → null (full match)
- `suffixLength=k` → `suffix[0..k-1]`
- `suffixLength=suffixSize` → full original suffix (+ FINAL if applicable)

---

## Test Cases (implemented in `ExtractMatchingSuffixTest`)

1. **nullSuffix** — null suffix returns `[(tree, null)]`
2. **singlePathFullMatch** — single path fully matching the suffix
3. **singlePathPartialMatch** — single path with suffix matching at the end
4. **singlePathNoMatch** — suffix doesn't match any path
5. **branchingTreeSuffixMatchesOneBranch** — DAG with shared leaf node, suffix matches one branch; round-trip check
6. **branchingTreeDominationPartialConsumption** — deeper suffix match with non-matching branch; round-trip check
7. **dominationNodeFullyConsumed** — all paths fully match, single result
8. **roundTripInvariant** — complex tree with multiple paths, verify `mergeAdd(all results) == original`
