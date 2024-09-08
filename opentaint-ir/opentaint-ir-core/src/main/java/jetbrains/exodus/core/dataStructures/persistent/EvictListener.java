
package jetbrains.exodus.core.dataStructures.persistent;

public interface EvictListener<K, V> {

    void onEvict(K key, V value);
}
