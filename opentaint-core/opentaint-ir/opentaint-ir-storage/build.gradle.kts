import org.opentaint.common.JunitDependencies

plugins {
    id("kotlin-conventions")
    `java-test-fixtures`
}

dependencies {
    api(project(":opentaint-ir-api-storage"))

    compileOnly(Libs.xodusEnvironment)
    compileOnly(Libs.lmdb_java)
    compileOnly(Libs.rocks_db)
    compileOnly(Libs.guava)
    compileOnly(Libs.xodusUtils)

    testImplementation(Libs.xodusEnvironment)
    testImplementation(Libs.lmdb_java)
    testImplementation(Libs.rocks_db)

    testFixturesImplementation(platform(JunitDependencies.Libs.junit_bom))
    testFixturesImplementation(JunitDependencies.Libs.junit_jupiter)
    testFixturesApi(Libs.xodusEnvironment)
    testFixturesApi(Libs.lmdb_java)
    testFixturesApi(Libs.rocks_db)
}