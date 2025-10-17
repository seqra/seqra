#!/bin/bash

env "opentaint.jvm.api.jar.path=$JVM_API_JAR" \
    "opentaint.jvm.approximations.jar.path=$JVM_APPROXIMATIONS_JAR" \
    "opentaint_taint_config_path=$TAINT_CONFIG" \
    "SARIF_Opentaint_ORGANIZATION=$SARIF_Opentaint_ORGANIZATION" \
    "SARIF_Opentaint_VERSION=$SARIF_Opentaint_VERSION" \
    "$JAVA_17_HOME"/bin/java \
      -Dorg.opentaint.ir.impl.storage.defaultBatchSize=2000 \
      -Djdk.util.jar.enableMultiRelease=false \
      -Xmx8g \
      -jar $ANALYZER_JAR_NAME "$@"
