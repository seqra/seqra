package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx

/** Visit each proper prefix before looking up its next child. */
inline fun <N : Any> walkPath(
    root: N,
    path: IntArray,
    child: (N, AccessorIdx) -> N?,
    onPrefix: (Int, N) -> Unit = { _, _ -> },
): N? {
    var node = root
    for (depth in path.indices) {
        onPrefix(depth, node)
        node = child(node, path[depth]) ?: return null
    }
    return node
}

inline fun <P : TrieLeaf> TrieNode<P>.walk(
    path: IntArray,
    onPrefix: (Int, TrieNode<P>) -> Unit = { _, _ -> },
): TrieNode<P>? = walkPath(this, path, { node, accessor -> node.child(accessor) }, onPrefix)
