package utils

import (
	"testing"
)

func TestParseMemoryValue(t *testing.T) {
	tests := []struct {
		input    string
		expected string
		hasError bool
	}{
		{"1024m", "-Xmx1024m", false},
		{"8G", "-Xmx8g", false},
		{"81920k", "-Xmx81920k", false},
		{"1024M", "-Xmx1024m", false},
		{"8g", "-Xmx8g", false},
		{"81920K", "-Xmx81920k", false},
		{"83886080", "-Xmx83886080", false},
		{"1024", "-Xmx1024", false},
		{"", "", true},
		{"invalid", "", true},
		{"1024x", "", true},
	}

	for _, test := range tests {
		result, err := ParseMemoryValue(test.input)
		if test.hasError {
			if err == nil {
				t.Errorf("Expected error for input %s, but got none", test.input)
			}
		} else {
			if err != nil {
				t.Errorf("Unexpected error for input %s: %v", test.input, err)
			}
			if result != test.expected {
				t.Errorf("For input %s, expected %s, got %s", test.input, test.expected, result)
			}
		}
	}
}
