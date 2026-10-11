package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.util.Cancellation
import java.util.ArrayDeque
import java.util.IdentityHashMap

/** Pair identity is local to a traversal; node value equality remains structural. */
internal class NodePair<P : TrieLeaf>(val a: TrieNode<P>, val b: TrieNode<P>) {
    private val hash = System.identityHashCode(a) * 31 + System.identityHashCode(b)
    override fun hashCode(): Int = hash
    override fun equals(other: Any?): Boolean = other is NodePair<*> && a === other.a && b === other.b
}

private class PairFrame<P : TrieLeaf>(val pair: NodePair<P>, var expanded: Boolean = false)

private fun <P : TrieLeaf, T> mergeNodeLoop(
    a: TrieNode<P>, b: TrieNode<P>, cancellation: Cancellation?,
    same: (TrieNode<P>) -> T,
    step: (TrieNode<P>, TrieNode<P>, Map<NodePair<P>, T>) -> T,
): T {
    val results = HashMap<NodePair<P>, T>()
    val initial = NodePair(a, b)
    val stack = ArrayDeque<PairFrame<P>>()
    stack.addLast(PairFrame(initial))
    while (stack.isNotEmpty()) {
        cancellation?.checkpoint()
        val frame = stack.peekLast()
        val pair = frame.pair
        if (results.containsKey(pair)) {
            stack.removeLast()
        } else if (pair.a === pair.b) {
            results[pair] = same(pair.a)
            stack.removeLast()
        } else if (!frame.expanded) {
            frame.expanded = true
            val x = pair.a.accessorData
            val y = pair.b.accessorData
            if (x != null && y != null) {
                var i = 0
                var j = 0
                while (i < x.size && j < y.size) {
                    cancellation?.checkpoint()
                    when {
                        x[i] < y[j] -> i++
                        x[i] > y[j] -> j++
                        else -> {
                            stack.addLast(PairFrame(NodePair(pair.a.childData!![i], pair.b.childData!![j])))
                            i++
                            j++
                        }
                    }
                }
            }
        } else {
            results[pair] = step(pair.a, pair.b, results)
            stack.removeLast()
        }
    }
    return results.getValue(initial)
}

/** Return null when all children of a stay unchanged. */
private fun <P : TrieLeaf> mergeAccessors(
    a: TrieNode<P>, b: TrieNode<P>, cancellation: Cancellation?,
    onOtherNode: (AccessorIdx, TrieNode<P>) -> Unit,
    merge: (AccessorIdx, TrieNode<P>, TrieNode<P>) -> TrieNode<P>,
): Pair<IntArray, Array<TrieNode<P>>>? {
    val y = b.accessorData ?: return null
    val yc = b.childData!!
    val x = a.accessorData
    val xc = a.childData
    if (x == null) {
        for (i in y.indices) {
            cancellation?.checkpoint()
            onOtherNode(y[i], yc[i])
        }
        return y to yc
    }
    val accessors = ArrayList<Int>(x.size + y.size)
    val children = ArrayList<TrieNode<P>>(x.size + y.size)
    var i = 0
    var j = 0
    var changed = false
    while (i < x.size || j < y.size) {
        cancellation?.checkpoint()
        when {
            j == y.size || (i < x.size && x[i] < y[j]) -> {
                accessors += x[i]
                children += xc!![i++]
            }
            i == x.size || y[j] < x[i] -> {
                onOtherNode(y[j], yc[j])
                accessors += y[j]
                children += yc[j++]
                changed = true
            }
            else -> {
                val child = merge(x[i], xc!![i], yc[j])
                accessors += x[i]
                children += child
                changed = changed || child !== xc[i]
                i++
                j++
            }
        }
    }
    return if (changed) accessors.toIntArray() to children.toTypedArray() else null
}

fun <P : TrieLeaf> LeafAlgebra<P>.mergeAdd(
    a: TrieNode<P>, b: TrieNode<P>, cancellation: Cancellation? = null,
): TrieNode<P> = mergeNodeLoop(a, b, cancellation, { it }) { x, y, results ->
    val leaf = union(x.leaf, y.leaf)
    val merged = mergeAccessors(x, y, cancellation, { _, _ -> }) { _, c, d ->
        results.getValue(NodePair(c, d))
    }
    if (leaf == x.leaf && merged == null) x
    else node(leaf, merged?.first ?: x.accessorData, merged?.second ?: x.childData)
}

