package p

//go:ir-test func=mustPositive
func mustPositive(x int) int {
	if x <= 0 {
		panic("must be positive") //@ inst("GoIRPanic")
	}
	return x
}

//go:ir-test func=deferRecover
func deferRecover() (result int) {
	defer func() { //@ inst("GoIRDefer")
		if r := recover(); r != nil {
			result = -1
		}
	}()
	panic("test")
}
