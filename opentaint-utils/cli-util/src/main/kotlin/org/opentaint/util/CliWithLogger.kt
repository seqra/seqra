package org.opentaint.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

abstract class CliWithLogger : CliktCommand() {
    abstract fun main()

    private val logsFile by option(help = "File to which logs will be saved")
        .newFile()
        .defaultLazy { Path("opentaint.log") }

    private val logLevels = listOf(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR)

    private val verbosity: Level by option(help = "Analyzer verbosity (log level)")
        .choice(logLevels.associateBy { it.levelStr }, ignoreCase = true)
        .default(Level.WARN)

    private fun configureLogger(logsFile: Path, verbosity: Level) {
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger

        val ple = PatternLayoutEncoder().apply {
            pattern = "%d{HH:mm:ss.SSS} |%.-1level| %replace(%c{0}){'(\\\$Companion)?\\\$logger\\\$1',''} - %msg%n"
            context = rootLogger.loggerContext
        }

        (rootLogger.getAppender("console") as? ConsoleAppender)?.encoder = ple

        val appender = FileAppender<ILoggingEvent>().apply {
            file = logsFile.absolutePathString()
            isAppend = false
            encoder = ple
            context = rootLogger.loggerContext
        }

        ple.start()
        appender.start()
        rootLogger.addAppender(appender)

        rootLogger.level = verbosity
    }

    final override fun run() {
        configureLogger(logsFile, verbosity)
        main()
    }
}
