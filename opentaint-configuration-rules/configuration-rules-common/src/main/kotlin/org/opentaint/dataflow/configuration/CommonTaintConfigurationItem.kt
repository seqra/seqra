package org.opentaint.dataflow.configuration

interface CommonTaintConfigurationItem

interface CommonTaintConfigurationSinkMeta {
    val message: String
    val severity: Severity

    enum class Severity {
        Note, Warning, Error
    }
}

interface CommonTaintConfigurationSink : CommonTaintConfigurationItem {
    val id: String
    val meta: CommonTaintConfigurationSinkMeta
}

interface CommonTaintConfigurationSource : CommonTaintConfigurationItem

interface CommonTaintAction

interface CommonTaintAssignAction : CommonTaintAction

interface CommonTaintRulesProvider
