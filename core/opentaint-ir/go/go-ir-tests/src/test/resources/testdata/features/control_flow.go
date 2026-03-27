package p

// @ cfg(blocks=4)
//
//go:ir-test func=ifElse
func ifElse(x int) int {
	if x > 0 {
		return x
	} else {
		return -x
	}
}

//go:ir-test func=forLoop
func forLoop(n int) int {
	total := 0
	for i := 0; i < n; i++ {
		total += i //@ inst("GoIRBinOp", op=ADD)
	}
	return total
}

//go:ir-test func=earlyReturn
func earlyReturn(x int) string {
	if x > 100 {
		return "big"
	}
	if x > 0 {
		return "small"
	}
	if x == 0 {
		return "zero"
	}
	return "negative"
}

//go:ir-test func=infiniteBreak
func infiniteBreak(n int) int {
	x := 0
	for {
		if x >= n {
			break
		}
		x++
	}
	return x
}
