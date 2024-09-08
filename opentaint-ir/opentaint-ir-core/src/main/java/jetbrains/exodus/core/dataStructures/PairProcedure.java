
package jetbrains.exodus.core.dataStructures;

public interface PairProcedure<K, V> {

    boolean execute(K key, V value);
}
