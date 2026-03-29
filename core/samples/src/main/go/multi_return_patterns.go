package test

// ── Multi-return pattern tests ───────────────────────────────────────

// ── Swap returns ─────────────────────────────────────────────────────

func swapReturns(a string, b string) (string, string) {
	return b, a
}

func multiRetSwap001T() {
	data := source()
	first, _ := swapReturns("clean", data)
	sink(first)
}

func multiRetSwap002F() {
	data := source()
	_, second := swapReturns("clean", data)
	sink(second)
}

// ── Chain of multi-return calls ──────────────────────────────────────

func pairPass(a string, b string) (string, string) {
	return a, b
}

func multiRetChain001T() {
	data := source()
	x, _ := pairPass(data, "clean")
	result, _ := pairPass(x, "other")
	sink(result)
}

func multiRetChain002F() {
	data := source()
	_, y := pairPass(data, "clean")
	_, result := pairPass("other", y)
	sink(result)
}

// ── Multi-return used inside another function call ───────────────────

func firstOf(a string, b string) string {
	return a
}

func multiRetFunc001T() {
	data := source()
	a, _ := pairPass(data, "clean")
	result := firstOf(a, "safe")
	sink(result)
}

func multiRetFunc002F() {
	data := source()
	_, b := pairPass(data, "clean")
	result := firstOf("safe", b)
	sink(result)
}

// ── Discarding returns with _ ────────────────────────────────────────

func multiRetIgnore001T() {
	data := source()
	result, _ := pairPass(data, "clean")
	sink(result)
}

func multiRetIgnore002F() {
	data := source()
	_, _ = pairPass(data, "clean")
	sink("safe")
}
