package p

type Stringer interface {
	String() string
}

type Reader interface {
	Read(p []byte) (int, error)
}

type ReadWriter interface {
	Reader
	Write(p []byte) (int, error)
}

type MyStr struct {
	val_ string
}

func (m MyStr) String() string {
	return m.val_
}

// @ count("GoIRMakeInterface", 1)
//
//go:ir-test func=makeInterface
func makeInterface(s MyStr) Stringer {
	return s
}

//go:ir-test func=typeAssert
func typeAssert(s Stringer) MyStr {
	return s.(MyStr) //@ inst("GoIRTypeAssert", commaOk=false)
}

//go:ir-test func=typeAssertCommaOk
func typeAssertCommaOk(s Stringer) (MyStr, bool) {
	v, ok := s.(MyStr) //@ inst("GoIRTypeAssert", commaOk=true)
	return v, ok
}
