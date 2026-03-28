package test

// ── Generics tests (Go 1.18+) ───────────────────────────────────────

// ── Generic identity function ────────────────────────────────────────

func GenIdentity[T any](x T) T { return x }

func genericFunc001T() {
	data := source()
	result := GenIdentity(data)
	sink(result)
}

func genericFunc002F() {
	_ = source()
	result := GenIdentity("safe")
	sink(result)
}

func genericFuncInt001T() {
	data := sourceInt()
	result := GenIdentity(data)
	sinkInt(result)
}

// ── Generic container ────────────────────────────────────────────────

type GenBox[T any] struct {
	value T
}

func (b GenBox[T]) Get() T   { return b.value }
func (b *GenBox[T]) Set(v T) { b.value = v }

func genericBox001T() {
	data := source()
	b := GenBox[string]{value: data}
	result := b.Get()
	sink(result)
}

func genericBox002F() {
	_ = source()
	b := GenBox[string]{value: "safe"}
	result := b.Get()
	sink(result)
}

func genericBoxSet001T() {
	data := source()
	b := &GenBox[string]{}
	b.Set(data)
	result := b.Get()
	sink(result)
}

// ── Generic pair ─────────────────────────────────────────────────────

type GenPair[A any, B any] struct {
	first  A
	second B
}

func (p GenPair[A, B]) GetFirst() A  { return p.first }
func (p GenPair[A, B]) GetSecond() B { return p.second }

func genericPair001T() {
	data := source()
	p := GenPair[string, string]{first: data, second: "safe"}
	result := p.GetFirst()
	sink(result)
}

func genericPair002F() {
	data := source()
	p := GenPair[string, string]{first: data, second: "safe"}
	result := p.GetSecond()
	sink(result)
}
