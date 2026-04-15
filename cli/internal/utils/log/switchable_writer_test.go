package log

import (
	"bytes"
	"testing"
)

func TestSwitchableWriter_WritesInitial(t *testing.T) {
	var buf bytes.Buffer
	sw := NewSwitchableWriter(&buf)
	_, _ = sw.Write([]byte("hello"))
	if buf.String() != "hello" {
		t.Fatalf("expected 'hello', got %q", buf.String())
	}
}

func TestSwitchableWriter_SwapRedirects(t *testing.T) {
	var buf1, buf2 bytes.Buffer
	sw := NewSwitchableWriter(&buf1)
	_, _ = sw.Write([]byte("before"))
	sw.Swap(&buf2)
	_, _ = sw.Write([]byte("after"))
	if buf1.String() != "before" {
		t.Fatalf("buf1: expected 'before', got %q", buf1.String())
	}
	if buf2.String() != "after" {
		t.Fatalf("buf2: expected 'after', got %q", buf2.String())
	}
}
