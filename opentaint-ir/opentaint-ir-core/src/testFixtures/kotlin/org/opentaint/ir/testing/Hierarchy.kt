package org.opentaint.ir.testing


interface SuperDuper {
    fun say() {}
}

open class A : SuperDuper

open class B : A() {
    fun saySmth(phrase: String) {
    }
}

open class C() : B() {

    constructor(smth: String) : this()
    constructor(smth: String, smth2: String) : this()

    fun saySmth() {

    }
}

open class D : B()