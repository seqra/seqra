package org.opentaint.ir.approximation.annotation

import kotlin.reflect.KClass

@Suppress("unused")
@Target(AnnotationTarget.CLASS)
annotation class Approximate(val value: KClass<*>)
