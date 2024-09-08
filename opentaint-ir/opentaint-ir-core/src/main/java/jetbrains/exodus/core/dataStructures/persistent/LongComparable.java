
package jetbrains.exodus.core.dataStructures.persistent;

public interface LongComparable<K extends Comparable<K>> extends Comparable<K> {

    long getWeight();
}
