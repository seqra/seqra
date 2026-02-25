package formatters

import (
	"testing"

	"github.com/seqra/seqra/v2/internal/utils/color"
)

func TestTreePrinterPushPopLevels(t *testing.T) {
	p := NewTreePrinter()

	p.AddNode("root")
	p.Push()
	p.AddNode("child")
	p.Push()
	p.AddNode("grandchild")
	p.Pop()
	p.AddNode("child-2")
	p.Pop()
	p.AddNode("root-2")

	if len(p.nodes) != 5 {
		t.Fatalf("expected 5 nodes, got %d", len(p.nodes))
	}

	levels := []int{p.nodes[0].level, p.nodes[1].level, p.nodes[2].level, p.nodes[3].level, p.nodes[4].level}
	expected := []int{0, 1, 2, 1, 0}

	for i := range expected {
		if levels[i] != expected[i] {
			t.Fatalf("node %d level: expected %d, got %d", i, expected[i], levels[i])
		}
	}
}

func TestTreePrinterPopAtRootIsNoop(t *testing.T) {
	p := NewTreePrinter()

	p.Pop()
	p.AddNode("root")

	if len(p.nodes) != 1 {
		t.Fatalf("expected 1 node, got %d", len(p.nodes))
	}

	if p.nodes[0].level != 0 {
		t.Fatalf("expected node level 0, got %d", p.nodes[0].level)
	}
}

func TestTreePrinterNodeVariants(t *testing.T) {
	p := NewTreePrinter()

	p.AddNodeColored("error", color.Red)
	p.AddNodeColoredWrapped("warning", color.Yellow)
	p.AddNodeWrapped("wrapped")

	if len(p.nodes) != 3 {
		t.Fatalf("expected 3 nodes, got %d", len(p.nodes))
	}

	if p.nodes[0].textColor != color.Red {
		t.Fatalf("expected first node color %q, got %q", color.Red, p.nodes[0].textColor)
	}
	if p.nodes[0].wrapped {
		t.Fatal("expected first node to be unwrapped")
	}

	if p.nodes[1].textColor != color.Yellow {
		t.Fatalf("expected second node color %q, got %q", color.Yellow, p.nodes[1].textColor)
	}
	if !p.nodes[1].wrapped {
		t.Fatal("expected second node to be wrapped")
	}

	if p.nodes[2].textColor != "" {
		t.Fatalf("expected third node default color, got %q", p.nodes[2].textColor)
	}
	if !p.nodes[2].wrapped {
		t.Fatal("expected third node to be wrapped")
	}
}
