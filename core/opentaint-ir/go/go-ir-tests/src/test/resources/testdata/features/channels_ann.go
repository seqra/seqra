package p

//go:ir-test func=makeChan
func makeChan() chan int {
	return make(chan int, 10) //@ inst("GoIRMakeChan")
}

//go:ir-test func=sendRecv
func sendRecv(ch chan int, val_ int) int {
	ch <- val_  //@ inst("GoIRSend")
	return <-ch //@ inst("GoIRUnOp", op=ARROW)
}

//go:ir-test func=startGoroutine
func startGoroutine(ch chan int) {
	go func() { //@ inst("GoIRGo")
		ch <- 42
	}()
}

//go:ir-test func=deferExample
func deferExample(ch chan int) {
	defer close(ch) //@ inst("GoIRDefer")
}
