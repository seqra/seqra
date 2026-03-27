package p

//go:ir-test func=intArith
func intArith(a, b int) int {
	sum := a + b  //@ inst("GoIRBinOp", op=ADD)
	diff := a - b //@ inst("GoIRBinOp", op=SUB)
	prod := a * b //@ inst("GoIRBinOp", op=MUL)
	return sum + diff + prod
}

//go:ir-test func=intDiv
func intDiv(a, b int) (int, int) {
	q := a / b //@ inst("GoIRBinOp", op=DIV)
	r := a % b //@ inst("GoIRBinOp", op=REM)
	return q, r
}

//go:ir-test func=comparison
func comparison(a, b int) bool {
	if a == b { //@ inst("GoIRBinOp", op=EQ)
		return true
	}
	if a < b { //@ inst("GoIRBinOp", op=LT)
		return true
	}
	return false
}