fun <P : TrieLeaf> LeafAlgebra<P>.mergeAddDelta(
    a: TrieNode<P>, b: TrieNode<P>, cancellation: Cancellation? = null,
): Pair<TrieNode<P>, TrieNode<P>?> =
    mergeNodeLoop<P, Pair<TrieNode<P>, TrieNode<P>?>>(a, b, cancellation, { it to null }) { x, y, results ->
        val leaf = union(x.leaf, y.leaf)
        val deltaAccessors = ArrayList<Int>()
        val deltaChildren = ArrayList<TrieNode<P>>()
        val merged = mergeAccessors(x, y, cancellation, { accessor, child ->
            deltaAccessors += accessor
            deltaChildren += child
        }) { accessor, c, d ->
            val (child, delta) = results.getValue(NodePair(c, d))
            if (delta != null) {
                deltaAccessors += accessor
                deltaChildren += delta
            }
            child
        }
        if (leaf == x.leaf && merged == null) x to null
        else {
            val delta = node(minus(y.leaf, x.leaf), deltaAccessors.toIntArray(), deltaChildren.toTypedArray())
                .takeIf { !it.isEmpty }
            node(leaf, merged?.first ?: x.accessorData, merged?.second ?: x.childData) to delta
        }
    }

internal fun ApManager.mergeAddDelta(old: Facts, new: Facts): Pair<Facts, Facts?> {
    check(old.groupKey == new.groupKey) { "T3: merge needs one group key" }
    return when (old) {
        is Reach -> old to null
        is FlowTree -> FlowAlgebra.mergeAddDelta(old.root, (new as FlowTree).root, cancellation)
            .let { (root, delta) -> old.withRoot(root) to delta?.let(old::withRoot) }
        is TaintTree -> taintAlg.mergeAddDelta(old.root, (new as TaintTree).root, cancellation)
            .let { (root, delta) -> old.withRoot(root) to delta?.let(old::withRoot) }
    }
}

internal fun <P : TrieLeaf> LeafAlgebra<P>.withLeaf(n: TrieNode<P>?, leaf: P): TrieNode<P>? = when {
    n == null -> if (leaf.isEmpty) null else leafNode(leaf)
    leaf == n.leaf -> n.takeIf { !it.isEmpty }
    else -> node(leaf, n.accessorData, n.childData).takeIf { !it.isEmpty }
}

internal fun <P : TrieLeaf> LeafAlgebra<P>.mapChildren(
    n: TrieNode<P>, leaf: P, f: (AccessorIdx, TrieNode<P>) -> TrieNode<P>?,
): TrieNode<P>? = mapChildren(n, leaf, null, f)

internal fun <P : TrieLeaf> LeafAlgebra<P>.mapChildren(
    n: TrieNode<P>, leaf: P, cancellation: Cancellation?,
    f: (AccessorIdx, TrieNode<P>) -> TrieNode<P>?,
): TrieNode<P>? {
    cancellation?.checkpoint()
    val accessors = n.accessorData ?: return withLeaf(n, leaf)
    val children = n.childData!!
    val outAccessors = ArrayList<Int>(accessors.size)
    val outChildren = ArrayList<TrieNode<P>>(accessors.size)
    var changed = false
    for (i in accessors.indices) {
        cancellation?.checkpoint()
        val child = f(accessors[i], children[i])
        changed = changed || child !== children[i]
        if (child != null) {
            outAccessors += accessors[i]
            outChildren += child
        }
    }
    return if (!changed) withLeaf(n, leaf)
    else node(leaf, outAccessors.toIntArray(), outChildren.toTypedArray()).takeIf { !it.isEmpty }
}

