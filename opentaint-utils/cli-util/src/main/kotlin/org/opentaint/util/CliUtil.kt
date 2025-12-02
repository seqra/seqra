package org.opentaint.util

import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.types.path

fun RawOption.directory() = path(mustExist = true, canBeFile = false, canBeDir = true)

fun RawOption.newDirectory() = path(mustExist = false, canBeFile = false, canBeDir = true)

fun RawOption.file() = path(mustExist = true, canBeFile = true, canBeDir = false)

fun RawOption.newFile() = path(mustExist = false, canBeFile = true, canBeDir = false)
