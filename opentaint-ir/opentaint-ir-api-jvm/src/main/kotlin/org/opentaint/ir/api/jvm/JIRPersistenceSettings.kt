package org.opentaint.ir.api.jvm

class JIRPersistenceSettings {
    val persistenceId: String? get() = implSettings?.persistenceId
    var persistenceLocation: String? = null
    var persistenceClearOnStart: Boolean? = null
    var implSettings: JIRPersistenceImplSettings? = null
}

interface JIRPersistenceImplSettings {
    val persistenceId: String
}
