package org.opentaint.ir.testing.cfg;

import com.google.common.collect.Lists;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class Close {

    public static Object test() {
        byte[] buf = new byte[42];

        List<CloseableBAOS> list = Lists.newArrayList(new CloseableBAOS(buf),
                new CloseableBAOS(buf), new CloseableBAOS(buf),
                new CloseableBAOS(buf));

        Enumeration<CloseableBAOS> enumeration = Collections.enumeration(list);

        SequenceInputStream sequence = new SequenceInputStream(enumeration);
        try {
            sequence.close();
            throw new RuntimeException("Expected IOException not thrown");
        } catch (IOException e) {
            for (CloseableBAOS c : list) {
                if (!c.isClosed()) {
                    throw new RuntimeException("Component stream not closed");
                }
            }
            Throwable[] suppressed = e.getSuppressed();
            if (suppressed == null) {
                throw new RuntimeException("No suppressed exceptions");
            } else if (suppressed.length != list.size() - 1) {
                throw new RuntimeException("Expected " + (list.size() - 1) +
                        " suppressed exceptions but got " + suppressed.length);
            }
            for (Throwable t : suppressed) {
                if (!(t instanceof IOException)) {
                    throw new RuntimeException("Expected IOException but got " +
                            t.getClass().getName());
                }
            }
        }
        return null;
    }

    static class CloseableBAOS extends ByteArrayInputStream {
        private boolean closed;

        CloseableBAOS(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            throw new IOException();
        }

        public boolean isClosed() {
            return closed;
        }
    }
}
