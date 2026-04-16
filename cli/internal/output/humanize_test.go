package output

import (
	"bytes"
	"errors"
	"strings"
	"testing"
)

func TestHumanize(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected string
	}{
		{"lowercase error", errors.New("something went wrong"), "Something went wrong"},
		{"already capitalized", errors.New("Already capitalized"), "Already capitalized"},
		{"single char", errors.New("x"), "X"},
		{"empty error", errors.New(""), ""},
		{"unicode first char", errors.New("\u00e9chec total"), "\u00c9chec total"},
		{"number prefix", errors.New("404 not found"), "404 not found"},
		{"multibyte unchanged", errors.New("\u4f60\u597d"), "\u4f60\u597d"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := Humanize(tt.err)
			if got != tt.expected {
				t.Errorf("Humanize(%q) = %q, want %q", tt.err.Error(), got, tt.expected)
			}
		})
	}
}

func TestPrinterErrorErr(t *testing.T) {
	var buf bytes.Buffer
	p := NewWithWriter(&buf)
	p.Configure("never", false)

	p.ErrorErr(errors.New("something broke"))

	got := buf.String()
	if !strings.Contains(got, "Something broke") {
		t.Errorf("expected capitalized error in output, got %q", got)
	}
}
