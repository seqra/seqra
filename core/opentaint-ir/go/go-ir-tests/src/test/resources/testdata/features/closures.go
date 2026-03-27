package p

// @ count("GoIRMakeClosure", 1)
//
//go:ir-test func=makeAdder
func makeAdder(x int) func(int) int {
	return func(y int) int {
		return x + y
	}
}

// @ count("GoIRMakeClosure", 1)
//
//go:ir-test func=counter
func counter() func() int {
	n := 0
	return func() int {
		n++
		return n
	}
}

//go:ir-test func=applyTwice
func applyTwice(f func(int) int, x int) int {
	return f(f(x)) //@ inst("GoIRCall", mode=DYNAMIC)
}
