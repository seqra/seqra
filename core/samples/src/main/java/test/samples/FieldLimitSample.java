package test.samples;

public class FieldLimitSample {

    static class Leaf {
        String value;
    }

    static class Middle {
        Leaf leaf;
    }

    static class Root {
        Middle middle;
    }

    static class LeafHolder {
        Leaf leaf;
    }

    public void depthThreeFlow() {
        Root root = wrapThree(source());
        sinkThree(root);
    }

    public void depthOneFlow() {
        Leaf leaf = wrapOne(source());
        sinkOne(leaf);
    }

    public void mixedDepthFlow() {
        Leaf leaf = taintedLeaf();
        LeafHolder holder = new LeafHolder();
        holder.leaf = leaf;
        sinkHolderLeaf(holder);
        sinkHolderLeafValue(holder);
    }

    private Leaf taintedLeaf() {
        Leaf leaf = leafSource();
        leaf.value = source();
        return leaf;
    }

    private void sinkHolderLeaf(LeafHolder holder) {
        shallowSink(holder.leaf);
    }

    private void sinkHolderLeafValue(LeafHolder holder) {
        deepSink(holder.leaf.value);
    }

    private Root wrapThree(String data) {
        Leaf leaf = new Leaf();
        leaf.value = data;

        Middle middle = new Middle();
        middle.leaf = leaf;

        Root root = new Root();
        root.middle = middle;
        return root;
    }

    private void sinkThree(Root root) {
        sink(root.middle.leaf.value);
    }

    private Leaf wrapOne(String data) {
        Leaf leaf = new Leaf();
        leaf.value = data;
        return leaf;
    }

    private void sinkOne(Leaf leaf) {
        sink(leaf.value);
    }

    public String source() {
        return "tainted";
    }

    public void sink(String data) {
    }

    public Leaf leafSource() {
        return new Leaf();
    }

    public void shallowSink(Object data) {
    }

    public void deepSink(String data) {
    }
}
