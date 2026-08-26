/** Names used for built-in dataflow model modules. */
object ApproximationModules {
    const val PROJECT_PREFIX = "dataflow-approximations-"

    /** The support module that is on the classpath of each model module. */
    const val CORE = ":${PROJECT_PREFIX}core"

    /** Names of the API jar and its directory in the analyzer jar. */
    const val API_JAR_NAME = "opentaint-approximations-api.jar"
    const val API_JAR_RESOURCE_DIR = "opentaint-approximations-api"
}
