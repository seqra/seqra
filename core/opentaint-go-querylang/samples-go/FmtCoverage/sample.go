package util

import (
	"fmt"
	"strings"
)

func Source() string { return "tainted" }
func Sink(s string)  { _ = s }

// fmt.Sprint: Phase 2 removed [arg(*),'[*]']->result collapse; whole arg(*)->result kept.
func Positive_sprint() {
	Sink(fmt.Sprint("p", Source()))
}

// fmt.Sprintf
func Positive_sprintf() {
	Sink(fmt.Sprintf("%s", Source()))
}

// fmt.Sprintln
func Positive_sprintln() {
	Sink(fmt.Sprintln(Source()))
}

// fmt.Fprint: taints the writer arg(0); read it back.
func Positive_fprint() {
	var b strings.Builder
	fmt.Fprint(&b, Source())
	Sink(b.String())
}

// fmt.Fprintf / fmt.Fprintln: variadic collapse to the writer arg(0) (kept).
func Positive_fprintf() {
	var b strings.Builder
	fmt.Fprintf(&b, "%s", Source())
	Sink(b.String())
}

func Positive_fprintln() {
	var b strings.Builder
	fmt.Fprintln(&b, Source())
	Sink(b.String())
}

// fmt.Append / fmt.Appendf / fmt.Appendln: append formatted args to a []byte (arg->result).
func Positive_append() {
	b := fmt.Append(nil, Source())
	Sink(string(b))
}

func Positive_appendf() {
	b := fmt.Appendf(nil, "%s", Source())
	Sink(string(b))
}

func Positive_appendln() {
	b := fmt.Appendln(nil, Source())
	Sink(string(b))
}

func Negative_clean() {
	Sink(fmt.Sprint("safe", "clean"))
}
