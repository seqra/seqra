package util

func Source() string { return "tainted" }
func Sink(s string)  { _ = s }

// builtin append base slice arg(0): folded (elem->elem entry deleted, whole arg(0)->result kept).
func Positive_append_base() {
	bar := Source()
	s := []string{bar}
	r := append(s, "x")
	Sink(r[0])
}

// builtin append variadic arg(1): element star kept (boxed variadic element).
func Positive_append_variadic() {
	bar := Source()
	base := []string{"x"}
	r := append(base, bar)
	Sink(r[1])
}

// builtin copy(dst, src): folded (elem->elem deleted, whole arg(1)->arg(0) kept).
func Positive_copy() {
	bar := Source()
	src := []string{bar}
	dst := make([]string, 1)
	copy(dst, src)
	Sink(dst[0])
}

func Negative_append_clean() {
	s := []string{"safe"}
	r := append(s, "x")
	Sink(r[0])
}