internal fun <P : TrieLeaf> LeafAlgebra<P>.replaceChild(
    n: TrieNode<P>?, leaf: P, accessor: AccessorIdx, child: TrieNode<P>?,
): TrieNode<P>? {
    if (n?.child(accessor) === child) return withLeaf(n, leaf)
    val accessors = ArrayList<Int>()
    val children = ArrayList<TrieNode<P>>()
    var inserted = child == null
    n?.accessorData?.forEachIndexed { i, current ->
        if (!inserted && current > accessor) {
            accessors += accessor
            children += child!!
            inserted = true
        }
        if (current == accessor) {
            if (child != null) {
                accessors += current
                children += child
            }
            inserted = true
        } else {
            accessors += current
            children += n.childData!![i]
        }
    }
    if (!inserted) {
        accessors += accessor
        children += child!!
    }
    return node(leaf, accessors.toIntArray(), children.toTypedArray()).takeIf { !it.isEmpty }
}

internal fun <P : TrieLeaf> LeafAlgebra<P>.retainChildren(
    n: TrieNode<P>, pred: (AccessorIdx) -> Boolean,
): TrieNode<P>? = retainChildren(n, null, pred)

internal fun <P : TrieLeaf> LeafAlgebra<P>.retainChildren(
    n: TrieNode<P>, cancellation: Cancellation?, pred: (AccessorIdx) -> Boolean,
): TrieNode<P>? = mapChildren(n, empty, cancellation) { accessor, child -> if (pred(accessor)) child else null }

private class NodeFrame<P : TrieLeaf>(val node: TrieNode<P>, var next: Int = 0)

/** Compute each distinct input node once, children before parent. Null is a cached result too. */
private fun <P : TrieLeaf, T> walkNodes(
    root: TrieNode<P>, cancellation: Cancellation?,
    step: (TrieNode<P>, IdentityHashMap<TrieNode<P>, T>) -> T,
): T {
    val memo = IdentityHashMap<TrieNode<P>, T>()
    val stack = ArrayDeque<NodeFrame<P>>()
    stack.addLast(NodeFrame(root))
    while (stack.isNotEmpty()) {
        cancellation?.checkpoint()
        val frame = stack.peekLast()
        val children = frame.node.childData
        if (memo.containsKey(frame.node)) {
            stack.removeLast()
        } else if (children != null && frame.next < children.size) {
            val child = children[frame.next++]
            if (!memo.containsKey(child)) stack.addLast(NodeFrame(child))
        } else {
            memo[frame.node] = step(frame.node, memo)
            stack.removeLast()
        }
    }
    return memo.getValue(root)
}

fun <P : TrieLeaf> LeafAlgebra<P>.mapLeaves(
    n: TrieNode<P>, f: (P) -> P,
): TrieNode<P>? = mapLeaves(n, null, f)

fun <P : TrieLeaf> LeafAlgebra<P>.mapLeaves(
    n: TrieNode<P>, cancellation: Cancellation?, f: (P) -> P,
): TrieNode<P>? = walkNodes(n, cancellation) { current, memo ->
    mapChildren(current, intern(f(current.leaf)), cancellation) { _, child -> memo.getValue(child) }
}

/** Cached folds skip complete subtrees and do not expand shared DAGs. */
fun <P : TrieLeaf> LeafAlgebra<P>.foldAll(n: TrieNode<P>, cancellation: Cancellation? = null): P {
    val stack = ArrayDeque<NodeFrame<P>>()
    stack.addLast(NodeFrame(n))
    while (stack.isNotEmpty()) {
        cancellation?.checkpoint()
        val frame = stack.peekLast()
        if (frame.node.allLeaves != null) {
            stack.removeLast()
            continue
        }
        val children = frame.node.childData
        if (children != null && frame.next < children.size) {
            val child = children[frame.next++]
            if (child.allLeaves == null) stack.addLast(NodeFrame(child))
            continue
        }
        var leaf = frame.node.leaf
        children?.forEach {
            cancellation?.checkpoint()
            leaf = union(leaf, checkNotNull(it.allLeaves))
        }
        frame.node.allLeaves = leaf
        stack.removeLast()
    }
    return checkNotNull(n.allLeaves)
}

internal fun <P : TrieLeaf> LeafAlgebra<P>.foldLeaves(
    n: TrieNode<P>, pred: (AccessorIdx) -> Boolean,
): P = foldLeaves(n, null, pred)

