package phase3;

import base.RuleSample;
import base.RuleSet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Phase 3 stdlib coverage: java.io / java.nio / java.util.stream passthrough
// entries touched by the redundant-star cleanup. Each Positive flows taint from
// a source, through the changed passthrough, into a holder, then back out to a
// sink. A Positive turning red means the config change dropped a real flow.
@RuleSet("phase3/CoverageStreams.yaml")
public abstract class CoverageStreams implements RuleSample {
    public byte[] bsrc() { return new byte[]{1}; }
    public char[] csrc() { return new char[]{'x'}; }
    public int[] isrc() { return new int[]{1}; }
    public long[] lsrc() { return new long[]{1L}; }
    public String ssrc() { return "tainted"; }

    public void bSink(byte[] b) {}
    public void cSink(char[] c) {}
    public void iSink(int[] i) {}
    public void lSink(long[] l) {}
    public void strSink(String s) {}

    // java.io.OutputStream#write(byte[]) : arg0 -> this ; toByteArray this->result.
    // Also exercises the java-io `write.*` pattern entry (same arg0->this shape).
    static class PositiveOutputStreamWrite extends CoverageStreams {
        @Override public void entrypoint() {
            try {
                byte[] b = bsrc();
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                o.write(b);
                bSink(o.toByteArray());
            } catch (IOException e) {
            }
        }
    }

    // java.io.ByteArrayOutputStream#write(byte[],int,int) : arg0 -> this.
    static class PositiveByteArrayOutputStreamWrite3 extends CoverageStreams {
        @Override public void entrypoint() {
            byte[] b = bsrc();
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            o.write(b, 0, b.length);
            bSink(o.toByteArray());
        }
    }

    // java.nio.ByteBuffer#put(int,byte[]) : arg1 -> this.data ; array() this.data->result.
    static class PositiveByteBufferPut extends CoverageStreams {
        @Override public void entrypoint() {
            byte[] b = bsrc();
            ByteBuffer buf = ByteBuffer.allocate(64);
            buf.put(0, b);
            bSink(buf.array());
        }
    }

    // java.nio.CharBuffer#put(int,char[]) : arg1 -> this.data ; array() -> result.
    static class PositiveCharBufferPut extends CoverageStreams {
        @Override public void entrypoint() {
            char[] c = csrc();
            CharBuffer cb = CharBuffer.allocate(64);
            cb.put(0, c);
            cSink(cb.array());
        }
    }

    // java.nio.IntBuffer#put(int[]) : arg0 -> this.data ; array() -> result.
    static class PositiveIntBufferPut extends CoverageStreams {
        @Override public void entrypoint() {
            int[] i = isrc();
            IntBuffer ib = IntBuffer.allocate(64);
            ib.put(i);
            iSink(ib.array());
        }
    }

    // java.nio.LongBuffer#put(long[]) : arg0 -> this ; array() this->result.
    static class PositiveLongBufferPut extends CoverageStreams {
        @Override public void entrypoint() {
            long[] l = lsrc();
            LongBuffer lb = LongBuffer.allocate(64);
            lb.put(l);
            lSink(lb.array());
        }
    }

    // java.util.stream.Stream#of(Object) : arg0 -> result.Element.
    static class PositiveStreamOf extends CoverageStreams {
        @Override public void entrypoint() {
            String s = ssrc();
            Stream<String> st = Stream.of(s);
            List<String> l = st.collect(Collectors.toList());
            strSink(l.get(0));
        }
    }

    // Negative: a clean local buffer must not be reported.
    static class NegativeCleanByteBuffer extends CoverageStreams {
        @Override public void entrypoint() {
            byte[] b = new byte[]{2};
            ByteBuffer buf = ByteBuffer.allocate(64);
            buf.put(0, b);
            bSink(buf.array());
        }
    }
}
