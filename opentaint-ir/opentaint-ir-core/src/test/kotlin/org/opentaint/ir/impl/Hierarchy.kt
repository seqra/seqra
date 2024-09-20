package org.opentaint.ir.impl


interface SuperDuper

open class A : SuperDuper

open class B : A()

open class C : B()
open class D : B()