internal fun <P : TrieLeaf> LeafAlgebra<P>.foldLeaves(
    n: TrieNode<P>, cancellation: Cancellation?, pred: (AccessorIdx) -> Boolean,
): P {
    cancellation?.checkpoint()
    var leaf = n.leaf
    n.accessorData?.forEachIndexed { i, accessor ->
        cancellation?.checkpoint()
        if (pred(accessor)) leaf = union(leaf, foldAll(n.childData!![i], cancellation))
    }
    return leaf
}

fun <P : TrieLeaf> LeafAlgebra<P>.prepend(m: ApManager, path: PathNode?, n: TrieNode<P>): TrieNode<P> {
    check(path == null || path.manager === m) { "A prepended path must use this AP manager" }
    m.cancellation.checkpoint()
    if (path == null || n.isEmpty) return n
    var result = n
    val accessors = path.toIntArray()
    for (i in accessors.indices.reversed()) {
        m.cancellation.checkpoint()
        result = node(empty, intArrayOf(accessors[i]), arrayOf(result))
    }
    return result
}

internal fun <P : TrieLeaf> LeafAlgebra<P>.chain(
    m: ApManager, path: IntArray, spine: List<P>, tip: TrieNode<P>? = null,
): TrieNode<P>? {
    check(spine.size.toLong() <= path.size.toLong() + 1) { "The spine exceeds its path" }
    m.cancellation.checkpoint()
    var result = tip?.takeIf { !it.isEmpty }
    if (spine.size.toLong() == path.size.toLong() + 1)
        result = withLeaf(result, union(result?.leaf ?: empty, spine[path.size]))
    for (i in path.indices.reversed()) {
        m.cancellation.checkpoint()
        result = replaceChild(null, spine.getOrNull(i) ?: empty, path[i], result)
    }
    return result
}

fun <P : TrieLeaf> LeafAlgebra<P>.minusNode(
    n: TrieNode<P>, k: TrieNode<P>, cancellation: Cancellation? = null,
): TrieNode<P>? = mergeNodeLoop(n, k, cancellation, { null as TrieNode<P>? }) { current, part, memo ->
    mapChildren(current, minus(current.leaf, part.leaf), cancellation) { accessor, child ->
        val matching = part.child(accessor)
        if (matching == null) child else memo.getValue(NodePair(child, matching))
    }
}

private class LeafFrame<P : TrieLeaf>(
    val node: TrieNode<P>, val depth: Int, var next: Int = 0, var entered: Boolean = false,
)

/** A shared subtree contributes once per path; callbacks receive owned path arrays. */
fun <P : TrieLeaf> LeafAlgebra<P>.forEachLeaf(
    n: TrieNode<P>, prefix: IntArray, f: (IntArray, P) -> Unit,
) = forEachLeaf(n, prefix, null, f)

fun <P : TrieLeaf> LeafAlgebra<P>.forEachLeaf(
    n: TrieNode<P>, prefix: IntArray, cancellation: Cancellation?, f: (IntArray, P) -> Unit,
) {
    var path = prefix.copyOf(maxOf(16, prefix.size))
    val stack = ArrayDeque<LeafFrame<P>>()
    stack.addLast(LeafFrame(n, prefix.size))
    while (stack.isNotEmpty()) {
        cancellation?.checkpoint()
        val frame = stack.peekLast()
        if (!frame.entered) {
            frame.entered = true
            if (!frame.node.leaf.isEmpty) f(path.copyOf(frame.depth), frame.node.leaf)
        }
        val accessors = frame.node.accessorData
        if (accessors == null || frame.next == accessors.size) {
            stack.removeLast()
            continue
        }
        if (frame.depth == path.size) {
            val capacity = maxOf(frame.depth.toLong() + 1, path.size.toLong() * 2)
            check(capacity <= Int.MAX_VALUE) { "A leaf path exceeds its array representation" }
            path = path.copyOf(capacity.toInt())
        }
        val i = frame.next++
        path[frame.depth] = accessors[i]
        stack.addLast(LeafFrame(frame.node.childData!![i], Math.addExact(frame.depth, 1)))
    }
}

fun <P : TrieLeaf> LeafAlgebra<P>.forEachLeafPosition(
    n: TrieNode<P>, f: (IntArray) -> Unit,
) = forEachLeafPosition(n, null, f)

