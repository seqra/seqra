package org.opentaint.ir.approximation.annotation

import kotlin.reflect.KClass

@Suppress("unused")
@Target(allowedTargets = [])
annotation class Version(val target: String, val fromVersion: String, val toVersion: String)

@Suppress("unused")
@Target(AnnotationTarget.CLASS)
annotation class Approximate(
    val value: KClass<*>,
    val versions: Array<Version> = [],
)

@Suppress("unused")
@Target(AnnotationTarget.CLASS)
annotation class ApproximateByName(
    val value: String,
    val versions: Array<Version> = [],
)

@Suppress("unused")
@Target(AnnotationTarget.FUNCTION)
annotation class ApproximatedMethodName(val value: String)

@Suppress("unused")
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class ApproximatedFieldName(val value: String)
