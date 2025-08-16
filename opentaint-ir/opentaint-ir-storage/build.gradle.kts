dependencies {
    api(project(":opentaint-ir-api-storage"))

    compileOnly(Libs.xodusEnvironment)
    compileOnly(Libs.lmdb_java)
    compileOnly(Libs.rocks_db)

    testImplementation(Libs.xodusEnvironment)
    testImplementation(Libs.lmdb_java)
    testImplementation(Libs.rocks_db)

    testFixturesImplementation(platform(Libs.junit_bom))
    testFixturesImplementation(Libs.junit_jupiter)
}