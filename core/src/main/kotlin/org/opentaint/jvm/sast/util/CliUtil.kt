package org.opentaint.jvm.sast.util

import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.types.path

internal fun RawOption.directory() = path(mustExist = true, canBeFile = false, canBeDir = true)

internal fun RawOption.newDirectory() = path(mustExist = false, canBeFile = false, canBeDir = true)

internal fun RawOption.file() = path(mustExist = true, canBeFile = true, canBeDir = false)

internal fun RawOption.newFile() = path(mustExist = false, canBeFile = true, canBeDir = false)
