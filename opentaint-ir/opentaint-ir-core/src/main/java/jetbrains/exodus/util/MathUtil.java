
package jetbrains.exodus.util;

public class MathUtil {

    /**
     * @param i integer
     * @return discrete logarithm of specified integer base 2
     */
    public static int integerLogarithm(final int i) {
        return i <= 0 ? 0 : Integer.SIZE - Integer.numberOfLeadingZeros(i - 1);
    }

    /**
     * @param l long
     * @return discrete logarithm of specified integer base 2
     */
    public static long longLogarithm(final long l) {
        return l <= 0 ? 0 : Long.SIZE - Long.numberOfLeadingZeros(l - 1);
    }
}
