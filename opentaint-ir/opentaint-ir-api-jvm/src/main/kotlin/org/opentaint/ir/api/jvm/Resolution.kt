package org.opentaint.ir.api.jvm

interface Resolution
interface MethodResolution : Resolution
interface RecordComponentResolution : Resolution
interface FieldResolution : Resolution
interface TypeResolution : Resolution

object Malformed : TypeResolution, FieldResolution, MethodResolution, RecordComponentResolution
object Pure : TypeResolution, FieldResolution, MethodResolution, RecordComponentResolution
