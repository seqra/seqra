package p

// @ count("GoIRAlloc", 1)
//
//go:ir-test func=pointerCreate
func pointerCreate() *int {
	x := 42
	return &x
}

//go:ir-test func=pointerDeref
func pointerDeref(p *int) int {
	return *p //@ inst("GoIRUnOp", op=DEREF)
}

//go:ir-test func=pointerStore
func pointerStore(p *int, val_ int) {
	*p = val_ //@ inst("GoIRStore")
}

//go:ir-test func=heapAlloc
func heapAlloc() *int {
	p := new(int) //@ inst("GoIRAlloc", isHeap=true)
	*p = 42
	return p
}
