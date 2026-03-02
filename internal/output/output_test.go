package output

import (
	"bytes"
	"regexp"
	"strings"
	"testing"
	"time"
)

func TestPrinterPrint(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Print("hello world")

	got := buf.String()
	if !strings.Contains(got, "hello world") {
		t.Errorf("expected output to contain 'hello world', got %q", got)
	}
}

func TestPrinterPrintf(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Printf("hello %s", "world")

	got := buf.String()
	if !strings.Contains(got, "hello world") {
		t.Errorf("expected output to contain 'hello world', got %q", got)
	}
}

func TestPrinterQuietDoesNotSuppressTextOutput(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", true)

	p.Print("hello")
	p.Printf("value %d", 42)
	p.Warn("warning message")

	got := buf.String()
	if !strings.Contains(got, "hello") {
		t.Errorf("expected Print output in quiet mode, got %q", got)
	}
	if !strings.Contains(got, "value 42") {
		t.Errorf("expected Printf output in quiet mode, got %q", got)
	}
	if !strings.Contains(got, "warning message") {
		t.Errorf("expected Warn output in quiet mode, got %q", got)
	}
}

func TestPrinterErrorNotSuppressedByQuiet(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", true)

	p.Error("fatal problem")

	got := buf.String()
	if !strings.Contains(got, "fatal problem") {
		t.Errorf("expected error output even in quiet mode, got %q", got)
	}
}

func TestSectionRender(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", false)

	p.Section("Test Section").
		Field("Key1", "Value1").
		Field("Key2", "Value2").
		Render()

	got := buf.String()
	if !strings.Contains(got, "Test Section") {
		t.Errorf("expected section title in output, got %q", got)
	}
	if !strings.Contains(got, "Key1") || !strings.Contains(got, "Value1") {
		t.Errorf("expected field Key1: Value1 in output, got %q", got)
	}
	if !strings.Contains(got, "Key2") || !strings.Contains(got, "Value2") {
		t.Errorf("expected field Key2: Value2 in output, got %q", got)
	}
}

func TestSectionWithGroup(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", false)

	p.Section("Grouped").
		Group("Items",
			p.Item("Alpha"),
			p.Item("Beta"),
		).
		Render()

	got := buf.String()
	if !strings.Contains(got, "Items") {
		t.Errorf("expected group name 'Items' in output, got %q", got)
	}
	if !strings.Contains(got, "Alpha") || !strings.Contains(got, "Beta") {
		t.Errorf("expected group children in output, got %q", got)
	}
}

func TestSectionMirroredToLogWriter(t *testing.T) {
	var buf bytes.Buffer
	var logBuf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("always", false)
	p.SetLogWriter(&logBuf)

	p.Section("Mirrored Section").
		StyledText("colored text", p.Theme().Success).
		Field("Key", "Value").
		Render()

	logged := logBuf.String()
	if !strings.Contains(logged, "Mirrored Section") {
		t.Errorf("expected mirrored section title in log output, got %q", logged)
	}
	if !strings.Contains(logged, "Key") || !strings.Contains(logged, "Value") {
		t.Errorf("expected mirrored field in log output, got %q", logged)
	}
	if regexp.MustCompile(`\x1b\[[0-9;?]*[ -/]*[@-~]`).MatchString(logged) {
		t.Errorf("expected mirrored log output without ANSI escapes, got %q", logged)
	}
}

func TestSectionQuietStillRenders(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", true)

	p.Section("Should Appear").
		Field("Key", "Value").
		Render()

	got := buf.String()
	if !strings.Contains(got, "Should Appear") {
		t.Errorf("expected section output in quiet mode, got %q", got)
	}
}

func TestFormatDuration(t *testing.T) {
	tests := []struct {
		seconds  int
		expected string
	}{
		{0, "0s"},
		{5, "5s"},
		{65, "1m 5s"},
		{3665, "1h 1m 5s"},
	}

	for _, tt := range tests {
		d := time.Duration(tt.seconds) * time.Second
		got := FormatDuration(d)
		if got != tt.expected {
			t.Errorf("FormatDuration(%ds) = %q, want %q", tt.seconds, got, tt.expected)
		}
	}
}

func TestFormatSize(t *testing.T) {
	tests := []struct {
		bytes    int64
		expected string
	}{
		{0, "0 B"},
		{500, "500 B"},
		{1024, "1.0 KB"},
		{1536, "1.5 KB"},
		{1048576, "1.0 MB"},
		{1073741824, "1.0 GB"},
	}

	for _, tt := range tests {
		got := FormatSize(tt.bytes)
		if got != tt.expected {
			t.Errorf("FormatSize(%d) = %q, want %q", tt.bytes, got, tt.expected)
		}
	}
}

func TestBoxRender(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", false)

	p.Box("TITLE").
		Field("Key", "Value").
		Render()

	got := buf.String()
	if !strings.Contains(got, "TITLE") {
		t.Errorf("expected box title in output, got %q", got)
	}
	if !strings.Contains(got, "Key") || !strings.Contains(got, "Value") {
		t.Errorf("expected field in box output, got %q", got)
	}
	if !strings.Contains(got, "╭") || !strings.Contains(got, "╯") {
		t.Errorf("expected box borders in output, got %q", got)
	}
}

func TestSuggest(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", false)

	p.Suggest("Try this approach", "seqra scan --output result.sarif /project")

	got := buf.String()
	if !strings.Contains(got, "Suggestions") {
		t.Errorf("expected 'Suggestions' header in output, got %q", got)
	}
	if !strings.Contains(got, "Try this approach") {
		t.Errorf("expected suggestion description in output, got %q", got)
	}
	if !strings.Contains(got, "seqra scan") {
		t.Errorf("expected command in output, got %q", got)
	}
}

func TestFileLinkNonTTY(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf) // non-TTY since it's a buffer
	got := p.FileLink("/project", "src/Main.java", "Main.java", 42)
	if got != "Main.java:42" {
		t.Errorf("expected plain text link for non-TTY, got %q", got)
	}
}

func TestIsInteractiveUIDisabledOnDebugVerbosity(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", false)
	p.isTTY = true
	p.SetVerbosity("debug")

	if p.IsInteractiveUI() {
		t.Fatal("expected interactive UI to be disabled on debug verbosity")
	}
}

func TestIsInteractiveUIEnabledOnInfoVerbosity(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", false)
	p.isTTY = true
	p.SetVerbosity("info")

	if !p.IsInteractiveUI() {
		t.Fatal("expected interactive UI to be enabled on info verbosity")
	}
}