fun <P : TrieLeaf> LeafAlgebra<P>.forEachLeafPosition(
    n: TrieNode<P>, cancellation: Cancellation?, f: (IntArray) -> Unit,
) = forEachLeaf(n, EMPTY_PATH, cancellation) { path, _ -> f(path) }

fun ApManager.forEachLeafPosition(f: Facts, action: (AccessPathBase, IntArray) -> Unit) {
    cancellation.checkpoint()
    when (f) {
        is Reach -> action(AccessPathBase.Zero, EMPTY_PATH)
        is FlowTree -> FlowAlgebra.forEachLeafPosition(f.root, cancellation) { action(f.base, it) }
        is TaintTree -> taintAlg.forEachLeafPosition(f.root, cancellation) { action(f.base, it) }
    }
}

fun <G : TrieLeaf, R : TrieLeaf> LeafAlgebra<R>.graft(
    g: TrieNode<G>, occurs: (G) -> Boolean, r: TrieNode<R>, cancellation: Cancellation? = null,
): TrieNode<R>? = walkNodes(g, cancellation) { current, memo ->
    val accessors = ArrayList<Int>()
    val children = ArrayList<TrieNode<R>>()
    current.accessorData?.forEachIndexed { i, accessor ->
        cancellation?.checkpoint()
        memo.getValue(current.childData!![i])?.let { child ->
            accessors += accessor
            children += child
        }
    }
    val below = node(empty, accessors.toIntArray(), children.toTypedArray()).takeIf { !it.isEmpty }
    when {
        !occurs(current.leaf) -> below
        below == null -> r.takeIf { !it.isEmpty }
        else -> mergeAdd(r, below, cancellation)
    }
}

private class SubtractKey<P : TrieLeaf>(val node: TrieNode<P>, val stored: TrieNode<P>?, val above: P) {
    private val hash = (System.identityHashCode(node) * 31 + System.identityHashCode(stored)) * 31 + above.hashCode()
    override fun hashCode(): Int = hash
    override fun equals(other: Any?): Boolean = other is SubtractKey<*> &&
        node === other.node && stored === other.stored && above == other.above
}

private class SubtractFrame<P : TrieLeaf>(val key: SubtractKey<P>, var expanded: Boolean = false)

/** Only the first step below each stored leaf reads its exclusion. Prior cover persists. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.subtract(
    n: TrieNode<P>, s: TrieNode<P>?, above: P,
    at: (P) -> P, below: (P) -> P, admits: (AccessorIdx) -> Boolean = { true },
    cancellation: Cancellation? = null,
): TrieNode<P>? {
    val memo = HashMap<SubtractKey<P>, TrieNode<P>?>()
    val initial = SubtractKey(n, s, above)
    val stack = ArrayDeque<SubtractFrame<P>>()
    stack.addLast(SubtractFrame(initial))
    while (stack.isNotEmpty()) {
        cancellation?.checkpoint()
        val frame = stack.peekLast()
        val key = frame.key
        if (memo.containsKey(key)) {
            stack.removeLast()
            continue
        }
        if (key.stored == null && key.above.isEmpty) {
            memo[key] = key.node.takeIf { !it.isEmpty }
            stack.removeLast()
            continue
        }
        val next = key.stored?.let { union(key.above, below(it.leaf)) } ?: key.above
        if (!frame.expanded) {
            frame.expanded = true
            key.node.accessorData?.forEachIndexed { i, accessor ->
                cancellation?.checkpoint()
                val childKey = SubtractKey(key.node.childData!![i], key.stored?.child(accessor),
                    if (admits(accessor)) next else key.above)
                if (!memo.containsKey(childKey)) stack.addLast(SubtractFrame(childKey))
            }
        } else {
            val cover = key.stored?.let { union(key.above, at(it.leaf)) } ?: key.above
            memo[key] = mapChildren(key.node, minus(key.node.leaf, cover), cancellation) { accessor, child ->
                memo.getValue(SubtractKey(child, key.stored?.child(accessor),
                    if (admits(accessor)) next else key.above))
            }
            stack.removeLast()
        }
    }
    return memo.getValue(initial)
}
