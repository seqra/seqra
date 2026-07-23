package phase3;

import base.RuleSample;
import base.RuleSet;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

// Coverage for the java.nio buffer models after the <rule-storage> collapse.
// Each Positive puts tainted data into a buffer and reads it back out; the
// byte[] overloads exercise the element->scalar carriers that must stay explicit.
@RuleSet("phase3/CoverageBuffers.yaml")
public abstract class CoverageBuffers implements RuleSample {
    public byte[] bsrc() { return new byte[]{1}; }
    public String ssrc() { return "tainted"; }
    public void strSink(String s) {}
    public void bytesSink(byte[] b) {}

    // java.nio.ByteBuffer#put(byte[]) : arg0 and arg0[*] -> this
    static class PositivePutBytesReadArray extends CoverageBuffers {
        @Override public void entrypoint() {
            byte[] data = bsrc();
            ByteBuffer buf = ByteBuffer.allocate(16);
            buf.put(data);
            bytesSink(buf.array());
        }
    }

    // java.nio.ByteBuffer#get(byte[]) : this -> arg0[*] (scalar -> element)
    static class PositiveGetIntoArray extends CoverageBuffers {
        @Override public void entrypoint() {
            byte[] data = bsrc();
            ByteBuffer buf = ByteBuffer.allocate(16);
            buf.put(data);
            byte[] out = new byte[16];
            buf.get(out);
            bytesSink(out);
        }
    }

    // java.nio.CharBuffer#put(String) then toString
    static class PositiveCharBufferPutToString extends CoverageBuffers {
        @Override public void entrypoint() {
            CharBuffer buf = CharBuffer.allocate(16);
            buf.put(ssrc());
            strSink(buf.toString());
        }
    }

    // Negative: a clean buffer must not be reported.
    static class NegativeCleanBuffer extends CoverageBuffers {
        @Override public void entrypoint() {
            ByteBuffer buf = ByteBuffer.allocate(16);
            buf.put(new byte[]{2});
            bytesSink(buf.array());
        }
    }
}
