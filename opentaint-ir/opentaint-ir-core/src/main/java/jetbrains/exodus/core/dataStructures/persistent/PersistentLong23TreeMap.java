
package jetbrains.exodus.core.dataStructures.persistent;

import jetbrains.exodus.core.dataStructures.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class PersistentLong23TreeMap<V> implements PersistentLongMap<V> {

    private final Persistent23Tree<Entry<V>> set;

    public PersistentLong23TreeMap() {
        this(null);
    }

    private PersistentLong23TreeMap(@Nullable final AbstractPersistent23Tree.RootNode<Entry<V>> root) {
        set = new Persistent23Tree<>(root);
    }

    @Override
    public PersistentLongMap.ImmutableMap<V> beginRead() {
        return new ImmutableMap<>(set.getRoot());
    }

    @Override
    public PersistentLong23TreeMap<V> getClone() {
        return new PersistentLong23TreeMap<>(set.getRoot());
    }

    @Override
    public PersistentLongMap.MutableMap<V> beginWrite() {
        return new MutableMap<>(set);
    }

    @Deprecated
    public boolean endWrite(MutableMap<V> tree) {
        return set.endWrite(tree);
    }

    protected static class ImmutableMap<V> extends Persistent23Tree.ImmutableTree<Entry<V>> implements PersistentLongMap.ImmutableMap<V> {

        ImmutableMap(RootNode<Entry<V>> root) {
            super(root);
        }

        @Override
        public V get(long key) {
            Node<Entry<V>> root = getRoot();
            if (root == null) {
                return null;
            }
            Entry<V> entry = root.getByWeight(key);
            return entry == null ? null : entry.getValue();
        }

        @Override
        public boolean containsKey(long key) {
            final Node<Entry<V>> root = getRoot();
            return root != null && root.getByWeight(key) != null;
        }

        @Override
        public Iterator<Entry<V>> tailEntryIterator(long staringKey) {
            return tailIterator(new LongMapEntry<>(staringKey));
        }

        @Override
        public Iterator<Entry<V>> tailReverseEntryIterator(long staringKey) {
            return tailReverseIterator(new LongMapEntry<>(staringKey));
        }
    }

    protected static class MutableMap<V> extends Persistent23Tree.MutableTree<Entry<V>> implements PersistentLongMap.MutableMap<V>, RootHolder {

        MutableMap(Persistent23Tree<Entry<V>> set) {
            super(set);
        }

        @Override
        public V get(long key) {
            Node<Entry<V>> root = getRoot();
            if (root == null) {
                return null;
            }
            Entry<V> entry = root.getByWeight(key);
            return entry == null ? null : entry.getValue();
        }

        @Override
        public boolean containsKey(long key) {
            return get(key) != null;
        }

        @Override
        public Iterator<Entry<V>> tailEntryIterator(long staringKey) {
            return tailIterator(new LongMapEntry<>(staringKey));
        }

        @Override
        public Iterator<Entry<V>> tailReverseEntryIterator(long staringKey) {
            return tailReverseIterator(new LongMapEntry<>(staringKey));
        }

        @Override
        public void put(long key, @NotNull V value) {
            add(new LongMapEntry<>(key, value));
        }

        @Override
        public V remove(long key) {
            RootNode<Entry<V>> root = getRoot();
            if (root == null) {
                return null;
            }
            Pair<Node<Entry<V>>, Entry<V>> removeResult = root.remove(new LongMapEntry<>(key), true);
            if (removeResult == null) {
                return null;
            }
            Node<Entry<V>> res = removeResult.getFirst();
            if (res instanceof RemovedNode) {
                res = res.getFirstChild();
            }
            root = res == null ? null : res.asRoot(root.getSize() - 1);
            setRoot(root);
            return removeResult.getSecond().getValue();
        }

        @Override
        public void clear() {
            setRoot(null);
        }
    }
}
