package p

//go:ir-test func=bitwiseOps
func bitwiseOps(a, b int) int {
	x := a & b  //@ inst("GoIRBinOp", op=AND)
	y := a | b  //@ inst("GoIRBinOp", op=OR)
	z := a ^ b  //@ inst("GoIRBinOp", op=XOR)
	w := a &^ b //@ inst("GoIRBinOp", op=AND_NOT)
	return x + y + z + w
}

//go:ir-test func=shiftOps
func shiftOps(a int, n uint) int {
	left := a << n  //@ inst("GoIRBinOp", op=SHL)
	right := a >> n //@ inst("GoIRBinOp", op=SHR)
	return left + right
}
