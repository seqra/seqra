package test.samples;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

public class AnnotatedSample<T> {
    private T data;
    private List<String> items;

    @Deprecated
    public /*annotatedConstructor:start*/AnnotatedSample()/*annotatedConstructor:end*/ {
        this.data = null;
    /*annotatedConstructorExit:start*/}/*annotatedConstructorExit:end*/

    @Deprecated
    @SuppressWarnings("unchecked")
    public /*multiAnnotationConstructor:start*/AnnotatedSample(T data)/*multiAnnotationConstructor:end*/ {
        this.data = data;
    /*multiAnnotationConstructorExit:start*/}/*multiAnnotationConstructorExit:end*/

    @Deprecated
    public <R> /*annotatedMethod:start*/R transform(java.util.function.Function<T, R> fn)/*annotatedMethod:end*/ {
        return fn.apply(data);
    /*annotatedMethodExit:start*/}/*annotatedMethodExit:end*/

    @SuppressWarnings("unchecked")
    public <E extends Number> /*typeParamMethod:start*/E process(E input)/*typeParamMethod:end*/ {
        return input;
    /*typeParamMethodExit:start*/}/*typeParamMethodExit:end*/

    public void localAnnotations() {
        /*annotatedLocal:start*/@SuppressWarnings("unused") String unused = "test"/*annotatedLocal:end*/;
        /*multiAnnotatedLocal:start*/@Deprecated @SuppressWarnings("all") List<String> list = null/*multiAnnotatedLocal:end*/;
        bh(unused);
        bh(list);
    }

    public <K, V extends Comparable<V>> /*complexSignature:start*/java.util.Map<K, V> complexMethod(K key, V value)/*complexSignature:end*/ {
        return java.util.Collections.singletonMap(key, value);
    /*complexSignatureExit:start*/}/*complexSignatureExit:end*/

    @Target(ElementType.LOCAL_VARIABLE)
    @interface LocalOnly {}

    private void bh(Object o) {}
}
