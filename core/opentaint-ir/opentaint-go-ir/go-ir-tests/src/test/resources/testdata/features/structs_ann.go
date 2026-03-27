package p

type Point struct {
	X, Y int
}

type Named struct {
	Name string
	Age  int
}

type Base struct {
	ID int
}

type Derived struct {
	Base
	Extra string
}

//go:ir-test func=getX
func getX(p *Point) int {
	return p.X //@ inst("GoIRFieldAddr", fieldName=X, fieldIndex=0)
}

//go:ir-test func=setXY
func setXY(p *Point, x, y int) {
	p.X = x //@ inst("GoIRFieldAddr", fieldName=X)
	p.Y = y //@ inst("GoIRFieldAddr", fieldName=Y)
}

//go:ir-test func=makePoint
func makePoint(x, y int) Point {
	return Point{X: x, Y: y}
}
