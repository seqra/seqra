package test

// ── Advanced interprocedural tests ───────────────────────────────────

// ── Mutual recursion ─────────────────────────────────────────────────

func mutualA(s string, n int) string {
	if n <= 0 {
		return s
	}
	return mutualB(s, n-1)
}

func mutualB(s string, n int) string {
	return mutualA(s, n-1)
}

func mutualRecursion001T() {
	data := source()
	result := mutualA(data, 4)
	sink(result)
}

func mutualRecursion002F() {
	_ = source()
	result := mutualA("safe", 4)
	sink(result)
}

// ── Function returning function result ───────────────────────────────

func wrapIdentity(s string) string {
	return identity(s)
}

func wrapDrop(s string) string {
	return dropValue(s)
}

func funcReturnFunc001T() {
	data := source()
	result := wrapIdentity(data)
	sink(result)
}

func funcReturnFunc002F() {
	data := source()
	result := wrapDrop(data)
	sink(result)
}

// ── Multiple callers of same function ────────────────────────────────

func multiCaller001T() {
	data := source()
	r1 := identity(data)
	r2 := identity("safe")
	sink(r1)
	consume(r2)
}

func multiCaller002F() {
	data := source()
	r1 := identity("safe")
	r2 := identity(data)
	sink(r1)
	consume(r2)
}

// ── Pass through multiple functions ──────────────────────────────────

func passA(s string) string { return passB(s) }
func passB(s string) string { return passC(s) }
func passC(s string) string { return s }

func multiPass001T() {
	data := source()
	result := passA(data)
	sink(result)
}

func multiPass002F() {
	_ = source()
	result := passA("safe")
	sink(result)
}

// ── Function with side effects on argument ───────────────────────────

type AdvHolder struct {
	data string
}

func fillHolder(h *AdvHolder, val string) {
	h.data = val
}

func advSideEffect001T() {
	data := source()
	h := &AdvHolder{}
	fillHolder(h, data)
	sink(h.data)
}

func advSideEffect002F() {
	_ = source()
	h := &AdvHolder{}
	fillHolder(h, "safe")
	sink(h.data)
}

// ── Builder pattern ──────────────────────────────────────────────────

type AdvBuilder struct {
	result string
}

func (b *AdvBuilder) Add(s string) *AdvBuilder {
	b.result = b.result + s
	return b
}

func (b *AdvBuilder) Build() string {
	return b.result
}

func builder001T() {
	data := source()
	b := &AdvBuilder{}
	result := b.Add(data).Build()
	sink(result)
}

func builder002F() {
	_ = source()
	b := &AdvBuilder{}
	result := b.Add("safe").Build()
	sink(result)
}

// ── Function that returns different values based on condition ─────────

func condReturn(s string, useIt bool) string {
	if useIt {
		return s
	}
	return "safe"
}

func condReturn001T() {
	data := source()
	result := condReturn(data, true)
	sink(result)
}

func condReturn002T() {
	// Conservative: analysis doesn't know runtime value of condition
	data := source()
	result := condReturn(data, false)
	sink(result)
}
