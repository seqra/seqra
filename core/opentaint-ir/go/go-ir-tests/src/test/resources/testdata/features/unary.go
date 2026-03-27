package p

//go:ir-test func=negate
func negate(x int) int {
	return -x //@ inst("GoIRUnOp", op=NEG)
}

//go:ir-test func=notOp
func notOp(b bool) bool {
	return !b //@ inst("GoIRUnOp", op=NOT)
}

//go:ir-test func=derefPointer
func derefPointer(p *int) int {
	return *p //@ inst("GoIRUnOp", op=DEREF)
}

//go:ir-test func=bitwiseNot
func bitwiseNot(x int) int {
	return ^x //@ inst("GoIRUnOp", op=XOR)
}
