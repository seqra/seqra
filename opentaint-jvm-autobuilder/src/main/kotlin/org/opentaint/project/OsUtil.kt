package org.opentaint.project

fun selectExecutableName(win: String, other: String): String = if (!osIsWindows()) other else win

private fun osIsWindows() = System.getProperty("os.name").lowercase().contains("win")
