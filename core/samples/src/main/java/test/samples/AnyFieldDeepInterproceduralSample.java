package test.samples;

/**
 * The same any-field flow as {@link AnyFieldInterproceduralSample}, at a range of call depths.
 *
 * Every frame between the write and the sink is entered with the container as a formal parameter,
 * so each is analyzed from its own initial fact and the field-unfold request the sink condition
 * raises has to travel the chain on fact-to-fact edges rather than zero-to-fact ones. Fact-to-fact
 * edges vastly outnumber zero-to-fact ones, which is why answering the request on all of them is
 * what costs; the depth here decides how far the request must climb before the mark comes in view.
 *
 * Each depth gets its own disjoint chain on purpose. Sharing the frames would give the climb a
 * shortcut: the shallower chain's writing frame is also a caller of the shared sink frame, so the
 * request gets answered there and never exercises the depth under test.
 *
 * Sibling fields are read on the way down so the frames do not all present the same refinement
 * delta. That breadth is what the request's identity multiplies against: if the caller's delta is
 * part of the key, every frame stores and re-broadcasts its own copy of the same question.
 */
public class AnyFieldDeepInterproceduralSample {
    public static class Path {
        public String value;
    }

    public static class Holder {
        public Path path;
        public Path spare;
        public String note;
    }

    public static class Node {
        public Path payload;
        public Node next;
    }

    /**
     * Self-recursive descent through a repeated field. Each recursive frame is entered with a fact
     * one `.next` deeper than its caller, so the refinement delta the caller brings is exactly the
     * tail by which its own initial fact extends -- the `arg0.next.* -> n.next.*` application of a
     * request already applied at `arg0.* -> n.*`. This is the shape of ImportController#joinKV.
     */
    private void walk(Node n) {
        sink(n.payload);
        if (n.next != null) {
            walk(n.next);
        }
    }

    private void recursiveStore(String value) {
        Node tail = new Node();
        tail.payload = new Path();
        tail.payload.value = value;

        Node head = new Node();
        head.payload = new Path();
        head.next = tail;

        walk(head);
    }

    public void fieldFlowRecursive() { recursiveStore(source()); }

    public String source() {
        return "tainted";
    }

    public void sink(Path path) { }

    private void touch(Path p) { }

    private void note(String s) { }

    // ---- depth 1: 1 frame(s) between the write and the sink ----
    private void d01_f00(Holder h) { sink(h.path); }

    private void d01_store(String value) {
        Holder holder = new Holder();
        holder.path = new Path();
        holder.path.value = value;

        d01_f00(holder);
    }

    public void fieldFlowDepth1() { d01_store(source()); }

    // ---- depth 2: 2 frame(s) between the write and the sink ----
    private void d02_f00(Holder h) { sink(h.path); }
    private void d02_f01(Holder h) { d02_f00(h); }

    private void d02_store(String value) {
        Holder holder = new Holder();
        holder.path = new Path();
        holder.path.value = value;

        d02_f01(holder);
    }

    public void fieldFlowDepth2() { d02_store(source()); }

    // ---- depth 3: 3 frame(s) between the write and the sink ----
    private void d03_f00(Holder h) { sink(h.path); }
    private void d03_f01(Holder h) { d03_f00(h); }
    private void d03_f02(Holder h) { touch(h.spare); d03_f01(h); }

    private void d03_store(String value) {
        Holder holder = new Holder();
        holder.path = new Path();
        holder.path.value = value;

        d03_f02(holder);
    }

    public void fieldFlowDepth3() { d03_store(source()); }

    // ---- depth 5: 5 frame(s) between the write and the sink ----
    private void d05_f00(Holder h) { sink(h.path); }
    private void d05_f01(Holder h) { d05_f00(h); }
    private void d05_f02(Holder h) { touch(h.spare); d05_f01(h); }
    private void d05_f03(Holder h) { d05_f02(h); }
    private void d05_f04(Holder h) { note(h.note); d05_f03(h); }

    private void d05_store(String value) {
        Holder holder = new Holder();
        holder.path = new Path();
        holder.path.value = value;

        d05_f04(holder);
    }

    public void fieldFlowDepth5() { d05_store(source()); }

    // ---- depth 10: 10 frame(s) between the write and the sink ----
    private void d10_f00(Holder h) { sink(h.path); }
    private void d10_f01(Holder h) { d10_f00(h); }
    private void d10_f02(Holder h) { touch(h.spare); d10_f01(h); }
    private void d10_f03(Holder h) { d10_f02(h); }
    private void d10_f04(Holder h) { note(h.note); d10_f03(h); }
    private void d10_f05(Holder h) { d10_f04(h); }
    private void d10_f06(Holder h) { touch(h.spare); d10_f05(h); }
    private void d10_f07(Holder h) { d10_f06(h); }
    private void d10_f08(Holder h) { note(h.note); d10_f07(h); }
    private void d10_f09(Holder h) { d10_f08(h); }

    private void d10_store(String value) {
        Holder holder = new Holder();
        holder.path = new Path();
        holder.path.value = value;

        d10_f09(holder);
    }

    public void fieldFlowDepth10() { d10_store(source()); }
}
